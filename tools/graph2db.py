#!/usr/bin/env python
import sys
import sqlite3
from graph2gv import load_graphs
from graph2gv import Graph, Scope, Link, Node

def build_tables(cur):
    cur.executescript('''
CREATE TABLE SourceFile (
    Cid INTEGER PRIMARY KEY,
    FileName TEXT
);

CREATE TABLE ASTNode (
    Aid INTEGER PRIMARY KEY,
    Cid INTEGER,
    Type INTEGER,
    Start INTEGER,
    End INTEGER
);

CREATE TABLE DFGraph (
    Gid INTEGER PRIMARY KEY
);

CREATE TABLE DFNode (
    Nid INTEGER,
    Gid INTEGER,
    Aid INTEGER,
    Type INTEGER,
    Ref TEXT,
    Label TEXT
);

CREATE TABLE TreeNode (
    Nid INTEGER PRIMARY KEY,
    Pid INTEGER,
    Label TEXT
);

''')
    return

def get_label(link, label):
    node = link.src
    if node.ntype == Node.N_Terminal and label is not None:
        return ':'+label
    elif node.ntype == Node.N_Operator:
        return node.label
    elif node.ntype == Node.N_Branch:
        return 'branch'
    elif node.ntype == Node.N_Join:
        return 'join'
    elif node.ntype == Node.N_Loop:
        return 'loop'
    elif node.ntype == Node.N_Refer and not node.recv:
        return '='+node.label
    return None

def get_length(node):
    if node.recv:
        return 1+max(( get_length(link.src) for link in node.recv ))
    else:
        return 0

def find_chain(graph):
    ends = []
    for node in graph.nodes.values():
        if node.send: continue
        length = get_length(node)
        ends.append((length, node))
    return sorted(ends, key=lambda x:x[0], reverse=True)

def get_args(graph):
    labels = {}
    for node in graph.nodes.values():
        if node.ntype == Node.N_Terminal and not node.recv:
            label = 'N%s' % len(labels)
            labels[node] = label
    return labels

def index_graph(cur, cid, graph):
    cur.execute('INSERT INTO DFGraph VALUES (NULL);')
    gid = cur.lastrowid
    args = get_args(graph)
    graph.dump()
    print (cid, graph, args)
    def index_node(parent, pid=0):
        for link in parent.recv:
            node = link.src
            label = get_label(link, args.get(node))
            if label is None:
                nid = pid
            else:
                cur.execute('SELECT Nid FROM TreeNode WHERE Pid=? AND Label=?;',
                            (pid, label))
                result = cur.fetchone()
                if result is not None:
                    (nid,) = result
                else:
                    cur.execute('INSERT INTO TreeNode VALUES (NULL,?,?);',
                                (pid, label))
                    nid = cur.lastrowid
                print (' ',pid, label, '->', nid)
                aid = 0
                if node.ast is not None:
                    (t,s,e) = node.ast
                    cur.execute('INSERT INTO ASTNode VALUES (NULL,?,?,?,?);',
                                (cid, t, s, e))
                    aid = cur.lastrowid
                cur.execute('INSERT INTO DFNode VALUES (?,?,?,?,?,?);',
                            (nid, gid, aid, node.ntype, node.ref, node.label))
            index_node(node, nid)
        return
    for node in graph.nodes.values():
        if not node.send:
            index_node(node)
    return

def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-o output] [graph ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'o:')
    except getopt.GetoptError:
        return usage()
    dbname = ':memory:'
    for (k, v) in opts:
        if k == '-o': dbname = v
    if not args: return usage()

    conn = sqlite3.connect(dbname)
    cur = conn.cursor()
    try:
        build_tables(conn)
    except sqlite3.OperationalError:
        pass
    with fileinput.input(args) as fp:
        cid = None
        for graph in load_graphs(fp):
            if isinstance(graph, Graph):
                assert cid is not None
                index_graph(cur, cid, graph)
            elif isinstance(graph, str):
                cur.execute('INSERT INTO SourceFile VALUES (NULL,?)',
                            (graph,))
                cid = cur.lastrowid
    conn.commit()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
