#!/usr/bin/env python
import sys
import os.path
import sqlite3
from graphs import DFKlass, DFMethod, DFScope, DFNode, get_graphs


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

    def __init__(self, path, create=False):
        if not create and not os.path.exists(path):
            raise IOError(f'not found: {path}')
        elif create and os.path.exists(path):
            raise IOError(f'already exists: {path}')
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
    Mid INTEGER PRIMARY KEY,
    Kid INTEGER,
    Name TEXT,
    Style TEXT
);
CREATE INDEX IF NOT EXISTS DFMethodKidIndex ON DFMethod(Kid);
CREATE INDEX IF NOT EXISTS DFMethodNameIndex ON DFMethod(Name);

CREATE TABLE IF NOT EXISTS DFFuncCall (
    Name TEXT,
    Nid INTEGER
);

CREATE TABLE IF NOT EXISTS DFScope (
    Sid INTEGER PRIMARY KEY,
    Mid INTEGER,
    Parent INTEGER,
    Name TEXT
);
CREATE INDEX IF NOT EXISTS DFScopeMidIndex ON DFScope(Mid);

CREATE TABLE IF NOT EXISTS DFNode (
    Nid INTEGER PRIMARY KEY,
    Mid INTEGER,
    Sid INTEGER,
    Aid INTEGER,
    Kind TEXT,
    Rid INTEGER,
    Data TEXT,
    Type TEXT
);
CREATE INDEX IF NOT EXISTS DFNodeMidIndex ON DFNode(Mid);

CREATE TABLE IF NOT EXISTS DFRef (
    Rid INTEGER PRIMARY KEY,
    Name TEXT
);
CREATE INDEX IF NOT EXISTS DFRefNameIndex ON DFRef(Name);

CREATE TABLE IF NOT EXISTS DFLink (
    Lid INTEGER PRIMARY KEY,
    Nid0 INTEGER,
    Nid1 INTEGER,
    Label TEXT
);
CREATE INDEX IF NOT EXISTS DFLinkNid0Index ON DFLink(Nid0);
''')
        self._rids = {}
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
        mid = cur.lastrowid
        method.mid = mid
        nids = {}

        def add_node(sid, node):
            aid = 0
            if node.ast is not None:
                cur.execute(
                    'INSERT INTO ASTNode VALUES (NULL,?,?,?);',
                    node.ast)
                aid = cur.lastrowid
            ref = node.ref
            rid = 0
            if ref is not None:
                if ref in self._rids:
                    rid = self._rids[ref]
                else:
                    rid = len(self._rids)+1
                    self._rids[ref] = rid
                    cur.execute(
                        'INSERT INTO DFRef VALUES (?,?);',
                        (rid, ref))
            cur.execute(
                'INSERT INTO DFNode VALUES (NULL,?,?,?,?,?,?,?);',
                (mid, sid, aid, node.kind, rid, node.data, node.ntype))
            nid = cur.lastrowid
            node.nid = nid
            if node.is_funcall():
                for name in node.data.split():
                    cur.execute(
                        'INSERT INTO DFFuncCall VALUES (?,?);',
                        (name, nid))
            return nid

        def add_scope(scope, parent=0):
            cur.execute(
                'INSERT INTO DFScope VALUES (NULL,?,?,?);',
                (mid, parent, scope.name))
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

        if method.root is not None:
            add_scope(method.root)
            for node in method:
                for (label,src) in node.inputs.items():
                    add_link(node, src, label)
        return mid

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

    def get_mids(self):
        cur = self._conn.cursor()
        for (mid,) in cur.execute('SELECT Mid FROM DFMethod;'):
            yield mid
        return

    def get_midsbykid(self, kid):
        cur = self._conn.cursor()
        for (mid,) in cur.execute(
            'SELECT Mid FROM DFMethod WHERE Kid=?;',
            (kid,)):
            yield mid
        return

    def get_midbyname(self, name):
        cur = self._cur
        cur.execute(
            'SELECT Mid FROM DFMethod WHERE Name=?;',
            (name,))
        (mid,) = fetch1(cur, f'DFMethod({name})')
        return mid

    def get_funcalls(self, mid):
        cur = self._cur
        rows = cur.execute(
            'SELECT DFNode.Nid, DFNode.Mid FROM DFMethodCall, DFNode WHERE Mid=? AND DFMethodCall.Nid=DFNode.Nid;',
            (mid,))
        return list(rows)

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

    def get_method(self, mid, klass=None):
        cur = self._cur
        cur.execute(
            'SELECT Kid,Name,Style FROM DFMethod WHERE Mid=?;',
            (mid,))
        (kid,name,style) = fetch1(cur, f'DFMethod({mid})')
        if klass is None:
            klass = self.get_klass(kid)
        method = DFMethod(klass, name, style, mid)
        rows = cur.execute(
            'SELECT Sid,Parent,Name FROM DFScope WHERE Mid=?;',
            (mid,))
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
            'SELECT Nid,Sid,Aid,Kind,Rid,Data,Type FROM DFNode WHERE Mid=?;',
            (mid,))
        for (nid,sid,aid,kind,rid,data,ntype) in list(rows):
            scope = scopes[sid]
            try:
                (ref,) = fetch1(cur.execute(
                    'SELECT Name FROM DFRef WHERE Rid=?;',
                    (rid,)), f'DFRef({rid})')
            except ValueError:
                ref = None
            node = DFNode(method, nid, scope, kind, ref, data, ntype)
            try:
                node.ast = fetch1(cur.execute(
                    'SELECT Type,Start,End FROM ASTNode WHERE Aid=?;',
                    (aid,)), f'ASTNode({aid})')
            except ValueError:
                pass
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
            for mid in self.get_midsbykid(kid):
                yield self.get_method(mid, klass)
        return


##  IndexDB
##
class IndexDB:

    def __init__(self, path, insert=False, create=False):
        if not create and not os.path.exists(path):
            raise IOError(f'not found: {path}')
        elif create and os.path.exists(path):
            raise IOError(f'already exists: {path}')
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
    Mid INTEGER,
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
                        (tid, method.mid, node0.nid))
                    tids.append(tid)
                #print ('index:', pids, key, '->', tids)
                for (label1,src) in node0.get_inputs():
                    index_tree(label1, src, tids)
            else:
                for (label1,src) in node0.get_inputs():
                    #assert label1 == ''
                    index_tree(label0, src, pids)
            return

        for node in method:
            if not node.outputs:
                index_tree('', node, [0])
        return

    # searches subgraphs
    def search_method(self, method, minnodes=2, mindepth=2,
                     checkmid=(lambda method, mid: True)):
        cur = self._cur

        # match_tree:
        #   result: {mid: [(label,node0,nid1)]}
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
                    'SELECT Mid,Nid FROM TreeLeaf WHERE Tid=?;',
                    (tid,))
                found = [ (mid1,nid1) for (mids1,nid1) in rows
                          if checkmid(method, mid1) ]
                if not found: return (0,0)
                for (mid1,nid1) in found:
                    if mid1 in result:
                        pairs = result[mid1]
                    else:
                        pairs = result[mid1] = []
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
                    assert label1 == ''
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
            for (mid1,pairs) in result.items():
                maxnodes = len(set( node0.nid for (_,_,node0,_) in pairs ))
                if maxnodes < minnodes: continue
                maxnodes = len(set( nid1 for (_,_,_,nid1) in pairs ))
                if maxnodes < minnodes: continue
                maxdepth = max( d for (d,_,_,_) in pairs )
                if maxdepth < mindepth: continue
                if mid1 not in votes:
                    votes[mid1] = []
                votes[mid1].extend(pairs)
        return votes

# main
def main(argv):
    import fileinput
    import getopt
    def usage():
        print(f'usage: {argv[0]} [-a)ppend] [-i index.db] graph.db [graph ...]')
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'ai:')
    except getopt.GetoptError:
        return usage()

    create = True
    indexpath = None
    for (k, v) in opts:
        if k == '-a': create = False
        elif k == '-i': indexpath = v

    if not args: return usage()

    path = args.pop(0)
    graphdb = GraphDB(path, create=create)

    indexdb = None
    if indexpath is not None:
        indexdb = IndexDB(indexpath, insert=True, create=create)

    klass = None
    cid = None
    for path in args:
        for method in get_graphs(path):
            assert isinstance(method, DFMethod)
            if klass is not method.klass:
                klass = method.klass
                graphdb.add_klass(klass)
            graphdb.add_method(method)
            if indexdb is not None:
                indexdb.index_method(method)
    graphdb.close()
    if indexdb is not None:
        indexdb.close()

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
