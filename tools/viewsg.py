#!/usr/bin/env python
import sys
import sqlite3
from graph import SourceDB, DFGraph
from graph import fetch_graph

def q(s):
    return s.replace('&','&amp;').replace('>','&gt;').replace('<','&lt;').replace('"','&quot;')

def show_html_headers():
    print('''<html>
<style>
pre { margin: 1em; border: 1px solid gray;}
.pair { border: 2px solid black; margin: 1em; }
.head { font-size: 120%; font-weight: bold; }
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

def show_html(gid, src, url, nodes, klass=''):
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

def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-B basedir] [-U urls.db] [-H] '
              'graph.db sg.out' %
              argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'B:U:H')
    except getopt.GetoptError:
        return usage()
    srcdb = None
    urlsname = None
    html = False
    for (k, v) in opts:
        if k == '-B': srcdb = SourceDB(v)
        elif k == '-U': urlsname = v
        elif k == '-H': html = True
    if not args: return usage()

    urlsconn = urlscur = None
    if urlsname is not None:
        urlsconn = sqlite3.connect(urlsname)
        urlscur = urlsconn.cursor()
    
    graphname = args.pop(0)
    graphconn = sqlite3.connect(graphname)
    graphcur = graphconn.cursor()
    if html:
        show_html_headers()

    def geturl(name):
        if urlscur is None:
            return name
        rows = urlscur.execute('SELECT URL FROM SourceURL WHERE FileName=?;', (name,))
        (url,) = rows.fetchone()
        return url

    npairs = 0
    # "- gid0 gid1 nodes depth branch pairs key ..."
    for line in fileinput.input(args):
        line = line.strip()
        if not line.startswith('-'): continue
        f = line.split(' ')
        (gid0, gid1, nodes, depth, branch) = map(int, f[1:6])
        graph0 = fetch_graph(graphcur, gid0)
        graph1 = fetch_graph(graphcur, gid1)
        src0 = srcdb.get(graph0.src)
        src1 = srcdb.get(graph1.src)
        nodes0 = []
        nodes1 = []
        nids = []
        for v in f[6].split(','):
            (nid0,_,nid1) = v.partition(':')
            nodes0.append(graph0.nodes[int(nid0)])
            nodes1.append(graph1.nodes[int(nid1)])
        if html:
            print('<div class=pair><span class=head>%s Pair %d:</span> (nodes=%d, depth=%d, branch=%d)<br>' % \
                  (f[0], npairs, nodes, depth, branch))
            show_html(gid0, src0, geturl(src0.name), nodes0, 's0')
            show_html(gid1, src1, geturl(src1.name), nodes1, 's1')
            print('</div>')
        else:
            print('###', src0.name, len(nids))
            src0.show_nodes(nodes0)
            print('###', src1.name, len(nids))
            src1.show_nodes(nodes1)
            print()
        npairs += 1
    
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
