#!/usr/bin/env python
import sys
from srcdb import SourceDB
from graph import get_graphs

def q(s):
    return s.replace('&','&amp;').replace('>','&gt;').replace('<','&lt;').replace('"','&quot;')

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

def show_html(src, nodes):
    d = {}
    for (i,n) in enumerate(nodes):
        d[n.nid] = i
    def astart(nid):
        return '<span class="p%s">' % d[nid]
    def aend(anno):
        return '</span>'
    def abody(annos, s):
        return q(s.replace('\n',''))
    print('<div class=src>%s: </div>' % (src.name))
    print('<pre>')
    for (lineno,s) in src.show_nodes(nodes, astart=astart, aend=aend, abody=abody):
        if lineno is None:
            print ('     '+s)
        else:
            lineno += 1
            print ('%5d:%s' % (lineno, s))
    print('</pre>')
    return

def isiter(ref, n_begin, n_end):
    #print (ref, n_begin, n_end)
    def isref(n):
        if n.ref == ref:
            return True
        else:
            for (label,src) in n.get_inputs():
                if isref(src):
                    return True
            return False
    def isisolated(n):
        if n is n_begin:
            return True
        elif n.kind == 'begin':
            return False
        elif n.kind == 'end' and n is not n_end:
            return True
        else:
            for (label,src) in n.get_inputs():
                if not isisolated(src):
                    return False
            return True
    cond = n_end.inputs['cond']
    return isref(cond) and isisolated(n_end)

def finditer(graph):
    refs = set()
    for node in graph.nodes.values():
        if node.kind == 'begin':
            n_end = node.inputs['_repeat']
            ref = node.ref
            if isiter(ref, node, n_end):
                refs.add(ref)
    return refs

def main(argv):
    import getopt
    def usage():
        print('usage: %s [-B basedir] [-H] [graph ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'vB:H')
    except getopt.GetoptError:
        return usage()
    verbose = False
    srcdb = None
    html = False
    for (k, v) in opts:
        if k == '-v': verbose = True
        elif k == '-B': srcdb = SourceDB(v)
        elif k == '-H': html = True
    if not args: return usage()

    if html:
        show_html_headers()
    for graph in get_graphs(args.pop(0)):
        src = None
        if srcdb is not None:
            try:
                src = srcdb.get(graph.src)
            except KeyError:
                pass
        for ref in finditer(graph):
            if src is not None:
                nodes = [ node for node in graph.nodes.values() if node.ref == ref ]
                if html:
                    show_html(src, nodes)
                else:
                    print (src, graph, ref)
                    src.show_nodes(nodes)
                print()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
