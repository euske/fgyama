//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFContext
//
public class DFContext {

    private DFGraph _graph;
    private DFVarScope _scope;

    private ConsistentHashMap<DFRef, DFNode> _first =
        new ConsistentHashMap<DFRef, DFNode>();
    private Map<DFRef, DFNode> _last =
        new HashMap<DFRef, DFNode>();
    private DFNode _lval = null;
    private DFNode _rval = null;

    public DFContext(DFGraph graph, DFVarScope scope) {
        _graph = graph;
        _scope = scope;
    }

    private DFNode create(DFRef ref) {
        DFNode node = new DFNode(_graph, _scope, ref.getRefType(), ref, null);
        _last.put(ref, node);
        _first.put(ref, node);
        return node;
    }

    // get(ref): get a current value of the context if defined.
    public DFNode get(DFRef ref) {
        DFNode node = _last.get(ref);
        if (node == null) {
            assert !_first.containsKey(ref);
            node = this.create(ref);
        }
        return node;
    }

    public DFNode getFirst(DFRef ref) {
        DFNode node = _first.get(ref);
        if (node == null) {
            assert !_last.containsKey(ref);
            node = this.create(ref);
        }
        return node;
    }

    public DFNode getLast(DFRef ref) {
        return _last.get(ref);
    }

    public DFNode getLValue() {
        return _lval;
    }

    public DFNode getRValue() {
        return _rval;
    }

    public void set(DFNode node) {
        DFRef ref = node.getRef();
        assert ref != null;
        _last.put(ref, node);
        if (!_first.containsKey(ref)) {
            _first.put(ref, node);
        }
    }

    public void setLValue(DFNode node) {
        _lval = node;
    }

    public void setRValue(DFNode node) {
        _rval = node;
    }

    public DFNode[] getFirsts() {
        DFNode[] values = new DFNode[_first.size()];
        int i = 0;
        for (DFRef ref : _first.keys()) {
            values[i++] = _first.get(ref);
        }
        return values;
    }

    public List<DFRef> getChanged() {
        List<DFRef> refs = new ArrayList<DFRef>();
        for (DFRef ref : _first.keys()) {
            DFNode node0 = _first.get(ref);
            DFNode node1 = _last.get(ref);
            if (node0 != node1) {
                refs.add(ref);
            }
        }
        return refs;
    }

    // dump: for debugging.
    public void dump() {
        dump(System.err);
    }
    public void dump(PrintStream out) {
        out.println("DFContext");
        out.println("  firsts:");
        for (Map.Entry<DFRef, DFNode> ent : _first.entrySet()) {
            out.println("    "+ent.getKey()+" = "+ent.getValue());
        }
        out.println("  lasts:");
        for (Map.Entry<DFRef, DFNode> ent : _last.entrySet()) {
            out.println("    "+ent.getKey()+" = "+ent.getValue());
        }
        if (_lval != null) {
            out.println("  lval: "+_lval);
        }
        if (_rval != null) {
            out.println("  rval: "+_rval);
        }
    }
}
