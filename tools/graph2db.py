#!/usr/bin/env python
import sys
import os.path
import sqlite3
from graph import DFGraph, DFNode
from graph import get_graphs, build_graph_tables, index_graph


##  build_index_tables
##
def build_index_tables(cur):
    cur.executescript('''
CREATE TABLE TreeNode (
    Tid INTEGER PRIMARY KEY,
    Pid INTEGER,
    Key TEXT
);
CREATE INDEX TreeNodePidAndKeyIndex ON TreeNode(Pid, Key);

CREATE TABLE TreeLeaf (
    Tid INTEGER,
    Gid INTEGER,
    Nid INTEGER
);
CREATE INDEX TreeLeafTidIndex ON TreeLeaf(Tid);
''')
    return


##  TreeCache
##
class TreeCache:

    def __init__(self, cur, insert=False):
        self.cur = cur
        self.insert = insert
        self._cache = {}
        return

    def get(self, pid, key):
        cur = self.cur
        k = (pid,key)
        if k in self._cache:
            tid = self._cache[k]
        else:
            cur.execute(
                'SELECT Tid FROM TreeNode WHERE Pid=? AND Key=?;',
                (pid, key))
            result = cur.fetchone()
            if result is not None:
                (tid,) = result
            else:
                if not self.insert: return None
                cur.execute(
                    'INSERT INTO TreeNode VALUES (NULL,?,?);',
                    (pid, key))
                tid = cur.lastrowid
            self._cache[k] = tid
        return tid

# is_key
def get_data(node):
    if node.ntype in ('select','begin','end','return'):
        return node.ntype
    elif node.data is not None:
        return node.data
    else:
        return None

def index_graph_tree(cache, cur, graph):
    visited = set()
    
    def index_tree(label, node0, pids):
        if node0 in visited: return
        visited.add(node0)
        data = get_data(node0)
        if data is not None:
            key = (label or '')+':'+data
            tids = [0]
            for pid in pids:
                tid = cache.get(pid, key)
                cur.execute(
                    'INSERT INTO TreeLeaf VALUES (?,?,?);',
                    (tid, graph.gid, node0.nid))
                tids.append(tid)
            #print ('index:', pids, key, '->', tids)
            for (label,src) in node0.inputs.items():
                index_tree(label, src, tids)
        else:
            for (_,src) in node0.inputs.items():
                index_tree(label, src, pids)
        return

    print (graph)
    for node in graph.nodes.values():
        if not node.outputs:
            index_tree(None, node, [0])
    return

def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-c] graph.db index.db [graph ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'c')
    except getopt.GetoptError:
        return usage()
    
    isnew = True
    for (k, v) in opts:
        if k == '-c': isnew = False
    
    def exists(path):
        print('already exists: %r' % path)
        return 111
    if not args: return usage()
    path = args.pop(0)
    if isnew and os.path.exists(path): return exists(path)
    graphconn = sqlite3.connect(path)
    graphcur = graphconn.cursor()
    try:
        build_graph_tables(graphconn)
    except sqlite3.OperationalError:
        pass
    
    if not args: return usage()
    path = args.pop(0)
    if isnew and os.path.exists(path): return exists(path)
    indexconn = sqlite3.connect(path)
    indexcur = indexconn.cursor()
    try:
        build_index_tables(indexconn)
    except sqlite3.OperationalError:
        pass
    
    cache = TreeCache(indexconn.cursor(), insert=True)
    cid = None
    for path in args:
        for graph in get_graphs(path):
            if isinstance(graph, DFGraph):
                assert cid is not None
                index_graph(graphcur, cid, graph)
                index_graph_tree(cache, indexcur, graph)
            elif isinstance(graph, str):
                graphcur.execute(
                    'INSERT INTO SourceFile VALUES (NULL,?)',
                    (graph,))
                cid = graphcur.lastrowid
    graphconn.commit()
    indexconn.commit()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
