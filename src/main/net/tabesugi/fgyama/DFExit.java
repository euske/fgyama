//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFExit
//
public class DFExit {

    public DFNode node;
    public DFFrame frame;
    public boolean cont;

    public DFExit(DFNode node, DFFrame frame) {
	this(node, frame, false);
    }

    public DFExit(DFNode node, DFFrame frame, boolean cont) {
	this.node = node;
	this.frame = frame;
	this.cont = cont;
    }

    public String toString() {
	return ("<DFExit: "+this.node+" -> "+this.frame+">");
    }

    public DFExit wrap(DFNode node) {
	return new DFExit(node, this.frame, this.cont);
    }
}
