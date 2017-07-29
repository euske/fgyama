//  Java2DF
//
package net.tabesugi.dfaaa;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFMeet
//
public class DFMeet {

    public DFNode node;
    public DFFrame frame;
    public DFLabel label;
    public DFNode value;
    public boolean cond;

    public DFMeet(DFNode node, DFFrame frame, DFLabel label) {
	this(node, frame, label, null, false);
    }
    
    public DFMeet(DFNode node, DFFrame frame, DFLabel label,
		  DFNode value, boolean cond) {
	this.node = node;
	this.frame = frame;
	this.label = label;
	this.value = value;
	this.cond = cond;
    }

    public String toString() {
	return (this.node+" -> "+this.frame+":"+this.label);
    }
}

