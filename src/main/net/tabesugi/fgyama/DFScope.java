//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFScope
//  Mapping from name -> reference.
//
public class DFScope {

    public DFGraph graph;
    public String name;
    public DFScope parent = null;

    private List<DFScope> children = new ArrayList<DFScope>();
    private Map<ASTNode, DFScope> ast2child = new HashMap<ASTNode, DFScope>();
    private Map<String, DFRef> refs = new HashMap<String, DFRef>();

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
	for (DFRef ref : this.refs.values()) {
	    out.println(i2+"defined: "+ref);
	}
	for (DFScope scope : this.children) {
	    scope.dump(out, i2);
	}
	out.println(indent+"}");
    }

    public DFRef addVar(String name, Type type) {
	DFRef ref = new DFVar(this, name, type);
	this.refs.put(name, ref);
	return ref;
    }

    public DFRef[] refs() {
	DFRef[] refs = new DFRef[this.refs.size()];
	this.refs.values().toArray(refs);
	Arrays.sort(refs);
	return refs;
    }

    public DFRef lookupRef(String name) {
	DFRef ref = this.refs.get(name);
	if (ref != null) {
	    return ref;
	} else if (this.parent != null) {
	    return this.parent.lookupRef(name);
	} else {
	    return this.addVar(name, null);
	}
    }

    public DFRef lookupField(String name) {
	return this.lookupRef("."+name);
    }

    public DFRef lookupThis() {
	return this.lookupRef("#this");
    }

    public DFRef lookupSuper() {
	return this.lookupRef("#super");
    }

    public DFRef lookupReturn() {
	return this.lookupRef("#return");
    }

    public DFRef lookupArray() {
	return this.lookupRef("#array");
    }
}
