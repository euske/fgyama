#!/usr/bin/env python
import sys
from graph import DFGraph, get_graphs


def clen(x):
    if x is None:
        return 0
    else:
        return len(x)


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

    def __hash__(self):
        return id(self)

    def __iter__(self):
        c = self
        while c is not None:
            yield c.car
            c = c.cdr
        return

    def __eq__(self, c1):
        c0 = self
        while c0 is not c1:
            if c0 is None or c1 is None: return False
            if c0.car != c1.car: return False
            (c0,c1) = (c0.cdr, c1.cdr)
        return True

    def __contains__(self, obj0):
        for obj in self:
            if obj is obj0: return True
        return False

    @classmethod
    def fromseq(self, seq):
        c = None
        for x in seq:
            c = Cons(x, c)
        return c


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

    def connect(self, label, output, funcall=None):
        #print('# connect: %r-%s-%r' % (self, label, output))
        #assert output is not self
        assert isinstance(label, str)
        assert isinstance(output, IPVertex)
        self.outputs.append((label, output, funcall))
        output.inputs.append((label, self, funcall))
        return


##  IDFBuilder
##
class IDFBuilder:

    def __init__(self, maxoverrides=1, dbg=None):
        self.maxoverrides = maxoverrides
        self.dbg = dbg
        self.graphs = []
        self.srcmap = {}
        self.gid2graph = {}
        self.funcalls = {}
        self.vtxs = {}
        return

    def __len__(self):
        return len(self.vtxs)

    # List all the vertexes.
    def __iter__(self):
        return iter(self.vtxs.values())

    # Load graphs.
    def load(self, path, fp=None):
        for graph in get_graphs(path):
            if graph.style == 'initializer': continue
            path = graph.klass.path
            if path not in self.srcmap:
                fid = len(self.srcmap)
                self.srcmap[path] = fid
                src = (fid, path)
                if fp is not None:
                    fp.write('+SOURCE %r\n' % (src,))
            self.graphs.append(graph)
            self.gid2graph[graph.name] = graph
        return

    # Get a source.
    def getsrc(self, node, resolve=True):
        if node.ast is None: return None
        if isinstance(node, DFGraph):
            path = node.klass.path
        else:
            path = node.graph.klass.path
        (_,start,end) = node.ast
        if resolve:
            fid = self.srcmap[path]
            return (fid, start, end)
        else:
            return (path, start, end)

    # Register a funcall.
    def addcall(self, x, y): # (caller, callee)
        if self.dbg is not None:
            self.dbg.write('# addcall %r: %r\n' % (x.graph.name, y))
        if y in self.funcalls:
            a = self.funcalls[y]
        else:
            a = self.funcalls[y] = []
        if x not in a:
            a.append(x)
        return

    # Create a IPVertex.
    def getvtx(self, node):
        if node in self.vtxs:
            vtx = self.vtxs[node]
        else:
            vtx = self.vtxs[node] = IPVertex(node)
            if self.dbg is not None:
                self.dbg.write('# getvtx %r: %s(%r)\n' %
                               (vtx, node.kind, node.data))
        return vtx

    def run(self):
        # Enumerate caller/callee relationships.
        for src in self.graphs:
            for node in src:
                if node.is_funcall():
                    funcs = node.data.split(' ')
                    for gid in funcs[:self.maxoverrides]:
                        self.addcall(node, gid)

        # Convert every node to IPVertex.
        for graph in self.graphs:
            for node in graph:
                if node.is_funcall():
                    funcs = node.data.split(' ')
                    for gid in funcs[:self.maxoverrides]:
                        if gid not in self.gid2graph: continue
                        func = self.gid2graph[gid]
                        for n1 in func.ins:
                            assert n1.kind == 'input'
                            label = n1.ref
                            if label in node.inputs:
                                self.getvtx(node.inputs[label]).connect(
                                    label, self.getvtx(n1), node)
                        for n1 in func.outs:
                            assert n1.kind == 'output'
                            vtx1 = self.getvtx(n1)
                            for (label,n2) in node.outputs:
                                assert n2.kind == 'receive'
                                assert n2.ref == label or n2.ref is None
                                vtx1.connect(label, self.getvtx(n2), node)
                vtx = self.getvtx(node)
                for (label,n1) in node.outputs:
                    vtx.connect(label, self.getvtx(n1))
        return

# main
def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-d] [-M maxoverrides] [graph ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'dM:')
    except getopt.GetoptError:
        return usage()
    debug = 0
    maxoverrides = 1
    for (k, v) in opts:
        if k == '-d': debug += 1
        elif k == '-M': maxoverrides = int(v)
    if not args: return usage()

    builder = IDFBuilder(maxoverrides=maxoverrides)
    for path in args:
        print('Loading: %r...' % path, file=sys.stderr)
        builder.load(path)

    builder.run()
    print('Read: %d sources, %d graphs, %d funcalls, %d IPVertexes' %
          (len(builder.srcmap), len(builder.graphs),
           sum( len(a) for a in builder.funcalls.values() ),
           len(builder.vtxs)),
          file=sys.stderr)
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))