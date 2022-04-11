#!/usr/bin/env python
import sys
import logging
import re
from algos import Cons
from graphs import IDFBuilder, DFType, parsemethodname, parserefname
from srcdb import SourceDB, SourceAnnot

REFS = {'ref_var', 'ref_field'}
ASSIGNS = {'assign_var', 'assign_field'}
TYPEOPS = {'op_typecast', 'op_typecheck'}
EXTOPS = {'op_infix', 'value'}

IGNORED = {
    None, 'receive', 'input', 'output', 'begin', 'end', 'repeat', 'catchjoin'
}

AUGMENTED = {
    'call', 'op_infix',
    'ref_array', 'ref_field',
    'assign_array', 'assign_field'
}

def fmtsrc(x):
    if x is None: return 'None'
    return '%d:%d:%d' % x

def is_ignored(n, label):
    return (label.startswith('_') or
            (n.is_funcall() and not label.startswith('#arg')))

def is_varref(ref):
    return (ref and ref[0] not in '#%!@')

def getfeatext(label, n):
    if n.is_funcall():
        (data,_,_) = n.data.partition(' ')
        (klass,name,func) = parsemethodname(data)
        return f'{label},{n.kind},{name}'
    elif n.kind in REFS or n.kind in ASSIGNS:
        return f'{label},{n.kind},{parserefname(n.ref)}'
    elif n.kind == 'value' and n.ntype == 'Ljava/lang/String;':
        return f'{label},{n.kind},STRING'
    elif n.kind in TYPEOPS:
        (_,typ) = DFType.parse(n.data)
        return f'{label},{n.kind},{typ.get_name()}'
    elif n.kind in EXTOPS:
        return f'{label},{n.kind},{n.data}'
    else:
        return None

def getfeat1(label, n):
    if n.kind == 'call':
        (data,_,_) = n.data.partition(' ')
        (klass,name,func) = parsemethodname(data)
        return f'{label},{n.kind},{name}'
    elif n.kind == 'new':
        (data,_,_) = n.data.partition(' ')
        (klass,name,func) = parsemethodname(data)
        return f'{label},{n.kind},{klass.name}'
    elif n.kind in TYPEOPS:
        (_,typ) = DFType.parse(n.data)
        return f'{label},{n.kind},{typ.get_name()}'
    elif n.data is None:
        return f'{label},{n.kind}'
    else:
        return f'{label},{n.kind},{n.data}'

def getfeats(n0, label, n1):
    if n1.kind in IGNORED: return None
    if n1.kind == 'op_assign' and n1.data == '=': return None
    f1 = getfeat1(label, n1)
    feats = [f1]
    if n1.kind in AUGMENTED:
        for (k,n2) in n1.inputs.items():
            if is_ignored(n2, k): continue
            if n2 is not n0 and k != label:
                f2 = getfeatext(k, n2)
                if f2 is not None:
                    feats.append(f1+'|'+f2)
    return feats

def enumkeys(chain, key=None):
    assert chain is not None
    (label,n1) = chain.car
    prev = chain.cdr
    if prev is None:
        yield key
        return
    (_,n0) = prev.car
    feats = getfeats(n0, label, n1)
    if feats is None:
        for z in enumkeys(prev, key):
            yield z
    else:
        for feat in feats:
            for z in enumkeys(prev, Cons(feat, key)):
                yield z
    return


# main
def main(argv):
    import fileinput
    import getopt
    def usage():
        print(f'usage: {argv[0]} [-d] [-o output] [-M maxoverrides] '
              '[-c encoding] [-B basedir] [-m maxlen] [-C maxchains] [-k minkey] '
              '[graph ...]')
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'do:M:c:B:m:C:k:')
    except getopt.GetoptError:
        return usage()
    level = logging.INFO
    output = None
    maxoverrides = 1
    encoding = None
    srcdb = None
    maxlen = 6
    maxchains = 100
    minkey = 3
    for (k, v) in opts:
        if k == '-d': level = logging.DEBUG
        elif k == '-o': output = v
        elif k == '-M': maxoverrides = int(v)
        elif k == '-c': encoding = v
        elif k == '-B': srcdb = SourceDB(v, encoding)
        elif k == '-m': maxlen = int(v)
        elif k == '-C': maxchains = int(v)
        elif k == '-k': minkey = int(v)
    logging.basicConfig(format='%(asctime)s %(levelname)s %(message)s', level=level)
    if not args: return usage()

    if output is None:
        fp = sys.stdout
    else:
        fp = open(output, 'w')

    builder = IDFBuilder(maxoverrides=maxoverrides)
    for path in args:
        logging.info(f'Loading: {path!r}...')
        builder.load(path, fp)

    builder.run()
    logging.info(f'Read: {len(builder.srcmap)} sources, {len(builder.vtxs)} IPVertexes')

    def enumnodes(chains, label0, vtx0, prev=None):
        n0 = vtx0.node
        chain = Cons((label0,n0), prev)
        if n0.kind in ASSIGNS:
            assert prev is not None
            if is_varref(n0.ref):
                if len(chains) < maxchains:
                    chains.append(chain)
            return
        if maxlen <= len(chain): return
        for (label1,vtx1,_) in vtx0.outputs:
            if is_ignored(n0, label1): continue
            enumnodes(chains, label1, vtx1, chain)
        return

    def show(nodes, key):
        assert 2 <= len(nodes)
        fp.write(f'+KEY {" ".join(key)}\n')
        fp.write(f'+REFS {nodes[0].ref} {nodes[-1].ref}\n')
        fp.write('+CHAIN %s\n' % (' '.join( fmtsrc(builder.getsrc(n)) for n in nodes )))
        if srcdb is not None:
            annot = SourceAnnot(srcdb)
            for (i,n) in enumerate(nodes):
                src = builder.getsrc(n, False)
                if src is None: continue
                (name,start,end) = src
                annot.add(name, start, end, i)
            annot.show_text(fp)
        fp.write('\n')
        return

    key2pair = {}
    for vtx0 in builder:
        node0 = vtx0.node
        if node0.kind in REFS and is_varref(node0.ref):
            chains = []
            enumnodes(chains, None, vtx0)
            for chain in chains:
                nodes = [ n for (_,n) in chain ]
                nodes.reverse()
                ref0 = nodes[0].ref
                ref1 = nodes[-1].ref
                if ref0 is None or ref1 is None or ref0 == ref1: continue
                for key in enumkeys(chain):
                    if key is None: continue
                    key = tuple(key)
                    if len(key) < minkey: continue
                    show(nodes, key)
                    if key in key2pair:
                        p = key2pair[key]
                    else:
                        p = key2pair[key] = []
                    p.append((nodes[0].ref, nodes[-1].ref))

    for (key,pairs) in key2pair.items():
        if len(pairs) < 2: continue
        fp.write(f'+GROUP {" ".join(key)}\n')
        for (ref0,ref1) in pairs:
            n0 = parserefname(ref0)
            n1 = parserefname(ref1)
            fp.write(f'+PAIR {parserefname(ref0)} {parserefname(ref1)}\n')
        fp.write('\n')

    if fp is not sys.stdout:
        fp.close()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
