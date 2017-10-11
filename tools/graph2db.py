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

        def index_tree(label0, node0, pids):
            if node0 in visited: return
            visited.add(node0)
            data = get_nodekey(node0)
            if data is not None:
                key = (label0 or '')+':'+data
                tids = [0]
                for pid in pids:
                    tid = self.get(pid, key)
                    cur.execute(
                        'INSERT INTO TreeLeaf VALUES (?,?,?);',
                        (tid, graph.gid, node0.nid))
                    tids.append(tid)
                #print ('index:', pids, key, '->', tids)
                for (label1,src) in node0.inputs.items():
                    index_tree(label1, src, tids)
            else:
                for (label1,src) in node0.inputs.items():
                    assert label1 is None
                    index_tree(label0, src, pids)
            return

        print (graph)
        for node in graph.nodes.values():
            if not node.outputs:
                index_tree(None, node, [0])
        return

    # searches subgraphs
    def search_graph(self, graph, minnodes=2, mindepth=2,
                     checkgid=(lambda graph, gid: True)):
        cur = self.cur

        # match_tree:
        #   result: {gid: [(label,node0,nid1)]}
        #   pid: parent tid.
        #   label0: previous edge label0.
        #   node0: node to match.
        # returns (#nodes, #depth)
        def match_tree(result, pid, label0, node0):
            data = get_nodekey(node0)
            if data is not None:
                key = (label0 or '')+':'+data
                # descend a trie.
                tid = self.get(pid, key)
                if tid is None: return (0,0)
                rows = cur.execute(
                    'SELECT Gid,Nid FROM TreeLeaf WHERE Tid=?;',
                    (tid,))
                found = [ (gid1,nid1) for (gid1,nid1) in rows
                          if checkgid(graph, gid1) ]
                if not found: return (0,0)
                for (gid1,nid1) in found:
                    if gid1 in result:
                        pairs = result[gid1]
                    else:
                        pairs = result[gid1] = []
                    pairs.append((label0, node0, nid1))
                #print ('search:', pid, key, '->', tid, pairs)
                nodes = depth = 0
                for (label1,src) in node0.inputs.items():
                    (n,d) = match_tree(result, tid, label1, src)
                    nodes += n
                    depth = max(d, depth)
                nodes += 1
                depth += 1
            else:
                # skip this node, using the same label.
                nodes = depth = 0
                for (label1,src) in node0.inputs.items():
                    assert label1 is None
                    (n,d) = match_tree(result, pid, label0, src)
                    nodes += n
                    depth = max(d, depth)
            return (nodes,depth)

        votes = {}
        for node in graph.nodes.values():
            if node.outputs: continue
            # start from each terminal node.
            result = {}
            (maxnodes,maxdepth) = match_tree(result, 0, None, node)
            if maxnodes < minnodes: continue
            if maxdepth < mindepth: continue
            for (gid1,pairs) in result.items():
                maxnodes = len(set( nid for (_,_,nid) in pairs ))
                if maxnodes < minnodes: continue
                if gid1 not in votes:
                    votes[gid1] = []
                votes[gid1].extend(pairs)
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
    src = None
    for path in args:
        for graph in get_graphs(path):
            assert isinstance(graph, DFGraph)
            if graph.src is not None and graph.src != src:
                src = graph.src
                graphcur.execute(
                    'INSERT INTO SourceFile VALUES (NULL,?)',
                    (src,))
                cid = graphcur.lastrowid
            assert cid is not None
            store_graph(graphcur, cid, graph)
            cache.index_graph(graph)
    graphconn.commit()
    indexconn.commit()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
