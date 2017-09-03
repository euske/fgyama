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
    public boolean cont;

    public DFExit(DFNode node, String label) {
	this(node, label, false);
    }
    
    public DFExit(DFNode node, String label, boolean cont) {
	this.node = node;
	this.label = label;
	this.cont = cont;
    }

    public String toString() {
	return ("<DFExit: "+this.node+" -> "+this.label+">");
    }

    public DFExit addJoin(DFScope scope, DFNode condValue, boolean cond) {
	JoinNode join = new JoinNode(scope, this.node.ref, null, condValue);
	join.recv(cond, this.node);
	return new DFExit(join, this.label, this.cont);
    }
}
