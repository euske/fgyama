#!/usr/bin/env python
import sys
from graph2gv import SourceFile, SourceDB, DFGraph, DFLink, DFNode
from graph2gv import load_graphs

def doit(db, graph):
    for node in graph.nodes.values():
        if node.ntype != DFNode.N_Loop: continue
        ref = node.ref
        loop_begin = node
        loop_end = None
        for link in loop_begin.other:
            if link.ltype == DFLink.L_Informational and link.name == 'end':
                loop_end = link.dst
        cond = None
        for link in loop_end.recv:
            if link.ltype == DFLink.L_ControlFlow:
                cond = link.src
                
        def findvar(n):
            if n.ref == ref:
                return n
            else:
                for link in n.recv:
                    if link.ltype == DFLink.L_DataFlow:
                        c = findvar(link.src)
                        if c is not None:
                            return c
                return None
        def isisolated(n):
            if n is loop_begin:
                return True
            if n.ntype == DFNode.N_Loop:
                return False
            if n.ntype == DFNode.N_Branch and n is not loop_end:
                return True
            else:
                for link in n.recv:
                    if link.ltype == DFLink.L_DataFlow and not isisolated(link.src):
                        return False
                return True
        var = findvar(cond)
        if var is None: continue
        print ('+', isisolated(loop_end), var)
        src = db.get(graph.src)
        nodes = [var]
        for n in node.scope.walk():
            if n.ref == ref and n.ntype in (DFNode.N_Refer, DFNode.N_Assign):
                nodes.append(n)
        src.show_nodes(nodes)
        print ()
    return

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
    for graph in load_graphs(fileinput.input(args)):
        if isinstance(graph, DFGraph):
            doit(db, graph)
    #db.show()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
