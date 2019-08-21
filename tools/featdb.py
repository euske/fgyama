#!/usr/bin/env python
#
# feat2db.py - store features to sqlite3.
#
import sys
import os.path
import marshal
import json
import sqlite3


##  FeatDB
##
class FeatDB:

    def __init__(self, path):
        self._conn = sqlite3.connect(path)
        self._cur = self._conn.cursor()
        self._srcmap = {}
        self._featmap = {}
        self._items = []
        self._itemmap = {}
        return

    def __iter__(self):
        return iter(self.get_items())

    def init(self):
        self._cur.executescript('''
CREATE TABLE Sources (
    SrcId INTEGER PRIMARY KEY,
    Path TEXT NOT NULL
);
CREATE TABLE Feats (
    Fid INTEGER PRIMARY KEY,
    Feat TEXT NOT NULL
);
CREATE TABLE Items (
    Tid INTEGER PRIMARY KEY,
    Item TEXT NOT NULL
);
CREATE TABLE ItemFeatSrcs (
    Sid INTEGER PRIMARY KEY,
    SrcId INTEGER,
    Start INTEGER,
    End INTEGER
);
CREATE TABLE ItemFeats (
    Sid0 INTEGER PRIMARY KEY,
    Sid1 INTEGER,
    Tid INTEGER,
    Fid INTEGER
);
CREATE INDEX ItemFeatsByTid ON ItemFeats(Tid);
CREATE INDEX ItemFeatsByFid ON ItemFeats(Fid);
''')
        return

    def close(self):
        self._conn.commit()
        return

    def add_path(self, srcid, path):
        assert srcid not in self._srcmap
        self._cur.execute(
            'INSERT INTO Sources VALUES (?,?);',
            (srcid+1, path))
        self._srcmap[srcid] = path
        return

    def get_path(self, srcid):
        if srcid in self._srcmap:
            path = self._srcmap[srcid]
        else:
            self._cur.execute(
                'SELECT Path FROM Sources WHERE SrcId=?;',
                (srcid+1,))
            (path,) = self._cur.fetchone()
            self._srcmap[srcid] = path
        return path

    def get_sources(self, sid0, sid1):
        self._cur.execute(
            'SELECT SrcId,Start,End FROM ItemFeatSrcs WHERE Sid BETWEEN ? AND ?;',
            (sid0, sid1))
        srcs = []
        for (srcid,start,end) in list(self._cur.fetchall()):
            if srcid < 0: continue
            path = self.get_path(srcid)
            srcs.append((path, start, end))
        return srcs

    def add_feat(self, feat, fid):
        assert fid not in self._featmap
        data = marshal.dumps(feat)
        self._cur.execute(
            'INSERT INTO Feats VALUES (?,?);',
            (fid, data))
        self._featmap[fid] = feat
        return

    def get_feat(self, fid):
        if fid == 0:
            return None
        elif fid in self._featmap:
            feat = self._featmap[fid]
        else:
            self._cur.execute(
                'SELECT Feat FROM Feats WHERE Fid=?;',
                (fid,))
            (data,) = self._cur.fetchone()
            feat = marshal.loads(data)
            self._featmap[fid] = feat
        return feat

    def add_item(self, item, fid2srcs):
        self._cur.execute(
            'INSERT INTO Items VALUES (NULL,?);',
            (item,))
        tid = self._cur.lastrowid
        assert tid not in self._itemmap
        for (fid,srcs) in fid2srcs.items():
            if srcs:
                sid0 = 0
                for (srcid,start,end) in srcs:
                    self._cur.execute(
                        'INSERT INTO ItemFeatSrcs VALUES (NULL,?,?,?);',
                        (srcid, start, end))
                    sid1 = self._cur.lastrowid
                    if sid0 == 0:
                        sid0 = sid1
                assert sid0 != 0
            else:
                self._cur.execute(
                    'INSERT INTO ItemFeatSrcs VALUES (NULL,?,?,?);',
                    (-1, 0, 0))
                sid0 = sid1 = self._cur.lastrowid
            self._cur.execute(
                'INSERT INTO ItemFeats VALUES (?,?,?,?);',
                (sid0, sid1, tid, fid))
        self._items.append((tid, item))
        self._itemmap[tid] = item
        return

    def get_item(self, tid):
        if tid in self._itemmap:
            item = self._itemmap[tid]
        else:
            self._cur.execute(
                'SELECT Item FROM Items WHERE Tid=?;',
                (tid,))
            (item,) = self._cur.fetchone()
            self._itemmap[fid] = item
        return item

    def get_tid(self, item):
        for (tid,v) in self._items:
            if v == item: return tid
        raise KeyError(item)

    def get_items(self):
        if not self._items:
            self._cur.execute(
                'SELECT Tid,Item FROM Items;')
            self._items = list(self._cur.fetchall())
            for (tid,item) in self._items:
                self._itemmap[tid] = item
        return self._items

    def get_feats(self, tid, resolve=False, source=False):
        self._cur.execute(
            'SELECT Sid0,Sid1,Fid FROM ItemFeats WHERE Tid=?;',
            (tid,))
        feat2srcs = {}
        for (sid0,sid1,fid) in list(self._cur.fetchall()):
            if resolve:
                feat = self.get_feat(fid)
            else:
                feat = fid
            if source:
                srcs = self.get_sources(sid0, sid1)
            else:
                srcs = None
            feat2srcs[feat] = srcs
        return feat2srcs

    def get_featitems(self, fid, resolve=False, source=False):
        self._cur.execute(
            'SELECT Sid0,Sid1,Tid FROM ItemFeats WHERE Fid=?;',
            (fid,))
        item2srcs = {}
        for (sid0,sid1,tid) in list(self._cur.fetchall()):
            if resolve:
                item = self.get_item(tid)
            else:
                item = tid
            if source:
                srcs = self.get_sources(sid0, sid1)
            else:
                srcs = None
            item2srcs[item] = srcs
        return item2srcs

    def get_numfeatitems(self, fid, resolve=False, source=False):
        self._cur.execute(
            'SELECT count(*) FROM ItemFeats WHERE Fid=?;',
            (fid,))
        (n,) = self._cur.fetchone()
        return n

# main
def main(argv):
    import fileinput
    import getopt
    def usage():
        print('usage: %s [-o dbpath] [-s] [feats ...]' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'o:s')
    except getopt.GetoptError:
        return usage()
    outpath = None
    source = False
    for (k, v) in opts:
        if k == '-o': outpath = v
        elif k == 's': source = True
    if not args: return usage()

    if outpath is None:
        dbpath = args.pop(0)
        print('Reading from: %r' % dbpath)
        db = FeatDB(dbpath)
        for (tid,item) in db:
            print('+ITEM', item)
            feat2srcs = db.get_feats(tid, resolve=True, source=source)
            for (feat,srcs) in feat2srcs.items():
                if feat is None: continue
                print('+FEAT', feat)
                if srcs:
                    print('#', srcs)
            print()
        return

    if os.path.exists(outpath):
        print('Already exists: %r' % outpath)
        return 1

    print('Writing to: %r' % outpath)
    db = FeatDB(outpath)
    db.init()

    feat2fid = {}
    nitems = nfeats = nsrcs = 0

    for line in fileinput.input(args):
        line = line.strip()
        if line.startswith('+SOURCE '):
            (srcid, path) = json.loads(line[8:])
            db.add_path(srcid, path)

        elif line.startswith('+ITEM '):
            data = json.loads(line[6:])
            item = fid2srcs = None
            if data[0] == 'REF':
                item = data[1]
                fid2srcs = {0: data[2:]}
                sys.stderr.write('.'); sys.stderr.flush()
                nitems += 1

        elif item is not None and line.startswith('+FEAT '):
            assert fid2srcs is not None
            data = json.loads(line[6:])
            feat = tuple(data[0:3])
            if feat in feat2fid:
                fid = feat2fid[feat]
            else:
                fid = len(feat2fid)+1
                feat2fid[feat] = fid
                db.add_feat(feat, fid)
            if fid in fid2srcs:
                srcs = fid2srcs[fid]
            else:
                srcs = fid2srcs[fid] = []
            srcs.extend(data[3:])
            nfeats += 1
            nsrcs += len(data[3:])

        elif item is not None and not line:
            assert fid2srcs is not None
            db.add_item(item, fid2srcs)
            item = fid2srcs = None

    print('Items=%r, Feats=%r, Srcs=%r' %
          (nitems, nfeats, nsrcs))

    db.close()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
