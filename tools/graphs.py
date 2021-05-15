#!/usr/bin/env python
import sys
import re
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

def clen(x):
    if x is None:
        return 0
    else:
        return len(x)

NAME = re.compile(r'\w+$', re.U)
def stripid(name):
    if name.startswith('%'):
        return stripid(name[1:-1])
    m = NAME.search(name)
    if m:
        return m.group(0)
    else:
        return None

def stripgeneric(name):
    (base,_,_) = name.partition('<')
    return base

def splitmethodname(name):
    assert '(' in name and ')' in name
    i = name.index('(')
    j = name.index(')')
    (name, args, retype) = (name[:i], name[i:j+1], name[j+1:])
    if name.endswith(';.<init>'):
        name = name[:-8]
    return (stripid(name), args, retype)


##  DFType
##
class DFType:

    @classmethod
    def parse(klass, s, i=0):
        assert i < len(s)
        c = s[i]
        if c in 'BCSIJFDZV':
            return (i+1, DFBasicType(c))
        elif c == 'L':
            prev = None
            i2 = i+1
            while i2 < len(s):
                c = s[i2]
                if c == ';':
                    return (i2+1, DFKlassType(s[i+1:i2], prev))
                elif c == '<':
                    # generics
                    name = s[i+1:i2]
                    i2 += 1
                    params = []
                    while s[i2] != '>':
                        (i2, t) = klass.parse(s, i2)
                        params.append(t)
                    i2 += 1
                    t = DFKlassType(name, prev, params)
                    c = s[i2]
                    if c == ';':
                        return (i2+1, t)
                    elif c == '.':
                        i2 += 1
                        prev = t
                    else:
                        raise ValueError(s[i2:])
                else:
                    i2 += 1
        elif c == 'T':
            i2 = i+1
            while i2 < len(s):
                c = s[i2]
                if c == ';':
                    return (i2+1, DFKlassType(s[i+1:i2]))
                else:
                    i2 += 1
        elif c == '[':
            (i2, t) = klass.parse(s, i+1)
            return (i2, DFArrayType(t))
        elif c in '+-*':
            # XXX
            return klass.parse(s, i+1)
        elif c == '(':
            i2 = i+1
            args = []
            while s[i2] != ')':
                (i2, t) = klass.parse(s, i2)
                args.append(t)
            (i2, retype) = klass.parse(s, i2+1)
            return (i2, DFFuncType(retype, args))
        elif c == '?':
            return (i+1, DFUnknownType())
        raise ValueError(c)

class DFBasicType(DFType):

    def __init__(self, name):
        self.name = name
        return

    def __repr__(self):
        return f'<{self.name}>'

class DFArrayType(DFType):

    def __init__(self, name):
        self.name = name
        return

    def __repr__(self):
        return f'<[{self.name}]>'

class DFFuncType(DFType):

    def __init__(self, retype, args):
        self.retype = retype
        self.args = args
        return

    def __repr__(self):
        return f'<({self.args}) {self.retype}>'

class DFKlassType(DFType):

    def __init__(self, name, prev=None, params=None):
        self.name = name
        self.prev = prev
        self.params = params
        return

    def __repr__(self):
        if self.prev is None:
            name = self.name
        else:
            name = f'{self.prev}.{self.name}'
        if self.params is None:
            return f'<{name}>'
        else:
            params = ",".join(map(repr, self.params))
            return f'<{name}<{params}>>'

class DFUnknownType(DFType):
    pass

# parsemethodname: returns (klass, name, func).
def parsemethodname(s):
    if s.startswith('!'):
        return (None, s[1:], None)
    else:
        (i, klass) = DFType.parse(s)
        assert s[i] == '.'
        j = s.index('(', i+1)
        name = s[i+1:j]
        (_, func) = DFType.parse(s, j)
        return (klass, name, func)

def parserefname(name):
    if name.startswith('@'):    # ThisRef
        (i, klass) = DFType.parse(name[1:])
        return klass.name
    elif name.startswith('#'):  # InternalRef
        return name[1:]
    elif name.startswith('%'):  # ElemRef
        (i, klass) = DFType.parse(name[1:])
        return f'[{klass.name}]'
    elif name.startswith('.'):  # FieldRef
        (_,_,name) = name.rpartition('/')
        return name
    elif name.startswith('$'):  # VarRef
        (_,_,name) = name.rpartition('/$')
        return name
    elif name.startswith('!'):  # exception
        (i, klass) = DFType.parse(name[1:])
        return f'!{klass.name}'
    else:
        raise ValueError(name)


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
        emethod.set('id', self.name)
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
        if self.data is not None:
            return (f'<DFNode({self.nid}): {self.kind}({self.ntype}) {self.ref}, data={self.data!r}>')
        else:
            return (f'<DFNode({self.nid}): {self.kind}({self.ntype}) {self.ref}>')

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
            eedge = Element('accept')
            if label:
                eedge.set('label', label)
            eedge.set('src', ns(src.nid))
            enode.append(eedge)
        return enode

    def get_inputs(self):
        for (label,src) in self.inputs.items():
            # edges with _ in its name is informational.
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
            gname = attrs.get('id')
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
            self.method.callers.append(attrs.get('id'))
            return
        elif name == 'overrider':
            self.method.overrider.append(attrs.get('id'))
            return
        elif name == 'overriding':
            self.method.overriding.append(attrs.get('id'))
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
        elif name == 'accept':
            label = attrs.get('label', '')
            src = attrs.get('src')
            assert label not in self.node.inputs, self.node.inputs
            assert src is not None, src
            self.node.inputs[label] = src
            return
        else:
            raise ValueError(f'Invalid tag: {name}')


##  get_graphs
##
def get_graphs(arg, gid=0):
    (path,_,ext) = arg.partition(':')
    if ext:
        gids = map(int, ext.split(','))
    else:
        gids = None

    fp = None
    if path == '-':
        methods = FGYamaParser(gid).parse(sys.stdin)
    elif path.endswith('.db'):
        from graph2index import GraphDB
        if not os.path.exists(path): raise IOError(path)
        db = GraphDB(path)
        if gids is None:
            methods = db.get_allmethods()
        else:
            methods = ( db.get_method(gid) for gid in gids )
    else:
        fp = open(path)
        methods = FGYamaParser(gid).parse(fp)

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
def run_fgyama(path, gid=0):
    args = ['java', '-cp', ':'.join(CLASSPATH),
            'net.tabesugi.fgyama.Java2DF', path]
    print(f'run_fgyama: {args!r}')
    p = Popen(args, stdout=PIPE)
    methods = list(FGYamaParser(gid).parse(p.stdout))
    p.wait()
    return methods


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

    def __hash__(self):
        return id(self)

    def __iter__(self):
        c = self
        while c is not None:
            yield c.car
            c = c.cdr
        return

    def __eq__(self, c1):
        c0 = self
        while c0 is not c1:
            if c0 is None or c1 is None: return False
            if c0.car != c1.car: return False
            (c0,c1) = (c0.cdr, c1.cdr)
        return True

    def __contains__(self, obj0):
        for obj in self:
            if obj is obj0: return True
        return False

    @classmethod
    def fromseq(self, seq):
        c = None
        for x in seq:
            c = Cons(x, c)
        return c


##  IPVertex (Inter-Procedural Vertex)
##  (why vertex? because calling this another "node" is confusing!)
##
class IPVertex:

    vid_base = 0

    def __init__(self, node):
        IPVertex.vid_base += 1
        self.vid = self.vid_base
        self.node = node
        self.inputs = []   # [(label,node,funcall), ...]
        self.outputs = []  # [(label,node,funcall), ...]
        return

    def __repr__(self):
        return (f'<IPVertex({self.vid})>')

    def connect(self, label, output, funcall=None):
        #print(f'# connect: {self}-{label}-{output}')
        #assert output is not self
        assert isinstance(label, str)
        assert isinstance(output, IPVertex)
        self.outputs.append((label, output, funcall))
        output.inputs.append((label, self, funcall))
        return


##  IDFBuilder
##
class IDFBuilder:

    def __init__(self, maxoverrides=1, dbg=None):
        self.maxoverrides = maxoverrides
        self.dbg = dbg
        self.methods = []
        self.srcmap = {}
        self.gid2method = {}
        self.funcalls = {}
        self.vtxs = {}
        return

    def __len__(self):
        return len(self.vtxs)

    # List all the vertexes.
    def __iter__(self):
        return iter(self.vtxs.values())

    # Load methods.
    def load(self, path, fp=None, filter=None):
        for method in get_graphs(path):
            if method.style == 'initializer': continue
            if filter is not None and not filter(method): continue
            path = method.klass.path
            if path not in self.srcmap:
                fid = len(self.srcmap)
                self.srcmap[path] = fid
                src = (fid, path)
                if fp is not None:
                    fp.write(f'+SOURCE {src}\n')
            self.methods.append(method)
            self.gid2method[method.name] = method
        return

    # Get a source.
    def getsrc(self, node, resolve=True):
        if node.ast is None: return None
        if isinstance(node, DFMethod):
            path = node.klass.path
        else:
            path = node.method.klass.path
        (_,start,end) = node.ast
        if resolve:
            fid = self.srcmap[path]
            return (fid, start, end)
        else:
            return (path, start, end)

    # Register a funcall.
    def addcall(self, x, y): # (caller, callee)
        if self.dbg is not None:
            self.dbg.write(f'# addcall {x.method.name}: {y}\n')
        if y in self.funcalls:
            a = self.funcalls[y]
        else:
            a = self.funcalls[y] = []
        if x not in a:
            a.append(x)
        return

    # Create a IPVertex.
    def getvtx(self, node):
        if node in self.vtxs:
            vtx = self.vtxs[node]
        else:
            vtx = self.vtxs[node] = IPVertex(node)
            if self.dbg is not None:
                self.dbg.write(f'# getvtx {vtx}: {node.kind}({node.data!r})\n')
        return vtx

    def run(self):
        # Enumerate caller/callee relationships.
        for src in self.methods:
            for node in src:
                if node.is_funcall():
                    funcs = node.data.split(' ')
                    for gid in funcs[:self.maxoverrides]:
                        self.addcall(node, gid)

        # Convert every node to IPVertex.
        for method in self.methods:
            for node in method:
                if node.is_funcall():
                    funcs = node.data.split(' ')
                    for callee in funcs[:self.maxoverrides]:
                        if callee not in self.gid2method: continue
                        for n1 in self.gid2method[callee]:
                            if n1.kind == 'input':
                                label = n1.ref
                                if label in node.inputs:
                                    n0 = node.inputs[label]
                                    vtx0 = self.getvtx(n0)
                                    vtx0.connect(label, self.getvtx(n1), node)
                                    #print(f'# send: {n0} {label} -> {n1}')
                            elif n1.kind == 'output':
                                vtx1 = self.getvtx(n1)
                                for (label,n2) in node.outputs:
                                    assert n2.kind in ('receive', 'throw')
                                    assert n2.ref == label or n2.ref is None
                                    if not label: label = '#return'
                                    if n1.ref != label: continue
                                    vtx1.connect(label, self.getvtx(n2), node)
                                    #print(f'# recv: {n1} -> {label} {n2}')
                vtx = self.getvtx(node)
                for (label,n1) in node.outputs:
                    vtx.connect(label, self.getvtx(n1))
        return


# main
def main(argv):
    import getopt
    def usage():
        print(f'usage: {argv[0]} [-d] [-M maxoverrides] [graph ...]')
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'dM:')
    except getopt.GetoptError:
        return usage()
    debug = 0
    maxoverrides = 1
    for (k, v) in opts:
        if k == '-d': debug += 1
        elif k == '-M': maxoverrides = int(v)
    if not args: return usage()

    builder = IDFBuilder(maxoverrides=maxoverrides)
    for path in args:
        print(f'Loading: {path!r}...', file=sys.stderr)
        builder.load(path)

    builder.run()
    nfuncalls = sum( len(a) for a in builder.funcalls.values() )
    print(f'Read: {len(builder.srcmap)} sources, {len(builder.methods)} methods, {nfuncalls} funcalls, {len(builder.vtxs)} IPVertexes',
          file=sys.stderr)

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
