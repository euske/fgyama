#!/usr/bin/env python
import sys
import sqlite3
from graph2gv import SourceDB, DFGraph, DFLink, DFNode
from graph2db import get_key, get_args, fetch_graph

def find_graph(cur, gid0, graph, minlevel=3, minnodes=5):
    #graph.dump()
    args = get_args(graph)
    
    def match_tree(pid, link0, node, match):
        key = get_key(link0, node, args.get(node))
        if key is None:
            tid = pid
        else:
            cur.execute(
                'SELECT Tid FROM TreeNode WHERE Pid=? AND Key=?;',
                (pid, key))
            result = cur.fetchone()
            if result is None: return
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
                a[gid] = nid
        for link1 in node.recv:
            match_tree(tid, link1, link1.src, match)
        return

    maxvotes = {}
    def find_tree(node):
        match = {}
        match_tree(0, None, node, match)
        votes = {}
        for (n,gids) in match.items():
            for (gid,nid) in gids.items():
                if gid in votes:
                    a = votes[gid]
                else:
                    a = votes[gid] = []
                a.append((n, nid))
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
        print('usage: %s [-b basedir] [graph ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'b:')
    except getopt.GetoptError:
        return usage()
    basedir = '.'
    for (k, v) in opts:
        if k == '-b': basedir = v
    if not args: return usage()

    db = SourceDB(basedir)
    dbname = args.pop(0)
    conn = sqlite3.connect(dbname)
    cur = conn.cursor()

    if args:
        with fileinput.input(args) as fp:
            for graph in load_graphs(fp):
                if isinstance(graph, DFGraph):
                    maxvotes = find_graph(cur, 0, graph)
    else:
        cur0 = conn.cursor()
        for (gid0,) in cur0.execute('SELECT Gid FROM DFGraph;'):
            graph0 = fetch_graph(cur, gid0)
            print ('***', gid0)
            try:
                src0 = db.get(graph0.src)
            except KeyError:
                src0 = None
            maxvotes = find_graph(cur, gid0, graph0)
            for (gid1,pairs) in maxvotes.items():
                graph1 = fetch_graph(cur, gid1)
                print ('=', gid1)
                if src0 is not None:
                    try:
                        src1 = db.get(graph1.src)
                    except KeyError:
                        continue
                    nodes0 = []
                    nodes1 = []
                    for (node0,nid1) in pairs:
                        nodes0.append(node0)
                        nodes1.append(graph1.nodes[nid1])
                    src0.show_nodes(nodes0)
                    print ('---')
                    src1.show_nodes(nodes1)
                    print ()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
