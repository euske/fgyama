#!/usr/bin/env python
import sys
from graphs import IDFBuilder

def main(argv):
    import getopt
    def usage():
        print(f'usage: {argv[0]} [-v] [graph ...]')
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'v')
    except getopt.GetoptError:
        return usage()

    verbose = 0
    maxoverrides = 1
    for (k, v) in opts:
        if k == '-v': verbose += 1
        elif k == '-M': maxoverrides = int(v)

    builder = IDFBuilder(maxoverrides=maxoverrides)
    for path in args:
        print(f'Loading: {path!r}...', file=sys.stderr)
        builder.load(path)

    builder.run()

    m = builder.methods[-1]
    current = builder.getvtx(list(m)[-1])
    cmd0 = None
    while True:
        nav = {}
        for (i,(label,vtx,funcall)) in enumerate(current.inputs):
            i = -(len(current.inputs)-i)
            nav[i] = vtx
            print(f' {i}:{label} {vtx.node!r}')
        print(f'   {current.node!r}')
        for (i,(label,vtx,funcall)) in enumerate(current.outputs):
            i = i+1
            nav[i] = vtx
            print(f' +{i}:{label} {vtx.node!r}')

        try:
            cmd1 = input(f'[{current.node.method.name}] ')
        except EOFError:
            break
        if not cmd1:
            cmd1 = cmd0
        if cmd1 == '-':
            d = -1
        elif cmd1 == '+':
            d = +1
        else:
            try:
                d = int(cmd1)
            except ValueError:
                d = None
        if d in nav:
            current = nav[d]
        cmd0 = cmd1

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
