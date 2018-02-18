//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;
import org.w3c.dom.*;


//  DFScope
//  Mapping from name -> reference.
//
public class DFScope {

    private DFGraph _graph;
    private String _name;
    private DFScope _parent = null;

    private List<DFScope> _children = new ArrayList<DFScope>();
    private Map<ASTNode, DFScope> _ast2child = new HashMap<ASTNode, DFScope>();
    private Map<String, DFRef> _refs = new HashMap<String, DFRef>();

    public DFScope(DFGraph graph, String name) {
	_graph = graph;
	_name = name;
	graph.setRoot(this);
    }

    public DFScope(String name, DFScope parent) {
	_graph = parent._graph;
	_name = name;
	_parent = parent;
    }

    @Override
    public String toString() {
	return ("<DFScope("+_name+")>");
    }

    public Element toXML(Document document, DFNode[] nodes) {
	Element elem = document.createElement("scope");
	elem.setAttribute("name", _name);
	for (DFScope child : this.getChildren()) {
	    elem.appendChild(child.toXML(document, nodes));
	}
	for (DFNode node : nodes) {
	    if (node.getScope() == this) {
		elem.appendChild(node.toXML(document));
	    }
	}
	return elem;
    }

    public DFGraph getGraph() {
        return _graph;
    }

    public String getName() {
        return _name;
    }

    public DFScope addChild(String basename, ASTNode ast) {
	int id = _children.size();
	String name = _name+"_"+basename+id;
	DFScope scope = new DFScope(name, this);
	_children.add(scope);
	_ast2child.put(ast, scope);
	return scope;
    }

    public DFScope getChild(ASTNode ast) {
	return _ast2child.get(ast);
    }

    public DFScope[] getChildren() {
	DFScope[] scopes = new DFScope[_children.size()];
	_children.toArray(scopes);
	return scopes;
    }

    public DFRef addVar(String name, Type type) {
	DFRef ref = new DFVar(this, name, type);
	_refs.put(name, ref);
	return ref;
    }

    public DFRef[] getRefs() {
	DFRef[] refs = new DFRef[_refs.size()];
	_refs.values().toArray(refs);
	Arrays.sort(refs);
	return refs;
    }

    public DFRef lookupRef(String name) {
	DFRef ref = _refs.get(name);
	if (ref != null) {
	    return ref;
	} else if (_parent != null) {
	    return _parent.lookupRef(name);
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

    // dump: for debugging.
    public void dump() {
	dump(System.out, "");
    }
    public void dump(PrintStream out, String indent) {
	out.println(indent+_name+" {");
	String i2 = indent + "  ";
	for (DFRef ref : _refs.values()) {
	    out.println(i2+"defined: "+ref);
	}
	for (DFScope scope : _children) {
	    scope.dump(out, i2);
	}
	out.println(indent+"}");
    }
}
