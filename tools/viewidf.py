#!/usr/bin/env python
import sys
from math import log
from srcdb import SourceDB

def q(s):
    return (s.replace('&','&amp;')
            .replace('>','&gt;')
            .replace('<','&lt;')
            .replace('"','&quot;'))

def show_html_headers():
    print('''<html>
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
<body>
<h1>Results</h1>
''')
    return

def show_html(src, url, ranges, ncontext=3):
    def astart(nid):
        return '<span class="p%s">' % nid
    def aend(anno):
        return '</span>'
    def abody(annos, s):
        return q(s.replace('\n',''))
    print('<div class=src><a href="%s">%s</a></div>' % (q(url), src.name))
    print('<pre>')
    for (lineno,s) in src.show(
            ranges, astart=astart, aend=aend, abody=abody, ncontext=ncontext):
        if lineno is None:
            print ('     '+s)
        else:
            lineno += 1
            print ('<a href="%s#L%d">%5d</a>:%s' %
                   (q(url), lineno, lineno, s))
    print('</pre>')
    return

def show_text(src, ranges, ncontext=3):
    print('#', src.name)
    for (lineno,line) in src.show(ranges, ncontext=ncontext):
        if lineno is None:
            print(line.rstrip())
        else:
            print(lineno, line.rstrip())
    print()
    return

def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-B basedir] [-H] '
              'out.idf' %
              argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'B:H')
    except getopt.GetoptError:
        return usage()
    srcdb = None
    html = False
    for (k, v) in opts:
        if k == '-B': srcdb = SourceDB(v)
        elif k == '-H': html = True
    if not args: return usage()

    def sast(v):  # strip ast
        (v1,_,v) = v.partition(',')
        (v2,_,v) = v.partition(',')
        return (v1+','+v2)

    count = {}
    srcmap = {}
    featmap = {}
    total = 0
    for line in fileinput.input(args):
        line = line.strip()
        if not line.startswith('+'): continue
        f = line.split(' ')
        if f[0] == '+PATH':
            if f[2] != '+1': continue
            func = (f[1],f[2])
            feats = tuple(map(sast, f[3:]))
            if len(feats) < 2: continue
            if feats in count:
                d = count[feats]
            else:
                d = count[feats] = {}
            d[func] = f[3:]
        elif f[0] == '+SOURCE':
            srcmap[int(f[1])] = f[2]
        elif f[0] == '+FEAT':
            n = int(f[1])
            featmap[f[2]] = n
            total += n
    #
    for k in featmap:
        p = log(total/featmap[k])
        featmap[k] = p
    def featsscore(feats):
        return sum( featmap[k] for k in feats )*log(len(count[feats]))
    keys = sorted(count.keys(), key=featsscore, reverse=True)

    if html:
        show_html_headers()
    for feats in keys:
        n = len(count[feats])
        if n < 2: continue
        if html:
            print('<h2><code>%s</code> (%d)</h2>' % (q(' '.join(feats)), n))
        else:
            print('!', feats, n)
        for ((func,_),data) in count[feats].items():
            f = func.split(',')
            if html:
                print('<h3><code>%s</code></h3>' % q(f[0]))
            else:
                print ('+', f[0])
            if srcdb is None: continue
            if len(f) != 4: continue
            start = int(f[1])
            length = int(f[2])
            fid = int(f[3])
            src = srcdb.get(srcmap[fid])
            ranges = [(start, start+min(100, length), None)]
            if html:
                show_html(src, src.name, ranges, ncontext=1)
            else:
                show_text(src, ranges, ncontext=1)
            nodes = {}
            for (i,x) in enumerate(data):
                f = x.split(',')
                if len(f) != 5: continue
                start = int(f[2])
                length = int(f[3])
                fid = int(f[4])
                src = srcdb.get(srcmap[fid])
                if src in nodes:
                    a = nodes[src]
                else:
                    a = nodes[src] = []
                a.append((start,start+length,i))
            for (src,ranges) in nodes.items():
                if html:
                    show_html(src, src.name, ranges)
                else:
                    show_text(src, ranges)

        if html:
            print('<hr>')
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
