#!/usr/bin/env python
##
##  flatten .zip files
##

import sys
import re
import os.path
import zipfile

BUFSIZ = 2**16

def main(argv):
    import getopt
    def usage():
        print('usage: %s [-p pat] [zip ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'p:')
    except getopt.GetoptError:
        return usage()
    debug = 0
    pat = None
    for (k, v) in opts:
        if k == '-p': pat = re.compile(v)
    if not args: return usage()
    for zippath in args:
        zfp = zipfile.ZipFile(zippath)
        outdir = None
        for info in zfp.infolist():
            if info.is_dir(): continue
            src = info.filename
            if '/.' in src: continue
            if pat is not None and not pat.search(src): continue
            (dir1,_,dst) = src.partition('/')
            if outdir != dir1:
                outdir = dir1
                try:
                    os.makedirs(outdir)
                except OSError:
                    pass
            dst = os.path.join(outdir, dst.replace('/','_'))
            print('extract: %r -> %r' % (src, dst), file=sys.stderr)
            fp = zfp.open(src, 'r')
            out = open(dst, 'wb')
            while 1:
                data = fp.read(BUFSIZ)
                if not data: break
                out.write(data)
            out.close()
            fp.close()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
