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

    item2srcs = {}
    item2feats = {}
    feats = None
    for line in fileinput.input(args):
        if line.startswith('# gid:'):
            sys.stderr.write('.'); sys.stderr.flush()

        elif line.startswith('+SOURCE '):
            print(line.strip())

        elif line.startswith('! '):
            data = eval(line[2:])
            assert isinstance(data, tuple)
            item = data[0:2]
            src = data[2]
            if target is not None and data[0] != target:
                feats = None
            elif item in item2feats:
                feats = item2feats[item]
            else:
                feats = item2feats[item] = {}
            if item in item2srcs:
                srcs = item2srcs[item]
            else:
                srcs = item2srcs[item] = []
            if src is not None:
                srcs.append(src)

        elif line.startswith('+ '):
            if feats is not None:
                data = eval(line[2:])
                assert isinstance(data, tuple)
                feat = data[0:4]
                src = data[4]
                if src is not None:
                    if feat in feats:
                        a = feats[feat]
                    else:
                        a = feats[feat] = []
                    if src not in a:
                        a.append(src)
        else:
            pass
    #
    for item in sorted(item2feats.keys()):
        print('!', item + tuple(item2srcs[item]))
        feats = item2feats[item]
        for feat in sorted(feats.keys()):
            print('+', feat + tuple(feats[feat]))
        print()
    total = sum( len(d) for d in item2feats.values() )
    print('%d items, %d keys' % (len(item2feats), total), file=sys.stderr)
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
