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
>>> b.commit()
>>> a = b.get(['long','sweet','yellow'])
>>> [ (k, fs) for (k,p,fs) in a ]
[('banana', ['yellow', 'long', 'sweet']), ('other', ['sweet', 'long', 'yellow'])]
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
        for (k,v) in self.kcount.items():
            self.kprob[k] = math.log(v)
        for (f,d) in self.fcount.items():
            p = { k:math.log(v) for (k,v) in d.items() }
            self.fprob[f] = p
        return

    def get(self, feats, n=0):
        # argmax P(k | f1,f2,...) = argmax P(k) P(f1,f2,...|k)
        # = argmax P(k) P(f1|k) P(f2|k), ...
        assert feats
        keys = {}
        keyp = {}
        keyf = {}
        for f in feats:
            if f not in self.fprob: continue
            d = self.fprob[f]
            #print(f, d)
            for (k,v) in d.items():
                if k not in keys:
                    keys[k] = 0
                keys[k] += 1
                if k not in self.kprob: continue
                p0 = self.kprob[k]
                p1 = v - p0
                if k not in keyp:
                    keyp[k] = p0
                    keyf[k] = []
                keyp[k] += p1
                keyf[k].append((p1, f))
        assert keyp
        # prevent exp(x) overflow by adjusting the maximum log to zero.
        m = max(keyp.values())
        a = [ (k,math.exp(p-m)) for (k,p) in keyp.items() if keys[k] == len(feats) ]
        z = sum( p for (_,p) in a )
        a = [ (k,p/z,[ f for (_,f) in sorted(keyf[k], reverse=True)])
              for (k,p) in a ]
        a.sort(key=lambda x:x[1], reverse=True)
        if n:
            a = a[:n]
        return a
