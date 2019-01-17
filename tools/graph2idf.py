#!/usr/bin/env python
import sys
from graph import get_graphs

IGNORED = frozenset([None, 'ref', 'assign', 'input', 'output'])
def getfeat(label, node):
    if node.kind in IGNORED:
        return None
    elif node.kind == 'assignop' and node.data == '=':
        return None
    elif node.kind == 'join' and label != 'cond':
        return None
    elif node.ref == '#exception':
        return None
    elif node.data is None:
        return '%s:%s' % (label, node.kind)
    elif node.kind == 'call':
        (data,_,_) = node.data.partition(' ')
        return '%s:%s:%s' % (label, node.kind, data)
    else:
        return '%s:%s:%s' % (label, node.kind, node.data)


##  Cons
##
class Cons:

    def __init__(self, car, cdr=None):
        self.car = car
        self.cdr = cdr
        self.length = 1
        if (cdr is not None):
            self.length = cdr.length+1
        return

    def __len__(self):
        return self.length

    def __iter__(self):
        c = self
        while c is not None:
            yield c.car
            c = c.cdr
        return

    def __contains__(self, obj0):
        for obj in self:
            if obj is obj0: return True
        return False


##  IPVertex (Inter-Procedural Vertex)
##  (why vertex? because calling this another "node" is confusing!)
##
class IPVertex:

    vid_base = 0

    def __init__(self, node):
        IPVertex.vid_base += 1
        self.vid = self.vid_base
        self.node = node
        self.inputs = []
        self.outputs = []
        return

    def __repr__(self):
        return ('<IPVertex(%d)>' % (self.vid))

    def connect(self, feat, output):
        #print('# connect: %r %s %r' % (self, feat, output))
        assert output is not self
        assert isinstance(feat, str)
        assert isinstance(output, IPVertex)
        self.outputs.append((feat, output))
        output.inputs.append((feat, self))
        return

    def enum(self, direction, prev=None):
        if direction < 0:
            vtxs = self.inputs
        else:
            vtxs = self.outputs
        yield prev
        for (feat,vtx) in vtxs:
            if feat is not None:
                prev = Cons((feat, self.node), prev)
            for z in vtx.enum(direction, prev):
                yield z
        return


# main
def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-d] [-m maxlen] [graph ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'dm:')
    except getopt.GetoptError:
        return usage()
    debug = 0
    maxlen = 5
    mincall = 1
    maxoverrides = 1
    for (k, v) in opts:
        if k == '-d': debug += 1
        elif k == '-m': maxlen = int(v)
    if not args: return usage()

    # Load graphs.
    graphs = []
    srcmap = {}
    gid2graph = {}
    for path in args:
        print('# loading: %r...' % path, file=sys.stderr)
        for graph in get_graphs(path):
            if graph.src not in srcmap:
                fid = len(srcmap)
                srcmap[graph.src] = fid
                print('+SOURCE %d %s' % (fid, graph.src))
            graphs.append(graph)
            gid2graph[graph.name] = graph

    print('# graphs: %r' % len(graphs), file=sys.stderr)

    # Enumerate caller/callee relationships.
    caller = {}
    def addcall(x, y): # (caller, callee)
        if y in caller:
            a = caller[y]
        else:
            a = caller[y] = []
        if x not in a:
            a.append(x)
        return
    for src in graphs:
        for node in src:
            if node.kind in ('call', 'new'):
                for gid in node.data.split(' '):
                    addcall(node, gid)

    def trace(out, v0, label, n1, length=0):
        #print('[%d] trace' % length, label, n1)
        if maxlen <= length: return
        if label is not None and (label == 'update' or label.startswith('_')): return
        feat = getfeat(label, n1)
        if feat is None:
            v1 = v0
        elif n1 in out:
            v1 = out[n1]
            v0.connect(feat, v1)
            return
        else:
            v1 = out[n1] = IPVertex(n1)
            v0.connect(feat, v1)
        length += 1
        if n1.kind in ('call', 'new'):
            args = set( label for label in n1.inputs.keys()
                        if not label.startswith('_') )
            funcs = n1.data.split(' ')
            for gid in funcs[:maxoverrides]:
                if gid not in gid2graph: continue
                graph = gid2graph[gid]
                for n2 in graph.ins:
                    label = n2.ref
                    if label not in args: continue
                    trace(out, v1, label, n2, length)
        for (label, n2) in n1.outputs:
            trace(out, v1, label, n2, length)
        if n1.kind == 'output':
            gid = n1.graph.name
            if gid in caller:
                for nc in caller[n1.graph.name]:
                    for (label, n2) in nc.outputs:
                        trace(out, v1, label, n2, length)
        return

    def fmt(feat, node):
        if node.ast is not None:
            src = node.graph.src
            fid = srcmap[src]
            (_,s,e) = node.ast
            feat += (',%s,%s,%s' % (fid, s, e))
        return feat

    for graph in graphs:
        gid = graph.name
        if gid not in caller or len(caller[gid]) < mincall: continue
        if graph.ast is not None:
            fid = srcmap[graph.src]
            (_,s,e) = graph.ast
            name = ('%s,%s,%s,%s' % (gid, fid, s, e))
        else:
            name = gid
        print('# start: %r' % gid, file=sys.stderr)
        for funcall in caller[gid]:
            out = {}
            v1 = IPVertex(funcall)
            trace(out, v1, None, funcall)
            for feats in v1.enum(+1):
                if feats is None: continue
                if len(feats) <= 1: continue
                a = list(feats)
                a.reverse()
                print('+PATH %s forw %s' %
                      (name, ' '.join( fmt(feat,n) for (feat,n) in a[1:] )))
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
