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

    public Map<DFRef, DFNode> inputs = new HashMap<DFRef, DFNode>();
    public Map<DFRef, DFNode> outputs = new HashMap<DFRef, DFNode>();
    public DFNode value = null;
    public AssignNode assign = null;
    
    public DFComponent() {
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
		node = new DistNode(ref.scope, ref);
		this.inputs.put(ref, node);
	    }
	}
	return node;
    }

    public void put(DFNode node) {
	this.outputs.put(node.ref, node);
    }

    public void removeRef(DFRef ref) {
	this.inputs.remove(ref);
	this.outputs.remove(ref);
    }
}

