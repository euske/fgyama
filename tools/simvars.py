#!/usr/bin/env python
import sys
import re
import random
import json
from featdb import FeatDB
from getwords import stripid, splitwords
from vsm import VSM
from math import exp

def jaccard(s1, s2):
    if not s1 or not s2: return 0
    return len(s1.intersection(s2)) / len(s1.union(s2))

def getrecs(fp):
    rec = {}
    for line in fp:
        line = line.strip()
        if line.startswith('+'):
            (k,_,v) = line[1:].partition(' ')
            rec[k] = json.loads(v)
        elif not line:
            yield rec
            rec = {}
    return

# main
def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-d] [-n minpairs] [-i items] [-t threshold] vars feats [var ...]' %
              argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'dn:i:t:')
    except getopt.GetoptError:
        return usage()
    debug = 0
    encoding = None
    minpairs = 0
    items = []
    threshold = 0.90
    for (k, v) in opts:
        if k == '-d': debug += 1
        elif k == '-n': minpairs = int(v)
        elif k == '-i':
            with open(v) as fp:
                items.extend( rec['ITEM'] for rec in getrecs(fp) )
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
    sp.commit()
    print('Docs: %r' % len(sp), file=sys.stderr)

    if args:
        items.extend(args)

    if items:
        print('Items: %r' % len(items), file=sys.stderr)
        pairs = []
        for item0 in items:
            tid0 = db.get_tid(item0)
            for (sim,tid1) in sp.findsim(tid0, threshold=threshold):
                pairs.append((sim, tid0, tid1))
            sys.stderr.write('.'); sys.stderr.flush()
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
        pairs = sp.findall(threshold=threshold, verbose=True)

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
        print('+SIM', json.dumps(sim))
        print('+WORDSIM', json.dumps(wordsim))
        print('+TYPE', json.dumps(type0 == type1))
        print('+ITEMS', json.dumps([item0, item1]))
        srcs0 = db.get_feats(tid0, source=True)[0]
        srcs1 = db.get_feats(tid1, source=True)[0]
        print('+SRCS', json.dumps([srcs0, srcs1]))
        print()

    print('NPairs=%r, NameType=%r, NameOnly=%r, TypeOnly=%r, AvgWordSim=%.2f' %
          (npairs, nametype, nameonly, typeonly, totalwordsim/npairs),
          file=sys.stderr)

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
