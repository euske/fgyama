//  Java2DF
//
package net.tabesugi.dfaaa;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFScopeMap
//
public class DFScopeMap {

    public Map<ASTNode, DFScope> scopes;

    public DFScopeMap() {
	this.scopes = new HashMap<ASTNode, DFScope>();
    }

    public void put(ASTNode ast, DFScope scope) {
	this.scopes.put(ast, scope);
    }

    public DFScope get(ASTNode ast) {
	return this.scopes.get(ast);
    }
}

