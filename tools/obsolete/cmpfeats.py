#!/usr/bin/env python
import sys
import os
from featdb import FeatDB

# main
def main(argv):
    import fileinput
    import getopt
    def usage():
        print(f'usage: {argv[0]} [-d] feats0.db feats0_a.db ... - feats1.db feats1_a.db ...')
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'd')
    except getopt.GetoptError:
        return usage()
    debug = 0
    for (k, v) in opts:
        if k == '-d': debug += 1
    if not args: return usage()

    db0 = items0 = None
    for dbpath in args:
        if dbpath == '-':
            db0 = items0 = None
            continue
        db1 = FeatDB(dbpath)
        items1 = sorted(db1.get_items())
        if db0 is None:
            db0 = db1
            items0 = items1
            print(f'src: {dbpath}, {len(items0)} items')
            continue
        #assert items0 == items1, (len(items0), len(items1))
        items1 = dict(items1)
        nfeats0 = nfeats1 = ncommon = 0
        for (tid,item) in items0:
            if tid not in items1: continue
            feats0 = set(db0.get_feats(tid, resolve=True))
            feats1 = set(db1.get_feats(tid, resolve=True))
            nfeats0 += len(feats0)
            nfeats1 += len(feats1)
            ncommon += len(feats0.intersection(feats1))
        print(f'  {dbpath}: {ncommon} / ({nfeats1}, {nfeats0})')

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
