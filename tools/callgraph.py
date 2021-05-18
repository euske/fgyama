#!/usr/bin/env python
import sys
import logging
from graphs import get_graphs, parsemethodname, Cons, clen, stripid

def q(s):
    if s:
        return '"%s"' % s.replace('"',r'\"')
    else:
        return '""'

def topsort(nodes):
    incoming = { n0: [] for n0 in nodes }
    for n0 in incoming:
        for (label,n1) in n0.inputs.items():
            if label.startswith('_'): continue
            incoming[n1].append(n0)
    out = []
    while incoming:
        for (n0,z) in incoming.items():
            if not z: break
        else:
            raise ValueError('cycle')
        del incoming[n0]
        out.append(n0)
        for (label,n1) in n0.inputs.items():
            if label.startswith('_'): continue
            incoming[n1].remove(n0)
    return out

class Vertex:
    vid = 0
    @classmethod
    def newvid(klass):
        klass.vid += 1
        return klass.vid

# main
def main(argv):
    import fileinput
    import getopt
    def usage():
        print(f'usage: {argv[0]} [-d] [-o output] [-f method] [-M maxoverrides] [-L maxlevel] [graph ...]')
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'do:f:M:L:')
    except getopt.GetoptError:
        return usage()
    level = logging.INFO
    outpath = None
    maxoverrides = 1
    maxlevel = 5
    targets = {'main'}
    for (k, v) in opts:
        if k == '-d': level = logging.DEBUG
        elif k == '-o': outpath = v
        elif k == '-f': targets.update(v.split(','))
        elif k == '-M': maxoverrides = int(v)
        elif k == '-L': maxlevel = int(v)
    if not args: return usage()

    logging.basicConfig(format='%(asctime)s %(levelname)s %(message)s', level=level)

    out = sys.stdout
    if outpath is not None:
        out = open(outpath, 'w')

    methods = []
    gid2method = {}
    for path in args:
        logging.info(f'Loading: {path}...')
        for method in get_graphs(path):
            if method.style == 'initializer': continue
            methods.append(method)
            gid2method[method.name] = method

    outedges = []
    def trace(method, prevs, cc=None):
        logging.info(f'trace {method}')
        h = '  '*clen(cc)
        (klass,name,func) = parsemethodname(method.name)
        vout = Vertex.newvid()
        out.write(h+f'subgraph {q("cluster_"+str(vout))} {{\n')
        out.write(h+f'  label={q(stripid(klass.name)+"."+name)};\n')
        out.write(h+f'  V{vout} [label={q("exit "+name)}];\n')
        for vtx in prevs:
            outedges.append((vout, vtx))
        edges = { 'in': set() }
        for n0 in topsort(method):
            if n0 in edges:
                p = edges[n0]
            else:
                p = set([vout])
            if n0.is_funcall() and clen(cc) < maxlevel:
                funcs = n0.data.split(' ')
                a = []
                for gid in funcs[:maxoverrides]:
                    if gid not in gid2method: continue
                    callee = gid2method[gid]
                    if cc is not None and callee in cc: continue
                    vtx = trace(callee, p, Cons(method, cc))
                    a.append(vtx)
                if a:
                    p = set(a)
            if n0.inputs:
                for (label,n1) in n0.inputs.items():
                    if label.startswith('_'): continue
                    if n1 in edges:
                        edges[n1].update(p)
                    else:
                        edges[n1] = p.copy()
            else:
                edges['in'].update(p)
        vin = Vertex.newvid()
        out.write(h+f'  V{vin} [label={q("enter "+name)}];\n')
        out.write(h+f'}}\n')
        for vtx in edges['in']:
            outedges.append((vin, vtx))
        return vin

    out.write(f'digraph {q(path)} {{\n')
    for method in methods:
        # Filter "top-level" methods only which aren't called by anyone else.
        if method.callers: continue
        (klass,name,func) = parsemethodname(method.name)
        if (name not in targets) and (method.name not in targets): continue
        trace(method, [])
        break
    for (v0,v1) in outedges:
        out.write(f'V{v0} -> V{v1};\n')
    out.write(f'}}\n')

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
