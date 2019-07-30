#!/usr/bin/env python
import sys
import re
from srcdb import SourceDB, SourceAnnot
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
        print('usage: %s [-d] [-o path] [-i path] [-B srcdb] [-f find] [feats ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'do:i:B:f:')
    except getopt.GetoptError:
        return usage()
    debug = 0
    encoding = None
    outpath = None
    inpath = None
    srcdb = None
    watch = set()
    threshold = 0.99
    for (k, v) in opts:
        if k == '-d': debug += 1
        elif k == '-o': outpath = v
        elif k == '-i': inpath = v
        elif k == '-B': srcdb = SourceDB(v, encoding)
        elif k == '-f': watch.add(v)
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
        feats = nb.narrow(feats, threshold)
        if not feats: return
        cands = nb.get(feats)
        n = len(words)
        if sorted(words) != sorted( w for (w,_,_) in cands[:n] ):
            print(len(feats), item)
            print('#', [ (w,p) for (w,p,_) in cands[:n+1] ])
        return

    item2feats = None
    if inpath is None and outpath is None:
        item2feats = {}

    f = learn
    if inpath is not None:
        with open(inpath, 'rb') as fp:
            nb.load(fp)
            f = predict

    srcmap = {}
    with open(path) as fp:
        item = feats = None
        annot = None
        for line in fp:
            if line.startswith('+SOURCE'):
                (_,_,line) = line.partition(' ')
                (srcid, path) = eval(line)
                srcmap[srcid] = path
            elif line.startswith('! '):
                data = eval(line[2:])
                if data[0] == 'REF':
                    item = data[1]
                    feats = set()
                    if item in watch:
                        (srcid,start,end) = data[2]
                        annot = SourceAnnot(srcdb)
                        annot.add(srcmap[srcid], start, end, 0)
                else:
                    feats = None
            elif feats is not None and line.startswith('+ '):
                (n,_,line) = line[2:].partition(' ')
                data = eval(line)
                feat = data[0:4]
                feats.add(feat)
                if annot is not None and data[4] is not None:
                    (srcid,start,end) = data[4]
                    annot.add(srcmap[srcid], start, end, 0)
            elif feats is not None and not line.strip():
                if feats:
                    f(item, feats)
                    if item2feats is not None:
                        item2feats[item] = feats
                if annot is not None:
                    annot.show_text()
                    annot = None

    if outpath is not None:
        with open(outpath, 'wb') as fp:
            nb.save(fp)

    if item2feats is not None:
        nb.commit()
        for (item,feats) in item2feats.items():
            predict(item, feats)

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
