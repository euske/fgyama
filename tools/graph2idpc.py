#!/usr/bin/env python
import sys
from graph import get_graphs

IGNORED = frozenset([None, 'ref', 'assign', 'input', 'output', 'begin', 'repeat'])
def getfeat(label, node):
    if node.kind in IGNORED:
        return None
    elif node.kind == 'assignop' and node.data == '=':
        return None
    elif node.kind in ('join','end') and label != 'cond':
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

def skiplink(label, node):
    if label is None:
        return False
    if label.startswith('_'):
        return True
    if label in ('update',):
        return True
    return False


##  Cons
##
class Cons:

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

    def connect(self, feat, output):
        #print('# connect: %r %s %r' % (self, feat, output))
        #assert output is not self
        assert isinstance(feat, str)
        assert isinstance(output, IPVertex)
        self.outputs.append((feat, output))
        output.inputs.append((feat, self))
        return

    def enum(self, direction, prev0=None, done=None):
        if done is not None and self in done: return
        done = Cons(self, done)
        if direction < 0:
            vtxs = self.inputs
        else:
            vtxs = self.outputs
        for (feat,vtx) in vtxs:
            prev = prev0
            if feat is not None:
                prev = Cons((feat, vtx.node), prev0)
                yield prev
            for z in vtx.enum(direction, prev, done):
                yield z
        return


# main
def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-d] [-o output] [-m maxlen] [-n mincall] '
              '[-M maxoverride] [graph ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'do:m:n:M:')
    except getopt.GetoptError:
        return usage()
    debug = 0
    maxlen = 50
    mincall = 2
    maxoverride = 1
    maxfanout = 100
    output = None
    for (k, v) in opts:
        if k == '-d': debug += 1
        elif k == '-o': output = v
        elif k == '-m': maxlen = int(v)
        elif k == '-n': mincall = int(v)
        elif k == '-M': maxoverride = int(v)
    if not args: return usage()

    if output is None:
        fp = sys.stdout
    else:
        fp = open(output, 'w')
    if 0 < debug:
        dbg = sys.stderr
    else:
        dbg = fp

    # Load graphs.
    graphs = []
    srcmap = {}
    gid2graph = {}
    for path in args:
        print('Loading: %r...' % path, file=sys.stderr)
        for graph in get_graphs(path):
            if graph.style == 'initializer': continue
            if graph.src not in srcmap:
                fid = len(srcmap)
                srcmap[graph.src] = fid
                src = (fid, graph.src)
                if debug == 0:
                    fp.write('+SOURCE %r\n' % (src,))
            graphs.append(graph)
            name = graph.name
            i = name.find('.<init>')
            if 0 <= i:
                name = name[:i+7]
            gid2graph[name] = graph

    # Enumerate caller/callee relationships.
    funcalls = {}
    def addcall(x, y): # (caller, callee)
        if y in funcalls:
            a = funcalls[y]
        else:
            a = funcalls[y] = []
        if x not in a:
            a.append(x)
        return
    for src in graphs:
        for node in src:
            if node.kind == 'call':
                funcs = node.data.split(' ')
                for gid in funcs[:maxoverride]:
                    addcall(node, gid)
            elif node.kind == 'new':
                addcall(node, node.data+'.<init>')

    print('Read: %d sources, %d graphs, %d funcalls' %
          (len(srcmap), len(graphs), sum( len(a) for a in funcalls.values() )),
          file=sys.stderr)

    def trace(out, v0, n0, label, n1, length=0, done=None, caller=None):
        if maxlen <= length: return
        if skiplink(label, n1): return
        if done is not None and n1 in done: return
        feat = getfeat(label, n1)
        if debug:
            print('[trace: %s]' % n1.graph.name, v0, n1, feat)
        if feat is None:
            v1 = v0
            length += 1
        elif n1 in out:
            v1 = out[n1]
            v0.connect(feat, v1)
            return
        else:
            v1 = out[n1] = IPVertex(n1)
            v0.connect(feat, v1)
            length += 10
        done = Cons(n1, done)
        if n1.kind in ('call', 'new'):
            caller1 = Cons(n1, caller)
            funcs = n1.data.split(' ')
            for gid in funcs[:maxoverride]:
                if gid not in gid2graph: continue
                graph = gid2graph[gid]
                if 2 <= debug:
                    print(' [funcall]', gid, graph.ins)
                for n2 in graph.ins:
                    arg = n2.ref
                    if arg != '#this' and not arg.startswith('#arg'): continue
                    if arg not in n1.inputs: continue
                    if n1.inputs[arg] is not n0: continue
                    trace(out, v0, n0, arg, n2, length, done, caller1)
        for (label, n2) in n1.outputs[:maxfanout]:
            trace(out, v1, n1, label, n2, length, done, caller)
        if n1.kind == 'output':
            if caller is not None:
                #print(' ', v0, 'return', n1.graph.name)
                for (label, n2) in caller.car.outputs[:maxfanout]:
                    trace(out, v1, n0, label, n2, length, done, caller.cdr)
        return

    def getsrc(node):
        if node.ast is None: return None
        src = node.graph.src
        fid = srcmap[src]
        (_,loc,length) = node.ast
        return (fid, loc, length)

    nents = 0
    for (gid,nodes) in funcalls.items():
        if '.toString()' in gid: continue
        if '.equals(L' in gid: continue
        if len(nodes) < mincall: continue
        src = None
        if gid in gid2graph:
            graph = gid2graph[gid]
            if graph.ast is not None:
                fid = srcmap[graph.src]
                (_,loc,length) = graph.ast
                src = (fid, loc, length)
        dbg.write('# gid: %r\n' % gid)
        data = (gid, src)
        fp.write('+ITEM %r\n' % (data,))
        for funcall in nodes:
            caller = funcall.graph.name
            dbg.write('#   at %r\n' % caller)
            out = {}
            v1 = IPVertex(funcall)
            for (label,n) in funcall.outputs:
                trace(out, v1, funcall, label, n)
            for feats in v1.enum(+1):
                if feats is None: continue
                a = list(feats)
                a.append((None,funcall))
                a.reverse()
                data = [ (feat,getsrc(n)) for (feat,n) in a ]
                fp.write('+FORW %r\n' % (data,))
                nents += 1
    print('Ents: %r' % nents, file=sys.stderr)

    if fp is not sys.stdout:
        fp.close()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
