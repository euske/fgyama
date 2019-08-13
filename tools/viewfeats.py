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
        print('usage: %s [-d] [-n feats] srcdb feats.db [word ...]' % argv[0])
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

    word2feats = { w:{} for w in args }
    
    for (tid,item) in db:
        name = stripid(item)
        words = splitwords(name)
        feats = db.get_feats(tid)
        for w in words:
            if w not in word2feats: continue
            feat2items = word2feats[w]
            for feat in feats:
                if feat in feat2items:
                    items = feat2items[feat]
                else:
                    items = feat2items[feat] = []
                assert tid not in items
                items.append(tid)
        #sys.stderr.write('.'); sys.stderr.flush()

    for (word,feat2items) in word2feats.items():
        fscore = []
        iscore = {}
        for (fid,items) in feat2items.items():
            if fid == 0: continue
            (d,_,_) = db.get_feat(fid)
            score = math.exp(-abs(d)) * len(items)
            fscore.append((score, fid, items))
            for item in items:
                if item not in iscore:
                    iscore[item] = 0
                iscore[item] += score
        print('word: %r, items: %r, feats: %r' % (word, len(iscore), len(fscore)))
        fscore.sort(reverse=True)
        for (score,fid,items) in fscore[:ntop]:
            feat = db.get_feat(fid)
            print('+FEAT', feat, len(items), score)
            items.sort(key=lambda item:iscore[item], reverse=True)
            for item in items[:1]:
                print('+ITEM', db.get_item(item))
                feats = db.get_feats(item, resolve=True, source=True)
                srcs = feats[feat]
                if not srcs: continue
                #srcs.extend(feats[None])
                annot = SourceAnnot(srcdb)
                for src in srcs:
                    (path,start,end) = src
                    annot.add(path, start, end)
                annot.show_text()

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
