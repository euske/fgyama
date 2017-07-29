//  Java2DF
//
package net.tabesugi.dfaaa;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


// DistNode: a DFNode that distributes a value to multiple nodes.
public class DistNode extends DFNode {

    public DistNode(DFScope scope, DFRef ref) {
	super(scope, ref);
    }

    public DFNodeType type() {
	return DFNodeType.None;
    }

    public String label() {
	return null;
    }
}

