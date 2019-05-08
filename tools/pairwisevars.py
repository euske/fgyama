#!/usr/bin/env python
import sys
import re
from graph2idf import is_funcall, Cons, IDFBuilder

IGNORED = frozenset([
    None, 'ref', 'assign', 'receive',
    'begin', 'end', 'repeat'])

def getfeat(label, n1):
    if n1.kind in IGNORED:
        return None
    elif n1.data is None:
        return '%s:%s' % (label, n1.kind)
    elif is_funcall(n1):
        (data,_,_) = n1.data.partition(' ')
        return '%s:%s:%s' % (label, n1.kind, data)
    else:
        return '%s:%s:%s' % (label, n1.kind, n1.data)

def is_ref(ref):
    return not (ref is None or ref.startswith('#') or ref.startswith('%'))

# main
def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-d] [-o output] [-c encoding] [-B basedir] [-m maxpaths] '
              '[-M maxoverrides] [graph ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'do:c:B:m:M:')
    except getopt.GetoptError:
        return usage()
    debug = 0
    maxpaths = 100
    maxoverrides = 1
    output = None
    for (k, v) in opts:
        if k == '-d': debug += 1
        elif k == '-o': output = v
        elif k == '-m': maxpaths = int(v)
        elif k == '-M': maxoverrides = int(v)
    if not args: return usage()

    if output is None:
        fp = sys.stdout
    else:
        fp = open(output, 'w')

    builder = IDFBuilder(maxoverrides=maxoverrides)
    for path in args:
        print('Loading: %r...' % path, file=sys.stderr)
        builder.load(path)

    builder.run()
    print('Read: %d sources, %d graphs, %d funcalls, %d IPVertexes' %
          (len(builder.srcmap), len(builder.graphs),
           sum( len(a) for a in builder.funcalls.values() ),
           len(builder.vtxs)), file=sys.stderr)

    def exists(v0, chain):
        if chain is None: return False
        for (_,v1) in chain:
            if v0 is v1: return True
        return False

    def trace(mchain, ref0, v1, chain):
        ref1 = v1.node.ref
        if is_ref(ref1) and ref1 != ref0:
            p = (ref0,ref1)
            if p not in mchain or chain.length < mchain[p].length:
                mchain[p] = chain
        else:
            for (label,v2) in v1.outputs:
                if label.startswith('_'): continue
                if exists(v2, chain): continue
                trace(mchain, ref0, v2, Cons((label,v2), chain))
        return

    mchain = {}
    for (i,vtx) in enumerate(builder):
        ref = vtx.node.ref
        if is_ref(ref):
            trace(mchain, ref, vtx, Cons((None,vtx)))
        if (i % 1000) == 0:
            sys.stderr.write('(%d/%d)\n' % (i,len(builder)))
            sys.stderr.flush()

    for ((ref0,ref1),chain) in mchain.items():
        chain = list(chain)
        chain.reverse()
        assert chain[0][1].node.ref == ref0
        assert chain[-1][1].node.ref == ref1
        feats = []
        for (label,v) in chain[1:]:
            f = getfeat(label, v.node)
            if f is not None:
                feats.append(f)
        fp.write('%s %s %s\n' % (ref0, ref1, ' '.join(feats)))

    if fp is not sys.stdout:
        fp.close()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
