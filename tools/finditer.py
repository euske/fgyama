#!/usr/bin/env python
import sys
from graph import SourceDB, DFGraph
from graph import get_graphs

def isiter(ref, n_begin, n_end):
    #print (ref, n_begin, n_end)
    def isref(n):
        if n.ref == ref:
            return True
        else:
            for (label,src) in n.get_inputs():
                if isref(src):
                    return True
            return False
    def isisolated(n):
        if n is n_begin:
            return True
        elif n.ntype == 'begin':
            return False
        elif n.ntype == 'end' and n is not n_end:
            return True
        else:
            for (label,src) in n.get_inputs():
                if not isisolated(src):
                    return False
            return True
    cond = n_end.inputs['cond']
    return isref(cond) and isisolated(n_end)

def finditer(graph):
    refs = set()
    for node in graph.nodes.values():
        if node.ntype == 'begin':
            n_end = node.inputs['_repeat']
            ref = node.ref
            if isiter(ref, node, n_end):
                refs.add(ref)
    return refs

def main(argv):
    import getopt
    def usage():
        print('usage: %s [-B basedir] [graph ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'B:')
    except getopt.GetoptError:
        return usage()
    verbose = False
    srcdb = None
    for (k, v) in opts:
        if k == '-v': verbose = True
        elif k == '-B': srcdb = SourceDB(v)
    if not args: return usage()
    
    for graph in get_graphs(args.pop(0)):
        src = None
        if srcdb is not None:
            try:
                src = srcdb.get(graph.src)
            except KeyError:
                pass
        for ref in finditer(graph):
            print (src, graph, ref)
            if src is not None:
                nodes = [ node for node in graph.nodes.values() if node.ref == ref ]
                src.show_nodes(nodes)
                print()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
