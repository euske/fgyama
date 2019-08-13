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

def tocamelcase(words):
    return ''.join(
        (w if i == 0 else w[0].upper()+w[1:])
        for (i,w) in enumerate(words) )

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
            nb.add(w, fids.keys())
        return

    def predict(tid, item, fids):
        name = stripid(item)
        words = splitwords(name)
        nwords = len(words)
        f2 = nb.narrow(fids.keys(), threshold)
        if len(f2) <= 1: return
        cands = nb.getkeys(f2)
        if not cands: return
        keys = [ k for (k,_) in cands ]
        topword = keys[0]
        if topword in words: return
        print('#', words, cands[:nwords+1])
        feats = []
        for (k,fs) in nb.getfeats([topword]).items():
            fs = dict(fs)
            for fid in fids:
                #assert fid in fid2srcs
                if fid != 0 and fid in fs:
                    (d,_,_) = db.get_feat(fid)
                    score = math.exp(-abs(d)) * fs[fid]
                    feats.append((score, fid))
        feats.sort(reverse=True)
        totalscore = sum( score for (score,_) in feats )
        print('+ITEM', json.dumps((totalscore, item)))
        for (i,k) in enumerate(keys[:nwords+1]):
            if k in words:
                name1 = tocamelcase(keys[:i])
                name2 = tocamelcase(keys[:i]+[name])
                print('+NAME', json.dumps((name, name1, name2)))
                break
        else:
            print('+NAME', json.dumps((name, topword)))
        fid2srcs = db.get_feats(tid, source=True)
        print('+SOURCE', json.dumps(fid2srcs[0]))
        supports = []
        for (_,fid) in feats[:nfeats]:
            srcs0 = fid2srcs[fid]
            srcs1 = []
            evidence = None
            item2srcs = db.get_featitems(fid, resolve=True, source=True)
            for (item1,srcs) in item2srcs.items():
                if item1 == item: continue
                if not srcs: continue
                name = stripid(item1)
                words = splitwords(name)
                if topword not in words: continue
                srcs1 = srcs
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
        proc(tid, item, fids)
        #sys.stderr.write('.'); sys.stderr.flush()

    if outpath is not None:
        print('Exporting model: %r' % outpath, file=sys.stderr)
        with open(outpath, 'wb') as fp:
            nb.save(fp)

    if inpath is None and outpath is None:
        nb.commit()
        for (tid,item) in db.get_items():
            fids = db.get_feats(tid)
            predict(tid, item, fids)

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
