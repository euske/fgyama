#!/usr/bin/env python
import sys
import os.path


##  SourceFile
##
class SourceFile:

    def __init__(self, name, data):
        self.name = name
        self.data = data
        self.lines = data.splitlines(True)
        return
    
    def __repr__(self):
        return ('<SourceFile(%s)>' %
                (self.name,))

    def show_nodes(self, nodes,
                   astart=(lambda _: '['),
                   aend=(lambda _: ']'),
                   abody=(lambda _,s: s),
                   ncontext=1, skip='...\n'):
        ranges = []
        for node in nodes:
            if node.ast is None: continue
            (_,i,n) = node.ast
            ranges.append((i, i+n, node.nid))
        return self.show(ranges, ncontext=ncontext, skip=skip,
                         astart=astart, aend=aend, abody=abody)

    # ranges=[(start,end,anno), ...]
    def show(self, ranges,
             astart=(lambda _: '['),
             aend=(lambda _: ']'),
             abody=(lambda _,s: s),
             ncontext=1, skip='...\n'):
        if not ranges: return
        triggers = []
        for (s,e,anno) in ranges:
            triggers.append((s,+1,anno))
            triggers.append((e,-1,anno))
        triggers.sort(key=lambda x: (x[0],x[1]))
        lines = {}
        loc0 = 0
        i = 0
        annos = []
        for (lineno,line) in enumerate(self.lines):
            loc1 = loc0+len(line)
            pos0 = 0
            buf = ''
            while i < len(triggers):
                (loc,v,anno) = triggers[i]
                if loc1 < loc: break
                i += 1
                pos1 = loc - loc0
                buf += abody(annos, line[pos0:pos1])
                pos0 = pos1
                if 0 < v:
                    buf += astart(anno)
                    annos.append(anno)
                else:
                    buf += aend(anno)
                    annos.remove(anno)
            if buf:
                buf += abody(annos, line[pos0:])
                lines[lineno] = buf
            loc0 = loc1
        n = len(self.lines)
        for (lineno,line) in list(lines.items()):
            for i in range(max(0, lineno-ncontext),
                           min(n, lineno+ncontext+1)):
                if i not in lines:
                    lines[i] = abody(None, self.lines[i])
        lineno0 = 0
        for lineno1 in sorted(lines):
            if lineno0 < lineno1:
                yield (None, skip)
            yield (lineno1, lines[lineno1])
            lineno0 = lineno1+1
        return


##  SourceDB
##
class SourceDB:
    
    def __init__(self, basedir):
        self.basedir = basedir
        self._cache = {}
        return

    def get(self, name):
        if name in self._cache:
            src = self._cache[name]
        else:
            path = os.path.join(self.basedir, name)
            try:
                with open(path) as fp:
                    data = fp.read()
            except IOError:
                raise KeyError(name)
            except UnicodeError:
                raise KeyError(name)
            src = SourceFile(name, data)
            self._cache[name] = src
        return src

    def show(self, fp=sys.stdout):
        for src in self._cache.values():
            for (_,line) in src.show():
                fp.write(line)
        return
