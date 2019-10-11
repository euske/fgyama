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

CONTEXT_CHOICES = (
    ('x','???'), ('a','Good'), ('b', 'Acceptable'), ('c','Bad'),
)

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

def getnewname(words, cands):
    wordidx = [ i for (i,w) in enumerate(cands) if w in words ]
    wordincands = [ w for w in words if w in cands ]
    for (i,w) in zip(wordidx, wordincands):
        cands[i] = w
    return tocamelcase(cands)

def tocamelcase(words):
    return ''.join(
        (w if i == 0 else w[0].upper()+w[1:])
        for (i,w) in enumerate(words) )

def overlap(src0, src1):
    (name0,b0,e0) = src0
    assert b0 <= e0
    (name1,b1,e1) = src1
    assert b1 <= e1
    return (name0 == name1 and b0 < e1 and b1 < e0)

def showchoices(choices):
    opts = [ f'<option value="{v}">{v}. {q(s)}</option>' for (v,s) in choices ]
    return f'<select>{"".join(opts)}</select>'

def showhtmlheader(out, title, script=None):
    out.write(f'''<!DOCTYPE html>
<html>
<meta charset="UTF-8" />
<title>{q(title)}</title>
<style>''' '''
h1 { border-bottom: 4px solid black; }
h2 { color: white; background: black; padding: 4px; }
h3 { border-bottom: 1px solid black; margin-top: 0.5em; }
pre { margin: 0 1em 1em 1em; outline: 1px solid gray; }
ul > li { margin-bottom: 0.5em; }
.mission { background: purple; color: white; }
.cat { outline: 1px dashed black; padding: 2px; background: #eeeeee; margin: 1em; }
.cand { margin: 1em; padding: 1em; outline: 2px solid black; }
.old { background: #ccccff; }
.old mark { color: white; background: blue; }
.new { background: #88ff88; }
.new mark { color: black; background: yellow; }
.match { background: #ffcccc; }
.match mark { color: white; background: red; }
.support { background: green; color: white; }
</style>
<script>
function toggle(id) {
  let e = document.getElementById(id);
  e.hidden = !e.hidden;
  return false;
}
</script>
''')
    if script is not None:
        out.write(f'<script>\n{script}\n</script>\n')
        out.write(f"<body onload=\"run('results', '{title}_eval')\">\n")
    else:
        out.write('<body>\n')
    out.write(f'<h1>Variable Rewrite Experiment: {title}</h1>\n')
    return

def main(argv):
    import getopt
    import fileinput
    def usage():
        print(f'usage: {argv[0]} [-o output] [-H|-E|-C] [-T title] [-S script] [-n limit] [-m maxcands] [-e simvars] [-c encoding] [-v vars] srcdb [namecon]')
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'o:HECT:S:n:m:e:c:v:')
    except getopt.GetoptError:
        return usage()
    output = None
    title = None
    script = None
    encoding = None
    mode = None
    excluded = set()
    types = {}
    limit = 10
    maxcands = 0
    timestamp = time.strftime('%Y%m%d')
    for (k, v) in opts:
        if k == '-o':
            output = v
        elif k == '-H': mode = k[1:]
        elif k == '-E': mode = k[1:]
        elif k == '-C': mode = k[1:]
        elif k == '-T':
            title = v
        elif k == '-S':
            with open(v) as fp:
                script = fp.read()
        elif k == '-n': limit = int(v)
        elif k == '-m': maxcands = int(v)
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
        print(f'Output: {output}', file=sys.stderr)
        out = open(output, 'w')

    def showsrc_plain(srcs, klass):
        annot = SourceAnnot(srcdb)
        for (path,s,e) in srcs:
            annot.add(path,s,e)
        annot.show_text(out, showline=lambda line: klass+' '+line)
        return

    def showsrc_html(srcs, klass, name=None):
        pat = None
        if name is not None:
            pat = re.compile(r'\b'+re.escape(name)+r'\b')
        annot = SourceAnnot(srcdb)
        for (path,s,e) in srcs:
            annot.add(path,s,e)
        out.write(f'<div class={klass} style="margin: 1em;">\n')
        def abody(annos, s):
            if pat is not None:
                s = pat.sub('xxx', s)
            return q(s.replace('\n',''))
        for (src,ranges) in annot:
            out.write(f'<div>{q(src.name)} <pre class=src>\n')
            for (lineno,line) in src.show(
                    ranges,
                    astart=lambda nid: '<mark>',
                    aend=lambda anno: '</mark>',
                    abody=abody):
                if lineno is None:
                    out.write('     '+line+'\n')
                else:
                    out.write(f'{lineno:5d}:{line}\n')
            out.write('</pre></div>\n')
        out.write('</div>\n')
        return

    def showsupports_html(rid, w, feats):
        for (sid, ((fscore,fid,feat),(srcs0,item1,srcs1))) in enumerate(feats):
            name1 = stripid(item1)
            out.write('<div class=cand>\n')
            out.write(f'<h3 class=support>Support ({sid}) for "<code>{w}</code>": <code class=new><mark>{name1}</mark></code> &nbsp; (<code>{feat}</code>)</h3>\n')
            showsrc_html(srcs1, 'new')
            id = f'{rid}_{w}_{sid}'
            out.write(f'<a href="javascript:void(0)" onclick="toggle(\'{id}\')">[+]</a> Show Proof<br><div id={id} hidden>\n')
            showsrc_html(srcs0, 'match')
            out.write('</div></div>\n')
        return

    def showrec_plain(rid, rec):
        item = rec['ITEM']
        name = stripid(item)
        out.write(f'*** {item!r}\n\n')
        out.write(f'{rec["SCORE"]} {name} {rec["CANDS"]}\n\n')
        srcs = dict(rec['SOURCE'])
        showsrc_plain(srcs[0], ' ')
        for (w, wscore, feats) in rec['SUPPORTS']:
            out.write(f'* {w}\n')
            for ((fscore,fid,feat),(srcs0,item1,srcs1)) in feats:
                out.write(f'+ {feat}\n')
                showsrc_plain(srcs1, 'E')
                showsrc_plain(srcs0, 'S')
        return

    def showrec_html(rid, rec):
        item = rec['ITEM']
        score = rec['SCORE']
        old = stripid(item)
        new = getnewname(rec['WORDS'], rec['CANDS'])
        assert old != new
        out.write(f'<h2>Rewrite {rid}: {old} &rarr; {new} ({score:.3f})</h2>\n')
        out.write(f'<h3><code class=old><mark>{old}</mark></code></h3>')
        srcs = dict(rec['SOURCE'])
        showsrc_html(srcs[0], 'old')
        for (w, wscore, feats) in rec['SUPPORTS']:
            showsupports_html(rid, w, feats)
        return

    def showrec_eval(rid, rec):
        item = rec['ITEM']
        score = rec['SCORE']
        base = rec.get('DEFAULT')
        old = stripid(item)
        new = getnewname(rec['WORDS'], rec['CANDS'])
        assert old != new
        names = [new, old]
        keys = ['a','b']
        if base is not None and new != base and old != base:
            names.append(base)
            keys.append('c')
        random.shuffle(keys)
        out.write(f'<h2>Rewrite {rid} ({score:.3f})</h2>\n')
        choices = [('x','???')] + list(sorted(zip(keys, names)))
        out.write(f'<div class=cat><span id="{rid}" class=ui>Choice: <code class=old><mark>xxx</mark></code> &rarr; {showchoices(choices)} &nbsp; Comment: <input size="30" /></span></div>\n')
        srcs = dict(rec['SOURCE'])
        showsrc_html(srcs[0], 'old', old)
        srcs0 = srcs[0]
        for (d,srcs1) in rec['SOURCE']:
            if d == 0: continue
            srcs1 = [ src1 for src1 in srcs1
                      if not any( overlap(src1,src0) for src0 in srcs0 ) ]
            if not srcs1: continue
            if d < 0:
                out.write(f'<h3>Source</h3>\n')
            else:
                out.write(f'<h3>Destination</h3>\n')
            showsrc_html(srcs1, 'new')
        print(rid, keys[0])
        return

    def showrec_context(rid, rec):
        item = rec['ITEM']
        old = stripid(item)
        new = getnewname(rec['WORDS'], rec['CANDS'])
        assert old != new
        out.write(f'<h2>Rewrite {rid}: <code class=old><mark>{old}</mark></code> &rarr; <code class=new><mark>{new}</mark></code></h2>\n')
        out.write(f'<div class=cat><span id="{rid}" class=ui>Choice: {showchoices(CONTEXT_CHOICES)}</select> &nbsp; Comment: <input size="30" /></span></div>\n')
        out.write(f'<h3><code class=old><mark>{old}</mark></code></h3>')
        srcs = dict(rec['SOURCE'])
        showsrc_html(srcs[0], 'old')
        for (w, wscore, feats) in rec['SUPPORTS']:
            showsupports_html(rid, w, feats)
        print(rid, rec['SCORE'], rec['RANK'])
        return

    #
    if mode is not None:
        if title is None:
            title = f'{args[0]}_{timestamp}'
        showhtmlheader(out, title, script)
    if mode == 'H':
        showrec = showrec_html
    elif mode == 'E':
        showrec = showrec_eval
        out.write('''
<h2 class=mission>Your Mission</h2>
<ul>
<li> For each <code class=old>blue</code> snippet, look at the variable
    <code class=old><mark>xxx</mark></code> and its use.
    Then look at the <code class=new>green</code> snippets for its Source and Destination
    <code class=new><mark>expressions</mark></code>.
<li> From the dropdown menu, choose which name best fits the code.
<li> If it's undecidable, you can skip it by choosing "<code>???</code>".
     However, try to think about it for at least one minute before skipping.
<li> Your choices are saved in the follwoing textbox:<br>
  <textarea id="results" cols="80" rows="4" spellcheck="false" autocomplete="off"></textarea><br>
  When finished, send the above content (from <code>#START</code> to <code>#END</code>) to
  the experiment organizer.<br>
<li> <u style="color:red;">Do not consult others about the code during this experiment.</u>
</ul>
''')
    elif mode == 'C':
        showrec = showrec_context
        out.write('''
<h2 class=mission>Your Mission</h2>
<ul>
<li> For each <code class=old>blue</code> snippet, look at the
    <code class=old><mark>variable</mark></code> and its proposed
    <code class=new><mark>rewrite</mark></code>.
<li> Look at a <span class=support>Support (0)</span> and determine
    if the rewrite is "<code>good</code>" or "<code>acceptable</code>".
    Click the <a href="javascript:void(0)">[+]</a> to see how it matches the original code.
    If the support is not convincing, try further looking at
    <span class=support>Support (1)</span> and <span class=support>Support (2)</span>.
    If none of them looks convincing, choose "<code>bad</code>".
<li> If it's undecidable, you can skip it by choosing "<code>???</code>".
     However, try to think about it for at least one minute before skipping.
<li> Your choices are saved in the follwoing textbox:<br>
  <textarea id="results" cols="80" rows="4" spellcheck="false" autocomplete="off"></textarea><br>
  When finished, send the above content (from <code>#START</code> to <code>#END</code>) to
  the experiment organizer.<br>
<li> <u style="color:red;">Do not consult others about the code during this experiment.</u>
</ul>
''')
    else:
        showrec = showrec_plain

    allrecs = []
    for path in args:
        recs = []
        with open(path) as fp:
            for rec in getrecs(fp):
                if rec['ITEM'] in excluded: continue
                if types and types.get(rec['ITEM']) not in TYPES: continue
                recs.append(rec)
        recs.sort(key=lambda rec:rec['SCORE'], reverse=True)
        for (i,rec) in enumerate(recs):
            rec['RANK'] = i/len(recs)
        if mode == 'C':
            n = len(recs)
            recs = [ recs[i*n//limit] for i in range(limit) ]
            random.shuffle(recs)
        else:
            recs = recs[:limit]
        allrecs.extend(recs)

    for (rid, rec) in enumerate(allrecs):
        showrec(f'R{rid:003d}', rec)

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
