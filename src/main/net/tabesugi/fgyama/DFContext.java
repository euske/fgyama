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

    private SortedMap<DFRef, DFNode> _first =
        new TreeMap<DFRef, DFNode>();
    private SortedMap<DFRef, DFNode> _last =
        new TreeMap<DFRef, DFNode>();
    private DFNode _lval = null;
    private DFNode _rval = null;

    public DFContext(DFGraph graph, DFVarScope scope) {
        _graph = graph;
        _scope = scope;
    }

    // get(ref): get a current value of the context if defined.
    public DFNode get(DFRef ref) {
        DFNode node = _last.get(ref);
        if (node == null) {
            assert !_first.containsKey(ref);
            node = new DFNode(_graph, _scope, ref.getRefType(), ref);
            _last.put(ref, node);
            _first.put(ref, node);
        }
        return node;
    }

    public DFNode getFirst(DFRef ref) {
        DFNode node = _first.get(ref);
        if (node == null) {
            assert !_last.containsKey(ref);
            node = new DFNode(_graph, _scope, ref.getRefType(), ref);
            _first.put(ref, node);
            _last.put(ref, node);
        }
        return node;
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

    public Collection<DFNode> getFirsts() {
        return _first.values();
    }

    public DFRef[] getChanged() {
        List<DFRef> refs = new ArrayList<DFRef>();
        for (Map.Entry<DFRef, DFNode> ent : _first.entrySet()) {
            DFRef ref = ent.getKey();
            DFNode node0 = ent.getValue();
            DFNode node1 = _last.get(ref);
            if (node0 != node1) {
                refs.add(ref);
            }
        }
        DFRef[] a = new DFRef[refs.size()];
        refs.toArray(a);
        return a;
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
