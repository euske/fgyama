#!/usr/bin/env python
import sys
import os.path
import sqlite3
from graph import DFMethod
from graph import GraphDB, get_graphs


# get_nodekey
def get_nodekey(node):
    if node.kind in (None, 'assign_var', 'ref_var'):
        return None
    else:
        return node.kind+':'+(node.data or '')


##  IndexDB
##
class IndexDB:

    def __init__(self, path, insert=False):
        self.insert = insert
        self._conn = sqlite3.connect(path)
        self._cur = self._conn.cursor()
        try:
            self._cur.executescript('''
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
        except sqlite3.OperationalError:
            pass
        self._cache = {}
        return

    def close(self):
        self._conn.commit()
        return

    def get(self, pid, key):
        cur = self._cur
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

    # stores the index of the method.
    def index_method(self, method):
        visited = set()
        cur = self._cur

        def index_tree(label0, node0, pids):
            if node0 in visited: return
            visited.add(node0)
            key = get_nodekey(node0)
            if key is not None:
                key = label0+':'+key
                tids = [0]
                for pid in pids:
                    tid = self.get(pid, key)
                    cur.execute(
                        'INSERT INTO TreeLeaf VALUES (?,?,?);',
                        (tid, method.gid, node0.nid))
                    tids.append(tid)
                #print ('index:', pids, key, '->', tids)
                for (label1,src) in node0.get_inputs():
                    index_tree(label1, src, tids)
            else:
                for (label1,src) in node0.get_inputs():
                    assert label1 is ''
                    index_tree(label0, src, pids)
            return

        print (method)
        for node in method:
            if not node.outputs:
                index_tree('', node, [0])
        return

    # searches subgraphs
    def search_method(self, method, minnodes=2, mindepth=2,
                     checkgid=(lambda method, gid: True)):
        cur = self._cur

        # match_tree:
        #   result: {gid: [(label,node0,nid1)]}
        #   pid: parent tid.
        #   label0: previous edge label0.
        #   node0: node to match.
        # returns (#nodes, #depth)
        def match_tree(result, pid, label0, node0, depth=1):
            key = get_nodekey(node0)
            if key is not None:
                key = label0+':'+key
                # descend a trie.
                tid = self.get(pid, key)
                if tid is None: return (0,0)
                rows = cur.execute(
                    'SELECT Gid,Nid FROM TreeLeaf WHERE Tid=?;',
                    (tid,))
                found = [ (gid1,nid1) for (gid1,nid1) in rows
                          if checkgid(method, gid1) ]
                if not found: return (0,0)
                for (gid1,nid1) in found:
                    if gid1 in result:
                        pairs = result[gid1]
                    else:
                        pairs = result[gid1] = []
                    pairs.append((depth, label0, node0, nid1))
                #print ('search:', pid, key, '->', tid, pairs)
                maxnodes = maxdepth = 0
                for (label1,src) in node0.get_inputs():
                    (n,d) = match_tree(result, tid, label1, src, depth+1)
                    maxnodes += n
                    maxdepth = max(d, maxdepth)
                maxnodes += 1
                maxdepth += 1
            else:
                # skip this node, using the same label.
                maxnodes = maxdepth = 0
                for (label1,src) in node0.get_inputs():
                    assert label1 is ''
                    (n,d) = match_tree(result, pid, label0, src, depth)
                    maxnodes += n
                    maxdepth = max(d, maxdepth)
            return (maxnodes,maxdepth)

        votes = {}
        for node in method:
            if node.outputs: continue
            # start from each terminal node.
            result = {}
            (maxnodes,maxdepth) = match_tree(result, 0, '', node)
            if maxnodes < minnodes: continue
            if maxdepth < mindepth: continue
            for (gid1,pairs) in result.items():
                maxnodes = len(set( node0.nid for (_,_,node0,_) in pairs ))
                if maxnodes < minnodes: continue
                maxnodes = len(set( nid1 for (_,_,_,nid1) in pairs ))
                if maxnodes < minnodes: continue
                maxdepth = max( d for (d,_,_,_) in pairs )
                if maxdepth < mindepth: continue
                if gid1 not in votes:
                    votes[gid1] = []
                votes[gid1].extend(pairs)
        return votes

# main
def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-c)ontinue] graph.db index.db [graph ...]' % argv[0])
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
    graphdb = GraphDB(path)

    if not args: return usage()
    path = args.pop(0)
    if isnew and os.path.exists(path): return exists(path)
    indexdb = IndexDB(path, insert=True)

    cid = None
    for path in args:
        for method in get_graphs(path):
            assert isinstance(method, DFMethod)
            path = method.klass.src
            if path is not None:
                cid = graphdb.add_src(path)
            assert cid is not None
            graphdb.add(cid, method)
            indexdb.index_method(method)
    graphdb.close()
    indexdb.close()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
