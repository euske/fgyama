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
    public String name;
    public String label;
    public List<DFExit> exits;

    public static String RETURN = "@RETURN";
    
    public DFFrame(DFFrame parent, String name) {
	this(parent, name, null);
    }
    
    public DFFrame(DFFrame parent, String name, String label) {
	this.parent = parent;
	this.name = name;
	this.label = label;
	this.exits = new ArrayList<DFExit>();
    }

    public String toString() {
	if (this.label == null) {
	    return ("<DFFrame("+this.name+")>");
	} else {
	    return ("<DFFrame("+this.name+": "+this.label+")>");
	}
    }
    
    public void add(DFExit exit) {
	this.exits.add(exit);
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
