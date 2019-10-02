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

def getvars(path):
    types = {}
    with open(path) as fp:
        for line in fp:
            line = line.strip()
            if not line: continue
            (v,_,t) = line.partition(' ')
            types[v] = t
    return types

def getdefaultnames(types):
    names = {}
    for (v,t) in types.items():
        v = stripid(v)
        if t in names:
            c = names[t]
        else:
            c = names[t] = {}
        if v not in c:
            c[v] = 0
        c[v] += 1
    for (t,c) in names.items():
        maxn = -1
        maxv = None
        for (v,n) in c.items():
            if maxn < n:
                maxn = n
                maxv = v
        names[t] = maxv
    defaults = {}
    for (v,t) in types.items():
        defaults[v] = names[t]
    return defaults

# main
def main(argv):
    import fileinput
    import getopt
    def usage():
        print(f'usage: {argv[0]} '
              '[-d] [-o path] [-i path] [-t threshold] [-s supports] [-v vars] '
              'feats.db [items ...]')
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'do:i:t:s:v:')
    except getopt.GetoptError:
        return usage()
    debug = 0
    outpath = None
    inpath = None
    threshold = 0.75
    maxsupports = 3
    types = {}
    C = 0.7
    for (k, v) in opts:
        if k == '-d': debug += 1
        elif k == '-o': outpath = v
        elif k == '-i': inpath = v
        elif k == '-t': threshold = float(v)
        elif k == '-s': maxsupports = int(v)
        elif k == '-v': types = getvars(v)
    assert inpath is None or outpath is None
    if outpath is not None and os.path.exists(outpath):
        print('Already exists: %r' % outpath)
        return 1
    if not args: return usage()
    dbpath = args.pop(0)
    db = FeatDB(dbpath)
    nallitems = len(db.get_items())
    defaultnames = getdefaultnames(types)
    items = db.get_items()
    if args:
        items = [ (tid,item) for (tid,item) in items if item in args ]

    def learn(tid, item, fids):
        name = stripid(item)
        words = splitwords(name)
        (count,_) = fids[0]
        feats = { feat:fc for (feat,(fc,_)) in fids.items() }
        for w in words:
            nb.adddict(w, count, feats)
        return True

    def predict(tid, item, fids):
        name = stripid(item)
        words = splitwords(name)
        (count,_) = fids[0]
        feats = { fid:fc for (fid,(fc,_)) in fids.items() if fid != 0 }
        for w in words:
            nb.removedict(w, count, feats)
        threshold = max(feats.values()) // 2
        f2 = [ feat for (feat,fc) in feats.items() if threshold <= fc ]
        cands = nb.getkeys(f2)
        for w in words:
            nb.adddict(w, count, feats)
        if not cands: return False
        cands = cands[:len(words)]
        cwords = [ w for (_,w) in cands ]
        topword = cwords[0]
        if topword in words: return True
        print('#', words, '->', cands)
        print('+ITEM', json.dumps(item))
        print('+CANDS', json.dumps(cands))
        if item in defaultnames:
            print('+DEFAULT', json.dumps(defaultnames[item]))
        feats = []
        fscore = {}
        for fid in f2:
            d = nb.fcount[fid]
            if topword not in d: continue
            # A rarer feature overall means stronger indication.
            df = math.log(nallitems / db.get_numfeatitems(fid))
            # A frequent feature for this category means stronger indication.
            ff = d[topword] / d[None]
            # Discount a "distant" feature from the subject.
            feat = db.get_feat(fid)
            assert feat is not None
            fscore[fid] = math.exp(-C*abs(feat[0])) * df * ff
            feats.append((feat, df, ff))
        print('+FEATS', json.dumps(feats))
        fids0 = db.get_feats(tid, source=True)
        (_,srcs) = fids0[0]
        print('+SOURCE', json.dumps(srcs))
        supports = []
        fids2 = sorted(fscore.keys(), key=lambda fid:fscore[fid], reverse=True)
        for cw in cwords:
            for fid in fids2:
                (_,srcs0) = fids0[fid]
                srcs1 = []
                evidence = None
                tids = db.get_featitems(fid)
                for tid1 in tids.keys():
                    if tid1 == tid: continue
                    item1 = db.get_item(tid1)
                    name1 = stripid(item1)
                    if cw not in splitwords(name1): continue
                    fids1 = db.get_feats(tid1, source=True)
                    (_,srcs1a) = fids1[0]
                    (_,srcs1b) = fids1[fid]
                    srcs1 = srcs1a + srcs1b
                    evidence = item1
                    break
                if evidence is None: continue
                feat = db.get_feat(fid)
                supports.append((feat, srcs0, evidence, srcs1))
                if maxsupports <= len(supports): break
            if maxsupports <= len(supports): break
        print('+SUPPORT', json.dumps(supports))
        print()
        return False

    nb = NaiveBayes()
    proc = learn
    if inpath is not None:
        print(f'Importing model: {inpath!r}', file=sys.stderr)
        with open(inpath, 'rb') as fp:
            nb.load(fp)
            proc = predict

    n = m = 0
    for (tid,item) in items:
        fids = db.get_feats(tid)
        n += 1
        if proc(tid, item, fids):
            m += 1
        sys.stderr.write('.'); sys.stderr.flush()
    print(f'\nProcessed: {m}/{n}', file=sys.stderr)

    if outpath is not None:
        print(f'Exporting model: {outpath!r}', file=sys.stderr)
        with open(outpath, 'wb') as fp:
            nb.save(fp)

    if inpath is None and outpath is None:
        for (tid,item) in items:
            fids = db.get_feats(tid)
            predict(tid, item, fids)

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
