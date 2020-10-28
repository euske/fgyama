#!/usr/bin/env python
import sys
from graphs import IDFBuilder, Cons, clen, splitmethodname, stripid

debug = 0
MAXNAMES = 5

def q(s):
    if s:
        return '"%s"' % s.replace('"',r'\"')
    else:
        return '""'

class IRef:

    ccid = {}

    @classmethod
    def get(klass, cc, ref):
        if cc in klass.ccid:
            d = klass.ccid[cc]
        else:
            d = klass.ccid[cc] = {}
        if ref in d:
            iref = d[ref]
        else:
            iid = len(d)+1
            iref = d[ref] = klass(cc, ref, iid)
        return iref

    def __init__(self, cc, ref, iid):
        self.cc = cc
        self.ref = ref
        self.iid = iid
        self.linkto = []
        self.linkfrom = []
        return

    def __str__(self):
        return (stripid(self.ref) or self.ref)

    def __repr__(self):
        return f'<+{self.iid}:{self.ref}>'

    def connect(self, iref):
        if iref in self.linkto: return
        assert self not in iref.linkfrom
        self.linkto.append(iref)
        iref.linkfrom.append(self)
        return

class IRefComponent:

    def __init__(self, cid, irefs=None):
        if irefs is None:
            irefs = []
        self.cid = cid
        self.irefs = irefs
        self.linkto = set()
        self.linkfrom = set()
        return

    def __repr__(self):
        names = set( str(iref) for iref in self.irefs )
        if MAXNAMES < len(names):
            names = list(names)[:MAXNAMES] + ['...']
        return '[%s]' % ', '.join(names)

    def __len__(self):
        return len(self.irefs)

    def __iter__(self):
        return iter(self.irefs)

    def add(self, iref):
        self.irefs.append(iref)
        return

    def fixate(self, sc):
        for iref0 in self.irefs:
            for iref1 in iref0.linkto:
                cpt = sc[iref1]
                if cpt is self: continue
                self.linkto.add(cpt)
                cpt.linkfrom.add(self)
        return

    @classmethod
    def fromitems(klass, irefs):
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
        for iref in irefs:
            if iref not in sc:
                visit(iref)
        for c in cpts:
            c.fixate(sc)
        return (sc, cpts)

# trace()
IGNORED = {
    'value', 'valueset',
    'op_assign', 'op_prefix', 'op_infix', 'op_postfix',
    'op_typecast', 'op_typecheck', 'op_iter',
    'ref_array', 'ref_field',
}
def trace(ctx2iref, v1, iref0=None, cc=None):
    k = (cc,v1)
    if k in ctx2iref:
        iref1 = ctx2iref[k]
        if iref0 is not None:
            if debug:
                print(f'{clen(cc)} {iref1!r} -> {iref0!r}')
            yield (iref1, iref0)
        return
    n1 = v1.node
    ref1 = n1.ref
    if n1.kind in IGNORED:
        iref1 = iref0
    elif ref1 is None or ref1.startswith('#'):
        iref1 = iref0
    else:
        if ref1[0] == '$':
            iref1 = IRef.get(cc, ref1)
        else:
            iref1 = IRef.get(None, ref1)
        if iref0 is not None:
            if debug:
                print(f'{clen(cc)} {iref1!r} -> {iref0!r}')
            yield (iref1, iref0)
        ctx2iref[k] = iref1
    for (link,v2,funcall) in v1.inputs:
        if link.startswith('_'): continue
        n2 = v2.node
        if n2.kind == 'output' and funcall is not None:
            if cc is None or funcall not in cc:
                yield from trace(ctx2iref, v2, iref1, Cons(funcall, cc))
        elif n1.kind == 'input' and funcall is not None:
            if cc is not None and cc.car is funcall:
                yield from trace(ctx2iref, v2, iref1, cc.cdr)
        else:
            yield from trace(ctx2iref, v2, iref1, cc)
    return

# main
def main(argv):
    global debug
    import fileinput
    import getopt
    def usage():
        print(f'usage: {argv[0]} [-d] [-o output] [-M maxoverrides] [-r ratio] [-f method] [graph ...]')
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'do:M:r:f:')
    except getopt.GetoptError:
        return usage()
    outpath = None
    maxoverrides = 1
    ratio = 0.9
    maxfan = 5
    methods = set()
    for (k, v) in opts:
        if k == '-d': debug += 1
        elif k == '-o': outpath = v
        elif k == '-M': maxoverrides = int(v)
        elif k == '-r': ratio = float(v)
        elif k == '-f': methods.update(v.split(','))
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

    # Enumerate all the flows.
    irefs = set()
    ctx2iref = {}
    nlinks = 0
    for method in builder.methods:
        # Filter "top-level" methods only which aren't called by anyone else.
        if method.callers: continue
        (name,_,_) = splitmethodname(method.name)
        if methods and (name not in methods) and (method.name not in methods): continue
        print(f'method: {method.name}', file=sys.stderr)
        for node in method:
            vtx = builder.vtxs[node]
            if vtx.outputs: continue
            # Filter the output nodes only.
            for (iref1, iref0) in trace(ctx2iref, vtx):
                # iref1 -> iref0
                assert iref1 is not None
                if iref0 is None: continue
                if iref0 == iref1: continue
                iref1.connect(iref0)
                irefs.add(iref0)
                irefs.add(iref1)
                nlinks += 1
    print(f'irefs: {len(irefs)}', file=sys.stderr)
    print(f'links: {nlinks}', file=sys.stderr)

    # Discover strong components.
    (ref2cpt, allcpts) = IRefComponent.fromitems(irefs)
    print(f'allcpts: {len(allcpts)}', file=sys.stderr)

    # Discover the most significant edges.
    incount = {}
    incoming = {}
    outcount = {}
    outgoing = {}
    for cpt in allcpts:
        incount[cpt] = len(cpt.linkfrom)
        incoming[cpt] = 0 if cpt.linkfrom else 1
        outcount[cpt] = len(cpt.linkto)
        outgoing[cpt] = 0 if cpt.linkto else 1
    def count_forw(cpt0):
        for cpt1 in cpt0.linkto:
            assert cpt0 is not cpt1
            incount[cpt1] -= 1
            incoming[cpt1] += incoming[cpt0]
            if incount[cpt1] == 0:
                count_forw(cpt1)
        return
    def count_bacj(cpt1):
        for cpt0 in cpt1.linkfrom:
            assert cpt0 is not cpt1
            outcount[cpt0] -= 1
            outgoing[cpt0] += outgoing[cpt1]
            if outcount[cpt0] == 0:
                count_bacj(cpt0)
        return
    for cpt in allcpts:
        if not cpt.linkfrom:
            count_forw(cpt)
        if not cpt.linkto:
            count_bacj(cpt)

    maxcount = 0
    for cpt0 in allcpts:
        for cpt1 in cpt0.linkto:
            count = incoming[cpt0] + outgoing[cpt1]
            if maxcount < count:
                maxcount = count
        if len(cpt0) < 2: continue
        if debug:
            print(f'cpt: {cpt0}', file=sys.stderr)
            for iref in cpt0:
                print(f'  {iref!r}', file=sys.stderr)
    print(f'maxcount: {maxcount}', file=sys.stderr)

    # Traverse the edges.
    maxlinks = set()
    def trav_forw(cpt0):
        linkto = sorted(cpt0.linkto, key=lambda cpt1: outgoing[cpt1], reverse=True)
        for cpt1 in linkto[:maxfan]:
            maxlinks.add((cpt0, cpt1))
            trav_forw(cpt1)
        return
    def trav_back(cpt1):
        linkfrom = sorted(cpt1.linkfrom, key=lambda cpt0: incoming[cpt0], reverse=True)
        for cpt0 in linkfrom[:maxfan]:
            maxlinks.add((cpt0, cpt1))
            trav_back(cpt0)
        return
    for cpt0 in allcpts:
        for cpt1 in cpt0.linkto:
            count = incoming[cpt0] + outgoing[cpt1]
            if ratio*maxcount <= count:
                maxlinks.add((cpt0, cpt1))
                trav_back(cpt0)
                trav_forw(cpt1)
    print(f'maxlinks: {len(maxlinks)}', file=sys.stderr)

    maxcpts = set( cpt0 for (cpt0,_) in maxlinks )
    maxcpts.update( cpt1 for (_,cpt1) in maxlinks )
    print(f'maxcpts: {len(maxcpts)}', file=sys.stderr)

    # Generate a trimmed graph.
    out.write(f'digraph {q(path)} {{\n')
    for cpt in maxcpts:
        out.write(f' N{cpt.cid} [label={q(str(cpt))}, fontname="courier"];\n')
    for (cpt0,cpt1) in maxlinks:
        out.write(f' N{cpt0.cid} -> N{cpt1.cid};\n')
    out.write('}\n')

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
