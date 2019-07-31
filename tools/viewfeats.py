#!/usr/bin/env python
import sys
import re
from srcdb import SourceDB, SourceAnnot

NAME = re.compile(r'\w+$', re.U)
def stripid(name):
    m = NAME.search(name)
    if m:
        return m.group(0)
    else:
        return None

WORD = re.compile(r'[a-z]+[A-Z]?|[A-Z]+|[0-9]+')
def splitwords(s):
    """
    >>> splitwords('name')
    ['name']
    >>> splitwords('this_is_name_!')
    ['name', 'is', 'this']
    >>> splitwords('thisIsName')
    ['name', 'is', 'this']
    >>> splitwords('SomeXMLStuff')
    ['stuff', 'xml', 'some']
"""
    if s is None: return []
    n = len(s)
    r = ''.join(reversed(s))
    return [ s[n-m.end(0):n-m.start(0)].lower() for m in WORD.finditer(r) ]

# main
def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-d] -B srcdb path [feats ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'dc:B:n:')
    except getopt.GetoptError:
        return usage()
    debug = 0
    encoding = None
    srcdb = None
    ntop = 10
    for (k, v) in opts:
        if k == '-d': debug += 1
        elif k == '-c': encoding = v
        elif k == '-B': srcdb = SourceDB(v, encoding)
        elif k == '-n': ntop = int(v)
    if not args: return usage()
    if srcdb is None: return usage()

    path = args.pop(0)
    print('Loading: %r...' % path, file=sys.stderr)

    srcmap = {}
    itemsrcs = { w:set() for w in args if not w.startswith('+') }
    wordsrcs = { w[1:]:{} for w in args if w.startswith('+') }
    srcs = {}
    with open(path) as fp:
        for line in fp:
            if line.startswith('+SOURCE'):
                (_,_,line) = line.partition(' ')
                (srcid, path) = eval(line)
                srcmap[srcid] = path

            elif line.startswith('! '):
                data = eval(line[2:])
                srcs = None
                if data[0] == 'REF':
                    item = data[1]
                    name = stripid(item)
                    if name in itemsrcs:
                        itemsrcs[name].update(data[2:])
                    for w in splitwords(name):
                        if w in wordsrcs:
                            srcs = wordsrcs[w]
                            break

            elif srcs is not None and line.startswith('+ '):
                data = eval(line[2:])
                feat = data[0:4]
                if feat in srcs:
                    a = srcs[feat]
                else:
                    a = srcs[feat] = set()
                a.update(data[4:])

            else:
                srcs = None

    for (item,srcs) in itemsrcs.items():
        print('#', item)
        annot = SourceAnnot(srcdb)
        for src in srcs:
            if src is None: continue
            (srcid,start,end) = src
            annot.add(srcmap[srcid], start, end)
        annot.show_text()

    for (word,srcs) in wordsrcs.items():
        feats = sorted(srcs.items(), key=lambda x: len(x[1]), reverse=True)
        feats = feats[:ntop]
        print('#', '+'+word, [ f for (f,_) in feats ])
        annot = SourceAnnot(srcdb)
        for (f,srcs) in feats:
            for src in srcs:
                if src is None: continue
                (srcid,start,end) = src
                annot.add(srcmap[srcid], start, end)
        annot.show_text()

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
