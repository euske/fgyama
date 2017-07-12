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

CREATE TABLE DFScope (
    Sid INTEGER PRIMARY KEY,
    Pid INTEGER,
    Name TEXT
);

CREATE TABLE ASTNode (
    Aid INTEGER PRIMARY KEY,
    Type INTEGER,
    Cid INTEGER,
    Start INTEGER,
    End INTEGER
);

CREATE TABLE DFNode (
    Nid INTEGER PRIMARY KEY,
    Gid INTEGER,
    Sid INTEGER,
    Aid INTEGER,
    Type INTEGER,
    Ref TEXT,
    Arg TEXT
);

CREATE TABLE DFLink (
    Lid INTEGER PRIMARY KEY,
    Nid0 INTEGER,
    Nid1 INTEGER,
    Type INTEGER,
    Arg TEXT
);

-- CREATE TABLE Subgraphs (Sgid INTEGER PRIMARY KEY, Nid INTEGER, Pattern TEXT);
''')
    return

def index_graph(cur, cid, graph):
    nids = {}
    def index_scope(scope, pid=0, gid=0):
        cur.execute('INSERT INTO DFScope VALUES (NULL,?,?);', (pid, scope.sid))
        sid = cur.lastrowid
        if pid == 0:
            gid = sid
        for node in scope.nodes:
            aid = None
            if node.ast is not None:
                (t,s,e) = node.ast
                cur.execute('INSERT INTO ASTNode VALUES (NULL,?,?,?,?);',
                            (t, cid, s, e))
                aid = cur.lastrowid
            cur.execute('INSERT INTO DFNode VALUES (NULL,?,?,?,?,?,?);',
                        (gid, sid, aid, node.ntype, node.ref, node.label))
            nids[node.nid] = cur.lastrowid
        for child in scope.children:
            index_scope(child, sid, gid)
        return
    index_scope(graph.root)
    for node in graph.nodes.values():
        for link in node.send:
            cur.execute('INSERT INTO DFLink VALUES (NULL,?,?,?,?);',
                        (nids[link.src], nids[link.dst], link.ltype, link.name))
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
            else:
                cur.execute('INSERT INTO SourceFile VALUES (NULL,?)',
                            (graph,))
                cid = cur.lastrowid
    conn.commit()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
