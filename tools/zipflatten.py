#!/usr/bin/env python
##
##  flatten .zip files
##
##  usage:
##    $ zipflatten.py -b src -p '\.java$' -R repos.out -M srcmap.db zip/*.zip
##

import sys
import re
import os.path
import zipfile
import hashlib
from srcdb import SourceMap

BUFSIZ = 2**16

NONASCII = re.compile(r'[^.a-zA-Z0-9]')
def getkey(path):
    h = hashlib.md5(path.encode('utf-8')).hexdigest()
    name = os.path.basename(path)
    name = NONASCII.sub(lambda m:'_%04x' % ord(m.group(0)), name)
    return h+'_'+name

def main(argv):
    import getopt
    def usage():
        print('usage: %s [-n] [-p pat] [-m maxsize] [-b dstbase] [-R repos.out] [-M srcmap.db] [zip ...]' %
              argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'np:b:R:M:')
    except getopt.GetoptError:
        return usage()
    debug = 0
    extract = True
    pat = None
    maxsize = 1024*1024
    dstbase = '.'
    repomap = None
    srcmap = None
    for (k, v) in opts:
        if k == '-n': extract = False
        elif k == '-p': pat = re.compile(v)
        elif k == '-m': maxsize = int(v)
        elif k == '-b': dstbase = v
        elif k == '-R': repomap = v
        elif k == '-M': srcmap = SourceMap(v)
    if not args: return usage()
    assert (srcmap is None) == (repomap is None)

    repo = None
    if repomap is not None:
        repo = {}
        with open(repomap) as fp:
            for line in fp:
                (reponame,branch,commit) = line.strip().split(' ')
                repo[commit] = (reponame, branch)
    
    for zippath in args:
        print('extracting: %r...' % zippath, file=sys.stderr)
        try:
            zfp = zipfile.ZipFile(zippath)
        except zipfile.BadZipFile as e:
            print('error: %r: %r' % (zippath, e), file=sys.stderr)
            raise
        dstdir = None
        (commit,_) = os.path.splitext(os.path.basename(zippath))
        if repo is not None:
            (reponame,branch) = repo[commit]
        for info in zfp.infolist():
            if info.is_dir(): continue
            src = info.filename
            if '/.' in src: continue
            if pat is not None and not pat.search(src): continue
            if maxsize < info.file_size:
                print('skipped: %r (%r)' % (src, info.file_size), file=sys.stderr)
                continue
            (dir1,_,src1) = src.partition('/')
            if dstdir != dir1:
                dstdir = dir1
                if extract:
                    try:
                        os.makedirs(os.path.join(dstbase, dstdir))
                    except OSError:
                        pass
            dst = os.path.join(dstdir, getkey(src1))
            print('extract: %r -> %r' % (src1, dst))
            if srcmap is not None:
                srcmap.add(dst, reponame, branch, commit, src1)
            if extract:
                try:
                    fp = zfp.open(src, 'r')
                    try:
                        out = open(os.path.join(dstbase, dst), 'wb')
                        try:
                            while 1:
                                data = fp.read(BUFSIZ)
                                if not data: break
                                out.write(data)
                        finally:
                            out.close()
                    finally:
                        fp.close()
                except (zipfile.BadZipFile, zipfile.zlib.error) as e:
                    print('error: %r/%r: %r' % (zippath, src, e), file=sys.stderr)

    if srcmap is not None:
        srcmap.close()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
