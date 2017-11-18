#!/usr/bin/env python
import sys
import os.path
from srcdb import SourceDB, SourceMap

def q(s):
    return s.replace('&','&amp;').replace('>','&gt;').replace('<','&lt;').replace('"','&quot;')

def show_html_headers():
    print('''<html>
<style>
pre { margin: 1em; background: #eeeeee; }
.head { font-size: 75%; font-weight: bold; }
.src { margin: 8px; padding: 4px; border: 2px solid gray; }
.key { font-weight: bold; }
</style>
<body>
''')        
    return

def show(index, src, start, end, key, url=None, ncontext=3):
    ranges = [(start, end, None)]
    if url is None:
        print('# %s:' % index)
        print('@ %s %d %d key=%s' % (src.name, start, end, key))
        for (_,line) in src.show(ranges, ncontext=ncontext):
            print('  '+line, end='')
        print()
    else:
        def astart(anno):
            return '<mark>'
        def aend(anno):
            return '</mark>'
        def abody(annos, s):
            return q(s.replace('\n',''))
        name = os.path.basename(src.name)
        print('<div class=src><div class=head>%s: <a href="%s">%s</a></div>' %
              (index, q(url), name))
        print('<div class=key>key=%s</div>' % (q(key)))
        print('<pre>')
        for (lineno,s) in src.show(ranges, astart=astart, aend=aend, abody=abody,
                                   ncontext=ncontext):
            if lineno is None:
                print ('     '+s)
            else:
                lineno += 1
                print ('<a href="%s#L%d">%5d</a>:%s' %
                       (q(url), lineno, lineno, s))
        print('</pre></div>')
    return

def get_props(a):
    d = {}
    for x in a:
        (k,_,v) = x.partition('=')
        d[k] = v
    return d

def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-B basedir] [-M srcmap.db] [-H] '
              '[-c context] comm.out' %
              argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'B:M:Hc:')
    except getopt.GetoptError:
        return usage()
    srcdb = None
    srcmap = None
    html = False
    ncontext = 3
    for (k, v) in opts:
        if k == '-B': srcdb = SourceDB(v)
        elif k == '-M': srcmap = SourceMap(v)
        elif k == '-H': html = True
        elif k == '-c': ncontext = int(v)
    if not args: return usage()

    if html:
        show_html_headers()
    
    # "+ path.java"
    # "- 2886 2919 type=LineComment parent=Block ..."
    src = None
    index = 0
    for line in fileinput.input(args):
        line = line.strip()
        if line.startswith('+'):
            (_,_,name) = line.strip().partition(' ')
            src = srcdb.get(name)
        elif line.startswith('-'):
            assert src is not None
            f = line.split(' ')
            start = int(f[1])
            end = int(f[2])
            props = get_props(f[3:])
            key = props.get('key', 'XXX')
            show(index, src, start, end, key, ncontext=ncontext)
            index += 1
            
        elif line.startswith('@'):
            f = line.split(' ')
            name = f[1]
            start = int(f[2])
            end = int(f[3])
            props = get_props(f[3:])
            key = props.get('key', 'XXX')
            src = srcdb.get(name)
            url = None
            if html and srcmap is not None:
                url = srcmap.geturl(name)
            show(index, src, start, end, key, url=url, ncontext=ncontext)
            index += 1
            
        elif line:
            raise ValueError(line)
    
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
