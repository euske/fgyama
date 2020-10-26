#!/usr/bin/env python
import sys
from graph import get_graphs


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
    for (k, v) in opts:
        if k == '-v': verbose += 1

    methods = []
    for path in args:
        methods.extend(get_graphs(path))

    m = methods[-1]
    cur = list(m)[-1]
    cmd0 = None
    while True:
        nav = {}
        a = sorted(cur.inputs.items())
        for (i,(k,n)) in enumerate(a):
            i = -(len(a)-i)
            nav[i] = n
            print(f' {i}:{k} {n!r}')
        print(f'   {cur!r}')
        for (i,(k,n)) in enumerate(cur.outputs):
            i = i+1
            nav[i] = n
            print(f' +{i}:{k} {n!r}')

        try:
            cmd1 = input('] ')
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
            cur = nav[d]
        cmd0 = cmd1

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
