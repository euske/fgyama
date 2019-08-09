#!/usr/bin/env python
import sys
import re
from graph2idf import IDFBuilder
from srcdb import SourceDB, SourceAnnot
from getwords import striptypename, stripmethodname, stripref, splitwords

CALLS = ('call', 'new')
REFS = ('ref_var', 'ref_field', 'ref_array')
ASSIGNS = ('assign_var', 'assign_field', 'assign_array')
CONDS = ('join', 'begin', 'end', 'case')

IGNORED = (None, 'receive', 'input', 'output', 'repeat')

AUGMENTED = (
    'call', 'op_infix',
    'ref_array', 'ref_field',
    'assign_array', 'assign_field')

def is_ref(ref):
    return not (ref is None or ref.startswith('#') or ref.startswith('%'))

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

def enum_back(feats, count, done, v1, lprev, v0=None, fprev=None, dist=0, maxdist=5):
    # prevent explosion.
    if count <= 0: return
    if maxdist <= dist: return
    if (v0,v1) in done: return
    done.add((v0,v1))
    n1 = v1.node
    #print('back: %s %r [%s] %s(%s)' % (fprev, lprev, dist, n1.nid, n1.kind))
    # list the input nodes to visit.
    inputs = []
    for (link,v2) in v1.inputs:
        # do not follow informational links.
        if link.startswith('_') and link != '_end': continue
        # do not use a value in arrays.
        if n1.kind == 'ref_array' and not link: continue
        # strip label names.
        if link and link[0] in '@%': link = ''
        inputs.append((link, v2))
    # ignore transparent nodes.
    if n1.kind in IGNORED or n1.kind == 'assign_var':
        for (_,v2) in inputs:
            enum_back(feats, count-1, done, v2, lprev, v0, fprev, dist, maxdist)
        return
    # add the features.
    fs = [ lprev+':'+f for f in getfeats(n1) ]
    if not fs: return
    feats.update( (-(dist+1),fprev,f,n1) for f in fs )
    # if this is a ref_var node, the fact that it refers to a certain variable
    # is recorded, but the node itself is transparent in a chain.
    if n1.kind == 'ref_var':
        for (_,v2) in inputs:
            enum_back(feats, count-1, done, v2, lprev, v0, fprev, dist, maxdist)
        return
    # visit the next nodes.
    for (link,v2) in inputs:
        enum_back(feats, count-1, done, v2, link, v1, fs[0], dist+1, maxdist)
    return

def enum_forw(feats, count, done, v1, lprev, v0=None, fprev=None, dist=0, maxdist=5):
    # prevent explosion.
    if count <= 0: return
    if maxdist <= dist: return
    if (v0,v1) in done: return
    done.add((v0,v1))
    n1 = v1.node
    #print('forw: %s %r [%s] %s(%s)' % (fprev, lprev, dist, n1.nid, n1.kind))
    # list the output nodes to visit.
    outputs = []
    for (link,v2) in v1.outputs:
        # do not follow informational links.
        if link.startswith('_') and link != '_end': continue
        # do not pass a value in arrays.
        if n1.kind == 'assign_array' and not link: continue
        # strip label names.
        if link and link[0] in '@%': link = ''
        outputs.append((link, v2))
    # ignore transparent nodes.
    if n1.kind in IGNORED or n1.kind == 'ref_var':
        for (link,v2) in outputs:
            enum_forw(feats, count-1, done, v2, link, v0, fprev, dist, maxdist)
        return
    # add the features.
    fs = [ lprev+':'+f for f in getfeats(n1) ]
    if not fs: return
    feats.update( (dist+1,fprev,f,n1) for f in fs )
    # if this is a assign_var node, the fact that it assigns to a certain variable
    # is recorded, but the node itself is transparent in a chain.
    if n1.kind == 'assign_var':
        for (link,v2) in outputs:
            enum_forw(feats, count-1, done, v2, link, v0, fprev, dist, maxdist)
        return
    # visit the next nodes.
    for (link,v2) in outputs:
        enum_forw(feats, count-1, done, v2, link, v1, fs[0], dist+1, maxdist)
    return

# main
def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-d] [-o output] [-m maxdist] '
              '[-M maxoverrides] [-B srcdb] [-f|-b] [graph ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'do:m:M:B:fbC')
    except getopt.GetoptError:
        return usage()
    debug = 0
    maxdist = 4
    maxoverrides = 1
    mode = None
    output = None
    srcdb = None
    for (k, v) in opts:
        if k == '-d': debug += 1
        elif k == '-o': output = v
        elif k == '-m': maxdist = int(v)
        elif k == '-M': maxoverrides = int(v)
        elif k == '-B': srcdb = SourceDB(v)
        elif k == '-f': mode = k
        elif k == '-b': mode = k
        elif k == '-C': mode = k
    if not args: return usage()

    if output is None:
        fp = sys.stdout
    else:
        fp = open(output, 'w')

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

    item2nodes = {}
    for graph in builder.graphs:
        for node in graph:
            if (node.kind in REFS or node.kind in ASSIGNS) and is_ref(node.ref):
                item = ('REF', node.ref)
            elif mode == '-C' and node.kind in CALLS:
                item = ('METHOD', node.data)
            else:
                continue
            if item in item2nodes:
                nodes = item2nodes[item]
            else:
                nodes = item2nodes[item] = []
            nodes.append(node)
    print('Items: %r, Nodes: %r' %
          (len(item2nodes), sum( len(nodes) for nodes in item2nodes.values() )),
          file=sys.stderr)

    count = maxdist*5+5
    nfeats = 0
    for (item,nodes) in sorted(item2nodes.items(), key=lambda x:x[0]):
        feats = set()
        if item[0] == 'REF':
            for node in nodes:
                vtx = builder.vtxs[node]
                if mode in (None,'-f'):
                    for (link,v1) in vtx.outputs:
                        if link.startswith('_') and link != '_end': continue
                        enum_forw(feats, count, set(), v1, lprev=link, maxdist=maxdist)
                if mode in (None,'-b'):
                    for (link,v1) in vtx.inputs:
                        if link.startswith('_') and link != '_end': continue
                        enum_back(feats, count, set(), v1, lprev=link, maxdist=maxdist)
        elif item[0] == 'METHOD':
            for node in nodes:
                vtx = builder.vtxs[node]
                enum_forw(feats, count, set(), vtx, maxdist=maxdist)
                enum_back(feats, count, set(), vtx, maxdist=maxdist)
        if not feats: continue

        srcs = ( builder.getsrc(n) for n in nodes if n.ast is not None )
        data = item + tuple(sorted(srcs))
        fp.write('! %r\n' % (data,))
        feat2nodes = {}
        for (dist,f0,f1,n) in feats:
            feat = (dist,f0,f1)
            if feat in feat2nodes:
                nodes = feat2nodes[feat]
            else:
                nodes = feat2nodes[feat] = set()
            nodes.add(n)
        for (feat,nodes) in sorted(feat2nodes.items(), key=lambda x:x[0]):
            srcs = set( builder.getsrc(n) for n in nodes if n.ast is not None )
            data = feat + tuple(sorted(srcs))
            fp.write('+ %r\n' % (data,))
            if srcdb is not None:
                annot = SourceAnnot(srcdb)
                for n in nodes:
                    src = builder.getsrc(n, False)
                    if src is None: continue
                    (path,start,end) = src
                    annot.add(path, start, end)
                annot.show_text()
        fp.write('\n')
        nfeats += len(feat2nodes)
        if debug:
            print('# %r: %r feats' % (item, len(feat2nodes)))
        else:
            sys.stderr.write('.'); sys.stderr.flush()

    print('Total: %r (avg: %r)' % (nfeats, nfeats//len(item2nodes)),
          file=sys.stderr)

    if fp is not sys.stdout:
        fp.close()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
