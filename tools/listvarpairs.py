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
    None, 'ref_var', 'assign_var',
    'receive', 'input', 'output', 'begin', 'end', 'repeat', 'catchjoin'}

AUGMENTED = {
    'call', 'op_infix',
    'ref_array', 'ref_field',
    'assign_array', 'assign_field'}

def is_ignored(label):
    return (label.startswith('_') or label == '#bypass')

def is_varref(ref):
    return (ref and ref[0] not in '#%!@')

def getfeat1(label, n):
    if n.is_funcall():
        (data,_,_) = n.data.partition(' ')
        (klass,name,func) = parsemethodname(data)
        return f'{label},{n.kind},{name}'
    elif n.kind in TYPEOPS:
        (_,klass) = DFType.parse(n.data)
        return f'{label},{n.kind},{klass.name}'
    elif n.data is None:
        return f'{label},{n.kind}'
    else:
        return f'{label},{n.kind},{n.data}'

def getfeat2(label, n):
    if n.is_funcall():
        (data,_,_) = n.data.partition(' ')
        (klass,name,func) = parsemethodname(data)
        return f'{label},{n.kind},{name}'
    elif n.kind in REFS or n.kind in ASSIGNS:
        return f'{label},{n.kind},{parserefname(n.ref)}'
    elif n.kind == 'value' and n.ntype == 'Ljava/lang/String;':
        return f'{label},{n.kind},STRING'
    elif n.kind in TYPEOPS:
        (_,klass) = DFType.parse(n.data)
        return f'{label},{n.kind},{klass.name}'
    elif n.kind in EXTOPS:
        return f'{label},{n.kind},{n.data}'
    else:
        return None

def getfeats(n0, label, n1):
    if n1.kind in IGNORED: return []
    if n1.kind == 'op_assign' and n1.data == '=': return []
    f1 = getfeat1(label, n1)
    feats = [f1]
    if n1.kind in AUGMENTED:
        for (k,n2) in n1.inputs.items():
            if is_ignored(k): continue
            if n2 is not n0 and k != label:
                f2 = getfeat2(k, n2)
                if f2 is not None:
                    feats.append(f1+'|'+f2)
    return feats


# main
def main(argv):
    import fileinput
    import getopt
    def usage():
        print(f'usage: {argv[0]} [-d] [-o output] [-M maxoverrides] '
              '[-m maxlen] [-G] [graph ...]')
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'do:M:c:B:m:G')
    except getopt.GetoptError:
        return usage()
    level = logging.INFO
    output = None
    maxoverrides = 1
    encoding = None
    srcdb = None
    maxlen = 5
    maxchains = 32
    grouping = False
    for (k, v) in opts:
        if k == '-d': level = logging.DEBUG
        elif k == '-o': output = v
        elif k == '-M': maxoverrides = int(v)
        elif k == '-C': maxchains = int(v)
        elif k == '-c': encoding = v
        elif k == '-B': srcdb = SourceDB(v, encoding)
        elif k == '-m': maxlen = int(v)
        elif k == '-G': grouping = True
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
    funcalls = sum( len(a) for a in builder.funcalls.values() )
    logging.info(f'Read: {len(builder.srcmap)} sources, {funcalls} funcalls, {len(builder.vtxs)} IPVertexes')

    def trace(r, vtx0, srcs=None, chain=None):
        if chain is not None and maxlen <= len(chain): return
        node = vtx0.node
        if srcs is not None and node in srcs: return
        srcs = Cons(node, srcs)
        #print('  '*level, node.name, node.kind, node.ref, node.data)
        if node in r:
            chains = r[node]
        else:
            chains = r[node] = []
        if maxchains <= len(chains): return
        chains.append(chain)
        if chain is None:
            n0 = None
        else:
            n0 = chain.car
        for (label,vtx1,funcall) in vtx0.outputs:
            if is_ignored(label): continue
            n1 = vtx1.node
            if n1.kind == 'call' and not label.startswith('#'): continue
            feats = getfeats(n0, label, n1)
            if feats:
                for feat in feats:
                    trace(r, vtx1, srcs, Cons((feat, n1), chain))
            else:
                trace(r, vtx1, srcs, chain)
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
        if node0.kind in REFS and is_varref(node0.ref):
            r = {}
            #print('trace', vtx.node)
            trace(r, vtx)
            for (node1,chains) in r.items():
                if (node1.kind not in ASSIGNS or
                    not is_varref(node1.ref)): continue
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
                n0 = parserefname(node0.ref)
                n1 = parserefname(node1.ref)
                if n0 is None or n1 is None or n0 == n1: continue
                fp.write('# %s %s\n' % (n0, n1))

    if fp is not sys.stdout:
        fp.close()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
