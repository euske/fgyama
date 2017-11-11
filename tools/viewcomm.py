#!/usr/bin/env python
import sys
import random
from srcdb import SourceDB

def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-B basedir] '
              'comm.out' %
              argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'B:')
    except getopt.GetoptError:
        return usage()
    srcdb = None
    ncomments = 2
    for (k, v) in opts:
        if k == '-B': srcdb = SourceDB(v)
    if not args: return usage()

    # "+ path.java"
    # "- 2886 2919 type=LineComment parent=Block ..."
    src = None
    ranges = []
    random.seed(0)
    for line in fileinput.input(args):
        line = line.strip()
        if line.startswith('+'):
            (_,_,name) = line.strip().partition(' ')
            src = srcdb.get(name)
            print('+ %s' % name)
            ranges = []
        elif line.startswith('-'):
            assert src is not None
            f = line.split(' ')
            (start, end) = map(int, f[1:3])
            ranges.append((start,end,1))
        elif not line:
            if ranges:
                random.shuffle(ranges)
                for (start,end,tag) in ranges[:ncomments]:
                    print('- %s %s key=XXX' % (start, end))
                    for (_,line) in src.show([(start,end,tag)], ncontext=3):
                        print('   '+line, end='')
                print()
                ranges = None
        else:
            raise ValueError(line)
    
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
