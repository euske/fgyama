#!/usr/bin/env python
import sys
from xml.etree.cElementTree import Element
from xml.etree.cElementTree import ElementTree

class Variable:

    def __init__(self, scope, name, type):
        self.scope = scope
        self.name = name
        self.type = type
        return

    def __repr__(self):
        return '<%s(%s)>' % (self.name, self.type)

class Scope:

    def __init__(self, parent=None):
        self.parent = parent
        self.vars = {}
        self.children = []
        return

    def add(self, var):
        self.vars[var.name] = var
        return

    def descend(self):
        c = Scope(self)
        self.children.append(c)
        return c

    def dump(self, indent=''):
        for var in self.vars.values():
            print(indent+str(var))
        for c in self.children:
            c.dump(indent+'  ')
        return

def get_name(elem):
    return elem.find('name').text

def get_decl(elem):
    type = get_name(elem.find('type'))
    name = get_name(elem)
    return (type, name)

def process_block(scope, elem):
    for declstmt in elem.findall('decl_stmt'):
        (param_type, param_name) = get_decl(declstmt.find('decl'))
        var = Variable(scope, param_name, param_type)
        scope.add(var)
    for stmt in elem.getchildren():
        if stmt.tag == 'if':
            then = stmt.find('then')
            process_block(scope.descend(), then.find('block'))
    return

def process_func(elem):
    scope = Scope()
    func_type = get_name(elem.find('type'))
    func_name = get_name(elem)
    params = elem.find('parameter_list')
    for param in params.findall('parameter'):
        (param_type, param_name) = get_decl(param.find('decl'))
        var = Variable(scope, param_name, param_type)
        scope.add(var)
    block = elem.find('block')
    process_block(scope, block)
    scope.dump()
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
