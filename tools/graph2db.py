#!/usr/bin/env python
import sys
import sqlite3
from graph import DFGraph, DFLink, DFNode
from graph import get_graphs, build_graph_tables, index_graph

class TreeCache:

    def __init__(self, cur):
        self.cur = cur
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
                cur.execute(
                    'INSERT INTO TreeNode VALUES (NULL,?,?);',
                    (pid, key))
                tid = cur.lastrowid
            self._cache[k] = tid
        return tid

def get_key(link, node, arg):
    if link is not None and link.label is not None:
        s = link.label+':'
    else:
        s = ':'
    if node.ntype == DFNode.N_Terminal and arg is not None:
        return s+arg
    elif node.ntype == DFNode.N_Operator:
        return s+node.label
    elif node.ntype == DFNode.N_Branch:
        return s+'branch'
    elif node.ntype == DFNode.N_Join:
        return s+'join'
    elif node.ntype == DFNode.N_Loop:
        return s+'loop'
    elif node.ntype == DFNode.N_Refer and not node.recv:
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

def get_terms(graph):
    labels = {}
    for node in graph.nodes.values():
        if node.ntype == DFNode.N_Terminal and not node.recv:
            label = 'N%s' % len(labels)
            labels[node] = label
    return labels

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

def index_graph_tree(cache, cur, graph):
    print (graph)
    terms = get_terms(graph)
    
    def index_tree(link0, node, pids, visited):
        if node in visited: return
        visited.add(node)
        key = get_key(link0, node, terms.get(node))
        #print (nid, key, len(pids))
        for link1 in node.recv:
            if key is not None:
                tids = [0]
                for pid in pids:
                    tid = cache.get(pid, key)
                    cur.execute(
                        'INSERT INTO TreeLeaf VALUES (?,?,?);',
                        (tid, graph.gid, node.nid))
                    #print (pid, key, '->', tid, node.nid)
                    tids.append(tid)
                pids = tids
            index_tree(link1, link1.src, pids, visited)
        return

    for node in graph.nodes.values():
        if not node.send:
            index_tree(None, node, [0], set())
    return

def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-o output] [-O index] [graph ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'o:O:')
    except getopt.GetoptError:
        return usage()
    
    graphname = ':memory:'
    indexname = ':memory:'
    for (k, v) in opts:
        if k == '-o': graphname = v
        elif k == '-O': indexname = v
        
    graphconn = sqlite3.connect(graphname)
    graphcur = graphconn.cursor()
    try:
        build_graph_tables(graphconn)
    except sqlite3.OperationalError:
        pass
    
    indexconn = sqlite3.connect(indexname)
    indexcur = indexconn.cursor()
    try:
        build_index_tables(indexconn)
    except sqlite3.OperationalError:
        pass
    
    cache = TreeCache(indexconn.cursor())
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
