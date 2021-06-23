#!/usr/bin/env python
import sys
import logging
from graphs import IDFBuilder, parsemethodname, parserefname
from graph2index import GraphDB
from algos import SCC, Cons

def gettail(name):
    (_,_,name) = name.rpartition('/')
    return name.lower()

# main
def main(argv):
    import getopt
    def usage():
        print(f'usage: {argv[0]} [-d] [-M maxoverrides] [-m maxpathlen] graph.db')
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'dM:m:')
    except getopt.GetoptError:
        return usage()
    level = logging.INFO
    maxoverrides = 1
    maxpathlen = 5
    for (k, v) in opts:
        if k == '-d': level = logging.DEBUG
        elif k == '-M': maxoverrides = int(v)
        elif k == '-m': maxpathlen = int(v)
    if not args: return usage()

    logging.basicConfig(format='%(asctime)s %(levelname)s %(message)s', level=level)

    path = args.pop(0)
    logging.info(f'Loading: {path!r}...')
    db = GraphDB(path)

    funargs = {
        # new java.io.File(arg0)
        'Ljava/io/File;.<init>(Ljava/lang/String;)V': ['#arg0'],
        # new java.io.FileReader(arg0)
        'Ljava/io/FileReader;.<init>(Ljava/lang/String;)V': ['#arg0'],
        # new java.io.FileWriter(arg0)
        'Ljava/io/FileWriter;.<init>(Ljava/lang/String;)V': ['#arg0'],
        # new java.io.FileWriter(arg0, *)
        'Ljava/io/FileWriter;.<init>(Ljava/lang/String;Z)V': ['#arg0'],
        # new java.io.FileInputStream(arg0)
        'Ljava/io/FileInputStream;.<init>(Ljava/lang/String;)V': ['#arg0'],
        # new java.io.FileOutputStream(arg0)
        'Ljava/io/FileOutputStream;.<init>(Ljava/lang/String;)V': ['#arg0'],
        # new java.io.FileOutputStream(arg0, *)
        'Ljava/io/FileOutputStream;.<init>(Ljava/lang/String;Z)V': ['#arg0'],
    }

    #logging.info(f'Running...')
    #builder.run()

    KINDS = {
        'value', 'valueset',
        'op_assign', 'op_prefix', 'op_infix', 'op_postfix',
        'op_typecast', 'op_typecheck',
        'ref_var', 'ref_array', 'ref_field',
        'call', 'new',
    }
    CALLS = {'call', 'new'}
    REFS = {'ref_var', 'ref_field', 'ref_array'}
    CONDS = {'join', 'begin', 'end', 'case', 'catchjoin'}
    def ignored(n):
        return (len(n.inputs) == 1 and n.kind not in KINDS)
    def addfeats(out, label, n):
        if n.kind not in KINDS: return
        if label.startswith('@'):
            label = '@'+gettail(parserefname(label))
        if n.kind in REFS:
            out.add(f'{label}:{n.kind}:{parserefname(n.ref)}')
        elif n.kind in CALLS:
            out.add(f'{label}:{n.kind}:')
        else:
            out.add(f'{label}:{n.kind}:{n.data}')
        return
    def visit(out, label0, n0, visited, n=maxpathlen):
        if n0 in visited: return
        visited.add(n0)
        if ignored(n0):
            for (label1,n1) in n0.inputs.items():
                if label1 == '#bypass': continue
                if label1.startswith('_'): continue
                visit(out, label0, n1, visited, n)
        else:
            addfeats(out, label0, n0)
            n -= 1
            if 0 < n:
                for (label1,n1) in n0.inputs.items():
                    if label1.startswith('_'): continue
                    if label1 == '#bypass': continue
                    if n1.kind == 'ref_array' and not label1: continue
                    visit(out, label1, n1, visited, n)

    # list all the methods and number of its uses. (being called)
    for method in db.get_allmethods():
        for node in method:
            if not node.is_funcall(): continue
            for func in node.data.split():
                if func not in funargs: continue
                for arg in funargs[func]:
                    out = set()
                    visit(out, arg, node.inputs[arg], set())
                    print(node)
                    for x in out:
                        print(' ',x)
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
