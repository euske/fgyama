#!/usr/bin/env python
import sys
import logging
from graphs import get_graphs, parsemethodname, stripid
from algos import Cons

def q(s):
    if s:
        return '"%s"' % s.replace('"',r'\"')
    else:
        return '""'

class Group:

    gid = 0

    def __init__(self, method):
        Group.gid += 1
        self.gid = Group.gid
        self.method = method
        self.children = []
        self.vin = None
        self.vout = None
        return

    def add(self, group):
        self.children.append(group)
        return

class Vertex:

    vid = 0

    def __init__(self, group):
        Vertex.vid += 1
        self.vid = Vertex.vid
        self.group = group
        self.linkto = []
        return

    def connect(self, vtx):
        self.linkto.append(vtx)
        return

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
    name2method = {}
    for path in args:
        logging.info(f'Loading: {path}...')
        for method in get_graphs(path):
            if method.style == 'initializer': continue
            methods.append(method)
            name2method[method.name] = method

    def trace(method, prevs, cc=None):
        logging.info(f'trace {method}')
        group = Group(method)
        vout = Vertex(group)
        group.vout = vout
        for vtx in prevs:
            vout.connect(vtx)
        edges = { 'in': set() }
        edgefunc = (lambda n0: ( n1 for (x,n1) in n0.inputs.items() if x.startswith('_') ))
        for n0 in topsort(method, edgefunc):
            if n0 in edges:
                p = edges[n0]
            else:
                p = set([vout])
            if n0.is_funcall() and (maxlevel == 0 or Cons.len(cc) < maxlevel):
                funcs = n0.data.split()
                a = []
                for name in funcs[:maxoverrides]:
                    if name not in name2method: continue
                    callee = name2method[name]
                    if cc is not None and callee in cc: continue
                    vtx = trace(callee, p, Cons(method, cc))
                    group.add(vtx.group)
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
        vin = Vertex(group)
        group.vin = vin
        for vtx in edges['in']:
            vin.connect(vtx)
        return vin

    groups = []
    for method in methods:
        # Filter "top-level" methods only which aren't called by anyone else.
        if method.callers: continue
        (klass,name,func) = parsemethodname(method.name)
        #if (name not in targets) and (method.name not in targets): continue
        vtx = trace(method, [])
        groups.append(vtx.group)
        #break

    outedges = []
    out.write(f'digraph {q(path)} {{\n')
    def f(group, level=1):
        h = '  '*level
        (klass,name,func) = parsemethodname(group.method.name)
        if group.children:
            out.write(h+f'subgraph {q("cluster_"+str(group.gid))} {{\n')
            out.write(h+f'  label={q(stripid(klass.name)+"."+name)};\n')
            vin = group.vin
            vout = group.vout
            out.write(h+f'  V{vin.vid} [label={q("enter")}];\n')
            out.write(h+f'  V{vout.vid} [label={q("exit")}];\n')
            for vtx in vin.linkto:
                outedges.append((vin,vtx))
            for vtx in vout.linkto:
                outedges.append((vout,vtx))
            for g in group.children:
                f(g, level+1)
            out.write(h+'}\n')
        else:
            vin = group.vin
            vout = group.vout
            out.write(h+f'V{vin.vid} [shape=box, label={q(name)}];\n')
            for vtx in vin.linkto:
                if vtx is not vout:
                    outedges.append((vin,vtx))
            for vtx in vout.linkto:
                if vtx is not vout:
                    outedges.append((vin,vtx))
        return
    for group in groups:
        f(group)
    for (v0,v1) in outedges:
        out.write(f'  V{v0.vid} -> V{v1.vid};\n')
    out.write('}\n')

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
