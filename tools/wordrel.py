#!/usr/bin/env python
import sys
from interproc import IDFBuilder, Cons, clen
from getwords import splitmethodname, stripid, splitwords

debug = 0

def count(d, v):
    if v not in d:
        d[v] = 0
    d[v] += 1
    return

def group(d, f):
    t = {}
    for (k,v) in d.items():
        (k1,k2) = f(k)
        if k1 in t:
            a = t[k1]
        else:
            a = t[k1] = {}
        a[k2] = a.get(k2, 0) + v
    r = [ (sum(v.values()), k,
           sorted(v.items(), key=lambda x:x[1], reverse=True))
          for (k,v) in t.items() ]
    return sorted(r, reverse=True)

class Node:

    def __init__(self, name):
        self.name = stripid(name)
        self.linkto = []
        return

    def __repr__(self):
        return f'<{self.name}>'

    def link(self, node):
        self.linkto.append(node)
        return

class Component:

    def __init__(self, cid, nodes=None):
        self.cid = cid
        self.nodes = nodes or []
        self.linkto = set()
        self.linkfrom = set()
        return

    def __repr__(self):
        return f'<{self.nodes}>'

    def __len__(self):
        return len(self.nodes)

    def __iter__(self):
        return iter(self.nodes)

    def add(self, node):
        self.nodes.append(node)
        return

    def fixate(self, sc):
        for node0 in self.nodes:
            for node1 in node0.linkto:
                cpt = sc[node1]
                if cpt is self: continue
                self.linkto.add(cpt)
                cpt.linkfrom.add(self)
        return

    @classmethod
    def fromnodes(klass, nodes):
        S = []
        P = []
        sc = {}
        po = {}
        cpts = []
        def visit(v0):
            if v0 in po: return
            po[v0] = len(po)
            S.append(v0)
            P.append(v0)
            for v in v0.linkto:
                if v not in po:
                    visit(v)
                elif v not in sc:
                    # assert(po[w] < po[v0])
                    i = len(P)
                    while po[v] < po[P[i-1]]:
                        i -= 1
                    P[i:] = []
            if P[-1] is v0:
                c = klass(len(cpts)+1)
                while S:
                    v = S.pop()
                    c.add(v)
                    if v is v0: break
                assert P.pop() is v0
                for v in c:
                    sc[v] = c
                cpts.append(c)
        for node in nodes:
            if node not in sc:
                visit(node)
        for c in cpts:
            c.fixate(sc)
        return (sc, cpts)

def f(n):
    return f'<{n.nid}({n.kind})>'

def dump(vtxs, method):
    for node in method:
        v0 = vtxs[node]
        for (link,v1,_) in v0.inputs:
            if link.startswith('_'): continue
            print(f(node), stripid(node.ref or '') or '-', link or '<-', f(v1.node))
    print()
    return

EQUIV_KIND = {
    None,
    'ref_var', 'assign_var', 'op_assign',
    'ref_field', 'assign_field',
    'receive', 'input', 'output',
    'join', 'begin', 'end', 'repeat', 'return',
}
EQUIV_LINK = {
    'op_assign':['R'],
    'ref_field':[''], 'assign_field':[''],
    'join':['true','false'], 'end':[''], 'repeat':[''],
}
def check_equiv(n):
    kind = n.kind
    if kind not in EQUIV_KIND: return None
    if kind == 'op_assign' and n.data != '=': return None
    return EQUIV_LINK.get(kind, [])

def check_any(n):
    return []

check = check_equiv

def trace(v1, ref0=None, done=None):
    if done is not None and v1 in done: return
    done = Cons(v1, done)
    n1 = v1.node
    ref1 = n1.ref
    if ref1 is not None:
        if ref1.startswith('%'): return
        if not ref1.startswith('#'):
            if ref0 is not None:
                yield (ref0, ref1, done)
                return
            ref0 = ref1
    links = check(n1)
    if links is None: return
    for (link,v2,_) in v1.inputs:
        if link.startswith('_'): continue
        if links and link not in links: continue
        yield from trace(v2, ref0, done)
    return

# main
def main(argv):
    global debug
    global check
    import fileinput
    import getopt
    def usage():
        print(f'usage: {argv[0]} [-d] [-o output] [-M maxoverrides] [-E] [graph ...]')
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'do:M:E')
    except getopt.GetoptError:
        return usage()
    outpath = None
    maxoverrides = 1
    minlen = 2
    check = check_equiv
    for (k, v) in opts:
        if k == '-d': debug += 1
        elif k == '-o': outpath = v
        elif k == '-M': maxoverrides = int(v)
        elif k == '-E': check = check_equiv
        elif k == '-A': check = check_any
    if not args: return usage()

    out = sys.stdout
    if outpath is not None:
        out = open(outpath, 'w')

    builder = IDFBuilder(maxoverrides=maxoverrides)
    for path in args:
        print(f'Loading: {path}...', file=sys.stderr)
        builder.load(path)
    builder.run()
    nfuncalls = sum( len(a) for a in builder.funcalls.values() )
    print(f'Read: {len(builder.srcmap)} sources, {len(builder.methods)} methods, {nfuncalls} funcalls, {len(builder.vtxs)} IPVertexes',
          file=sys.stderr)

    # Enumerate all the assignments.
    links = {}
    for method in builder.methods:
        (name,_,_) = splitmethodname(method.name)
        if debug:
            print(f'method: {method.name}', file=sys.stderr)
        for node in method:
            if not node.inputs: continue
            for (ref1, ref0, chain) in trace(builder.vtxs[node]):
                if ref1 == ref0: continue
                k = (ref1, ref0)
                if k in links and clen(links[k]) <= clen(chain): continue
                links[k] = chain
                if debug:
                    print(f'{ref1!r} <- {ref0!r}')
    print(f'links: {len(links)}', file=sys.stderr)

    srcs = {}
    dsts = {}
    for ((ref1, ref0), chain) in links.items():
        if ref1 == ref0: continue
        #print(ref1, '=', ref0, [ v.node.kind for v in chain ])
        if ref1 in srcs:
            a = srcs[ref1]
        else:
            a = srcs[ref1] = set()
        a.add(ref0)
        if ref0 in dsts:
            a = dsts[ref0]
        else:
            a = dsts[ref0] = set()
        a.add(ref1)
    print()

    nodes = {}
    def getnode(ref):
        if ref in nodes:
            n = nodes[ref]
        else:
            n = nodes[ref] = Node(ref)
        return n
    # ref = {src, ...} :: ref is supertype of srcs: src -> ref.
    for (ref,a) in srcs.items():
        if len(a) == 1: continue
        n = getnode(ref)
        for src in a:
            getnode(src).link(n)
    # {dst, ...} = ref :: ref is supertype of dsts: dst -> ref.
    for (ref,a) in dsts.items():
        if len(a) == 1: continue
        n = getnode(ref)
        for dst in a:
            getnode(dst).link(n)

    (sc, cpts) = Component.fromnodes(nodes.values())
    def disp(c, level=0):
        print('  '*level, c)
        for s in c.linkfrom:
            assert c in s.linkto
            disp(s, level+1)
        return
    for c in cpts:
        if c.linkto: continue
        disp(c)
    return

    for (ref,a) in srcs.items():
        a = set(map(stripid, a))
        if len(a) == 1: continue
        print(ref, '=', a)
    print()
    for (ref,a) in dsts.items():
        a = set(map(stripid, a))
        if len(a) == 1: continue
        print(a, '=', ref)
    print()

    pairs = []
    for (name,a) in srcs.items():
        if len(a) < 2: continue
        if name not in dsts: continue
        b = dsts[name]
        if len(b) < 2: continue
        pairs.append((name, a, b))
    pairs.sort(key=lambda x:len(x[1])*len(x[2]), reverse=True)
    for (name,a,b) in pairs:
        print(name, a, b)

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
