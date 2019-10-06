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
    Fid INTEGER,
    Count INTEGER
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

    def add_item(self, item, fids):
        self._cur.execute(
            'INSERT INTO Items VALUES (NULL,?);',
            (item,))
        tid = self._cur.lastrowid
        self._items.append((tid, item))
        assert tid not in self._itemmap
        self._itemmap[tid] = item
        for (fid,(fc,srcs)) in fids.items():
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
                'INSERT INTO ItemFeats VALUES (?,?,?,?,?);',
                (sid0, sid1, tid, fid, fc))
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
            'SELECT Sid0,Sid1,Fid,Count FROM ItemFeats WHERE Tid=?;',
            (tid,))
        feat2srcs = {}
        for (sid0,sid1,fid,fc) in list(self._cur.fetchall()):
            if resolve:
                feat = self.get_feat(fid)
            else:
                feat = fid
            if source:
                srcs = self.get_sources(sid0, sid1)
            else:
                srcs = None
            feat2srcs[feat] = (fc, srcs)
        return feat2srcs

    def get_featitems(self, fid, resolve=False, source=False):
        self._cur.execute(
            'SELECT Sid0,Sid1,Tid,Count FROM ItemFeats WHERE Fid=?;',
            (fid,))
        item2srcs = {}
        for (sid0,sid1,tid,fc) in list(self._cur.fetchall()):
            if resolve:
                item = self.get_item(tid)
            else:
                item = tid
            if source:
                srcs = self.get_sources(sid0, sid1)
            else:
                srcs = None
            item2srcs[item] = (fc, srcs)
        return item2srcs

    def get_numfeatitems(self, fid, resolve=False, source=False):
        self._cur.execute(
            'SELECT sum(Count) FROM ItemFeats WHERE Fid=?;',
            (fid,))
        (n,) = self._cur.fetchone()
        return n

# main
def main(argv):
    import fileinput
    import getopt
    def usage():
        print(f'usage: {argv[0]} [-o dbpath] [-s] [feats ...]')
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
        print(f'Reading from: {dbpath!r}')
        db = FeatDB(dbpath)
        items = db.get_items()
        if args:
            items = [ (tid,item) for (tid,item) in items if item in args ]
        dcount = {}
        for (tid,item) in items:
            print('+ITEM', item)
            feat2srcs = db.get_feats(tid, resolve=True, source=source)
            for (feat,srcs) in feat2srcs.items():
                if feat is None: continue
                d = abs(feat[0])
                dcount[d] = dcount.get(d, 0)+1
                print('+FEAT', feat)
                if srcs:
                    print('#', srcs)
            print()
        print('#', sum(dcount.values()), dcount)
        return

    if os.path.exists(outpath):
        print(f'Already exists: {outpath!r}')
        return 1

    print(f'Writing to: {outpath!r}')
    db = FeatDB(outpath)
    db.init()

    feat2fid = {}
    nitems = nfeats = nsrcs = 0
    fids = None

    for line in fileinput.input(args):
        line = line.strip()
        if line.startswith('+SOURCE '):
            (srcid, path) = json.loads(line[8:])
            db.add_path(srcid, path)

        elif line.startswith('+ITEM '):
            (_,_,line) = line.partition(' ')
            data = json.loads(line)
            (item,count,srcs) = data
            fids = {0: (count,srcs)}
            sys.stderr.write('.'); sys.stderr.flush()
            nitems += 1

        elif item is not None and line.startswith('+FEAT '):
            assert fids is not None
            (_,_,line) = line.partition(' ')
            data = json.loads(line)
            feat = tuple(data[0])
            fc = data[1]
            srcs = data[2]
            if feat in feat2fid:
                fid = feat2fid[feat]
            else:
                fid = len(feat2fid)+1
                feat2fid[feat] = fid
                db.add_feat(feat, fid)
            fids[fid] = (fc, srcs)
            nfeats += 1
            nsrcs += len(data[2])

        elif item is not None and not line:
            assert fids is not None
            db.add_item(item, fids)
            item = fids = None

    print(f'Items={nitems}, Feats={nfeats}, Srcs={nsrcs}')

    db.close()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
