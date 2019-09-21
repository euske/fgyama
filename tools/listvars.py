#!/usr/bin/env python
import sys
import re
from graph import get_graphs

def main(argv):
    args = argv[1:]

    refs = {}
    for path in args:
        for method in get_graphs(path):
            for node in method:
                if node.ref is None: continue
                if node.ntype is None: continue
                if node.ref[0] not in '$@': continue
                refs[node.ref] = node.ntype

    for (ref,ntype) in sorted(refs.items()):
        print(ref, ntype)
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
