#!/usr/bin/env python
import sys
import re
from graph2idf import is_funcall, Cons, IDFBuilder

REFS = ('ref_var', 'ref_field')
ASSIGNS = ('assign_var', 'assign_field')

IGNORED = frozenset([
    None, 'ref_var', 'ref_field', 'assign_var', 'assign_field',
    'receive', 'input', 'output', 'begin', 'end', 'repeat'])

AUGMENTED = frozenset([
    'call', 'op_infix',
    'ref_array', 'ref_field',
    'assign_array', 'assign_field'])

WORD1 = re.compile(r'[A-Z]?[a-z]+$')
WORD2 = re.compile(r'[A-Z]+$')
def getnoun(name):
    if name[-1].islower():
        return WORD1.search(name).group(0).lower()
    elif name[-1].isupper():
        return WORD2.search(name).group(0).lower()
    else:
        return None

def gettypenoun(name):
    if name.endswith(';'):
        return getnoun(name[:-1])
    else:
        return name

def getmethodnoun(name):
    assert '(' in name
    (name,_,_) = name.partition('(')
    if name.endswith(';.<init>'):
        name = name[:-8]
    return getnoun(name)

def getfeat1(label, n):
    if is_funcall(n):
        (data,_,_) = n.data.partition(' ')
        return '%s:%s:%s' % (label, n.kind, getmethodnoun(data))
    elif n.kind in ('op_typecast', 'op_typecheck'):
        return '%s:%s:%s' % (label, n.kind, gettypenoun(n.data))
    elif n.data is None:
        return '%s:%s' % (label, n.kind)
    else:
        return '%s:%s:%s' % (label, n.kind, n.data)

def getfeat2(label, n):
    if is_funcall(n):
        (data,_,_) = n.data.partition(' ')
        return '%s:%s:%s' % (label, n.kind, data)
    elif n.kind in ('ref_var','ref_field','assign_var','assign_field'):
        return '%s:%s:%s' % (label, n.kind, getnoun(n.ref))
    elif n.kind == 'value' and n.ntype == 'Ljava/lang/String;':
        return '%s:%s:STRING' % (label, n.kind)
    elif n.kind in ('op_typecast', 'op_typecheck'):
        return '%s:%s:%s' % (label, n.kind, gettypenoun(n.data))
    elif n.kind in ('op_infix', 'value'):
        return '%s:%s:%s' % (label, n.kind, n.data)
    else:
        return None

def getfeats(n0, label, n1):
    if n1.kind in IGNORED: return []
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

def enum_forw(vtx, feats=None, chain=None, maxlen=5):
    if feats is not None and maxlen < len(feats): return
    if chain is not None and vtx in chain: return
    chain = Cons(vtx, chain)
    for (label,v) in vtx.outputs:
        if label.startswith('_'): continue
        if is_funcall(v.node) and not label.startswith('#'): continue
        for feat1 in getfeats(vtx.node, label, v.node):
            yield Cons((feat1, v.node), feats)
            for z in enum_forw(v, feats, chain, maxlen):
                yield z
    return

def enum_back(vtx, feats=None, chain=None, maxlen=5):
    if feats is not None and maxlen < len(feats): return
    if chain is not None and vtx in chain: return
    chain = Cons(vtx, chain)
    for (label,v) in vtx.inputs:
        if label.startswith('_'): continue
        if is_funcall(v.node) and not label.startswith('#'): continue
        for feat1 in getfeats(vtx.node, label, v.node):
            yield Cons((feat1, v.node), feats)
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
        (opts, args) = getopt.getopt(argv[1:], 'do:m:M:')
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

    nents = 0
    for graph in builder.graphs:
        dbg.write('# gid: %r\n' % graph.name)
        for node in graph:
            if node.kind in REFS:
                enum = enum_forw
                k = 'FORW'
            elif node.kind in ASSIGNS:
                enum = enum_back
                k = 'BACK'
            else:
                continue
            if not is_ref(node.ref): continue
            data = (node.ref, builder.getsrc(node))
            fp.write('+ITEM %r\n' % (data,))
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
