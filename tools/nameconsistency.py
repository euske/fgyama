#!/usr/bin/env python
import sys
import re
from naivebayes import NaiveBayes

NAME = re.compile(r'\w+$', re.U)
def stripid(name):
    m = NAME.search(name)
    if m:
        return m.group(0)
    else:
        return None

WORD = re.compile(r'[a-z]+[A-Z]?|[A-Z]+|[0-9]+')
def splitwords(s):
    """
    >>> splitwords('name')
    ['name']
    >>> splitwords('this_is_name_!')
    ['name', 'is', 'this']
    >>> splitwords('thisIsName')
    ['name', 'is', 'this']
    >>> splitwords('SomeXMLStuff')
    ['stuff', 'xml', 'some']
"""
    if s is None: return []
    n = len(s)
    r = ''.join(reversed(s))
    return [ s[n-m.end(0):n-m.start(0)].lower() for m in WORD.finditer(r) ]

# main
def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-d] [graph ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'd')
    except getopt.GetoptError:
        return usage()
    debug = 0
    for (k, v) in opts:
        if k == '-d': debug += 1
    if not args: return usage()

    path = args.pop(0)
    print('Loading: %r...' % path, file=sys.stderr)
    feats = {}
    with open(path) as fp:
        a = None
        for line in fp:
            if line.startswith('! '):
                data = eval(line[2:])
                if data[0] == 'REF':
                    item = data[1]
                    if item in feats:
                        a = feats[item]
                    else:
                        a = feats[item] = {}
                else:
                    a = None
            elif a is not None and line.startswith('+ '):
                (n,_,line) = line[2:].partition(' ')
                data = eval(line)
                feat = data[0:4]
                if feat not in a:
                    a[feat] = 0
                a[feat] += int(n)
    #
    nb = NaiveBayes()
    for (item,a) in feats.items():
        name = stripid(item)
        words = splitwords(name)
        for w in words:
            nb.add(w, a.keys())
    nb.commit()
    #
    nitems = 0
    for (item,a) in feats.items():
        name = stripid(item)
        words = splitwords(name)
        cands = nb.get(a)
        n = len(words)
        if sorted(words) != sorted(cands[:n]):
            print(item)
            print('#', [ (w,p) for (w,p,_) in cands[:n+1] ])
        nitems += 1
    print(nitems)

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
