#!/usr/bin/env python
import io
import sys
import logging
from subprocess import Popen, PIPE
from graphs import get_graphs, parserefname, parsemethodname, DFType

def q(s):
    if s:
        return '"%s"' % s.replace('"',r'\"')
    else:
        return '""'

def qp(props):
    return ', '.join( f'{k}={q(v)}' for (k,v) in props.items() )

def r(s):
    return ''.join( c for c in s if c == '_' or c.isalnum() )

def write_gv(out, scope, highlight=None, level=0, name=None):
    h = ' '*level
    if name is None:
        name = scope.name.split('.')[-1]
    if level == 0:
        out.write(f'digraph {q(name)} {{\n')
    else:
        out.write(h+f'subgraph {q("cluster_"+name)} {{\n')
    out.write(h+f' label={q(name)};\n')
    nodes = {-1:[], 0:[], 1:[]}
    for node in scope.nodes:
        rank = 0
        kind = node.kind
        styles = { 'label':kind }
        if kind in ('join','begin','end','repeat','case'):
            styles['shape'] = 'diamond'
            if node.ref is not None:
                styles['label'] = f'{kind} ({parserefname(node.ref)})'
        elif kind in ('value', 'valueset'):
            styles['shape'] = 'box'
            styles['fontname'] = 'courier'
            styles['label'] = repr(node.data)
        elif kind in ('input','output','receive'):
            if node.ref is not None:
                styles['label'] = f'{kind} ({parserefname(node.ref)})'
            if kind == 'input':
                rank = -1
            elif kind == 'output':
                rank = +1
        elif kind in ('passin','passout'):
            styles['style'] = 'dotted'
            if kind == 'passin':
                rank = -1
            elif kind == 'passout':
                rank = +1
        elif kind == 'new':
            (_, klass) = DFType.parse(node.ntype)
            styles['shape'] = 'box'
            styles['style'] = 'rounded'
            styles['fontname'] = 'courier'
            styles['label'] = f'new {klass.name}'
        elif kind == 'call':
            (klass,name,func) = parsemethodname(node.data)
            styles['shape'] = 'box'
            styles['style'] = 'rounded'
            styles['fontname'] = 'courier'
            styles['label'] = f'{name}()'
        elif kind is not None and kind.startswith('op_'):
            styles['fontname'] = 'courier'
            styles['label'] = (node.data or kind)
        elif kind is not None:
            if node.ref is not None:
                styles['label'] = f'{kind} ({parserefname(node.ref)})'
        else:
            if node.ref is not None:
                styles['label'] = f'({parserefname(node.ref)})'
        if highlight is not None and node.nid in highlight:
            styles['style'] = 'filled'
        nodes[rank].append((node, styles))
    for (rank,a) in nodes.items():
        if not a: continue
        if rank < 0:
            out.write(h+'subgraph { rank=source;\n')
        elif 0 < rank:
            out.write(h+'subgraph { rank=sink;\n')
        for (node,styles) in a:
            out.write(h+f' N{r(node.nid)} [{qp(styles)}];\n')
        if rank != 0:
            out.write(h+'}\n')
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
                out.write(h+f' N{r(src.nid)} -> N{r(node.nid)} [{qp(styles)}];\n')
    out.write(h+'}\n')
    return

def run_dot(methods, type='svg'):
    args = ['dot', '-T'+type]
    logging.info(f'run_dot: {args!r}')
    data = io.StringIO()
    for method in methods:
        if method.root is None: continue
        (klass,name,func) = parsemethodname(method.name)
        write_gv(data, method.root, name=name)
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
        print(f'usage: {argv[0]} [-H] [-o output] [-n name] [-h nid] [graph ...]')
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
    if not args:
        args.append('-')

    methods = []
    for path in args:
        for method in get_graphs(path):
            if names and method.name not in names: continue
            methods.append(method)

    if html:
        output.write('<!DOCTYPE html><html><body>\n')
        for data in run_dot(methods):
            output.write('<div>\n')
            output.write(data)
            output.write('</div><hr>\n')
        output.write('</body>')
    else:
        for method in methods:
            if method.root is None: continue
            (klass,name,func) = parsemethodname(method.name)
            write_gv(output, method.root,
                     highlight=highlight, name=f'{klass}.{name}')

    output.close()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
