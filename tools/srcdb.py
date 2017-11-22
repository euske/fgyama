#!/usr/bin/env python
import sys
import os.path
import sqlite3


##  SourceMap
##
class SourceMap:

    def __init__(self, path):
        self._conn = sqlite3.connect(path)
        self._cur = self._conn.cursor()
        try:
            self._cur.executescript('''
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
        except sqlite3.OperationalError:
            pass
        return

    def close(self):
        self._conn.commit()
        return

    def add(self, path, reponame, branch, commit, src):
        self._cur.execute(
            'INSERT INTO SourceMap VALUES (NULL,?,?,?,?,?);',
            (path, reponame, branch, commit, src))
        return

    def get(self, key):
        rows = self._cur.execute(
            'SELECT RepoName,CommitId,SrcPath FROM SourceMap WHERE FileName=?;',
            (key,))
        if not rows: raise KeyError(key)
        return rows.fetchone()

    def geturl(self, key):
        (reponame,commit,srcpath) = self.get(key)
        url = 'https://github.com/%s/tree/%s/%s' % (reponame, commit, srcpath)
        return url


##  SourceFile
##
class SourceFile:

    def __init__(self, name, data):
        self.name = name
        self.data = data
        self.lines = data.splitlines(True)
        return
    
    def __repr__(self):
        return ('<SourceFile(%s)>' %
                (self.name,))

    def show_nodes(self, nodes,
                   astart=(lambda _: '['),
                   aend=(lambda _: ']'),
                   abody=(lambda _,s: s),
                   ncontext=1, skip='...\n'):
        ranges = []
        for node in nodes:
            if node.ast is None: continue
            (_,i,n) = node.ast
            ranges.append((i, i+n, node.nid))
        return self.show(ranges, ncontext=ncontext, skip=skip,
                         astart=astart, aend=aend, abody=abody)

    # ranges=[(start,end,anno), ...]
    def show(self, ranges,
             astart=(lambda _: '['),
             aend=(lambda _: ']'),
             abody=(lambda _,s: s),
             ncontext=1, skip='...\n'):
        for (lineno,line) in self.chunk(ranges, ncontext=ncontext):
            if lineno is None:
                yield (lineno,skip)
            else:
                buf = ''
                for (v, anno, s) in line:
                    if v == 0:
                        buf += abody(anno, s)
                    elif 0 < v:
                        buf += astart(anno)
                    else:
                        buf += aend(anno)
                yield (lineno,buf)
        return

    # ranges=[(start,end,anno), ...]
    def chunk(self, ranges, ncontext=1):
        if not ranges: return
        triggers = []
        for (s,e,anno) in ranges:
            triggers.append((s,+1,anno))
            triggers.append((e,-1,anno))
        triggers.sort(key=lambda x: (x[0],x[1]))
        lines = {}
        loc0 = 0
        i = 0
        annos = []
        for (lineno,line) in enumerate(self.lines):
            loc1 = loc0+len(line)
            pos0 = 0
            out = []
            while i < len(triggers):
                (loc,v,anno) = triggers[i]
                if loc1 < loc: break
                i += 1
                pos1 = loc - loc0
                out.append((0, annos[:], line[pos0:pos1]))
                pos0 = pos1
                if 0 < v:
                    out.append((v, anno, None))
                    annos.append(anno)
                else:
                    out.append((v, anno, None))
                    annos.remove(anno)
            if out:
                out.append((0, annos[:], line[pos0:]))
                lines[lineno] = out
            elif annos:
                lines[lineno] = [(0, annos[:], line)]
            loc0 = loc1
        n = len(self.lines)
        for (lineno,line) in list(lines.items()):
            for i in range(max(0, lineno-ncontext),
                           min(n, lineno+ncontext+1)):
                if i not in lines:
                    lines[i] = [(0, [], self.lines[i])]
        lineno0 = 0
        for lineno1 in sorted(lines):
            if lineno0 < lineno1:
                yield (None, None)
            yield (lineno1, lines[lineno1])
            lineno0 = lineno1+1
        if lineno0 < len(self.lines):
            yield (None, None)
        return


##  SourceDB
##
class SourceDB:
    
    def __init__(self, basedir):
        self.basedir = basedir
        self._cache = {}
        return

    def get(self, name):
        if name in self._cache:
            src = self._cache[name]
        else:
            path = os.path.join(self.basedir, name)
            try:
                with open(path) as fp:
                    data = fp.read()
            except IOError:
                raise KeyError(name)
            except UnicodeError:
                raise KeyError(name)
            src = SourceFile(name, data)
            self._cache[name] = src
        return src

    def show(self, fp=sys.stdout):
        for src in self._cache.values():
            for (_,line) in src.show():
                fp.write(line)
        return
