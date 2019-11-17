#!/usr/bin/env python
import sys
import os.path
import json

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

def main(argv):
    import getopt
    import fileinput
    def usage():
        print(f'usage: {argv[0]} [-n limit] [-e simvars] namecon ...')
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'n:e:')
    except getopt.GetoptError:
        return usage()
    excluded = set()
    limit = 0
    for (k, v) in opts:
        if k == '-n': limit = int(v)
        elif k == '-e':
            with open(v) as fp:
                for rec in getrecs(fp):
                    excluded.update(rec['ITEMS'])

    results0 = None
    for path in args:
        if path == '-':
            results0 = None
            continue
        recs = []
        with open(path) as fp:
            for rec in getrecs(fp):
                if rec['ITEM'] in excluded: continue
                recs.append(rec)
        recs.sort(key=lambda rec:rec['SCORE'], reverse=True)
        if limit:
            recs = recs[:limit]
        results1 = set( (rec['ITEM'], rec['CANDS'][0] ) for rec in recs )
        if results0 is None:
            results0 = results1
            print(f'src: {path}, {len(results0)} results')
            continue
        common = results0.intersection(results1)
        print(f'  {path}, {len(common)} / {len(results0)}')

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
