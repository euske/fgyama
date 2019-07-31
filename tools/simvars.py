#!/usr/bin/env python
import sys
import re
from srcdb import SourceDB, SourceAnnot
from vsm import VSM

# main
def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-d] [-B srcdb] [-t threshold] [feats ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'dB:t:')
    except getopt.GetoptError:
        return usage()
    debug = 0
    encoding = None
    srcdb = None
    threshold = 0.90
    for (k, v) in opts:
        if k == '-d': debug += 1
        elif k == '-B': srcdb = SourceDB(v, encoding)
        elif k == '-t': threshold = float(v)
    if not args: return usage()

    sp = VSM()
    path = args.pop(0)
    print('Loading: %r...' % path, file=sys.stderr)

    srcmap = {}
    items = []
    with open(path) as fp:
        item = feats = None
        annot = None
        for line in fp:
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

    for (sim,item0,item1) in sp.findall(threshold=threshold):
        print(sim, item0, item1)

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
