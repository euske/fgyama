#!/usr/bin/env python
import sys
import re
from srcdb import SourceDB, SourceAnnot
from getwords import stripid, splitwords
from vsm import VSM

def jaccard(s1, s2):
    if not s1 or not s2: return 0
    return len(s1.intersection(s2)) / len(s1.union(s2))

# main
def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-d] [-B srcdb] [-t threshold] vars [feats ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'dB:t:')
    except getopt.GetoptError:
        return usage()
    debug = 0
    encoding = None
    srcdb = None
    threshold = 0.50
    for (k, v) in opts:
        if k == '-d': debug += 1
        elif k == '-B': srcdb = SourceDB(v, encoding)
        elif k == '-t': threshold = float(v)
    if not args: return usage()

    path = args.pop(0)
    refs = {}
    with open(path) as fp:
        for line in fp:
            (ref,_,ntype) = line.strip().partition(' ')
            refs[ref] = ntype
    print('Refs: %r' % len(refs), file=sys.stderr)

    srcmap = {}
    item2srcs = {}
    items = []
    item = feats = None
    sp = VSM()
    for line in fileinput.input(args):
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
                if item in item2srcs:
                    srcs = item2srcs[item]
                else:
                    srcs = item2srcs[item] = []
                srcs.extend(( x for x in data[2:] if x is not None ))
            else:
                feats = None

        elif feats is not None and line.startswith('+ '):
            data = eval(line[2:])
            feat = data[0:4]
            feats.add(feat)

        elif feats is not None and not line.strip():
            if feats:
                sp.add(item, { k:1 for k in feats })

    sp.commit()

    def show(srcs):
        annot = SourceAnnot(srcdb)
        for (srcid, start, end) in srcs:
            annot.add(srcmap[srcid], start, end, 0)
        annot.show_text()
        return

    nbins = 100
    bins = [ None for _ in range(nbins) ]
    filled = 0
    total = 0
    count = 0
    for (sim,item0,item1) in sp.findall(threshold=threshold):
        i = int((sim-threshold)*(nbins-1))
        if bins[i] is None:
            filled += 1
        else:
            total -= bins[i][0]
        bins[i] = (sim, item0, item1)
        total += sim
        count += 1
        if nbins < count and threshold < total/filled: break

    bins = [ b for b in bins if b is not None ]
    bins.sort(reverse=True)
    npairs = len(bins)
    nametype = 0
    nameonly = 0
    typeonly = 0
    totalwordsim = 0
    for (sim,item0,item1) in bins:
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
        print(sim, (type0==type1), name0, name1, wordsim)
        if srcdb is not None:
            print(sim, item0, item1)
            print('+++')
            show(item2srcs[item0])
            print('+++')
            show(item2srcs[item1])
    print('npairs=%r, score=%r/%r/%r, avg=%r' %
          (npairs, nametype, nameonly, typeonly, totalwordsim/npairs))

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
