//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFComponent
//
public class DFComponent {

    private DFGraph _graph;
    private DFVarSpace _space;

    private Map<DFVarRef, DFNode> _current =
        new HashMap<DFVarRef, DFNode>();
    private Map<DFVarRef, DFNode> _first =
        new HashMap<DFVarRef, DFNode>();
    private DFNode _lval = null;
    private DFNode _rval = null;

    public DFComponent(DFGraph graph, DFVarSpace space) {
        _graph = graph;
        _space = space;
    }

    // getCurrent(ref): get a current value of the component if defined.
    public DFNode getCurrent(DFVarRef ref) {
        DFNode node = _current.get(ref);
        if (node == null) {
            assert(!_first.containsKey(ref));
            node = new DFNode(_graph, _space, ref.getType(), ref);
            _current.put(ref, node);
            _first.put(ref, node);
        }
        return node;
    }

    public DFNode getFirst(DFVarRef ref) {
        return _first.get(ref);
    }

    public DFNode getLValue() {
        return _lval;
    }

    public DFNode getRValue() {
        return _rval;
    }

    public void setCurrent(DFNode node) {
        DFVarRef ref = node.getRef();
        _current.put(ref, node);
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

    // dump: for debugging.
    public void dump() {
        dump(System.err);
    }
    public void dump(PrintStream out) {
        out.println("DFComponent");
        StringBuilder firsts = new StringBuilder();
        for (DFVarRef ref : _first.keySet()) {
            firsts.append(" "+ref);
        }
        out.println("  firsts:"+firsts);
        StringBuilder currents = new StringBuilder();
        for (DFVarRef ref : _current.keySet()) {
            currents.append(" "+ref);
        }
        out.println("  currents:"+currents);
        if (_rval != null) {
            out.println("  rval: "+_rval);
        }
        if (_lval != null) {
            out.println("  lval: "+_lval);
        }
    }
}
