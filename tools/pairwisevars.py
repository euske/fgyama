#!/usr/bin/env python
import sys
from interproc import IDFBuilder, Cons, clen
from getwords import splitmethodname

debug = 0

def mkgraph(links):
    linkto = {}
    linkfrom = {}
    for (iref0,iref1) in links:
        if iref0 in linkto:
            a = linkto[iref0]
        else:
            a = linkto[iref0] = []
        if iref1 not in a:
            a.append(iref1)
        if iref1 in linkfrom:
            a = linkfrom[iref1]
        else:
            a = linkfrom[iref1] = []
        if iref0 not in a:
            a.append(iref0)
    return (linkto, linkfrom)

def trace(done, ccid, vtx, iref0=None, cc=None):
    node = vtx.node
    ref = node.ref
    if ref is not None and ref[0] not in '%#':
        if ref[0] == '$':
            if ref in ccid:
                d = ccid[ref]
            else:
                d = ccid[ref] = {}
            if cc in d:
                z = d[cc]
            else:
                z = d[cc] = len(d)+1
            iref1 = '+%s:%s' % (z, ref)
        else:
            iref1 = ref
        if iref0 is not None:
            yield (iref0, iref1)
        iref0 = iref1
    if vtx in done: return
    done.add(vtx)
    for (link,v,funcall) in vtx.outputs:
        if link.startswith('_') and link != '_end': continue
        if v.node.kind == 'input' and funcall is not None:
            if cc is None or funcall not in cc:
                for z in trace(done, ccid, v, iref0, Cons(funcall, cc)):
                    yield z
        elif node.kind == 'output' and funcall is not None:
            if cc is not None and cc.car is funcall:
                for z in trace(done, ccid, v, iref0, cc.cdr):
                    yield z
        else:
            for z in trace(done, ccid, v, iref0, cc):
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
    ccid = {}
    links = []
    irefs = set()
    for graph in builder.graphs:
        (name,_,_) = splitmethodname(graph.name)
        if methods and name not in methods: continue
        if graph.callers: continue
        done = set()
        for node in graph:
            if not node.inputs: continue
            mat = trace(done, ccid, builder.vtxs[node])
            for (iref0, iref1) in mat:
                if iref0 == iref1: continue
                if debug:
                    print(iref0, '->', iref1)
                links.append((iref0, iref1))
                irefs.add(iref0)
                irefs.add(iref1)
    print('irefs:', len(irefs))
    print('links:', len(links))

    # Discover strong components.
    (linkto, linkfrom) = mkgraph(links)
    S = []
    P = []
    C = []
    sc = {}
    po = {}
    def visit(v):
        if v in po: return
        po[v] = len(po)
        S.append(v)
        P.append(v)
        if v in linkto:
            for w in linkto[v]:
                assert v is not w
                if w not in po:
                    visit(w)
                elif w not in sc:
                    # assert(po[w] < po[v])
                    i = len(P)
                    while po[w] < po[P[i-1]]:
                        i -= 1
                    P[i:] = []
        if P[-1] is v:
            c = []
            while S:
                z = S.pop()
                c.append(z)
                if z is v: break
            assert P.pop() is v
            for v in c:
                sc[v] = c
            C.append(c)
    for iref in irefs:
        visit(iref)

    # Compute maximum spanning trees.
    queue = [ iref for iref in irefs if iref not in linkto ]
    maxdist = { iref:0 for iref in queue }
    maxprev = { iref:None for iref in queue }
    while queue:
        iref1 = queue.pop(0)
        if iref1 not in linkfrom: continue
        assert iref1 in maxdist
        dist = maxdist[iref1] + 1
        assert iref1 in sc
        c = sc[iref1]
        for iref1 in c:
            for iref0 in linkfrom[iref1]:
                assert iref0 != iref1
                if sc[iref0] is c: continue
                if iref0 in maxdist and dist <= maxdist[iref0]: continue
                maxdist[iref0] = dist
                maxprev[iref0] = iref1
                queue.append(iref0)
                if (len(maxdist) % 1000) == 0:
                    print('queue:', len(queue), len(maxdist))

    for iref in irefs:
        if iref in linkfrom: continue
        a = []
        while iref in maxprev:
            a.append(iref)
            iref = maxprev[iref]
        print(a)

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
