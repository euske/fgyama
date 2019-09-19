#!/usr/bin/env python
import io
import sys
from subprocess import Popen, PIPE
from graph import get_graphs
from getwords import stripid, splitmethodname

def q(s):
    if s:
        return '"%s"' % s.replace('"',r'\"')
    else:
        return '""'

def qp(props):
    return ', '.join( '%s=%s' % (k,q(v)) for (k,v) in props.items() )

def write_gv(out, scope, highlight=None, level=0, name=None):
    h = ' '*level
    if name is None:
        name = scope.name
    if level == 0:
        out.write('digraph %s {\n' % q(name))
    else:
        out.write(h+'subgraph %s {\n' % q("cluster_"+name))
    out.write(h+' label=%s;\n' % q(name))
    for node in scope.nodes:
        kind = node.kind
        styles = { 'label':kind }
        if kind in ('join','begin','end','repeat','case'):
            styles['shape'] = 'diamond'
            if node.ref is not None:
                styles['label'] = '%s (%s)' % (kind, stripid(node.ref))
        elif kind in ('value', 'valueset'):
            styles['shape'] = 'box'
            styles['fontname'] = 'courier'
            styles['label'] = node.data
        elif kind in ('input','output','receive'):
            if node.ref is not None:
                styles['label'] = '%s (%s)' % (kind, stripid(node.ref))
        elif kind in ('call','new'):
            (name,_,_) = splitmethodname(node.data)
            styles['fontname'] = 'courier'
            styles['label'] = name
        elif kind is not None and kind.startswith('op_'):
            styles['fontname'] = 'courier'
            styles['label'] = (node.data or kind)
        else:
            if node.ref is not None:
                styles['label'] = '%s (%s)' % (kind, stripid(node.ref))
        if highlight is not None and node.nid in highlight:
            styles['style'] = 'filled'
        out.write(h+' N%s [%s];\n' % (node.nid, qp(styles)))
    for child in scope.children:
        write_gv(out, child, highlight, level=level+1)
    if level == 0:
        for node in scope.walk():
            for (label,src) in node.inputs.items():
                if not label:
                    styles = {}
                elif label == 'cond':
                    styles = {'style': 'dotted', 'label': label}
                elif label == '_end':
                    styles = {'style': 'dashed', 'constraint': 'false'}
                elif label.startswith('_'):
                    continue
                else:
                    styles = {'label': label}
                out.write(h+' N%s -> N%s [%s];\n' % (src.nid, node.nid, qp(styles)))
    out.write(h+'}\n')
    return

def run_dot(graphs, type='svg'):
    args = ['dot', '-T'+type]
    print('run_dot: %r' % args, file=sys.stderr)
    data = io.StringIO()
    for graph in graphs:
        write_gv(data, graph.root, name=graph.name)
    p = Popen(args, stdin=PIPE, stdout=PIPE, encoding='utf-8')
    (stdout, _) = p.communicate(data.getvalue())
    a = []
    lines = []
    for line in stdout.splitlines():
        if line.startswith('<?'):
            if lines:
                a.append(''.join(lines))
                lines = []
            continue
        lines.append(line)
    if lines:
        a.append(''.join(lines))
    return a

def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-H] [-o output] [-n name] [-h nid] [graph ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'Ho:h:n:')
    except getopt.GetoptError:
        return usage()
    html = False
    output = sys.stdout
    highlight = None
    names = None
    for (k, v) in opts:
        if k == '-H': html = True
        elif k == '-o':
            output = open(v, 'w')
            html = v.endswith('.html')
        elif k == '-h': highlight = set(( int(nid) for nid in v.split(',') ))
        elif k == '-n': names = [v]
    if not args: return usage()

    graphs = []
    for path in args:
        for graph in get_graphs(path):
            if names and graph.name not in names: continue
            graphs.append(graph)

    if html:
        output.write('<!DOCTYPE html><html><body>\n')
        for data in run_dot(graphs):
            output.write('<div>\n')
            output.write(data)
            output.write('</div><hr>\n')
        output.write('</body>')
    else:
        for graph in graphs:
            write_gv(output, graph.root,
                     highlight=highlight, name=graph.name)

    output.close()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
