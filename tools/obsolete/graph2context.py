#!/usr/bin/env python
import sys
from graph import get_graphs

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

# main
def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-d] [graph ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'd')
    except getopt.GetoptError:
        return usage()
    debug = 0
    for (k, v) in opts:
        if k == '-d': debug += 1
    if not args: return usage()

    # Load graphs.
    graphs = {}
    for path in args:
        for graph in get_graphs(path):
            graphs[graph.name] = graph

    print ('# graphs: %r' % len(graphs), file=sys.stderr)

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

    # enum contexts
    def enum_context(src, chain=None):
        if chain is not None and src in chain: return
        chain = CLink(src, chain)
        if 2 <= len(chain):
            print (' '.join(chain))
        if src in linkto:
            for dst in linkto[src]:
                enum_context(dst, chain)
        return

    # Find start nodes.
    for graph in graphs.values():
        if graph.name not in linkfrom:
            print ('# start: %r' % graph.name, file=sys.stderr)
            enum_context(graph.name)
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
