//  Java2DF
//
package net.tabesugi.dfaaa;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


// ReferNode: reference node.
public abstract class ReferNode extends ProgNode {

    public ReferNode(DFScope scope, DFRef ref, ASTNode ast) {
	super(scope, ref, ast);
    }

    public DFNodeType type() {
	return DFNodeType.Refer;
    }

    public String label() {
	return "ref";
    }

    public boolean canOmit() {
	return true;
    }
}
