#!/usr/bin/env python
import sys
from graph import get_graphs

IGNORED = frozenset([None, 'ref', 'assign', 'input', 'output'])
def getfeat(node):
    if node.kind in IGNORED:
        return None
    elif node.kind == 'assignop' and node.data == '=':
        return None
    elif node.data is None:
        return node.kind
    elif node.kind == 'call':
        (data,_,_) = node.data.partition(' ')
        return '%s:%s' % (node.kind, data)
    else:
        return '%s:%s' % (node.kind, node.data)

def getarg(label):
    if label == 'obj':
        return '#this'
    elif label.startswith('arg'):
        return '#'+label
    else:
        return label


##  Chain Link
##
class CLink:

    def __init__(self, obj, prev=None):
        self.obj = obj
        self.prev = prev
        self.length = 1
        if (prev is not None):
            self.length = prev.length+1
        return

    def __len__(self):
        return self.length

    def __iter__(self):
        c = self
        while c is not None:
            yield c.obj
            c = c.prev
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
    srcmap = {}
    feats = {}

    @classmethod
    def register(klass, name):
        if name not in klass.srcmap:
            fid = len(klass.srcmap)
            klass.srcmap[name] = fid
        return

    @classmethod
    def dumpsrcs(klass):
        return klass.srcmap.items()

    @classmethod
    def dumpfeats(klass):
        return sorted(klass.feats.items(), key=lambda x:x[1], reverse=True)

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
        #print('# connect: %r -%s-> %r' % (self, label, outvtx))
        assert output is not self
        assert isinstance(label, str)
        assert isinstance(output, IPVertex)
        self.outputs.append((label, output))
        output.inputs.append((label, self))
        return

    def dump(self, direction, label, traversed, indent=0):
        print('  '*indent+label+' -> '+str(self.node))
        if self in traversed: return
        traversed.add(self)
        if direction < 0:
            vtxs = self.inputs
        else:
            vtxs = self.outputs
        for (label, vtx) in vtxs:
            vtx.follow(direction, label, traversed, indent+1)
        return

    def enum(self, direction, label, maxlen, chain=None):
        feat = getfeat(self.node)
        if feat is not None:
            v = ('%s,%s' % (label, feat))
            if v not in self.feats:
                self.feats[v] = 0
            self.feats[v] += 1
            if self.node.ast is not None:
                (_,s,e) = self.node.ast
                fid = self.srcmap[self.node.graph.src]
                v = ('%s,%s,%s,%s' % (v, s, e, fid))
            chain = CLink(v, chain)
            yield ' '.join(reversed(list(chain)))
        if chain is None or len(chain) < maxlen:
            if direction < 0:
                vtxs = self.inputs
            else:
                vtxs = self.outputs
            for (label, vtx) in vtxs:
                for z in vtx.enum(direction, label, maxlen, chain):
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
    for (k, v) in opts:
        if k == '-d': debug += 1
        elif k == '-m': maxlen = int(v)
    if not args: return usage()

    # Load graphs.
    graphs = {}
    for path in args:
        for graph in get_graphs(path):
            IPVertex.register(graph.src)
            graphs[graph.name] = graph
    print('# graphs: %r' % len(graphs), file=sys.stderr)

    # Enumerate caller/callee relationships.
    linkto = {}                 # callee
    linkfrom = {}               # caller
    def link(x, y): # (caller, callee)
        if x in linkto:
            a = linkto[x]
        else:
            a = linkto[x] = []
        if y not in a:
            a.append(y)
        if y in linkfrom:
            a = linkfrom[y]
        else:
            a = linkfrom[y] = []
        if x not in a:
            a.append(x)
        return
    for src in graphs.values():
        for node in src:
            if node.kind == 'call':
                for name in node.data.split(' '):
                    # In order to stop the number of possible contexts grow
                    # exponentially, the only first function is used.
                    if name in graphs:
                        link(src.name, name)
                        break
                else:
                    # This function is not defined within the source code.
                    for name in node.data.split(' '):
                        link(src.name, name)
            elif node.kind == 'new':
                name = node.data
                link(src.name, name)

    # trace dataflow
    def trace(graph, inputs, chain=None):
        if chain is None:
            ind = ''
        else:
            ind = '  '*len(chain)
        print ('#%s trace(%r)' % (ind, graph.name), file=sys.stderr)
        ind += ' '
        # Copy all nodes as IPVertex.
        vtxs = {}
        for node in graph:
            vtxs[node] = IPVertex(node)
        # Receive a passed value from the caller.
        for node in graph.ins:
            if node.ref in inputs:
                inputs[node.ref].connect('RECV', vtxs[node])
        print ('#%s inputs=%r' % (ind, inputs), file=sys.stderr)
        outputs = {}
        for node in graph.outs:
            outputs[node.ref] = vtxs[node]
        print ('#%s outputs=%r' % (ind, outputs), file=sys.stderr)
        # calls: {funcall: {key:value}}
        calls = {}
        # rtns: {funcall: {key:value}}
        rtns = {}
        for node in graph:
            vnode = vtxs[node]
            # Connect data paths.
            for (label,prev) in node.inputs.items():
                if label.startswith('_'): continue
                if prev.kind in ('call', 'new'):
                    # Receive a return value from the callee.
                    if prev in rtns:
                        a = rtns[prev]
                    else:
                        a = rtns[prev] = []
                    a.append(vnode)
                else:
                    vprev = vtxs[prev]
                    vprev.connect(label, vnode)
            if node.kind in ('call', 'new'):
                # Send a passing value to the callee.
                assert node not in calls
                args = { getarg(label): vtxs[src] for (label,src)
                         in node.inputs.items() if not label.startswith('_') }
                calls[node] = args
        for (funcall,args) in calls.items():
            print ('#%s %s(%r, %r)' % (ind, funcall.kind, funcall.data, args),
                   file=sys.stderr)
        for (funcall,vprevs) in rtns.items():
            print ('#%s rtn(%r, %r)' % (ind, funcall.data, vprevs),
                   file=sys.stderr)
        # Store the input/output info.
        if graph in graph2info:
            info = graph2info[graph]
        else:
            info = graph2info[graph] = []
        info.append((inputs, outputs))
        # Embed inter-procedural graphs.
        if chain is None or graph not in chain:
            chain = CLink(graph, chain)
            for (funcall,rcvers) in rtns.items():
                assert funcall in calls
                args = calls[funcall]
                for name in funcall.data.split(' '):
                    if name in graphs:
                        vals = trace(graphs[name], args, chain)
                        for (label,sender) in vals.items():
                            for rcver in rcvers:
                                sender.connect(label, rcver)
                        break
        return outputs

    # Find start nodes.
    for graph in graphs.values():
        if graph.name in linkfrom: continue
        print('# start: %r' % graph.name, file=sys.stderr)
        inputs = { node.ref: IPVertex(node) for node in graph.ins }
        graph2info = {}
        trace(graph, inputs)
        for (graph,info) in graph2info.items():
            if graph.ast is not None:
                (_,s,e) = graph.ast
                fid = IPVertex.srcmap[graph.src]
                name = ('%s,%s,%s,%s' % (graph.name, s, e, fid))
            else:
                name = graph.name
            for (inputs,outputs) in info:
                for (label,vtx) in inputs.items():
                    for feats in vtx.enum(-1, label, maxlen):
                        print('+PATH %s -1 %s' % (name, feats))
                for (label,vtx) in outputs.items():
                    for feats in vtx.enum(+1, label, maxlen):
                        print('+PATH %s +1 %s' % (name, feats))

    for (name,fid) in IPVertex.dumpsrcs():
        print('+SOURCE %d %s' % (fid, name))
    for (feat,n) in IPVertex.dumpfeats():
        print('+FEAT %d %s' % (n, feat))

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
