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
            try:
                with open(path) as fp:
                    data = fp.read()
            except IOError:
                raise KeyError(name)
            except UnicodeError:
                raise KeyError(name)
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
        self.data = data
        self.lines = data.splitlines(True)
        return
    
    def __repr__(self):
        return ('<SourceFile(%s)>' %
                (self.name,))

    def show_nodes(self, nodes,
                   fp=sys.stdout,
                   astart=(lambda _: '['),
                   aend=(lambda _: ']'),
                   abody=(lambda _,s: s),
                   ncontext=1, skip='...\n'):
        ranges = []
        for node in nodes:
            if node.ast is None: continue
            (_,i,n) = node.ast
            ranges.append((i, i+n, None))
        self.show(ranges, fp=fp, ncontext=ncontext, skip=skip,
                  astart=astart, aend=aend, abody=abody)
        return
    
    def show(self, ranges,
             fp=sys.stdout,
             astart=(lambda _: '['),
             aend=(lambda _: ']'),
             abody=(lambda _,s: s),
             ncontext=1, skip='...\n'):
        if not ranges: return
        triggers = []
        for (s,e,anno) in ranges:
            triggers.append((s,+1,anno))
            triggers.append((e,-1,anno))
        triggers.sort(key=lambda x: (x[0],x[1]))
        lines = {}
        loc0 = 0
        i = 0
        annos = []
        for (lineno,line) in enumerate(self.lines):
            loc1 = loc0+len(line)
            pos0 = 0
            buf = ''
            while i < len(triggers):
                (loc,v,anno) = triggers[i]
                if loc1 < loc: break
                i += 1
                pos1 = loc - loc0
                buf += abody(annos, line[pos0:pos1])
                pos0 = pos1
                if 0 < v:
                    buf += astart(anno)
                    annos.append(anno)
                else:
                    buf += aend(anno)
                    annos.remove(anno)
            if 0 < pos0:
                buf += abody(annos, line[pos0:])
                lines[lineno] = buf
            loc0 = loc1
        for (lineno,line) in list(lines.items()):
            for i in range(lineno-ncontext, lineno+ncontext+1):
                if i not in lines:
                    lines[i] = self.lines[i]
        lineno0 = 0
        for lineno1 in sorted(lines):
            if lineno0 < lineno1:
                fp.write(skip)
            fp.write(lines[lineno1])
            lineno0 = lineno1+1
        return
    
class DFNode:

    N_None = 0
    N_Refer = 1
    N_Operator = 2
    N_Assign = 3
    N_Branch = 4
    N_Join = 5
    N_Loop = 6
    N_Terminal = 7

    def __init__(self, scope, name, ntype, label, ref):
        self.scope = scope
        self.name = name
        self.ntype = ntype
        self.label = label
        self.ref = ref
        self.send = []
        self.recv = []
        self.other = []
        self.ast = None
        return

    def __repr__(self):
        return ('<DFNode(%s): ntype=%d, ref=%r, label=%r>' %
                (self.name, self.ntype, self.ref, self.label))

class DFLink:

    L_None = 0
    L_DataFlow = 1
    L_BackFlow = 2
    L_ControlFlow = 3
    L_Informational = 4
    
    def __init__(self, srcid, dstid, idx, ltype, name):
        self.srcid = srcid
        self.src = None
        self.dstid = dstid
        self.dst = None
        self.idx = idx
        self.ltype = ltype
        self.name = name
        return

    def __repr__(self):
        return ('<DFLink(%d): ltype=%d, %r-(%r)-%r>' %
                (self.idx, self.ltype, self.srcid, self.name, self.dstid))

class DFScope:

    def __init__(self, name, parent=None):
        self.name = name
        self.nodes = []
        self.children = []
        self.set_parent(parent)
        return

    def __repr__(self):
        return ('<DFScope(%s)>' % self.name)

    def set_parent(self, parent):
        self.parent = parent
        if parent is not None:
            parent.children.append(self)
        return

    def walk(self):
        for n in self.nodes:
            yield n
        for child in self.children:
            for n in child.walk():
                yield n
        return
    
class DFGraph:

    def __init__(self, name, src=None):
        self.name = name
        self.src = src
        self.root = None
        self.scopes = {}
        self.nodes = {}
        self.links = []
        return

    def __repr__(self):
        return ('<DFGraph(%s), src=%r (%d nodes, %d links)>' %
                (self.name, self.src, len(self.nodes), len(self.links)))

    def fixate(self):
        for link in self.links:
            assert link.srcid in self.nodes
            assert link.dstid in self.nodes
            link.src = self.nodes[link.srcid]
            link.dst = self.nodes[link.dstid]
            if link.ltype in (DFLink.L_DataFlow, DFLink.L_ControlFlow):
                link.src.send.append(link)
                link.dst.recv.append(link)
            else:
                link.src.other.append(link)
                link.dst.other.append(link)
        for node in self.nodes.values():
            node.send.sort(key=lambda link: link.idx)
            node.recv.sort(key=lambda link: link.idx)
        return self
    
    def dump(self, out=sys.stdout):
        def f(scope):
            if scope.parent is None:
                out.write('@%s\n' % (scope.name,))
            else:
                out.write(':%s,%s\n' % (scope.name, scope.parent.name))
            for node in scope.nodes:
                out.write('+%s,%s,%s,%s,%s' %
                          (scope.name, node.name, node.ntype, node.label, node.ref))
                if node.ast is not None:
                    out.write(',%s,%s,%s' % node.ast)
                out.write('\n')
            for node in scope.nodes:
                for link in (node.send+node.other):
                    out.write('-%s,%s,%s,%s,%s\n' %
                              (link.srcid, link.dstid, link.idx, link.ltype, link.name))
            for child in scope.children:
                f(child)
        if self.src is not None:
            out.write('#%s\n' % (self.src,))
        f(self.root)
        out.write('\n')
        return

def load_graphs(fp):
    graph = None
    src = None
    for line in fp:
        line = line.strip()
        if line.startswith('#'):
            assert graph is None
            src = line[1:]
            yield src
        elif line.startswith('!'):
            graph = None
        elif line.startswith('@'):
            assert graph is None
            sid = line[1:]
            graph = DFGraph(sid, src)
            assert sid not in graph.scopes
            scope = DFScope(sid)
            graph.root = scope
            graph.scopes[sid] = scope
        elif line.startswith(':'):
            f = line[1:].split(',')
            (sid,pid) = f[0:2]
            assert graph is not None
            assert sid not in graph.scopes
            assert pid in graph.scopes
            parent = graph.scopes[pid]
            scope = DFScope(sid, parent)
            graph.scopes[sid] = scope
        elif line.startswith('+'):
            f = line[1:].split(',')
            (sid,nid,ntype,label,ref) = f[0:5]
            assert graph is not None
            assert sid in graph.scopes
            scope = graph.scopes[sid]
            node = DFNode(scope, nid, int(ntype), label, ref)
            if len(f) == 8:
                node.ast = (int(f[5]),int(f[6]),int(f[7]))
            graph.nodes[nid] = node
            scope.nodes.append(node)
        elif line.startswith('-'):
            f = line[1:].split(',')
            (nid1,nid2,idx,ltype) = f[0:4]
            name = f[4] if 5 <= len(f) else None
            assert graph is not None
            link = DFLink(nid1, nid2, int(idx), int(ltype), name)
            graph.links.append(link)
        elif not line:
            if graph is not None:
                yield graph.fixate()
            graph = None
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
        out.write('digraph %s {\n' % scope.name)
    else:
        out.write(h+'subgraph cluster_%s {\n' % scope.name)
    out.write(h+' label=%s;\n' % q(scope.name))
    for node in scope.nodes:
        label = node.ref+':'+node.label if node.ref else node.label
        out.write(h+' %s [label=%s' % (node.name, q(label)))
        if node.ntype in (DFNode.N_Operator, DFNode.N_Terminal):
            out.write(', shape=box')
        elif node.ntype in (DFNode.N_Branch, DFNode.N_Join):
            out.write(', shape=diamond')
        out.write('];\n')
    for child in scope.children:
        write_graph(out, child, level=level+1)
    if level == 0:
        for node in scope.walk():
            for link in node.send:
                out.write(h+' %s -> %s' % (link.srcid, link.dstid))
                out.write(h+' [label=%s' % q(link.name))
                if link.ltype == DFLink.L_ControlFlow:
                    out.write(', style=dashed')
                out.write('];\n')
            for link in node.other:
                if link.src == node and link.ltype == DFLink.L_BackFlow:
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
        if isinstance(graph, DFGraph):
            write_graph(output, graph.root)
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
