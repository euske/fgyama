#!/usr/bin/env python
import sys
from graph import get_graphs

IGNORED = frozenset([None, 'ref', 'assign', 'input', 'output'])
def getfeat(label, node):
    if node.kind in IGNORED:
        return None
    elif node.kind == 'assignop' and node.data == '=':
        return None
    elif node.kind == 'join' and label != 'cond':
        return None
    elif node.ref == '#exception':
        return None
    elif node.data is None:
        return '%s:%s' % (label, node.kind)
    elif node.kind == 'call':
        (data,_,_) = node.data.partition(' ')
        return '%s:%s:%s' % (label, node.kind, data)
    else:
        return '%s:%s:%s' % (label, node.kind, node.data)


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
    mincall = 1
    maxoverrides = 1
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
                print('+SOURCE %d %s' % (fid, graph.src))
            graphs.append(graph)
            gid2graph[graph.name] = graph

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
            if node.kind in ('call', 'new'):
                for gid in node.data.split(' '):
                    addcall(node, gid)

    def getchain(label, node, chain):
        feat = getfeat(label, node)
        if feat is None: return chain
        if node.ast is not None:
            src = node.graph.src
            fid = srcmap[src]
            (_,s,e) = node.ast
            feat += (',%s,%s,%s' % (fid, s, e))
        return CLink(feat, chain)

    def trace(out, label, n0, done, chain0=None, level=0):
        #print('[%d] trace' % level, label, n0)
        if n0 in done: return
        done.add(n0)
        if label is None:
            chain1 = chain0
        elif label == 'update' or label.startswith('_'):
            return
        else:
            if n0.kind in ('call', 'new'):
                args = set( label for label in n0.inputs.keys()
                            if not label.startswith('_') )
                funcs = n0.data.split(' ')
                for gid in funcs[:maxoverrides]:
                    if gid not in gid2graph: continue
                    graph = gid2graph[gid]
                    for n1 in graph.ins:
                        label = n1.ref
                        if label not in args: continue
                        trace(out, label, n1, done, chain0, level+1)
            chain1 = getchain(label, n0, chain0)
            if chain1 != chain0:
                out.append(list(chain1))
                if maxlen <= len(chain1): return
        for (label, n1) in n0.outputs:
            trace(out, label, n1, done, chain1, level+1)
        if n0.kind == 'output':
            gid = n0.graph.name
            if gid in caller:
                for nc in caller[n0.graph.name]:
                    for (label, n1) in nc.outputs:
                        trace(out, label, n1, done, chain1, level+1)
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
            out = []
            trace(out, None, funcall, set())
            for feats in out:
                print('+PATH %s forw %s' % (name, ' '.join(reversed(feats))))

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
