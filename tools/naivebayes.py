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
>>> b.fixate()
>>> a = b.get(['long','sweet','yellow'])
>>> [ k for (k,p) in a ]
['banana', 'other']
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

    def fixate(self):
        for (k,v) in self.kcount.items():
            self.kprob[k] = math.log(v)
        for (f,d) in self.fcount.items():
            p = { k:math.log(v) for (k,v) in d.items() }
            self.fprob[f] = p
        return

    def get(self, feats, n=0):
        # argmax P(k | f1,f2,...) = argmax P(k) P(f1,f2,...|k)
        # = argmax P(k) P(f1|k) P(f2|k), ...
        keyp = {}
        keys = {}
        for f in feats:
            if f not in self.fprob: continue
            d = self.fprob[f]
            #print(f, d)
            for (k,v) in d.items():
                if k not in keys:
                    keys[k] = 0
                keys[k] += 1
                if k not in self.kprob: continue
                if k not in keyp:
                    keyp[k] = self.kprob[k]
                keyp[k] += v - self.kprob[k]
        a = [ (k,p) for (k,p) in keyp.items() if keys[k] == len(feats) ]
        a.sort(key=lambda x:x[1], reverse=True)
        if n:
            a = a[:n]
        return a
