#!/usr/bin/env python
import sys
import os.path
import marshal

def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-f] outpath [feats ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'f')
    except getopt.GetoptError:
        return usage()
    force = False
    for (k, v) in opts:
        if k == '-f': force = True
    if not args: return usage()

    outpath = args.pop(0)
    if not force and os.path.exists(outpath):
        print('already exists: %r' % outpath)
        return 1

    srcmap = {}
    featmap = {}
    item2fid = {}

    for line in fileinput.input(args):
        line = line.strip()
        if line.startswith('+SOURCE'):
            (_,_,line) = line.partition(' ')
            (srcid, path) = eval(line)
            srcmap[srcid] = path

        elif line.startswith('! '):
            data = eval(line[2:])
            item = fid2srcs = None
            if data[0] == 'REF':
                item = data[1]
                fid2srcs = {0: data[2:]}
                assert item not in item2fid
                item2fid[item] = fid2srcs

        elif item is not None and line.startswith('+ '):
            assert fid2srcs is not None
            data = eval(line[2:])
            feat = data[0:3]
            if feat in featmap:
                fid = featmap[feat]
            else:
                fid = len(featmap)+1
                featmap[feat] = fid
            if fid in fid2srcs:
                srcs = fid2srcs[fid]
            else:
                srcs = fid2srcs[fid] = []
            srcs.extend(data[3:])

        elif not line:
            item = fid2srcs = None

    print('%r: srcmap=%d, featmap=%d, item2fid=%d' %
          (outpath, len(srcmap), len(featmap), len(item2fid)))

    data = (srcmap, featmap, item2fid)
    with open(outpath, 'wb') as fp:
        marshal.dump(data, fp)
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
