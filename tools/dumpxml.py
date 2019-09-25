#!/usr/bin/env python
import sys
import re
from xml.etree.cElementTree import Element
from xml.etree.cElementTree import ElementTree

def attrib(e):
    return ''.join( f' {k}="{v}"' for (k,v) in e.attrib.items() )

def dump(output, elem, indent=''):
    output.write(indent)
    tag = re.sub(r'{[^}]*}', '', elem.tag)
    children = list(elem)
    if children:
        output.write(f'<{tag}{attrib(elem)}>\n')
        for e in children:
            dump(output, e, indent+'  ')
        output.write(f'{indent}</{tag}>\n')
    else:
        if elem.text:
            output.write(f'<{tag}{attrib(elem)}>{elem.text}</{tag}>')
        else:
            output.write(f'<{tag}{attrib(elem)} />')
        output.write('\n')
    return

def main(argv):
    import getopt
    def usage():
        print(f'usage: {argv[0]} [-o output] [file ...]')
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'do:')
    except getopt.GetoptError:
        return usage()
    debug = 0
    output = sys.stdout
    for (k, v) in opts:
        if k == '-d': debug += 1
    if not args:
        args.append(None)
    for path in args:
        if path is not None:
            fp = open(path)
        else:
            fp = sys.stdin
        root = ElementTree(file=fp).getroot()
        if path is not None:
            fp.close()
        dump(output, root)
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
