//  Java2DF
//
package net.tabesugi.dfaaa;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


// ProgNode: a DFNode that corresponds to an actual program point.
public abstract class ProgNode extends DFNode {

    public ASTNode ast;
    
    public ProgNode(DFScope scope, DFRef ref, ASTNode ast) {
	super(scope, ref);
	this.ast = ast;
    }
}

