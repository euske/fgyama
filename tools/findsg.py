#!/usr/bin/env python
import sys
import sqlite3
from graph2gv import SourceDB, DFGraph, DFLink, DFNode
from graph2db import get_key, get_args, fetch_graph

def find_graph(cur, gid0, graph, minnodes=5, minbranches=3):
    #graph.dump()
    args = get_args(graph)
    
    def match_tree(pid, link0, node, match):
        key = get_key(link0, node, args.get(node))
        if key is None:
            tid = pid
            branches = 0
        else:
            cur.execute(
                'SELECT Tid FROM TreeNode WHERE Pid=? AND Key=?;',
                (pid, key))
            result = cur.fetchone()
            if result is None: return 0
            (tid,) = result
            rows = cur.execute(
                'SELECT Gid,Nid FROM TreeLeaf WHERE Tid=?;',
                (tid,))
            for (gid,nid) in rows:
                if gid == gid0: continue
                if node in match:
                    a = match[node]
                else:
                    a = match[node] = {}
                a[gid] = (key,nid)
            branches = 1
        n = 0
        for link1 in node.recv:
            f = match_tree(tid, link1, link1.src, match)
            if 0 < f:
                n += 1
                branches = max(branches, f)
        return max(branches, n)

    maxvotes = {}
    def find_tree(node):
        match = {}
        branches = match_tree(0, None, node, match)
        if branches < minbranches: return
        votes = {}
        for (n,gids) in match.items():
            for (gid,m) in gids.items():
                if gid in votes:
                    a = votes[gid]
                else:
                    a = votes[gid] = []
                a.append((n, m))
        for (gid,pairs) in votes.items():
            if len(pairs) < minnodes: continue
            if gid in maxvotes and len(pairs) < len(maxvotes[gid]): continue
            maxvotes[gid] = pairs
        for link in node.recv:
            find_tree(link.src)
        return
    
    for node in graph.nodes.values():
        if not node.send:
            find_tree(node)
    return maxvotes

def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-b basedir] [-m minnodes] [-f minbranches] '
              '[-s gidstart] [graph ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'b:m:f:s:')
    except getopt.GetoptError:
        return usage()
    srcdb = None
    minnodes = 5
    minbranches = 3
    gidstart = 0
    for (k, v) in opts:
        if k == '-b': srcdb = SourceDB(v)
        elif k == '-m': minnodes = int(v)
        elif k == '-f': minbranches = int(v)
        elif k == '-s': gidstart = int(v)
    if not args: return usage()

    indexname = args.pop(0)
    conn = sqlite3.connect(indexname)
    cur = conn.cursor()

    if args:
        with fileinput.input(args) as fp:
            for graph in load_graphs(fp):
                if isinstance(graph, DFGraph):
                    maxvotes = find_graph(cur, 0, graph,
                                          minnodes=minnodes,
                                          minbranches=minbranches)
    else:
        cur0 = conn.cursor()
        rows = cur0.execute('SELECT Gid FROM DFGraph WHERE ? < Gid;',
                            (gidstart,))
        for (gid0,) in rows:
            graph0 = fetch_graph(cur, gid0)
            src0 = None
            if srcdb is not None:
                try:
                    src0 = srcdb.get(graph0.src)
                except KeyError:
                    pass
            print ('***', gid0)
            maxvotes = find_graph(cur, gid0, graph0,
                                  minnodes=minnodes,
                                  minbranches=minbranches)
            if maxvotes:
                print ('+', gid0, ' '.join(str(gid1) for gid1 in maxvotes.keys()))
            for (gid1,pairs) in maxvotes.items():
                graph1 = fetch_graph(cur, gid1)
                print ('=', gid0, gid1, '-'.join(k for (n,(k,_)) in pairs))
                if src0 is None: continue
                try:
                    src1 = srcdb.get(graph1.src)
                except KeyError:
                    continue
                nodes0 = []
                nodes1 = []
                for (node0,m) in pairs:
                    (key,nid1) = m
                    nodes0.append(node0)
                    nodes1.append(graph1.nodes[nid1])
                src0.show_nodes(nodes0)
                print ('---')
                src1.show_nodes(nodes1)
                print ()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
