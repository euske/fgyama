#!/usr/bin/env python
import sys
import sqlite3
from graph import SourceDB, DFGraph
from graph import fetch_graph

def q(s):
    return s.replace('&','&amp;').replace('>','&gt;').replace('<','&lt;').replace('"','&quot;')

def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-B basedir] [-U urls.db] [-H] [-n minnodes] [-m maxpairs] '
              'graph.db sg.out' %
              argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'B:U:Hn:m:')
    except getopt.GetoptError:
        return usage()
    srcdb = None
    urlsname = None
    minnodes = 7
    maxpairs = 3
    html = False
    for (k, v) in opts:
        if k == '-B': srcdb = SourceDB(v)
        elif k == '-U': urlsname = v
        elif k == '-H': html = True
        elif k == '-n': minnodes = int(v)
        elif k == '-m': maxpairs = int(v)
    if not args: return usage()

    urlsconn = urlscur = None
    if urlsname is not None:
        urlsconn = sqlite3.connect(urlsname)
        urlscur = urlsconn.cursor()
    
    def geturl(name):
        if urlscur is None:
            return name
        rows = urlscur.execute('SELECT URL FROM SourceURL WHERE FileName=?;', (name,))
        (url,) = rows.fetchone()
        return url

    def show_html_headers():
        print('''<html>
<style>
pre { margin: 1em; border: 1px solid gray;}
.pair { border: 2px solid black; margin: 1em; }
.s0 { background: #ddffff; }
.s1 { background: #ffffdd; }
.src { font-size: 50%; font-weight: bold; margin: 1em; }
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
''')        
        return

    def show_html(gid, src, nodes, klass=''):
        url = geturl(src.name)
        d = {}
        for (i,n) in enumerate(nodes):
            d[n.nid] = i
        def astart(nid):
            return '<span class="p%s">' % d[nid]
        def aend(anno):
            return '</span>'
        def abody(annos, s):
            return q(s.replace('\n',''))
        def println(lineno, s):
            if lineno is None:
                print ('     '+s)
            else:
                lineno += 1
                print ('<a href="%s#L%d">%5d</a>:%s' %
                       (q(url), lineno, lineno, s))
            return
        print('<div class=src>%s: <a href="%s">%s</a></div>' % (gid, q(url), src.name))
        print('<pre class=%s>' % klass)
        src.show_nodes(nodes, println=println, astart=astart, aend=aend, abody=abody)
        print('</pre>')
        return

    graphname = args.pop(0)
    graphconn = sqlite3.connect(graphname)
    graphcur = graphconn.cursor()
    if html:
        show_html_headers()

    npairs = {}
    for line in fileinput.input(args):
        line = line.strip()
        if not line.startswith('-'): continue
        f = line.split(' ')
        gid0 = int(f[1])
        gid1 = int(f[2])
        nids = []
        for v in f[4].split(','):
            (v1,_,v2) = v.partition(':')
            nids.append((int(v1), int(v2)))
        if len(nids) < minnodes: continue
        if 0 < maxpairs:
            if gid0 in npairs:
                if maxpairs <= npairs[gid0]: continue
                npairs[gid0] += 1
            else:
                npairs[gid0] = 1
        graph0 = fetch_graph(graphcur, gid0)
        src0 = srcdb.get(graph0.src)
        graph1 = fetch_graph(graphcur, gid1)
        src1 = srcdb.get(graph1.src)
        nodes0 = []
        nodes1 = []
        for (p,(nid0,nid1)) in enumerate(nids):
            nodes0.append(graph0.nodes[nid0])
            nodes1.append(graph1.nodes[nid1])
        if html:
            print('<div class=pair>%s nodes<br>' % len(nids))
            show_html(gid0, src0, nodes0, 's0')
            show_html(gid1, src1, nodes1, 's1')
            print('</div>')
        else:
            print('###', src0.name, len(nids))
            src0.show_nodes(nodes0)
            print('###', src1.name, len(nids))
            src1.show_nodes(nodes1)
            print()
    
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
