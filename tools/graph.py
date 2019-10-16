#!/usr/bin/env python
import sys
import os.path
import xml.sax
import xml.sax.handler
from subprocess import Popen, PIPE
from xml.etree.ElementTree import Element

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


##  DFKlass
##
class DFKlass:

    def __init__(self, name, path, interface, extends, implements, generic, kid=None):
        self.name = name
        self.path = path
        self.interface = interface
        self.extends = extends
        self.implements = implements
        self.generic = generic
        self.kid = kid
        self.params = []
        self.fields = []
        self.methods = []
        self.parameterized = []
        return

    def __repr__(self):
        return (f'<DFKlass({self.kid}): name={self.name}, methods={len(self.methods)}>')

    def add_param(self, pname, ptype):
        self.params.append((pname, ptype))
        return

    def add_field(self, fname, ftype):
        self.fields.append((fname, ftype))
        return

    def add_method(self, method):
        self.methods.append(method)
        return

    def get_methods(self):
        return self.methods


##  DFMethod
##
class DFMethod:

    def __init__(self, klass, name, style, gid=None):
        self.klass = klass
        self.name = name
        self.style = style
        self.gid = gid
        self.root = None
        self.scopes = {}
        self.nodes = {}
        self.callers = []
        self.overrider = []
        self.overriding = []
        self.ast = None
        return

    def __repr__(self):
        return (f'<DFMethod({self.gid}): name={self.name} ({len(self.nodes)} nodes)>')

    def __len__(self):
        return len(self.nodes)

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
        emethod = Element('method')
        emethod.set('name', self.name)
        if self.ast is not None:
            east = Element('ast')
            (astype,astart,aend) = self.ast
            east.set('type', str(astype))
            east.set('start', str(astart))
            east.set('end', str(aend))
            emethod.append(east)
        emethod.append(self.root.toxml())
        return emethod


##  DFScope
##
class DFScope:

    def __init__(self, method, sid, name, parent=None):
        self.method = method
        self.sid = sid
        self.name = name
        self.nodes = []
        self.children = []
        self.set_parent(parent)
        return

    def __repr__(self):
        return (f'<DFScope({self.sid})>')

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

    def __init__(self, method, nid, scope, kind, ref, data, ntype):
        self.method = method
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
        return (f'<DFNode({self.nid}): kind={self.kind}, ref={self.ref}, data={self.data}, ntype={self.ntype}, inputs={len(self.inputs)}>')

    def toxml(self):
        enode = Element('node')
        enode.set('id', str(self.nid))
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

    def is_funcall(self):
        return (self.kind == 'call' or self.kind == 'new')

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


##  parse_method
##
class FGYamaParser(xml.sax.handler.ContentHandler):

    def __init__(self, gid=0):
        xml.sax.handler.ContentHandler.__init__(self)
        self._stack = []
        self._cur = self.handleRoot
        self._result = []
        self.gid = gid
        self.sid = None
        self.klass = None
        self.method = None
        self.scope = None
        self.node = None
        return

    def parse(self, fp, bufsize=65536):
        p = xml.sax.make_parser()
        p.setContentHandler(self)
        while True:
            b = fp.read(bufsize)
            if not b: break
            p.feed(b)
            for data in self.flush():
                yield data
        for data in self.flush():
            yield data
        return

    def flush(self):
        (result, self._result) = (self._result, [])
        return result

    def startElement(self, name, attrs):
        #print('startElement', name, dict(attrs))
        self._stack.append(self._cur)
        if self._cur is not None:
            self._cur = self._cur(name, attrs)
        return

    def endElement(self, name):
        #print('endElement', name)
        assert self._stack
        if self._cur is not None:
            self._cur(None, None)
        self._cur = self._stack.pop()
        return

    def handleRoot(self, name, attrs):
        if name is None:
            return
        elif name == 'fgyama':
            return self.handleFGYama
        else:
            raise ValueError(f'Invalid tag: {name}')

    def handleFGYama(self, name, attrs):
        if name is None:
            return
        elif name == 'class':
            assert self.klass is None
            name = attrs.get('name')
            path = attrs.get('path')
            interface = (attrs.get('interface') == 'true')
            extends = attrs.get('extends')
            generic = attrs.get('generic')
            impls = attrs.get('implements')
            if impls is None:
                implements = []
            else:
                implements = impls.split(' ')
            self.klass = DFKlass(
                name, path, interface,
                extends, implements, generic)
            return self.handleClass
        else:
            raise ValueError(f'Invalid tag: {name}')

    def handleClass(self, name, attrs):
        if name is None:
            assert self.klass is not None
            self.klass = None
            return
        elif name == 'param':
            pname = attrs.get('name')
            ptype = attrs.get('type')
            self.klass.add_param(pname, ptype)
            return
        elif name == 'field':
            fname = attrs.get('name')
            ftype = attrs.get('type')
            self.klass.add_field(fname, ftype)
            return
        elif name == 'method':
            assert self.method is None
            self.gid += 1
            gname = attrs.get('name')
            style = attrs.get('style')
            self.method = DFMethod(
                self.klass, gname, style, gid=self.gid)
            self.klass.add_method(gname)
            return self.handleMethod
        elif name == 'parameterized':
            ptype = attrs.get('type')
            self.klass.parameterized.append(ptype)
        else:
            raise ValueError(f'Invalid tag: {name}')

    def handleMethod(self, name, attrs):
        if name is None:
            assert self.method is not None
            self.method.fixate()
            self._result.append(self.method)
            self.method = None
            self.sid = None
            return
        elif name == 'ast':
            self.method.ast = (
                int(attrs.get('type')),
                int(attrs.get('start')),
                int(attrs.get('end')))
            return
        elif name == 'caller':
            self.method.callers.append(attrs.get('name'))
            return
        elif name == 'overrider':
            self.method.overrider.append(attrs.get('name'))
            return
        elif name == 'overriding':
            self.method.overriding.append(attrs.get('name'))
            return
        elif name == 'scope':
            assert self.scope is None
            sname = attrs.get('name')
            self.sid = 1
            self.scope = DFScope(
                self.method, self.sid, sname)
            self.method.root = self.scope
            return self.handleScope
        else:
            raise ValueError(f'Invalid tag: {name}')

    def handleScope(self, name, attrs):
        if name is None:
            assert self.scope is not None
            self.scope = self.scope.parent
            return
        elif name == 'scope':
            sname = attrs.get('name')
            self.sid += 1
            self.scope = DFScope(
                self.method, self.sid, sname, self.scope)
            return self.handleScope
        elif name == 'node':
            assert self.node is None
            nid = attrs.get('id')
            kind = attrs.get('kind')
            ref = attrs.get('ref')
            data = attrs.get('data')
            ntype = attrs.get('type')
            self.node = DFNode(
                self.method, nid, self.scope, kind, ref, data, ntype)
            self.method.nodes[self.node.nid] = self.node
            self.scope.nodes.append(self.node)
            return self.handleNode
        else:
            raise ValueError(f'Invalid tag: {name}')

    def handleNode(self, name, attrs):
        if name is None:
            assert self.node is not None
            self.node = None
            return
        elif name == 'ast':
            self.node.ast = (
                int(attrs.get('type')),
                int(attrs.get('start')),
                int(attrs.get('end')))
            return
        elif name == 'link':
            label = attrs.get('label', '')
            src = attrs.get('src')
            assert label not in self.node.inputs, self.node.inputs
            assert src is not None, src
            self.node.inputs[label] = src
            return
        else:
            raise ValueError(f'Invalid tag: {name}')


##  load_graphs
##
def load_graphs(fp, gid=0):
    return FGYamaParser(gid).parse(fp)


# get_graphs
def get_graphs(arg):
    (path,_,ext) = arg.partition(':')
    if ext:
        gids = map(int, ext.split(','))
    else:
        gids = None

    fp = None
    if path == '-':
        methods = load_graphs(sys.stdin)
    elif path.endswith('.db'):
        db = GraphDB(path)
        if gids is None:
            methods = ( db.get(gid) for gid in db.get_gids() )
        else:
            methods = ( db.get(gid) for gid in gids )
    else:
        fp = open(path)
        methods = load_graphs(fp)

    for method in methods:
        if gids is None or method.gid in gids:
            yield method

    if fp is not None:
        fp.close()
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
    print(f'run_fgyama: {args!r}')
    p = Popen(args, stdout=PIPE)
    methods = list(load_graphs(p.stdout))
    p.wait()
    return methods


##  GraphDB
##
def fetch1(cur, name):
    x = cur.fetchone()
    if x is None:
        raise ValueError(name)
    return x

class GraphDB:

    def __init__(self, conn):
        self._conn = conn
        self._cur = self._conn.cursor()
        self._cur.executescript('''
CREATE TABLE IF NOT EXISTS ASTNode (
    Aid INTEGER PRIMARY KEY,
    Type INTEGER,
    Start INTEGER,
    End INTEGER
);

CREATE TABLE IF NOT EXISTS DFKlass (
    Kid INTEGER PRIMARY KEY,
    Name TEXT,
    Path TEXT,
    Interface INTEGER,
    Extends TEXT,
    Implements TEXT,
    Generic INTEGER
);

CREATE TABLE IF NOT EXISTS DFMethod (
    Gid INTEGER PRIMARY KEY,
    Kid INTEGER,
    Name TEXT,
    Style TEXT
);

CREATE TABLE IF NOT EXISTS DFScope (
    Sid INTEGER PRIMARY KEY,
    Gid INTEGER,
    Parent INTEGER,
    Name TEXT
);
CREATE INDEX IF NOT EXISTS DFScopeGidIndex ON DFScope(Gid);

CREATE TABLE IF NOT EXISTS DFNode (
    Nid INTEGER PRIMARY KEY,
    Gid INTEGER,
    Sid INTEGER,
    Aid INTEGER,
    Kind TEXT,
    Ref TEXT,
    Data TEXT,
    Type TEXT
);
CREATE INDEX IF NOT EXISTS DFNodeGidIndex ON DFNode(Gid);

CREATE TABLE IF NOT EXISTS DFLink (
    Lid INTEGER PRIMARY KEY,
    Nid0 INTEGER,
    Nid1 INTEGER,
    Label TEXT
);
CREATE INDEX IF NOT EXISTS DFLinkNid0Index ON DFLink(Nid0);
''')
        return

    def __enter__(self):
        return self

    def __exit__(self):
        self.close()
        return

    def close(self):
        self._conn.commit()
        return

    def add_klass(self, klass):
        cur = self._cur
        cur.execute(
            'INSERT INTO DFKlass VALUES (NULL,?,?,?,?,?,?)',
            (klass.name, klass.path, klass.interface, klass.extends,
             ' '.join(klass.implements), klass.generic))
        kid = cur.lastrowid
        klass.kid = kid
        return kid

    def add_method(self, method):
        assert method.klass.kid is not None
        cur = self._cur
        cur.execute(
            'INSERT INTO DFMethod VALUES (NULL,?,?,?);',
            (method.klass.kid, method.name, method.style))
        gid = cur.lastrowid
        method.gid = gid
        nids = {}

        def add_node(sid, node):
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

        def add_scope(scope, parent=0):
            cur.execute(
                'INSERT INTO DFScope VALUES (NULL,?,?,?);',
                (gid, parent, scope.name))
            sid = cur.lastrowid
            scope.sid = sid
            for node in scope.nodes:
                nids[node] = add_node(sid, node)
            for child in scope.children:
                add_scope(child, sid)
            return

        def add_link(node, src, label):
            cur.execute(
                'INSERT INTO DFLink VALUES (NULL,?,?,?);',
                (nids[node], nids[src], label))
            return

        add_scope(method.root)
        for node in method:
            for (label,src) in node.inputs.items():
                add_link(node, src, label)
        return gid

    def get_kids(self):
        cur = self._conn.cursor()
        for (kid,) in cur.execute('SELECT Kid FROM DFKlass;'):
            yield kid
        return

    def get_gids(self):
        cur = self._conn.cursor()
        for (gid,) in cur.execute('SELECT Gid FROM DFMethod;'):
            yield gid
        return

    def get_klass(self, kid):
        cur = self._cur
        cur.execute(
            'SELECT Name,Path,Interface,Extends,Implements,Generic FROM DFKlass WHERE Kid=?;',
            (kid,))
        (name,path,interface,extends,impls,generic) = fetch1(cur, f'DFKlass({kid})')
        if impls:
            implements = impls.split(' ')
        else:
            implements = []
        return DFKlass(name, path, interface, extends, implements, generic, kid)

    def get_method(self, gid, klasses=None):
        cur = self._cur
        cur.execute(
            'SELECT Kid,Name,Style FROM DFMethod WHERE Gid=?;',
            (gid,))
        (kid,name,style) = fetch1(cur, f'DFMethod({gid})')
        if klasses is None:
            klass = self.get_klass(kid)
        else:
            klass = klasses[kid]
        method = DFMethod(klass, name, style, gid)
        rows = cur.execute(
            'SELECT Sid,Parent,Name FROM DFScope WHERE Gid=?;',
            (gid,))
        pids = {}
        scopes = method.scopes
        for (sid,parent,name) in rows:
            scope = DFScope(method, sid, name)
            scopes[sid] = scope
            pids[sid] = parent
            if parent == 0:
                method.root = scope
        for (sid,parent) in pids.items():
            if parent != 0:
                scopes[sid].set_parent(scopes[parent])
        rows = cur.execute(
            'SELECT Nid,Sid,Aid,Kind,Ref,Data,Type FROM DFNode WHERE Gid=?;',
            (gid,))
        for (nid,sid,aid,kind,ref,data,ntype) in list(rows):
            scope = scopes[sid]
            node = DFNode(method, nid, scope, kind, ref, data, ntype)
            rows = cur.execute(
                'SELECT Type,Start,End FROM ASTNode WHERE Aid=?;',
                (aid,))
            for (t,s,e) in rows:
                node.ast = (t,s,e)
            method.nodes[nid] = node
            scope.nodes.append(node)
        for (nid0,node) in method.nodes.items():
            rows = cur.execute(
                'SELECT Lid,Nid1,Label FROM DFLink WHERE Nid0=?;',
                (nid0,))
            for (lid,nid1,label) in rows:
                node.inputs[label] = nid1
        method.fixate()
        return method

    def get_methods(self):
        klasses = {}
        for kid in self.get_kids():
            klasses[kid] = self.get_klass(kid)
        for gid in self.get_gids():
            yield self.get_method(gid, klasses)
        return


# main
def main(argv):
    import getopt
    import sqlite3
    from xml.etree.ElementTree import dump
    def usage():
        print(f'usage: {argv[0]} [-d] [-o output.db] [file ...]')
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'do:')
    except getopt.GetoptError:
        return usage()
    debug = 0
    output = None
    for (k, v) in opts:
        if k == '-d': debug += 1
        elif k == '-o': output = v
    if not args: return usage()

    nklasses = nmethods = nnodes = 0

    outconn = outdb = None
    if output is not None:
        if os.path.exists(output):
            print(f'Already exists: {output!r}')
            return 1
        print(f'Output GraphDB: {output!r}', file=sys.stderr)
        outconn = sqlite3.connect(output)
        outdb = GraphDB(outconn)

    for path in args:
        rconn = rdb = None
        if path.endswith('.db'):
            if not os.path.exists(path):
                print(f'Not found: {path!r}')
                return 1
            rconn = sqlite3.connect(path)
            rdb = GraphDB(rconn)
            methods = rdb.get_methods()
        else:
            methods = get_graphs(path)
        klass = None
        for method in methods:
            if klass is not method.klass:
                klass = method.klass
                nklasses += 1
                if outdb is not None:
                    outdb.add_klass(klass)
            nmethods += 1
            nnodes += len(method.nodes)
            if debug:
                dump(method.toxml())
            if outdb is not None:
                outdb.add_method(method)
                sys.stderr.write('.'); sys.stderr.flush()
        print(f'\n{path}: {nklasses} classes, {nmethods} methods, {nnodes} nodes',
              file=sys.stderr)
        if rdb is not None:
            rdb.close()
        if rconn is not None:
            rconn.close()

    if outdb is not None:
        outdb.close()
    if outconn is not None:
        outconn.close()

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
