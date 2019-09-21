#!/usr/bin/env python
import sys
from featdb import FeatDB
from getwords import stripid, splitwords
from vsm import VSM

def get(d, k, v):
    if k in d:
        a = d[k]
    else:
        a = d[k] = v
    return a

def main(argv):
    import getopt
    def usage():
        print('usage: %s [-t threshold] featdb1 featdb2 ...' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 't:')
    except getopt.GetoptError:
        return usage()

    threshold = 0.5
    for (k, v) in opts:
        if k == '-t': threshold = float(v)

    relword = {}
    ws = []
    for (i,path) in enumerate(args):
        print('Loading: %d: %r...' % (i,path), file=sys.stderr)
        db = FeatDB(path)
        wordfeats = {}
        for (tid,item) in db.get_items():
            feats = db.get_feats(tid, resolve=True)
            name = stripid(item)
            words = splitwords(name)
            for (i,w0) in enumerate(words):
                for w1 in words[i+1:]:
                    get(relword, w0, []).append(w1)
                    get(relword, w1, []).append(w0)
            for w in words:
                fs = get(wordfeats, w, {})
                for f in feats.keys():
                    if f not in fs:
                        fs[f] = 0
                    fs[f] += 1
        ws.append(wordfeats)

    sp = VSM()
    for (i,wordfeats) in enumerate(ws):
        for (w,fs) in wordfeats.items():
            sp.add((i,w), fs)
    sp.commit()

    for (sim,(i0,w0),(i1,w1)) in sp.findall(threshold=threshold, verbose=True):
        if w0 == w1: continue
        if w1 in relword and w0 in relword[w1]: continue
        if w0 in relword and w1 in relword[w0]: continue
        print(sim, (i0,w0), (i1,w1))

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
