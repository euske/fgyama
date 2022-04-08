#!/usr/bin/env python
import sys
import logging
import re
from graphs import IDFBuilder
from srcdb import SourceDB, SourceAnnot

class Viewer:

    DIGIT = re.compile(r'([-+])(\d+)?')

    def __init__(self, builder, srcdb):
        self.builder = builder
        self.srcdb = srcdb
        self.curvtx = builder.getvtx(list(self.builder.methods[-1])[-1])
        return

    def get_current(self):
        return self.curvtx

    def show_current(self):
        self.nav = {}
        for (i,(label,vtx,funcall)) in enumerate(self.curvtx.inputs):
            i = -(len(self.curvtx.inputs)-i)
            self.nav[i] = vtx
            print(f' {i}:{label} {vtx.node!r}')
        node = self.curvtx.node
        print(f'   {node.method.name}: {node.nid}')
        if self.srcdb is not None:
            (path,start,end) = self.builder.getsrc(node, False)
            src = self.srcdb.get(path)
            for (lineno,line) in src.show_nodes([node]):
                if lineno is not None:
                    print(f' {lineno:5d}: {line.rstrip()}')
        for (i,(label,vtx,funcall)) in enumerate(self.curvtx.outputs):
            i = i+1
            self.nav[i] = vtx
            print(f' +{i}:{label} {vtx.node!r}')
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
        if cmd == 's':
            for (i,method) in enumerate(self.builder.methods):
                if not args or args in method.name:
                    print(f'{i}: {method.name}')
            return
        if cmd == 'm':
            i = int(args)
            method = self.builder.methods[i]
            self.curvtx = self.builder.getvtx(list(method)[-1])
            self.show_current()
            return
        print('help: [-+]n, s')
        return


def main(argv):
    import getopt
    def usage():
        print(f'usage: {argv[0]} [-d] [-M maxoverrides] [graph ...]')
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
    viewer.show_current()
    line = '?'
    while True:
        vtx = viewer.get_current()
        try:
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
