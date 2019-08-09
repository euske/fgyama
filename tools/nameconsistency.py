#!/usr/bin/env python
import sys
import os
import re
from srcdb import SourceDB, SourceAnnot
from featdb import FeatDB
from getwords import stripid, splitwords
from naivebayes import NaiveBayes

# main
def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-d] [-f] [-o path] [-i path] [-c encoding] [-B srcdb] [-t threshold] feats.db' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'dfo:i:c:B:w:t:')
    except getopt.GetoptError:
        return usage()
    debug = 0
    force = False
    encoding = None
    outpath = None
    inpath = None
    srcdb = None
    threshold = 0.99
    for (k, v) in opts:
        if k == '-d': debug += 1
        elif k == '-f': force = True
        elif k == '-o': outpath = v
        elif k == '-i': inpath = v
        elif k == '-c': encoding = v
        elif k == '-B': srcdb = SourceDB(v, encoding)
        elif k == '-t': threshold = float(v)
    assert inpath is None or outpath is None
    if not force and outpath is not None and os.path.exists(outpath):
        print('Already exists: %r' % outpath)
        return 1
    if not args: return usage()
    dbpath = args.pop(0)

    def learn(item, feats):
        name = stripid(item)
        words = splitwords(name)
        for w in words:
            nb.add(w, feats.keys())
        return

    def predict(item, feats):
        name = stripid(item)
        words = splitwords(name)
        f2 = nb.narrow(feats.keys(), threshold)
        if len(f2) <= 1: return
        cands = nb.get(f2)
        n = len(words)
        if sorted(words) != sorted( w for (w,_) in cands[:n] ):
            print('!', len(feats), len(f2), item)
            print('#', cands[:n+1])
            nb.explain([ w for (w,_) in cands[:5] ], f2)
        return

    nb = NaiveBayes()
    proc = learn
    if inpath is not None:
        print('Importing model: %r' % inpath)
        with open(inpath, 'rb') as fp:
            nb.load(fp)
            proc = predict

    db = FeatDB(dbpath)
    for (tid,item) in db:
        feats = db.get_feats(tid)
        proc(item, feats)
        sys.stderr.write('.'); sys.stderr.flush()

    if outpath is not None:
        print('Exporting model: %r' % outpath)
        with open(outpath, 'wb') as fp:
            nb.save(fp)

    if inpath is None and outpath is None:
        nb.commit()
        for (tid,item) in db.get_items():
            feats = db.get_feats(tid)
            predict(item, feats)

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
