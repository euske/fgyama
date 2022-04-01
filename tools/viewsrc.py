#!/usr/bin/env python
import sys
import logging
from graphs import get_graphs
from srcdb import SourceFile, q

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

    def astart(nid):
        return f'<span class="p{nid}">'
    def aend(anno):
        return '</span>'
    def abody(annos, s):
        return q(s.replace('\n',''))
    for path in args:
        assert path in file2ranges
        with open(path) as fp:
            data = fp.read()
        src = SourceFile(path, data)
        ranges = file2ranges[path]
        for (lineno, line) in src.chunk(ranges):
            if lineno is None: continue
            print(lineno, line)

    return

if __name__ == '__main__': sys.exit(main(sys.argv))
