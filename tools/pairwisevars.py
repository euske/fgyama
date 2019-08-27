#!/usr/bin/env python
import sys
from interproc import IDFBuilder, Cons, clen

debug = 0

def trace(done, ccid, vtx, iref0=None, cc=None):
    node = vtx.node
    ref = node.ref
    if ref is not None and ref[0] not in '%#':
        if ref[0] == '$':
            if cc in ccid:
                z = ccid[cc]
            else:
                z = ccid[cc] = len(ccid)+1
            iref1 = '%s:%s' % (z, ref)
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
        print('usage: %s [-d] [-o output] [-M maxoverrides] [graph ...]' % argv[0])
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

    ccid = {}
    for graph in builder.graphs:
        if '<clinit>' in graph.name: continue
        if graph.callers: continue
        done = set()
        for node in graph:
            if not node.inputs: continue
            mat = trace(done, ccid, builder.vtxs[node])
            for (iref0, iref1) in mat:
                if iref0 == iref1: continue
                print(iref0, iref1)
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
