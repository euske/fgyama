#!/usr/bin/env python
import sys
import re
from graph2idf import IDFBuilder

CALLS = ('call', 'new')
REFS = ('ref_var', 'ref_field', 'ref_array')
ASSIGNS = ('assign_var', 'assign_field', 'assign_array')
ARRAYS = ('ref_array', 'assign_array')
CONDS = ('join', 'begin', 'end', 'case')

IGNORED = (None, 'receive', 'input', 'output', 'repeat')

AUGMENTED = (
    'call', 'op_infix',
    'ref_array', 'ref_field',
    'assign_array', 'assign_field')

def is_ref(ref):
    return not (ref is None or ref.startswith('#') or ref.startswith('%'))

NAME = re.compile(r'\w+$', re.U)
def striplast(name):
    m = NAME.search(name)
    if m:
        return m.group(0)
    else:
        return None

def striptypename(name):
    if name.endswith(';'):
        name = name[:-1]
    return striplast(name)

def stripmethodname(name):
    assert '(' in name
    (name,_,_) = name.partition('(')
    if name.endswith(';.<init>'):
        name = name[:-8]
    return striplast(name)

def stripref(name):
    if name.startswith('%'):
        return striptypename(name)
    else:
        return striplast(name)

WORD1 = re.compile(r'[A-Z]?[a-z]+$')
WORD2 = re.compile(r'[A-Z]+$')
def getnoun(name):
    if name is None:
        return None
    elif name[-1].islower():
        return WORD1.search(name).group(0).lower()
    elif name[-1].isupper():
        return WORD2.search(name).group(0).lower()
    else:
        return None

WORD = re.compile(r'[a-z]+[A-Z]?|[A-Z]+')
def splitwords(s):
    """
    >>> splitwords('name')
    ['name']
    >>> splitwords('this_is_name_!')
    ['name', 'is', 'this']
    >>> splitwords('thisIsName')
    ['name', 'is', 'this']
    >>> splitwords('SomeXMLStuff')
    ['stuff', 'xml', 'some']
"""
    if s is None: return []
    n = len(s)
    r = ''.join(reversed(s))
    return [ s[n-m.end(0):n-m.start(0)].lower() for m in WORD.finditer(r) ]

def getfeats(n):
    if n.kind in CALLS:
        (data,_,_) = n.data.partition(' ')
        return [ '%s:%s' % (n.kind, w) for w in splitwords(stripmethodname(data)) ]
    elif n.kind in REFS or n.kind in ASSIGNS:
        return [ '%s:%s' % (n.kind, w) for w in splitwords(stripref(n.ref)) ]
    elif n.kind in CONDS:
        return [ n.kind ]
    elif n.kind == 'value' and n.ntype == 'Ljava/lang/String;':
        return [ '%s:STRING' % (n.kind) ]
    elif n.kind in ('op_typecast', 'op_typecheck'):
        return [ '%s:%s' % (n.kind, w) for w in splitwords(striptypename(n.data)) ]
    elif n.data is None:
        return [ n.kind ]
    else:
        return [ '%s:%s' % (n.kind, n.data) ]

def enum_forw(feats, done, v1, v0=None, fprev=None, lprev='', dist=0, maxdist=5):
    if maxdist <= dist: return
    if (v0,v1) in done: return
    done.add((v0,v1))
    n1 = v1.node
    #print('forw: %s %r [%s] %s(%s)' % (fprev, lprev, dist, n1.nid, n1.kind))
    if lprev.startswith('@'):
        lprev = '@'
    outputs = []
    for (link,v2) in v1.outputs:
        if link.startswith('_') and link != '_end': continue
        if n1.kind in ARRAYS and not link: continue
        outputs.append((link, v2))
    if n1.kind == 'output':
        for (link,v2) in outputs:
            enum_forw(feats, done, v2, v0, fprev, link, dist, maxdist)
        return
    elif n1.kind == 'receive' and lprev.startswith('@'):
        pass
    elif n1.kind in IGNORED or (lprev == '' and n1.kind in REFS):
        for (link,v2) in outputs:
            enum_forw(feats, done, v2, v0, fprev, link, dist, maxdist)
        return
    fs = [ lprev+':'+f for f in getfeats(n1) ]
    if fs:
        for f in fs:
            feats.add(('FORW',dist,fprev,f,n1))
        if n1.kind in ASSIGNS:
            for (link,v2) in outputs:
                enum_forw(feats, done, v2, v0, fprev, link, dist, maxdist)
        else:
            for (link,v2) in outputs:
                enum_forw(feats, done, v2, v1, fs[0], link, dist+1, maxdist)
    return

def enum_back(feats, done, v1, v0=None, fprev=None, lprev='', dist=0, maxdist=5):
    if maxdist <= dist: return
    if (v0,v1) in done: return
    done.add((v0,v1))
    n1 = v1.node
    #print('back: %s %r [%s] %s(%s)' % (fprev, lprev, dist, n1.nid, n1.kind))
    if lprev.startswith('@'):
        lprev = '@'
    inputs = []
    for (link,v2) in v1.inputs:
        if link.startswith('_') and link != '_end': continue
        if n1.kind in ARRAYS and not link: continue
        inputs.append((link, v2))
    if n1.kind == 'input':
        for (link,v2) in inputs:
            enum_back(feats, done, v2, v0, fprev, link, dist, maxdist)
        return
    elif n1.kind == 'receive':
        pass
    elif n1.kind in IGNORED or n1.kind in ASSIGNS:
        for (link,v2) in inputs:
            enum_back(feats, done, v2, v0, fprev, lprev, dist, maxdist)
        return
    fs = [ lprev+':'+f for f in getfeats(n1) ]
    if fs:
        for f in fs:
            feats.add(('BACK',dist,fprev,f,n1))
        if n1.kind in REFS:
            for (link,v2) in inputs:
                enum_back(feats, done, v2, v0, fprev, lprev, dist, maxdist)
        else:
            for (link,v2) in inputs:
                enum_back(feats, done, v2, v1, fs[0], link, dist+1, maxdist)
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
        (opts, args) = getopt.getopt(argv[1:], 'do:m:M:FBC')
    except getopt.GetoptError:
        return usage()
    debug = 0
    maxdist = 5
    maxoverrides = 1
    mode = None
    output = None
    for (k, v) in opts:
        if k == '-d': debug += 1
        elif k == '-o': output = v
        elif k == '-m': maxdist = int(v)
        elif k == '-M': maxoverrides = int(v)
        elif k == '-F': mode = k
        elif k == '-B': mode = k
        elif k == '-C': mode = k
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

    items = set()
    nfeats = 0
    for graph in builder.graphs:
        dbg.write('# gid: %r\n' % graph.name)
        for node in graph:
            feats = set()
            vtx = builder.vtxs[node]
            ok = False
            if (node.kind in REFS or node.kind in ASSIGNS) and is_ref(node.ref):
                item = ('REF', node.ref)
                if mode in (None,'-F'):
                    enum_forw(feats, set(), vtx, maxdist=maxdist)
                    ok = True
                if mode in (None,'-B'):
                    enum_back(feats, set(), vtx, maxdist=maxdist)
                    ok = True
            elif node.kind in CALLS:
                item = ('METHOD', node.data)
                if mode in (None,'-C'):
                    enum_forw(feats, set(), vtx, maxdist=maxdist)
                    enum_back(feats, set(), vtx, maxdist=maxdist)
                    ok = True
            if not ok: continue
            items.add(item)
            data = item + (builder.getsrc(node),)
            fp.write('! %r\n' % (data,))
            for (t, dist,f0,f1,n) in feats:
                data = (t, dist, f0, f1, builder.getsrc(n))
                fp.write('+ %r %r\n' % (1, data))
            nfeats += len(feats)
            fp.write('\n')
    print('Items: %r, Features: %r' % (len(items), nfeats), file=sys.stderr)

    if fp is not sys.stdout:
        fp.close()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
