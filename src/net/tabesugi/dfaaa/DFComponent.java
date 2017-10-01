//  Java2DF
//
package net.tabesugi.dfaaa;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFComponent
//
public class DFComponent {

    public DFScope scope;
    public Map<DFRef, DFNode> inputs = new HashMap<DFRef, DFNode>();
    public Map<DFRef, DFNode> outputs = new HashMap<DFRef, DFNode>();
    public List<DFExit> exits = new ArrayList<DFExit>();
    public DFNode value = null;
    public AssignNode assign = null;
    
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
	if (this.value != null) {
	    out.println("  value: "+this.value);
	}
	if (this.assign != null) {
	    out.println("  assign: "+this.assign);
	}
    }

    public DFNode get(DFRef ref) {
	DFNode node = this.outputs.get(ref);
	if (node == null) {
	    node = this.inputs.get(ref);
	    if (node == null) {
		node = new DistNode(this.scope, ref);
		this.inputs.put(ref, node);
	    }
	}
	return node;
    }

    public void put(DFNode node) {
	this.outputs.put(node.ref, node);
    }

    public void endScope(DFScope scope) {
	for (DFRef ref : scope.vars()) {
	    this.inputs.remove(ref);
	    this.outputs.remove(ref);
	}
    }
    
    public void addExit(DFExit exit) {
	this.exits.add(exit);
    }

    public void addExitAll(DFRef[] refs, String label) {
	for (DFRef ref : refs) {
	    DFNode node = this.get(ref);
	    this.addExit(new DFExit(node, label));
	}
    }

    public void endFrame(DFFrame frame) {
	for (DFExit exit : this.exits) {
	    if (exit.label == null || exit.label.equals(frame.label)) {
		DFNode node = exit.node;
		if (node instanceof JoinNode) {
		    JoinNode join = (JoinNode)node;
		    if (!join.isClosed()) {
			join.close(this.get(node.ref));
		    }
		}
		this.put(node);
	    }
	}
    }
}
