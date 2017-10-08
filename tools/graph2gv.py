#!/usr/bin/env python
import sys
from graph import DFGraph, DFNode
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
        if node.data is not None:
            label = node.data
        elif node.ref is not None:
            label = '(%s)' % node.ref
        else:
            assert 0
        out.write(h+' N%s [label=%s' % (node.nid, q(label)))
        if node.ntype in ('select','begin','end'):
            out.write(', shape=diamond')
        elif node.data is not None:
            out.write(', shape=box, fontname=courier')
        elif node.ntype is not None:
            out.write(', shape=box')
        if highlight is not None and node.nid in highlight:
            out.write(', style=filled')
        out.write('];\n')
    for child in scope.children:
        write_gv(out, child, highlight, level=level+1)
    if level == 0:
        for node in scope.walk():
            for (label,src) in node.inputs.items():
                out.write(h+' N%s -> N%s' % (src.nid, node.nid))
                label = (label or '')
                out.write(h+' [label=%s' % q(label))
                if label == 'cond':
                    out.write(', style=dotted')
                out.write('];\n')
            for (label,src) in node.other.items():
                out.write(h+' N%s -> N%s' % (src.nid, node.nid))
                out.write(h+' [xlabel=%s, style=dashed, constraint=false];\n' % q(label))
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
