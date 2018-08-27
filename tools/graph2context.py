#!/usr/bin/env python
import sys
from subprocess import Popen, PIPE
from graph import DFGraph, DFNode
from graph import get_graphs

class IPVertex:

    def __init__(self, node):
        self.node = node
        self.inputs = []
        self.outputs = []
        return

    def __repr__(self):
        return '<IPVertex: %r>' % self.node

    def connect(self, label, vtx):
        self.outputs.append((label, vtx))
        vtx.inputs.append((label, self))
        return

    def follow(self, traversed, indent=0):
        for (label, vtx) in self.outputs:
            print(' '*indent, '-'+label+' '+str(vtx.node))
            if vtx not in traversed:
                traversed.add(vtx)
                vtx.follow(traversed, indent+1)
        return

def get_ins(graph):
    for node in graph:
        if node.kind == 'arg':
            assert node.data.startswith('arg')
            label = node.data
            yield (label, node)
    return

def get_outs(graph):
    for node in graph:
        if node.kind == 'return':
            yield node
    return

def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-o output] [graph ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'o:')
    except getopt.GetoptError:
        return usage()
    output = sys.stdout
    for (k, v) in opts:
        if k == '-o': output = open(v, 'w')
    if not args: return usage()

    # Load graphs.
    graphs = {}
    for path in args:
        for graph in get_graphs(path):
            graphs[graph.name] = graph
    print ('graphs: %r' % len(graphs), file=sys.stderr)

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

    # Find start nodes.
    starts = []
    for src in graphs.values():
        if src.name not in linkfrom:
            starts.append(src.name)

    if 0:
        # enum contexts
        def enum_context(src, ctx):
            if src in ctx: return
            ctx = ctx+[src]
            if src in linkto:
                for dst in linkto[src]:
                    enum_context(dst, ctx)
            else:
                print (' '.join(ctx))
        for name in starts:
            print ('# start: %r' % name)
            enum_context(name, [])

    # enum dataflow
    graph2info = {}
    def enum_dataflow(graph, inputs, ctx):
        print ('# enum_dataflow(%r, %r)' % (graph.name, inputs))
        vtxs = {}
        for node in graph:
            vtxs[node] = IPVertex(node)
        sends = {}
        recvs = {}
        outputs = []
        for node in graph:
            v1 = vtxs[node]
            if node.kind == 'arg':
                label = node.data
                inputs[label].connect('pass', v1)
            elif node.kind in ('call', 'new'):
                funcall = node
                sends[funcall] = args = {}
                for (label,src) in funcall.inputs.items():
                    args[label] = vtxs[src]
            else:
                for (label,src) in node.inputs.items():
                    v0 = vtxs[src]
                    if src.kind in ('call', 'new'):
                        funcall = src
                        recvs[funcall] = v0
                    v0.connect(label, v1)
                if node.kind == 'return':
                    outputs.append(v1)
        if graph.name in graph2info:
            info = graph2info[graph.name]
        else:
            info = graph2info[graph.name] = []
        info.append((inputs, outputs))
        if graph.name not in ctx:
            ctx = ctx + [graph.name]
            for (funcall,recv) in recvs.items():
                assert funcall in sends
                args = sends[funcall]
                for name in funcall.data.split(' '):
                    if name in graphs:
                        rtns = enum_dataflow(graphs[name], args, ctx)
                        for rtn in rtns:
                            rtn.connect('return', recv)
                        break
                    else:
                        # fallback
                        for (label,vtx) in args.items():
                            vtx.connect(label, recv)
        return outputs

    for name in starts:
        print('# start: %r' % name)
        graph = graphs[name]
        inputs = {}
        for (label,node) in get_ins(graph):
            inputs[label] = IPVertex(node)
        enum_dataflow(graph, inputs, [])

    for (name,info) in graph2info.items():
        for (inputs,outputs) in info:
            print (name,inputs,outputs)
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
