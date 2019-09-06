#!/usr/bin/env python
import sys
from interproc import IDFBuilder, Cons, clen
from getwords import splitmethodname, stripid

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
        return stripid(self.ref)

    def __repr__(self):
        return '<+%s:%s>' % (self.iid, self.ref)

    def connect(self, iref):
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
        return cpts

def trace(done, vtx, iref0=None, cc=None, todo=None):
    node = vtx.node
    ref = node.ref
    if ref is not None and ref[0] not in '%#':
        if ref[0] == '$':
            iref1 = IRef.get(cc, ref)
        else:
            iref1 = IRef.get(None, ref)
        yield (iref1, iref0)
        iref0 = iref1
        if vtx in done: return
        done[vtx] = iref1
        while todo is not None:
            done[todo.car] = iref1
            todo = todo.cdr
    elif vtx in done:
        iref1 = done[vtx]
        yield (iref1, iref0)
        return
    else:
        todo = Cons(vtx, todo)
    for (link,v,funcall) in vtx.inputs:
        if link.startswith('_'): continue
        if v.node.kind == 'output' and funcall is not None:
            if cc is None or funcall not in cc:
                for z in trace(done, v, iref0, Cons(funcall, cc), todo):
                    yield z
        elif node.kind == 'input' and funcall is not None:
            if cc is not None and cc.car is funcall:
                for z in trace(done, v, iref0, cc.cdr, todo):
                    yield z
        else:
            for z in trace(done, v, iref0, cc, todo):
                yield z
    return

# main
def main(argv):
    global debug
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-d] [-o output] [-M maxoverrides] [-f method] [graph ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'do:M:f:')
    except getopt.GetoptError:
        return usage()
    outpath = None
    maxoverrides = 1
    methods = set()
    for (k, v) in opts:
        if k == '-d': debug += 1
        elif k == '-o': outpath = v
        elif k == '-M': maxoverrides = int(v)
        elif k == '-f': methods.update(v.split(','))
    if not args: return usage()

    out = sys.stdout
    if outpath is not None:
        out = open(outpath, 'w')

    builder = IDFBuilder(maxoverrides=maxoverrides)
    for path in args:
        print('Loading: %r...' % path, file=sys.stderr)
        builder.load(path)
    builder.run()
    print('Read: %d sources, %d graphs, %d funcalls, %d IPVertexes' %
          (len(builder.srcmap), len(builder.graphs),
           sum( len(a) for a in builder.funcalls.values() ),
           len(builder.vtxs)),
          file=sys.stderr)

    # Enumerate all the flows.
    irefs = set()
    nlinks = 0
    for graph in builder.graphs:
        (name,_,_) = splitmethodname(graph.name)
        if methods and (name not in methods) and (graph.name not in methods): continue
        if graph.callers: continue
        print('graph:', graph.name, file=sys.stderr)
        done = {}
        for node in graph:
            if not node.inputs: continue
            mat = trace(done, builder.vtxs[node])
            for (iref0, iref1) in mat:
                assert iref0 is not None
                if iref1 is None: continue
                if iref0 == iref1: continue
                if debug:
                    print(iref0, '->', iref1)
                iref0.connect(iref1)
                irefs.add(iref0)
                irefs.add(iref1)
                nlinks += 1
    print('irefs:', len(irefs), file=sys.stderr)
    print('links:', nlinks, file=sys.stderr)

    # Discover strong components.
    cpts = IRefComponent.fromitems(irefs)
    print('cpts:', len(cpts), file=sys.stderr)

    # Discover the most significant edges.
    incount = {}
    incoming = {}
    outcount = {}
    outgoing = {}
    for cpt in cpts:
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
    for cpt in cpts:
        if not cpt.linkfrom:
            count_forw(cpt)
        if not cpt.linkto:
            count_bacj(cpt)

    maxcount = 0
    for cpt0 in cpts:
        for cpt1 in cpt0.linkto:
            count = incoming[cpt0] + outgoing[cpt1]
            if maxcount < count:
                maxcount = count
    print('maxcount:', maxcount, file=sys.stderr)

    # Traverse the edges.
    maxlinks = set()
    def trav_forw(cpt0):
        if not cpt0.linkto: return
        mc = max( outgoing[cpt1] for cpt1 in cpt0.linkto )
        for cpt1 in cpt0.linkto:
            if outgoing[cpt1] == mc:
                maxlinks.add((cpt0, cpt1))
                trav_forw(cpt1)
        return
    def trav_back(cpt1):
        if not cpt1.linkfrom: return
        mc = max( incoming[cpt0] for cpt0 in cpt1.linkfrom )
        for cpt0 in cpt1.linkfrom:
            if incoming[cpt0] == mc:
                maxlinks.add((cpt0, cpt1))
                trav_back(cpt0)
        return
    for cpt0 in cpts:
        for cpt1 in cpt0.linkto:
            count = incoming[cpt0] + outgoing[cpt1]
            if count == maxcount:
                maxlinks.add((cpt0, cpt1))
                trav_back(cpt0)
                trav_forw(cpt1)
    print('maxlinks:', len(maxlinks), file=sys.stderr)

    maxcpts = set( cpt0 for (cpt0,_) in maxlinks )
    maxcpts.update( cpt1 for (_,cpt1) in maxlinks )
    print('maxcpts:', len(maxcpts), file=sys.stderr)

    # Generate a trimmed graph.
    out.write('digraph %s {\n' % q(name))
    for cpt in maxcpts:
        out.write(' N%d [label=%s, fontname="courier"];\n' %
                  (cpt.cid, q(str(cpt))))
    for (cpt0,cpt1) in maxlinks:
        out.write(' N%d -> N%d;\n' % (cpt0.cid, cpt1.cid))
    out.write('}\n')

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
