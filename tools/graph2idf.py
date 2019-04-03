#!/usr/bin/env python
import sys
from graph import get_graphs


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
        #print('# connect: %r-%s-%r' % (self, label, output))
        #assert output is not self
        assert isinstance(label, str)
        assert isinstance(output, IPVertex)
        self.outputs.append((label, output))
        output.inputs.append((label, self))
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

    # Load graphs.
    def load(self, path, fp=None):
        for graph in get_graphs(path):
            if graph.style == 'initializer': continue
            if graph.src not in self.srcmap:
                fid = len(self.srcmap)
                self.srcmap[graph.src] = fid
                src = (fid, graph.src)
                if fp is not None:
                    fp.write('+SOURCE %r\n' % (src,))
            self.graphs.append(graph)
            self.gid2graph[graph.name] = graph
        return

    # Get a source.
    def getsrc(self, node):
        if node.ast is None: return None
        src = node.graph.src
        fid = self.srcmap[src]
        (_,loc,length) = node.ast
        return (fid, loc, length)

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
                if node.kind == 'call':
                    funcs = node.data.split(' ')
                    for gid in funcs[:self.maxoverrides]:
                        self.addcall(node, gid)
                elif node.kind == 'new':
                    self.addcall(node, node.data)

        # Convert every node to IPVertex.
        for graph in self.graphs:
            for node in graph:
                if node.kind in ('call', 'new'):
                    funcs = node.data.split(' ')
                    for gid in funcs[:self.maxoverrides]:
                        if gid not in self.gid2graph: continue
                        graph = self.gid2graph[gid]
                        for n1 in graph.ins:
                            label = n1.ref
                            if label in node.inputs:
                                self.getvtx(node.inputs[label]).connect(
                                    label, self.getvtx(n1))
                        for n1 in graph.outs:
                            for (label,n2) in node.outputs:
                                if label == 'update' and n1.ref == n2.ref:
                                    self.getvtx(n1).connect(
                                        label, self.getvtx(n2))
                                elif n1.ref == '#return':
                                    self.getvtx(n1).connect(
                                        label, self.getvtx(n2))
                vtx = self.getvtx(node)
                for (label,n1) in node.outputs:
                    vtx.connect(label, self.getvtx(n1))
        return
