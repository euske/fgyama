#!/usr/bin/env python
import sys
import re
import math
from srcdb import SourceDB, SourceAnnot
from featdb import FeatDB
from getwords import stripid, splitwords

# main
def main(argv):
    import fileinput
    import getopt
    def usage():
        print(f'usage: {argv[0]} [-d] [-n feats] srcdb featdb [word ...]')
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'dc:n:')
    except getopt.GetoptError:
        return usage()
    debug = 0
    encoding = None
    ntop = 5
    for (k, v) in opts:
        if k == '-d': debug += 1
        elif k == '-c': encoding = v
        elif k == '-n': ntop = int(v)
    if not args: return usage()
    basepath = args.pop(0)
    if not args: return usage()
    dbpath = args.pop(0)

    srcdb = SourceDB(basepath, encoding)
    db = FeatDB(dbpath)

    word2fids = { w:{} for w in args }

    for (tid,item) in db:
        name = stripid(item)
        words = splitwords(name)
        fids = db.get_feats(tid)
        for w in words:
            if w not in word2fids: continue
            fid2items = word2fids[w]
            for fid in fids:
                if fid in fid2items:
                    items = fid2items[fid]
                else:
                    items = fid2items[fid] = []
                assert tid not in items
                items.append(tid)
            assert fid in fid2items
        #sys.stderr.write('.'); sys.stderr.flush()

    nallitems = len(db.get_items())
    for (word,fid2items) in word2fids.items():
        if not fid2items: continue
        fscore = []
        iscore = {}
        nitems = len(fid2items[0])
        for (fid,items) in fid2items.items():
            if fid == 0: continue
            df = math.log(nallitems / db.get_numfeatitems(fid))
            feat = db.get_feat(fid)
            score = math.exp(-abs(feat[0])) * df * len(items)
            fscore.append((score, fid, items))
            for item in items:
                if item not in iscore:
                    iscore[item] = 0
                iscore[item] += score
        print(f'*** word: {word!r}, items: {nitems}, feats: {len(fscore)}\n')
        fscore.sort(reverse=True)
        for (score,fid,items) in fscore[:ntop]:
            feat = db.get_feat(fid)
            print('+FEAT', feat, len(items), score)
            items.sort(key=lambda item:iscore[item], reverse=True)
            for item in items[:1]:
                print('+ITEM', db.get_item(item))
                feats = db.get_feats(item, resolve=True, source=True)
                (fc,srcs) = feats[feat]
                if not srcs: continue
                #srcs.extend(feats[None])
                annot = SourceAnnot(srcdb)
                for src in srcs:
                    (path,start,end) = src
                    annot.add(path, start, end)
                annot.show_text()

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
