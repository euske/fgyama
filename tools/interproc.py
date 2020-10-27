#!/usr/bin/env python
import sys
from graph import DFMethod, get_graphs


def clen(x):
    if x is None:
        return 0
    else:
        return len(x)

def clist(x):
    if x is None:
        return []
    else:
        return list(x)


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
        self.inputs = []   # [(label,node,funcall), ...]
        self.outputs = []  # [(label,node,funcall), ...]
        return

    def __repr__(self):
        return (f'<IPVertex({self.vid})>')

    def connect(self, label, output, funcall=None):
        #print(f'# connect: {self}-{label}-{output}')
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
        self.methods = []
        self.srcmap = {}
        self.gid2method = {}
        self.funcalls = {}
        self.vtxs = {}
        return

    def __len__(self):
        return len(self.vtxs)

    # List all the vertexes.
    def __iter__(self):
        return iter(self.vtxs.values())

    # Load methods.
    def load(self, path, fp=None, filter=None):
        for method in get_graphs(path):
            if method.style == 'initializer': continue
            if filter is not None and not filter(method): continue
            path = method.klass.path
            if path not in self.srcmap:
                fid = len(self.srcmap)
                self.srcmap[path] = fid
                src = (fid, path)
                if fp is not None:
                    fp.write(f'+SOURCE {src}\n')
            self.methods.append(method)
            self.gid2method[method.name] = method
        return

    # Get a source.
    def getsrc(self, node, resolve=True):
        if node.ast is None: return None
        if isinstance(node, DFMethod):
            path = node.klass.path
        else:
            path = node.method.klass.path
        (_,start,end) = node.ast
        if resolve:
            fid = self.srcmap[path]
            return (fid, start, end)
        else:
            return (path, start, end)

    # Register a funcall.
    def addcall(self, x, y): # (caller, callee)
        if self.dbg is not None:
            self.dbg.write(f'# addcall {x.method.name}: {y}\n')
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
                self.dbg.write(f'# getvtx {vtx}: {node.kind}({node.data!r})\n')
        return vtx

    def run(self):
        # Enumerate caller/callee relationships.
        for src in self.methods:
            for node in src:
                if node.is_funcall():
                    funcs = node.data.split(' ')
                    for gid in funcs[:self.maxoverrides]:
                        self.addcall(node, gid)

        # Convert every node to IPVertex.
        for method in self.methods:
            for node in method:
                if node.is_funcall():
                    funcs = node.data.split(' ')
                    for callee in funcs[:self.maxoverrides]:
                        if callee not in self.gid2method: continue
                        for n1 in self.gid2method[callee]:
                            if n1.kind == 'input':
                                label = n1.ref
                                if label in node.inputs:
                                    n0 = node.inputs[label]
                                    vtx0 = self.getvtx(n0)
                                    vtx0.connect(label, self.getvtx(n1), node)
                                    #print(f'# send: {n0} {label} -> {n1}')
                            elif n1.kind == 'output':
                                vtx1 = self.getvtx(n1)
                                for (label,n2) in node.outputs:
                                    assert n2.kind in ('receive', 'throw')
                                    assert n2.ref == label or n2.ref is None
                                    if not label: label = '#return'
                                    if n1.ref != label: continue
                                    vtx1.connect(label, self.getvtx(n2), node)
                                    #print(f'# recv: {n1} -> {label} {n2}')
                vtx = self.getvtx(node)
                for (label,n1) in node.outputs:
                    vtx.connect(label, self.getvtx(n1))
        return

# main
def main(argv):
    import fileinput
    import getopt
    def usage():
        print(f'usage: {argv[0]} [-d] [-M maxoverrides] [graph ...]')
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
        print(f'Loading: {path!r}...', file=sys.stderr)
        builder.load(path)

    builder.run()
    nfuncalls = sum( len(a) for a in builder.funcalls.values() )
    print(f'Read: {len(builder.srcmap)} sources, {len(builder.methods)} methods, {nfuncalls} funcalls, {len(builder.vtxs)} IPVertexes',
          file=sys.stderr)
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
