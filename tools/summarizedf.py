#!/usr/bin/env python
import sys
import logging
from graphs import IDFBuilder, parsemethodname, stripid
from algos import Cons, SCC

def q(s):
    if s:
        return '"%s"' % s.replace('"',r'\"')
    else:
        return '""'

class IRef:

    ccid = {}

    @classmethod
    def get(klass, cc, ref):
        if cc in klass.ccid:
            d = klass.ccid[cc]
        else:
            d = klass.ccid[cc] = {}
        if ref in d:
            iref = d[ref]
        else:
            iid = len(d)+1
            iref = d[ref] = klass(cc, ref, iid)
        return iref

    def __init__(self, cc, ref, iid):
        self.cc = cc
        self.ref = ref
        self.iid = iid
        self.linkto = []
        self.linkfrom = []
        return

    def __str__(self):
        return (stripid(self.ref) or self.ref)

    def __repr__(self):
        return f'<+{self.iid}:{self.ref}>'

    def connect(self, iref):
        if iref in self.linkto: return
        assert self not in iref.linkfrom
        self.linkto.append(iref)
        iref.linkfrom.append(self)
        return

# trace()
IGNORED = {
    'value', 'valueset',
    'op_assign', 'op_prefix', 'op_infix', 'op_postfix',
    'op_typecast', 'op_typecheck', 'op_iter',
    'ref_array', 'ref_field',
}
def trace(ctx2iref, v1, iref0=None, cc=None):
    k = (cc,v1)
    if k in ctx2iref:
        iref1 = ctx2iref[k]
        if iref0 is not None:
            logging.debug(f'{Cons.len(cc)} {iref1!r} -> {iref0!r}')
            yield (iref1, iref0)
        return
    n1 = v1.node
    ref1 = n1.ref
    if n1.kind in IGNORED:
        iref1 = iref0
    elif ref1 is None or not (ref1.startswith('$') or ref1.startswith('.')):
        iref1 = iref0
    else:
        if ref1[0] == '$':
            iref1 = IRef.get(cc, ref1)
        else:
            iref1 = IRef.get(None, ref1)
        if iref0 is not None:
            logging.debug(f'{Cons.len(cc)} {iref1!r} -> {iref0!r}')
            yield (iref1, iref0)
        ctx2iref[k] = iref1
    for (link,v2,funcall) in v1.inputs:
        if link.startswith('_'): continue
        n2 = v2.node
        if n2.kind == 'output' and funcall is not None:
            if cc is None or funcall not in cc:
                yield from trace(ctx2iref, v2, iref1, Cons(funcall, cc))
        elif n1.kind == 'input' and funcall is not None:
            if cc is not None and cc.car is funcall:
                yield from trace(ctx2iref, v2, iref1, cc.cdr)
        else:
            yield from trace(ctx2iref, v2, iref1, cc)
    return

# main
def main(argv):
    import fileinput
    import getopt
    def usage():
        print(f'usage: {argv[0]} [-d] [-o output] [-M maxoverrides] [-r ratio] [-f method] [graph ...]')
        return 100
    try:
        (opts, args) = getopt.getopt(argv[1:], 'do:M:r:f:')
    except getopt.GetoptError:
        return usage()
    level = logging.INFO
    outpath = None
    maxoverrides = 1
    ratio = 0.9
    maxfan = 5
    methods = {'main'}
    for (k, v) in opts:
        if k == '-d': level = logging.DEBUG
        elif k == '-o': outpath = v
        elif k == '-M': maxoverrides = int(v)
        elif k == '-r': ratio = float(v)
        elif k == '-f': methods.update(v.split(','))
    if not args: return usage()

    logging.basicConfig(format='%(asctime)s %(levelname)s %(message)s', level=level)

    out = sys.stdout
    if outpath is not None:
        out = open(outpath, 'w')

    builder = IDFBuilder(maxoverrides=maxoverrides)
    for path in args:
        logging.info(f'Loading: {path}...')
        builder.load(path)
    builder.run()
    nfuncalls = sum( len(a) for a in builder.funcalls.values() )
    logging.info(f'Read: {len(builder.srcmap)} sources, {len(builder.methods)} methods, {nfuncalls} funcalls, {len(builder.vtxs)} IPVertexes')

    # Enumerate all the flows.
    irefs = set()
    ctx2iref = {}
    nlinks = 0
    for method in builder.methods:
        # Filter "top-level" methods only which aren't called by anyone else.
        if method.callers: continue
        (klass,name,func) = parsemethodname(method.name)
        if (name not in methods) and (method.name not in methods): continue
        logging.info(f'method: {method.name}')
        for node in method:
            vtx = builder.vtxs[node]
            if vtx.outputs: continue
            # Filter the output nodes only.
            for (iref1, iref0) in trace(ctx2iref, vtx):
                # iref1 -> iref0
                assert iref1 is not None
                if iref0 is None: continue
                if iref0 == iref1: continue
                iref1.connect(iref0)
                irefs.add(iref0)
                irefs.add(iref1)
                nlinks += 1
        break
    logging.info(f'irefs: {len(irefs)}')
    logging.info(f'links: {nlinks}')

    # Discover strong components.
    (allcpts, ref2cpt) = SCC.fromnodes(irefs, lambda iref: iref.linkto)
    logging.info(f'allcpts: {len(allcpts)}')

    # Discover the most significant edges.
    incount = {}
    incoming = {}
    outcount = {}
    outgoing = {}
    for cpt in allcpts:
        incount[cpt] = len(cpt.linkfrom)
        incoming[cpt] = 0 if cpt.linkfrom else 1
        outcount[cpt] = len(cpt.linkto)
        outgoing[cpt] = 0 if cpt.linkto else 1
    def count_forw(cpt0):
        for cpt1 in cpt0.linkto:
            assert cpt0 is not cpt1
            incount[cpt1] -= 1
            incoming[cpt1] += incoming[cpt0]
            if incount[cpt1] == 0:
                count_forw(cpt1)
        return
    def count_bacj(cpt1):
        for cpt0 in cpt1.linkfrom:
            assert cpt0 is not cpt1
            outcount[cpt0] -= 1
            outgoing[cpt0] += outgoing[cpt1]
            if outcount[cpt0] == 0:
                count_bacj(cpt0)
        return
    for cpt in allcpts:
        if not cpt.linkfrom:
            count_forw(cpt)
        if not cpt.linkto:
            count_bacj(cpt)

    maxcount = 0
    for cpt0 in allcpts:
        for cpt1 in cpt0.linkto:
            count = incoming[cpt0] + outgoing[cpt1]
            if maxcount < count:
                maxcount = count
        if len(cpt0) < 2: continue
        if level == logging.DEBUG:
            logging.debug(f'cpt: {cpt0}')
            for iref in cpt0:
                logging.debug(f'  {iref!r}')
    logging.info(f'maxcount: {maxcount}')

    # Traverse the edges.
    maxlinks = set()
    def trav_forw(cpt0):
        linkto = sorted(cpt0.linkto, key=lambda cpt1: outgoing[cpt1], reverse=True)
        for cpt1 in linkto[:maxfan]:
            maxlinks.add((cpt0, cpt1))
            trav_forw(cpt1)
        return
    def trav_back(cpt1):
        linkfrom = sorted(cpt1.linkfrom, key=lambda cpt0: incoming[cpt0], reverse=True)
        for cpt0 in linkfrom[:maxfan]:
            maxlinks.add((cpt0, cpt1))
            trav_back(cpt0)
        return
    for cpt0 in allcpts:
        for cpt1 in cpt0.linkto:
            count = incoming[cpt0] + outgoing[cpt1]
            if ratio*maxcount <= count:
                maxlinks.add((cpt0, cpt1))
                trav_back(cpt0)
                trav_forw(cpt1)
    logging.info(f'maxlinks: {len(maxlinks)}')

    maxcpts = set( cpt0 for (cpt0,_) in maxlinks )
    maxcpts.update( cpt1 for (_,cpt1) in maxlinks )
    logging.info(f'maxcpts: {len(maxcpts)}')

    # Generate a trimmed graph.
    out.write(f'digraph {q(path)} {{\n')
    for cpt in maxcpts:
        out.write(f' N{cpt.cid} [label={q(str(cpt))}, fontname="courier"];\n')
    for (cpt0,cpt1) in maxlinks:
        out.write(f' N{cpt0.cid} -> N{cpt1.cid};\n')
    out.write('}\n')

    return 0

if __name__ == '__main__': sys.exit(main(sys.argv))
