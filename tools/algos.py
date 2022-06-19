#!/usr/bin/env python

##  Cons
##
class Cons:

    @staticmethod
    def len(x):
        if x is None:
            return 0
        else:
            return len(x)

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

    def equals(self, c1):
        c0 = self
        while c0 is not c1:
            if c0 is None or c1 is None: return False
            if c0.car != c1.car: return False
            (c0,c1) = (c0.cdr, c1.cdr)
        return True

    @classmethod
    def fromseq(self, seq):
        c = None
        for x in seq:
            c = Cons(x, c)
        return c


##  SCC (Tarjan's)
##
class SCC:

    MAXNODES = 10

    def __init__(self, cid, nodes):
        self.cid = cid
        self.nodes = nodes
        self.linkto = set()
        self.linkfrom = set()
        return

    def __repr__(self):
        a = self.nodes[:self.MAXNODES]
        if len(a) < len(self.nodes):
            a = a + ['...']
        return f'[{", ".join(map(str, a))}]'

    def __len__(self):
        return len(self.nodes)

    def __iter__(self):
        return iter(self.nodes)

    @classmethod
    def fromnodes(klass, nodes, getnodes):
        node2cpt = {}
        cpts = []
        # Tarjan's algorithm.
        stack = []
        index = {}
        lowlink = {}
        onstack = set()
        def visit(v0):
            i = len(index)
            index[v0] = i
            lowlink[v0] = i
            stack.append(v0)
            onstack.add(v0)
            for v1 in getnodes(v0):
                if v1 not in index:
                    visit(v1)
                    lowlink[v0] = min(lowlink[v0], lowlink[v1])
                elif v1 in onstack:
                    lowlink[v0] = min(lowlink[v0], index[v1])
            if index[v0] == lowlink[v0]:
                a = []
                while True:
                    v = stack.pop()
                    onstack.remove(v)
                    a.append(v)
                    if v is v0: break
                cpt = klass(len(cpts)+1, a)
                cpts.append(cpt)
                for v in a:
                    node2cpt[v] = cpt
        for node in nodes:
            if node not in node2cpt:
                visit(node)
        # fixate
        for cpt0 in cpts:
            for node0 in cpt0.nodes:
                for node1 in getnodes(node0):
                    cpt1 = node2cpt[node1]
                    if cpt1 is cpt0: continue
                    cpt0.linkto.add(cpt1)
                    cpt1.linkfrom.add(cpt0)
        return (cpts, node2cpt)


##  topsort
##
def topsort(nodes, getnodes):
    incoming = { n0: [] for n0 in nodes }
    for n0 in incoming:
        for n1 in getnodes(n0):
            incoming[n1].append(n0)
    out = []
    while incoming:
        for (n0,z) in incoming.items():
            if not z: break
        else:
            raise ValueError('Cycle detected', incoming)
        del incoming[n0]
        out.append(n0)
        for n1 in getnodes(n0):
            incoming[n1].remove(n0)
    return out
