#!/usr/bin/env python
import sys
import re
from math import sqrt, log
from srcdb import SourceDB
from graph import get_graphs

# main
def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-v] [-o output] [-B basedir] [-c encoding] [-t threshold] '
              'out.graph ...' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'vo:B:c:t:')
    except getopt.GetoptError:
        return usage()
    output = None
    srcdb = None
    encoding = 'utf-8'
    threshold = 0.7
    verbose = False
    for (k, v) in opts:
        if k == '-v': verbose = True
        elif k == '-o': output = v
        elif k == '-B': srcdb = SourceDB(v, encoding)
        elif k == '-c': encoding = v
        elif k == '-t': threshold = float(v)
    if not args: return usage()

    if output is None:
        fp = sys.stdout
    else:
        fp = open(output, 'w')

    assert srcdb is not None

    PAT = re.compile(r'\w+')
    def gettokens(s):
        return ( m.group(0) for m in PAT.finditer(s) )
    tokens = []
    freq = {}
    for path in args:
        print('Loading: %r...' % path, file=sys.stderr)
        for graph in get_graphs(path):
            if graph.style == 'initializer': continue
            if graph.src is None: continue
            if graph.ast is None: continue
            (_,loc,length) = graph.ast
            src = srcdb.get(graph.src)
            text = src.data[loc:loc+length]
            c = {}
            for t in gettokens(text):
                if t not in c:
                    c[t] = 0
                c[t] += 1
            assert c, repr(text)
            for t in c.keys():
                if t not in freq:
                    freq[t] = 0
                freq[t] += 1
            tokens.append((graph, c))
    total = sum(freq.values())
    idf = {}
    for (t,n) in freq.items():
        idf[t] = log(total/n)

    sys.stderr.write('Clustering')
    def calcsim(t1, t2):
        s1 = sum( (idf[w1]*f1)**2 for (w1,f1) in t1.items() )
        s2 = sum( (idf[w2]*f2)**2 for (w2,f2) in t2.items() )
        s3 = 0
        for (w,f1) in t1.items():
            if w in t2:
                f2 = t2[w]
                s3 += (idf[w]**2)*f1*f2
        return s3/sqrt(s1*s2)
    a = []
    for (i,(g1,c1)) in enumerate(tokens):
        for (g2,c2) in tokens[i+1:]:
            sim = calcsim(c1,c2)
            if threshold <= sim:
                a.append((sim,g1,g2))
        sys.stderr.write('.')
        sys.stderr.flush()
    a.sort(key=lambda x:x[0], reverse=True)
    sys.stderr.write('\n')

    class Cluster:
        def __init__(self):
            self.objs = []
            return
        def __len__(self):
            return len(self.objs)
        def __iter__(self):
            return iter(self.objs)
        def add(self, obj):
            self.objs.append(obj)
        def merge(self, c):
            self.objs.extend(c.objs)
    cls = {}
    for (_,g1,g2) in a:
        if g1 in cls and g2 in cls:
            # both g1 and g2 are in - merge them.
            cls[g1].merge(cls[g2])
            del cls[g2]
        elif g1 in cls:
            # g1 is in, g2 is not.
            cls[g1].add(g2)
        elif g2 in cls:
            # g2 is in, g1 is not.
            cls[g2].add(g1)
        else:
            # both are not in. create new.
            c = Cluster()
            c.add(g1)
            c.add(g2)
            cls[g1] = cls[g2] = c
    for c in sorted(set(cls.values()), key=len, reverse=True):
        fp.write('= %d\n' % len(c))
        for graph in c:
            fp.write('+ %s\n' % graph.name)
            if not verbose: continue
            if graph.src is None or graph.ast is None: continue
            src = srcdb.get(graph.src)
            (_,loc,length) = graph.ast
            fp.write('# %s\n' % graph.src)
            ranges = [(loc, loc+length, 0)]
            for (lineno,line) in src.show(ranges):
                if lineno is None:
                    fp.write(line.rstrip()+'\n')
                else:
                    fp.write('%4d: %s\n' % (lineno, line.rstrip()))
        fp.write('\n')

    if fp is not sys.stdout:
        fp.close()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
