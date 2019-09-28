#!/usr/bin/env python
import sys
import re
import os.path
import stat
import json
import time
import math
import random
from srcdb import SourceDB, SourceAnnot
from srcdb import q
from getwords import stripid, splitwords

CHOICES = [
    'a:GOOD',
    'b:ACCEPTABLE',
    'c:BAD',
    'z:UNDECIDABLE'
]

def showhtmlheaders(out, title, script=None):
    out.write('''<!DOCTYPE html>
<meta charset="UTF-8" />
<style>
h1 { border-bottom: 4px solid black; }
h2 { color: white; background: black; padding: 4px; }
h3 { border-bottom: 1px solid black; margin-top: 0.5em; }
pre { margin: 0 1em 1em 1em; border: 1px solid gray; }
.support { margin: 1em; padding: 1em; border: 2px solid black; }
.old { background: #ccccff; }
.old mark { color: white; background: blue; }
.new { background: #88ff88; }
.match { background: #ffcccc; }
.match mark { color: white; background: red; }
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
    out.write(f'CHOICES={json.dumps(CHOICES)};\n')
    out.write('</script>\n')
    out.write('''<body onload="run('results', '{title}_eval', CHOICES)">
<h1>Variable Rewrite Experiment: {title}</h1>
<h2>Your Mission</h2>
<ul>
<li> For each <span class=old>blue</span> snippet, look at the <code class=old><mark>variable</mark></code>
    and choose if one of the <code class=new><mark>rewrites</mark></code> helps understanding the code.<br>
  Choose either:
  <ol type=a>
  <li> The rewrite is GOOD.
  <li> The rewrite is ACCEPTABLE.
  <li> The rewrite is BAD.
  </ol><ol type=a start="26">
  <li> UNDECIDABLE (cannot determine after <u>3 minutes</u>).
  </ol>
<li> Your choices are saved in the follwoing textbox:<br>
  <textarea id="results" cols="80" rows="4" spellcheck="false" autocomplete="off"></textarea><br>
  When finished, send the above content (from <code>#START</code> to <code>#END</code>) to
  the experiment organizer.<br>
<li> <u>Do not consult others about the code during this experiment.</u>
</ul>
'''.format(title=title))
    return

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
        print(f'usage: {argv[0]} [-o output] [-T title] [-S script] [-n limit] [-R] [-e simvars] [-c encoding] srcdb [namecon]')
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'o:T:S:n:Re:c:')
    except getopt.GetoptError:
        return usage()
    output = None
    html = False
    title = None
    script = None
    encoding = None
    randomized = False
    excluded = set()
    limit = 10
    timestamp = time.strftime('%Y%m%d')
    for (k, v) in opts:
        if k == '-o':
            output = v
            html = output.endswith('.html')
        elif k == '-T':
            title = v
        elif k == '-S':
            with open(v) as fp:
                script = fp.read()
        elif k == '-n': limit = int(v)
        elif k == '-R': randomized = True
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
            print(f'Already exists: {output!r}')
            return 1
        out = open(output, 'w')

    def showsrc(srcs, klass):
        annot = SourceAnnot(srcdb)
        for (path,s,e) in srcs:
            annot.add(path,s,e)
        if html:
            out.write(f'<div class={klass} style="margin: 1em;">\n')
            for (src,ranges) in annot:
                url = src.name
                out.write(f'<div>{q(src.name)} <pre>\n')
                for (lineno,line) in src.show(
                        ranges,
                        astart=lambda nid: '<mark>',
                        aend=lambda anno: '</mark>',
                        abody=lambda annos, s: q(s.replace('\n',''))):
                    if lineno is None:
                        out.write('     '+line+'\n')
                    else:
                        out.write(f'{lineno:5d}:{line}\n')
                out.write('</pre></div>\n')
            out.write('</div>\n')
        else:
            annot.show_text(out, showline=lambda line: klass+' '+line)
        return

    def showrec(title, rid, rec):
        item = rec['ITEM']
        score = rec['SCORE']
        name = stripid(item)
        if html:
            key = (f'R{title}_{rid:003d}')
            words = list(reversed(splitwords(name)))
            cands = [ k for (_,k) in rec['CANDS'] ]
            wordidx = [ i for (i,w) in enumerate(cands) if w in words ]
            wordincands = [ w for w in words if w in cands ]
            for (i,w) in zip(wordidx, wordincands):
                cands[i] = w
            def f(w):
                s = 'old' if w in words else 'new'
                return f'<code class={s}><mark>{q(w)}</mark></code>'
            old = ' / '.join( f(w) for w in words )
            new = ' / '.join( f(k) for k in cands )
            footer = f' &nbsp; ({score:.3f})'
            if randomized:
                footer = ''
                print(key, score)
            out.write(f'<h2>Proposal {rid}: {old} &rarr; {new}{footer}</h2>\n')
            if script is not None:
                out.write(f'<div class=cat><span id="{key}" class=ui></span></div>\n')
            out.write(f'<h3><code class=old><mark>{stripid(item)}</mark></code></h3>')
            showsrc(rec['SOURCE'], 'old')
            for (sid,(feat,srcs0,evidence,srcs1)) in enumerate(rec['SUPPORT']):
                out.write('<div class=support>\n')
                out.write(f'<h3><code class=new><mark>{stripid(evidence)}</mark></code> '
                          f'&nbsp; (<code>{feat}</code>)</h3>\n')
                showsrc(srcs1, 'new')
                id = f'S_{key}_{sid}'
                out.write(f'<a href="javascript:void(0)" onclick="toggle(\'{id}\')">[+]</a> Match<br>\n')
                out.write(f'<div id={id} hidden>\n')
                showsrc(srcs0, 'match')
                out.write('</div></div>\n')
        else:
            out.write(f'*** {item!r}\n\n')
            out.write(f'{score} {name} {rec["CANDS"]}\n\n')
            showsrc(rec['SOURCE'], ' ')
            for (feat,srcs0,evidence,srcs1) in rec['SUPPORT']:
                out.write(f'+ {evidence} {feat}\n')
                showsrc(srcs1, 'E')
                showsrc(srcs0, 'S')
        return

    #
    if html:
        if title is None:
            title = f'{args[0]}_{timestamp}'
        showhtmlheaders(out, title, script)

    for path in args:
        with open(path) as fp:
            recs = [ rec for rec in getrecs(fp) if rec['ITEM'] not in excluded ]
        for rec in recs:
            feats = rec['FEATS']
            score = sum( math.exp(-abs(feat[0]))*df*ff for (feat,df,ff) in feats )
            rec['SCORE'] = score
        recs.sort(key=lambda rec:rec['SCORE'], reverse=True)
        if randomized:
            n = len(recs)
            recs = [ recs[n*i//limit] for i in range(limit) ]
            random.shuffle(recs)
        elif limit:
            recs = recs[:limit]
        (name,_) = os.path.splitext(os.path.basename(path))
        for (rid,rec) in enumerate(recs):
            showrec(name, rid, rec)

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
