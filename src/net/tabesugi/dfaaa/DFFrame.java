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

    public String label;
    public Map<ASTNode, DFFrame> children = new HashMap<ASTNode, DFFrame>();
    
    public Set<DFRef> inputs = new HashSet<DFRef>();
    public Set<DFRef> outputs = new HashSet<DFRef>();

    public static String TRY = "@TRY";
    public static String METHOD = "@METHOD";

    public DFFrame(String label) {
	this.label = label;
    }

    public String toString() {
	return ("<DFFrame("+this.label+")>");
    }
    
    public DFFrame addChild(String label, ASTNode ast) {
	DFFrame frame = new DFFrame(label);
	this.children.put(ast, frame);
	return frame;
    }

    public DFFrame getChild(ASTNode ast) {
	return this.children.get(ast);
    }

    public void addInput(DFRef ref) {
	this.inputs.add(ref);
    }

    public void addOutput(DFRef ref) {
	this.outputs.add(ref);
    }

    public Set<DFRef> getInsAndOuts() {
	Set<DFRef> refs = new HashSet<DFRef>(this.inputs);
	refs.retainAll(this.outputs);
	return refs;
    }

    public void dump() {
	dump(System.out, "");
    }
    
    public void dump(PrintStream out, String indent) {
	out.println(indent+this.label+" {");
	String i2 = indent + "  ";
	StringBuilder inputs = new StringBuilder();
	for (DFRef ref : this.inputs) {
	    inputs.append(" "+ref);
	}
	out.println(i2+"inputs:"+inputs);
	StringBuilder outputs = new StringBuilder();
	for (DFRef ref : this.outputs) {
	    outputs.append(" "+ref);
	}
	out.println(i2+"outputs:"+outputs);
	StringBuilder inouts = new StringBuilder();
	for (DFRef ref : this.getInsAndOuts()) {
	    inouts.append(" "+ref);
	}
	out.println(i2+"in/outs:"+inouts);
	for (DFFrame frame : this.children.values()) {
	    frame.dump(out, i2);
	}
	out.println(indent+"}");
    }
}
