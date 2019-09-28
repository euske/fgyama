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
    i2c = {}
    clusters = []
    for rec in getrecs(fp):
        t = rec['SIM']
        if t < threshold: continue
        (i1, i2) = rec['ITEMS']
        if i1 in i2c and i2 in i2c:
            c1 = i2c[i1]
            c2 = i2c[i2]
            if c1 is not c2:
                c1.update(c2)
                for i in c:
                    i2c[i] = c1
                clusters.remove(c2)
        elif i1 in i2c:
            c = i2c[i1]
            c.add(i2)
            i2c[i2] = c
        elif i2 in i2c:
            c = i2c[i2]
            c.add(i1)
            i2c[i1] = c
        else:
            c = set([i1, i2])
            i2c[i1] = i2c[i2] = c
            clusters.append(c)

    assert len(i2c) == sum( len(c) for c in clusters )
    for c in clusters:
        print('+CLUSTER', len(c))
        for i in c:
            print(i)
        print()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
