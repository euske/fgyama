#!/usr/bin/env python
#
# check name consistency.
#
import sys
import re
from srcdb import SourceDB, SourceAnnot
from naivebayes import NaiveBayes

WORD1 = re.compile(r'[A-Z]?[a-z]+$')
WORD2 = re.compile(r'[A-Z]+$')
def getnoun(name):
    if name[-1].islower():
        return WORD1.search(name).group(0).lower()
    elif name[-1].isupper():
        return WORD2.search(name).group(0).lower()
    else:
        return None

class Chain:
    def __init__(self, ref0, ref1, feats, nodes):
        self.ref0 = ref0
        self.ref1 = ref1
        self.noun0 = getnoun(ref0)
        self.noun1 = getnoun(ref1)
        self.feats = feats
        self.nodes = nodes
        return

def main(argv):
    import fileinput

    srcdb = SourceDB('.')
    nb0 = NaiveBayes()
    nb1 = NaiveBayes()
    chains = []
    feats2c = {}
    for line in fileinput.input():
        line = line.strip()
        if line.startswith('+SOURCE'):
            (_,_,s) = line.partition(' ')
            (fid,name) = eval(s)
            srcdb.register(fid, name)
        elif line.startswith('+NODES'):
            f = line.split()[1:]
            nodes = []
            for k in f:
                if k == 'None': continue
                (fid,start,end) = k.split(':')
                nodes.append((int(fid), int(start), int(end)))
        elif line.startswith('+PAIR'):
            f = line.split()[1:]
            feats = tuple(f[2:])
            c = Chain(f[0], f[1], feats, nodes)
            chains.append(c)
            if feats in feats2c:
                a = feats2c[feats]
            else:
                a = feats2c[feats] = []
            nb1.add(c.noun1, (c.noun0, feats))
            nb0.add(c.noun0, (c.noun1, feats))
            a.append(c)
    nb0.fixate()
    nb1.fixate()

    T = 10
    for c in chains:
        cands1 = nb1.get((c.noun0, c.feats))
        (n1,p1) = cands1[0]
        if 2 < len(cands1):
            (n2,p2) = cands1[1]
            if p1 < p2*T: continue
        if c.noun1 == n1: continue
        print('#', c.noun0, c.noun1, '->', n1, c.feats)
        annot = SourceAnnot(srcdb)
        for (fid,start,end) in (c.nodes[0], c.nodes[-1]):
            annot.addbyfid(fid, start, end)
        annot.show_text()
        for c2 in feats2c[c.feats]:
            if c2.noun1 != n1: continue
            print('!', c2.feats)
            annot = SourceAnnot(srcdb)
            for (fid,start,end) in (c2.nodes[0], c2.nodes[-1]):
                annot.addbyfid(fid, start, end)
            annot.show_text()
        print()

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
