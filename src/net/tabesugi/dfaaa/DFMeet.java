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

    public DFMeet(DFNode node, DFFrame frame) {
	this.node = node;
	this.frame = frame;
    }

    public String toString() {
	return ("<DFMeet: "+this.node+" -> "+this.frame+">");
    }
}
