#!/usr/bin/env python
import sys
from graph import get_graphs

IGNORED = frozenset([None, 'ref', 'assign', 'input', 'output', 'begin', 'repeat'])
def getfeatforw(label, node):
    assert label is not None
    if node.kind in IGNORED:
        return None
    elif node.kind == 'assignop' and node.data == '=':
        return None
    elif node.kind in ('join','end') and label != 'cond':
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

def getfeatback(node, label):
    assert label is not None
    if node.kind in IGNORED:
        return None
    elif node.kind == 'assignop' and node.data == '=':
        return None
    elif node.kind in ('join','end') and label != 'cond':
        return None
    elif node.ref == '#exception':
        return None
    elif node.data is None:
        return '%s:%s' % (node.kind, label)
    elif node.kind == 'call':
        (data,_,_) = node.data.partition(' ')
        return '%s:%s:%s' % (node.kind, data, label)
    else:
        return '%s:%s:%s' % (node.kind, node.data, label)

def skiplink(label, node):
    if label == '':
        return False
    if label.startswith('_'):
        return True
    if label in ('update',):
        return True
    return False


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

    def connect(self, label, output):
        #print('# connect: %r %s %r' % (self, label, output))
        #assert output is not self
        assert isinstance(label, str)
        assert isinstance(output, IPVertex)
        self.outputs.append((label, output))
        output.inputs.append((label, self))
        return

    def enum(self, direction, prev0=None, done=None):
        done = Cons(self, done)
        if direction < 0:
            vtxs = self.inputs
        else:
            vtxs = self.outputs
        for (label,vtx) in vtxs:
            prev = prev0
            if label is not None:
                prev = Cons((label, vtx.node), prev0)
                yield prev
            for z in vtx.enum(direction, prev, done):
                yield z
        return


# main
def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-d] [-o output] [-m maxlen] [-n mincall] '
              '[-M maxoverride] [graph ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'do:m:n:M:')
    except getopt.GetoptError:
        return usage()
    debug = 0
    maxlen = 50
    mincall = 2
    maxoverride = 1
    maxfanout = 100
    output = None
    for (k, v) in opts:
        if k == '-d': debug += 1
        elif k == '-o': output = v
        elif k == '-m': maxlen = int(v)
        elif k == '-n': mincall = int(v)
        elif k == '-M': maxoverride = int(v)
    if not args: return usage()

    if output is None:
        fp = sys.stdout
    else:
        fp = open(output, 'w')
    if 0 < debug:
        dbg = sys.stderr
    else:
        dbg = fp

    # Load graphs.
    graphs = []
    srcmap = {}
    gid2graph = {}
    for path in args:
        print('Loading: %r...' % path, file=sys.stderr)
        for graph in get_graphs(path):
            if graph.style == 'initializer': continue
            if graph.src not in srcmap:
                fid = len(srcmap)
                srcmap[graph.src] = fid
                src = (fid, graph.src)
                if debug == 0:
                    fp.write('+SOURCE %r\n' % (src,))
            graphs.append(graph)
            gid2graph[graph.name] = graph

    # Enumerate caller/callee relationships.
    funcalls = {}
    def addcall(x, y): # (caller, callee)
        if y in funcalls:
            a = funcalls[y]
        else:
            a = funcalls[y] = []
        if x not in a:
            a.append(x)
        return
    for src in graphs:
        for node in src:
            if node.kind == 'call':
                funcs = node.data.split(' ')
                for gid in funcs[:maxoverride]:
                    addcall(node, gid)
            elif node.kind == 'new':
                addcall(node, node.data)

    # Convert every node to IPVertex.
    vtxs = {}
    def getvtx(node):
        if node in vtxs:
            vtx = vtxs[node]
        else:
            vtx = vtxs[node] = IPVertex(node)
        return vtx
    for graph in graphs:
        for n0 in graph:
            if n0.kind in ('call', 'new'):
                funcs = n0.data.split(' ')
                for gid in funcs[:maxoverride]:
                    if gid not in gid2graph: continue
                    graph = gid2graph[gid]
                    for n2 in graph.ins:
                        label = n2.ref
                        if label in n0.inputs:
                            getvtx(n0.inputs[label]).connect(label, getvtx(n2))
                    outputs = {}
                    for (label,n1) in n0.outputs:
                        if label == '':
                            outputs['#return'] = n1
                        elif label == 'update':
                            outputs[n1.ref] = n1
                    for n2 in graph.outs:
                        label = n2.ref
                        if label in outputs:
                            getvtx(n2).connect(label, getvtx(outputs[label]))
            v0 = getvtx(n0)
            for (label,n1) in n0.outputs:
                v0.connect(label, getvtx(n1))

    print('Read: %d sources, %d graphs, %d funcalls, %d IPVertexes' %
          (len(srcmap), len(graphs),
           sum( len(a) for a in funcalls.values() ), len(vtxs)),
          file=sys.stderr)

    def getsrc(node):
        if node.ast is None: return None
        src = node.graph.src
        fid = srcmap[src]
        (_,loc,length) = node.ast
        return (fid, loc, length)

    for graph in graphs:
        for node in graph:
            if node.kind not in ('ref','assign'): continue
            if not node.ref.startswith('@'): continue
            print('#', node.ref)
            if node.kind == 'ref':
                d = +1
                k = 'FORW'
            else:
                d = -1
                k = 'BACK'
            for feats in vtxs[node].enum(d):
                if feats is None: continue
                a = list(feats)
                a.append((node.kind, node))
                a.reverse()
                data = [ (feat,getsrc(n)) for (feat,n) in a ]
                fp.write('+%s %r\n' % (k, data,))

    if fp is not sys.stdout:
        fp.close()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))