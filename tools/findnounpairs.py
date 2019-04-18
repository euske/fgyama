#!/usr/bin/env python
import sys
import re
from srcdb import SourceDB, SourceAnnot
from graph2idf import Cons, IDFBuilder, is_funcall

IGNORED = frozenset([
    None, 'ref', 'assign',
    'begin', 'end', 'repeat'])

def getfeat(n0, label, n1):
    if n1.data is None:
        return '%s:%s' % (label, n1.kind)
    elif is_funcall(n1):
        (data,_,_) = n1.data.partition(' ')
        return '%s:%s:%s' % (label, n1.kind, data)
    else:
        return '%s:%s:%s' % (label, n1.kind, n1.data)

WORD1 = re.compile(r'[A-Z]?[a-z]+$')
WORD2 = re.compile(r'[A-Z]+$')
def getnoun(name):
    if name[-1].islower():
        return WORD1.search(name).group(0).lower()
    elif name[-1].isupper():
        return WORD2.search(name).group(0).lower()
    else:
        return None

def is_ref(ref):
    return not (ref is None or ref.startswith('#') or ref.startswith('%'))


# main
def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-d] [-o output] [-M maxoverrides] '
              '[-m maxlen] [graph ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'do:M:c:B:m:')
    except getopt.GetoptError:
        return usage()
    debug = 0
    output = None
    maxoverrides = 1
    encoding = None
    srcdb = None
    maxlen = 5
    for (k, v) in opts:
        if k == '-d': debug += 1
        elif k == '-o': output = v
        elif k == '-M': maxoverrides = int(v)
        elif k == '-c': encoding = v
        elif k == '-B': srcdb = SourceDB(v, encoding)
        elif k == '-m': maxlen = int(v)
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
        builder.load(path)

    builder.run()
    print('Read: %d sources, %d graphs, %d funcalls, %d IPVertexes' %
          (len(builder.srcmap), len(builder.graphs),
           sum( len(a) for a in builder.funcalls.values() ),
           len(builder.vtxs)),
          file=sys.stderr)

    def doit(r, vtx, prev=None, chain=None, length=0):
        if maxlen < length: return
        node = vtx.node
        #print('  '*length, node.kind, node.ref, node.data)
        if node.kind == 'assign' and is_ref(node.ref):
            if node in r:
                chains = r[node]
            else:
                chains = r[node] = []
            chains.append(chain)
        for (label,v) in vtx.outputs:
            if label.startswith('_'): continue
            n = v.node
            if n.kind in ('receive',):
                doit(r, v, prev, chain, length)
            elif n.kind in IGNORED:
                doit(r, v, prev, chain, length+1)
            else:
                feat = getfeat(prev, label, n)
                doit(r, v, n, Cons((feat, n), chain), length+1)
        return

    def put(node0, node1, chain):
        n0 = getnoun(node0.ref)
        n1 = getnoun(node1.ref)
        if n0 is None or n1 is None or n0 == n1: return
        fp.write('+ %s %s %s\n' % (n0, n1, ' '.join( k for (k,_) in chain )))
        if srcdb is not None:
            annot = SourceAnnot(srcdb)
            chain.insert(0, (None,node0))
            chain.append((None,node1))
            for (i,(_,n)) in enumerate(chain):
                src = builder.getsrc(n, False)
                if src is None: continue
                (name,start,length) = src
                annot.add(name, start, start+length, i)
            annot.show_text(fp)
        return

    for vtx in builder:
        node0 = vtx.node
        if node0.kind == 'ref' and is_ref(node0.ref):
            r = {}
            #print('doit', vtx.node)
            doit(r, vtx)
            for (node1,chains) in r.items():
                done = []
                for chain in chains:
                    if chain in done: continue
                    done.append(chain)
                    if chain is None:
                        a = []
                    else:
                        a = list(chain)
                        a.reverse()
                        put(node0, node1, a)

    if fp is not sys.stdout:
        fp.close()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
