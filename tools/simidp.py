#!/usr/bin/env python
import sys
from math import log
from srcdb import SourceDB, SourceAnnot
from srcdb import q, show_html_headers


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

    def add(self, feat, key, value):
        if feat in self._d:
            t = self._d[feat]
        else:
            t = self._d[feat] = FeatTree(self, feat)
        t.matches[key] = value
        return t


def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-o output] [-H] [-B basedir] [-c encoding] '
              '[-t threshold] [-n minproj] [-m maxresults] [-M maxlength] '
              'out.idp ...' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'o:HB:c:t:n:m:M:')
    except getopt.GetoptError:
        return usage()
    output = None
    html = False
    srcdb = None
    encoding = None
    threshold = 10
    minproj = 1
    maxresults = 100
    maxlength = 200
    for (k, v) in opts:
        if k == '-o': output = v
        elif k == '-H': html = True
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
    for path in args:
        with open(path) as fp:
            for line in fp:
                line = line.strip()
                if not line.startswith('+'): continue
                (k,_,v) = line.partition(' ')
                if k == '+SOURCE':
                    (fid,name) = eval(v)
                    srcmap[fid] = name
                elif k == '+ITEM':
                    (func,src) = eval(v)
                    funcmap[func] = (path,src)
                elif k in ('+FORW','+BACK'):
                    assert func is not None
                    usedfuncs.add(func)
                    nents += 1
                    feats = eval(v)
                    tree = root
                    locs = [ loc for (_,loc) in feats ]
                    for (i,(feat,_)) in enumerate(feats):
                        #if i == 0: continue
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

    results = sorted(results.values(), key=lambda x:x[0], reverse=True)
    if 0 < maxresults:
        results = results[:maxresults]
    print('Results: %d' % len(results), file=sys.stderr)

    # Output results.
    if output is None:
        fp = sys.stdout
    else:
        fp = open(output, 'w')
    if html:
        fp.write('<html>')
        show_html_headers(fp)
        fp.write('<body><h1>Results</h1>')
    mid = 0
    for (score,tree) in results:
        feats = list(tree.getpath())
        feats.reverse()
        if html:
            fp.write('<h2>[%.3f] <code>%s</code> (%d)</h2>\n' %
                     (score, q(repr(feats)), len(tree.matches)))
        else:
            fp.write('! %.3f %r %d\n' % (score, feats, len(tree.matches)))
        for (func,(locs,n)) in tree.matches.items():
            locs = locs[:n]
            if html:
                mid += 1
                fp.write('<h3><a href="#M%d" onclick="toggle(\'M%d\');">[+]</a> '
                         '<code>%s</code></h3>\n' % (mid, mid, q(func)))
            else:
                fp.write('+ %s\n' % func)
            if srcdb is None: continue
            if html:
                fp.write('<div class=result hidden id="M%d">\n' % mid)
            annot = SourceAnnot(srcdb)
            def add(loc, i, maxlength):
                (fid,start,length) = loc
                name = srcmap[fid]
                length = min(length, maxlength)
                annot.add(name, start, start+length, i)
                return
            (_,loc) = funcmap[func]
            if loc is not None:
                add(loc, 0, maxlength)
            for loc in locs:
                if loc is not None:
                    add(loc, i+1, sys.maxsize)
            if html:
                annot.show_html(fp)
                fp.write('</div>\n')
            else:
                annot.show_text(fp)
        if html:
            fp.write('<hr>\n')
        else:
            fp.write('\n')
    if fp is not sys.stdout:
        fp.close()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
