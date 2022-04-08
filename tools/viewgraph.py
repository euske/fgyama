#!/usr/bin/env python
import sys
import logging
import re
from graphs import IDFBuilder
from graphs import DFType, parsemethodname, parserefname
from srcdb import SourceDB

def shownode(n):
    return f'<{n.kind} {n.data}>'

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
        else:
            self.curvtx = None
        self.show_current()
        return

    def get_vertex(self):
        return self.curvtx

    def show_current(self):
        self.nav = {}
        if self.curvtx is None: return
        for (i,(label,vtx,funcall)) in enumerate(self.curvtx.inputs):
            i = -(len(self.curvtx.inputs)-i)
            self.nav[i] = vtx
            print(f' {i}: <-{label}- {shownode(vtx.node)}')
        node = self.curvtx.node
        print(f'   {shownode(node)}')
        if self.srcdb is not None:
            (path,start,end) = self.builder.getsrc(node, False)
            src = self.srcdb.get(path)
            for (lineno,line) in src.show_nodes([node]):
                if lineno is not None:
                    print(f' {lineno:5d}: {line.rstrip()}')
        for (i,(label,vtx,funcall)) in enumerate(self.curvtx.outputs):
            i = i+1
            self.nav[i] = vtx
            print(f' +{i}: -{label}-> {shownode(vtx.node)}')
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
            self.show_current()
            return
        if cmd == '.':
            self.show_current()
            return
        if cmd == 't':
            if self.curvtx is None:
                print('!no node')
                return
            method = self.curvtx.node.method
            for (i,node) in enumerate(method):
                if not args or args == node.kind:
                    print(f'{i}: {node.kind} {node.data}')
            return
        if cmd == 'r':
            if self.curvtx is None:
                print('!no node')
                return
            method = self.curvtx.node.method
            for (i,node) in enumerate(method):
                if not node.ref: continue
                if not args or args in node.ref:
                    print(f'{i}: {node.ref} {node.data}')
            return
        if cmd == 'n':
            if self.curvtx is None:
                print('!no node')
                return
            i = int(args)
            method = self.curvtx.node.method
            self.curvtx = self.builder.getvtx(list(method)[i])
            self.show_current()
            return
        if cmd == 's':
            for (i,method) in enumerate(self.builder.methods):
                if not args or args in method.name:
                    print(f'{i}: {method.name}')
            return
        if cmd == 'm':
            i = int(args)
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
