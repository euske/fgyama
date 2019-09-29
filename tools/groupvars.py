#!/usr/bin/env python
import sys
import json

def getrecs(fp):
    rec = {}
    for line in fp:
        line = line.strip()
        if line.startswith('+'):
            (k,_,v) = line[1:].partition(' ')
            rec[k] = json.loads(v)
        elif not line:
            yield rec
            rec = {}
    return

# main
def main(argv):
    import fileinput
    import getopt
    def usage():
        print(f'usage: {argv[0]} [-t threshold] [simvars]')
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'dt:')
    except getopt.GetoptError:
        return usage()
    debug = 0
    threshold = 0.90
    for (k, v) in opts:
        if k == '-d': debug += 1
        elif k == '-t': threshold = float(v)

    fp = fileinput.input(args)
    ic = {}
    clusters = []
    for rec in getrecs(fp):
        t = rec['SIM']
        if t < threshold: continue
        (i1, i2) = rec['ITEMS']
        if i1 in ic and i2 in ic:
            c1 = ic[i1]
            c2 = ic[i2]
            if c1 is not c2:
                c1.update(c2)
                for i in c2:
                    ic[i] = c1
                clusters.remove(c2)
        elif i1 in ic:
            c = ic[i1]
            c.add(i2)
            ic[i2] = c
        elif i2 in ic:
            c = ic[i2]
            c.add(i1)
            ic[i1] = c
        else:
            c = set([i1, i2])
            ic[i1] = ic[i2] = c
            clusters.append(c)

    assert len(ic) == sum( len(c) for c in clusters )
    for c in sorted(clusters, key=len, reverse=True):
        print('+CLUSTER', len(c))
        print('+ITEMS', json.dumps(list(c)))
        print()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
