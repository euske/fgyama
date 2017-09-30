//  Java2DF
//
package net.tabesugi.dfaaa;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFScope
//  Mapping from name -> variable.
//
public class DFScope implements Comparable<DFScope> {

    public DFGraph graph;
    public String name;
    public DFScope parent = null;
    public Map<ASTNode, DFScope> children = new HashMap<ASTNode, DFScope>();
    public int baseId = 0;

    public Map<String, DFVar> vars = new HashMap<String, DFVar>();

    public DFScope(DFGraph graph, String name) {
	this.graph = graph;
	this.name = name;
	graph.setRoot(this);
    }

    public DFScope(String name, DFScope parent) {
	this.graph = parent.graph;
	this.name = name;
	this.parent = parent;
    }

    @Override
    public String toString() {
	return ("<DFScope("+this.name+")>");
    }

    @Override
    public int compareTo(DFScope scope) {
	return this.name.compareTo(scope.name);
    }

    public DFScope addChild(String basename, ASTNode ast) {
	String name = this.name+"_"+basename+(this.baseId++);
	DFScope scope = new DFScope(name, this);
	this.children.put(ast, scope);
	return scope;
    }

    public DFScope getChild(ASTNode ast) {
	return this.children.get(ast);
    }

    public DFScope[] children() {
	DFScope[] scopes = new DFScope[this.children.size()];
	this.children.values().toArray(scopes);
	Arrays.sort(scopes);
	return scopes;
    }

    public void dump() {
	dump(System.out, "");
    }
    
    public void dump(PrintStream out, String indent) {
	out.println(indent+this.name+" {");
	String i2 = indent + "  ";
	for (DFVar var : this.vars.values()) {
	    out.println(i2+"defined: "+var);
	}
	for (DFScope scope : this.children.values()) {
	    scope.dump(out, i2);
	}
	out.println(indent+"}");
    }

    public DFVar add(String name, Type type) {
	DFVar var = new DFVar(this, name, type);
	this.vars.put(name, var);
	return var;
    }

    public Collection<DFVar> vars() {
	return this.vars.values();
    }

    public DFRef lookupVar(String name) {
	DFVar var = this.vars.get(name);
	if (var != null) {
	    return var;
	} else if (this.parent != null) {
	    return this.parent.lookupVar(name);
	} else {
	    return this.add(name, null);
	}
    }

    public DFRef lookupField(String name) {
	return this.lookupVar("."+name);
    }

    public DFRef lookupThis() {
	return this.lookupVar("#this");
    }

    public DFRef lookupSuper() {
	return this.lookupVar("#super");
    }
    
    public DFRef lookupReturn() {
	return this.lookupVar("#return");
    }
    
    public DFRef lookupArray() {
	return this.lookupVar("#array");
    }
}
