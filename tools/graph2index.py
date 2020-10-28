#!/usr/bin/env python
import sys
import os.path
import sqlite3
from graphs import DFMethod, get_graphs


# fetch1
def fetch1(cur, name):
    x = cur.fetchone()
    if x is None:
        raise ValueError(name)
    return x

# get_nodekey
def get_nodekey(node):
    if node.kind in (None, 'assign_var', 'ref_var'):
        return None
    else:
        return node.kind+':'+(node.data or '')


##  GraphDB
##
class GraphDB:

    def __init__(self, path):
        self._conn = sqlite3.connect(path)
        self._cur = self._conn.cursor()
        self._cur.executescript('''
CREATE TABLE IF NOT EXISTS ASTNode (
    Aid INTEGER PRIMARY KEY,
    Type INTEGER,
    Start INTEGER,
    End INTEGER
);

CREATE TABLE IF NOT EXISTS DFKlass (
    Kid INTEGER PRIMARY KEY,
    Name TEXT,
    Path TEXT,
    Interface INTEGER,
    Extends TEXT,
    Implements TEXT,
    Generic INTEGER
);
CREATE INDEX IF NOT EXISTS DFKlassNameIndex ON DFKlass(Name);

CREATE TABLE IF NOT EXISTS DFMethod (
    Gid INTEGER PRIMARY KEY,
    Kid INTEGER,
    Name TEXT,
    Style TEXT
);
CREATE INDEX IF NOT EXISTS DFMethodKidIndex ON DFMethod(Kid);
CREATE INDEX IF NOT EXISTS DFMethodNameIndex ON DFMethod(Name);

CREATE TABLE IF NOT EXISTS DFScope (
    Sid INTEGER PRIMARY KEY,
    Gid INTEGER,
    Parent INTEGER,
    Name TEXT
);
CREATE INDEX IF NOT EXISTS DFScopeGidIndex ON DFScope(Gid);

CREATE TABLE IF NOT EXISTS DFNode (
    Nid INTEGER PRIMARY KEY,
    Gid INTEGER,
    Sid INTEGER,
    Aid INTEGER,
    Kind TEXT,
    Ref TEXT,
    Data TEXT,
    Type TEXT
);
CREATE INDEX IF NOT EXISTS DFNodeGidIndex ON DFNode(Gid);

CREATE TABLE IF NOT EXISTS DFLink (
    Lid INTEGER PRIMARY KEY,
    Nid0 INTEGER,
    Nid1 INTEGER,
    Label TEXT
);
CREATE INDEX IF NOT EXISTS DFLinkNid0Index ON DFLink(Nid0);
''')
        return

    def __enter__(self):
        return self

    def __exit__(self):
        self.close()
        return

    def close(self):
        self._conn.commit()
        self._conn.close()
        return

    def add_klass(self, klass):
        cur = self._cur
        cur.execute(
            'INSERT INTO DFKlass VALUES (NULL,?,?,?,?,?,?)',
            (klass.name, klass.path, klass.interface, klass.extends,
             ' '.join(klass.implements), klass.generic))
        kid = cur.lastrowid
        klass.kid = kid
        return kid

    def add_method(self, method):
        assert method.klass.kid is not None
        cur = self._cur
        cur.execute(
            'INSERT INTO DFMethod VALUES (NULL,?,?,?);',
            (method.klass.kid, method.name, method.style))
        gid = cur.lastrowid
        method.gid = gid
        nids = {}

        def add_node(sid, node):
            aid = 0
            if node.ast is not None:
                cur.execute(
                    'INSERT INTO ASTNode VALUES (NULL,?,?,?);',
                    node.ast)
                aid = cur.lastrowid
            cur.execute(
                'INSERT INTO DFNode VALUES (NULL,?,?,?,?,?,?,?);',
                (gid, sid, aid, node.kind, node.ref, node.data, node.ntype))
            nid = cur.lastrowid
            node.nid = nid
            return nid

        def add_scope(scope, parent=0):
            cur.execute(
                'INSERT INTO DFScope VALUES (NULL,?,?,?);',
                (gid, parent, scope.name))
            sid = cur.lastrowid
            scope.sid = sid
            for node in scope.nodes:
                nids[node] = add_node(sid, node)
            for child in scope.children:
                add_scope(child, sid)
            return

        def add_link(node, src, label):
            cur.execute(
                'INSERT INTO DFLink VALUES (NULL,?,?,?);',
                (nids[node], nids[src], label))
            return

        add_scope(method.root)
        for node in method:
            for (label,src) in node.inputs.items():
                add_link(node, src, label)
        return gid

    def get_kids(self):
        cur = self._conn.cursor()
        for (kid,) in cur.execute('SELECT Kid FROM DFKlass;'):
            yield kid
        return

    def get_kidbyname(self, name):
        cur = self._cur
        cur.execute(
            'SELECT Kid FROM DFKlass WHERE Name=?;',
            (name,))
        (kid,) = fetch1(cur, f'DFKlass({name})')
        return kid

    def get_gids(self):
        cur = self._conn.cursor()
        for (gid,) in cur.execute('SELECT Gid FROM DFMethod;'):
            yield gid
        return

    def get_gidsbykid(self, kid):
        cur = self._conn.cursor()
        for (gid,) in cur.execute(
            'SELECT Gid FROM DFMethod WHERE Kid=?;',
            (kid,)):
            yield gid
        return

    def get_gidbyname(self, name):
        cur = self._cur
        cur.execute(
            'SELECT Gid FROM DFMethod WHERE Name=?;',
            (name,))
        (gid,) = fetch1(cur, f'DFMethod({name})')
        return gid

    def get_klass(self, kid):
        cur = self._cur
        cur.execute(
            'SELECT Name,Path,Interface,Extends,Implements,Generic FROM DFKlass WHERE Kid=?;',
            (kid,))
        (name,path,interface,extends,impls,generic) = fetch1(cur, f'DFKlass({kid})')
        if impls:
            implements = impls.split(' ')
        else:
            implements = []
        return DFKlass(name, path, interface, extends, implements, generic, kid)

    def get_method(self, gid, klass=None):
        cur = self._cur
        cur.execute(
            'SELECT Kid,Name,Style FROM DFMethod WHERE Gid=?;',
            (gid,))
        (kid,name,style) = fetch1(cur, f'DFMethod({gid})')
        if klass is None:
            klass = self.get_klass(kid)
        method = DFMethod(klass, name, style, gid)
        rows = cur.execute(
            'SELECT Sid,Parent,Name FROM DFScope WHERE Gid=?;',
            (gid,))
        pids = {}
        scopes = method.scopes
        for (sid,parent,name) in rows:
            scope = DFScope(method, sid, name)
            scopes[sid] = scope
            pids[sid] = parent
            if parent == 0:
                method.root = scope
        for (sid,parent) in pids.items():
            if parent != 0:
                scopes[sid].set_parent(scopes[parent])
        rows = cur.execute(
            'SELECT Nid,Sid,Aid,Kind,Ref,Data,Type FROM DFNode WHERE Gid=?;',
            (gid,))
        for (nid,sid,aid,kind,ref,data,ntype) in list(rows):
            scope = scopes[sid]
            node = DFNode(method, nid, scope, kind, ref, data, ntype)
            rows = cur.execute(
                'SELECT Type,Start,End FROM ASTNode WHERE Aid=?;',
                (aid,))
            for (t,s,e) in rows:
                node.ast = (t,s,e)
            method.nodes[nid] = node
            scope.nodes.append(node)
        for (nid0,node) in method.nodes.items():
            rows = cur.execute(
                'SELECT Lid,Nid1,Label FROM DFLink WHERE Nid0=?;',
                (nid0,))
            for (lid,nid1,label) in rows:
                node.inputs[label] = nid1
        method.fixate()
        return method

    def get_allmethods(self):
        for kid in self.get_kids():
            klass = self.get_klass(kid)
            for gid in self.get_gidsbykid(kid):
                yield self.get_method(gid, klass)
        return


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
        self._conn.close()
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
        print(f'usage: {argv[0]} [-c)ontinue] graph.db index.db [graph ...]')
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'c')
    except getopt.GetoptError:
        return usage()

    isnew = True
    for (k, v) in opts:
        if k == '-c': isnew = False

    def exists(path):
        print(f'already exists: {path}')
        return 111

    if not args: return usage()
    path = args.pop(0)
    if isnew and os.path.exists(path): return exists(path)
    graphdb = GraphDB(path)

    if not args: return usage()
    path = args.pop(0)
    if isnew and os.path.exists(path): return exists(path)
    indexdb = IndexDB(path, insert=True)

    klass = None
    cid = None
    for path in args:
        for method in get_graphs(path):
            assert isinstance(method, DFMethod)
            if klass is not method.klass:
                klass = method.klass
                graphdb.add_klass(klass)
            graphdb.add_method(method)
            indexdb.index_method(method)
    graphdb.close()
    indexdb.close()

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
