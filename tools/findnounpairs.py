#!/usr/bin/env python
import sys
import re
from srcdb import SourceDB, SourceAnnot
from graph2idf import Cons, IDFBuilder, is_funcall

IGNORED = frozenset([
    None, 'ref', 'assign',
    'input', 'output', 'begin', 'end', 'repeat'])

AUGMENTED = frozenset([
    'call', 'infix',
    'arrayref', 'fieldref',
    'arrayassign', 'fieldassign'])

WORD1 = re.compile(r'[A-Z]?[a-z]+$')
WORD2 = re.compile(r'[A-Z]+$')
def getnoun(name):
    if name[-1].islower():
        return WORD1.search(name).group(0).lower()
    elif name[-1].isupper():
        return WORD2.search(name).group(0).lower()
    else:
        return None

def getfeat1(label, n):
    if is_funcall(n):
        (data,_,_) = n.data.partition(' ')
        return '%s:%s:%s' % (label, n.kind, data)
    elif n.data is None:
        return '%s:%s' % (label, n.kind)
    else:
        return '%s:%s:%s' % (label, n.kind, n.data)

def getfeat2(label, n):
    if is_funcall(n):
        (data,_,_) = n.data.partition(' ')
        return '%s:%s:%s' % (label, n.kind, data)
    elif n.kind in ('ref','fieldref','assign','fieldassign'):
        return '%s:%s:%s' % (label, n.kind, getnoun(n.ref))
    elif n.kind in ('infix','const'):
        return '%s:%s:%s' % (label, n.kind, n.data)
    else:
        return None

def getfeats(n0, label, n1):
    f1 = getfeat1(label, n1)
    feats = [f1]
    if n1.kind in AUGMENTED:
        for (k,n2) in n1.inputs.items():
            if k != label:
                f2 = getfeat2(k, n2)
                if f2 is not None:
                    feats.append(f1+'|'+f2)
    return feats

def is_ref(ref):
    return not (ref is None or ref.startswith('#') or ref.startswith('%'))


# main
def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-d] [-o output] [-M maxoverrides] '
              '[-m maxlen] [-G] [graph ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'do:M:c:B:m:G')
    except getopt.GetoptError:
        return usage()
    debug = 0
    output = None
    maxoverrides = 1
    encoding = None
    srcdb = None
    maxlen = 5
    grouping = False
    for (k, v) in opts:
        if k == '-d': debug += 1
        elif k == '-o': output = v
        elif k == '-M': maxoverrides = int(v)
        elif k == '-c': encoding = v
        elif k == '-B': srcdb = SourceDB(v, encoding)
        elif k == '-m': maxlen = int(v)
        elif k == '-G': grouping = True
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

    def trace(r, vtx, chain=None, length=0):
        if maxlen < length: return
        node = vtx.node
        #print('  '*length, node.kind, node.ref, node.data)
        if node in r:
            chains = r[node]
        else:
            chains = r[node] = set()
        if chain in chains: return
        chains.add(chain)
        for (label,v) in vtx.outputs:
            if label.startswith('_'): continue
            n = v.node
            if n.kind == 'call' and not label.startswith('#'): continue
            if n.kind in ('receive',):
                trace(r, v, chain, length)
            elif n.kind in IGNORED:
                trace(r, v, chain, length+1)
            else:
                if chain is None:
                    n0 = None
                else:
                    n0 = chain.car
                for feat in getfeats(n0, label, n):
                    trace(r, v, Cons((feat, n), chain), length+1)
        return

    def put(node0, node1, chain):
        n0 = node0.ref
        n1 = node1.ref
        if n0 is None or n1 is None or n0 == n1: return
        nodes = [node0,node1] + [ n for (_,n) in chain ]
        def f(x):
            if x is None: return 'None'
            return '%d:%d:%d' % x
        fp.write('+NODES %s\n' % (' '.join( f(builder.getsrc(n)) for n in nodes )))
        fp.write('+PAIR %s %s %s\n' % (n0, n1, ' '.join( k for (k,_) in chain )))
        if srcdb is not None:
            annot = SourceAnnot(srcdb)
            chain.insert(0, (None,node0))
            chain.append((None,node1))
            for (i,(_,n)) in enumerate(chain):
                src = builder.getsrc(n, False)
                if src is None: continue
                (name,start,end) = src
                annot.add(name, start, end, i)
            annot.show_text(fp)
        return

    key2pair = {}
    for vtx in builder:
        node0 = vtx.node
        if node0.kind in ('ref','fieldref') and is_ref(node0.ref):
            r = {}
            #print('trace', vtx.node)
            trace(r, vtx)
            for (node1,chains) in r.items():
                if (node1.kind not in ('assign','fieldassign') or
                    not is_ref(node1.ref)): continue
                for chain in chains:
                    if chain is None: continue
                    a = list(chain)
                    a.reverse()
                    put(node0, node1, a)
                    if grouping:
                        key = tuple( k for (k,_) in a )
                        if key in key2pair:
                            p = key2pair[key]
                        else:
                            p = key2pair[key] = []
                        p.append((node0,node1))
    if grouping:
        for (key,pairs) in key2pair.items():
            if len(pairs) < 2: continue
            fp.write('+GROUP %s\n' % ' '.join(key))
            for (node0,node1) in pairs:
                n0 = getnoun(node0.ref)
                n1 = getnoun(node1.ref)
                if n0 is None or n1 is None or n0 == n1: continue
                fp.write('# %s %s\n' % (n0, n1))

    if fp is not sys.stdout:
        fp.close()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
