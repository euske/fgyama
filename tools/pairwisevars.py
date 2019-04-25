#!/usr/bin/env python
import sys
import re
from graph2idf import is_funcall, Cons, IDFBuilder

IGNORED = frozenset([
    None, 'ref', 'assign',
    'begin', 'end', 'repeat'])

def getfeat(label, n1):
    if n1.data is None:
        return '%s:%s' % (label, n1.kind)
    elif is_funcall(n1):
        (data,_,_) = n1.data.partition(' ')
        return '%s:%s:%s' % (label, n1.kind, data)
    else:
        return '%s:%s:%s' % (label, n1.kind, n1.data)

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

    def trace(mdist, ref0, v1, chain=None):
        ref1 = v1.node.ref
        if ref1 is not None and not ref1.startswith('#') and ref1 != ref0:
            p = (ref0,ref1)
            if p not in mdist or chain.length < mdist[p].length:
                mdist[p] = chain
        else:
            for (label,v2) in v1.outputs:
                if label.startswith('_'): continue
                if exists(v2, chain): continue
                trace(mdist, ref0, v2, Cons((label,v2), chain))
        return

    mdist = {}
    for vtx in builder:
        ref = vtx.node.ref
        if ref is not None and not ref.startswith('#'):
            trace(mdist, ref, vtx)

    for ((ref0,ref1),chain) in mdist.items():
        print(ref0, ref1, ' '.join( getfeat(label, v.node) for (label,v) in chain ))

    if fp is not sys.stdout:
        fp.close()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
