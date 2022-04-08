#!/usr/bin/env python
import sys
import logging
import re
from graphs import IDFBuilder
from graphs import DFType, parsemethodname, parserefname
from srcdb import SourceDB

REFS = {'ref_var', 'ref_field', 'assign_var', 'assign_field'}
TYPEOPS = {'op_typecast', 'op_typecheck'}

def shownode(n):
    if not n.kind:
        return '<empty>'
    elif n.kind == 'call':
        (data,_,_) = n.data.partition(' ')
        (klass,name,func) = parsemethodname(data)
        return f'<{n.kind} {name}()>'
    elif n.kind == 'new':
        (data,_,_) = n.data.partition(' ')
        (klass,name,func) = parsemethodname(data)
        return f'<{n.kind} {klass.name}>'
    elif n.kind in REFS:
        return f'<{n.kind} {parserefname(n.ref)}>'
    elif n.kind in TYPEOPS:
        (_,klass) = DFType.parse(n.data)
        return f'<{n.kind} {klass.name}>'
    elif n.data is None:
        return f'<{n.kind}>'
    else:
        return f'<{n.kind} {n.data}>'

def showcall(n):
    if n is None:
        return ''
    elif n.kind == 'call':
        (data,_,_) = n.data.partition(' ')
        (klass,name,func) = parsemethodname(data)
        return f'[{name}()]'
    elif n.kind == 'new':
        (data,_,_) = n.data.partition(' ')
        (klass,name,func) = parsemethodname(data)
        return f'[new {klass.name}]'
    else:
        raise ValueError(n)

def showreturn(n):
    if n is None:
        return ''
    else:
        (klass,name,func) = parsemethodname(n.method.name)
        return f'[{name}]'

class Viewer:

    DIGIT = re.compile(r'([-+])(\d+)?')

    def __init__(self, builder, srcdb):
        self.builder = builder
        self.srcdb = srcdb
        self.curvtx = None
        return

    def set_method(self, method):
        nodes = list(method)
        if nodes:
            self.curvtx = self.builder.getvtx(nodes[0])
            self.show_nav()
        else:
            self.curvtx = None
        return

    def get_vertex(self):
        return self.curvtx

    def show_nav(self):
        assert self.curvtx is not None
        self.nav = {}
        for (i,(label,vtx,funcall)) in enumerate(self.curvtx.inputs):
            i = -(len(self.curvtx.inputs)-i)
            self.nav[i] = vtx
            print(f' {i}: <-({label})- {shownode(vtx.node)} {showcall(funcall)}')
        node = self.curvtx.node
        print(f'   {node.nid}: {shownode(node)}')
        if self.srcdb is not None:
            ast = self.builder.getsrc(node, False)
            if ast is not None:
                (path,start,end) = ast
                src = self.srcdb.get(path)
                for (lineno,line) in src.show_nodes([node]):
                    if lineno is not None:
                        print(f' {lineno:5d}: {line.rstrip()}')
        for (i,(label,vtx,funcall)) in enumerate(self.curvtx.outputs):
            i = i+1
            self.nav[i] = vtx
            print(f' +{i}: -({label})-> {shownode(vtx.node)} {showreturn(funcall)}')
        return

    def run(self, cmd, args):
        m = self.DIGIT.match(cmd)
        if m:
            if m.group(2):
                d = int(m.group(2))
            else:
                d = 1
            if m.group(1) == '-':
                d = -d
            if d in self.nav:
                self.curvtx = self.nav[d]
            self.show_nav()
            return
        if cmd == '.':
            if self.curvtx is not None:
                print(self.curvtx.node)
                self.show_nav()
            return
        if cmd == 't':
            if self.curvtx is None:
                print('!no method')
                return
            method = self.curvtx.node.method
            for (i,node) in enumerate(method):
                if not args or args == node.kind:
                    print(f' {i}: {node.kind} {node.data}')
            return
        if cmd == 'r':
            if self.curvtx is None:
                print('!no method')
                return
            method = self.curvtx.node.method
            for (i,node) in enumerate(method):
                if not node.ref: continue
                if not args or args in node.ref:
                    print(f' {i}: {node.ref} {node.data}')
            return
        if cmd == 'n':
            if self.curvtx is None:
                print('!no method')
                return
            try:
                i = int(args)
            except ValueError:
                print('!invalid number {args}')
                return
            method = self.curvtx.node.method
            nodes = list(method)
            if i < 0 or len(nodes) <= i:
                print('!invalid node {i}')
                return
            self.curvtx = self.builder.getvtx(nodes[i])
            self.show_nav()
            return
        if cmd == 'm':
            try:
                i = int(args)
            except ValueError:
                for (i,method) in enumerate(self.builder.methods):
                    if not args or args in method.name:
                        print(f' {i}: {method.name}')
                return
            if i < 0 or len(self.builder.methods) <= i:
                print('!invalid method {i}')
                return
            self.set_method(self.builder.methods[i])
            return
        self.show_help()
        return

    def show_help(self):
        print('help:')
        print('  .           show the current node.')
        print('  [-+][n]     move to a next node.')
        print('  t [kind]    search nodes by kind.')
        print('  r [ref]     search nodes by ref.')
        print('  n num       change the current node.')
        print('  s [method]  search methods.')
        print('  m num       change the current method.')
        print('  q           quit.')
        print()
        return


def main(argv):
    import getopt
    def usage():
        print(f'usage: {argv[0]} [-d] [-M maxoverrides] [-c encoding] [-B srcdir] [graph ...]')
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'dM:c:B:')
    except getopt.GetoptError:
        return usage()

    level = logging.INFO
    maxoverrides = 1
    encoding = None
    srcdb = None
    for (k, v) in opts:
        if k == '-d': level = logging.DEBUG
        elif k == '-M': maxoverrides = int(v)
        elif k == '-c': encoding = v
        elif k == '-B': srcdb = SourceDB(v, encoding)
    logging.basicConfig(format='%(asctime)s %(levelname)s %(message)s', level=level)

    if not args: return usage()

    builder = IDFBuilder(maxoverrides=maxoverrides)
    for path in args:
        logging.info(f'Loading: {path!r}...')
        builder.load(path)
    builder.run()

    viewer = Viewer(builder, srcdb)
    line = '?'
    while True:
        vtx = viewer.get_vertex()
        try:
            if vtx is None:
                s = input('[no node] ')
            else:
                s = input(f'[{vtx.node.method.name}] ')
        except EOFError:
            break
        if s:
            line = s
        (cmd,_,args) = line.partition(' ')
        if cmd == 'q': break
        viewer.run(cmd, args)
    print()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
