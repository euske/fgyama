#!/usr/bin/env python
import math

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
{'yellow': {'banana': 45, 'orange': 30, 'other': 5}, 'long': {'banana': 40, 'other': 10}, 'sweet': {'banana': 35, 'orange': 15, 'other': 15}}
>>> b.commit()
>>> a = b.get(['long','sweet','yellow'])
>>> [ (k, fs) for (k,p,fs) in a ]
[('banana', ['yellow', 'long', 'sweet']), ('other', ['sweet', 'long', 'yellow']), ('orange', ['yellow', 'sweet'])]
"""

    def __init__(self):
        self.fcount = {}
        self.fprob = {}
        self.kcount = {}
        self.kprob = {}
        return

    def add(self, key, feats, c=1):
        for f in feats:
            if f in self.fcount:
                d = self.fcount[f]
            else:
                d = self.fcount[f] = {}
            if key not in d:
                d[key] = 0
            d[key] += c
        if key not in self.kcount:
            self.kcount[key] = 0
        self.kcount[key] += c
        return

    def commit(self):
        # self.kprob[k] = log(C(k))
        for (k,v) in self.kcount.items():
            self.kprob[k] = math.log(v)
        # self.fprob[f][k] = log(C(f,k))
        for (f,d) in self.fcount.items():
            p = { k: math.log(v) for (k,v) in d.items() }
            self.fprob[f] = p
        return

    def get(self, feats, n=0):
        # argmax P(k | f1,f2,...) = argmax P(k) P(f1,f2,...|k)
        # = argmax P(k) P(f1|k) P(f2|k), ...
        assert feats
        keyp = {}
        keyf = {}
        for f in feats:
            if f not in self.fprob: continue
            d = self.fprob[f]
            #print(f, d)
            for (k,p0) in self.kprob.items():
                if k in d:
                    p1 = d[k] - p0
                    if k not in keyf:
                        keyf[k] =[]
                    keyf[k].append((p1, f))
                else:
                    p1 = -p0
                if k not in keyp:
                    keyp[k] = p0
                keyp[k] += p1
        assert keyp
        a = [ (k,p) for (k,p) in keyp.items() ]
        a.sort(key=lambda x:x[1], reverse=True)
        # prevent exp(x) overflow by adjusting the maximum log to zero.
        m = max( p for (k,p) in a )
        a = [ (k,math.exp(p-m)) for (k,p) in a ]
        z = sum( p for (_,p) in a )
        a = [ (k,p/z,[ f for (_,f) in sorted(keyf[k], reverse=True)])
              for (k,p) in a ]
        if n:
            a = a[:n]
        return a

    def getd(self, feats, n=0):
        # argmax P(k | f1,f2,...) = argmax P(k) P(f1,f2,...|k)
        # = argmax P(k) P(f1|k) P(f2|k), ...
        assert feats
        keyp = {}
        n = sum(self.kcount.values())
        for f in feats:
            print(f+':')
            if f not in self.fcount: continue
            d = self.fcount[f]
            for (k,c0) in self.kcount.items():
                if k not in keyp:
                    p = c0 / n
                    print('  p(%r) = %r' % (k, p))
                    keyp[k] = p
                if k in d:
                    p = d[k] / c0
                    print('    p(%r|%r) = %r' % (f, k, p))
                else:
                    p = 1 / c0
                    print('    p(%r|%r)? = %r' % (f, k, p))
                keyp[k] *= p
        assert keyp
        a = [ (k,p) for (k,p) in keyp.items() ]
        a.sort(key=lambda x:x[1], reverse=True)
        for (k,p) in a:
            print('p(%r) = %r' % (k, keyp[k]))
        if n:
            a = a[:n]
        return a
