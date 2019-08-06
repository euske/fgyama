#!/usr/bin/env python
import sys
import re
from srcdb import SourceDB, SourceAnnot
from getwords import stripid, splitwords
from vsm import VSM
from math import exp

def jaccard(s1, s2):
    if not s1 or not s2: return 0
    return len(s1.intersection(s2)) / len(s1.union(s2))

# main
def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-d] [-B srcdb] [-n nbins] [-t threshold] vars feats [var ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'dB:n:t:')
    except getopt.GetoptError:
        return usage()
    debug = 0
    encoding = None
    srcdb = None
    nbins = 0
    threshold = 0.30
    minratio = 0.90
    for (k, v) in opts:
        if k == '-d': debug += 1
        elif k == '-B': srcdb = SourceDB(v, encoding)
        elif k == '-n': nbins = int(v)
        elif k == '-t': threshold = float(v)
    if not args: return usage()

    path = args.pop(0)
    refs = {}
    with open(path) as fp:
        for line in fp:
            (ref,_,ntype) = line.strip().partition(' ')
            refs[ref] = ntype
    print('Refs: %r' % len(refs), file=sys.stderr)

    item2srcs = None
    if srcdb is not None:
        item2srcs = {}
    srcmap = {}
    items = []
    sp = VSM()
    path = args.pop(0)
    with open(path) as fp:
        item = feats = None
        for line in fp:
            line = line.strip()
            if line.startswith('+SOURCE'):
                (_,_,line) = line.partition(' ')
                (srcid, path) = eval(line)
                srcmap[srcid] = path

            elif line.startswith('! '):
                sys.stderr.write('.'); sys.stderr.flush()
                data = eval(line[2:])
                if data[0] == 'REF':
                    item = data[1]
                    feats = set()
                    items.append(item)
                    if item2srcs is not None:
                        if item in item2srcs:
                            srcs = item2srcs[item]
                        else:
                            srcs = item2srcs[item] = []
                        srcs.extend(data[2:])
                else:
                    feats = None

            elif feats is not None and line.startswith('+ '):
                data = eval(line[2:])
                feat = data[0:3]
                feats.add(feat)

            elif feats is not None and not line:
                if feats:
                    sp.add(item, { (d,f0,f1):exp(-abs(d)) for (d,f0,f1) in feats })
    sp.commit()

    def show(srcs):
        annot = SourceAnnot(srcdb)
        for (srcid, start, end) in srcs:
            annot.add(srcmap[srcid], start, end, 0)
        annot.show_text()
        return

    if args:
        pairs = []
        for item0 in args:
            for (sim,item1) in sp.findsim(item0, threshold=threshold):
                pairs.append((sim, item0, item1))
    elif nbins:
        pairs = [ None for _ in range(nbins) ]
        z = (nbins-1) / (1.0-threshold)
        minpairs = int(nbins * minratio)
        npairs = 0
        for (sim,item0,item1) in sp.findall(threshold=threshold):
            i = int((sim-threshold)*z)
            if pairs[i] is None:
                npairs += 1
            pairs[i] = (sim, item0, item1)
            if minpairs < npairs: break
        pairs = [ x for x in pairs if x is not None ]
        pairs.sort(key=lambda b:b[0], reverse=True)
    else:
        pairs = sp.findall(threshold=threshold)

    npairs = 0
    nametype = 0
    nameonly = 0
    typeonly = 0
    totalwordsim = 0
    for (sim,item0,item1) in pairs:
        assert item0 != item1
        npairs += 1
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
        print('*** sim=%.2f, wordsim=%.2f, type=%r: %s/%s' %
              (sim, wordsim, (type0==type1), name0, name1))
        if item2srcs is not None and name0 != name1:
            print('+++', item0)
            show(item2srcs[item0])
            print('+++', item1)
            show(item2srcs[item1])
        print('# score=%r/%r/%r/%r, avg=%.2f' %
              (npairs, nametype, nameonly, typeonly, totalwordsim/npairs))
        print()

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
