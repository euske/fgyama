//  Java2DF
//
package net.tabesugi.dfaaa;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;
import org.w3c.dom.*;


// ProgNode: a DFNode that corresponds to an actual program point.
public abstract class ProgNode extends DFNode {

    public ASTNode ast;
    
    public ProgNode(DFScope scope, DFRef ref, ASTNode ast) {
	super(scope, ref);
	this.ast = ast;
    }
    
    @Override
    public Element toXML(Document document) {
	Element elem = super.toXML(document);
	if (this.ast != null) {
	    Element east = document.createElement("ast");
	    east.setAttribute("type", Integer.toString(this.ast.getNodeType()));
	    east.setAttribute("start", Integer.toString(this.ast.getStartPosition()));
	    east.setAttribute("length", Integer.toString(this.ast.getLength()));
	    elem.appendChild(east);
	}
	return elem;
    }
}

