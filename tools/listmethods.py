#!/usr/bin/env python
import sys
import re
from graph import DFGraph, get_graphs
from getwords import stripmethodname, splitwords

def main(argv):
    args = argv[1:]

    nnames = 0
    words = {}
    for path in args:
        for graph in get_graphs(path):
            name = stripmethodname(graph.name)
            if name is None: continue
            #print(name)
            nnames += 1
            for w in splitwords(name):
                if w not in words:
                    words[w] = 0
                words[w] += 1

    print()
    print('total', nnames)
    for (w,n) in sorted(words.items(), key=lambda x:x[1], reverse=True):
        print(n, w)
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
