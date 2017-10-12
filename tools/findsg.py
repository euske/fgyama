#!/usr/bin/env python
import sys
import sqlite3
from graph import SourceDB, DFGraph
from graph import load_graphs_file, load_graphs_db, fetch_graph
from graph2db import TreeCache

def is_key(node):
    if node.ntype in ('select','begin','end','return'):
        return True
    else:
        return (node.data is not None)

def flatten(t):
    (n0,n1,st) = t
    yield (n0,n1)
    for (_,c) in st:
        for z in flatten(c):
            yield z
    return

def count_nodes(t):
    (_,_,st) = t
    n = 0
    for (_,c) in st:
        n += count_nodes(c)
    return n+1

def count_depth(t):
    (_,_,st) = t
    n = 0
    for (_,c) in st:
        n = max(n, count_depth(c))
    return n+1

def str_tree(t, label=None):
    (node,_,st) = t
    r = [('%s:%s:%s' % ((label or ''), node.ntype, (node.data or '')))]
    for (label,c) in st:
        r.append(str_tree(c,label))
    return '(%s)' % ' '.join(r)

def get_match(node0, node1):
    visited0 = set()
    visited1 = set()
    def visit(n0, n1):
        if n0 in visited0: return None
        if n1 in visited1: return None
        if not is_key(n0):
            visited0.add(n0)
            if None in n0.inputs:
                return visit(n0.inputs[None], n1)
            else:
                return None
        if not is_key(n1):
            visited1.add(n1)
            if None in n1.inputs:
                return visit(n0, n1.inputs[None])
            else:
                return None
        visited0.add(n0)
        visited1.add(n1)
        st = []
        for (label,src0) in n0.inputs.items():
            if label is not None and label.startswith('_'):
                pass
            elif label in n1.inputs:
                src1 = n1.inputs[label]
                t = visit(src0, src1)
                if t is not None:
                    st.append((label, t))
        return (n0,n1,st)
    
    return visit(node0, node1)

def main(argv):
    import getopt
    def usage():
        print('usage: %s [-v] [-B basedir] [-n minnodes] [-d mindepth] '
              'graph.db index.db [graphs]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'vB:n:d:')
    except getopt.GetoptError:
        return usage()
    verbose = False
    srcdb = None
    minnodes = 5
    mindepth = 2
    for (k, v) in opts:
        if k == '-v': verbose = True
        elif k == '-B': srcdb = SourceDB(v)
        elif k == '-n': minnodes = int(v)
        elif k == '-d': mindepth = int(v)
    if not args: return usage()

    graphname = args.pop(0)
    graphconn = sqlite3.connect(graphname)
    graphcur = graphconn.cursor()

    indexname = args.pop(0)
    indexconn = sqlite3.connect(indexname)
    indexcur = indexconn.cursor()

    cache = TreeCache(indexconn.cursor())
    
    def show_result(graph0, graph1, pairs, s):
        print ('-', graph0.gid, graph1.gid,
               ','.join('%d:%d' % (n0.nid, n1.nid) for (n0,n1) in pairs),
               s)
        if srcdb is None: return
        try:
            src1 = srcdb.get(graph1.src)
        except KeyError:
            return
        try:
            src0 = srcdb.get(graph0.src)
        except KeyError:
            return
        print ('###', src0.name)
        src0.show_nodes([ n0 for (n0,n1) in pairs ])
        print ('###', src1.name)
        src1.show_nodes([ n1 for (n0,n1) in pairs ])
        print ()
        return

    checkgid = (lambda graph, gid: graph.gid is None or graph.gid < gid)
    if args:
        graphs = load_graphs_file(args.pop(0), None)
    else:
        graphs = load_graphs_db(graphconn)
    for graph0 in graphs:
        gid0 = graph0.gid
        if verbose or (isinstance(gid0, int) and (gid0 % 100) == 0):
            sys.stderr.write('*** %d ***\n' % gid0)
            sys.stderr.flush()
        result = cache.search_graph(graph0, checkgid=checkgid)
        if not result: continue
        print ('+', graph0.gid)
        for (gid1,result) in result.items():
            graph1 = fetch_graph(graphcur, gid1)
            result = [ (label, node0, graph1.nodes[nid1]) for (_,label,node0,nid1) in result ]
            visited0 = set()
            visited1 = set()
            for (_,node0,node1) in result:
                if node0 in visited0: continue
                if node1 in visited1: continue
                tree = get_match(node0, node1)
                pairs = list(flatten(tree))
                for (n0,n1) in pairs:
                    visited0.add(n0)
                    visited1.add(n1)
                if len(pairs) < minnodes: continue
                if count_depth(tree) < mindepth: continue
                show_result(graph0, graph1, pairs, str_tree(tree))
        sys.stdout.flush()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
