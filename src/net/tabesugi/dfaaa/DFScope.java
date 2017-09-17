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
public class DFScope {

    public DFGraph graph;
    public String name;
    public DFScope parent = null;
    public Map<ASTNode, DFScope> children = new HashMap<ASTNode, DFScope>();
    public int baseId = 0;

    public Map<String, DFVar> vars = new HashMap<String, DFVar>();

    public DFScope(DFGraph graph, String name) {
	this.graph = graph;
	this.name = name;
	graph.addScope(this);
    }

    public DFScope(String name, DFScope parent) {
	this.graph = parent.graph;
	this.name = name;
	this.parent = parent;
	graph.addScope(this);
    }

    public String toString() {
	return ("<DFScope("+this.name+")>");
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

    public void addNode(DFNode node) {
	this.graph.addNode(node);
    }

    public void removeNode(DFNode node) {
	this.graph.removeNode(node);
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
	return this.lookupVar(":THIS");
    }

    public DFRef lookupSuper() {
	return this.lookupVar(":SUPER");
    }
    
    public DFRef lookupReturn() {
	return this.lookupVar(":RETURN");
    }
    
    public DFRef lookupArray() {
	return this.lookupVar(":ARRAY");
    }
    
    public void finish(DFComponent cpt) {
	for (DFRef ref : this.vars.values()) {
	    cpt.removeRef(ref);
	}
    }
}
