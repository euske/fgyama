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
    private DFVarScope _scope;

    private DFNode _lval = null;
    private DFNode _rval = null;
    private Map<DFVarRef, DFNode> _inputs =
	new HashMap<DFVarRef, DFNode>();
    private Map<DFVarRef, DFNode> _outputs =
	new HashMap<DFVarRef, DFNode>();
    private List<DFExit> _exits =
	new ArrayList<DFExit>();

    public DFComponent(DFGraph graph, DFVarScope scope) {
        _graph = graph;
	_scope = scope;
    }

    // getValue(ref): get an output value of the component if defined.
    public DFNode getValue(DFVarRef ref) {
	DFNode node = _outputs.get(ref);
	if (node == null) {
	    node = _inputs.get(ref);
	    if (node == null) {
		node = new DFNode(_graph, _scope, ref.getType(), ref);
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

    public DFExit[] getExits() {
	DFExit[] exits = new DFExit[_exits.size()];
	_exits.toArray(exits);
	return exits;
    }

    public void addExit(DFExit exit) {
	_exits.add(exit);
    }

    public void endFrame(DFFrame frame) {
	for (DFExit exit : _exits) {
	    if (frame == exit.getFrame()) {
		DFNode node = exit.getNode();
		node.finish(this);
		this.setOutput(node);
	    }
	}
    }

    // dump: for debugging.
    public void dump() {
	dump(System.out);
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
