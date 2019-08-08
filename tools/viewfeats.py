#!/usr/bin/env python
import sys
import re
import math
from srcdb import SourceDB, SourceAnnot
from getwords import stripid, splitwords

def featfunc(feat):
    return math.exp(-abs(feat[0]))

# main
def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-d] srcdb params [feats ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'dc:n:')
    except getopt.GetoptError:
        return usage()
    debug = 0
    encoding = None
    ntop = 10
    for (k, v) in opts:
        if k == '-d': debug += 1
        elif k == '-c': encoding = v
        elif k == '-n': ntop = int(v)
    if not args: return usage()
    path = args.pop(0)
    srcdb = SourceDB(path, encoding)
    path = args.pop(0)

    srcid2path = {}
    name2srcs = { w:[] for w in args if not w.startswith('+') }
    word2feats = { w[1:]:{} for w in args if w.startswith('+') }
    key2srcs = {}
    words = None
    with open(path) as fp:
        for line in fp:
            line = line.strip()
            if line.startswith('+SOURCE'):
                (_,_,line) = line.partition(' ')
                (srcid, path) = eval(line)
                srcid2path[srcid] = path

            elif line.startswith('! '):
                data = eval(line[2:])
                item = feat2srcs = None
                if data[0] == 'REF':
                    item = data[1]
                    name = stripid(item)
                    if name in name2srcs:
                        name2srcs[name].extend(data[2:])
                    words = splitwords(name)
                    if item in key2srcs:
                        srcs = key2srcs[item]
                    else:
                        srcs = key2srcs[item] = []
                    srcs.extend(data[2:])

            elif words is not None and line.startswith('+ '):
                assert item is not None
                data = eval(line[2:])
                feat = data[0:3]
                for w in words:
                    if w not in word2feats: continue
                    feat2items = word2feats[w]
                    if feat in feat2items:
                        items = feat2items[feat]
                    else:
                        items = feat2items[feat] = set()
                    items.add(item)
                    k = (item,feat)
                    if k in key2srcs:
                        srcs = key2srcs[k]
                    else:
                        srcs = key2srcs[k] = []
                    srcs.extend(data[3:])

            else:
                words = None

    for (name,srcs) in name2srcs.items():
        print('!!!', name)
        annot = SourceAnnot(srcdb)
        for src in srcs:
            if src is None: continue
            (srcid,start,end) = src
            annot.add(srcid2path[srcid], start, end)
        annot.show_text()

    for (word,feat2items) in word2feats.items():
        print('!!!', '+'+word, len(feat2items))
        item2feats = {}
        for (feat,items) in feat2items.items():
            for item in items:
                if item in item2feats:
                    feat2srcs = item2feats[item]
                else:
                    feat2srcs = item2feats[item] = {}
                if feat in feat2srcs:
                    srcs = feat2srcs[feat]
                else:
                    srcs = feat2srcs[feat] = set()
                k = (item,feat)
                srcs.update(key2srcs[k])
        # pick the item that has most desired features.
        (maxitem,maxscore) = (None,None)
        for (item,feats) in item2feats.items():
            score = sum( featfunc(feat) for feat in feats.keys() )
            if maxscore is None or maxscore < score:
                (maxitem,maxscore) = (item,score)
        print('!', maxitem)
        maxfeats = item2feats[maxitem]
        allsrcs = set()
        feats = sorted(maxfeats.keys(), key=featfunc, reverse=True)
        for feat in feats[:10]:
            print('+', feat)
            allsrcs.update(maxfeats[feat])
        allsrcs.update(key2srcs[maxitem])
        annot = SourceAnnot(srcdb)
        for src in allsrcs:
            if src is None: continue
            (srcid,start,end) = src
            annot.add(srcid2path[srcid], start, end)
        annot.show_text()

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
