#!/usr/bin/env python
import sys
import os.path
import re
from srcdb import SourceDB, SourceAnnot
from srcdb import show_html_headers

BASEDIR = os.path.dirname(__file__)
dummy = (lambda _: '')


def q(s):
    return (s.replace('&','&amp;')
            .replace('>','&gt;')
            .replace('<','&lt;')
            .replace('"','&quot;'))

NAME = re.compile(r'\w+$', re.U)
def stripid(name):
    m = NAME.search(name)
    if m:
        return m.group(0)
    else:
        return None

def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-o output] [-c encoding] srcdb name '
              'out.pairs ...' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'o:c:B:')
    except getopt.GetoptError:
        return usage()
    out = sys.stdout
    encoding = None
    for (k, v) in opts:
        if k == '-o': out = open(v, 'w')
        elif k == '-c': encoding = v

    if not args: return usage()
    path = args.pop(0)
    srcdb = SourceDB(path, encoding)
    if not args: return usage()
    name = args.pop(0)

    out.write('''<!DOCTYPE html>
<html>
<meta charset="utf-8" />
<style>
h1 { background: #88ffff; padding: 2px; }
h2 { background: #ffccff; padding: 2px; }
h3 { background: #000088; color: white; padding: 2px; }
.cat { border: 1px solid black; padding: 2px; background: #eeeeee; margin: 1em; }
.result { }
.src0 { background:#eeffff; }
.var0 { background:#ff88ff; }
.src1 { background:#ffffee; }
.var1 { background:#448844; }
</style>
''')
    show_html_headers(out)
    with open(os.path.join(BASEDIR, 'helper.js')) as fp:
        out.write('<script>')
        out.write(fp.read())
        out.write('</script>')
    out.write('''<body onload="run('{fieldname}', 'results')">
<h1>Similar Variable Tagging Experiment: {title}</h1>
'''.format(fieldname='data_'+name, title=name))
    out.write('''
<h2>Your Mission</h2>
<ul>
<li> For each code snippet, look at the variable marked as
    <code class=var0>XXX</code> / <code class=var1>XXX</code>
    and choose their relationship from the menu.
  <ol type=a>
  <li> They <code>MUST HAVE</code> the same name.
  <li> They <code>CAN HAVE</code> the same name.
  <li> They <code>MUST NOT HAVE</code> the same name.
  </ol>
<li> When it's undecidable after <u>3 minutes</u> with the given snippet,
 choose <code>UNDECIDABLE</code>.
<li> <u>Do not consult others about the code during this experiment.</u>
<li> Your choices are saved in the follwoing textbox:<br>
  <textarea id="results" cols="80" rows="4" spellcheck="false" autocomplete="off"></textarea><br>
  When finished, send the above content (from <code>#START</code> to <code>#END</code>) to
  the experiment organizer.<br>
</ul>

<h2>Problems</h2>
''')
    for line in fileinput.input(args):
        data = eval(line.strip())
        index = data[0]
        pid = 'p%03d' % index
        out.write('<h3 class=pair>Pair %d</h3>\n' % index)
        out.write('<div class=cat>Category: <span id="%s" class=ui> </span></div>\n' % (pid))
        for (i,(item,type,srcs)) in enumerate(data[2:]):
            name = stripid(item)
            pat = re.compile(r'\b'+re.escape(name)+r'\b')
            annot = SourceAnnot(srcdb)
            for (path, start, end) in srcs:
                annot.add(path, start, end, 0)
            for (src,ranges) in annot.nodes.items():
                out.write('<pre class=src%d>\n' % i)
                def abody(annos, s):
                    s = q(s.replace('\n',''))
                    if annos:
                        s = pat.sub('<span class=var%d>XXX</span>' % i, s)
                    return s
                for (lineno,s) in src.show(ranges, astart=dummy, aend=dummy, abody=abody):
                    if lineno is None:
                        out.write('     '+s+'\n')
                    else:
                        lineno += 1
                        out.write('%5d:%s\n' % (lineno, s))
                out.write('</pre>\n')
    out.write('''
<hr><address>Yusuke Shinyama</address>
''')
    if out is not sys.stdout:
        out.close()

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
