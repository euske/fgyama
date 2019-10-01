#!/usr/bin/env python
import sys
import re
import os.path
import json
import time
import math
import random
from srcdb import SourceDB, SourceAnnot
from srcdb import q
from getwords import stripid, splitwords

TYPES = {
    'B', 'C', 'S', 'I', 'J', 'F', 'D', 'Z',
    'Ljava/lang/String;'
}

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

def getvars(fp):
    types = {}
    for line in fp:
        line = line.strip()
        if not line: continue
        (v,_,t) = line.partition(' ')
        types[v] = t
    return types

def tocamelcase(words):
    return ''.join(
        (w if i == 0 else w[0].upper()+w[1:])
        for (i,w) in enumerate(words) )

def showhtmlheaders(out, title, script=None):
    out.write('''<!DOCTYPE html>
<html>
<meta charset="UTF-8" />
<title>%s</title>
<style>
h1 { border-bottom: 4px solid black; }
h2 { color: white; background: black; padding: 4px; }
h3 { border-bottom: 1px solid black; margin-top: 0.5em; }
pre { margin: 0 1em 1em 1em; border: 1px solid gray; }
ul > li { margin-bottom: 0.5em; }
.cat { border: 1px solid black; padding: 2px; background: #eeeeee; margin: 1em; }
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
''' % q(title))
    if script is None:
        out.write('<body>\n')
        return
    out.write(f'<script>\n{script}\n</script>\n')
    out.write('''<body onload="run('results', '{title}_eval')">
<h1>Variable Rewrite Experiment: {title}</h1>
<h2>Your Mission</h2>
<ul>
<li> For each <span class=old>blue</span> snippet, look at the <code class=old><mark>variable</mark></code>
    and choose which candidate best explains the working of the code.
<li> The proposed <code class=new><mark>name</mark></code> is highlighted yellow.
<li> Click the <a href="javascript:void(0)">[+]</a> to see the <span class=new>supports</span> of the rewrite.
<li> If there's not enough information, choose <code>???</code>.
<li> Your choices are saved in the follwoing textbox:<br>
  <textarea id="results" cols="80" rows="4" spellcheck="false" autocomplete="off"></textarea><br>
  When finished, send the above content (from <code>#START</code> to <code>#END</code>) to
  the experiment organizer.<br>
<li> <u>Do not consult others about the code during this experiment.</u>
</ul>
'''.format(title=q(title)))
    return

def main(argv):
    import getopt
    def usage():
        print(f'usage: {argv[0]} [-o output] [-T title] [-S script] [-n limit] [-m supports] [-R] [-e simvars] [-c encoding] [-v vars] srcdb [namecon]')
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'o:T:S:n:m:Re:c:v:')
    except getopt.GetoptError:
        return usage()
    output = None
    html = False
    title = None
    script = None
    encoding = None
    randomized = False
    excluded = set()
    types = {}
    limit = 10
    maxsupports = 0
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
        elif k == '-m': maxsupports = int(v)
        elif k == '-R': randomized = True
        elif k == '-e':
            with open(v) as fp:
                for rec in getrecs(fp):
                    excluded.update(rec['ITEMS'])
        elif k == '-c': encoding = v
        elif k == '-v':
            with open(v) as fp:
                types = getvars(fp)
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

    def showrec(rid, rec):
        item = rec['ITEM']
        if types and types.get(item) not in TYPES: return False
        score = rec['SCORE']
        base = rec.get('DEFAULT')
        name = stripid(item)
        supports = rec['SUPPORT'][:maxsupports]
        if html:
            key = (f'R{rid:003d}')
            words = list(reversed(splitwords(name)))
            cands = [ k for (_,k) in rec['CANDS'] ]
            wordidx = [ i for (i,w) in enumerate(cands) if w in words ]
            wordincands = [ w for w in words if w in cands ]
            for (i,w) in zip(wordidx, wordincands):
                cands[i] = w
            old = tocamelcase(words)
            new = tocamelcase(cands)
            assert old != new
            if randomized:
                if base == new or base == old: return False
                if random.random() < 0.5:
                    choices = [('a',new), ('b',base)]
                    print(key, 'a')
                else:
                    choices = [('a',base), ('b',new)]
                    print(key, 'b')
            out.write(f'<h2>Proposal {rid}: {old} ({score:.3f})</h2>\n')
            if script is not None:
                choices = [('x','???')] + choices + [('z',old)]
                options = ''.join(
                    f'<option value="{v}">{v}. {q(c)}</option>' for (v,c) in choices)
                out.write(
                    f'<div class=cat><span id="{key}" class=ui>Choice: <code class=old><mark>{old}</mark></code> &rarr; <select>{options}</select> &nbsp; Comment: <input size="30" /></span></div>\n')
            out.write(f'<h3><code class=old><mark>{stripid(item)}</mark></code></h3>')
            showsrc(rec['SOURCE'], 'old')
            for (sid,(feat,srcs0,evidence,srcs1)) in enumerate(supports):
                out.write('<div class=support>\n')
                out.write(
                    f'<h3>Support {sid+1}: <code class=new><mark>{stripid(evidence)}</mark></code> &nbsp; (<code>{feat}</code>)</h3>\n')
                showsrc(srcs1, 'new')
                id = f'{key}_{sid}'
                out.write(
                    f'<a href="javascript:void(0)" onclick="toggle(\'{id}\')">[+]</a> Match<br><div id={id} hidden>\n')
                showsrc(srcs0, 'match')
                out.write('</div></div>\n')
        else:
            out.write(f'*** {item!r}\n\n')
            out.write(f'{score} {name} {rec["CANDS"]}\n\n')
            showsrc(rec['SOURCE'], ' ')
            for (feat,srcs0,evidence,srcs1) in supports:
                out.write(f'+ {evidence} {feat}\n')
                showsrc(srcs1, 'E')
                showsrc(srcs0, 'S')
        return True

    #
    if html:
        if title is None:
            title = f'{args[0]}_{timestamp}'
        showhtmlheaders(out, title, script)

    rid = 0
    for path in args:
        with open(path) as fp:
            recs = [ rec for rec in getrecs(fp) if rec['ITEM'] not in excluded ]
        for rec in recs:
            feats = rec['FEATS']
            score = sum( math.exp(-abs(feat[0]))*df*ff for (feat,df,ff) in feats )
            rec['SCORE'] = score
        recs.sort(key=lambda rec:rec['SCORE'], reverse=True)
        n = limit
        for rec in recs:
            if showrec(rid, rec):
                rid += 1
                n -= 1
            if n == 0: break

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
