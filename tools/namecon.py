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
        print(f'usage: {argv[0]} '
              '[-d] [-o path] [-i path] [-t threshold] [-f nfeats] '
              'feats.db [items ...]')
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'do:i:t:f:')
    except getopt.GetoptError:
        return usage()
    debug = 0
    outpath = None
    inpath = None
    threshold = 0.75
    nfeats = 3
    C = 0.7
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
    nallitems = len(db.get_items())
    items = db.get_items()
    if args:
        items = [ (tid,item) for (tid,item) in items if item in args ]

    def learn(tid, item, fids):
        name = stripid(item)
        words = splitwords(name)
        for w in words:
            nb.add(w, fids)
        return True

    def predict(tid, item, fids):
        name = stripid(item)
        words = splitwords(name)
        for w in words:
            nb.remove(w, fids)
        f2 = nb.narrow(fids, threshold)
        if f2:
            cands = nb.getkeys(f2)
        else:
            cands = []
        for w in words:
            nb.add(w, fids)
        if not cands: return False
        (_,topword) = cands[0]
        if topword in words: return True
        cands = cands[:len(words)+1]
        print('#', words, '->', cands)
        print('+ITEM', json.dumps(item))
        print('+CANDS', json.dumps(cands))
        feats = []
        fscore = {}
        for fid in fids:
            d = nb.fcount[fid]
            if topword not in d: continue
            # A rarer feature overall means stronger indication.
            df = math.log(nallitems / db.get_numfeatitems(fid))
            # A frequent feature for this category means stronger indication.
            ff = d[topword] / d[None]
            # Discount a "distant" feature from the subject.
            feat = db.get_feat(fid)
            fscore[fid] = math.exp(-C*abs(feat[0])) * df * ff
            feats.append((feat, df, ff))
        print('+FEATS', json.dumps(feats))
        fid2srcs0 = db.get_feats(tid, source=True)
        print('+SOURCE', json.dumps(fid2srcs0[0]))
        supports = []
        fids2 = sorted(fscore.keys(), key=lambda fid:fscore[fid], reverse=True)
        for fid in fids2[:nfeats]:
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
                srcs1 = fid2srcs1[0] + fid2srcs1[fid]
                evidence = item1
                break
            if evidence is None: continue
            feat = db.get_feat(fid)
            supports.append((feat, srcs0, evidence, srcs1))
        print('+SUPPORT', json.dumps(supports))
        print()
        return False

    nb = NaiveBayes()
    proc = learn
    if inpath is not None:
        print('Importing model: %r' % inpath, file=sys.stderr)
        with open(inpath, 'rb') as fp:
            nb.load(fp)
            proc = predict

    n = m = 0
    for (tid,item) in items:
        fids = db.get_feats(tid)
        fids = [ fid for fid in fids if fid != 0 ]
        n += 1
        if proc(tid, item, fids):
            m += 1
        sys.stderr.write('.'); sys.stderr.flush()
    print('\nProcessed: %d/%d' % (m,n), file=sys.stderr)

    if outpath is not None:
        print('Exporting model: %r' % outpath, file=sys.stderr)
        with open(outpath, 'wb') as fp:
            nb.save(fp)

    if inpath is None and outpath is None:
        for (tid,item) in items:
            fids = db.get_feats(tid)
            predict(tid, item, fids)

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
