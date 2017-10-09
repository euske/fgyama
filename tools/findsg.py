#!/usr/bin/env python
import sys
import sqlite3
from graph import SourceDB, DFGraph
from graph import get_graphs, fetch_graph
from graph2db import TreeCache

def main(argv):
    import getopt
    def usage():
        print('usage: %s [-v] [-B basedir] [-n minnodes] [-b minbranches] '
              'graph.db index.db [graphs]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'vB:n:b:')
    except getopt.GetoptError:
        return usage()
    verbose = False
    srcdb = None
    minnodes = 5
    minbranches = 2
    for (k, v) in opts:
        if k == '-v': verbose = True
        elif k == '-B': srcdb = SourceDB(v)
        elif k == '-n': minnodes = int(v)
        elif k == '-b': minbranches = int(v)
    if not args: return usage()

    graphname = args.pop(0)
    graphconn = sqlite3.connect(graphname)
    graphcur = graphconn.cursor()

    indexname = args.pop(0)
    indexconn = sqlite3.connect(indexname)
    indexcur = indexconn.cursor()

    cache = TreeCache(indexconn.cursor())
    
    def show_result(graph0, src0, votes):
        print ('+', graph0.gid)
        for (gid1,pairs) in votes.items():
            graph1 = fetch_graph(graphcur, gid1)
            print ('-', graph0.gid, graph1.gid,
                   '-'.join(key for (key,_,_) in pairs),
                   ','.join('%d:%d' % (node.nid,nid) for (_,node,nid) in pairs))
            if src0 is None: continue
            try:
                src1 = srcdb.get(graph1.src)
            except KeyError:
                continue
            nodes0 = []
            nodes1 = []
            for (key,node,nid) in pairs:
                nodes0.append(node)
                nodes1.append(graph1.nodes[nid])
            print ('###', src0.name)
            src0.show_nodes(nodes0)
            print ('###', src1.name)
            src1.show_nodes(nodes1)
            print ()
        return
    
    for graph0 in get_graphs(args.pop(0)):
        gid0 = graph0.gid
        if verbose or (isinstance(gid0, int) and (gid0 % 100) == 0):
            sys.stderr.write('*** %d ***\n' % gid0)
            sys.stderr.flush()
        src0 = None
        if srcdb is not None:
            try:
                src0 = srcdb.get(graph0.src)
            except KeyError:
                pass
        maxvotes = cache.search_graph(
            graph0,
            minnodes=minnodes, minbranches=minbranches,
            checkgid=(lambda gid: graph0.gid < gid))
        if not maxvotes: continue
        show_result(graph0, src0, maxvotes)
        sys.stdout.flush()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
