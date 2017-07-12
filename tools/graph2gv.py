#!/usr/bin/env python
import sys

class Node:

    def __init__(self, scope, nid, ntype, label, ref):
        self.scope = scope
        self.nid = nid
        self.ntype = ntype
        self.label = label
        self.ref = ref
        self.send = []
        self.recv = []
        self.ast = None
        return

class Link:

    def __init__(self, src, dst, ltype, name):
        self.src = src
        self.dst = dst
        self.ltype = ltype
        self.name = name
        src.send.append(self)
        dst.recv.append(self)
        return

class Scope:

    def __init__(self, sid, parent=None):
        self.sid = sid
        self.parent = parent
        if parent is not None:
            parent.children.append(self)
        self.nodes = []
        self.children = []
        return
    
class Graph:

    def __init__(self, name):
        self.name = name
        self.root = None
        self.scopes = {}
        self.nodes = {}
        self.links = []
        return

def load_graphs(fp):
    graph = None
    for line in fp:
        line = line.strip()
        if line.startswith('#'):
            yield line[1:]
        elif line.startswith('!'):
            pass
        elif line.startswith('@'):
            sid = line[1:]
            if graph is not None:
                yield graph
            graph = Graph(sid)
            assert sid not in graph.scopes
            scope = Scope(sid)
            graph.root = scope
            graph.scopes[sid] = scope
        elif line.startswith(':'):
            f = line[1:].split(',')
            (sid,pid) = f[0:2]
            assert graph is not None
            assert sid not in graph.scopes
            assert pid in graph.scopes
            parent = graph.scopes[pid]
            scope = Scope(sid, parent)
            graph.scopes[sid] = scope
        elif line.startswith('+'):
            f = line[1:].split(',')
            (sid,nid,ntype,label,ref) = f[0:5]
            assert graph is not None
            assert sid in graph.scopes
            scope = graph.scopes[sid]
            node = Node(scope, nid, int(ntype), label, ref)
            if len(f) == 8:
                node.ast = (int(f[5]),int(f[6]),int(f[7]))
            graph.nodes[nid] = node
            scope.nodes.append(node)
        elif line.startswith('-'):
            f = line[1:].split(',')
            (nid1,nid2,ltype) = f[0:3]
            name = None
            if 4 <= len(f):
                name = f[3]
            assert graph is not None
            assert nid1 in graph.nodes, nid1
            assert nid2 in graph.nodes, nid2
            link = Link(graph.nodes[nid1], graph.nodes[nid2], ltype, name)
            graph.links.append(link)
    return

def q(s):
    if s:
        return '"%s"' % s.replace('"',r'\"')
    else:
        return '""'

def write_graph(out, scope, level=0):
    h = ' '*level
    if level == 0:
        out.write('digraph %s {\n' % scope.sid)
    else:
        out.write(h+'subgraph cluster_%s {\n' % scope.sid)
    out.write(h+' label=%s;\n' % q(scope.sid))
    for node in scope.nodes:
        out.write(h+' %s [label=%s' % (node.nid, q(node.label)))
        if node.ntype == 3:
            out.write(', shape=box')
        elif node.ntype == 5:
            out.write(', shape=diamond')
        out.write('];\n')
    for node in scope.nodes:
        for link in node.send:
            out.write(h+' %s -> %s' % (link.src.nid, link.dst.nid))
            out.write(h+' [label=%s' % q(link.name))
            if link.ltype == 1:
                out.write(', style=dotted')
            out.write('];\n')
    for child in scope.children:
        write_graph(out, child, level=level+1)
    out.write(h+'}\n')
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
    
    for graph in load_graphs(fileinput.input(args)):
        if isinstance(graph, Graph):
            write_graph(output, graph.root)
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
