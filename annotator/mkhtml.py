#!/usr/bin/env python
import sys
import os.path
from math import log
from srcdb import SourceDB

BASEDIR = os.path.dirname(__file__)


##  FeatTree
##
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

    def getchildren(self):
        return self._d.items()

    def getpath(self):
        while self.feat is not None:
            yield self.feat
            self = self.parent
        return

    def getkey(self):
        return ''.join(self.getpath())

    def add(self, feat, key, value):
        if feat in self._d:
            t = self._d[feat]
        else:
            t = self._d[feat] = FeatTree(self, feat)
        t.matches[key] = value
        return t


def q(s):
    return (s.replace('&','&amp;')
            .replace('>','&gt;')
            .replace('<','&lt;')
            .replace('"','&quot;'))

def show_html_headers(out, fieldname, title):
    out.write('''<!DOCTYPE html>
<html>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
<style>
pre { margin: 1em; border: 1px solid gray;}
.pair { border-bottom: 2px solid black; color: red; }
.catui { border: 1px solid black; padding: 2px; background: #eeeeee; }
.item { border-bottom: 1px solid black; }
.red { color: red }
.result { }
.src { font-size: 75%; font-weight: bold; font-family: monospace; }
.src0 { background:#eeffff; }
.src1 { background:#ffffee; }
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
''')
    with open(os.path.join(BASEDIR, 'helper.js')) as fp:
        out.write('<script>')
        out.write(fp.read())
        out.write('</script>')
    out.write('''<body onload="run('%s', 'results')">
<h1>Functional Clone Tagging Experiment: %s</h1>

<h2>Your Mission</h2>
<ul>
<li> For each function pair, look at its source code and choose their relationship from the menu.
  <ol type=a>
  <li> Interchangeable (They are exact clones.)
  <li> Complementary (One function can be called instead of another.)
  <li> Inherited (One function overrides another.)
  <li> Inclusive (One function can be implemented by using the other.)
  <li> Opposite (Two functions have the opposite effects.)
  <li> Same Signature (Functions share the same Java signature.)
  <li> No Relation
  </ol>
 When it's undecidable from the given snippet or
 it takes more than <u>3 minutes</u> for a pair,
 choose <code>Unknown</code>.
<li> <strong>Notice:</strong>
  Some functions might not have a source code:
  <ul>
    <li> It's a part of Java standard API (e.g. <code>java.lang.Math.min()</code>).
    Consult the Java reference manual and make an educated guess.
    <li> Due to an extraction bug.
    Mark the pair as <code>Unknown</code>.
  </ul>
<li> <strong>Caution:</strong>
    <u>Do not consult others about the code during this experiment.</u>
<li> Your choices are saved in the follwoing textbox:<br>
  <textarea id="results" cols="80" rows="4" spellcheck="false" autocomplete="off"></textarea><br>
  When finished, send the above content (from <code>#START</code> to <code>#END</code>) to
  the experiment organizer.<br>
</ul>
''' % (fieldname, title))
    return

def show_html(fp, sid, src, ranges, ncontext=1):
    def astart(nid):
        return '<span class="p%s">' % nid
    def aend(anno):
        return '</span>'
    def abody(annos, s):
        return q(s.replace('\n',''))
    fp.write('<div class="src">%s</div>\n' % (src.name))
    fp.write('<pre class="%s">\n' % (sid))
    for (lineno,s) in src.show(
            ranges, astart=astart, aend=aend, abody=abody, ncontext=ncontext):
        if lineno is None:
            fp.write('     '+s+'\n')
        else:
            lineno += 1
            fp.write('%5d:%s\n' % (lineno, s))
    fp.write('</pre>\n')
    return

def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-o output] [-H] [-B basedir] [-c encoding] '
              '[-t threshold] [-n minproj] [-m maxresults] [-M maxlength] '
              'out.idf ...' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'o:HB:c:t:n:m:M:')
    except getopt.GetoptError:
        return usage()
    output = None
    srcdb = None
    encoding = None
    threshold = 10
    minproj = 1
    maxresults = 100
    maxlength = 500
    for (k, v) in opts:
        if k == '-o': output = v
        elif k == '-B': srcdb = SourceDB(v, encoding)
        elif k == '-c': encoding = v
        elif k == '-t': threshold = float(v)
        elif k == '-n': minproj = int(v)
        elif k == '-m': maxresults = int(v)
        elif k == '-M': maxlength = int(v)
    if not args: return usage()

    # Index features.
    srcmap = {}
    funcmap = {}
    usedfuncs = set()
    featfreq = {}
    func = None
    nents = 0
    root = FeatTree()
    title = None
    for path in args:
        title = os.path.basename(path).replace('.idf','').upper()
        with open(path) as fp:
            for line in fp:
                line = line.strip()
                if not line.startswith('+'): continue
                (k,_,v) = line.partition(' ')
                if k == '+SOURCE':
                    (fid,name) = eval(v)
                    srcmap[fid] = name
                elif k == '+FUNC':
                    (func,src) = eval(v)
                    funcmap[func] = (path,src)
                elif k == '+FORW':
                    assert func is not None
                    usedfuncs.add(func)
                    nents += 1
                    feats = eval(v)
                    tree = root
                    locs = [ loc for (_,loc) in feats ]
                    for (i,(feat,_)) in enumerate(feats):
                        if i == 0: continue
                        if feat not in featfreq:
                            featfreq[feat] = 0
                        featfreq[feat] += 1
                        tree = tree.add(feat, func, (locs,i+1))
    #
    featall = sum(featfreq.values())
    print('Read: %d ents, %d sources, %d funcs, %d feats (all: %d)' %
          (nents, len(srcmap), len(usedfuncs), len(featfreq), featall),
          file=sys.stderr)
    featscore = {}
    for (k,v) in featfreq.items():
        featscore[k] = log(featall/v)

    # Discover similars.
    results = {}
    def traverse(tree, score0=0):
        for (feat,st) in tree.getchildren():
            score1 = score0 + featscore.get(feat, 0)
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

    # Remove clusters from a single project.
    for funcs in list(results.keys()):
        a = set()
        for func in funcs:
            (proj,loc) = funcmap[func]
            if loc is not None:
                a.add(proj)
        if len(a) < minproj:
            del results[funcs]

    results = sorted(results.values(), key=lambda x:x[1].getkey(), reverse=True)
    if 0 < maxresults:
        results = results[:maxresults]
    print('Results: %d' % len(results), file=sys.stderr)

    # Output results.
    if output is None:
        fp = sys.stdout
    else:
        fp = open(output, 'w')
    show_html_headers(fp, 'PairTagging_'+title, title)
    for (index,(_,tree)) in enumerate(results):
        pid = 'p%03d' % index
        fp.write('<h2 class=pair>Pair %d</h2>\n' % index)
        fp.write('<div class=catui>Category: <span id="%s" class=ui> </span></div>\n' % (pid))
        for (sid,(func,(locs,n))) in enumerate(tree.matches.items()):
            if sid == 2: break
            mid = 'm%d_%d' % (index, sid)
            fp.write('<h3 class=item><code>%s</code>' % q(func))
            if '.<init>' in func:
                fp.write('&nbsp;<span class=red>(constructor)</span>')
            fp.write('</h3>\n')
            if srcdb is None: continue
            nodes = {}
            def add(loc, i, maxlength):
                (fid,start,length) = loc
                name = srcmap[fid]
                length = min(length, maxlength)
                src = srcdb.get(name)
                if src in nodes:
                    a = nodes[src]
                else:
                    a = nodes[src] = []
                a.append((start, start+length, i))
                return
            (_,loc) = funcmap[func]
            if loc is not None:
                add(loc, 0, maxlength)
            for (src,ranges) in nodes.items():
                show_html(fp, 'src%d' % sid, src, ranges)
        fp.write('<hr>\n')
    if fp is not sys.stdout:
        fp.close()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
