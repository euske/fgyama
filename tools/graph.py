#!/usr/bin/env python
import sys
import os.path
import sqlite3


##  SourceDB
##
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


##  SourceFile
##
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


##  DFGraph
##
class DFGraph:

    def __init__(self, gid, name, src=None):
        self.gid = gid
        self.name = name
        self.src = src
        self.root = None
        self.scopes = {}
        self.nodes = {}
        self.links = []
        return

    def __repr__(self):
        return ('<DFGraph(%s), name=%r (%d nodes, %d links)>' %
                (self.gid, self.name, len(self.nodes), len(self.links)))

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
                          (scope.name, node.nid, node.ntype, node.label, node.ref))
                if node.ast is not None:
                    out.write(',%s,%s,%s' % node.ast)
                out.write('\n')
            for node in scope.nodes:
                for link in (node.send+node.other):
                    out.write('-%s,%s,%s,%s,%s\n' %
                              (link.srcid, link.dstid, link.idx, link.ltype, link.label))
            for child in scope.children:
                f(child)
        if self.src is not None:
            out.write('#%s\n' % (self.src,))
        f(self.root)
        out.write('\n')
        return

    
##  DFScope
##
class DFScope:

    def __init__(self, sid, name, parent=None):
        self.sid = sid
        self.name = name
        self.nodes = []
        self.children = []
        self.set_parent(parent)
        return

    def __repr__(self):
        return ('<DFScope(%s)>' % self.sid)

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

    
##  DFNode
##
class DFNode:

    N_None = 0
    N_Refer = 1
    N_Const = 2
    N_Operator = 3
    N_Assign = 4
    N_Branch = 5
    N_Join = 6
    N_Loop = 7
    N_Terminal = 8

    def __init__(self, nid, scope, ntype, label, ref):
        self.nid = nid
        self.scope = scope
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
                (self.nid, self.ntype, self.ref, self.label))


##  DFLink
##
class DFLink:

    L_None = 0
    L_DataFlow = 1
    L_BackFlow = 2
    L_ControlFlow = 3
    L_Informational = 4
    
    def __init__(self, lid, srcid, dstid, idx, ltype, label):
        self.lid = lid
        self.srcid = srcid
        self.src = None
        self.dstid = dstid
        self.dst = None
        self.idx = idx
        self.ltype = ltype
        self.label = label
        return

    def __repr__(self):
        return ('<DFLink(%d): ltype=%d, %r-(%r)-%r, idx=%r>' %
                (self.lid, self.ltype, self.srcid, self.label, self.dstid, self.idx))


##  load_graphs
##
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
            graph = DFGraph(sid, sid, src)
            assert sid not in graph.scopes
            scope = DFScope(sid, sid)
            graph.root = scope
            graph.scopes[sid] = scope
        elif line.startswith(':'):
            f = line[1:].split(',')
            (sid,pid) = f[0:2]
            assert graph is not None
            assert sid not in graph.scopes
            assert pid in graph.scopes
            parent = graph.scopes[pid]
            scope = DFScope(sid, sid, parent)
            graph.scopes[sid] = scope
        elif line.startswith('+'):
            f = line[1:].split(',')
            (sid,nid,ntype,label,ref) = f[0:5]
            assert graph is not None
            assert sid in graph.scopes
            scope = graph.scopes[sid]
            node = DFNode(nid, scope, int(ntype), label, ref)
            if len(f) == 8:
                node.ast = (int(f[5]),int(f[6]),int(f[7]))
            graph.nodes[nid] = node
            scope.nodes.append(node)
        elif line.startswith('-'):
            f = line[1:].split(',')
            (nid1,nid2,idx,ltype) = f[0:4]
            label = f[4] if 5 <= len(f) else None
            assert graph is not None
            link = DFLink(len(graph.links), nid1, nid2, int(idx), int(ltype), label)
            graph.links.append(link)
        elif not line:
            if graph is not None:
                yield graph.fixate()
            graph = None
    if graph is not None:
        yield graph.fixate()
    return


##  build_graph_tables
##
def build_graph_tables(cur):
    cur.executescript('''
CREATE TABLE SourceFile (
    Cid INTEGER PRIMARY KEY,
    FileName TEXT
);

CREATE TABLE ASTNode (
    Aid INTEGER PRIMARY KEY,
    Type INTEGER,
    Start INTEGER,
    End INTEGER
);

CREATE TABLE DFGraph (
    Gid INTEGER PRIMARY KEY,
    Cid INTEGER,
    Name TEXT
);

CREATE TABLE DFScope (
    Sid INTEGER PRIMARY KEY,
    Gid INTEGER,
    Parent INTEGER,
    Name TEXT
);
CREATE INDEX DFScopeGidIndex ON DFScope(Gid);

CREATE TABLE DFNode (
    Nid INTEGER PRIMARY KEY,
    Gid INTEGER,
    Sid INTEGER,
    Aid INTEGER,
    Type INTEGER,
    Label TEXT,
    Ref TEXT
);
CREATE INDEX DFNodeGidIndex ON DFNode(Gid);

CREATE TABLE DFLink (
    Lid INTEGER PRIMARY KEY,
    Nid0 INTEGER,
    Nid1 INTEGER,
    Idx INTEGER,
    Type INTEGER,
    Label TEXT
);
CREATE INDEX DFLinkNid0Index ON DFLink(Nid0);
''')
    return


##  index_graph
##
def index_graph(cur, cid, graph):
    cur.execute(
        'INSERT INTO DFGraph VALUES (NULL,?,?);',
        (cid, graph.name))
    gid = cur.lastrowid
    graph.gid = gid
    
    def index_node(sid, node):
        aid = 0
        if node.ast is not None:
            cur.execute(
                'INSERT INTO ASTNode VALUES (NULL,?,?,?);', 
                node.ast)
            aid = cur.lastrowid
        cur.execute(
            'INSERT INTO DFNode VALUES (NULL,?,?,?,?,?,?);',
            (gid, sid, aid, node.ntype, node.label, node.ref))
        nid = cur.lastrowid
        node.nid = nid
        return nid

    def index_scope(scope, parent=0):
        cur.execute(
            'INSERT INTO DFScope VALUES (NULL,?,?,?);',
            (gid, parent, scope.name))
        sid = cur.lastrowid
        scope.sid = sid
        for node in scope.nodes:
            index_node(sid, node)
        for child in scope.children:
            index_scope(child, sid)
        return

    def index_link(link):
        cur.execute(
            'INSERT INTO DFLink VALUES (NULL,?,?,?,?,?);',
            (link.src.nid, link.dst.nid, link.idx, 
             link.ltype, link.label))
        lid = cur.lastrowid
        link.lid = lid
        return
    
    index_scope(graph.root)
    for node in graph.nodes.values():
        for link in node.send:
            index_link(link)
    return


##  fetch_graph
##
def fetch_graph(cur, gid):
    cur.execute(
        'SELECT Cid,Name FROM DFGraph WHERE Gid=?;',
        (gid,))
    (cid,name) = cur.fetchone()
    cur.execute(
        'SELECT FileName FROM SourceFile WHERE Cid=?;',
        (cid,))
    (src,) = cur.fetchone()
    graph = DFGraph(gid, name, src)
    rows = cur.execute(
        'SELECT Sid,Parent,Name FROM DFScope WHERE Gid=?;',
        (gid,))
    pids = {}
    scopes = graph.scopes
    for (sid,parent,name) in rows:
        scope = DFScope(sid, name)
        scopes[sid] = scope
        pids[sid] = parent
        if parent == 0:
            graph.root = scope
    for (sid,parent) in pids.items():
        if parent != 0:
            scopes[sid].set_parent(scopes[parent])
    rows = cur.execute(
        'SELECT Nid,Sid,Aid,Type,Label,Ref FROM DFNode WHERE Gid=?;',
        (gid,))
    for (nid,sid,aid,ntype,label,ref) in list(rows):
        scope = scopes[sid]
        node = DFNode(nid, scope, ntype, label, ref)
        rows = cur.execute(
            'SELECT Type,Start,End FROM ASTNode WHERE Aid=?;',
            (aid,))
        for (t,s,e) in rows:
            node.ast = (t,s,e)
        graph.nodes[nid] = node
        scope.nodes.append(node)
    for (nid0,node) in graph.nodes.items():
        rows = cur.execute(
            'SELECT Lid,Nid1,Idx,Type,Label FROM DFLink WHERE Nid0=?;',
            (nid0,))
        for (lid,nid1,idx,ltype,label) in rows:
            link = DFLink(lid, nid0, nid1, idx, ltype, label)
            graph.links.append(link)
    graph.fixate()
    return graph


# get_graphs
def get_graphs(arg):
    (path,_,ext) = arg.partition(':')
    if ext:
        gids = map(int, ext.split(','))
    else:
        gids = None
    if path.endswith('.graph'):
        with open(path) as fp:
            for (gid,graph) in enumerate(load_graphs(fp)):
                if gids is None or gid in gids:
                    yield graph
    elif path.endswith('.db'):
        conn = sqlite3.connect(path)
        cur1 = conn.cursor()
        cur2 = conn.cursor()
        if gids is not None:
            for gid in gids:
                graph = fetch_graph(cur2, gid)
                yield graph
        else:
            for (gid,) in cur1.execute('SELECT Gid FROM DFGraph;'):
                graph = fetch_graph(cur2, gid)
                yield graph
    return

# main
def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [file ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], '')
    except getopt.GetoptError:
        return usage()
    if not args: return usage()

    for path in args:
        for graph in get_graphs(path):
            if isinstance(graph, DFGraph):
                graph.dump()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
