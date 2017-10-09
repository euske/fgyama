#!/usr/bin/env python
import sys
import os.path
import sqlite3
from graph import DFGraph, DFNode
from graph import get_graphs, build_graph_tables, store_graph


# get_nodekey
def get_nodekey(node):
    if node.ntype in ('select','begin','end','return'):
        return node.ntype
    elif node.data is not None:
        return node.data
    else:
        return None


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

    # stores the index of the graph.
    def index_graph(self, graph):
        visited = set()
        cur = self.cur

        def index_tree(label, node0, pids):
            if node0 in visited: return
            visited.add(node0)
            data = get_nodekey(node0)
            if data is not None:
                key = (label or '')+':'+data
                tids = [0]
                for pid in pids:
                    tid = self.get(pid, key)
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

    # searches subgraphs
    def search_graph(self, graph, minnodes=5, minbranches=2):
        cur = self.cur

        def match_tree(pid, label, node0, match):
            data = get_nodekey(node0)
            if data is not None:
                key = (label or '')+':'+data
                tid = self.get(pid, key)
                if tid is None: return 0
                rows = cur.execute(
                    'SELECT Gid,Nid FROM TreeLeaf WHERE Tid=?;',
                    (tid,))
                found = [ (gid,nid) for (gid,nid) in rows if graph.gid < gid ]
                if not found: return 0
                for (gid,nid) in found:
                    if gid in match:
                        pairs = match[gid]
                    else:
                        pairs = match[gid] = []
                    pairs.append((key, node0, nid))
                #print ('search:', pid, key, '->', tid, pairs)
                n = 0
                branches = 1
                for (label,src) in node0.inputs.items():
                    b = match_tree(tid, label, src, match)
                    if 0 < b:
                        n += 1
                        branches = max(branches, b)
            else:
                n = 0
                branches = 0
                for (_,src) in node0.inputs.items():
                    b = match_tree(pid, label, src, match)
                    if 0 < b:
                        n += 1
                        branches = max(branches, b)
            return max(branches, n)

        def filter_pairs(pairs):
            a = []
            nodes = set()
            nids = set()
            for (key,node,nid) in pairs:
                if node not in nodes and nid not in nids:
                    nodes.add(node)
                    nids.add(nid)
                    a.append((key,node,nid))
            return a

        votes = {}
        def find_tree(root):
            match = {}
            branches = match_tree(0, None, root, match)
            if branches < minbranches: return
            for (gid,pairs) in match.items():
                pairs = filter_pairs(pairs)
                if len(pairs) < minnodes: continue
                if (gid not in votes) or len(votes[gid]) < len(pairs):
                    votes[gid] = pairs
            return

        for node in graph.nodes.values():
            if not node.outputs:
                find_tree(node)

        return votes

# main
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
                store_graph(graphcur, cid, graph)
                cache.index_graph(graph)
            elif isinstance(graph, str):
                graphcur.execute(
                    'INSERT INTO SourceFile VALUES (NULL,?)',
                    (graph,))
                cid = graphcur.lastrowid
    graphconn.commit()
    indexconn.commit()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
