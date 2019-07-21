#!/usr/bin/env python
import sys
import re
from graph import DFGraph, load_klasses

# methods:
#   words overlap (jaccard)
#   override overridden
#   same arguments
#   same return type

# refs:
#   words overlap (jaccard)
#   same type

def jaccard(s1, s2):
    if not s1 or not s2: return 0
    return len(s1.intersection(s2)) / len(s1.union(s2))

NAME = re.compile(r'\w+$', re.U)
def stripid(name):
    m = NAME.search(name)
    if m:
        return m.group(0)
    else:
        return None

def stripmethodid(x):
    assert '(' in x, x
    (name,_,x) = x.partition('(')
    assert ')' in x, x
    (args,_retype) = x.partition(')')
    if name.endswith(';.<init>'):
        name = name[:-8]
    return (stripid(name), args, retype)

def striptypename(name):
    if name.endswith(';'):
        name = name[:-1]
    return stripid(name)

WORD = re.compile(r'[a-z]+[A-Z]?|[A-Z]+')
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

class ClassDB:

    def __init__(self):
        self.klasses = {}
        self.methods = {}
        self.fields = {}
        return

    def add_klass(self, klass):
        assert klass.name not in self.klasses
        self.klasses[klass.name] = klass
        for method in klass.get_methods():
            assert method.name not in self.methods
            self.methods[method.name] = method
        for (fname,ftype) in klass.fields:
            assert fname not in self.fields
            self.fields[fname] = ftype
        return

    def cmp_methods(self, id1, id2):
        method1 = self.methods[id1]
        method2 = self.methods[id2]
        (name1,args1,retype1) = stripmethodid(method1.name)
        (name2,args2,retype2) = stripmethodid(method2.name)
        overriden = (method1.name in method2.overrides or
                     method2.name in method1.overrides)
        sameargs = (args1 == args2)
        sameretype = (retype1 == retype2)
        words1 = splitwords(name1)
        words2 = splitwords(name1)
        namesim = get_jaccard(set(words1), set(words2))
        return (namesim, overriden, sameargs, sameretype)

    def cmp_fields(self, ref1, ref2):
        name1 = stripid(ref1)
        name2 = stripid(ref2)
        words1 = splitwords(name1)
        words2 = splitwords(name1)
        namesim = get_jaccard(set(words1), set(words2))
        type1 = self.fields.get(ref1)
        type2 = self.fields.get(ref2)
        sametype = (type1 == typr2)
        return (namesim, sametype)


# main
def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-d] [graph ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'd')
    except getopt.GetoptError:
        return usage()
    debug = 0
    for (k, v) in opts:
        if k == '-d': debug += 1
    if not args: return usage()

    db = ClassDB()
    for path in args:
        print('Loading: %r...' % path, file=sys.stderr)
        with open(path) as fp:
            for klass in load_klasses(fp):
                db.add_klass(klass)

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
