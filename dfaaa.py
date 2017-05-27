#!/usr/bin/env python
import sys
from xml.etree.cElementTree import Element
from xml.etree.cElementTree import ElementTree

def log(*args):
    print(' '.join(map(str, args)), file=sys.stderr)
    
def getattrs(kwargs):
    return ', '.join(
        ( '%s="%s"' % (k,v.replace('"', '\\"'))
          for (k,v) in kwargs.items() if v is not None ))

def efind(elem, *tags):
    for e in elem.getchildren():
        if e.tag in tags:
            return e
    return None


##  Exporter
##  http://graphviz.org/content/dot-language
##
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

    @classmethod
    def genid(klass):
        klass.nid_base += 1
        return klass.nid_base

    def __init__(self, elem):
        self.nid = Node.genid()
        self.elem = elem
        self.send = {}
        self.recv = []
        return

    def __repr__(self):
        return '<%s(%r)>' % (self.__class__.__name__, self.nid)

    def name(self):
        return 'Node %d' % (self.nid)

    def connect(self, node, label=None):
        if self is node: return
        log('connect', self, node, label)
        self.send[node] = label
        node.recv.append(self)
        return

    def disconnect(self, node):
        assert node in self.send
        label = self.send[node]
        log('disconnect', self, node, label)
        del self.send[node]
        node.recv.remove(self)
        return label

class InnerNode(Node):

    def __init__(self):
        Node.__init__(self, None)
        return
        
    def name(self):
        return ''

    def trimmable(self):
        return len(self.send) == 1 and len(self.recv) == 1

    def trim(self):
        send = list(self.send.keys())[0]
        recv = self.recv[0]
        l1 = recv.disconnect(self)
        l2 = self.disconnect(send)
        assert l1 is None or l2 is None, (l1,l2)
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
        return 'Return'

class LoopNode(Node):

    def __init__(self, elem, cid):
        Node.__init__(self, elem)
        self.cid = cid
        return
    
    def name(self):
        return 'Loop %d' % (self.cid)

class CondNode(Node):

    def __init__(self, elem, cid, cond):
        Node.__init__(self, elem)
        self.cid = cid
        cond.connect(self, 'cond')
        return

class BranchNode(CondNode):
    
    def name(self):
        return 'Branch %d' % (self.cid)

class JoinNode(CondNode):
    
    def name(self):
        return 'Join %d' % (self.cid)

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

    def __init__(self, elem, op):
        Node.__init__(self, elem)
        self.op = op
        return
    
    def name(self):
        return 'Op %s' % (self.op)
    
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


##  process_expr
##
def process_expr(elem, scope):
    inputs = {}

    if elem.tag == 'SimpleName':  # Variable lookup.
        var = scope.lookup(elem.text)
        output = InnerNode()
        inputs[var] = output

    elif elem.tag in ('NumberLiteral', 'StringLiteral', 'BooleanLiteral'):
        output = ConstNode(elem, elem.text, elem.tag)

    else:
        op = elem.get('operator')
        output = ExprNode(elem, op)
        for (i,e) in enumerate(elem.getchildren()):
            (ins1,out1) = process_expr(e, scope)
            for (var1, in1) in ins1.items():
                if var1 in inputs:
                    src = inputs[var1]
                else:
                    src = inputs[var1] = InnerNode()
                src.connect(in1)
            out1.connect(output, 'Arg %d' % i)
            
    return (inputs, output)


##  process_block
##
def process_block(elem, parent):
    scope = Scope(parent)
    inputs = {}  # {var: src}
    bindings = {}
    def getvar(var):
        if var in bindings:
            src = bindings[var]
        elif var in inputs:
            src = inputs[var]
        else:
            src = inputs[var] = InnerNode()
        return src
    def setvar(var, dst):
        bindings[var] = dst
        return
    
    for stmt in elem.getchildren():
        if stmt.tag == 'VariableDeclarationStatement':
            param_type = stmt[0].text
            frag = stmt[1]
            if frag:
                sym = frag[0]
                param_name = sym.text
                var = scope.add(param_name, param_type)
                expr = frag[1]
                (ins1, out1) = process_expr(expr, scope)
                for (var1, in1) in ins1.items():
                    getvar(var1).connect(in1)
                dst = VarNode(sym, var)
                out1.connect(dst, 'assign')
                setvar(var, dst)
                
        elif stmt.tag == 'ExpressionStatement':
            assn = stmt[0]
            sym = assn[0]
            var = scope.lookup(sym.text)
            (ins1, out1) = process_expr(assn, scope)
            for (var1, in1) in ins1.items():
                getvar(var1).connect(in1)
            dst = VarNode(sym, var)
            out1.connect(dst, 'assign')
            setvar(var, dst)
            
        elif stmt.tag == 'ReturnStatement':
            expr = stmt[0]
            (ins1, out1) = process_expr(expr, scope)
            for (var1, in1) in ins1.items():
                getvar(var1).connect(in1)
            dst = ReturnNode(stmt)
            out1.connect(dst, 'assign')
            setvar(None, dst)
            
        elif stmt.tag == 'IfStatement':
            cid = Node.genid()
            expr = stmt[0]
            (ins1, cond1) = process_expr(expr, scope)
            for (var1, in1) in ins1.items():
                getvar(var1).connect(in1)
            then = stmt[1]
            (ins1, outs1) = process_block(then, scope)
            for (var1, in1) in ins1.items():
                branch = BranchNode(stmt, cid, cond1)
                branch.connect(in1)
                getvar(var1).connect(branch, 'true')
            for (var1, out1) in outs1.items():
                join = JoinNode(stmt, cid, cond1)
                out1.connect(join, 'true')
                getvar(var1).connect(join, 'false')
                setvar(var1, join)
            els = stmt[2]
            if els:
                (ins1, outs1) = process_block(els, scope)
                for (var1, in1) in ins1.items():
                    branch = BranchNode(stmt, cid, cond1)
                    branch.connect(in1)
                    getvar(var1).connect(branch, 'false')
                for (var1, out1) in outs1.items():
                    join = JoinNode(stmt, cid, cond1)
                    out1.connect(join, 'false')
                    getvar(var1).connect(join, 'true')
                    setvar(var1, join)
                
        elif stmt.tag == 'WhileStatement':
            cid = Node.genid()
            expr = stmt[0]
            (ins1, cond1) = process_expr(expr, scope)
            block = stmt[1]
            (ins2, outs2) = process_block(block, scope)
            loopvars = {}
            for var2 in outs2.keys():
                if var2 in ins1 or var2 in ins2:
                    loop = LoopNode(stmt, cid)
                    loopvars[var2] = loop
                    getvar(var2).connect(loop, 'init')
            for (var1, in1) in ins1.items():
                if var1 in loopvars:
                    loopvars[var1].connect(in1)
                else:
                    getvar(var1).connect(in1)
            for (var2, in2) in ins2.items():
                if var2 in loopvars:
                    branch = BranchNode(stmt, cid, cond1)
                    branch.connect(in2)
                    loopvars[var2].connect(branch, 'true')
                else:
                    getvar(var2).connect(in2)
            for (var2, out2) in outs2.items():
                if var2 in loopvars:
                    branch = BranchNode(stmt, cid, cond1)
                    branch.connect(loopvars[var2], 'cont')
                    out2.connect(branch, 'false')
                    setvar(var2, branch)
                else:
                    setvar(var2, out2)
            
    for var in scope.pop():
        assert var not in inputs
        if var in bindings:
            del bindings[var]
    return (inputs, bindings)

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
    func_name = efind(elem, 'SimpleName').text
    func_type = efind(elem, 'PrimitiveType').text
    bindings = {}
    for (i,param) in enumerate(elem.findall('SingleVariableDeclaration')):
        arg = FuncArgNode(param, i)
        param_name = efind(param, 'SimpleName').text
        param_type = efind(param, 'PrimitiveType').text
        var = scope.add(param_name, param_type)
        dst = VarNode(param, var)
        arg.connect(dst)
        bindings[var] = dst
        
    block = elem.find('Block')
    (ins1, outs1) = process_block(block, scope)
    for (var1, in1) in ins1.items():
        src = bindings[var1]
        src.connect(in1)

    allnodes = set()
    for node in bindings.values():
        visit_graph(node, allnodes)
    trim_graph(allnodes)
    
    exporter.open(func_name)
    for node in allnodes:
        exporter.put_node(node.nid, label=node.name())
    for node in allnodes:
        for (c,name) in node.send.items():
            if name == 'cond':
                exporter.put_edge(node.nid, c.nid, label=name, style='dotted')
            else:
                exporter.put_edge(node.nid, c.nid, label=name)
    exporter.close()
    return

def process_root(exporter, elem):
    for func in elem.iter('MethodDeclaration'):
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
