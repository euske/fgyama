#!/usr/bin/env python
import sys
import os
import re
import json
from interproc import IDFBuilder, Cons, clen
from srcdb import SourceDB, SourceAnnot
from featdb import FeatDB
from getwords import stripref, splitmethodname, splitwords, gettypewords

CALLS = ('call', 'new')
REFS = ('ref_var', 'ref_field', 'ref_array')
ASSIGNS = ('assign_var', 'assign_field', 'assign_array')
CONDS = ('join', 'begin', 'end', 'case')

IGNORED = (None, 'receive', 'input', 'output', 'repeat')

AUGMENTED = (
    'call', 'op_infix',
    'ref_array', 'ref_field',
    'assign_array', 'assign_field')

NODE_COST = 10

debug = 0

def is_ref(ref):
    return not (ref is None or ref.startswith('#') or ref.startswith('%'))

def getfeats(n):
    if n.kind in CALLS:
        (data,_,_) = n.data.partition(' ')
        (name,_,_) = splitmethodname(data)
        return [ '%s:%s' % (n.kind, w) for w in splitwords(name) ]
    elif n.kind in REFS or n.kind in ASSIGNS:
        if n.ref.startswith('%'):
            return [ '%s:%%%s' % (n.kind, w) for w in gettypewords(n.ref[1:]) ]
        else:
            return [ '%s:%s' % (n.kind, w) for w in splitwords(stripref(n.ref)) ]
    elif n.kind in CONDS:
        return [ n.kind ]
    elif n.kind == 'value' and n.ntype == 'Ljava/lang/String;':
        return [ '%s:STRING' % (n.kind) ]
    elif n.kind in ('op_typecast', 'op_typecheck'):
        return [ '%s:%s' % (n.kind, w) for w in gettypewords(n.data) ]
    elif n.data is None:
        return [ n.kind ]
    else:
        return [ '%s:%s' % (n.kind, n.data) ]

def gettypefeats(n):
    return [ '@'+w for w in gettypewords(n.ntype) ]

def enum_back(feats, count, done, v1, lprev=None,
              v0=None, fprev='', chain=None, dist=0, calls=None):
    # prevent explosion.
    if count <= 0: return
    count -= 1
    if (v0,v1) in done: return
    if v0 is not None:
        done.add((v0,v1))
    n1 = v1.node
    if debug:
        print('back: %s(%s), kids=%r, fprev=%r, lprev=%r, count=%r, done=%r' %
              (n1.nid, n1.kind, len(v1.inputs), fprev, lprev, count, len(done)))
    chain = Cons(n1, chain)
    # list the input nodes to visit.
    inputs = []
    for (link,v2,funcall) in v1.inputs:
        # do not follow informational links.
        if link.startswith('_') and link != '_end': continue
        # do not use a value in arrays.
        if n1.kind == 'ref_array' and not link: continue
        # treat indirect assignment the same way as normal assignment.
        if link and link[0] in '@%':
            #if funcall is None: continue
            link = ''
        # interprocedural.
        if n1.kind == 'output':
            inputs.append((link, v2, Cons(funcall, calls)))
        elif n1.kind == 'input' and calls is not None:
            if calls.car is funcall:
                inputs.append((link, v2, calls.cdr))
        else:
            inputs.append((link, v2, calls))
    if v0 is None:
        for (link,v2,calls) in inputs:
            enum_back(feats, count, done, v2, link,
                      v1, fprev, chain, dist, calls)
        return
    # ignore transparent nodes.
    if n1.kind in IGNORED or n1.kind == 'assign_var':
        for (_,v2,calls) in inputs:
            enum_back(feats, count, done, v2, lprev,
                      v1, fprev, chain, dist, calls)
        return
    # add the features.
    fs = [ lprev+':'+f for f in getfeats(n1) ]
    if not fs: return
    fs += [ lprev+':'+f for f in gettypefeats(n1) ]
    for f in fs:
        feat = (-(dist+1),fprev,f)
        feats[feat] = chain
        if debug:
            print('  feat:', feat)
    # if this is a ref_var node, the fact that it refers to a certain variable
    # is recorded, but the node itself is transparent in a chain.
    if n1.kind == 'ref_var':
        for (_,v2,calls) in inputs:
            enum_back(feats, count, done, v2, lprev,
                      v1, fprev, chain, dist, calls)
        return
    # visit the next nodes.
    count -= NODE_COST
    dist += 1
    for (link,v2,calls) in inputs:
        enum_back(feats, count, done, v2, link,
                  v1, fs[0], None, dist, calls)
    return

def enum_forw(feats, count, done, v1, lprev=None,
              v0=None, fprev='', chain=None, dist=0, calls=None):
    # prevent explosion.
    if count <= 0: return
    count -= 1
    if (v0,v1) in done: return
    if v0 is not None:
        done.add((v0,v1))
    n1 = v1.node
    if debug:
        print('forw: %s(%s), kids=%r, fprev=%r, lprev=%r, count=%r, done=%r' %
              (n1.nid, n1.kind, len(v1.outputs), fprev, lprev, count, len(done)))
    chain = Cons(n1, chain)
    # list the output nodes to visit.
    outputs = []
    for (link,v2,funcall) in v1.outputs:
        # do not follow informational links.
        if link.startswith('_') and link != '_end': continue
        # do not pass a value in arrays.
        if n1.kind == 'assign_array' and not link: continue
        # treat indirect assignment the same way as normal assignment.
        if link and link[0] in '@%':
            #if funcall is None: continue
            link = ''
        # interprocedural.
        if n1.kind == 'input':
            outputs.append((link, v2, Cons(funcall, calls)))
        elif n1.kind == 'output' and calls is not None:
            if calls.car is funcall:
                outputs.append((link, v2, calls.cdr))
        else:
            outputs.append((link, v2, calls))
    if v0 is None:
        for (link,v2,calls) in outputs:
            enum_forw(feats, count, done, v2, link,
                      v1, fprev, chain, dist, calls)
        return
    # ignore transparent nodes.
    if n1.kind in IGNORED or n1.kind == 'ref_var':
        for (link,v2,calls) in outputs:
            enum_forw(feats, count, done, v2, link,
                      v1, fprev, chain, dist, calls)
        return
    # add the features.
    fs = [ lprev+':'+f for f in getfeats(n1) ]
    if not fs: return
    fs += gettypefeats(v0.node)
    for f in fs:
        feat = ((dist+1),fprev,f)
        feats[feat] = chain
        if debug:
            print('  feat:', feat)
    # if this is a assign_var node, the fact that it assigns to a certain variable
    # is recorded, but the node itself is transparent in a chain.
    if n1.kind == 'assign_var':
        for (link,v2,calls) in outputs:
            enum_forw(feats, count, done, v2, link,
                      v1, fprev, chain, dist, calls)
        return
    # visit the next nodes.
    count -= NODE_COST
    dist += 1
    for (link,v2,calls) in outputs:
        enum_forw(feats, count, done, v2, link,
                  v1, fs[0], None, dist, calls)
    return


# main
def main(argv):
    global debug, maxchain, maxdist
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-d] [-o output] [-m maxdist] '
              '[-M maxoverrides] [-B srcdb] [-f|-b] [graph ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'do:c:M:B:fbC')
    except getopt.GetoptError:
        return usage()
    maxoverrides = 1
    count = 50
    mode = None
    funcs = False
    outpath = None
    srcdb = None
    for (k, v) in opts:
        if k == '-d': debug += 1
        elif k == '-o': outpath = v
        elif k == '-c': count = int(v)
        elif k == '-M': maxoverrides = int(v)
        elif k == '-B': srcdb = SourceDB(v)
        elif k == '-f': mode = k
        elif k == '-b': mode = k
        elif k == '-C': funcs = True
    if not args: return usage()

    db = None
    if outpath is not None:
        if os.path.exists(outpath):
            print('Already exists: %r' % outpath)
            return 1
        db = FeatDB(outpath)
        db.init()

    builder = IDFBuilder(maxoverrides=maxoverrides)
    for path in args:
        print('Loading: %r...' % path, file=sys.stderr)
        builder.load(path)
    if db is not None:
        for (path,srcid) in builder.srcmap.items():
            db.add_path(srcid, path)

    builder.run()
    print('Read: %d sources, %d graphs, %d funcalls, %d IPVertexes' %
          (len(builder.srcmap), len(builder.graphs),
           sum( len(a) for a in builder.funcalls.values() ),
           len(builder.vtxs)),
          file=sys.stderr)

    item2nodes = {}
    for graph in builder.graphs:
        for node in graph:
            item = None
            if funcs:
                if node.kind in CALLS:
                    item = node.data
            else:
                if (node.kind in REFS or node.kind in ASSIGNS) and is_ref(node.ref):
                    item = node.ref
            if item is None:
                continue
            elif item in item2nodes:
                nodes = item2nodes[item]
            else:
                nodes = item2nodes[item] = []
            nodes.append(node)
    print('Items: %r, Nodes: %r' %
          (len(item2nodes), sum( len(nodes) for nodes in item2nodes.values() )),
          file=sys.stderr)

    def getsrcs(nodes):
        srcs = set( builder.getsrc(n) for n in nodes if n.ast is not None )
        return tuple(srcs)

    nfeats = 0
    feat2fid = {}
    for (item,nodes) in sorted(item2nodes.items(), key=lambda x:x[0]):
        feats = {}
        if debug:
            print('item: %r' % item)
        for node in nodes:
            v0 = builder.vtxs[node]
            done = set()
            if mode in (None,'-b'):
                enum_back(feats, count, done, v0)
            if mode in (None,'-f'):
                enum_forw(feats, count, done, v0)
        if not feats: continue
        nfeats += len(feats)

        if db is not None:
            fid2srcs = { 0:getsrcs(nodes) }
            for (feat,chain) in sorted(feats.items(), key=lambda x:x[0]):
                if feat in feat2fid:
                    fid = feat2fid[feat]
                else:
                    fid = len(feat2fid)+1
                    feat2fid[feat] = fid
                    db.add_feat(feat, fid)
                fid2srcs[fid] = getsrcs(chain)
            db.add_item(item, fid2srcs)
            sys.stderr.write('.'); sys.stderr.flush()
        else:
            data = (item, getsrcs(nodes))
            print('+ITEM', json.dumps(data))
            if srcdb is not None:
                annot = SourceAnnot(srcdb)
                for n in nodes:
                    if n.ast is None: continue
                    src = builder.getsrc(n, False)
                    if src is None: continue
                    (path,start,end) = src
                    annot.add(path, start, end)
                annot.show_text()
            for (feat,chain) in sorted(feats.items(), key=lambda x:x[0]):
                data = feat + getsrcs(chain)
                print('+FEAT', json.dumps(data))
            print()

    print('Total: %r (avg: %r)' % (nfeats, nfeats//len(item2nodes)),
          file=sys.stderr)

    if db is not None:
        db.close()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
