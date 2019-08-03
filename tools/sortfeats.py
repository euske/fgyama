#!/usr/bin/env python
import sys

# main
def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-v] [-t type] [graph ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'vt:')
    except getopt.GetoptError:
        return usage()
    verbose = 0
    target = None
    for (k, v) in opts:
        if k == '-v': verbose += 1
        elif k == '-t': target = v
    if not args: return usage()

    srcid2path = {}
    fid2feat = {}
    feat2fid = {}
    item2srcs = {}
    item2feats = {}
    feat2srcs = None
    for line in fileinput.input(args):
        if line.startswith('# gid:'):
            sys.stderr.write('.'); sys.stderr.flush()

        elif line.startswith('+SOURCE '):
            (_,_,line) = line.partition(' ')
            (srcid, path) = eval(line)
            srcid2path[srcid] = path
            print('+SOURCE', (srcid, path))

        elif line.startswith('! '):
            data = eval(line[2:])
            assert isinstance(data, tuple)
            item = data[0:2]
            src = data[2]
            if target is not None and data[0] != target:
                feat2srcs = None
            elif item in item2feats:
                feat2srcs = item2feats[item]
            else:
                feat2srcs = item2feats[item] = {}
            if item in item2srcs:
                srcs = item2srcs[item]
            else:
                srcs = item2srcs[item] = []
            if src is not None:
                srcs.append(src)

        elif line.startswith('+ '):
            if feat2srcs is not None:
                data = eval(line[2:])
                assert isinstance(data, tuple)
                feat = data[0:4]
                if feat in feat2fid:
                    fid = feat2fid[feat]
                else:
                    fid = len(feat2fid)+1
                    feat2fid[feat] = fid
                    fid2feat[fid] = feat
                src = data[4]
                if src is not None:
                    if fid in feat2srcs:
                        srcs = feat2srcs[fid]
                    else:
                        srcs = feat2srcs[fid] = []
                    if src not in srcs:
                        srcs.append(src)
        else:
            pass
    #
    for item in sorted(item2feats.keys()):
        print('!', item + tuple(item2srcs[item]))
        feat2srcs = item2feats[item]
        for fid in sorted(feat2srcs.keys(), key=lambda fid:fid2feat[fid]):
            feat = fid2feat[fid]
            srcs = feat2srcs[fid]
            print('+', feat + tuple(srcs))
        print()
    total = sum( len(d) for d in item2feats.values() )
    print('%d items, %d keys' % (len(item2feats), total), file=sys.stderr)
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
