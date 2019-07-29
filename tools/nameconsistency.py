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
        print('usage: %s [-d] [-o path] [-i path] [feats ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'do:i:')
    except getopt.GetoptError:
        return usage()
    debug = 0
    outpath = None
    inpath = None
    for (k, v) in opts:
        if k == '-d': debug += 1
        elif k == '-o': outpath = v
        elif k == '-i': inpath = v
    if not args: return usage()
    assert inpath is None or outpath is None

    path = args.pop(0)
    print('Loading: %r...' % path, file=sys.stderr)

    nb = NaiveBayes()

    def learn(item, feats):
        name = stripid(item)
        words = splitwords(name)
        for w in words:
            nb.add(w, feats)
        sys.stderr.write('.'); sys.stderr.flush()
        return

    def predict(item, feats):
        name = stripid(item)
        words = splitwords(name)
        cands = nb.get(feats)
        n = len(words)
        if sorted(words) != sorted( w for (w,_,_) in cands[:n] ):
            print(item)
            print('#', [ (w,p) for (w,p,_) in cands[:n+1] ])
        return

    f = learn
    if inpath is not None:
        with open(inpath, 'rb') as fp:
            nb.load(fp)
            f = predict

    with open(path) as fp:
        a = item = None
        for line in fp:
            if line.startswith('! '):
                data = eval(line[2:])
                if data[0] == 'REF':
                    item = data[1]
                    a = set()
                else:
                    a = None
            elif a is not None and line.startswith('+ '):
                (n,_,line) = line[2:].partition(' ')
                data = eval(line)
                feat = data[0:4]
                a.add(feat)
            elif a is not None and not line.strip():
                if a:
                    f(item, a)

    if outpath is not None:
        with open(outpath, 'wb') as fp:
            nb.save(fp)
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
