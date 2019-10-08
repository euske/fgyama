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
              '[-d] [-o path] [-i path] [-r ratio] [-s supports] [-v vars] '
              'feats.db [items ...]')
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'do:i:r:s:v:')
    except getopt.GetoptError:
        return usage()
    debug = 0
    outpath = None
    inpath = None
    ratio = 0.5
    maxsupports = 3
    types = {}
    C = 0.4   # distance weight should be (1.5**d) ~= exp(0.4*d)
    for (k, v) in opts:
        if k == '-d': debug += 1
        elif k == '-o': outpath = v
        elif k == '-i': inpath = v
        elif k == '-r': ratio = float(v)
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
        # Use only prominent features that appears more than a certain threshold.
        threshold = int(max(feats.values()) * ratio)
        f2 = [ feat for (feat,fc) in feats.items() if threshold <= fc ]
        for w in words:
            nb.removedict(w, count, feats)
        cands = nb.getkeyfeats(f2)[:len(words)]
        for w in words:
            nb.adddict(w, count, feats)
        if not cands: return False
        cwords = [ w for (_,w,_) in cands ]
        topword = cwords[0]
        if topword in words: return True
        print('+ITEM', json.dumps(item))
        print('+WORDS', json.dumps(words))
        print('+CANDS', json.dumps(cwords))
        if item in defaultnames:
            print('+DEFAULT', json.dumps(defaultnames[item]))
        fids0 = db.get_feats(tid, source=True)
        srcs0 = {0:[], 1:[], -1:[]}
        for (fid,(_,srcs)) in fids0.items():
            if fid == 0:
                d = 0
            else:
                d = db.get_feat(fid)[0]
            if d in srcs0:
                srcs0[d].extend(srcs)
        print('+SOURCE', json.dumps([ (d,list(set(srcs))) for (d,srcs) in srcs0.items() ]))
        supports = []
        for (_,w,a) in cands:
            # Find top N features for each word.
            fs = []
            for (fid,c) in a[1:]:
                feat = db.get_feat(fid)
                assert feat is not None
                # A rarer feature overall means stronger indication.
                df = math.log(nallitems / db.get_numfeatitems(fid))
                # A more prominent feature for this category means stronger indication.
                ff = c / nb.fcount[fid][None]
                # Discount a "distant" feature from the subject.
                ds = math.exp(-C*abs(feat[0]))
                fs.append((ds * df * ff, fid, feat))
            fs = sorted(fs, reverse=True)[:maxsupports]
            score = sum( s for (s,_,_) in fs )
            # Find the variables that contains the same feature.
            ss = []
            for (_,fid,_) in fs:
                found = None
                (_,srcs0) = fids0[fid]
                tids = db.get_featitems(fid)
                for tid1 in tids.keys():
                    if tid1 == tid: continue
                    item1 = db.get_item(tid1)
                    name1 = stripid(item1)
                    if w not in splitwords(name1): continue
                    fids1 = db.get_feats(tid1, source=True)
                    (_,srcs1a) = fids1[0]
                    (_,srcs1b) = fids1[fid]
                    found = (srcs0, item1, srcs1a+srcs1b)
                    break
                ss.append(found)
            supports.append((w, score, list(zip(fs, ss))))
        print('+SCORE', json.dumps(sum( score for (_,score,_) in supports )))
        print('+SUPPORTS', json.dumps(supports))
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
