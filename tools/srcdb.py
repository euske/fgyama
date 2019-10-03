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
        url = f'https://github.com/{reponame}/tree/{commit}/{srcpath}'
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
        return (f'<SourceFile({self.name})>')

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
                    elif v < 0:
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
            triggers.append((s,-1,anno))
            triggers.append((e,+1,anno))
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
                if v < 0:
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

    def __init__(self, basedir, encoding=None):
        self.basedir = basedir
        self.encoding = encoding
        self.srcmap = {}
        self._cache = {}
        return

    def register(self, fid, name):
        self.srcmap[fid] = name
        return

    def get(self, name):
        if name in self._cache:
            src = self._cache[name]
        else:
            path = os.path.join(self.basedir, name)
            try:
                with open(path, encoding=self.encoding) as fp:
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


def q(s):
    return (s.replace('&','&amp;')
            .replace('>','&gt;')
            .replace('<','&lt;')
            .replace('"','&quot;'))

def show_html_headers(fp=sys.stdout):
    fp.write('''<style>
pre { margin: 1em; outline: 1px solid gray;}
h2 { border-bottom: 2px solid black; color: red; }
h3 { border-bottom: 1px solid black; }
.src { font-size: 75%; font-weight: bold; margin: 1em; }
.p1 { background:#ffff00; color:black; }
.p2 { background:#00ffff; color:black; }
.p3 { background:#88ff88; color:black; }
.p4 { background:#ff88ff; color:black; }
.p5 { background:#8888ff; color:black; }
.p6 { background:#ff0000; color:white; }
.p7 { background:#008800; color:white; }
.p8 { background:#0000ff; color:white; }
.p9 { background:#004488; color:white; }
.p10 { background:#884400; color:white; }
</style>
<script>
function toggle(id) {
  let e = document.getElementById(id);
  e.hidden = !e.hidden;
}
</script>
''')
    return


##  SourceAnnot
##
class SourceAnnot:

    def __init__(self, srcdb):
        self.srcdb = srcdb
        self.nodes = {}
        return

    def __iter__(self):
        return iter(self.nodes.items())

    def add(self, name, start, end, anno=None):
        src = self.srcdb.get(name)
        if src in self.nodes:
            a = self.nodes[src]
        else:
            a = self.nodes[src] = []
        a.append((start, end, anno))
        return

    def addbyfid(self, fid, start, end, anno=None):
        name = self.srcdb.srcmap[fid]
        self.add(name, start, end, anno=anno)
        return

    def show_html(self, fp=sys.stdout, furl=q, ncontext=3):
        for (src,ranges) in self:
            url = src.name
            def astart(nid):
                return f'<span class="p{nid}">'
            def aend(anno):
                return '</span>'
            def abody(annos, s):
                return q(s.replace('\n',''))
            fp.write(f'<div class=src><a href="{furl(url)}">{src.name}</a></div>\n')
            fp.write('<pre>\n')
            for (lineno,s) in src.show(
                    ranges, astart=astart, aend=aend, abody=abody,
                    ncontext=ncontext):
                if lineno is None:
                    fp.write('     '+s+'\n')
                else:
                    lineno += 1
                    fp.write(f'<a href="{furl(url)}#L{lineno}">{lineno:5d}</a>:{s}\n')
            fp.write('</pre>\n')
        return

    def show_text(self, fp=sys.stdout, ncontext=3,
                  showline=lambda line: line):
        for (src,ranges) in self:
            fp.write(showline(f'# {src.name}')+'\n')
            for (lineno,line) in src.show(ranges, ncontext=ncontext):
                if lineno is None:
                    fp.write(showline('    '+line.rstrip())+'\n')
                else:
                    fp.write(showline(f'{lineno:4d}: {line.rstrip()}')+'\n')
            fp.write('\n')
        return
