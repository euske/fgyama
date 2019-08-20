#!/usr/bin/env python
import sys
import re
import os.path
import json
import math
import random
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

def tocamelcase(words):
    return ''.join(
        (w if i == 0 else w[0].upper()+w[1:])
        for (i,w) in enumerate(words) )

def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-o output] [-J script] [-n limit] [-S] [-e simvars] [-c encoding] srcdb [namecon]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'o:J:n:Se:c:')
    except getopt.GetoptError:
        return usage()
    output = None
    html = False
    script = None
    encoding = None
    shuffle = False
    excluded = set()
    limit = 10
    for (k, v) in opts:
        if k == '-o':
            output = v
            html = output.endswith('.html')
        elif k == '-J':
            with open(v) as fp:
                script = fp.read()
        elif k == '-n': limit = int(v)
        elif k == '-S': shuffle = True
        elif k == '-e':
            with open(v) as fp:
                for rec in getrecs(fp):
                    excluded.update(rec['ITEMS'])
        elif k == '-c': encoding = v
    if not args: return usage()
    path = args.pop(0)
    srcdb = SourceDB(path, encoding)

    out = sys.stdout
    if output is not None:
        if os.path.exists(output):
            print('Already exists: %r' % output)
            return 1
        out = open(output, 'w')

    def showhtmlheaders(title):
        out.write('''<!DOCTYPE html>
<meta charset="UTF-8" />
<style>
h1 { border-bottom: 4px solid black; }
h2 { color: white; background: black; padding: 4px; }
h3 { border-bottom: 1px solid black; margin-top: 0; }
pre { margin: 0 1em 1em 1em; border: 1px solid gray; }
.support { margin: 1em; padding: 1em; border: 2px solid black; }
.src { background: #88ff88; }
.src0 { background: #ffcccc; }
.src0 mark { color: white; background: red; }
.src1 { background: #ccccff; }
.src1 mark { color: white; background: blue; }
</style>
<script>
function toggle(id) {
  let e = document.getElementById(id);
  e.hidden = !e.hidden;
  return false;
}
</script>
''')
        if script is None:
            out.write('<body>\n')
            return
        out.write('<script>\n')
        out.write(script)
        out.write('</script>\n')
        out.write('''<body onload="run('{title}', 'results')">
<h1>Variable Rewrite Experiment: {title}</h1>
<h2>Your Mission</h2>
<ul>
<li> For each <span class=src>green</span> snippet, look at the <code class=src><mark>variable</mark></code>
    and choose if one of the <code class=src1><mark>rewrites</mark></code> improves the ease of code understanding.
  <ol type=a>
  <li> The rewrite is GOOD.
  <li> The rewrite is ACCEPTABLE.
  <li> The rewrite is BAD.
  </ol>
<li> When it's undecidable after <u>3 minutes</u> with the given snippet,
 choose UNDECIDABLE.
<li> <u>Do not consult others about the code during this experiment.</u>
<li> Your choices are saved in the follwoing textbox:<br>
  <textarea id="results" cols="80" rows="4" spellcheck="false" autocomplete="off"></textarea><br>
  When finished, send the above content (from <code>#START</code> to <code>#END</code>) to
  the experiment organizer.<br>
</ul>
'''.format(title=title))

    def showsrc(srcs, klass):
        annot = SourceAnnot(srcdb)
        for (path,s,e) in srcs:
            annot.add(path,s,e)
        if html:
            out.write('<div class=%s style="margin: 1em;">\n' % klass)
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
        else:
            annot.show_text(out, showline=lambda line: klass+' '+line)
        return

    def showrec(title, rid, rec):
        item = rec['ITEM']
        score = rec['SCORE']
        name = stripid(item)
        names = rec['NAMES']
        if html:
            key = ('R%003d' % rid)
            s = ' / '.join( '<code class=src1><mark>%s</mark></code>' % q(n) for n in names )
            if shuffle:
                print(title, key, score)
            else:
                s += ' &nbsp; (%.3f)' % score
            out.write('<h2>Proposal %d: <code class=src><mark>%s</mark></code> &rarr; %s</h2>\n' %
                      (rid, q(name), s))
            out.write('<div class=cat>Category: <span id="%s" class=ui> </span></div>\n' % (key))
            showsrc(rec['SOURCE'], 'src')
            for (sid,(feat,srcs0,evidence,srcs1)) in enumerate(rec['SUPPORT']):
                out.write('<div class=support>\n')
                out.write('<h3>Support %d: <code>%s</code> &nbsp; (<code>%s</code>)</h3>\n' %
                          (sid, stripid(evidence), feat))
                showsrc(srcs1, 'src1')
                id = ('S_%s_%s' % (rid,sid))
                out.write('<a href="javascript:void(0)" onclick="toggle(\'%s\')">[+]</a>\n' % id)
                out.write('<div id=%s hidden>\n' % id)
                out.write('<h3>Original</h3>\n' % feat)
                showsrc(srcs0, 'src0')
                out.write('</div></div>\n')
        else:
            out.write('+ %r\n' % item)
            out.write('%r %r %r\n\n' % (score, name, names))
            showsrc(rec['SOURCE'], ' ')
            for (feat,srcs0,evidence,srcs1) in rec['SUPPORT']:
                out.write('- %r %r\n' % (evidence, feat))
                showsrc(srcs1, 'E')
                showsrc(srcs0, 'S')
        return

    #
    for path in args:
        with open(path) as fp:
            fp = fileinput.input(args)
            recs = [ rec for rec in getrecs(fp) if rec['ITEM'] not in excluded ]
        (title,_) = os.path.splitext(os.path.basename(path))
        if html:
            showhtmlheaders(title)
        if shuffle:
            random.shuffle(recs)
        else:
            recs.sort(key=lambda rec:rec['SCORE'], reverse=True)
        if limit:
            recs = recs[:limit]
        for (rid,rec) in enumerate(recs):
            showrec(title, rid, rec)

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
