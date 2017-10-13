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

    private List<DFScope> children = new ArrayList<DFScope>();
    private Map<ASTNode, DFScope> ast2child = new HashMap<ASTNode, DFScope>();
    private Map<String, DFVar> vars = new HashMap<String, DFVar>();

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

    public DFScope addChild(String basename, ASTNode ast) {
	int id = this.children.size();
	String name = this.name+"_"+basename+id;
	DFScope scope = new DFScope(name, this);
	this.children.add(scope);
	this.ast2child.put(ast, scope);
	return scope;
    }

    public DFScope getChild(ASTNode ast) {
	return this.ast2child.get(ast);
    }

    public DFScope[] getChildren() {
	DFScope[] scopes = new DFScope[this.children.size()];
	this.children.toArray(scopes);
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
	for (DFScope scope : this.children) {
	    scope.dump(out, i2);
	}
	out.println(indent+"}");
    }

    public DFVar add(String name, Type type) {
	DFVar var = new DFVar(this, name, type);
	this.vars.put(name, var);
	return var;
    }

    public DFVar[] vars() {
	DFVar[] vars = new DFVar[this.vars.size()];
	this.vars.values().toArray(vars);
	Arrays.sort(vars);
	return vars;
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
