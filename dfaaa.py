#!/usr/bin/env python
import sys
from xml.etree.cElementTree import Element
from xml.etree.cElementTree import ElementTree


##  Exporter
##  http://graphviz.org/content/dot-language
##
def getattrs(kwargs):
    return ', '.join(
        ( '%s="%s"' % (k,v.replace('"', '\\"'))
          for (k,v) in kwargs.items() if v is not None ))

def get1(d):
    return list(d.items())[0]

class Exporter:

    def __init__(self, fp):
        self.fp = fp
        return

    def open(self, name):
        self.fp.write('digraph %s {\n' % name)
        return

    def close(self):
        self.fp.write('}\n')
        return

    def put_node(self, nid, **kwargs):
        self.fp.write('  N%r' % nid)
        args = getattrs(kwargs)
        if args:
            self.fp.write(' [%s]' % args)
        self.fp.write(';\n')
        return

    def put_edge(self, nid1, nid2, **kwargs):
        self.fp.write('  N%r -> N%r' % (nid1, nid2))
        args = getattrs(kwargs)
        if args:
            self.fp.write(' [%s]' % args)
        self.fp.write(';\n')
        return

            
class Node:

    nid_base = 0

    def __init__(self, elem):
        Node.nid_base += 1
        self.nid = Node.nid_base
        self.elem = elem
        self.send = {}
        self.recv = []
        return

    def name(self):
        return 'Node %d' % (self.nid)

    def connect(self, node, label=None):
        if self is node: return
        #print ('connect', self, node)
        self.send[node] = label
        node.recv.append(self)
        return

class InnerNode(Node):

    def name(self):
        return ''

    def trimmable(self):
        return len(self.send) == 1 and len(self.recv) == 1

    def trim(self):
        send = list(self.send.keys())[0]
        recv = self.recv[0]
        l1 = recv.send[self]
        l2 = self.send[send]
        del recv.send[self]
        send.recv.remove(self)
        recv.connect(send, l1 or l2)
        return
    
class FuncArgNode(Node):

    def __init__(self, elem, index):
        Node.__init__(self, elem)
        self.index = index
        return
    
    def name(self):
        return 'FuncArg %d' % (self.index)

class ReturnNode(Node):

    def name(self):
        return 'Return %d' % (self.nid)

class CondNode(Node):
    pass

class BranchNode(CondNode):

    def __init__(self, elem, cond):
        CondNode.__init__(self, elem)
        cond.connect(self, 'branch')
        return
    
    def name(self):
        return 'Branch %d' % (self.nid)

class JoinNode(CondNode):

    def __init__(self, elem, cond):
        CondNode.__init__(self, elem)
        cond.connect(self, 'join')
        return
    
    def name(self):
        return 'Join %d' % (self.nid)

class VarNode(Node):

    def __init__(self, elem, var):
        Node.__init__(self, elem)
        self.var = var
        return
    
    def name(self):
        return 'Var %s' % (self.var.name)

class ConstNode(Node):

    def __init__(self, elem, value, type):
        Node.__init__(self, elem)
        self.value = value
        self.type = type
        return
    
    def name(self):
        return 'Const %s(%s)' % (self.value, self.type)

class ExprNode(Node):

    def __init__(self, elem):
        Node.__init__(self, elem)
        self.ops = []
        return

    def addop(self, op):
        self.ops.append(op)
        return
    
    def name(self):
        return 'Op %s' % (''.join(self.ops))
    
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

    def add(self, name, type):
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


##  process_expr
##
def process_expr(elem, scope):
    inputs = {}
    output = ExprNode(elem)
    arg = 0
    for e in elem.getchildren():
        if e.tag == 'name':  # Variable lookup.
            name = e.text
            var = scope.lookup(name)
            src = InnerNode(e)
            src.connect(output, 'Arg %r' % arg)
            inputs[var] = src
            arg += 1
            
        elif e.tag == 'literal':
            (name, type) = (e.text, e.get('type'))
            src = ConstNode(e, name, type)
            src.connect(output, 'Arg %r' % arg)
            arg += 1
            
        elif e.tag == 'operator':
            output.addop(e.text)
            
    return (inputs, output)


##  process_block
##
def process_block(elem, parent, bindings):
    scope = Scope(parent)
    bindings = bindings.copy()
    inputs = {}  # {var: src}
    outputs = {}
    for stmt in elem.getchildren():
        if stmt.tag == 'decl_stmt':
            decl = stmt.find('decl')
            (param_name, param_type) = get_decl(decl)
            var = scope.add(param_name, param_type)
            init = decl.find('init')
            if init:
                sym = decl.find('name')
                expr = init.find('expr')
                (ins1, out1) = process_expr(expr, scope)
                for (var1, dst1) in ins1.items():
                    if var1 not in inputs:
                        inputs[var1] = dst1
                    src = bindings[var1]
                    src.connect(dst1)
                dst = VarNode(sym, var)
                out1.connect(dst)
                bindings[var] = dst
                outputs[var] = dst
                
        elif stmt.tag == 'expr_stmt':
            expr = stmt.find('expr')
            sym = expr.find('name')
            var = scope.lookup(sym.text)
            dst = VarNode(sym, var)
            (ins1, out1) = process_expr(expr, scope)
            for (var1, dst1) in ins1.items():
                if var1 not in inputs:
                    inputs[var1] = dst1
                src = bindings[var1]
                src.connect(dst1)
            out1.connect(dst)
            bindings[var] = dst
            outputs[var] = dst
            
        elif stmt.tag == 'return':
            expr = stmt.find('expr')
            dst = ReturnNode(stmt)
            (ins1, out1) = process_expr(expr, scope)
            for (var1, dst1) in ins1.items():
                if var1 not in inputs:
                    inputs[var1] = dst1
                src = bindings[var1]
                src.connect(dst1)
            out1.connect(dst)
            outputs[None] = dst
            
        elif stmt.tag == 'if':
            condition = stmt.find('condition')
            expr = condition.find('expr')
            (ins1, cond1) = process_expr(expr, scope)
            for (var1, dst1) in ins1.items():
                if var1 not in inputs:
                    inputs[var1] = dst1
                src = bindings[var1]
                src.connect(dst1)
            
            then = stmt.find('then')
            block = then.find('block')
            (ins1, outs1) = process_block(block, scope, bindings)
            for (var1, dst1) in ins1.items():
                if var1 not in inputs:
                    inputs[var1] = dst1
                branch = BranchNode(stmt, cond1)
                branch.connect(dst1)
                src = bindings[var1]
                src.connect(branch)
                
            for (var1, src1) in outs1.items():
                join = JoinNode(stmt, cond1)
                src1.connect(join)
                dst = bindings[var1]
                join.connect(dst)
                bindings[var1] = join
                outputs[var1] = join
                
    for var in scope.pop():
        del outputs[var]
        if var in inputs:
            del inputs[var]
    return (inputs, outputs)

def visit_graph(node, visited):
    if node in visited: return
    visited.add(node)
    for (c,_) in node.send.items():
        visit_graph(c, visited)
    for c in node.recv:
        visit_graph(c, visited)
    return

def trim_graph(nodes):
    for node in list(nodes):
        if isinstance(node, InnerNode):
            if node.trimmable():
                node.trim()
                nodes.remove(node)
    return

def process_func(exporter, elem):
    scope = Scope()
    func_type = get_name(elem.find('type'))
    func_name = get_name(elem)
    params = elem.find('parameter_list')
    bindings = {}
    for (i,param) in enumerate(params.findall('parameter')):
        arg = FuncArgNode(param, i)
        (param_name, param_type) = get_decl(param.find('decl'))
        var = scope.add(param_name, param_type)
        dst = VarNode(param, var)
        arg.connect(dst)
        bindings[var] = dst
    block = elem.find('block')
    process_block(block, scope, bindings)

    exporter.open(func_name)
    allnodes = set()
    for node in bindings.values():
        visit_graph(node, allnodes)
    trim_graph(allnodes)
    for node in allnodes:
        exporter.put_node(node.nid, label=node.name())
    for node in allnodes:
        for (c,name) in node.send.items():
            exporter.put_edge(node.nid, c.nid, label=name)
    exporter.close()
    return

def process_root(exporter, elem):
    for func in elem.iter('function'):
        process_func(exporter, func)
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
    exporter = Exporter(sys.stdout)
    for path in args:
        if path is not None:
            fp = open(path)
        else:
            fp = sys.stdin
        root = ElementTree(file=fp).getroot()
        if path is not None:
            fp.close()
        process_root(exporter, root)
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
