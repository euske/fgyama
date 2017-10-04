#!/usr/bin/env python
import sys
from graph import DFGraph, DFLink, DFNode
from graph import get_graphs

def q(s):
    if s:
        return '"%s"' % s.replace('"',r'\"')
    else:
        return '""'

def write_gv(out, scope, highlight=None, level=0):
    h = ' '*level
    if level == 0:
        out.write('digraph %s {\n' % scope.name)
    else:
        out.write(h+'subgraph cluster_%s {\n' % scope.name)
    out.write(h+' label=%s;\n' % q(scope.name))
    for node in scope.nodes:
        label = (node.label or '')
        if node.ref is not None:
            label = '%s (%s)' % (label, node.ref)
        out.write(h+' N%s [label=%s' % (node.nid, q(label)))
        if node.ntype in (DFNode.N_Const,):
            out.write(', shape=box, fontname=courier')
        elif node.ntype in (DFNode.N_Operator, DFNode.N_Terminal):
            out.write(', shape=box')
        elif node.ntype in (DFNode.N_Branch, DFNode.N_Join, DFNode.N_Loop):
            out.write(', shape=diamond')
        if highlight is not None and node.nid in highlight:
            out.write(', style=filled')
        out.write('];\n')
    for child in scope.children:
        write_gv(out, child, highlight, level=level+1)
    if level == 0:
        for node in scope.walk():
            for link in node.outgoing:
                out.write(h+' N%s -> N%s' % (link.srcid, link.dstid))
                label = (link.label or '')
                out.write(h+' [label=%s' % q(label))
                if link.ltype == DFLink.L_ControlFlow:
                    out.write(', style=dotted')
                out.write('];\n')
            for link in node.other:
                if link.src == node and link.ltype == DFLink.L_BackFlow:
                    out.write(h+' N%s -> N%s' % (link.srcid, link.dstid))
                    out.write(h+' [xlabel=%s, style=dashed, constraint=false];\n' % q(link.label))
    out.write(h+'}\n')
    return

def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-o output] [-h nid] [graph ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'o:h:')
    except getopt.GetoptError:
        return usage()
    output = sys.stdout
    highlight = None
    for (k, v) in opts:
        if k == '-o': output = open(v, 'w')
        elif k == '-h': highlight = set(( int(nid) for nid in v.split(',') ))
    if not args: return usage()
    
    for path in args:
        for graph in get_graphs(path):
            if isinstance(graph, DFGraph):
                write_gv(output, graph.root, highlight=highlight)
                break
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
