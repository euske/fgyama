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
    Gid INTEGER PRIMARY KEY,
    Name TEXT
);

CREATE TABLE DFNode (
    Nid INTEGER PRIMARY KEY,
    Gid INTEGER,
    Aid INTEGER,
    Type INTEGER,
    Ref TEXT,
    Label TEXT
);

CREATE TABLE DFLink (
    Lid INTEGER PRIMARY KEY,
    Idx INTEGER,
    Nid0 INTEGER,
    Nid1 INTEGER,
    Type INTEGER,
    Name TEXT
);
    
CREATE TABLE TreeNode (
    Tid INTEGER PRIMARY KEY,
    Pid INTEGER,
    Key TEXT
);

CREATE TABLE TreeLeaf (
    Tid INTEGER,
    Gid INTEGER,
    Nid INTEGER
);
''')
    return

class DBCache:

    def __init__(self, cur):
        self.cur = cur
        self._cache = {}
        return

    def get(self, pid, key):
        cur = self.cur
        k = (pid,key)
        if k in self._cache:
            return self._cache[k]
        cur.execute('SELECT Tid FROM TreeNode WHERE Pid=? AND Key=?;',
                    (pid, key))
        result = cur.fetchone()
        if result is not None:
            (tid,) = result
        else:
            cur.execute('INSERT INTO TreeNode VALUES (NULL,?,?);',
                        (pid, key))
            tid = cur.lastrowid
        self._cache[k] = tid
        return tid

def get_key(link, node, arg):
    if link is not None and link.name is not None:
        s = link.name+':'
    else:
        s = ':'
    if node.ntype == Node.N_Terminal and arg is not None:
        return s+arg
    elif node.ntype == Node.N_Operator:
        return s+node.label
    elif node.ntype == Node.N_Branch:
        return s+'branch'
    elif node.ntype == Node.N_Join:
        return s+'join'
    elif node.ntype == Node.N_Loop:
        return s+'loop'
    elif node.ntype == Node.N_Refer and not node.recv:
        return s+'='+node.label
    else:
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

def index_graph(db, cur, cid, graph):
    print (cid, graph.name)
    #graph.dump()
    cur.execute('INSERT INTO DFGraph VALUES (NULL, ?);',
                (graph.name,))
    gid = cur.lastrowid

    nids = {}
    def index_node(node):
        aid = 0
        if node.ast is not None:
            (t,s,e) = node.ast
            cur.execute('INSERT INTO ASTNode VALUES (NULL,?,?,?,?);',
                        (cid, t, s, e))
            aid = cur.lastrowid
        cur.execute('INSERT INTO DFNode VALUES (NULL,?,?,?,?,?);',
                    (gid, aid, node.ntype, node.ref, node.label))
        nid = cur.lastrowid
        nids[node] = nid
        return nid

    def index_link(link):
        cur.execute('INSERT INTO DFLink VALUES (NULL,?,?,?,?,?);',
                    (link.lid, nids[link.src], nids[link.dst],
                     link.ltype, link.name))
        return
    
    args = get_args(graph)
    visited = set()
    def index_tree(link0, node, pids, level=0):
        if node in visited: return
        visited.add(node)
        nid = nids[node]
        key = get_key(link0, node, args.get(node))
        #print (level, nid, key, len(pids))
        for link1 in node.recv:
            if key is not None:
                tids = [0]
                for pid in pids:
                    tid = db.get(pid, key)
                    cur.execute('INSERT INTO TreeLeaf VALUES (?,?,?);',
                                (tid, gid, nid))
                    #print (pid, key, '->', tid, nid)
                    tids.append(tid)
                pids = tids
            index_tree(link1, link1.src, pids, level+1)
        return
    
    for node in graph.nodes.values():
        index_node(node)
    for node in graph.nodes.values():
        for link in node.send:
            index_link(link)
    for node in graph.nodes.values():
        if not node.send:
            index_tree(None, node, [0])
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
    db = DBCache(cur)
    with fileinput.input(args) as fp:
        cid = None
        for graph in load_graphs(fp):
            if isinstance(graph, Graph):
                assert cid is not None
                index_graph(db, cur, cid, graph)
            elif isinstance(graph, str):
                cur.execute('INSERT INTO SourceFile VALUES (NULL,?)',
                            (graph,))
                cid = cur.lastrowid
    conn.commit()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
