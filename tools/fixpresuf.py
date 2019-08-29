#!/usr/bin/env python
import sys
from getwords import stripid, splitwords

def get(d, k):
    if k in d:
        a = d[k]
    else:
        a = d[k] = []
    return a

def showtop(d, n=5):
    keys = sorted(d.keys(), key=lambda k:len(d[k]), reverse=True)
    return ', '.join(
        '%s(%d)' % (k[0], len(d[k]))
        for k in keys[:n] )

def doit(refs):
    prefix = {}
    midfix = {}
    suffix = {}
    for (ref,ntype) in refs.items():
        words = splitwords(stripid(ref))
        if len(words) < 2: continue
        get(prefix, (words[-1],ntype)).append(ref)
        for w in words[1:-1]:
            get(midfix, (w,ntype)).append(ref)
        get(suffix, (words[0],ntype)).append(ref)
    print('total:', len(refs))
    print('prefix:', showtop(prefix))
    print('midfix:', showtop(midfix))
    print('suffix:', showtop(suffix))
    print()
    return

def main(argv):
    for path in argv[1:]:
        print(path)
        refs = {}
        with open(path) as fp:
            for line in fp:
                (ref,_,ntype) = line.strip().partition(' ')
                refs[ref] = ntype
        doit(refs)
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
