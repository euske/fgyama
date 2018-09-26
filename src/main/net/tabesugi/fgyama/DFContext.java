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

    private Map<DFVarRef, DFNode> _first =
        new HashMap<DFVarRef, DFNode>();
    private Map<DFVarRef, DFNode> _last =
        new HashMap<DFVarRef, DFNode>();
    private DFNode _lval = null;
    private DFNode _rval = null;

    public DFContext(DFGraph graph, DFVarScope scope) {
        _graph = graph;
        _scope = scope;
    }

    // get(ref): get a current value of the context if defined.
    public DFNode get(DFVarRef ref) {
        DFNode node = _last.get(ref);
        if (node == null) {
            assert !_first.containsKey(ref);
            node = new DFNode(_graph, _scope, ref.getRefType(), ref);
            _last.put(ref, node);
            _first.put(ref, node);
        }
        return node;
    }

    public DFNode getFirst(DFVarRef ref) {
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
        DFVarRef ref = node.getRef();
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

    // dump: for debugging.
    public void dump() {
        dump(System.err);
    }
    public void dump(PrintStream out) {
        out.println("DFContext");
        StringBuilder firsts = new StringBuilder();
        for (DFVarRef ref : _first.keySet()) {
            firsts.append(" "+ref);
        }
        out.println("  firsts:"+firsts);
        StringBuilder lasts = new StringBuilder();
        for (DFVarRef ref : _last.keySet()) {
            lasts.append(" "+ref);
        }
        out.println("  lasts:"+lasts);
        if (_rval != null) {
            out.println("  rval: "+_rval);
        }
        if (_lval != null) {
            out.println("  lval: "+_lval);
        }
    }
}
