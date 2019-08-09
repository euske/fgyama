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
>>> b.commit()
>>> a = b.getkeys(['long','sweet','yellow'])
>>> [ k for (k,p) in a ]
['banana', 'other']
"""

    def __init__(self):
        self.fcount = {}
        self.kcount = {}
        self.fprob = None
        self.kprob = None
        return

    def add(self, key, feats, c=1):
        for f in feats:
            if f in self.fcount:
                d = self.fcount[f]
            else:
                d = self.fcount[f] = {None:0}
            if key not in d:
                d[key] = 0
            d[key] += c
            d[None] += c
        if key not in self.kcount:
            self.kcount[key] = 0
        self.kcount[key] += c
        self.fprob = self.kprob = None
        return

    def dump(self, threshold=2, ntop=10):
        key2feats = self.getfeats(threshold=threshold)
        for (k,n) in sorted(self.kcount.items(), key=lambda x:x[1], reverse=True):
            if k not in key2feats: continue
            feats = key2feats[k]
            print('+%s (%r cases, %r features)' % (k, n, len(feats)))
            a = [ (f,v,self.fcount[f][None]) for (f,v) in feats ]
            a = sorted(a, reverse=True, key=lambda x:x[1]*x[1]/x[2])
            for (f,v,t) in a[:ntop]:
                print(' %d/%d %r' % (v, t, f))
            print()
        return

    def commit(self):
        if self.kprob is None:
            # self.kprob[k] = log(C(k))
            self.kprob = {}
            for (k,v) in self.kcount.items():
                self.kprob[k] = math.log(v)
        if self.fprob is None:
            # self.fprob[f][k] = log(C(f,k))
            self.fprob = {}
            for (f,d) in self.fcount.items():
                p = { k: math.log(v) for (k,v) in d.items() }
                self.fprob[f] = p
        return

    def save(self, fp):
        data = (self.fcount, self.kcount)
        marshal.dump(data, fp)
        return

    def load(self, fp):
        data = marshal.load(fp)
        (self.fcount, self.kcount) = data
        self.commit()
        return

    def narrow(self, feats, ratio):
        key2feats = {}
        for f in feats:
            if f not in self.fprob: continue
            for k in self.fprob[f]:
                if k in key2feats:
                    a = key2feats[k]
                else:
                    a = key2feats[k] = []
                a.append(f)
        m = max( len(a) for a in key2feats.values() )
        f2 = set()
        for (k,a) in key2feats.items():
            if len(a) < m*ratio: continue
            if not f2:
                f2 = set(a)
            else:
                f2.intersection_update(set(a))
        return f2

    def getkeys(self, feats, n=0, fallback=False):
        # argmax P(k | f1,f2,...) = argmax P(k) P(f1,f2,...|k)
        # = argmax P(k) P(f1|k) P(f2|k), ...
        assert self.kprob is not None
        assert self.fprob is not None
        assert feats
        keyp = { k:p0 for (k,p0) in self.kprob.items() }
        skipped = set()
        for f in feats:
            if f not in self.fprob: continue
            d = self.fprob[f]
            #print(f, d)
            for (k,p0) in self.kprob.items():
                if k in d:
                    p1 = d[k] - p0
                elif fallback:
                    p1 = -p0
                else:
                    skipped.add(k)
                    continue
                keyp[k] += p1
        a = [ (k,p) for (k,p) in keyp.items() if k not in skipped ]
        if not a: return a
        a.sort(key=lambda x:x[1], reverse=True)
        # prevent exp(x) overflow by adjusting the maximum log to zero.
        m = max( p for (k,p) in a )
        a = [ (k, p-m) for (k,p) in a ]
        if n:
            a = a[:n]
        return a

    # for debugging
    def getkeysd(self, feats, n=0, fallback=False):
        # argmax P(k | f1,f2,...) = argmax P(k) P(f1,f2,...|k)
        # = argmax P(k) P(f1|k) P(f2|k), ...
        assert feats
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
        a = [ (k,p) for (k,p) in keyp.items() if k not in skipped ]
        if not a: return a
        a.sort(key=lambda x:x[1], reverse=True)
        for (k,p) in a:
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
                if k in keyfeats:
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
