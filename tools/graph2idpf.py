#!/usr/bin/env python
import sys
from graph2idf import Cons, IDFBuilder


IGNORED = frozenset([
    None, 'ref', 'fieldref', 'assign', 'fieldassign',
    'input', 'output', 'begin', 'repeat'])

def getfeat(n0, label, n1):
    if n0.kind in IGNORED:
        if label:
            return '%s:' % label
        else:
            return None
    elif n0.data is None:
        return '%s:%s' % (label, n0.kind)
    elif n0.kind == 'call':
        (data,_,_) = n0.data.partition(' ')
        return '%s:%s:%s' % (label, n0.kind, data)
    else:
        return '%s:%s:%s' % (label, n0.kind, n0.data)

def enum_forw(vtx, feats0=None, chain=None, maxlen=5):
    if feats0 is not None and maxlen < len(feats0): return
    if chain is not None and vtx in chain: return
    chain = Cons(vtx, chain)
    for (label,v) in vtx.outputs:
        feats = feats0
        feat1 = getfeat(v.node, label, vtx.node)
        if feat1 is not None:
            feats = Cons((feat1, v.node), feats0)
            yield feats
        for z in enum_forw(v, feats, chain, maxlen):
            yield z
    return

def enum_back(vtx, feats0=None, chain=None, maxlen=5):
    if feats0 is not None and maxlen < len(feats0): return
    if chain is not None and vtx in chain: return
    chain = Cons(vtx, chain)
    for (label,v) in vtx.inputs:
        feats = feats0
        feat1 = getfeat(v.node, label, vtx.node)
        if feat1 is not None:
            feats = Cons((feat1, v.node), feats0)
            yield feats
        for z in enum_back(v, feats, chain, maxlen):
            yield z
    return

# main
def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-d] [-o output] [-m maxlen] '
              '[-M maxoverrides] [graph ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'do:m:n:M:')
    except getopt.GetoptError:
        return usage()
    debug = 0
    maxlen = 5
    maxoverrides = 1
    output = None
    for (k, v) in opts:
        if k == '-d': debug += 1
        elif k == '-o': output = v
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

    builder = IDFBuilder(maxoverrides=maxoverrides, dbg=dbg)
    for path in args:
        print('Loading: %r...' % path, file=sys.stderr)
        builder.load(path, fp)

    builder.run()
    print('Read: %d sources, %d graphs, %d funcalls, %d IPVertexes' %
          (len(builder.srcmap), len(builder.graphs),
           sum( len(a) for a in builder.funcalls.values() ),
           len(builder.vtxs)),
          file=sys.stderr)

    nents = 0
    for graph in builder.graphs:
        dbg.write('# gid: %r\n' % graph.name)
        for node in graph:
            if node.kind not in ('ref','fieldref','assign','fieldassign'): continue
            if not node.ref.startswith('@'): continue
            data = (node.ref, builder.getsrc(node))
            fp.write('+ITEM %r\n' % (data,))
            if node.kind in ('ref','fieldref'):
                enum = enum_forw
                k = 'FORW'
            else:
                enum = enum_back
                k = 'BACK'
            for feats in enum(builder.vtxs[node], maxlen=maxlen):
                if feats is None: continue
                a = list(feats)
                a.append((node.kind, node))
                a.reverse()
                data = [ (feat, builder.getsrc(n)) for (feat,n) in a ]
                fp.write('+%s %r\n' % (k, data,))
                nents += 1
    print('Ents: %r' % nents, file=sys.stderr)

    if fp is not sys.stdout:
        fp.close()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
