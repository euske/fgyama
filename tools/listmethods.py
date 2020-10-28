#!/usr/bin/env python
import sys
import re
from graphs import get_graphs, splitmethodname
from words import splitwords, postag

def main(argv):
    import getopt
    def usage():
        print(f'usage: {argv[0]} '
              '[-d] [-n limit] '
              '[graph ...]')
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'dn:')
    except getopt.GetoptError:
        return usage()
    debug = 0
    limit = 10
    for (k, v) in opts:
        if k == '-d': debug += 1
        elif k == '-n': limit = int(v)

    words = {}
    for path in args:
        for method in get_graphs(path):
            (name,args,retype) = splitmethodname(method.name)
            if name is None: continue
            #print(name)
            for (pos,w) in postag(reversed(splitwords(name))):
                if pos in words:
                    d = words[pos]
                else:
                    d = words[pos] = {}
                d[w] = d.get(w, 0) + 1

    print('counts', { pos:sum(d.values()) for (pos,d) in words.items() })
    print('words', { pos:len(d) for (pos,d) in words.items() })
    for (pos,d) in sorted(words.items(), key=lambda x:len(x[1]), reverse=True):
        print(pos)
        a = sorted(d.items(), key=lambda x:x[1], reverse=True)
        if 0 < limit:
            a = a[:limit]
        for (w,n) in a:
            print(f'  {n} {w}')
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
