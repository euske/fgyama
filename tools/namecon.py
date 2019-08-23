#!/usr/bin/env python
import sys
import os
import re
import math
import json
from srcdb import SourceDB, SourceAnnot
from featdb import FeatDB
from getwords import stripid, splitwords
from naivebayes import NaiveBayes

# main
def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-d] [-o path] [-i path] [-t threshold] [-f nfeats] feats.db' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'dfo:i:w:t:')
    except getopt.GetoptError:
        return usage()
    debug = 0
    outpath = None
    inpath = None
    threshold = 0.99
    nfeats = 3
    for (k, v) in opts:
        if k == '-d': debug += 1
        elif k == '-o': outpath = v
        elif k == '-i': inpath = v
        elif k == '-t': threshold = float(v)
        elif k == '-f': nfeats = int(v)
    assert inpath is None or outpath is None
    if outpath is not None and os.path.exists(outpath):
        print('Already exists: %r' % outpath)
        return 1
    if not args: return usage()
    dbpath = args.pop(0)
    db = FeatDB(dbpath)

    def learn(tid, item, fids):
        name = stripid(item)
        words = splitwords(name)
        for w in words:
            nb.add(w, fids)
        return

    def predict(tid, item, fids):
        f2 = nb.narrow(fids, threshold)
        if len(f2) <= 1: return
        cands = nb.getkeys(f2)
        if not cands: return
        name = stripid(item)
        words = splitwords(name)
        (_,topword) = cands[0]
        if topword in words: return
        cands = cands[:len(words)+1]
        print('#', words, cands)
        print('+ITEM', json.dumps(item))
        print('+CANDS', json.dumps(cands))
        feats = []
        nallitems = len(db.get_items())
        for fid in fids:
            d = nb.fcount[fid]
            if topword not in d: continue
            df = math.log(nallitems / db.get_numfeatitems(fid))
            feat = db.get_feat(fid)
            score = math.exp(-abs(feat[0])) * df * d[topword] / d[None]
            feats.append((score, fid))
        feats.sort(reverse=True)
        totalscore = sum( score for (score,_) in feats )
        print('+SCORE', totalscore)
        fid2srcs0 = db.get_feats(tid, source=True)
        print('+SOURCE', json.dumps(fid2srcs0[0]))
        supports = []
        for (_,fid) in feats[:nfeats]:
            srcs0 = fid2srcs0[fid]
            srcs1 = []
            evidence = None
            tids = db.get_featitems(fid)
            for tid1 in tids.keys():
                if tid1 == tid: continue
                item1 = db.get_item(tid1)
                name = stripid(item1)
                words = splitwords(name)
                if topword not in words: continue
                fid2srcs1 = db.get_feats(tid1, source=True)
                srcs1 = fid2srcs1[fid]
                evidence = item1
                break
            if evidence is None: continue
            feat = db.get_feat(fid)
            supports.append((feat, srcs0, evidence, srcs1))
        print('+SUPPORT', json.dumps(supports))
        print()
        return

    nb = NaiveBayes()
    proc = learn
    if inpath is not None:
        print('Importing model: %r' % inpath, file=sys.stderr)
        with open(inpath, 'rb') as fp:
            nb.load(fp)
            proc = predict

    for (tid,item) in db:
        fids = db.get_feats(tid)
        proc(tid, item, [ fid for fid in fids if fid != 0 ])
        sys.stderr.write('.'); sys.stderr.flush()

    if outpath is not None:
        print('Exporting model: %r' % outpath, file=sys.stderr)
        nb.commit()
        with open(outpath, 'wb') as fp:
            nb.save(fp)

    if inpath is None and outpath is None:
        nb.commit()
        for (tid,item) in db.get_items():
            fids = db.get_feats(tid)
            predict(tid, item, fids)

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
