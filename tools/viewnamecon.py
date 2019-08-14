#!/usr/bin/env python
import sys
import re
import math
import json
from srcdb import SourceDB, SourceAnnot
from featdb import FeatDB
from getwords import stripid, splitwords

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
    
def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-H] [-c encoding] srcdb [out]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'Hc:')
    except getopt.GetoptError:
        return usage()
    html = False
    encoding = None
    for (k, v) in opts:
        if k == '-H': html = True
        elif k == '-c': encoding = v
    
    if not args: return usage()
    path = args.pop(0)
    srcdb = SourceDB(path, encoding)
    if not args: return usage()

    fp = fileinput.input(args)
    recs = sorted(getrecs(fp), key=lambda rec:rec['SCORE'], reverse=True)
    for rec in recs:
        item = rec['ITEM']
        score = rec['SCORE']
        name = stripid(item)
        names = rec['NAMES']
        print('+', item)
        print(score, name, names)
        annot = SourceAnnot(srcdb)
        for (path,s,e) in rec['SOURCE']:
            annot.add(path,s,e)
        annot.show_text()
        for (feat,srcs0,evidence,srcs1) in rec['SUPPORT']:
            print(feat)
            annot = SourceAnnot(srcdb)
            for (path,s,e) in srcs0:
                annot.add(path,s,e)
            annot.show_text(showline=lambda line: 'S '+line)
            print(evidence)
            annot = SourceAnnot(srcdb)
            for (path,s,e) in srcs1:
                annot.add(path,s,e)
            annot.show_text(showline=lambda line: 'E '+line)
            
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
