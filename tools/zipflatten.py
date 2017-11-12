#!/usr/bin/env python
##
##  flatten .zip files
##

import sys
import re
import os.path
import zipfile
import hashlib
import sqlite3

BUFSIZ = 2**16

def build_srcmap_tables(cur):
    cur.executescript('''
CREATE TABLE SourceMap (
    Uid INTEGER PRIMARY KEY,
    FileName TEXT,
    RepoName TEXT,
    BranchName TEXT,
    CommitId TEXT,
    SrcPath TEXT
);
CREATE INDEX SourceMapIndex ON SourceMap(FileName);
''')
    return

NONASCII = re.compile(r'[^.a-zA-Z0-9]')
def getkey(path):
    h = hashlib.md5(path.encode('utf-8')).hexdigest()
    name = os.path.basename(path)
    name = NONASCII.sub(lambda m:'_%04x' % ord(m.group(0)), name)
    return h+'_'+name

def main(argv):
    import getopt
    def usage():
        print('usage: %s [-n] [-p pat] [-b dstbase] [-R repos.out] [-M srcmap.db] [zip ...]' %
              argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'np:b:R:M:')
    except getopt.GetoptError:
        return usage()
    debug = 0
    extract = True
    pat = None
    dstbase = '.'
    repomap = None
    srcmap = None
    for (k, v) in opts:
        if k == '-n': extract = False
        elif k == '-p': pat = re.compile(v)
        elif k == '-b': dstbase = v
        elif k == '-R': repomap = v
        elif k == '-M': srcmap = v
    if not args: return usage()
    assert (srcmap is None) == (repomap is None)
    
    srcmapconn = srcmapcur = None
    if srcmap is not None:
        srcmapconn = sqlite3.connect(srcmap)
        srcmapcur = srcmapconn.cursor()
        try:
            build_srcmap_tables(srcmapcur)
        except sqlite3.OperationalError:
            pass

    repo = None
    if repomap is not None:
        repo = {}
        with open(repomap) as fp:
            for line in fp:
                (reponame,branch,commit) = line.strip().split(' ')
                repo[commit] = (reponame, branch)
    
    for zippath in args:
        zfp = zipfile.ZipFile(zippath)
        dstdir = None
        (commit,_) = os.path.splitext(os.path.basename(zippath))
        if repo is not None:
            (reponame,branch) = repo[commit]
        for info in zfp.infolist():
            if info.is_dir(): continue
            src = info.filename
            if '/.' in src: continue
            if pat is not None and not pat.search(src): continue
            (dir1,_,src1) = src.partition('/')
            if dstdir != dir1:
                dstdir = dir1
                if extract:
                    try:
                        os.makedirs(os.path.join(dstbase, dstdir))
                    except OSError:
                        pass
            dst = os.path.join(dstdir, getkey(src1))
            print('extract: %r -> %r' % (src1, dst), file=sys.stderr)
            if srcmapcur is not None:
                srcmapcur.execute(
                    'INSERT INTO SourceMap VALUES (NULL,?,?,?,?,?);',
                    (dst, reponame, branch, commit, src1))
            if extract:
                fp = zfp.open(src, 'r')
                out = open(os.path.join(dstbase, dst), 'wb')
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
