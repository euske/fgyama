#!/usr/bin/env python
import sys
from srcdb import SourceDB, SourceAnnot
from graph2idf import is_funcall, Cons, IDFBuilder


def enum(vtx, ref0, refs, chain=None, level=0):
    if chain is not None and vtx in chain: return
    if chain is None:
        chain = set()
    chain.add(vtx)
    for (label,v) in vtx.inputs:
        if label.startswith('_'): continue
        ref = v.node.ref
        if ref is not None and ref.startswith('@') and ref != ref0:
            refs.add(ref)
        else:
            enum(v, ref0, refs, chain, level+1)
    return

# main
def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-d] [-o output] [-c encoding] [-B basedir] [-m maxlen] '
              '[-M maxoverrides] [graph ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'do:c:B:m:M:')
    except getopt.GetoptError:
        return usage()
    debug = 0
    maxlen = 5
    maxoverrides = 1
    encoding = None
    srcdb = None
    output = None
    for (k, v) in opts:
        if k == '-d': debug += 1
        elif k == '-o': output = v
        elif k == '-c': encoding = v
        elif k == '-B': srcdb = SourceDB(v, encoding)
        elif k == '-m': maxlen = int(v)
        elif k == '-M': maxoverrides = int(v)
    if not args: return usage()

    if output is None:
        fp = sys.stdout
    else:
        fp = open(output, 'w')
    if 0 < debug:
        dbg = sys.stderr
    else:
        dbg = fp

    builder = IDFBuilder(maxoverrides=maxoverrides)
    for path in args:
        print('Loading: %r...' % path, file=sys.stderr)
        builder.load(path, fp)

    builder.run()
    print('Read: %d sources, %d graphs, %d funcalls, %d IPVertexes' %
          (len(builder.srcmap), len(builder.graphs),
           sum( len(a) for a in builder.funcalls.values() ),
           len(builder.vtxs)),
          file=sys.stderr)

    mdist = {}
    mprev = {}
    def add(v0, n=0, prev=None, chain=None):
        if chain is not None and v0 in chain: return
        if v0 in mdist and n <= mdist[v0]: return
        chain = Cons(v0, chain)
        mdist[v0] = n
        mprev[v0] = prev
        if v0.node.kind == 'assign':
            n += 1
        for (label,v1) in v0.outputs:
            if label.startswith('_'): continue
            add(v1, n, v0, chain)
        return
    for vtx in builder.vtxs.values():
        if not vtx.inputs:
            add(vtx)

    maxdist = max(mdist.values())
    print(maxdist)
    for (vtx,dist) in mdist.items():
        if dist < maxdist: continue
        nodes = []
        while vtx is not None:
            assert vtx in mprev
            nodes.append(vtx.node)
            vtx = mprev[vtx]
        nodes = [ n for n in nodes if n.kind == 'assign' ]
        nodes.reverse()
        print('#', ' '.join( n.ref for n in nodes ))
        if srcdb is not None:
            for (i,n) in enumerate(nodes):
                src = builder.getsrc(n, False)
                if src is None: continue
                (name,start,length) = src
                annot = SourceAnnot(srcdb)
                annot.add(name, start, start+length, i)
                annot.show_text(fp)

    if fp is not sys.stdout:
        fp.close()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
