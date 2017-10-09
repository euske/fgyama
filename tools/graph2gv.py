#!/usr/bin/env python
import sys
from graph import DFGraph, DFNode
from graph import get_graphs

def q(s):
    if s:
        return '"%s"' % s.replace('"',r'\"')
    else:
        return '""'

def qp(props):
    return ', '.join( '%s=%s' % (k,q(v)) for (k,v) in props.items() )

def write_gv(out, scope, highlight=None, level=0):
    h = ' '*level
    if level == 0:
        out.write('digraph %s {\n' % scope.name)
    else:
        out.write(h+'subgraph cluster_%s {\n' % scope.name)
    out.write(h+' label=%s;\n' % q(scope.name))
    for node in scope.nodes:
        if node.ntype in ('select','begin','end'):
            styles = {'shape': 'diamond', 
                      'label': '%s (%s)' % (node.ntype, node.ref)}
        elif node.ntype in ('return',):
            styles = {'shape': 'box', 
                      'label': '%s (%s)' % (node.ntype, node.ref)}
        elif node.data is not None:
            styles = {'shape': 'box', 'fontname':'courier',
                      'label': node.data}
        else:
            styles = {'label': node.ref}
        if highlight is not None and node.nid in highlight:
            styles['style'] = 'filled'
        out.write(h+' N%s [%s];\n' % (node.nid, qp(styles)))
    for child in scope.children:
        write_gv(out, child, highlight, level=level+1)
    if level == 0:
        for node in scope.walk():
            for (label,src) in node.inputs.items():
                if label == 'cond':
                    styles = {'style': 'dotted', 'label': label}
                elif label == '_repeat':
                    styles = {'xlabel': 'repeat',
                              'style': 'dashed', 'constraint': 'false'}
                elif label.startswith('_'):
                    continue
                else:
                    styles = {'label': label}
                out.write(h+' N%s -> N%s [%s];\n' % (src.nid, node.nid, qp(styles)))
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
