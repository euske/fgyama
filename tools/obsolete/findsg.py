#!/usr/bin/env python
import sys
import sqlite3
from srcdb import SourceDB
from graph import GraphDB
from graph2db import IndexDB, get_nodekey

def isok(n):
    return (n.data is not None)

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

def count_branch(t):
    (_,_,st) = t
    n = b = 0
    for (_,c) in st:
        b = max(b, count_branch(c))
        n += 1
    return max(1, n, b)

def str_tree(t, label=None):
    (node,_,st) = t
    r = [('%s:%s:%s' % ((label or ''), node.kind, (node.data or '')))]
    for (label,c) in st:
        r.append(str_tree(c,label))
    return '(%s)' % ' '.join(r)

def get_match(node0, node1):
    visited0 = set()
    visited1 = set()
    def visit(n0, n1):
        if n0 in visited0: return None
        if n1 in visited1: return None
        key0 = get_nodekey(n0)
        if key0 is None:
            visited0.add(n0)
            if '' in n0.inputs:
                return visit(n0.inputs[''], n1)
            else:
                return None
        key1 = get_nodekey(n1)
        if key1 is None:
            visited1.add(n1)
            if '' in n1.inputs:
                return visit(n0, n1.inputs[''])
            else:
                return None
        if key0 != key1: return None
        visited0.add(n0)
        visited1.add(n1)
        st = []
        for (label,src0) in n0.get_inputs():
            if label not in n1.inputs: continue
            src1 = n1.inputs[label]
            t = visit(src0, src1)
            if t is not None:
                st.append((label, t))
        return (n0,n1,st)

    return visit(node0, node1)

def main(argv):
    import getopt
    def usage():
        print('usage: %s [-v] [-B basedir] [-n minnodes] [-m mindepth] '
              'graph.db index.db [graphs]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'vB:n:m:')
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
        elif k == '-m': mindepth = int(v)
    if not args: return usage()
    graphdb = GraphDB(args.pop(0))
    if not args: return usage()
    indexdb = IndexDB(args.pop(0))

    def show_result(graph0, graph1, pairs):
        try:
            src1 = srcdb.get(graph1.src)
        except KeyError:
            return
        try:
            src0 = srcdb.get(graph0.src)
        except KeyError:
            return
        print ('###', src0.name)
        for (_,line) in src0.show_nodes([ n0 for (n0,n1) in pairs ]):
            print(line)
        print ('###', src1.name)
        for (_,line) in src1.show_nodes([ n1 for (n0,n1) in pairs ]):
            print(line)
        print ()
        return

    checkgid = (lambda graph, gid: graph.gid is None or graph.gid+100 < gid)
    if args:
        graphs = load_graphs(args.pop(0))
    else:
        graphs = ( graphdb.get(gid) for gid in graphdb.get_gids() )

    for graph0 in graphs:
        gid0 = graph0.gid
        if verbose or (isinstance(gid0, int) and (gid0 % 100) == 0):
            sys.stderr.write('*** %d ***\n' % gid0)
            sys.stderr.flush()
        result = indexdb.search_graph(
            graph0, checkgid=checkgid,
            minnodes=minnodes, mindepth=mindepth)
        if not result: continue
        maxmatch = None
        maxnodes = -1
        for (gid1,result) in result.items():
            graph1 = graphdb.get(gid1)
            result = [ (label, node0, graph1.nodes[nid1]) for (_,label,node0,nid1) in result ]
            visited0 = set()
            visited1 = set()
            for (_,node0,node1) in result:
                if node0 in visited0: continue
                if node1 in visited1: continue
                tree = get_match(node0, node1)
                if tree is None: continue
                pairs = list(flatten(tree))
                for (n0,n1) in pairs:
                    visited0.add(n0)
                    visited1.add(n1)
                nodes = sum( 1 for (n,_) in pairs if isok(n) )
                if nodes < minnodes: continue
                depth = count_depth(tree)
                if depth < mindepth: continue
                branch = count_branch(tree)
                if maxnodes < nodes:
                    maxnodes = nodes
                    maxmatch = (nodes, depth, branch, graph1, pairs, tree)
        if maxmatch is not None:
            (nodes, depth, branch, graph1, pairs, tree) = maxmatch
            print ('-', graph0.gid, graph1.gid, nodes, depth, branch,
                   ','.join('%d:%d' % (n0.nid, n1.nid) for (n0,n1) in pairs),
                   str_tree(tree))
            if srcdb is not None:
                show_result(graph0, graph1, pairs)
        sys.stdout.flush()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
