#!/usr/bin/env python
import sys
from graph2idf import Cons, IDFBuilder


IGNORED = frozenset([
    None, 'ref', 'fieldref', 'assign', 'fieldassign',
    'input', 'output', 'begin', 'repeat'])

def getfeat(n0, label, n1):
    if label.startswith('_'):
        return None
    elif n0.kind in IGNORED:
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
        if vtx.node.kind == 'call' and label == 'update': continue
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
        if vtx.node.kind == 'call' and not label.startswith('#arg'): continue
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
        print('usage: %s [-d] [-o output] [-m maxlen] [-c maxcalls] '
              '[-M maxoverrides] [graph ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'do:m:c:M:')
    except getopt.GetoptError:
        return usage()
    debug = 0
    maxlen = 5
    maxcalls = 100
    maxoverrides = 1
    output = None
    for (k, v) in opts:
        if k == '-d': debug += 1
        elif k == '-o': output = v
        elif k == '-m': maxlen = int(v)
        elif k == '-c': maxcalls = int(v)
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
    for (gid,nodes) in builder.funcalls.items():
        if maxcalls < len(nodes): continue
        dbg.write('# gid: %r\n' % gid)
        if gid in builder.gid2graph:
            src = builder.getsrc(builder.gid2graph[gid])
        else:
            src = None
        data = (gid, src)
        fp.write('+ITEM %r\n' % (data,))
        for node in nodes:
            caller = node.graph.name
            dbg.write('#   at %r\n' % caller)
            for feats in enum_back(builder.vtxs[node], maxlen=maxlen):
                a = list(feats)
                a.append(('call', node))
                a.reverse()
                data = [ (feat, builder.getsrc(n)) for (feat,n) in a ]
                fp.write('+BACK %r\n' % (data,))
                nents += 1
            for feats in enum_forw(builder.vtxs[node], maxlen=maxlen):
                a = list(feats)
                a.append(('return', node))
                a.reverse()
                data = [ (feat, builder.getsrc(n)) for (feat,n) in a ]
                fp.write('+FORW %r\n' % (data,))
                nents += 1

    print('Ents: %r' % nents, file=sys.stderr)

    if fp is not sys.stdout:
        fp.close()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
