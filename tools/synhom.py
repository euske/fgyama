#!/usr/bin/env python
import sys
import json
from getwords import stripid, splitwords

debug = 0

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

def getnewname(words, cands):
    wordidx = [ i for (i,w) in enumerate(cands) if w in words ]
    wordincands = [ w for w in words if w in cands ]
    for (i,w) in zip(wordidx, wordincands):
        cands[i] = w
    return tocamelcase(cands)

# main
def main(argv):
    global debug
    import fileinput
    import getopt
    def usage():
        print(f'usage: {argv[0]} [-d] [-n limit] [namecon ...]')
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'dn:')
    except getopt.GetoptError:
        return usage()

    limit = 20
    for (k, v) in opts:
        if k == '-d': debug += 1
        elif k == '-n': limit = int(v)
    if not args: return usage()

    for path in args:
        with open(path) as fp:
            recs = sorted(getrecs(fp), key=lambda rec: rec['SCORE'], reverse=True)
            
        new4olds = {}
        old4news = {}
        def add(d, x, y):
            if x in d:
                a = d[x]
            else:
                a = d[x] = set()
            a.add(y)
            return

        for rec in recs[:limit]:
            item = rec['ITEM']
            cands = rec['CANDS']
            old = stripid(item)
            new = getnewname(rec['WORDS'], cands)
            add(new4olds, new, old)
            add(old4news, old, new)

        print(path)
        for (new,olds) in new4olds.items():
            if len(olds) < 2: continue
            print(f' synonym: {olds} -> {new}')
        for (old,news) in old4news.items():
            if len(news) < 2: continue
            print(f' homonym: {news} <- {old}')
        print()
    return

if __name__ == '__main__': sys.exit(main(sys.argv))
