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
CONDS = ('join', 'begin', 'end', 'case', 'catchjoin')

IGNORED = (None, 'receive', 'input', 'output', 'repeat')

AUGMENTED = (
    'call', 'op_infix',
    'ref_array', 'ref_field',
    'assign_array', 'assign_field')

def gettypefeats(n):
    return [ '@'+w for w in gettypewords(n.ntype) ]


##  FeatGenerator
##
class FeatGenerator:

    node_cost = 10

    def __init__(self, direction, tracecount,
                 interproc=True, namefeat=True, typefeat=True,
                 debug=0):
        self.direction = direction
        self.tracecount = tracecount
        self.interproc = interproc
        self.namefeat = namefeat
        self.typefeat = typefeat
        self.debug = debug
        return

    def enum_feats(self, feats, vtx, ref0=None):
        self.feats = feats
        self.ref0 = ref0
        self.done = set()
        if self.direction <= 0:
            self.enum_back(self.tracecount, vtx)
        if 0 <= self.direction:
            self.enum_forw(self.tracecount, vtx)
        return

    def getbasefeats(self, n):
        if n.kind in CALLS:
            return []
        elif n.kind in REFS or n.kind in ASSIGNS:
            if n.ref is self.ref0:
                return [ f'{n.kind}:SELF' ]
            elif n.ref.startswith('%'):
                return [ f'{n.kind}:%{w}' for w in gettypewords(n.ref[1:]) ]
            else:
                return []
        elif n.kind in CONDS:
            return [ n.kind ]
        elif n.kind == 'value' and n.ntype == 'Ljava/lang/String;':
            return [ f'{n.kind}:STRING' ]
        elif n.kind in ('op_typecast', 'op_typecheck'):
            return [ f'{n.kind}:{w}' for w in gettypewords(n.data) ]
        elif n.data is None:
            return [ n.kind ]
        else:
            return [ f'{n.kind}:{n.data}' ]

    def getnamefeats(self, n):
        if n.kind in CALLS:
            (data,_,_) = n.data.partition(' ')
            (name,_,_) = splitmethodname(data)
            return [ f'{n.kind}:{w}' for w in splitwords(name) ]
        elif n.kind in REFS or n.kind in ASSIGNS:
            if n.ref is self.ref0:
                return []
            elif n.ref.startswith('%'):
                return []
            else:
                if self.namefeat:
                    return [ f'{n.kind}:{w}' for w in splitwords(stripref(n.ref)) ]
                else:
                    return [ n.kind ]
        else:
            return []

    def enum_back(self, count, v1, lprev=None,
                  v0=None, fprev='', chain=None, dist=0, calls=None):
        # prevent explosion.
        if count < 0: return
        count -= 1
        if (v0,v1) in self.done: return
        if v0 is not None:
            self.done.add((v0,v1))
        n1 = v1.node
        if self.debug:
            print(f'back: {n1.nid}({n1.kind}), kids={len(v1.inputs)}, fprev={fprev}, lprev={lprev}, count={count}, done={len(self.done)}')
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
                if self.interproc:
                    inputs.append((link, v2, Cons(funcall, calls)))
            elif n1.kind == 'input' and calls is not None:
                if self.interproc and calls.car is funcall:
                    inputs.append((link, v2, calls.cdr))
            else:
                inputs.append((link, v2, calls))
        if v0 is None:
            for (link,v2,calls) in inputs:
                self.enum_back(count, v2, link,
                               v1, fprev, chain, dist, calls)
            return
        # ignore transparent nodes.
        if n1.kind in IGNORED or n1.kind == 'assign_var':
            for (_,v2,calls) in inputs:
                self.enum_back(count, v2, lprev,
                               v1, fprev, chain, dist, calls)
            return
        # add the features.
        ws = [ lprev+':'+f for f in self.getnamefeats(n1) ]
        fs = ws + [ lprev+':'+f for f in self.getbasefeats(n1) ]
        if not fs: return
        if self.typefeat:
            fs += [ lprev+':'+f for f in gettypefeats(n1) ]
        for f in fs:
            feat = (-(dist+1),fprev,f)
            if feat in self.feats:
                a = self.feats[feat]
            else:
                a = self.feats[feat] = []
            a.append(chain)
            if self.debug:
                print('  feat:', feat)
        # if this is a ref_var node, the fact that it refers to a certain variable
        # is recorded, but the node itself is transparent in a chain.
        if n1.kind == 'ref_var':
            for (_,v2,calls) in inputs:
                self.enum_back(count, v2, lprev,
                               v1, fprev, chain, dist, calls)
            return
        # visit the next nodes.
        count -= self.node_cost
        dist += 1
        for fprev in (ws or fs[:1]):
            for (link,v2,calls) in inputs:
                self.enum_back(count, v2, link,
                               v1, fprev, None, dist, calls)
        return

    def enum_forw(self, count, v1, lprev=None,
                  v0=None, fprev='', chain=None, dist=0, calls=None):
        # prevent explosion.
        if count < 0: return
        count -= 1
        if (v0,v1) in self.done: return
        if v0 is not None:
            self.done.add((v0,v1))
        n1 = v1.node
        if self.debug:
            print(f'forw: {n1.nid}({n1.kind}), kids={len(v1.outputs)}, fprev={fprev}, lprev={lprev}, count={count}, done={len(self.done)}')
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
                if self.interproc:
                    outputs.append((link, v2, Cons(funcall, calls)))
            elif n1.kind == 'output' and calls is not None:
                if self.interproc and calls.car is funcall:
                    outputs.append((link, v2, calls.cdr))
            else:
                outputs.append((link, v2, calls))
        if v0 is None:
            for (link,v2,calls) in outputs:
                self.enum_forw(count, v2, link,
                               v1, fprev, chain, dist, calls)
            return
        # ignore transparent nodes.
        if n1.kind in IGNORED or n1.kind == 'ref_var':
            for (link,v2,calls) in outputs:
                self.enum_forw(count, v2, link,
                               v1, fprev, chain, dist, calls)
            return
        # add the features.
        ws = [ lprev+':'+f for f in self.getnamefeats(n1) ]
        fs = ws + [ lprev+':'+f for f in self.getbasefeats(n1) ]
        if not fs: return
        if self.typefeat:
            fs += gettypefeats(v0.node)
        for f in fs:
            feat = ((dist+1),fprev,f)
            if feat in self.feats:
                a = self.feats[feat]
            else:
                a = self.feats[feat] = []
            a.append(chain)
            if self.debug:
                print('  feat:', feat)
        # if this is a assign_var node, the fact that it assigns to a certain variable
        # is recorded, but the node itself is transparent in a chain.
        if n1.kind == 'assign_var':
            for (link,v2,calls) in outputs:
                self.enum_forw(count, v2, link,
                               v1, fprev, chain, dist, calls)
            return
        # visit the next nodes.
        count -= self.node_cost
        dist += 1
        for fprev in (ws or fs[:1]):
            for (link,v2,calls) in outputs:
                self.enum_forw(count, v2, link,
                               v1, fprev, None, dist, calls)
        return


# main
def main(argv):
    import fileinput
    import getopt
    def usage():
        print(f'usage: {argv[0]} [-d] [-o output] [-C tracecount] '
              '[-n maxnodes] [-M maxoverrides] [-B srcdb] [-f|-b] '
              '[-F)unction] [-I)nterproc] [-N)amefeat] [-T)ypefeat] '
              '[graph ...]')
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'do:C:n:M:B:fbFINT')
    except getopt.GetoptError:
        return usage()
    debug = 0
    outpath = None
    tracecount = 50
    maxnodes = 0
    maxoverrides = 1
    srcdb = None
    direction = 0
    itype = 'ref'
    interproc = False
    namefeat = False
    typefeat = False
    for (k, v) in opts:
        if k == '-d': debug += 1
        elif k == '-o': outpath = v
        elif k == '-C': tracecount = int(v)
        elif k == '-n': maxnodes = int(v)
        elif k == '-M': maxoverrides = int(v)
        elif k == '-B': srcdb = SourceDB(v)
        elif k == '-f': direction = +1
        elif k == '-b': direction = -1
        elif k == '-F': itype = 'func'
        elif k == '-I': interproc = True
        elif k == '-N': namefeat = True
        elif k == '-T': typefeat = True
    if not args: return usage()

    db = None
    if outpath is not None:
        if os.path.exists(outpath):
            print(f'Already exists: {outpath}')
            return 1
        db = FeatDB(outpath)
        db.init()

    def filtermethod(method):
        if maxnodes == 0: return True
        ins = outs = 0
        for node in method:
            if node.kind == 'input':
                ins += 1
            elif node.kind == 'output':
                outs += 1
        ok = (max(ins, outs) <= maxnodes)
        if not ok:
            print(f'Skipped: {method}', file=sys.stderr)
        return ok

    builder = IDFBuilder(maxoverrides=maxoverrides)
    for path in args:
        print(f'Loading: {path}...', file=sys.stderr)
        builder.load(path, filter=filtermethod)
    if db is not None:
        for (path,srcid) in builder.srcmap.items():
            db.add_path(srcid, path)

    builder.run()
    nfuncalls = sum( len(a) for a in builder.funcalls.values() )
    print(f'Read: {len(builder.srcmap)} sources, {len(builder.methods)} methods, {nfuncalls} funcalls, {len(builder.vtxs)} IPVertexes',
          file=sys.stderr)

    item2nodes = {}
    for method in builder.methods:
        for node in method:
            item = None
            if itype == 'func':
                if node.kind in CALLS:
                    item = node.data
            else:
                if node.kind in REFS or node.kind in ASSIGNS:
                    assert node.ref is not None
                    if not (node.ref.startswith('#') or node.ref.startswith('%')):
                        item = node.ref
            if item is None:
                continue
            elif item in item2nodes:
                nodes = item2nodes[item]
            else:
                nodes = item2nodes[item] = []
            nodes.append(node)
    n = sum( len(nodes) for nodes in item2nodes.values() )
    print(f'Items: {len(item2nodes)}, Nodes: {n}',
          file=sys.stderr)

    def getsrcs(nodes):
        srcs = set( builder.getsrc(n) for n in nodes if n.ast is not None )
        return tuple(srcs)

    gen = FeatGenerator(direction, tracecount, interproc,
                        namefeat, typefeat, debug=debug)
    nfeats = 0
    feat2fid = {}
    for (item,nodes) in sorted(item2nodes.items(), key=lambda x:x[0]):
        if debug:
            print(f'Item: {item}')
        feats = {}
        for node in nodes:
            v0 = builder.vtxs[node]
            gen.enum_feats(feats, v0, item)
        if not feats: continue
        nfeats += len(feats)

        if db is not None:
            fidcount = {}
            fid2srcs = { 0:getsrcs(nodes) }
            for (feat,chains) in sorted(feats.items(), key=lambda x:x[0]):
                if feat in feat2fid:
                    fid = feat2fid[feat]
                else:
                    fid = len(feat2fid)+1
                    feat2fid[feat] = fid
                    db.add_feat(feat, fid)
                if fid not in fidcount:
                    fidcount[fid] = 0
                fidcount[fid] += len(chains)
                fid2srcs[fid] = getsrcs(chains[0])
            # only keep major features. (threshold = max/2)
            threshold = max(fidcount.values()) // 2
            #threshold = sum(fidcount.values()) / len(fidcount) # avg
            for (fid,c) in fidcount.items():
                if c < threshold:
                    del fid2srcs[fid]
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
            for (feat,chains) in sorted(feats.items(), key=lambda x:x[0]):
                data = (feat, getsrcs(chains[0]))
                print('+FEAT', json.dumps(data))
            print()

    avg = nfeats//len(item2nodes)
    print(f'\nTotal: {nfeats} (avg: {avg})',
          file=sys.stderr)

    if db is not None:
        db.close()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
