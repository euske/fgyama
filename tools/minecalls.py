#!/usr/bin/env python
import sys
import logging
from graphs import IDFBuilder, parsemethodname
from graph2index import GraphDB
from algos import SCC, Cons

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
    maxpathlen = 3
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
    def trace(n0, n=maxpathlen):
        inputs = n0.inputs
        if 1 < len(inputs) or n0.kind in KINDS:
            a = [n0.kind]
            n -= 1
            if 0 < n:
                for (label,n1) in n0.inputs.items():
                    z = trace(n1, n)
                    if z is not None:
                        a.append(z)
            return a
        else:
            for (_,n1) in n0.inputs.items():
                return trace(n1, n)
            return None

    # list all the methods and number of its uses. (being called)
    for method in db.get_allmethods():
        for node in method:
            if not node.is_funcall(): continue
            for func in node.data.split():
                if func not in funargs: continue
                for arg in funargs[func]:
                    a = trace(node.inputs[arg])
                    print(node.method, a)
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
