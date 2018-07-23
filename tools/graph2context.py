#!/usr/bin/env python
import sys
from subprocess import Popen, PIPE
from graph import DFGraph, DFNode
from graph import get_graphs

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

    # load graphs
    graphs = {}
    for path in args:
        for graph in get_graphs(path):
            graphs[graph.name] = graph
    print ('graphs: %r' % len(graphs), file=sys.stderr)

    # enumerate links
    linkto = {}
    linkfrom = {}
    def add(x, y):
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
                    if name in graphs:
                        add(src.name, name)
                        break
                else:
                    # not found
                    for name in node.data.split(' '):
                        add(src.name, name)
            elif node.kind == 'new':
                name = node.data
                add(src.name, name)

    # find starts
    starts = []
    for src in graphs.values():
        if src.name not in linkfrom:
            starts.append(src.name)

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

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
