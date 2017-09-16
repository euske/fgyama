//  Java2DF
//
package net.tabesugi.dfaaa;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFFrame
//
public class DFFrame {

    public DFFrame parent;
    public DFScope scope;
    public String label;
    public List<DFExit> exits;

    public DFFrame(DFFrame parent, DFScope scope, String label) {
	this.parent = parent;
	this.scope = scope;
	this.label = label;
	this.exits = new ArrayList<DFExit>();
    }

    public String toString() {
	return ("<DFFrame("+this.scope.name+": "+this.label+")>");
    }
    
    public void add(DFExit exit) {
	this.exits.add(exit);
    }

    public void capture(DFComponent cpt, String label) {
	// XXX use a different set from scope.
	for (DFRef ref : this.scope.getInsAndOuts()) {
	    DFNode node = cpt.get(ref);
	    this.add(new DFExit(node, label));
	}
    }

    public void finish(DFComponent cpt) {
	for (DFExit exit : this.exits) {
	    if (exit.label == null || exit.label.equals(this.label)) {
		DFNode node = exit.node;
		if (node instanceof JoinNode) {
		    DFNode src = cpt.get(node.ref);
		    ((JoinNode)node).close(src);
		}
		cpt.put(node);
	    }
	}
    }

}
