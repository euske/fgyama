#!/usr/bin/env python
import sys
import math
import marshal

class NaiveBayes:

    """
>>> b = NaiveBayes()
>>> b.add('banana', ['yellow'], 5)
>>> b.add('banana', ['long','yellow'], 10)
>>> b.add('banana', ['sweet','yellow'], 5)
>>> b.add('banana', ['long','sweet'], 5)
>>> b.add('banana', ['long','sweet','yellow'], 25)
>>> b.add('orange', ['yellow'], 15)
>>> b.add('orange', ['sweet','yellow'], 15)
>>> b.add('other', ['sweet'], 10)
>>> b.add('other', ['sweet','long'], 5)
>>> b.add('other', ['yellow','long'], 5)
>>> b.kcount
{'banana': 50, 'orange': 30, 'other': 20}
>>> b.fcount
{'yellow': {None: 80, 'banana': 45, 'orange': 30, 'other': 5}, 'long': {None: 50, 'banana': 40, 'other': 10}, 'sweet': {None: 65, 'banana': 35, 'orange': 15, 'other': 15}}
>>> keys = b.getkeys(['long','sweet','yellow'])
>>> [ k for (_,k) in keys ]
['banana', 'other']
>>> keyfeats = b.getkeyfeats(['long','sweet','yellow'])
>>> [ (k,a) for (_,k,a) in keyfeats ]
[('banana', [50, ('long', 40), ('sweet', 35), ('yellow', 45)]), ('other', [20, ('long', 10), ('sweet', 15), ('yellow', 5)])]
>>> [ p for (p,_) in keys ] == [ p for (p,_,_) in keyfeats ]
True
"""

    def __init__(self):
        self.fcount = {}
        self.kcount = {}
        return

    def __len__(self):
        return len(self.kcount)

    def add(self, key, feats, c=1):
        self.adddict(key, c, { f:c for f in feats })
        return

    def adddict(self, key, count, feats):
        assert key is not None
        if key not in self.kcount:
            self.kcount[key] = 0
        self.kcount[key] += count
        for (f,c) in feats.items():
            if f in self.fcount:
                d = self.fcount[f]
            else:
                d = self.fcount[f] = {None:0}
            if key not in d:
                d[key] = 0
            d[key] += c
            d[None] += c
        return

    def remove(self, key, feats, c=1):
        self.removedict(key, c, { f:c for f in feats })
        return

    def removedict(self, key, count, feats):
        assert key is not None
        assert key in self.kcount
        self.kcount[key] -= count
        for (f,c) in feats.items():
            assert f in self.fcount
            d = self.fcount[f]
            assert key in d
            d[key] -= c
            d[None] -= c
        return

    def dump(self, threshold=2, ntop=10):
        key2feats = self.getfeats(threshold=threshold)
        for (k,n) in sorted(self.kcount.items(), key=lambda x:x[1], reverse=True):
            if k not in key2feats: continue
            feats = key2feats[k]
            if not feats: continue
            print('+%s (%r cases, %r features)' % (k, n, len(feats)))
            a = [ (f,v,self.fcount[f][None]) for (f,v) in feats ]
            a = sorted(a, reverse=True, key=lambda x:x[1]*x[1]/x[2])
            for (f,v,t) in a[:ntop]:
                print(' %d/%d %r' % (v, t, f))
            print()
        return

    def validate(self):
        n = max(self.kcount.values())
        for (f,d) in self.fcount.items():
            assert d[None] == sum( v for (k,v) in d.items() if k is not None )
            for (k,v) in d.items():
                if k is None: continue
                assert k in self.kcount
                assert v <= n
        return

    def save(self, fp):
        data = (self.fcount, self.kcount)
        marshal.dump(data, fp)
        return

    def load(self, fp):
        data = marshal.load(fp)
        (self.fcount, self.kcount) = data
        self.validate()
        return

    def narrow(self, feats, ratio):
        key2feats = {}
        for f in feats:
            if f not in self.fcount: continue
            for k in self.fcount[f]:
                if k is None: continue
                if k in key2feats:
                    a = key2feats[k]
                else:
                    a = key2feats[k] = []
                a.append(f)
        m = ratio * max( len(a) for a in key2feats.values() )
        f2 = set()
        for (k,a) in key2feats.items():
            if len(a) < m: continue
            if not f2:
                f2 = set(a)
            else:
                f2.intersection_update(set(a))
        return f2

    def getkeys(self, feats, n=0, fallback=False):
        # argmax P(k | f1,f2,...) = argmax P(k) P(f1,f2,...|k)
        # = argmax P(k) P(f1|k) P(f2|k), ...
        keyp = { k:math.log(v) for (k,v) in self.kcount.items() if 0 < v }
        kprob = keyp.copy()
        skipped = set()
        for f in feats:
            if f not in self.fcount: continue
            d = self.fcount[f]
            #print(f, d)
            for (k,p0) in kprob.items():
                if k in d and 0 < d[k]:
                    p1 = math.log(d[k]) - p0
                elif fallback:
                    p1 = -p0
                else:
                    skipped.add(k)
                    continue
                keyp[k] += p1
        a = [ (p,k) for (k,p) in keyp.items() if k not in skipped ]
        if not a: return a
        a.sort(reverse=True)
        # prevent exp(x) overflow by adjusting the maximum log to zero.
        m = max( p for (p,_) in a )
        a = [ (p-m, k) for (p,k) in a ]
        if n:
            a = a[:n]
        return a

    def getkeyfeats(self, feats):
        # keyp = { k1:[P(k), P(f1|k), P(f2|k), ...], k2:[ ... ] }
        keyp = {}
        m = 0
        for f in feats:
            if f not in self.fcount: continue
            for (k,c) in self.fcount[f].items():
                if k is None or c == 0: continue
                if k in keyp:
                    a = keyp[k]
                else:
                    a = keyp[k] = [self.kcount[k]]
                a.append((f, c))
                m = max(m, len(a))
        # compute P(k) P(f1|k) P(f2|k) for each k.
        # keyfeats = [(p1,k1,feats1), (p2,k2,feats2), ...]
        keyfeats = []
        for (k,a) in keyp.items():
            if len(a) != m: continue
            pk = p = math.log(a[0])
            for (_,c) in a[1:]:
                p += math.log(c) - pk
            keyfeats.append((p, k, a))
        if not keyfeats: return []
        keyfeats.sort(reverse=True)
        # prevent exp(x) overflow by adjusting the maximum log to zero.
        pm = max( p for (p,_,_) in keyfeats )
        return [ (p-pm, k, a) for (p,k,a) in keyfeats ]

    # for debugging
    def getkeysd(self, feats, n=0, fallback=False):
        # argmax P(k | f1,f2,...) = argmax P(k) P(f1,f2,...|k)
        # = argmax P(k) P(f1|k) P(f2|k), ...
        n = sum(self.kcount.values())
        keyp = { k:c0/n for (k,c0) in self.kcount.items() }
        skipped = set()
        for f in feats:
            print(f+':')
            if f not in self.fcount: continue
            d = self.fcount[f]
            for (k,c0) in self.kcount.items():
                if k in d:
                    p = d[k] / c0
                    print('    p(%r|%r) = %r' % (f, k, p))
                elif fallback:
                    p = 1 / c0
                    print('    ?p(%r|%r) = %r' % (f, k, p))
                else:
                    skipped.add(k)
                    continue
                keyp[k] *= p
        a = [ (p,k) for (k,p) in keyp.items() if k not in skipped ]
        if not a: return a
        a.sort(reverse=True)
        for (p,k) in a:
            print('p(%r) = %r' % (k, keyp[k]))
        if n:
            a = a[:n]
        return a

    def getfeats(self, keys=None, threshold=0):
        if keys is None:
            keys = self.kcount.keys()
        keyfeats = { k:[] for k in keys }
        for (f,d) in self.fcount.items():
            for (k,v) in d.items():
                if v < threshold: continue
                if k not in keyfeats: continue
                a = keyfeats[k]
                a.append((f, v))
        return keyfeats

def main(argv):
    nb = NaiveBayes()
    with open(argv[1], 'rb') as fp:
        nb.load(fp)
    nb.dump()
    return 0
if __name__ == '__main__': sys.exit(main(sys.argv))
