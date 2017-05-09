#!/usr/bin/env python
import sys
from xml.etree.cElementTree import Element
from xml.etree.cElementTree import ElementTree

class Node:

    nid_base = 0

    def __init__(self, elem):
        Node.nid_base += 1
        self.nid = Node.nid_base
        self.elem = elem
        self.send = []
        self.recv = []
        return

    def name(self):
        return 'Node_%d' % (self.nid)

    def connect(self, node):
        if self is node: return
        #print ('connect', self, node)
        self.send.append(node)
        node.recv.append(self)
        return

class ArgNode(Node):

    def name(self):
        return 'ArgNode_%d' % (self.nid)

class ReturnNode(Node):

    def name(self):
        return 'ReturnNode_%d' % (self.nid)

class BranchNode(Node):

    def name(self):
        return 'BranchNode_%d' % (self.nid)

class JoinNode(Node):

    def name(self):
        return 'JoinNode_%d' % (self.nid)

class VarNode(Node):

    def __init__(self, elem, var):
        Node.__init__(self, elem)
        self.var = var
        return
    
    def name(self):
        return 'VarNode_%d_%s' % (self.nid, self.var.name)

class ValueNode(Node):

    def __init__(self, elem, value, type):
        Node.__init__(self, elem)
        self.value = value
        self.type = type
        return
    
    def name(self):
        return 'ValueNode_%d_%s_%s' % (self.nid, self.value, self.type)

class ExprNode(Node):

    OPNAME = {
        '+': 'add',
        '-': 'sub',
        '*': 'mul',
        '/': 'div',
        '%': 'mod',
        '=': 'eq',
        '<': 'lt',
        '>': 'gt',
        }
    
    def __init__(self, elem, op):
        Node.__init__(self, elem)
        self.op = op
        return
    
    def name(self):
        op = '_'.join( self.OPNAME[c] for c in self.op )
        return 'ExprNode_%d_%s' % (self.nid, op)
    
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
        return

    def bind(self, name, type):
        var = Variable(self, name, type)
        self.vars[name] = var
        return var

    def lookup(self, name):
        if name in self.vars:
            return self.vars[name]
        elif self.parent:
            return self.parent.lookup(name)
        else:
            raise KeyError(name)

    def pop(self):
        return self.vars.values()

def get_name(elem):
    return elem.find('name').text

def get_decl(elem):
    name = get_name(elem)
    type = get_name(elem.find('type'))
    return (name, type)

def process_expr(scope, bindings, elem):
    vals = []
    ops = []
    ins = {}
    for e in elem.getchildren():
        if e.tag == 'name':
            var = scope.lookup(e.text)
            src = Node(e)
            ins[var] = src
            vals.append(src)
        elif e.tag == 'literal':
            (name, type) = (e.text, e.get('type'))
            node = ValueNode(e, name, type)
            vals.append(node)
        elif e.tag == 'operator':
            ops.append(e.text)
    out = ExprNode(elem, ''.join(ops))
    for src in vals:
        src.connect(out)
    return (ins, out)

def process_block(parent, bindings, elem):
    scope = Scope(parent)
    ins0 = {}
    outs = {}
    bindings = bindings.copy()
    for stmt in elem.getchildren():
        if stmt.tag == 'decl_stmt':
            decl = stmt.find('decl')
            (param_name, param_type) = get_decl(decl)
            var = scope.bind(param_name, param_type)
            init = decl.find('init')
            if init:
                name = decl.find('name')
                expr = init.find('expr')
                (ins1, out) = process_expr(scope, bindings, expr)
                for (v0, dst) in ins1.items():
                    if v0 not in ins0:
                        ins0[v0] = dst
                    src = bindings[v0]
                    src.connect(dst)
                dst = VarNode(name, var)
                out.connect(dst)
                bindings[var] = dst
                outs[var] = dst
                
        elif stmt.tag == 'expr_stmt':
            expr = stmt.find('expr')
            (ins1, out) = process_expr(scope, bindings, expr)
            for (v0, dst) in ins1.items():
                if v0 not in ins0:
                    ins0[v0] = dst
                src = bindings[v0]
                src.connect(dst)
            name = expr.find('name')
            var = scope.lookup(name.text)
            dst = VarNode(name, var)
            out.connect(dst)
            bindings[var] = dst
            outs[var] = dst
            
        elif stmt.tag == 'return':
            expr = stmt.find('expr')
            (ins1, out) = process_expr(scope, bindings, expr)
            for (v0, dst) in ins1.items():
                if v0 not in ins0:
                    ins0[v0] = dst
                src = bindings[v0]
                src.connect(dst)
            dst = ReturnNode(expr)
            out.connect(dst)
            bindings[None] = dst
            outs[None] = dst
            
        elif stmt.tag == 'if':
            condition = stmt.find('condition')
            expr = condition.find('expr')
            (ins1, out) = process_expr(scope, bindings, expr)
            for (v0, dst) in ins1.items():
                if v0 not in ins0:
                    ins0[v0] = dst
                src = bindings[v0]
                branch = BranchNode(stmt)
                out.connect(branch)
                src.connect(branch)
                branch.connect(dst)
            then = stmt.find('then')
            block = then.find('block')
            (ins1, outs1) = process_block(scope, bindings, block)
            for (v0, dst) in ins1.items():
                if v0 not in ins0:
                    ins0[v0] = dst
                src = bindings[v0]
                join = JoinNode(stmt)
                out.connect(join)
                dst.connect(join)
                join.connect(src)
            for (v0, src) in outs1.items():
                bindings[v0] = src
                
    for var in scope.pop():
        del outs[var]
        if var in ins0:
            del ins0[var]
    return (ins0, outs)

def visit_graph(node, visited):
    if node in visited: return
    visited.add(node)
    for c in node.send:
        visit_graph(c, visited)
    for c in node.recv:
        visit_graph(c, visited)
    return

def process_func(elem):
    scope = Scope()
    func_type = get_name(elem.find('type'))
    func_name = get_name(elem)
    params = elem.find('parameter_list')
    bindings = {}
    for param in params.findall('parameter'):
        (param_name, param_type) = get_decl(param.find('decl'))
        var = scope.bind(param_name, param_type)
        bindings[var] = ArgNode(param)
    block = elem.find('block')
    process_block(scope, bindings, block)

    visited = set()
    for node in bindings.values():
        visit_graph(node, visited)
    for node in visited:
        for c in node.send:
            print (' N%d -> N%d [label="%s"];' %
                   (node.nid, c.nid, node.name()))
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
    print('digraph G {')
    for path in args:
        if path is not None:
            fp = open(path)
        else:
            fp = sys.stdin
        root = ElementTree(file=fp).getroot()
        if path is not None:
            fp.close()
        process_root(root)
    print('}')
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
