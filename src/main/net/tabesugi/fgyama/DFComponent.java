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

    public DFScope scope;

    private DFNode lval = null;
    private DFNode rval = null;
    private Map<DFRef, DFNode> inputs = new HashMap<DFRef, DFNode>();
    private Map<DFRef, DFNode> outputs = new HashMap<DFRef, DFNode>();
    private List<DFExit> exits = new ArrayList<DFExit>();

    public DFComponent(DFScope scope) {
	this.scope = scope;
    }

    public void dump() {
	dump(System.out);
    }

    public void dump(PrintStream out) {
	out.println("DFComponent");
	StringBuilder inputs = new StringBuilder();
	for (DFRef ref : this.inputs.keySet()) {
	    inputs.append(" "+ref);
	}
	out.println("  inputs:"+inputs);
	StringBuilder outputs = new StringBuilder();
	for (DFRef ref : this.outputs.keySet()) {
	    outputs.append(" "+ref);
	}
	out.println("  outputs:"+outputs);
	if (this.rval != null) {
	    out.println("  rval: "+this.rval);
	}
	if (this.lval != null) {
	    out.println("  lval: "+this.lval);
	}
    }

    // getValue(ref): get an output value of the component if defined.
    public DFNode getValue(DFRef ref) {
	DFNode node = this.outputs.get(ref);
	if (node == null) {
	    node = this.inputs.get(ref);
	    if (node == null) {
		node = new DFNode(this.scope, ref);
		this.inputs.put(ref, node);
	    }
	}
	return node;
    }

    public DFNode getLValue() {
	return this.lval;
    }

    public void setLValue(DFNode node) {
	this.lval = node;
    }

    public DFNode getRValue() {
	return this.rval;
    }

    public void setRValue(DFNode node) {
	this.rval = node;
    }

    public DFNode getInput(DFRef ref) {
	return this.inputs.get(ref);
    }

    public DFNode getOutput(DFRef ref) {
	return this.outputs.get(ref);
    }

    public void setOutput(DFNode node) {
	this.outputs.put(node.ref, node);
    }

    public DFRef[] inputRefs() {
	DFRef[] refs = new DFRef[this.inputs.size()];
	this.inputs.keySet().toArray(refs);
	Arrays.sort(refs);
	return refs;
    }

    public DFRef[] outputRefs() {
	DFRef[] refs = new DFRef[this.outputs.size()];
	this.outputs.keySet().toArray(refs);
	Arrays.sort(refs);
	return refs;
    }

    public void endScope(DFScope scope) {
	for (DFRef ref : scope.refs()) {
	    this.inputs.remove(ref);
	    this.outputs.remove(ref);
	}
    }

    public DFExit[] exits() {
	DFExit[] exits = new DFExit[this.exits.size()];
	this.exits.toArray(exits);
	return exits;
    }

    public void addExit(DFExit exit) {
	this.exits.add(exit);
    }

    public void endFrame(DFFrame frame) {
	for (DFExit exit : this.exits) {
	    if (exit.frame == frame) {
		DFNode node = exit.node;
		node.finish(this);
		this.setOutput(node);
	    }
	}
    }
}
