#!/usr/bin/env python
import sys
import re
import random
from srcdb import SourceDB, SourceAnnot
from featdb import FeatDB
from getwords import stripid, splitwords
from vsm import VSM
from math import exp

def jaccard(s1, s2):
    if not s1 or not s2: return 0
    return len(s1.intersection(s2)) / len(s1.union(s2))

# main
def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-d] [-B srcdb] [-n minpairs] [-t threshold] vars feats [var ...]' %
              argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'dB:n:t:')
    except getopt.GetoptError:
        return usage()
    debug = 0
    encoding = None
    srcdb = None
    minpairs = 0
    threshold = 0.30
    for (k, v) in opts:
        if k == '-d': debug += 1
        elif k == '-B': srcdb = SourceDB(v, encoding)
        elif k == '-n': minpairs = int(v)
        elif k == '-t': threshold = float(v)
    if not args: return usage()

    path = args.pop(0)
    refs = {}
    with open(path) as fp:
        for line in fp:
            (ref,_,ntype) = line.strip().partition(' ')
            refs[ref] = ntype
    print('Refs: %r' % len(refs), file=sys.stderr)

    dbpath = args.pop(0)
    db = FeatDB(dbpath)
    
    sp = VSM()
    for (tid,_) in db:
        feats = db.get_feats(tid, resolve=True)
        sp.add(tid, { feat:exp(-abs(feat[0])) for feat in feats.keys()
                      if feat is not None })
        sys.stderr.write('.'); sys.stderr.flush()

    sp.commit()

    def show(srcs):
        if srcdb is None: return
        annot = SourceAnnot(srcdb)
        for (srcid, start, end) in srcs:
            annot.add(srcmap[srcid], start, end, 0)
        annot.show_text()
        return

    if args:
        pairs = []
        for tid0 in args:
            for (sim,tid1) in sp.findsim(tid0, threshold=threshold):
                pairs.append((sim, tid0, tid1))
    elif minpairs:
        nbins = int(minpairs*1.1)
        pairs = [ None for _ in range(nbins) ]
        z = (nbins-1) / (1.0-threshold)
        npairs = 0
        for (sim,tid0,tid1) in sp.findall(threshold=threshold):
            i = int((sim-threshold)*z)
            if pairs[i] is None:
                npairs += 1
            pairs[i] = (sim, tid0, tid1)
            if minpairs <= npairs: break
        pairs = [ x for x in pairs if x is not None ]
    else:
        pairs = sp.findall(threshold=threshold)

    if minpairs:
        random.shuffle(pairs)
        for (i,(sim,tid0,tid1)) in enumerate(pairs):
            item0 = db.get_item(tid0)
            item1 = db.get_item(tid1)
            type0 = refs[item0]
            type1 = refs[item1]
            srcs0 = db.get_feats(tid0, source=True)[0]
            srcs1 = db.get_feats(tid0, source=True)[1]
            data = (i, sim,
                    (item0, type0, f(srcs0)),
                    (item1, type1, f(srcs1)))
            print(data)
        return
    npairs = 0
    nametype = 0
    nameonly = 0
    typeonly = 0
    totalwordsim = 0
    for (sim,tid0,tid1) in pairs:
        assert tid0 != tid1
        npairs += 1
        item0 = db.get_item(tid0)
        item1 = db.get_item(tid1)
        type0 = refs[item0]
        type1 = refs[item1]
        name0 = stripid(item0)
        name1 = stripid(item1)
        if type0 == type1 and name0 == name1:
            nametype += 1
        elif type0 == type1:
            typeonly += 1
        elif name0 == name1:
            nameonly += 1
        words0 = set(splitwords(name0))
        words1 = set(splitwords(name1))
        wordsim = jaccard(words0, words1)
        totalwordsim += wordsim
        print('*** sim=%.2f, wordsim=%.2f, type=%r: %s/%s' %
              (sim, wordsim, (type0==type1), name0, name1))
        if name0 != name1:
            srcs0 = db.get_feats(tid0, source=True)[0]
            srcs1 = db.get_feats(tid0, source=True)[1]
            print('+++', item0)
            show(srcs0)
            print('+++', item1)
            show(srcs1)
        print('# score=%r/%r/%r/%r, avg=%.2f' %
              (npairs, nametype, nameonly, typeonly, totalwordsim/npairs))
        print()

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
