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

    graphs = {}
    for path in args:
        for graph in get_graphs(path):
            graphs[graph.name] = graph
    for graph in graphs.values():
        for node in graph.nodes.values():
            if (node.kind == 'call'):
                for name in node.data.split(' '):
                    try:
                        dst = graphs[name]
                        #print(graph.name, dst.name)
                    except KeyError:
                        print('!notfound', name)
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
