//  Java2DF
//
package net.tabesugi.dfaaa;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


// JoinNode
public class JoinNode extends CondNode {

    public boolean recvTrue = false;
    public boolean recvFalse = false;
    
    public JoinNode(DFScope scope, DFRef ref, ASTNode ast,
		    DFNode value) {
	super(scope, ref, ast, value);
    }
    
    public DFNodeType type() {
	return DFNodeType.Join;
    }

    public String label() {
	return "join";
    }
    
    public void recv(boolean cond, DFNode node) {
	if (cond) {
	    this.recvTrue = true;
	    node.connect(this, 1, "true");
	} else {
	    this.recvFalse = true;
	    node.connect(this, 2, "false");
	}
    }

    public boolean isClosed() {
	return (this.recvTrue && this.recvFalse);
    };

    public void close(DFNode node) {
	if (!this.recvTrue) {
	    node.connect(this, 1, "true");
	}
	if (!this.recvFalse) {
	    node.connect(this, 2, "false");
	}
    }
}
