#!/usr/bin/env python
import sys
import re
import random
import json
from featdb import FeatDB
from vsm import VSM
from math import exp
from getwords import stripid, splitwords

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
        print(f'usage: {argv[0]} [-d] [-n minpairs] [-r refs] [-i items] [-t threshold] featdb [var ...]')
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'dn:r:i:t:')
    except getopt.GetoptError:
        return usage()
    debug = 0
    minpairs = 0
    refs = None
    items = set()
    threshold = 0.90
    for (k, v) in opts:
        if k == '-d': debug += 1
        elif k == '-n': minpairs = int(v)
        elif k == '-r':
            refs = {}
            with open(v) as fp:
                for line in fp:
                    (ref,_,ntype) = line.strip().partition(' ')
                    refs[ref] = ntype
        elif k == '-i':
            with open(v) as fp:
                for rec in getrecs(fp):
                    items.add(rec['ITEM'])
                    items.update( x[2] for x in rec['SUPPORT'] )
        elif k == '-t': threshold = float(v)
    if not args: return usage()

    dbpath = args.pop(0)
    db = FeatDB(dbpath)

    sp = VSM()
    for (tid,_) in db:
        feats = db.get_feats(tid, resolve=True)
        sp.add(tid, { feat:fc*exp(-abs(feat[0])) for (feat,(fc,_)) in feats.items()
                      if feat is not None })
    sp.commit()
    print(f'Docs: {len(sp)}', file=sys.stderr)

    if args:
        items.update(args)

    if items:
        print(f'Items: {len(items)}', file=sys.stderr)
        tids = [ db.get_tid(item) for item in items ]
        pairs = sp.findall(tids, threshold=threshold, verbose=True)
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
            print('+PID', i)
            print('+SIM', json.dumps(sim))
            item0 = db.get_item(tid0)
            item1 = db.get_item(tid1)
            print('+ITEMS', json.dumps([item0, item1]))
            if refs is not None:
                type0 = refs[item0]
                type1 = refs[item1]
                print('+TYPES', json.dumps([type0, type1]))
            (_,srcs0) = db.get_feats(tid0, source=True)[0]
            (_,srcs1) = db.get_feats(tid1, source=True)[0]
            print('+SRCS', json.dumps([srcs0, srcs1]))
            print()
        return

    npairs = 0
    nametype = 0
    nameonly = 0
    typeonly = 0
    totalwordsim = 0
    for (i,(sim,tid0,tid1)) in enumerate(pairs):
        print('+PID', i)
        print('+SIM', json.dumps(sim))
        assert tid0 != tid1
        npairs += 1
        item0 = db.get_item(tid0)
        item1 = db.get_item(tid1)
        print('+ITEMS', json.dumps([item0, item1]))
        type0 = type1 = None
        if refs is not None:
            type0 = refs[item0]
            type1 = refs[item1]
            print('+TYPES', json.dumps([type0, type1]))
        name0 = stripid(item0)
        name1 = stripid(item1)
        if type0 is not None and type0 == type1 and name0 == name1:
            nametype += 1
        elif type0 is not None and type0 == type1:
            typeonly += 1
        elif name0 == name1:
            nameonly += 1
        words0 = set(splitwords(name0))
        words1 = set(splitwords(name1))
        wordsim = jaccard(words0, words1)
        print('+WORDSIM', json.dumps(wordsim))
        totalwordsim += wordsim
        (_,srcs0) = db.get_feats(tid0, source=True)[0]
        (_,srcs1) = db.get_feats(tid1, source=True)[0]
        print('+SRCS', json.dumps([srcs0, srcs1]))
        print()

    print(f'\nNPairs={npairs}, NameType={nametype}, NameOnly={nameonly}, TypeOnly={typeonly}, AvgWordSim={totalwordsim/npairs:.2f}',
          file=sys.stderr)

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
