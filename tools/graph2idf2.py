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
    mincall = 2
    for (k, v) in opts:
        if k == '-d': debug += 1
        elif k == '-m': maxlen = int(v)
    if not args: return usage()

    # Load graphs.
    graphs = []
    srcmap = {}
    gid2graph = {}
    for path in args:
        print('# loading: %r...' % path, file=sys.stderr)
        for graph in get_graphs(path):
            if graph.src not in srcmap:
                fid = len(srcmap)
                srcmap[graph.src] = fid
            graphs.append(graph)
            gid2graph[graph.name] = graph
    for (name,fid) in srcmap.items():
        print('+SOURCE %d %s' % (fid, name))

    print('# graphs: %r' % len(graphs), file=sys.stderr)

    # Enumerate caller/callee relationships.
    caller = {}
    def addcall(x, y): # (caller, callee)
        if y in caller:
            a = caller[y]
        else:
            a = caller[y] = []
        if x not in a:
            a.append(x)
        return
    for src in graphs:
        for node in src:
            if node.kind == 'call':
                for gid in node.data.split(' '):
                    addcall(node, gid)
            elif node.kind == 'new':
                gid = node.data
                addcall(node, gid)

    def getchain(node, label, chain):
        feat = getfeat(node)
        if feat is None: return chain
        v = ('%s,%s' % (label, feat))
        if node.ast is not None:
            src = node.graph.src
            fid = srcmap[src]
            (_,s,e) = node.ast
            v += (',%s,%s,%s' % (fid, s, e))
        return CLink(v, chain)

    def trace(n0, label, chain0=None):
        chain1 = getchain(n0, label, chain0)
        if chain1 is not None and maxlen <= len(chain1):
            yield ' '.join(reversed(list(chain1)))
        else:
            for (label, n1) in n0.outputs:
                for z in trace(n1, label, chain1):
                    yield z
        return

    for graph in graphs:
        gid = graph.name
        if gid not in caller or len(caller[gid]) < mincall: continue
        if graph.ast is not None:
            fid = srcmap[graph.src]
            (_,s,e) = graph.ast
            name = ('%s,%s,%s,%s' % (gid, fid, s, e))
        else:
            name = gid
        print('# start: %r' % gid, file=sys.stderr)
        for funcall in caller[gid]:
            for (label,n) in funcall.outputs:
                if label == 'update': continue
                for feats in trace(n, label):
                    print('+PATH %s forw %s' % (name, feats))

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
