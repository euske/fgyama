#!/usr/bin/env python
import sys
from interproc import IDFBuilder, Cons
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

ALLOWED = {
    None,
    'ref_var', 'assign_var', 'op_assign',
    'ref_field', 'assign_field',
    'receive', 'input', 'output',
    'join', 'begin', 'end', 'repeat', 'return',
}

LINK = {
    'op_assign':['R'],
    'ref_field':[''], 'assign_field':[''],
    'join':['true','false'], 'end':[''], 'repeat':[''],
}

def trace(v1, ref0=None, done=None):
    if done is not None and v1 in done: return
    done = Cons(v1, done)
    n1 = v1.node
    ref1 = n1.ref
    if ref1 is not None:
        if ref1.startswith('%'): return
        if not ref1.startswith('#'):
            if ref0 is not None:
                yield (ref0, ref1)
                return
            ref0 = ref1
    kind = n1.kind
    if kind not in ALLOWED: return
    if kind == 'op_assign' and n1.data != '=': return
    links = LINK.get(kind)
    for (link,v2,_) in v1.inputs:
        if link.startswith('_'): continue
        if links is not None and link not in links: continue
        yield from trace(v2, ref0, done)
    return

# main
def main(argv):
    global debug
    import fileinput
    import getopt
    def usage():
        print(f'usage: {argv[0]} [-d] [-o output] [-M maxoverrides] [graph ...]')
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'do:M:')
    except getopt.GetoptError:
        return usage()
    outpath = None
    maxoverrides = 1
    for (k, v) in opts:
        if k == '-d': debug += 1
        elif k == '-o': outpath = v
        elif k == '-M': maxoverrides = int(v)
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
    links = set()
    for method in builder.methods:
        (name,_,_) = splitmethodname(method.name)
        if debug:
            print(f'method: {method.name}', file=sys.stderr)
        for node in method:
            if not node.inputs: continue
            for (ref1, ref0) in trace(builder.vtxs[node]):
                if ref1 == ref0: continue
                links.add((ref1, ref0))
                if debug:
                    print(f'{ref1!r} <- {ref0!r}')
    print(f'links: {len(links)}', file=sys.stderr)

    srcs = {}
    dsts = {}
    for (ref1, ref0) in links:
        name1 = stripid(ref1)
        name0 = stripid(ref0)
        if name1 == name0: continue
        #print(name1, '=', name0)
        if name1 in srcs:
            a = srcs[name1]
        else:
            a = srcs[name1] = set()
        a.add(name0)
        if name0 in dsts:
            a = dsts[name0]
        else:
            a = dsts[name0] = set()
        a.add(name1)

    for (name,a) in srcs.items():
        if len(a) == 1: continue
        print(name, '=', a)
    print()
    for (name,a) in dsts.items():
        if len(a) == 1: continue
        print(a, '=', name)
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
