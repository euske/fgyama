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

    private DFNode _lval = null;
    private DFNode _rval = null;
    private Map<DFVarRef, DFNode> _inputs =
        new HashMap<DFVarRef, DFNode>();
    private Map<DFVarRef, DFNode> _outputs =
        new HashMap<DFVarRef, DFNode>();

    public DFComponent(DFGraph graph, DFVarSpace space) {
        _graph = graph;
        _space = space;
    }

    // getValue(ref): get an output value of the component if defined.
    public DFNode getValue(DFVarRef ref) {
        DFNode node = _outputs.get(ref);
        if (node == null) {
            node = _inputs.get(ref);
            if (node == null) {
                node = new DFNode(_graph, _space, ref.getType(), ref);
                _inputs.put(ref, node);
            }
        }
        return node;
    }

    public DFNode getLValue() {
        return _lval;
    }

    public void setLValue(DFNode node) {
        _lval = node;
    }

    public DFNode getRValue() {
        return _rval;
    }

    public void setRValue(DFNode node) {
        _rval = node;
    }

    public DFNode getInput(DFVarRef ref) {
        return _inputs.get(ref);
    }

    public DFNode getOutput(DFVarRef ref) {
        return _outputs.get(ref);
    }

    public void setOutput(DFNode node) {
        _outputs.put(node.getRef(), node);
    }

    public DFVarRef[] getInputRefs() {
        DFVarRef[] refs = new DFVarRef[_inputs.size()];
        _inputs.keySet().toArray(refs);
        Arrays.sort(refs);
        return refs;
    }

    public DFVarRef[] getOutputRefs() {
        DFVarRef[] refs = new DFVarRef[_outputs.size()];
        _outputs.keySet().toArray(refs);
        Arrays.sort(refs);
        return refs;
    }

    // dump: for debugging.
    public void dump() {
        dump(System.err);
    }
    public void dump(PrintStream out) {
        out.println("DFComponent");
        StringBuilder inputs = new StringBuilder();
        for (DFVarRef ref : _inputs.keySet()) {
            inputs.append(" "+ref);
        }
        out.println("  inputs:"+inputs);
        StringBuilder outputs = new StringBuilder();
        for (DFVarRef ref : _outputs.keySet()) {
            outputs.append(" "+ref);
        }
        out.println("  outputs:"+outputs);
        if (_rval != null) {
            out.println("  rval: "+_rval);
        }
        if (_lval != null) {
            out.println("  lval: "+_lval);
        }
    }
}
