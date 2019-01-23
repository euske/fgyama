#!/usr/bin/env python
import sys
from math import log
from srcdb import SourceDB

def q(s):
    return (s.replace('&','&amp;')
            .replace('>','&gt;')
            .replace('<','&lt;')
            .replace('"','&quot;'))

def show_html_headers(fp):
    fp.write('''<html>
<style>
pre { margin: 1em; border: 1px solid gray;}
h2 { border-bottom: 2px solid black; color: red; }
h3 { border-bottom: 1px solid black; }
.src { font-size: 75%; font-weight: bold; margin: 1em; }
.p0 { background:#ffff00; color:black; }
.p1 { background:#00ffff; color:black; }
.p2 { background:#88ff88; color:black; }
.p3 { background:#ff88ff; color:black; }
.p4 { background:#8888ff; color:black; }
.p5 { background:#ff0000; color:white; }
.p6 { background:#008800; color:white; }
.p7 { background:#0000ff; color:white; }
.p8 { background:#004488; color:white; }
.p9 { background:#884400; color:white; }
</style>
<script>
function toggle(id) {
  let e = document.getElementById(id);
  e.hidden = !e.hidden;
}
</script>
<body>
<h1>Results</h1>
''')
    return

def show_html(fp, src, url, ranges, ncontext=3):
    def astart(nid):
        return '<span class="p%s">' % nid
    def aend(anno):
        return '</span>'
    def abody(annos, s):
        return q(s.replace('\n',''))
    fp.write('<div class=src><a href="%s">%s</a></div>\n' % (q(url), src.name))
    fp.write('<pre>\n')
    for (lineno,s) in src.show(
            ranges, astart=astart, aend=aend, abody=abody, ncontext=ncontext):
        if lineno is None:
            fp.write('     '+s+'\n')
        else:
            lineno += 1
            fp.write('<a href="%s#L%d">%5d</a>:%s\n' %
                     (q(url), lineno, lineno, s))
    fp.write('</pre>\n')
    return

def show_text(fp, src, ranges, ncontext=3):
    fp.write('#\n', src.name)
    for (lineno,line) in src.show(ranges, ncontext=ncontext):
        if lineno is None:
            fp.write(line.rstrip()+'\n')
        else:
            fp.write('%4d: %s\n' % (lineno, line.rstrip()))
    fp.write('\n')
    return

class FeatTree:

    def __init__(self, parent=None, feat=None):
        if parent is None:
            self.level = 0
        else:
            self.level = parent.level+1
        self.parent = parent
        self.feat = feat
        self.matches = {}
        self._d = {}
        return

    def feats(self):
        a = []
        while self.feat is not None:
            a.append(self.feat)
            self = self.parent
        a.reverse()
        return a

    def children(self):
        return self._d.items()

    def add(self, feat, func, locs):
        if feat in self._d:
            t = self._d[feat]
        else:
            t = self._d[feat] = FeatTree(self, feat)
        t.matches[func] = locs
        return t


def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-o output] [-H] [-B basedir] '
              '[-t threshold] [-m maxresults] [-c encoding] '
              'out.idf ...' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'o:HB:t:m:c:')
    except getopt.GetoptError:
        return usage()
    output = None
    html = False
    srcdb = None
    encoding = None
    threshold = 10
    maxresults = 100
    for (k, v) in opts:
        if k == '-o': output = v
        elif k == '-H': html = True
        elif k == '-B': srcdb = SourceDB(v, encoding)
        elif k == '-t': threshold = float(v)
        elif k == '-m': maxresults = int(v)
        elif k == '-c': encoding = v
    if not args: return usage()

    def splitfeat(v):
        (v,_,v4) = v.rpartition(',')
        (v,_,v3) = v.rpartition(',')
        (v1,_,v2) = v.rpartition(',')
        return (v1, (srcmap[v2],int(v3),int(v4)))
    def getfeat(v):
        # strip ast
        (v,_,v4) = v.rpartition(',')
        (v,_,v3) = v.rpartition(',')
        (v1,_,v2) = v.rpartition(',')
        return v1
    def getloc(v):
        (v,_,v3) = v.rpartition(',')
        (v,_,v2) = v.rpartition(',')
        (v,_,v1) = v.rpartition(',')
        if not v1: return None
        return (srcmap[v1],int(v2),int(v3))

    paths = 0
    srcmap = {}
    featmap = {}
    root = FeatTree()
    for line in fileinput.input(args):
        line = line.strip()
        if not line.startswith('+'): continue
        f = line.split(' ')
        if f[0] == '+SOURCE':
            srcmap[f[1]] = f[2]
        elif f[0] == '+PATH' and f[2] == 'forw':
            paths += 1
            (func,loc0) = splitfeat(f[1])
            feats = [ getfeat(x) for x in f[3:] ]
            locs = [loc0] + [ getloc(x) for x in f[3:] ]
            tree = root
            for (i,feat) in enumerate(feats):
                if feat not in featmap:
                    featmap[feat] = 0
                featmap[feat] += 1
                tree = tree.add(feat, func, locs[:i+2])
    #
    featall = sum(featmap.values())
    for k in featmap.keys():
        featmap[k] = log(featall/featmap[k])
    print('Read: %d paths, %d sources, %d feats (all: %d)' %
          (paths, len(srcmap), len(featmap), featall), file=sys.stderr)
    #
    results = {}
    def traverse(tree, score0=0):
        for (feat,st) in tree.children():
            score1 = score0 + featmap.get(feat, 0)
            if threshold <= score1 and 2 <= len(st.matches):
                funcs = tuple(sorted(st.matches.keys()))
                if funcs in results:
                    (maxscore,maxlocs) = results[funcs]
                    if maxscore < score1:
                        results[funcs] = (score1, st)
                else:
                    results[funcs] = (score1, st)
            traverse(st, score1)
        return
    traverse(root)

    results = sorted(results.values(), key=lambda x:x[0], reverse=True)
    if 0 < maxresults:
        results = results[:maxresults]
    print('Results: %d' % len(results), file=sys.stderr)

    if output is None:
        fp = sys.stdout
    else:
        fp = open(output, 'w')
    if html:
        show_html_headers(fp)
    mid = 0
    for (score,tree) in results:
        feats = tree.feats()
        if html:
            fp.write('<h2>[%.3f] <code>%s</code> (%d)</h2>\n' %
                     (score, q(repr(feats)), len(tree.matches)))
        else:
            fp.write('! %.3f %r %d\n' % (score, feats, len(tree.matches)))
        for (func,locs) in tree.matches.items():
            if html:
                mid += 1
                fp.write('<h3><a href="#M%d" onclick="toggle(\'M%d\');">[+]</a> '
                         '<code>%s</code></h3>\n' % (mid, mid, q(func)))
            else:
                fp.write('+ %s\n' % func)
            if srcdb is None: continue
            if html:
                fp.write('<div class=result hidden id="M%d">\n' % mid)
            nodes = {}
            for (i,loc) in enumerate(locs):
                if loc is None: continue
                (name,start,length) = loc
                if i == 0:
                    length = max(length, 200)
                src = srcdb.get(name)
                if src in nodes:
                    a = nodes[src]
                else:
                    a = nodes[src] = []
                a.append((start, start+length, i))
            for (src,ranges) in nodes.items():
                if html:
                    show_html(fp, src, src.name, ranges)
                else:
                    show_text(fp, src, ranges)
            if html:
                fp.write('</div>\n')
        if html:
            fp.write('<hr>\n')
        else:
            fp.write()

    if fp is not sys.stdout:
        fp.close()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
