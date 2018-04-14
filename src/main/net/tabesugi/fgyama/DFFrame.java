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

    private String _label;
    private DFFrame _parent;

    private Map<ASTNode, DFFrame> _children = new HashMap<ASTNode, DFFrame>();
    private Set<DFVarRef> _inputs = new HashSet<DFVarRef>();
    private Set<DFVarRef> _outputs = new HashSet<DFVarRef>();

    public static final String TRY = "@TRY";
    public static final String METHOD = "@METHOD";

    public DFFrame(String label) {
	this(label, null);
    }

    public DFFrame(String label, DFFrame parent) {
	_label = label;
	_parent = parent;
    }

    @Override
    public String toString() {
	return ("<DFFrame("+_label+")>");
    }

    public DFFrame addChild(String label, ASTNode ast) {
	DFFrame frame = new DFFrame(label, this);
	_children.put(ast, frame);
	return frame;
    }

    public String getLabel() {
        return _label;
    }

    public DFFrame getParent() {
        return _parent;
    }

    public DFFrame getChildByAST(ASTNode ast) {
	return _children.get(ast);
    }

    public void addInput(DFVarRef ref) {
	_inputs.add(ref);
    }

    public void addOutput(DFVarRef ref) {
	_outputs.add(ref);
    }

    public DFFrame find(String label) {
	if (label == null) return this;
	DFFrame frame = this;
	while (frame.getParent() != null) {
	    if (frame.getLabel() != null &&
		frame.getLabel().equals(label)) break;
	    frame = frame.getParent();
	}
	return frame;
    }

    public DFVarRef[] getOutputs() {
	DFVarRef[] refs = new DFVarRef[_outputs.size()];
	_outputs.toArray(refs);
	Arrays.sort(refs);
	return refs;
    }

    public DFVarRef[] getInsAndOuts() {
	Set<DFVarRef> inouts = new HashSet<DFVarRef>(_inputs);
	inouts.retainAll(_outputs);
	DFVarRef[] refs = new DFVarRef[inouts.size()];
	inouts.toArray(refs);
	Arrays.sort(refs);
	return refs;
    }

    // dump: for debugging.
    public void dump() {
	dump(System.out, "");
    }
    public void dump(PrintStream out, String indent) {
	out.println(indent+_label+" {");
	String i2 = indent + "  ";
	StringBuilder inputs = new StringBuilder();
	for (DFVarRef ref : _inputs) {
	    inputs.append(" "+ref);
	}
	out.println(i2+"inputs:"+inputs);
	StringBuilder outputs = new StringBuilder();
	for (DFVarRef ref : _outputs) {
	    outputs.append(" "+ref);
	}
	out.println(i2+"outputs:"+outputs);
	StringBuilder inouts = new StringBuilder();
	for (DFVarRef ref : this.getInsAndOuts()) {
	    inouts.append(" "+ref);
	}
	out.println(i2+"in/outs:"+inouts);
	for (DFFrame frame : _children.values()) {
	    frame.dump(out, i2);
	}
	out.println(indent+"}");
    }
}
