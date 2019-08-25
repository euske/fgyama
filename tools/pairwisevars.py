#!/usr/bin/env python
import sys
from interproc import IDFBuilder, Cons, clen

debug = 0

def is_ref(ref):
    return (ref is not None and ref[0] not in '%#')

def trace(done, vtx, ref0=None, cc=None):
    node = vtx.node
    if is_ref(node.ref):
        ref1 = (cc, node.ref)
        if ref0 is not None:
            yield (ref0, ref1)
        ref0 = ref1
    if vtx in done: return
    done.add(vtx)
    for (link,v,funcall) in vtx.outputs:
        if link.startswith('_') and link != '_end': continue
        if node.kind == 'input':
            if cc is None or funcall not in cc:
                for z in trace(done, v, ref0, Cons(funcall, cc)):
                    yield z
        elif node.kind == 'output':
            if cc is not None and cc.car is funcall:
                for z in trace(done, v, ref0, cc.cdr):
                    yield z
        else:
            for z in trace(done, v, ref0, cc):
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
    def get(cc):
        if cc in ccid:
            z = ccid[cc]
        else:
            z = ccid[cc] = len(ccid)+1
        return z
    for graph in builder.graphs:
        if graph.callers: continue
        for node in graph.ins:
            mat = trace(set(), builder.vtxs[node])
            for ((cc0,ref0),(cc1,ref1)) in mat:
                if cc0 == cc1 and ref0 == ref1: continue
                print(get(cc0),ref0, get(cc1),ref1)
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
