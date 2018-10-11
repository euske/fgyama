#!/usr/bin/env python
import sys
from graph import get_graphs

IGNORED = frozenset(['ref','assign','input','output'])
def getfeat(node):
    if node.kind is None or node.kind in IGNORED:
        return None
    elif node.data is None:
        return node.kind
    else:
        return '%s:%s' % (node.kind, node.data)


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

    def __init__(self, node):
        IPVertex.vid_base += 1
        self.vid = self.vid_base
        self.node = node
        self.inputs = []
        self.outputs = []
        return

    def __repr__(self):
        return ('<IPVertex(%d)>' % (self.vid))

    def connect(self, label, vtx):
        #print('# connect: %r -%s-> %r' % (self, label, vtx))
        assert vtx is not self
        assert isinstance(label, str)
        assert isinstance(vtx, IPVertex)
        self.outputs.append((label, vtx))
        vtx.inputs.append((label, self))
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

    def enum(self, name, direction, label, maxlen, chain=None):
        v = getfeat(self.node)
        if v is not None:
            chain = CLink(label+':'+v, chain)
            s = ' '.join(reversed(list(chain)))
            print('%s %s %s' % (name, direction, s))
        if chain is None or len(chain) < maxlen:
            if direction < 0:
                vtxs = self.inputs
            else:
                vtxs = self.outputs
            for (label, vtx) in vtxs:
                vtx.enum(name, direction, label, maxlen, chain)
        return chain

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

    # enum_dataflow
    graph2info = {}
    def ff(x):
        if x == 'obj':
            return '#this'
        elif x.startswith('arg'):
            return '#'+x
        else:
            return x
    def enum_dataflow(graph, inputs, chain=None):
        if chain is None:
            ind = ''
        else:
            ind = '  '*len(chain)
        print ('#%s enum_dataflow(%r)' % (ind, graph.name), file=sys.stderr)
        ind += ' '
        # Convert all nodes to IPVertex.
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
            v1 = vtxs[node]
            # Connect data paths.
            for (label,prev) in node.inputs.items():
                #print('+', node, label, prev)
                if label.startswith('_'): continue
                v0 = vtxs[prev]
                if prev.kind in ('call', 'new'):
                    # Receive a return value from the callee.
                    rtns[prev] = v0
                v0.connect(label, v1)
            if node.kind in ('call', 'new'):
                # Send a passing value to the callee.
                args = { ff(label): vtxs[src] for (label,src)
                         in node.inputs.items() if not label.startswith('_') }
                calls[node] = args
        for (funcall,args) in calls.items():
            print ('#%s call(%r, %r)' % (ind, funcall.data, args),
                   file=sys.stderr)
        for (funcall,v0) in rtns.items():
            print ('#%s rtn(%r, %r)' % (ind, funcall.data, v0),
                   file=sys.stderr)
        # Store the input/output info.
        if graph.name in graph2info:
            info = graph2info[graph.name]
        else:
            info = graph2info[graph.name] = []
        info.append((inputs, outputs))
        # Embed inter-procedural graphs.
        if chain is None or graph not in chain:
            chain = CLink(graph, chain)
            for (funcall,rcver) in rtns.items():
                assert funcall in calls
                args = calls[funcall]
                for name in funcall.data.split(' '):
                    if name in graphs:
                        vals = enum_dataflow(graphs[name], args, chain)
                        for (label,sender) in vals.items():
                            sender.connect(label, rcver)
                        break
        return outputs

    # Find start nodes.
    for graph in graphs.values():
        if graph.name not in linkfrom:
            print('# start: %r' % graph.name, file=sys.stderr)
            inputs = { node.ref: IPVertex(node) for node in graph.ins }
            enum_dataflow(graph, inputs)

    for (name,info) in graph2info.items():
        for (inputs,outputs) in info:
            for (label,vtx) in inputs.items():
                vtx.enum(name, -1, label, maxlen)
            for (label,vtx) in outputs.items():
                vtx.enum(name, +1, label, maxlen)
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
