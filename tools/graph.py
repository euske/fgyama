#!/usr/bin/env python
import sys
import os.path
import sqlite3
from xml.etree.cElementTree import Element
from xml.etree.cElementTree import ElementTree

def ns(x):
    if isinstance(x, str):
        return x
    else:
        return 'N'+str(x)


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
        return

    def __repr__(self):
        return ('<DFGraph(%s), name=%r (%d nodes)>' %
                (self.gid, self.name, len(self.nodes)))

    def fixate(self):
        for node in self.nodes.values():
            for (label,name) in node.inputs.items():
                src = self.nodes[name]
                node.inputs[label] = src
                if not label.startswith('_'):
                    src.outputs.append(node)
        return self
    
    def toxml(self):
        egraph = Element('graph')
        egraph.set('name', self.name)
        if self.src is not None:
            egraph.set('src', self.src)
        egraph.append(self.root.toxml())
        return egraph

    
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

    def toxml(self):
        escope = Element('scope')
        escope.set('name', self.name)
        for child in self.children:
            escope.append(child.toxml())
        for node in self.nodes:
            escope.append(node.toxml())
        return escope

    
##  DFNode
##
class DFNode:

    def __init__(self, nid, name, scope, ntype, ref, data):
        self.nid = nid
        self.name = name
        self.scope = scope
        self.ntype = ntype
        self.ref = ref
        self.data = data
        self.ast = None
        self.inputs = {}
        self.outputs = []
        return

    def __repr__(self):
        name = self.nid if self.name is None else self.name
        return ('<DFNode(%s): ntype=%s, ref=%r, data=%r, inputs=%r>' %
                (name, self.ntype, self.ref, self.data, len(self.inputs)))

    def toxml(self):
        enode = Element('node')
        enode.set('name', ns(self.nid))
        if self.ntype is not None:
            enode.set('type', self.ntype)
        if self.data is not None:
            enode.set('data', self.data)
        if self.ref is not None:
            enode.set('ref', self.ref)
        if self.ast is not None:
            east = Element('ast')
            (astype,astart,alength) = self.ast
            east.set('type', str(astype))
            east.set('start', str(astart))
            east.set('length', str(alength))
            enode.append(east)
        for (label,src) in self.inputs.items():
            elink = Element('link')
            if label:
                elink.set('label', label)
            elink.set('src', ns(src.nid))
            enode.append(elink)
        return enode

    def get_inputs(self):
        for (label,src) in self.inputs.items():
            if not label.startswith('_'):
                yield (label, src)
        return


##  parse_graph
##
def parse_graph(gid, egraph, src=None):
    assert egraph.tag == 'graph'
    gname = egraph.get('name')
    graph = DFGraph(gid, gname, src)
    
    def parse_node(nid, scope, enode):
        assert enode.tag == 'node'
        nname = enode.get('name')
        ntype = enode.get('type')
        ref = enode.get('ref')
        data = enode.get('data')
        node = DFNode(nid, nname, scope, ntype, ref, data)
        for e in enode.getchildren():
            if e.tag == 'ast':
                node.ast = (int(e.get('type')),
                            int(e.get('start')),
                            int(e.get('length')))
            elif e.tag == 'link':
                label = e.get('label', '')
                src = e.get('src')
                assert label not in node.inputs
                assert src is not None
                node.inputs[label] = src
        return node
    
    def parse_scope(sid, escope, parent=None):
        assert escope.tag == 'scope'
        sname = escope.get('name')
        scope = DFScope(sid, sname, parent)
        sid += 1
        graph.scopes[sname] = scope
        for elem in escope.getchildren():
            if elem.tag == 'scope':
                (sid,child) = parse_scope(sid, elem, scope)
            elif elem.tag == 'node':
                nid = len(graph.nodes)+1
                node = parse_node(nid, scope, elem)
                graph.nodes[node.name] = node
                scope.nodes.append(node)
        return (sid,scope)
    
    for escope in egraph.getchildren():
        (_,graph.root) = parse_scope(1, escope)
        break
    return graph.fixate()


##  load_graphs
##
def load_graphs_file(fp, gid=0):
    root = ElementTree(file=fp).getroot()
    for efile in root.getchildren():
        if efile.tag != 'file': continue
        path = efile.get('path')
        for egraph in efile.getchildren():
            if egraph.tag != 'graph': continue
            if gid is not None:
                gid += 1
            yield parse_graph(gid, egraph, src=path)
    return

def load_graphs_db(conn, gids=None):
    cur = conn.cursor()
    if gids is not None:
        for gid in gids:
            graph = fetch_graph(cur, gid)
            yield graph
    else:
        cur1 = conn.cursor()
        for (gid,) in cur1.execute('SELECT Gid FROM DFGraph;'):
            graph = fetch_graph(cur, gid)
            yield graph
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
    Type TEXT,
    Ref TEXT,
    Data TEXT
);
CREATE INDEX DFNodeGidIndex ON DFNode(Gid);

CREATE TABLE DFLink (
    Lid INTEGER PRIMARY KEY,
    Nid0 INTEGER,
    Nid1 INTEGER,
    Label TEXT
);
CREATE INDEX DFLinkNid0Index ON DFLink(Nid0);
''')
    return


##  store_graph
##
def store_graph(cur, cid, graph):
    cur.execute(
        'INSERT INTO DFGraph VALUES (NULL,?,?);',
        (cid, graph.name))
    gid = cur.lastrowid
    graph.gid = gid
    
    def store_node(sid, node):
        aid = 0
        if node.ast is not None:
            cur.execute(
                'INSERT INTO ASTNode VALUES (NULL,?,?,?);', 
                node.ast)
            aid = cur.lastrowid
        cur.execute(
            'INSERT INTO DFNode VALUES (NULL,?,?,?,?,?,?);',
            (gid, sid, aid, node.ntype, node.ref, node.data))
        nid = cur.lastrowid
        node.nid = nid
        return nid

    def store_scope(scope, parent=0):
        cur.execute(
            'INSERT INTO DFScope VALUES (NULL,?,?,?);',
            (gid, parent, scope.name))
        sid = cur.lastrowid
        scope.sid = sid
        for node in scope.nodes:
            store_node(sid, node)
        for child in scope.children:
            store_scope(child, sid)
        return

    def store_link(node, src, label):
        cur.execute(
            'INSERT INTO DFLink VALUES (NULL,?,?,?);',
            (node.nid, src.nid, label))
        return
    
    store_scope(graph.root)
    for node in graph.nodes.values():
        for (label,src) in node.inputs.items():
            store_link(node, src, label)
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
        'SELECT Nid,Sid,Aid,Type,Ref,Data FROM DFNode WHERE Gid=?;',
        (gid,))
    for (nid,sid,aid,ntype,ref,data) in list(rows):
        scope = scopes[sid]
        node = DFNode(nid, None, scope, ntype, ref, data)
        rows = cur.execute(
            'SELECT Type,Start,End FROM ASTNode WHERE Aid=?;',
            (aid,))
        for (t,s,e) in rows:
            node.ast = (t,s,e)
        graph.nodes[nid] = node
        scope.nodes.append(node)
    for (nid0,node) in graph.nodes.items():
        rows = cur.execute(
            'SELECT Lid,Nid1,Label FROM DFLink WHERE Nid0=?;',
            (nid0,))
        for (lid,nid1,label) in rows:
            node.inputs[label] = nid1
    graph.fixate()
    return graph


# get_graphs
def get_graphs(arg):
    (path,_,ext) = arg.partition(':')
    if ext:
        gids = map(int, ext.split(','))
    else:
        gids = None
        
    if path == '-':
        graphs = load_graphs_file(sys.stdin)
    elif path.endswith('.db'):
        conn = sqlite3.connect(path)
        graphs = load_graphs_db(conn, gids)
    else:
        with open(path) as fp:
            graphs = list(load_graphs_file(fp))

    for graph in graphs:
        if gids is None or graph.gid in gids:
            yield graph
    return

# main
def main(argv):
    import fileinput
    import getopt
    from xml.etree.cElementTree import dump
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
            dump(graph.toxml())
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
