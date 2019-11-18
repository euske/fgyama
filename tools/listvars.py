#!/usr/bin/env python
import sys
import re
from graph import get_graphs
from getwords import stripid, splitwords, postag

def main(argv):
    import getopt
    def usage():
        print(f'usage: {argv[0]} '
              '[-d] [-n limit] [-w] '
              '[graph ...]')
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'dWn:')
    except getopt.GetoptError:
        return usage()
    debug = 0
    limit = 10
    wordstat = False
    for (k, v) in opts:
        if k == '-d': debug += 1
        elif k == '-W': wordstat = True
        elif k == '-n': limit = int(v)

    refs = {}
    words = {}
    for path in args:
        for method in get_graphs(path):
            for node in method:
                ref = node.ref
                if ref is None: continue
                if node.ntype is None: continue
                if ref[0] not in '$@': continue
                if wordstat:
                    name = stripid(ref)
                    if name is None: continue
                    for (pos,w) in postag(reversed(splitwords(name))):
                        if pos in words:
                            d = words[pos]
                        else:
                            d = words[pos] = {}
                        d[w] = d.get(w, 0) + 1
                else:
                    refs[ref] = node.ntype

    if wordstat:
        print('counts', { pos:sum(d.values()) for (pos,d) in words.items() })
        print('words', { pos:len(d) for (pos,d) in words.items() })
        for (pos,d) in sorted(words.items(), key=lambda x:len(x[1]), reverse=True):
            print(pos)
            a = sorted(d.items(), key=lambda x:x[1], reverse=True)
            if 0 < limit:
                a = a[:limit]
            for (w,n) in a:
                print(f'  {n} {w}')
    else:
        for (ref,ntype) in sorted(refs.items()):
            print(ref, ntype)
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
