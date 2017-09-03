//  Java2DF
//
package net.tabesugi.dfaaa;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFExit
//
public class DFExit {

    public DFNode node;
    public String label;

    public DFExit(DFNode node, String label) {
	this.node = node;
	this.label = label;
    }

    public String toString() {
	return ("<DFExit: "+this.node+" -> "+this.label+">");
    }
}
