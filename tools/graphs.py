#!/usr/bin/env python
import sys
import re
import os.path
import xml.sax
import xml.sax.handler
import logging
from subprocess import Popen, PIPE
from xml.etree.ElementTree import Element

def ns(x):
    if isinstance(x, str):
        return x
    else:
        return 'N'+str(x)

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
    if name.endswith(';.:init:'):
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
                    elif c == '/':
                        i2 += 1
                        prev = t
                    else:
                        raise ValueError(f'uknown character {c!r} at {i2} in {s!r}')
                else:
                    i2 += 1
            raise ValueError(f'premature end at {i} in {s!r}')
        elif c == 'T':
            i2 = i+1
            while i2 < len(s):
                c = s[i2]
                if c == ';':
                    return (i2+1, DFKlassType(s[i+1:i2]))
                else:
                    i2 += 1
            raise ValueError(f'premature end at {i} in {s!r}')
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
        else:
            raise ValueError(f'uknown character {c!r} at {i} in {s!r}')
        # don't reach here
        assert 0

    def get_name(self):
        raise NotImplementedError(self)

class DFBasicType(DFType):

    def __init__(self, name):
        assert isinstance(name, str)
        self.name = name
        return

    def __repr__(self):
        return f'<{self.name}>'

    def get_name(self):
        return self.name

class DFArrayType(DFType):

    def __init__(self, elem):
        assert isinstance(elem, DFType)
        self.elem = elem
        return

    def __repr__(self):
        return f'<[{self.elem}]>'

    def get_name(self):
        return self.elem.get_name()

class DFFuncType(DFType):

    def __init__(self, retype, args):
        assert isinstance(retype, DFType)
        self.retype = retype
        self.args = args
        return

    def __repr__(self):
        return f'<({self.args}) {self.retype}>'

class DFKlassType(DFType):

    def __init__(self, name, prev=None, params=None):
        assert isinstance(name, str)
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

    def get_name(self):
        return self.name

class DFUnknownType(DFType):

    def __repr__(self):
        return '<?>'

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

    def __init__(self, klass, name, style, mid=None):
        self.klass = klass
        self.name = name
        self.style = style
        self.mid = mid
        self.root = None
        self.scopes = {}
        self.nodes = {}
        self.callers = []
        self.overrider = []
        self.overriding = []
        self.passin = []
        self.passout = []
        self.ast = None
        return

    def __repr__(self):
        return (f'<DFMethod({self.mid}): name={self.name} ({len(self.nodes)} nodes)>')

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

    def __init__(self, mid=0):
        xml.sax.handler.ContentHandler.__init__(self)
        self._stack = []
        self._cur = self.handleRoot
        self._result = []
        self.mid = mid
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
                implements = impls.split()
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
            self.mid += 1
            gname = attrs.get('id')
            style = attrs.get('style')
            self.method = DFMethod(
                self.klass, gname, style, mid=self.mid)
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
        elif name == 'passin':
            self.method.passin.append(attrs.get('ref'))
            return
        elif name == 'passout':
            self.method.passout.append(attrs.get('ref'))
            return
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
            #assert label not in self.node.inputs, (label,self.node.inputs)
            assert src is not None, src
            self.node.inputs[label] = src
            return
        else:
            raise ValueError(f'Invalid tag: {name}')


##  get_graphs
##
def get_graphs(arg, mid=0):
    (path,_,ext) = arg.partition(':')
    if ext:
        mids = map(int, ext.split(','))
    else:
        mids = None

    if path.endswith('.db'):
        from graph2index import GraphDB
        db = GraphDB(path)
        if mids is None:
            for method in db.get_allmethods():
                yield method
        else:
            for mid in mids:
                yield db.get_method(mid)
    elif path == '-':
        methods = FGYamaParser(mid).parse(sys.stdin)
        for method in methods:
            if mids is None or method.mid in mids:
                yield method
    else:
        with open(path) as fp:
            methods = FGYamaParser(mid).parse(fp)
            for method in methods:
                if mids is None or method.mid in mids:
                    yield method
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
def run_fgyama(path, mid=0):
    args = ['java', '-cp', ':'.join(CLASSPATH),
            'net.tabesugi.fgyama.Java2DF', path]
    print(f'run_fgyama: {args!r}')
    p = Popen(args, stdout=PIPE)
    methods = list(FGYamaParser(mid).parse(p.stdout))
    p.wait()
    return methods


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

    def accept(self, label, vtx, funcall=None):
        #print(f'# accept: {self}-{label}-{output}')
        #assert output is not self
        assert isinstance(label, str)
        assert isinstance(vtx, IPVertex)
        self.inputs.append((label, vtx, funcall))
        vtx.outputs.append((label, self, funcall))
        return


##  IDFBuilder
##
class IDFBuilder:

    def __init__(self, maxoverrides=1, mfilter=None):
        self.maxoverrides = maxoverrides
        self.mfilter = mfilter
        self.methods = []
        self.srcmap = {}        # {path:fid}
        self.name2method = {}   # {name:method}
        self.funcalls = {}      # {name:[call, ...]}
        self.vtxs = {}          # {node:vtx}
        return

    def __len__(self):
        return len(self.vtxs)

    # List all the vertexes.
    def __iter__(self):
        return iter(self.vtxs.values())

    # Load methods.
    def load(self, path, filter=None):
        srcs = []
        for method in get_graphs(path):
            #if method.style == 'initializer': continue
            if self.mfilter is not None and not self.mfilter(method): continue
            path = method.klass.path
            if path not in self.srcmap:
                fid = len(self.srcmap)
                self.srcmap[path] = fid
                srcs.append((fid, path))
            self.methods.append(method)
            self.name2method[method.name] = method
            # Extract caller/callee relationships.
            for node in method:
                if not node.is_funcall(): continue
                funcs = node.data.split()
                for callee in funcs[:self.maxoverrides]:
                    logging.debug(f'addcall {method.name}: {callee}')
                    if callee in self.funcalls:
                        a = self.funcalls[callee]
                    else:
                        a = self.funcalls[callee] = []
                    if node not in a:
                        a.append(node)
        return srcs

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

    # Create a IPVertex.
    def getvtx(self, node):
        if node in self.vtxs:
            vtx = self.vtxs[node]
        else:
            vtx = self.vtxs[node] = IPVertex(node)
            logging.debug(f'getvtx {vtx}: {node.kind}({node.data!r})')
        return vtx

    # Convert every node to IPVertex.
    def run(self):
        for method in self.methods:
            for node in method:
                if node.is_funcall():
                    funcs = node.data.split()
                    for callee in funcs[:self.maxoverrides]:
                        if callee not in self.name2method: continue
                        method = self.name2method[callee]
                        for (n0,label,n1) in self.getextnodes(node, method):
                            vtx0 = self.getvtx(n0)
                            vtx1 = self.getvtx(n1)
                            vtx0.accept(label, vtx1, node)
                vtx = self.getvtx(node)
                for (label,n1) in node.inputs.items():
                    vtx.accept(label, self.getvtx(n1))
        return

    # Connect nodes interprocedurally.
    def getextnodes(self, node, method):
        for n1 in method:
            if n1.kind == 'input':
                label = n1.ref
                if label in node.inputs:
                    n0 = node.inputs[label]
                    #print(f'# send: {n0} {label} -> {n1}')
                    yield (n1, label, n0)

            elif n1.kind == 'output':
                for (label,n2) in node.outputs:
                    assert n2.kind in ('receive', 'throw')
                    assert n2.ref == label or n2.ref is None
                    if not label: label = '#return'
                    if n1.ref != label: continue
                    #print(f'# recv: {n1} -> {label} {n2}')
                    yield (n2, label, n1)
        return


# main_idf: boilarplate main for using interproc dataflow.
def main_idf(argv):
    import getopt
    def usage():
        print(f'usage: {argv[0]} [-d] [-M maxoverrides] [graph ...]')
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'dM:')
    except getopt.GetoptError:
        return usage()
    level = logging.INFO
    maxoverrides = 1
    for (k, v) in opts:
        if k == '-d': level = logging.DEBUG
        elif k == '-M': maxoverrides = int(v)
    if not args: return usage()
    logging.basicConfig(format='%(asctime)s %(levelname)s %(message)s', level=level)

    builder = IDFBuilder(maxoverrides=maxoverrides)
    for path in args:
        logging.info(f'Loading: {path!r}...')
        builder.load(path)

    builder.run()
    logging.info(f'Read: {len(builder.srcmap)} sources, {len(builder.methods)} methods, {len(builder.vtxs)} IPVertexes')

    return 0

# main: main for using basic graphs.
def main(argv):
    import getopt
    def usage():
        print(f'usage: {argv[0]} [-d] [-n ntop] [graph ...]')
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'dn:')
    except getopt.GetoptError:
        return usage()
    level = logging.INFO
    ntop = 10
    for (k, v) in opts:
        if k == '-d': level = logging.DEBUG
        elif k == '-n': ntop = int(v)
    if not args: return usage()
    logging.basicConfig(format='%(asctime)s %(levelname)s %(message)s', level=level)

    for path in args:
        methods = []
        kinds = {}
        for method in get_graphs(path):
            methods.append((len(method), method.name))
            for n in method:
                kinds[n.kind] = kinds.get(n.kind, 0)+1
        methods.sort(reverse=True)
        totalnodes = sum(kinds.values())
        topkinds = sorted(kinds.items(), key=lambda x:x[1], reverse=True)
        print(f'{path}: nodes={totalnodes}, methods={len(methods)}, kinds={topkinds}, topmethods={methods[:ntop]}')
    return 0


if __name__ == '__main__': sys.exit(main(sys.argv))
