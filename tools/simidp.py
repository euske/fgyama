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
    itemmap = {}
    useditems = set()
    featfreq = {}
    item = None
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
                    (item,src) = eval(v)
                    itemmap[item] = (path,src)
                elif k in ('+FORW','+BACK'):
                    assert item is not None
                    useditems.add(item)
                    nents += 1
                    feats = eval(v)
                    tree = root
                    locs = [ loc for (_,loc) in feats ]
                    for (i,(feat,_)) in enumerate(feats):
                        #if i == 0: continue
                        if feat not in featfreq:
                            featfreq[feat] = 0
                        featfreq[feat] += 1
                        tree = tree.add(feat, item, (locs,i+1))
    #
    featall = sum(featfreq.values())
    print('Read: %d ents, %d sources, %d items, %d feats (all: %d)' %
          (nents, len(srcmap), len(useditems), len(featfreq), featall),
          file=sys.stderr)
    featscore = {}
    for (k,v) in featfreq.items():
        featscore[k] = log(featall/v)

    # Discover similars.
    links = {}
    def traverse(tree, score0=0):
        for (feat,st) in tree.getchildren():
            score1 = score0 + featscore.get(feat, 0)
            if threshold <= score1 and 2 <= len(st.matches):
                items = tuple(sorted(st.matches.keys()))
                if items not in links:
                    links[items] = []
                links[items].append((score1, st))
            traverse(st, score1)
        return
    traverse(root)
    totals = { items: max( s for (s,_) in v ) for (items,v) in links.items() }

    # Form clusters.
    class Cluster:
        def __init__(self, items):
            self.items = items
            self.locs = set()
            return
        def add(self, item):
            self.items.append(item)
            return self
        def merge(self, c):
            if c is not self:
                self.items.extend(c.items)
                self.locs.update(c.locs)
            return self
        def addloc(self, loc):
            self.locs.add(loc)
            return
    clusters = {}
    pairs = sorted(totals.items(), key=lambda x:x[1], reverse=True)
    if 0 < maxresults:
        pairs = pairs[:maxresults]
    for (items,total) in pairs:
        for (i,k1) in enumerate(items):
            for k2 in items[i+1:]:
                if k1 in clusters and k2 in clusters:
                    c = clusters[k1].merge(clusters[k2])
                    for k2 in c.items:
                        clusters[k2] = c
                elif k1 in clusters:
                    c = clusters[k1].add(k2)
                elif k2 in clusters:
                    c = clusters[k2].add(k2)
                else:
                    c = clusters[k1] = clusters[k2] = Cluster([k1,k2])
                for (_,loc) in links[items]:
                    c.addloc(loc)

    # Remove clusters from a single project.
    for items in list(links.keys()):
        a = set()
        for item in items:
            (proj,loc) = itemmap[item]
            if loc is not None:
                a.add(proj)
        if len(a) < minproj:
            del links[items]

    results = sorted(links.items(), key=lambda x:totals[x[0]], reverse=True)
    print('Cluster: %d' % len(results), file=sys.stderr)

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
    for (_,trees) in results:
        for (score,tree) in sorted(trees, key=lambda x:x[0], reverse=True):
            feats = list(tree.getpath())
            feats.reverse()
            if html:
                fp.write('<h2>[%.3f] <code>%s</code> (%d)</h2>\n' %
                         (score, q(repr(feats)), len(tree.matches)))
            else:
                fp.write('! %.3f %r %d\n' % (score, feats, len(tree.matches)))
            for (item,(locs,n)) in tree.matches.items():
                mid += 1
                if html:
                    fp.write('<h3><a href="#M%d" onclick="toggle(\'M%d\');">[+]</a> '
                             '<code>%s</code></h3>\n' % (mid, mid, q(item)))
                else:
                    fp.write('+ %s\n' % item)
                if srcdb is None: continue
                if html:
                    fp.write('<div class=result hidden id="M%d">\n' % mid)
                annot = SourceAnnot(srcdb)
                def add(loc, i, maxlength):
                    (fid,start,end) = loc
                    name = srcmap[fid]
                    end = min(end, start+maxlength)
                    annot.add(name, start, end, i)
                    return
                (_,loc) = itemmap[item]
                if loc is not None:
                    add(loc, 0, maxlength)
                for loc in locs[:n]:
                    if loc is not None:
                        add(loc, i+1, sys.maxsize)
                if html:
                    annot.show_html(fp)
                    fp.write('</div>\n')
                else:
                    annot.show_text(fp)
            break
        if html:
            fp.write('<hr>\n')
        else:
            fp.write('\n')
    if fp is not sys.stdout:
        fp.close()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
