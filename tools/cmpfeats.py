#!/usr/bin/env python
import sys
from featdb import FeatDB
from getwords import stripid, splitwords
from vsm import VSM

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

    ws = []
    for (i,path) in enumerate(args):
        print('Loading: %d: %r...' % (i,path), file=sys.stderr)
        db = FeatDB(path)
        wordfeats = {}
        for (tid,item) in db.get_items():
            feats = db.get_feats(tid, resolve=True)
            name = stripid(item)
            words = splitwords(name)
            for w in words:
                if w in wordfeats:
                    fs = wordfeats[w]
                else:
                    fs = wordfeats[w] = {}
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

    for (sim,(i0,k0),(i1,k1)) in sp.findall(threshold=threshold, verbose=True):
        if i0 == i1: continue
        print(sim, (i0,k0), (i1,k1))
    
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
