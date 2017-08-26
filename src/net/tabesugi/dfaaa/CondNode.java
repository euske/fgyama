//  Java2DF
//
package net.tabesugi.dfaaa;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


// CondNode
public abstract class CondNode extends ProgNode {
    
    public DFNode value;
    
    public CondNode(DFScope scope, DFRef ref, ASTNode ast,
		    DFNode value) {
	super(scope, ref, ast);
	this.value = value;
	value.connect(this, 1, DFLinkType.ControlFlow, "cond");
    }
}
