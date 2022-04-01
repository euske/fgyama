#!/usr/bin/env python
import sys
import logging
from graphs import get_graphs
from srcdb import SourceFile, q, show_html_headers

def main(argv):
    import getopt
    def usage():
        print(f'usage: {argv[0]} [-d] graph [path ...] ')
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'd')
    except getopt.GetoptError:
        return usage()
    level = logging.INFO
    for (k, v) in opts:
        if k == '-d': level = logging.DEBUG
    logging.basicConfig(format='%(asctime)s %(levelname)s %(message)s', level=level)

    if not args: return usage()

    path = args.pop(0)
    file2ranges = {}
    for method in get_graphs(path):
        k = method.klass.path
        if k in file2ranges:
            a = file2ranges[k]
        else:
            a = file2ranges[k] = []
        for node in method:
            if node.ast is None: continue
            (_,s,e) = node.ast
            a.append((s,e,node.nid))

    print('<!DOCTYPE html><html><meta charset="UTF-8"><head>')
    show_html_headers()
    print('<body><pre>')
    out = sys.stdout
    for path in args:
        assert path in file2ranges
        with open(path) as fp:
            data = fp.read()
        src = SourceFile(path, data)
        ranges = file2ranges[path]
        annos = []
        for (lineno, chunks) in src.chunk(ranges):
            if lineno is None: continue
            buf = ''
            for (v,anno,s) in chunks:
                if v < 0:
                    annos.append(anno)
                    buf += f'<span class="p{len(annos)}" title="{anno}">'
                elif 0 < v:
                    buf += '</span>'
                    del annos[-1]
                else:
                    buf += q(s)
            out.write(buf)
    return

if __name__ == '__main__': sys.exit(main(sys.argv))
