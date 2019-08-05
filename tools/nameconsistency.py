#!/usr/bin/env python
import sys
import re
from srcdb import SourceDB, SourceAnnot
from getwords import stripid, splitwords
from naivebayes import NaiveBayes

# main
def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-d] [-o path] [-i path] [-c encoding] [-B srcdb] [-t threshold] [feats ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'do:i:c:B:w:t:')
    except getopt.GetoptError:
        return usage()
    debug = 0
    encoding = None
    outpath = None
    inpath = None
    srcdb = None
    threshold = 0.99
    for (k, v) in opts:
        if k == '-d': debug += 1
        elif k == '-o': outpath = v
        elif k == '-i': inpath = v
        elif k == '-c': encoding = v
        elif k == '-B': srcdb = SourceDB(v, encoding)
        elif k == '-t': threshold = float(v)
    assert inpath is None or outpath is None

    nb = NaiveBayes()

    def learn(item, feats, annot):
        name = stripid(item)
        words = splitwords(name)
        for w in words:
            nb.add(w, feats)
        sys.stderr.write('.'); sys.stderr.flush()
        return

    def predict(item, feats, annot):
        name = stripid(item)
        words = splitwords(name)
        f2 = nb.narrow(feats, threshold)
        if len(f2) <= 1: return
        cands = nb.get(f2)
        n = len(words)
        if sorted(words) != sorted( w for (w,_) in cands[:n] ):
            print('!', len(feats), len(f2), item)
            print('#', cands[:n+1])
            if annot is not None:
                annot.show_text()
            nb.explain([ w for (w,_) in cands[:5] ], f2)
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
    item = feats = annot = None
    for line in fileinput.input(args):
        line = line.strip()
        if line.startswith('+SOURCE'):
            (_,_,line) = line.partition(' ')
            (srcid, path) = eval(line)
            srcmap[srcid] = path

        elif line.startswith('! '):
            data = eval(line[2:])
            item = feats = annot = None
            if data[0] == 'REF':
                item = data[1]
                feats = set()
                if srcdb is not None:
                    annot = SourceAnnot(srcdb)
                    for (srcid,start,end) in data[2:]:
                        annot.add(srcmap[srcid], start, end, 0)

        elif feats is not None and line.startswith('+ '):
            data = eval(line[2:])
            feat = data[0:3]
            feats.add(feat)

        elif feats is not None and not line:
            if feats:
                f(item, feats, annot)
                if item2feats is not None:
                    item2feats[item] = feats
            item = feats = annot = None

    if outpath is not None:
        with open(outpath, 'wb') as fp:
            nb.save(fp)

    if item2feats is not None:
        nb.commit()
        for (item,feats) in item2feats.items():
            predict(item, feats)

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
