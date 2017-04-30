#!/usr/bin/env python
import sys
from xml.etree.cElementTree import Element
from xml.etree.cElementTree import ElementTree

class Block:

    def __init__(self, parent=None):
        self.parent = parent
        self.vars = {}
        self.children = []
        return

    def add(self, name, type):
        self.vars[name] = type
        return

    def descend(self):
        c = Block(self)
        self.children.append(c)
        return c

    def dump(self, indent=''):
        for (name,type) in self.vars.items():
            print(indent+('%s: %s' % (name,type)))
        for c in self.children:
            c.dump(indent+'  ')
        return

def get_name(elem):
    return elem.find('name').text

def get_decl(elem):
    type = get_name(elem.find('type'))
    name = get_name(elem)
    return (type, name)

def process_block(b, elem):
    for declstmt in elem.findall('decl_stmt'):
        (param_type, param_name) = get_decl(declstmt.find('decl'))
        b.add(param_name, param_type)
    for block in elem.findall('block'):
        process_block(b.descend(), block)
    return

def process_func(elem):
    b = Block()
    func_type = get_name(elem.find('type'))
    func_name = get_name(elem)
    params = elem.find('parameter_list')
    for param in params.findall('parameter'):
        (param_type, param_name) = get_decl(param.find('decl'))
        b.add(param_name, param_type)
    block = elem.find('block')
    process_block(b, block)
    b.dump()
    return

def process_root(elem):
    for func in elem.iter('function'):
        process_func(func)
    return

def main(argv):
    import getopt
    def usage():
        print ('usage: %s [-d] [file ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'd')
    except getopt.GetoptError:
        return usage()
    debug = 0
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
        process_root(root)
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
