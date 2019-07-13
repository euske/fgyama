#!/usr/bin/env python
import sys
import re
from graph2idf import IDFBuilder

CALLS = ('call', 'new')
REFS = ('ref_var', 'ref_field')
ASSIGNS = ('assign_var', 'assign_field')
CONDS = ('join', 'begin', 'end', 'case')

IGNORED = (None, 'receive', 'input', 'output', 'repeat')

AUGMENTED = (
    'call', 'op_infix',
    'ref_array', 'ref_field',
    'assign_array', 'assign_field')

def is_ref(ref):
    return not (ref is None or ref.startswith('#') or ref.startswith('%'))

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

def getfeat(n):
    if n.kind in CALLS:
        (data,_,_) = n.data.partition(' ')
        return '%s:%s' % (n.kind, getmethodnoun(data))
    elif n.kind in REFS or n.kind in ASSIGNS:
        return '%s:%s' % (n.kind, getnoun(n.ref))
    elif n.kind in CONDS:
        return n.kind
    elif n.kind == 'value' and n.ntype == 'Ljava/lang/String;':
        return '%s:STRING' % (n.kind)
    elif n.kind in ('op_typecast', 'op_typecheck'):
        return '%s:%s' % (n.kind, gettypenoun(n.data))
    elif n.data is None:
        return '%s' % (n.kind)
    else:
        return '%s:%s' % (n.kind, n.data)

def enum_forw(r, v0, v1, fprev=None, dist=0, maxdist=5):
    if maxdist <= dist: return
    if (v0,v1) in r: return
    feats = r[(v0,v1)] = []
    n1 = v1.node
    for (link,v2) in v1.outputs:
        if link.startswith('_') and link != '_end': continue
        n2 = v2.node
        if n2.kind in CALLS and not link.startswith('#'): continue
        if n2.kind in IGNORED or n2.kind in REFS:
            enum_forw(r, v0, v2, fprev, dist, maxdist)
        else:
            if n2.kind in ASSIGNS:
                enum_forw(r, v0, v2, fprev, dist, maxdist)
            feat = link+':'+getfeat(n2)
            feats.append((dist,fprev,feat,n2))
            enum_forw(r, v1, v2, feat, dist+1, maxdist)
    return

def enum_back(r, v0, v1, fprev=None, loverride=None, dist=0, maxdist=5):
    if maxdist <= dist: return
    if (v0,v1) in r: return
    feats = r[(v0,v1)] = []
    n1 = v1.node
    for (link,v2) in v1.inputs:
        if link.startswith('_') and link != '_end': continue
        n2 = v2.node
        if n1.kind in CALLS and not link.startswith('#'): continue
        if loverride is not None:
            link = loverride
        if n2.kind in IGNORED or n2.kind in ASSIGNS:
            enum_back(r, v0, v2, fprev, link, dist, maxdist)
        else:
            if n2.kind in REFS:
                enum_back(r, v0, v2, fprev, link, dist, maxdist)
            feat = link+':'+getfeat(n2)
            feats.append((dist,fprev,feat,n2))
            enum_back(r, v1, v2, feat, None, dist+1, maxdist)
    return

# main
def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-d] [-o output] [-m maxdist] '
              '[-M maxoverrides] [-F|-B] [graph ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'do:m:M:FB')
    except getopt.GetoptError:
        return usage()
    debug = 0
    maxdist = 5
    maxoverrides = 1
    forwonly = backonly = False
    output = None
    for (k, v) in opts:
        if k == '-d': debug += 1
        elif k == '-o': output = v
        elif k == '-m': maxdist = int(v)
        elif k == '-M': maxoverrides = int(v)
        elif k == '-F': forwonly = True
        elif k == '-B': backonly = True
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
    refs = set()
    for graph in builder.graphs:
        dbg.write('# gid: %r\n' % graph.name)
        for node in graph:
            if not is_ref(node.ref): continue
            refs.add(node.ref)
            if node.kind in REFS:
                if backonly: continue
                enum = enum_forw
                k = 'FORW'
            elif node.kind in ASSIGNS:
                if forwonly: continue
                enum = enum_back
                k = 'BACK'
            else:
                continue
            data = (node.ref, builder.getsrc(node))
            fp.write('+ITEM %r\n' % (data,))
            r = {}
            enum(r, None, builder.vtxs[node], maxdist=maxdist)
            s = set()
            for feats in r.values():
                s.update(feats)
            for (dist,f0,f1,n) in s:
                #if f0 is None: continue
                data = (dist, f0, f1, builder.getsrc(n))
                fp.write('+%s %r\n' % (k, data))
                nents += 1
    print('Ents: %r, Refs: %r' % (nents, len(refs)), file=sys.stderr)

    if fp is not sys.stdout:
        fp.close()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
