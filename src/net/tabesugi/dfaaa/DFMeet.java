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
    public String label;

    public DFMeet(DFNode node, String label) {
	this.node = node;
	this.label = label;
    }

    public String toString() {
	return ("<DFMeet: "+this.node+" -> "+this.label+">");
    }
}
