#!/usr/bin/env python
import sys
import sqlite3
from graph import SourceDB, DFGraph
from graph import get_graphs, fetch_graph
from graph2db import TreeCache, is_key

def find_graph(cache, cur, graph, minnodes=5, minbranches=2):
    
    def match_tree(pid, link0, node0, match):
        if link0 is None:
            key0 = ''
        else:
            key0 = link0.label or ''
        if is_key(node0):
            key = key0+':'+node0.label
            tid = cache.get(pid, key)
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
            for link1 in node0.recv:
                b = match_tree(tid, link1, link1.src, match)
                if 0 < b:
                    n += 1
                    branches = max(branches, b)
        else:
            n = 0
            branches = 0
            for link1 in node0.recv:
                b = match_tree(pid, link0, link1.src, match)
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
        if not node.send:
            find_tree(node)

    return votes

def main(argv):
    import getopt
    def usage():
        print('usage: %s [-v] [-B basedir] [-n minnodes] [-b minbranches] '
              '[-s gidstart] [-e gidend] graph.db index.db' % argv[0])
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'vB:n:b:s:e:')
    except getopt.GetoptError:
        return usage()
    verbose = False
    srcdb = None
    minnodes = 5
    minbranches = 2
    gidstart = 0
    gidend = 0
    for (k, v) in opts:
        if k == '-v': verbose = True
        elif k == '-B': srcdb = SourceDB(v)
        elif k == '-n': minnodes = int(v)
        elif k == '-b': minbranches = int(v)
        elif k == '-s': gidstart = int(v)
        elif k == '-e': gidend = int(v)
    if not args: return usage()

    graphname = args.pop(0)
    graphconn = sqlite3.connect(graphname)
    graphcur = graphconn.cursor()

    indexname = args.pop(0)
    indexconn = sqlite3.connect(indexname)
    indexcur = indexconn.cursor()

    cache = TreeCache(indexconn.cursor())
    
    def show_result(graph0, src0, votes):
        print ('+', graph0.gid)
        for (gid1,pairs) in votes.items():
            graph1 = fetch_graph(graphcur, gid1)
            print ('-', graph0.gid, graph1.gid,
                   '-'.join(key for (key,_,_) in pairs),
                   ','.join('%d:%d' % (node.nid,nid) for (_,node,nid) in pairs))
            if src0 is None: continue
            try:
                src1 = srcdb.get(graph1.src)
            except KeyError:
                continue
            nodes0 = []
            nodes1 = []
            for (key,node,nid) in pairs:
                nodes0.append(node)
                nodes1.append(graph1.nodes[nid])
            print ('###', src0.name)
            src0.show_nodes(nodes0)
            print ('###', src1.name)
            src1.show_nodes(nodes1)
            print ()
        return
    
    cur1 = graphconn.cursor()
    if gidstart < gidend:
        gids = cur1.execute('SELECT Gid FROM DFGraph WHERE ? <= Gid AND Gid < ?;',
                            (gidstart, gidend))
    else:
        gids = cur1.execute('SELECT Gid FROM DFGraph WHERE ? <= Gid;',
                            (gidstart,))
    for (gid0,) in gids:
        graph0 = fetch_graph(graphcur, gid0)
        src0 = None
        if srcdb is not None:
            try:
                src0 = srcdb.get(graph0.src)
            except KeyError:
                pass
        if verbose or (gid0 % 100) == 0:
            sys.stderr.write('*** %d ***\n' % gid0)
            sys.stderr.flush()
        maxvotes = find_graph(
            cache, indexcur, graph0,
            minnodes=minnodes, minbranches=minbranches)
        if not maxvotes: continue
        show_result(graph0, src0, maxvotes)
        sys.stdout.flush()
    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
