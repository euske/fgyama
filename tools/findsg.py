#!/usr/bin/env python
import sys
import sqlite3
from graph import SourceDB, DFGraph
from graph import get_graphs, fetch_graph
from graph2db import get_key, get_args

def find_graph(cur, graph, minnodes=5, minbranches=2):
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
                if gid == graph.gid: continue
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
                a.append((n.nid, m))
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
        print('usage: %s [-v] [-b basedir] [-m minnodes] [-f minbranches] '
              '[-s gidstart] [-n nresults] [graph ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'vb:m:f:s:n:')
    except getopt.GetoptError:
        return usage()
    verbose = False
    srcdb = None
    minnodes = 5
    minbranches = 3
    gidstart = 0
    nresults = None
    for (k, v) in opts:
        if k == '-v': verbose = True
        elif k == '-b': srcdb = SourceDB(v)
        elif k == '-m': minnodes = int(v)
        elif k == '-f': minbranches = int(v)
        elif k == '-s': gidstart = int(v)
        elif k == '-n': nresults = int(v)
    if not args: return usage()

    graphname = args.pop(0)
    graphconn = sqlite3.connect(graphname)
    graphcur = graphconn.cursor()

    indexname = args.pop(0)
    indexconn = sqlite3.connect(indexname)
    indexcur = indexconn.cursor()
    
    def show_result(graph0, src0, votes):
        print ('+', graph0.gid)
        for (gid1,pairs) in votes.items():
            graph1 = fetch_graph(graphcur, gid1)
            print ('-', graph0.gid, graph1.gid,
                   '-'.join(k for (_,(k,_)) in pairs),
                   ','.join('%d:%d' % (n0,n1) for (n0,(_,n1)) in pairs))
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
            print ('###', src0.name)
            src0.show_nodes(nodes0)
            print ('###', src1.name)
            src1.show_nodes(nodes1)
            print ()
        return
    
    cur1 = graphconn.cursor()
    gids = cur1.execute('SELECT Gid FROM DFGraph WHERE ? <= Gid;', (gidstart,))
    for (gid0,) in gids:
        graph0 = fetch_graph(graphcur, gid0)
        src0 = None
        if srcdb is not None:
            try:
                src0 = srcdb.get(graph0.src)
            except KeyError:
                pass
        if verbose or (gid0 % 100) == 0:
            sys.stderr.write('*** %d ***\n' % gid0)
            sys.stderr.flush()
        maxvotes = find_graph(
            indexcur, graph0,
            minnodes=minnodes, minbranches=minbranches)
        if not maxvotes: continue
        show_result(graph0, src0, maxvotes)
        sys.stdout.flush()
        if nresults is not None:
            nresults -= 1
        if nresults == 0: break
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
