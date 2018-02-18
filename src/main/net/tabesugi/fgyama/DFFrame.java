//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFFrame
//
public class DFFrame {

    public String label;
    public DFFrame parent;

    private Map<ASTNode, DFFrame> _children = new HashMap<ASTNode, DFFrame>();
    private Set<DFRef> _inputs = new HashSet<DFRef>();
    private Set<DFRef> _outputs = new HashSet<DFRef>();

    public static final String TRY = "@TRY";
    public static final String METHOD = "@METHOD";

    public DFFrame(String label) {
	this(label, null);
    }

    public DFFrame(String label, DFFrame parent) {
	this.label = label;
	this.parent = parent;
    }

    public String toString() {
	return ("<DFFrame("+this.label+")>");
    }

    public DFFrame addChild(String label, ASTNode ast) {
	DFFrame frame = new DFFrame(label, this);
	_children.put(ast, frame);
	return frame;
    }

    public DFFrame getChild(ASTNode ast) {
	return _children.get(ast);
    }

    public void addInput(DFRef ref) {
	_inputs.add(ref);
    }

    public void addOutput(DFRef ref) {
	_outputs.add(ref);
    }

    public DFFrame find(String label) {
	if (label == null) return this;
	DFFrame frame = this;
	while (frame.parent != null) {
	    if (frame.label != null &&
		frame.label.equals(label)) break;
	    frame = frame.parent;
	}
	return frame;
    }

    public DFRef[] getOutputs() {
	DFRef[] refs = new DFRef[_outputs.size()];
	_outputs.toArray(refs);
	Arrays.sort(refs);
	return refs;
    }

    public DFRef[] getInsAndOuts() {
	Set<DFRef> inouts = new HashSet<DFRef>(_inputs);
	inouts.retainAll(_outputs);
	DFRef[] refs = new DFRef[inouts.size()];
	inouts.toArray(refs);
	Arrays.sort(refs);
	return refs;
    }

    public void dump() {
	dump(System.out, "");
    }

    public void dump(PrintStream out, String indent) {
	out.println(indent+this.label+" {");
	String i2 = indent + "  ";
	StringBuilder inputs = new StringBuilder();
	for (DFRef ref : _inputs) {
	    inputs.append(" "+ref);
	}
	out.println(i2+"inputs:"+inputs);
	StringBuilder outputs = new StringBuilder();
	for (DFRef ref : _outputs) {
	    outputs.append(" "+ref);
	}
	out.println(i2+"outputs:"+outputs);
	StringBuilder inouts = new StringBuilder();
	for (DFRef ref : this.getInsAndOuts()) {
	    inouts.append(" "+ref);
	}
	out.println(i2+"in/outs:"+inouts);
	for (DFFrame frame : _children.values()) {
	    frame.dump(out, i2);
	}
	out.println(indent+"}");
    }
}
