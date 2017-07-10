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
        print('usage: %s [-p pat] [-o outdir] [-b basename] [zip ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'p:o:b:')
    except getopt.GetoptError:
        return usage()
    debug = 0
    outdir = '.'
    basename = None
    pat = None
    for (k, v) in opts:
        if k == '-p': pat = re.compile(v)
        elif k == '-o': outdir = v
        elif k == '-v': basename = v
    if not args: return usage()
    try:
        os.makedirs(outdir)
    except OSError:
        pass
    for zippath in args:
        zfp = zipfile.ZipFile(zippath)
        zname = os.path.basename(zippath)
        (zname,_) = os.path.splitext(zname)
        for info in zfp.infolist():
            if not info.is_dir():
                src = info.filename
                if '/.' in src: continue
                if pat is not None and not pat.search(src): continue
                if basename is None:
                    dst = zname+'_'+src.replace('/','_')
                else:
                    dst = basename+'_'+src.replace('/','_')
                print('extract: %r -> %r' % (src, dst), file=sys.stderr)
                fp = zfp.open(src, 'r')
                path = os.path.join(outdir, dst)
                out = open(path, 'wb')
                while 1:
                    data = fp.read(BUFSIZ)
                    if not data: break
                    out.write(data)
                out.close()
                fp.close()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
