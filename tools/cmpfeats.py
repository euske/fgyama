#!/usr/bin/env python
import sys
from naivebayes import NaiveBayes
from featdb import FeatDB
from vsm import VSM

def main(argv):
    import getopt
    def usage():
        print('usage: %s [-t threshold] param1 featdb1 param2 featdb2' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 't:')
    except getopt.GetoptError:
        return usage()

    threshold = 0.1
    for (k, v) in opts:
        if k == '-t': threshold = float(v)

    if not args: return usage()
    path = args.pop(0)
    nb1 = NaiveBayes()
    with open(path, 'rb') as fp:
        nb1.load(fp)
    if not args: return usage()
    path = args.pop(0)
    db1 = FeatDB(path)
    
    if not args: return usage()
    path = args.pop(0)
    nb2 = NaiveBayes()
    with open(path, 'rb') as fp:
        nb2.load(fp)
    if not args: return usage()
    path = args.pop(0)
    db2 = FeatDB(path)

    sp = VSM()
    keys = [[], []]
    for (i,(nb,db)) in enumerate([(nb1,db1), (nb2,db2)]):
        k2f = {}
        for (fid,d) in nb.fcount.items():
            feat = db.get_feat(fid)
            for (k,v) in d.items():
                if k is None: continue
                if k in k2f:
                    z = k2f[k]
                else:
                    z = k2f[k] = {}
                z[fid] = v
        for (k,z) in k2f.items():
            sp.add((i,k), z)
            keys[i].append((i,k))
        print('listing', i, len(k2f))
    sp.commit()

    for k0 in keys[0]:
        for k1 in keys[1]:
            sim = sp.calcsim(sp.docs[k0], sp.docs[k1])
            if sim < threshold: continue
            print(sim, k0, k1)
    
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
