#!/usr/bin/env python
import sys
import re
import math
import json
from srcdb import SourceDB, SourceAnnot
from srcdb import q
from getwords import stripid, splitwords

def getrecs(fp):
    rec = {}
    for line in fp:
        line = line.strip()
        if line.startswith('+'):
            (k,_,v) = line[1:].partition(' ')
            rec[k] = json.loads(v)
        elif not line:
            yield rec
            rec = {}
    return

def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-H] [-J script] [-e simvars] [-c encoding] srcdb [namecon]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'HJ:e:c:')
    except getopt.GetoptError:
        return usage()
    html = False
    script = None
    encoding = None
    excluded = set()
    out = sys.stdout
    for (k, v) in opts:
        if k == '-H': html = True
        elif k == '-J':
            with open(v) as fp:
                script = fp.read()
        elif k == '-e': 
            with open(v) as fp:
                for rec in getrecs(fp):
                    excluded.update(rec['ITEMS'])
        elif k == '-c': encoding = v
    
    if not args: return usage()
    path = args.pop(0)
    srcdb = SourceDB(path, encoding)

    def showtext(srcs, header=' '):
        annot = SourceAnnot(srcdb)
        for (path,s,e) in srcs:
            annot.add(path,s,e)
        annot.show_text(showline=lambda line: header+' '+line)
        return

    def showhtmlheaders():
        out.write('''<!DOCTYPE html>
<meta charset="UTF-8" />
<style>
h2 { color: white; background: black; padding: 4px; }
h3 { border-bottom: 1px solid black; margin-top: 0; }
pre { margin: 0 1em 1em 1em; border: 1px solid gray; }
.support { margin: 1em; padding: 1em; border: 2px solid black; }
.src { margin: 1em; background: #88ff88; }
.src0 { margin: 1em; background: #ffcccc; }
.src0 mark { color: white; background: red; }
.src1 { margin: 1em; background: #ccccff; }
.src1 mark { color: white; background: blue; }
</style>
<script>
function toggle(id) {
  let e = document.getElementById(id);
  e.hidden = !e.hidden;
}
</script>
''')

    def showhtml(srcs, klass='src'):
        annot = SourceAnnot(srcdb)
        for (path,s,e) in srcs:
            annot.add(path,s,e)
        out.write('<div class=%s>\n' % klass)
        for (src,ranges) in annot:
            url = src.name
            out.write('<div>%s <pre>\n' % q(src.name))
            for (lineno,line) in src.show(
                    ranges,
                    astart=lambda nid: '<mark>',
                    aend=lambda anno: '</mark>',
                    abody=lambda annos, s: q(s.replace('\n',''))):
                if lineno is None:
                    out.write('     '+line+'\n')
                else:
                    out.write('%5d:%s\n' % (lineno+1, line))
            out.write('</pre></div>\n')
        out.write('</div>\n')
        return
        
    if html:
        showhtmlheaders()
        
    fp = fileinput.input(args)
    recs = sorted(getrecs(fp), key=lambda rec:rec['SCORE'], reverse=True)
    for (i,rec) in enumerate(recs):
        item = rec['ITEM']
        if item in excluded: continue
        score = rec['SCORE']
        name = stripid(item)
        names = rec['NAMES']
        if html:
            out.write('<h2>Proposal %d: <code>%s</code> &rarr; <code>%s</code> (%.3f)</h2>\n' %
                      (i, q(name), q(' / '.join(names)), score))
            showhtml(rec['SOURCE'])
            for (j,(feat,srcs0,evidence,srcs1)) in enumerate(rec['SUPPORT']):
                out.write('<div class=support>\n')
                out.write('<h3>Support %d: <code>%s</code></h3>\n' %
                          (j, stripid(evidence)))
                showhtml(srcs1, 'src1')
                out.write('<h3>Evidence (<code>%s</code>)</h3>\n' % feat)
                showhtml(srcs0, 'src0')
                out.write('</div>\n')
        else:
            print('+', item)
            print(score, name, names)
            print()
            showtext(rec['SOURCE'])
            for (feat,srcs0,evidence,srcs1) in rec['SUPPORT']:
                print('-', feat)
                showtext(srcs0, 'S')
                print(evidence)
                showtext(srcs1, 'E')
            
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
