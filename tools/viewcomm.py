#!/usr/bin/env python
import sys
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
    for (k, v) in opts:
        if k == '-B': srcdb = SourceDB(v)
    if not args: return usage()

    # "+ path.java"
    # "- 2886 2919 type=LineComment parent=Block ..."
    src = None
    for line in fileinput.input(args):
        line = line.strip()
        if line.startswith('+'):
            (_,_,name) = line.strip().partition(' ')
            src = srcdb.get(name)
            print('+ %s' % name)
        elif line.startswith('-'):
            assert src is not None
            f = line.split(' ')
            (start, end) = map(int, f[1:3])
            print('- %s %s key=XXX' % (start, end))
            ranges = [(start,end,1)]
            for (_,line) in src.show(ranges, ncontext=2):
                print('   '+line, end='')
            print()
        elif not line:
            print()
        else:
            raise ValueError(line)
    
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
