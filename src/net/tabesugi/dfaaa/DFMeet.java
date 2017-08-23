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
    public boolean cont;
    public boolean cond;

    public DFMeet(DFNode node, DFFrame frame, boolean cont, DFLabel label) {
	this(node, frame, cont, label, null, false);
    }
    
    public DFMeet(DFNode node, DFFrame frame, boolean cont, DFLabel label, 
		  DFNode value, boolean cond) {
	this.node = node;
	this.frame = frame;
	this.cont = cont;
	this.label = label;
	this.value = value;
	this.cond = cond;
    }

    public String toString() {
	if (this.cont) {
	    return ("continue:"+this.node+" -> "+this.frame+":"+this.label);
	} else {
	    return ("break:"+this.node+" -> "+this.frame+":"+this.label);
	}
    }
}
