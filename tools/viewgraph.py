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

def showcall(n0, n1):
    if n0.method != n1.method:
        (klass,name,func) = parsemethodname(n1.method.name)
        return f'[{name}]'
    else:
        return ''

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
        curnode = self.curvtx.node
        self.nav = {}
        for (i,(label,vtx,funcall)) in enumerate(self.curvtx.inputs):
            i = -(len(self.curvtx.inputs)-i)
            self.nav[i] = vtx
            print(f' {i}: <-({label})- {shownode(vtx.node)} {showcall(curnode, vtx.node)}')
        print(f'   {curnode.nid}: {shownode(curnode)}')
        if self.srcdb is not None:
            ast = self.builder.getsrc(curnode, False)
            if ast is not None:
                (path,start,end) = ast
                src = self.srcdb.get(path)
                for (lineno,line) in src.show_nodes([curnode]):
                    if lineno is not None:
                        print(f' {lineno:5d}: {line.rstrip()}')
        for (i,(label,vtx,funcall)) in enumerate(self.curvtx.outputs):
            i = i+1
            self.nav[i] = vtx
            print(f' +{i}: -({label})-> {shownode(vtx.node)} {showcall(curnode, vtx.node)}')
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
                    print(f' {i}: {node.kind} {shownode(node)}')
            return
        if cmd == 'r':
            if self.curvtx is None:
                print('!no method')
                return
            method = self.curvtx.node.method
            for (i,node) in enumerate(method):
                if not node.ref: continue
                if not args or args in node.ref:
                    print(f' {i}: {node.ref} {shownode(node)}')
            return
        if cmd == 'l':
            if self.curvtx is None:
                print('!no method')
                return
            if self.srcdb is None:
                print('!no source')
                return
            method = self.curvtx.node.method
            path = method.klass.path
            src = self.srcdb.get(path)
            try:
                i = int(args)
            except ValueError:
                (_,s,e) = method.ast
                for (lineno,(line,loc0,loc1)) in enumerate(src.lines):
                    if e <= loc0 or loc1 <= s: continue
                    print(f' {lineno:5d}: {line.rstrip()}')
                return
            if i < 0 or len(src.lines) <= i:
                print('!invalid line {i}')
                return
            (_,loc0,loc1) = src.lines[i]
            for (i,node) in enumerate(method):
                if not node.ast: continue
                (_,s,e) = node.ast
                if e <= loc0 or loc1 <= s: continue
                print(f' {i}: {shownode(node)}')
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
        print('  n #         change the current node.')
        print('  m [method]  search methods.')
        print('  m #         change the current method.')
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
