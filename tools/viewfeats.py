#!/usr/bin/env python
import sys
import re
from srcdb import SourceDB, SourceAnnot
from getwords import stripid, splitwords

# main
def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-d] -B srcdb path [feats ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'dc:B:n:')
    except getopt.GetoptError:
        return usage()
    debug = 0
    encoding = None
    srcdb = None
    ntop = 10
    for (k, v) in opts:
        if k == '-d': debug += 1
        elif k == '-c': encoding = v
        elif k == '-B': srcdb = SourceDB(v, encoding)
        elif k == '-n': ntop = int(v)
    if not args: return usage()
    if srcdb is None: return usage()

    path = args.pop(0)
    print('Loading: %r...' % path, file=sys.stderr)

    srcid2path = {}
    word2freq = {}
    item2srcs = { w:[] for w in args if not w.startswith('+') }
    word2items = { w[1:]:{} for w in args if w.startswith('+') }
    feat2srcs = None
    with open(path) as fp:
        for line in fp:
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
                    if name in item2srcs:
                        item2srcs[name].extend(data[2:])
                    for w in splitwords(name):
                        if w not in word2freq:
                            word2freq[w] = 0
                        word2freq[w] += 1
                        if w in word2items:
                            item2feats = word2items[w]
                            if item in item2feats:
                                feat2srcs = item2feats[item]
                            else:
                                feat2srcs = item2feats[item] = {}
                            if None in feat2srcs:
                                srcs = feat2srcs[None]
                            else:
                                srcs = feat2srcs[None] = []
                            srcs.extend(data[2:])
                            break

            elif feat2srcs is not None and line.startswith('+ '):
                assert item is not None
                data = eval(line[2:])
                feat = data[0:4]
                if feat in feat2srcs:
                    srcs = feat2srcs[feat]
                else:
                    srcs = feat2srcs[feat] = []
                srcs.extend(data[4:])

            else:
                feat2srcs = None

    words = sorted(word2freq.keys(), key=lambda w: word2freq[w], reverse=True)
    for w in words[:10]:
        print('#', w, word2freq[w])

    for (item,srcs) in item2srcs.items():
        print('!!!', item)
        annot = SourceAnnot(srcdb)
        for src in srcs:
            if src is None: continue
            (srcid,start,end) = src
            annot.add(srcid2path[srcid], start, end)
        annot.show_text()

    for (word,item2feats) in word2items.items():
        print('!!!', '+'+word, len(item2feats))
        feat2items = {}
        for (item,feat2srcs) in item2feats.items():
            for (feat,srcs) in feat2srcs.items():
                if feat in feat2items:
                    item2srcs = feat2items[feat]
                else:
                    item2srcs = feat2items[feat] = {}
                if item in item2srcs:
                    item2srcs[item].extend(srcs)
                else:
                    item2srcs[item] = srcs
        # pick the item that has most common features first.
        items = []
        for (item,feat2srcs) in item2feats.items():
            score = sum( len(feat2items[feat]) for feat in feat2srcs.keys() )
            items.append((score, item))
        items.sort(reverse=True)
        for (score,item) in items:
            feat2srcs = item2feats[item]
            feats = sorted(
                feat2srcs.keys(), key=lambda feat: len(feat2items[feat]),
                reverse=True)[:ntop]
            print('!', item, score)
            srcs = set()
            for feat in feats:
                item2srcs = feat2items[feat]
                if item not in item2srcs: continue
                print('+', feat, len(item2srcs))
                srcs.update(item2srcs[item])
            annot = SourceAnnot(srcdb)
            for src in srcs:
                if src is None: continue
                (srcid,start,end) = src
                annot.add(srcid2path[srcid], start, end)
            annot.show_text()

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
