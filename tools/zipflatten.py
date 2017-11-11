#!/usr/bin/env python
##
##  flatten .zip files
##

import sys
import re
import os.path
import zipfile
import sqlite3

BUFSIZ = 2**16

def build_srcmap_tables(cur):
    cur.executescript('''
CREATE TABLE SourcePath (
    Uid INTEGER PRIMARY KEY,
    FileName TEXT,
    ZipFile TEXT,
    SrcPath TEXT
);
CREATE INDEX SourcePathIndex ON SourcePath(FileName);
''')
    return

def main(argv):
    import getopt
    def usage():
        print('usage: %s [-n] [-p pat] [-M srcmap.db] [zip ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'np:M:')
    except getopt.GetoptError:
        return usage()
    debug = 0
    extract = True
    pat = None
    srcmap = None
    for (k, v) in opts:
        if k == '-n': extract = False
        elif k == '-p': pat = re.compile(v)
        elif k == '-M': srcmap = v
    if not args: return usage()
    
    srcmapconn = srcmapcur = None
    if srcmap is not None:
        srcmapconn = sqlite3.connect(srcmap)
        srcmapcur = srcmapconn.cursor()
        try:
            build_srcmap_tables(srcmapcur)
        except sqlite3.OperationalError:
            pass
    
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
                if extract:
                    try:
                        os.makedirs(outdir)
                    except OSError:
                        pass
            dst = os.path.join(outdir, dst.replace('/','_'))
            print('extract: %r -> %r' % (src, dst), file=sys.stderr)
            if srcmapcur is not None:
                srcmapcur.execute('INSERT INTO SourcePath VALUES (NULL,?,?,?);',
                                  (dst, os.path.basename(zippath), src))
            if extract:
                fp = zfp.open(src, 'r')
                out = open(dst, 'wb')
                while 1:
                    data = fp.read(BUFSIZ)
                    if not data: break
                    out.write(data)
                out.close()
                fp.close()
    
    if srcmapconn is not None:
        srcmapconn.commit()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
