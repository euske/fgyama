#!/usr/bin/env python
import sys
import re

NAME = re.compile(r'\w+$', re.U)
def stripid(name):
    m = NAME.search(name)
    if m:
        return m.group(0)
    else:
        return None

def stripgeneric(name):
    (base,_,_) = name.partition('<')
    return base

def stripref(name):
    assert not name.startswith('%')
    return stripid(name)

def splitmethodname(name):
    assert '(' in name and ')' in name
    i = name.index('(')
    j = name.index(')')
    (name, args, retype) = (name[:i], name[i:j+1], name[j+1:])
    if name.endswith(';.<init>'):
        name = name[:-8]
    return (stripid(name), args, retype)

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

def gettypewords(name):
    header = ''
    while name.startswith('['):
        header += '['
        name = name[1:]
    if name.startswith('L'):
        assert name.endswith(';')
        name = name[1:-1]
        name = stripgeneric(name)
        return [ header+'L'+w for w in splitwords(name) ]
    else:
        return [ header+name ]

def main(argv):
    import fileinput

    nrefs = 0
    count = {}
    for line in fileinput.input():
        (ref,_,ntype) = line.strip().partition(' ')
        nrefs += 1
        name = stripid(ref)
        if name is None: continue
        for w in splitwords(name):
            if w not in count:
                count[w] = 0
            count[w] += 1

    print('nrefs', nrefs)
    for (w,n) in sorted(count.items(), key=lambda x:x[1], reverse=True):
        print(n, w)

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
