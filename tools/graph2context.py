#!/usr/bin/env python
import sys
from subprocess import Popen, PIPE
from graph import DFGraph, DFNode
from graph import get_graphs

def get_args(graph):
    for node in graph.nodes.values():
        if node.kind == 'arg':
            assert node.data.startswith('arg')
            i = int(node.data[3:])
            yield (node, i)
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
        for node in src.nodes.values():
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
        def enum_context(src, path):
            if src in path: return
            path = path+[src]
            if src in linkto:
                for dst in linkto[src]:
                    enum_context(dst, path)
            else:
                print (' '.join(path))
        for name in starts:
            print ('# start: %r' % name)
            enum_context(name, [])

    # enum dataflow
    def enum_dataflow(graph, args):
        calls = {}
        rtrn = []
        def traverse(src, paths):
            paths = [ (src,p) for p in paths ]
            for (label,dst) in src.outputs:
                if label.startswith('arg'):
                    i = int(label[3:])
                    if dst.kind == 'call':
                        for name in dst.data.split(' '):
                            if name in graphs:
                                if name not in calls:
                                    calls[name] = {}
                                calls[name][i] = paths
                                break
                        else:
                            for name in dst.data.split(' '):
                                if name not in calls:
                                    calls[name] = {}
                                calls[name][i] = paths
                    elif dst.kind == 'new':
                        name = dst.data
                        if name not in calls:
                            calls[name] = {}
                        calls[name][i] = paths
                elif dst.kind == 'return':
                    rtrn.extend(paths)
                else:
                    traverse(dst, paths)
        for (node, i) in get_args(graph):
            if i in args:
                paths = [args[i]]
            else:
                paths = []
            traverse(node, paths)
        print('calls:', calls)
        print('rtrn:', rtrn)

    for name in starts:
        print ('# start: %r' % name)
        graph = graphs[name]
        args = {}
        for (node, i) in get_args(graph):
            args[i] = node
        enum_dataflow(graph, args)

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
