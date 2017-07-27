#!/usr/bin/env python
import sys
import os.path

class SourceDB:
    
    def __init__(self, basedir):
        self.basedir = basedir
        self._cache = {}
        return

    def get(self, name):
        if name in self._cache:
            src = self._cache[name]
        else:
            path = os.path.join(self.basedir, name)
            with open(path) as fp:
                data = fp.read()
            src = SourceFile(name, data)
            self._cache[name] = src
        return src

    def show(self, fp=sys.stdout):
        for src in self._cache.values():
            src.show(fp)
        return

class SourceFile:

    def __init__(self, name, data):
        self.name = name
        self.lines = data.splitlines(True)
        self._ranges = []
        return
    
    def __repr__(self):
        return ('<SourceFile(%s)>' %
                (self.name,))

    def clear(self):
        self._ranges = []
        return

    def addast(self, ast, anno=None):
        if ast is not None:
            (_,i,n) = ast
            self._ranges.append((i, i+n, anno))
        return
    
    def show(self, fp=sys.stdout,
             context=1, skip='...\n',
             astart=(lambda _: '['),
             aend=(lambda _: ']'),
             abody=(lambda _,s: s)):
        if not self._ranges: return
        self._ranges.sort(key=lambda x: x[0])
        selected = {}
        i0 = 0
        ri = 0
        for (lineno,line) in enumerate(self.lines):
            i1 = i0+len(line)
            while ri < len(self._ranges):
                (s,e,anno) = self._ranges[ri]
                if e <= i0:
                    ri += 1
                elif i1 < s:
                    break
                else:
                    assert i0 < e and s <= i1
                    for dl in range(lineno-context, lineno+context+1):
                        if dl not in selected:
                            selected[dl] = []
                    assert lineno in selected
                    sel = selected[lineno]
                    sel.append((s-i0,True,anno))
                    sel.append((e-i0,False,anno))
                    break
            i0 = i1
        prevline = 0
        for (lineno,line) in enumerate(self.lines):
            if lineno not in selected: continue
            c0 = 0
            x0 = False
            s = ''
            for (c1,x1,anno) in selected[lineno]:
                s += line[c0:c1]
                if not x0 and x1:
                    s += astart(anno)
                elif x0 and not x1:
                    s += aend(anno)
                (c0,x0) = (c1,x1)
            s += abody(anno, line[c0:])
            if prevline+context < lineno:
                fp.write(skip)
            prevline = lineno
            fp.write(s)
        return
    
class Node:

    N_None = 0
    N_Refer = 1
    N_Operator = 2
    N_Assign = 3
    N_Branch = 4
    N_Join = 5
    N_Loop = 6
    N_Terminal = 7

    def __init__(self, scope, nid, ntype, label, ref):
        self.scope = scope
        self.nid = nid
        self.ntype = ntype
        self.label = label
        self.ref = ref
        self.send = []
        self.recv = []
        self.other = []
        self.ast = None
        return

    def __repr__(self):
        return ('<Node(%s): ntype=%d, ref=%r, label=%r>' %
                (self.nid, self.ntype, self.ref, self.label))

class Link:

    L_None = 0
    L_DataFlow = 1
    L_BackFlow = 2
    L_ControlFlow = 3
    L_Informational = 4
    
    def __init__(self, srcid, dstid, lid, ltype, name):
        self.srcid = srcid
        self.src = None
        self.dstid = dstid
        self.dst = None
        self.lid = lid
        self.ltype = ltype
        self.name = name
        return

    def __repr__(self):
        return ('<Link(%d): ltype=%d, %r-(%r)-%r>' %
                (self.lid, self.ltype, self.srcid, self.name, self.dstid))

class Scope:

    def __init__(self, sid, parent=None):
        self.sid = sid
        self.parent = parent
        if parent is not None:
            parent.children.append(self)
        self.nodes = []
        self.children = []
        return

    def __repr__(self):
        return ('<Scope(%s)>' % self.sid)

    def walk(self):
        for n in self.nodes:
            yield n
        for child in self.children:
            for n in child.walk():
                yield n
        return
    
class Graph:

    def __init__(self, name):
        self.name = name
        self.src = None
        self.root = None
        self.scopes = {}
        self.nodes = {}
        self.links = []
        return

    def __repr__(self):
        return ('<Graph(%s), src=%r (%d nodes, %d links)>' %
                (self.name, self.src, len(self.nodes), len(self.links)))

    def fixate(self):
        for link in self.links:
            assert link.srcid in self.nodes
            assert link.dstid in self.nodes
            link.src = self.nodes[link.srcid]
            link.dst = self.nodes[link.dstid]
            if link.ltype in (Link.L_DataFlow, Link.L_ControlFlow):
                link.src.send.append(link)
                link.dst.recv.append(link)
            else:
                link.src.other.append(link)
                link.dst.other.append(link)
        for node in self.nodes.values():
            node.send.sort(key=lambda link: link.lid)
            node.recv.sort(key=lambda link: link.lid)
        return self
    
    def dump(self, out=sys.stdout):
        visited = set()
        def f(node, level=0):
            visited.add(node)
            label = node.ref+':'+node.label if node.ref else node.label
            out.write('%s: %s\n' % (node.nid, label))
            out.write(' '*level)
            for link in node.send:
                if link.name is None:
                    out.write(' --> ')
                else:
                    out.write(' -%s-> ' % link.name)
                n = link.dst
                if n in visited:
                    out.write('#%s\n' % n.nid)
                else:
                    f(n, level=level+1)
            return
        for node in self.nodes.values():
            if not node.recv:
                f(node)
        return

def load_graphs(fp):
    graph = None
    src = None
    for line in fp:
        line = line.strip()
        if line.startswith('#'):
            src = line[1:]
            yield src
        elif line.startswith('!'):
            pass
        elif line.startswith('@'):
            sid = line[1:]
            if graph is not None:
                yield graph.fixate()
            graph = Graph(sid)
            graph.src = src
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
            (nid1,nid2,lid,ltype) = f[0:4]
            name = f[4] if 5 <= len(f) else None
            assert graph is not None
            link = Link(nid1, nid2, int(lid), int(ltype), name)
            graph.links.append(link)
    if graph is not None:
        yield graph.fixate()
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
        label = node.ref+':'+node.label if node.ref else node.label
        out.write(h+' %s [label=%s' % (node.nid, q(label)))
        if node.ntype in (Node.N_Operator, Node.N_Terminal):
            out.write(', shape=box')
        elif node.ntype in (Node.N_Branch, Node.N_Join):
            out.write(', shape=diamond')
        out.write('];\n')
    for child in scope.children:
        write_graph(out, child, level=level+1)
    if level == 0:
        for node in scope.walk():
            for link in node.send:
                out.write(h+' %s -> %s' % (link.srcid, link.dstid))
                out.write(h+' [label=%s' % q(link.name))
                if link.ltype == Link.L_ControlFlow:
                    out.write(', style=dashed')
                out.write('];\n')
            for link in node.other:
                if link.src == node and link.ltype == Link.L_BackFlow:
                    out.write(h+' %s -> %s' % (link.srcid, link.dstid))
                    out.write(h+' [label=%s, style=bold];\n' % q(link.name))
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
