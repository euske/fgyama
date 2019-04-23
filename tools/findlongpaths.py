#!/usr/bin/env python
import sys
import re
from srcdb import SourceDB, SourceAnnot
from graph2idf import is_funcall, Cons, IDFBuilder


WORD1 = re.compile(r'[A-Z]?[a-z]+$')
WORD2 = re.compile(r'[A-Z]+$')
def getnoun(name):
    if name[-1].islower():
        return WORD1.search(name).group(0).lower()
    elif name[-1].isupper():
        return WORD2.search(name).group(0).lower()
    else:
        return None

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
        print('usage: %s [-d] [-o output] [-c encoding] [-B basedir] [-m maxpaths] '
              '[-M maxoverrides] [graph ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'do:c:B:m:M:')
    except getopt.GetoptError:
        return usage()
    debug = 0
    maxpaths = 10
    maxoverrides = 1
    encoding = None
    srcdb = None
    output = None
    for (k, v) in opts:
        if k == '-d': debug += 1
        elif k == '-o': output = v
        elif k == '-c': encoding = v
        elif k == '-B': srcdb = SourceDB(v, encoding)
        elif k == '-m': maxpaths = int(v)
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

    def tracein(mdist, v0, dist=(0,0), prev=None, chain=None):
        if chain is not None and v0 in chain: return
        if v0 in mdist and dist <= mdist[v0][0]: return
        chain = Cons(v0, chain)
        mdist[v0] = (dist, prev)
        (a,n) = dist
        if v0.node.kind == 'assign':
            a += 1
        n += 1
        dist = (a,n)
        for (label,v1) in v0.outputs:
            if label.startswith('_'): continue
            if v1.node.kind == 'output': continue
            tracein(mdist, v1, dist, v0, chain)
        return

    def traceout(mdist, nexts, v0, dist=(0,0), prev=None, chain=None):
        if chain is not None and v0 in chain: return
        if v0 in mdist and dist <= mdist[v0][0]: return
        chain = Cons(v0, chain)
        mdist[v0] = (dist, prev)
        (a,n) = dist
        if v0.node.kind == 'assign':
            a += 1
        n += 1
        dist = (a,n)
        if v0.node.kind == 'output':
            assert nexts is not None
            caller = nexts.car
            nexts = nexts.cdr
            for (label,v1) in v0.outputs:
                if label.startswith('_'): continue
                if v1.node.kind == 'input': continue
                if v1.node.graph is not caller: continue
                traceout(mdist, nexts, v1, dist, v0, chain)
        else:
            for (label,v1) in v0.outputs:
                if label.startswith('_'): continue
                if v1.node.kind == 'input': continue
                traceout(mdist, nexts, v1, dist, v0, chain)
        return

    mdist = {}
    for vtx in builder:
        if not vtx.inputs:
            tracein(mdist, vtx)
    vtxs = sorted(mdist.items(), key=lambda x:x[1][0], reverse=True)
    for (vtx0,(dist,_)) in vtxs[:maxpaths]:
        nodes = []
        vtx = vtx0
        while vtx is not None:
            assert vtx in mdist
            nodes.append(vtx.node)
            (_,vtx) = mdist[vtx]
        nodes.reverse()
        nexts = Cons.fromseq( n.graph for n in nodes if n.kind == 'input' )
        mdist2 = {}
        traceout(mdist2, nexts, vtx0)
        nodes = [ n for n in nodes if n.kind == 'assign' ]
        print('+PATH', dist, ' '.join( getnoun(n.ref) for n in nodes ))
        for (i,n) in enumerate(nodes):
            print('#', n.ref)
            if srcdb is not None:
                src = builder.getsrc(n, False)
                if src is None: continue
                (name,start,length) = src
                annot = SourceAnnot(srcdb)
                annot.add(name, start, start+length, i)
                annot.show_text(fp)
        print()

    if fp is not sys.stdout:
        fp.close()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
