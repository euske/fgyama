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
    public List<DFMeet> breaks;
    public List<DFMeet> continues;
    
    public DFFrame(DFFrame parent, String name) {
	this(parent, name, null);
    }
    
    public DFFrame(DFFrame parent, String name, String label) {
	this.parent = parent;
	this.name = name;
	this.label = label;
	this.breaks = new ArrayList<DFMeet>();
	this.continues = new ArrayList<DFMeet>();
    }

    public String toString() {
	if (this.label == null) {
	    return ("<DFFrame("+this.name+")>");
	} else {
	    return ("<DFFrame("+this.name+": "+this.label+")>");
	}
    }
    
    public void addBreak(DFMeet meet) {
	this.breaks.add(meet);
    }

    public void addContinue(DFMeet meet) {
	this.continues.add(meet);
    }

    public void finish(DFComponent cpt) {
	for (DFMeet meet : this.breaks) {
	    if (meet.label == null || meet.label.equals(this.label)) {
		DFNode node = meet.node;
		if (node instanceof JoinNode) {
		    DFNode src = cpt.get(node.ref);
		    ((JoinNode)node).close(src);
		}
		cpt.put(node);
	    }
	}
    }

}
