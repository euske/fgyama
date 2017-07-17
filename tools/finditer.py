#!/usr/bin/env python
import sys
from graph2gv import SourceFile, SourceDB, Graph, Scope, Link, Node
from graph2gv import load_graphs

def getsrc(node, f):
    for link in node.recv:
        if f(link):
            return link.src
    return None

def getdst(node, f):
    for link in node.send:
        if f(link):
            return link.dst
    return None

def doit(db, graph):
    for node in graph.nodes.values():
        if node.ntype != Node.N_Loop: continue
        ref = node.ref
        loop_begin = node
        loop_end = getdst(loop_begin,
                          lambda link: link.ltype == Link.L_Informational and link.name == 'end')
        cond = getsrc(loop_end, lambda link: link.ltype == Link.L_ControlFlow)
        def findvar(n):
            if n.ref == ref:
                return n
            else:
                for link in n.recv:
                    if link.ltype == Link.L_DataFlow:
                        c = findvar(link.src)
                        if c is not None:
                            return c
                return None
        def isisolated(n):
            if n is loop_begin:
                return True
            if n.ntype == Node.N_Loop:
                return False
            if n.ntype == Node.N_Branch and n is not loop_end:
                return True
            else:
                for link in n.recv:
                    if link.ltype == Link.L_DataFlow and not isisolated(link.src):
                        return False
                return True
        var = findvar(cond)
        if var is None: continue
        print ('+', isisolated(loop_end), var)
        src = db.get(graph.src)
        src.clear()
        src.addast(var.ast)
        for n in node.scope.walk():
            if n.ref == ref and n.ntype in (Node.N_Refer, Node.N_Assign):
                src.addast(n.ast)
        src.show()
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
        if isinstance(graph, Graph):
            doit(db, graph)
    #db.show()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
