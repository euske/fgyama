#!/usr/bin/env python
import sys
import logging
from graphs import IDFBuilder, parsemethodname
from algos import SCC, Cons

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

    if 0:
        # list all the methods and number of its uses. (being called)
        for method in builder.methods:
            mname = method.name
            if mname not in builder.funcalls: continue
            try:
                (klass,name,func) = parsemethodname(mname)
                n = len(builder.funcalls[mname])
                print(n, name)
            except ValueError:
                pass
        return

    def getcallers(callee):
        if callee.name not in builder.funcalls: return []
        return set( node.method for node in builder.funcalls[callee.name] )

    (cpts, _) = SCC.fromnodes(builder.methods, getcallers)
    def visit(count, cpt):
        if cpt not in count:
            count[cpt] = 0
        count[cpt] += 1
        if 2 <= count[cpt]:
            return [cpt]
        else:
            return [cpt] + [ visit(count, c) for c in cpt.linkfrom ]
    def show(count, r, level=0):
        cpt = r[0]
        try:
            (klass,name,func) = parsemethodname(cpt.nodes[0].name)
        except ValueError:
            return
        h = '  '*level
        if count[cpt] < 2:
            print(h+f'{level} {name}')
            for c in r[1:]:
                show(count, c, level+1)
        else:
            print(h+f'{level} {name} ...')
        return
    for cpt in cpts:
        if cpt.linkto: continue
        count = {}
        r = visit(count, cpt)
        if len(r) < 2: continue
        show(count, r)
        print()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
