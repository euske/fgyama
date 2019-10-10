#!/usr/bin/env python
import sys
import re
import os.path
import json
import time
import random
from srcdb import SourceDB, SourceAnnot
from srcdb import q
from getwords import stripid

CHOICES = [
    ('x', '???'),
    ('a', 'MUST BE the same name'),
    ('b', 'CAN BE the same name'),
    ('c', 'MUST NOT BE the same name'),
]

def dummy(x): return ''

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


def showhtmlheaders(out, title, script=None):
    out.write(f'''<!DOCTYPE html>
<html>
<meta charset="utf-8" />
<title>{q(title)}</title>
<style>''' '''
h1 { border-bottom: 4px solid black; }
h2 { background: #ffccff; padding: 2px; }
h3 { background: #000088; color: white; padding: 2px; }
pre { margin: 0 1em 1em 1em; outline: 1px solid gray; }
ul > li { margin-bottom: 0.5em; }
.mission { background: green; color: white; }
.cat { outline: 1px dashed black; padding: 2px; background: #eeeeee; margin: 1em; }
.src0 { background:#eeffff; }
.src0 mark { background:#ff88ff; }
.src1 { background:#ffffee; }
.src1 mark { background:#44cc44; }
</style>
''')
    if script is not None:
        out.write(f'<script>\n{script}\n</script>\n')
        out.write(f"<body onload=\"run('results', '{title}_eval')\">\n")
    else:
        out.write('<body>\n')
    out.write(f'<h1>Similar Variable Tagging Experiment: {title}</h1>\n')
    return

def main(argv):
    import fileinput
    import getopt
    def usage():
        print(f'usage: {argv[0]} [-o output] [-T title] [-S script] [-t thresholld] [-n limit] [-R] [-c encoding] srcdb [pairs]')
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'o:T:S:t:n:Rc:')
    except getopt.GetoptError:
        return usage()
    output = None
    title = None
    script = None
    threshold = 0.9
    limit = 10
    randomized = False
    encoding = None
    timestamp = time.strftime('%Y%m%d')
    for (k, v) in opts:
        if k == '-o':
            output = v
        elif k == '-T':
            title = v
        elif k == '-S':
            with open(v) as fp:
                script = fp.read()
        elif k == '-t': threshold = float(v)
        elif k == '-n': limit = int(v)
        elif k == '-R': randomized = True
        elif k == '-c': encoding = v
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

    if title is None:
        title = f'{args[0]}_{timestamp}'
    showhtmlheaders(out, title, script)
    out.write('''
<h2 class=mission>Your Mission</h2>
<ul>
<li> For each code snippet, look at the variables marked as
    <code class=src0>aa</code> / <code class=src1>bb</code>
    and choose their relationship from the menu:
  <ol type=a>
  <li> They <code>MUST BE</code> the same name.
  <li> They <code>CAN BE</code> the same name.
  <li> They <code>MUST NOT BE</code> the same name.
  </ol>
<li> If it's undecidable, you can skip it by choosing "<code>???</code>".
     However, try to think about it for at least one minute before skipping.
<li> Your choices are saved in the follwoing textbox:<br>
  <textarea id="results" cols="80" rows="4" spellcheck="false" autocomplete="off"></textarea><br>
  When finished, send the above content (from <code>#START</code> to <code>#END</code>) to
  the experiment organizer.<br>
<li> <u style="color:red;">Do not consult others about the code during this experiment.</u>
</ul>
''')

    VARS = ['<mark>aa</mark>', '<mark>bb</mark>']
    OPTIONS = ''.join(
        f'<option value="{v}">{v}. {q(c)}</option>' for (v,c) in CHOICES)

    def showsrc(i, name, srcs):
        pat = re.compile(r'\b'+re.escape(name)+r'\b')
        annot = SourceAnnot(srcdb)
        for (path,s,e) in srcs:
            annot.add(path,s,e)
        for (src,ranges) in annot:
            out.write(f'<div>{q(src.name)} <pre class=src{i}>\n')
            def abody(annos, s):
                s = q(s.replace('\n',''))
                if annos:
                    s = pat.sub(VARS[i], s)
                return s
            for (lineno,line) in src.show(
                    ranges, astart=dummy, aend=dummy, abody=abody):
                if lineno is None:
                    out.write('     '+line+'\n')
                else:
                    out.write(f'{lineno:5d}:{line}\n')
            out.write('</pre></div>\n')
        return

    def showrec(rid, rec):
        out.write(f'<h3 class=pair>Pair {rid}</h3>\n')
        out.write(
            f'<div class=cat><span id="{rid}" class=ui>Choice: <select>{OPTIONS}</select> &nbsp; Comment: <input size="30" /></span></div>\n')
        for (i,(item,srcs)) in enumerate(zip(rec['ITEMS'], rec['SRCS'])):
            name = stripid(item)
            showsrc(i, name, srcs)
        if randomized:
            print(rid, rec['SIM'])
        return

    allrecs = []
    for path in args:
        with open(path) as fp:
            recs = [ rec for rec in getrecs(fp) if threshold <= rec['SIM'] ]
        recs.sort(key=lambda rec:rec['SIM'], reverse=True)
        if randomized:
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
