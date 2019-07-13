#!/usr/bin/env python
import sys
import os.path
import sqlite3
from subprocess import Popen, PIPE
from xml.etree.cElementTree import Element
from xml.etree.cElementTree import ElementTree

def ns(x):
    if isinstance(x, str):
        return x
    else:
        return 'N'+str(x)

def sp(x):
    if x:
        return x.split(' ')
    else:
        return []


##  DFGraph
##
class DFGraph:

    def __init__(self, gid, name, style, src=None):
        self.gid = gid
        self.name = name
        self.style = style
        self.src = src
        self.root = None
        self.scopes = {}
        self.nodes = {}
        self.ins = []
        self.outs = []
        self.callers = []
        self.ast = None
        return

    def __repr__(self):
        return ('<DFGraph(%s), name=%r (%d nodes)>' %
                (self.gid, self.name, len(self.nodes)))

    def __iter__(self):
        return iter(self.nodes.values())

    def fixate(self):
        # Make every node double-linked.
        for node in self:
            for (label,name) in node.inputs.items():
                src = self.nodes[name]
                node.inputs[label] = src
                src.outputs.append((label, node))
        return self

    def toxml(self):
        egraph = Element('method')
        egraph.set('name', self.name)
        if self.ast is not None:
            east = Element('ast')
            (astype,astart,aend) = self.ast
            east.set('type', str(astype))
            east.set('start', str(astart))
            east.set('end', str(aend))
            egraph.append(east)
        if self.src is not None:
            egraph.set('src', self.src)
        egraph.append(self.root.toxml())
        return egraph


##  DFScope
##
class DFScope:

    def __init__(self, graph, sid, name, parent=None):
        self.graph = graph
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

    def __init__(self, graph, nid, scope, kind, ref, data, ntype):
        self.graph = graph
        self.nid = nid
        self.scope = scope
        self.kind = kind
        self.ref = ref
        self.data = data
        self.ntype = ntype
        self.ast = None
        self.inputs = {}
        self.outputs = []
        return

    def __repr__(self):
        return ('<DFNode(%s): kind=%s, ref=%r, data=%r, ntype=%r, inputs=%r>' %
                (self.nid, self.kind, self.ref, self.data, self.ntype, len(self.inputs)))

    def toxml(self):
        enode = Element('node')
        enode.set('id', self.nid)
        if self.kind is not None:
            enode.set('kind', self.kind)
        if self.data is not None:
            enode.set('data', self.data)
        if self.ref is not None:
            enode.set('ref', self.ref)
        if self.ntype is not None:
            enode.set('type', self.ntype)
        if self.ast is not None:
            east = Element('ast')
            (astype,astart,aend) = self.ast
            east.set('type', str(astype))
            east.set('start', str(astart))
            east.set('end', str(aend))
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
            # links with _ in its name is informational.
            # and should not be considered as a real dataflow.
            if not label.startswith('_'):
                yield (label, src)
        return

    def dump(self, d, maxlev, lev=0, label=None):
        if label is None:
            print('  '*lev+str(self))
        else:
            print('  '*lev+label+':'+str(self))
        if lev < maxlev:
            if d < 0:
                nodes = self.inputs.items()
            else:
                nodes = self.outputs
            for (label,n) in nodes:
                n.dump(d, maxlev, lev+1, label)
        return


##  parse_graph
##
def parse_graph(gid, egraph, src=None):
    assert egraph.tag == 'method'
    gname = egraph.get('name')
    style = egraph.get('style')
    graph = DFGraph(gid, gname, style, src)

    def parse_node(scope, enode):
        assert enode.tag == 'node'
        nid = enode.get('id')
        kind = enode.get('kind')
        ref = enode.get('ref')
        data = enode.get('data')
        ntype = enode.get('type')
        node = DFNode(graph, nid, scope, kind, ref, data, ntype)
        for e in enode:
            if e.tag == 'ast':
                node.ast = (int(e.get('type')),
                            int(e.get('start')),
                            int(e.get('end')))
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
        scope = DFScope(graph, sid, sname, parent)
        sid += 1
        graph.scopes[sname] = scope
        for elem in escope:
            if elem.tag == 'scope':
                (sid,child) = parse_scope(sid, elem, scope)
            elif elem.tag == 'node':
                node = parse_node(scope, elem)
                graph.nodes[node.nid] = node
                scope.nodes.append(node)
        return (sid,scope)

    for e in egraph:
        if e.tag == 'ast':
            graph.ast = (
                int(e.get('type')),
                int(e.get('start')),
                int(e.get('end')))
        elif e.tag == 'scope':
            (_,graph.root) = parse_scope(1, e)
        elif e.tag == 'caller':
            graph.callers.append(e.get('name'))

    for node in graph.nodes.values():
        if node.kind == 'input':
            graph.ins.append(node)
        elif node.kind == 'output':
            graph.outs.append(node)
    return graph.fixate()


##  load_graphs
##
def load_graphs(fp, gid=0):
    root = ElementTree(file=fp).getroot()
    for efile in root:
        if efile.tag != 'class': continue
        path = efile.get('path')
        for egraph in efile:
            if egraph.tag != 'method': continue
            if gid is not None:
                gid += 1
            yield parse_graph(gid, egraph, src=path)
    return


##  GraphDB
##
class GraphDB:

    def __init__(self, path):
        self._conn = sqlite3.connect(path)
        self._cur = self._conn.cursor()
        try:
            self._cur.executescript('''
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
    Name TEXT,
    Style TEXT
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
    Kind TEXT,
    Ref TEXT,
    Data TEXT,
    Type TEXT
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
        except sqlite3.OperationalError:
            pass
        return

    def close(self):
        self._conn.commit()
        return

    def get_gids(self):
        cur1 = self._conn.cursor()
        for (gid,) in cur1.execute('SELECT Gid FROM DFGraph;'):
            yield gid
        return

    def add_src(self, src):
        self._cur.execute(
            'INSERT INTO SourceFile VALUES (NULL,?)',
            (src,))
        cid = self._cur.lastrowid
        return cid

    # store_graph
    def add(self, cid, graph):
        cur = self._cur
        cur.execute(
            'INSERT INTO DFGraph VALUES (NULL,?,?,?);',
            (cid, graph.name, graph.style))
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
                'INSERT INTO DFNode VALUES (NULL,?,?,?,?,?,?,?);',
                (gid, sid, aid, node.kind, node.ref, node.data, node.ntype))
            nid = cur.lastrowid
            node.nid = nid
            return nid

        def store_scope(nids, scope, parent=0):
            cur.execute(
                'INSERT INTO DFScope VALUES (NULL,?,?,?);',
                (gid, parent, scope.name))
            sid = cur.lastrowid
            scope.sid = sid
            for node in scope.nodes:
                nids[node] = store_node(sid, node)
            for child in scope.children:
                store_scope(nids, child, sid)
            return

        def store_link(nids, node, src, label):
            cur.execute(
                'INSERT INTO DFLink VALUES (NULL,?,?,?);',
                (nids[node], nids[src], label))
            return

        nids = {}
        store_scope(nids, graph.root)
        for node in graph:
            for (label,src) in node.inputs.items():
                store_link(nids, node, src, label)
        return gid

    # fetch_graph
    def get(self, gid):
        cur = self._cur
        cur.execute(
            'SELECT Cid,Name,Style FROM DFGraph WHERE Gid=?;',
            (gid,))
        (cid,name,style) = cur.fetchone()
        cur.execute(
            'SELECT FileName FROM SourceFile WHERE Cid=?;',
            (cid,))
        (src,) = cur.fetchone()
        graph = DFGraph(gid, name, style, src)
        rows = cur.execute(
            'SELECT Sid,Parent,Name FROM DFScope WHERE Gid=?;',
            (gid,))
        pids = {}
        scopes = graph.scopes
        for (sid,parent,name) in rows:
            scope = DFScope(graph, sid, name)
            scopes[sid] = scope
            pids[sid] = parent
            if parent == 0:
                graph.root = scope
        for (sid,parent) in pids.items():
            if parent != 0:
                scopes[sid].set_parent(scopes[parent])
        rows = cur.execute(
            'SELECT Nid,Sid,Aid,Kind,Ref,Data,Type FROM DFNode WHERE Gid=?;',
            (gid,))
        for (nid,sid,aid,kind,ref,data,ntype) in list(rows):
            scope = scopes[sid]
            node = DFNode(graph, nid, scope, kind, ref, data, ntype)
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
        graphs = load_graphs(sys.stdin)
    elif path.endswith('.db'):
        db = GraphDB(path)
        if gids is None:
            graphs = ( db.get(gid) for gid in db.get_gids() )
        else:
            graphs = [ db.get(gid) for gid in gids ]
    else:
        with open(path) as fp:
            graphs = list(load_graphs(fp))

    for graph in graphs:
        if gids is None or graph.gid in gids:
            yield graph
    return

# run_fgyama
BASEDIR = os.path.dirname(os.path.dirname(__file__))
LIBDIR = os.path.join(BASEDIR, 'lib')
LIBS = (
    'junit-4.12.jar',
    'bcel-6.2.jar',
    'org.eclipse.jdt.core-3.12.3.jar',
    'org.eclipse.core.resources-3.11.1.jar',
    'org.eclipse.core.expressions-3.5.100.jar',
    'org.eclipse.core.runtime-3.12.0.jar',
    'org.eclipse.osgi-3.11.3.jar',
    'org.eclipse.equinox.common-3.8.0.jar',
    'org.eclipse.core.jobs-3.8.0.jar',
    'org.eclipse.equinox.registry-3.6.100.jar',
    'org.eclipse.equinox.preferences-3.6.1.jar',
    'org.eclipse.core.contenttype-3.5.100.jar',
    'org.eclipse.equinox.app-1.3.400.jar',
    'org.eclipse.core.filesystem-1.6.1.jar',
    'org.eclipse.text-3.6.0.jar',
    'org.eclipse.core.commands-3.8.1.jar',
)
CLASSPATH = [ os.path.join(LIBDIR, name) for name in LIBS ]
CLASSPATH.append(os.path.join(BASEDIR, 'target'))
def run_fgyama(path):
    args = ['java', '-cp', ':'.join(CLASSPATH),
            'net.tabesugi.fgyama.Java2DF', path]
    print('run_fgyama: %r' % args)
    p = Popen(args, stdout=PIPE)
    graphs = list(load_graphs(p.stdout))
    p.wait()
    return graphs

# main
def main(argv):
    import fileinput
    import getopt
    from xml.etree.cElementTree import dump
    def usage():
        print('usage: %s [-d] [file ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'd')
    except getopt.GetoptError:
        return usage()
    debug = 0
    for (k, v) in opts:
        if k == '-d': debug += 1
    if not args: return usage()

    nclasses = 0
    ngraphs = 0
    nnodes = 0
    for path in args:
        for graph in get_graphs(path):
            ngraphs += 1
            nnodes += len(graph.nodes)
            if debug:
                dump(graph.toxml())
        with open(path) as fp:
            root = ElementTree(file=fp).getroot()
            for efile in root:
                if efile.tag == 'class':
                    nclasses += 1

    print('nclasses:', nclasses)
    print('ngraphs:', ngraphs)
    print('nnodes:', nnodes)
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
