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

    public String name;
    public DFScope parent;
    public Map<ASTNode, DFScope> children;
    public int baseId;

    public List<DFNode> nodes;
    public Map<String, DFVar> vars;

    public DFScope(String name) {
	this(name, null);
    }

    public DFScope(String name, DFScope parent) {
	this.name = name;
	this.parent = parent;
	this.children = new HashMap<ASTNode, DFScope>();
	this.baseId = 0;
	
	this.nodes = new ArrayList<DFNode>();
	this.vars = new HashMap<String, DFVar>();
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

    public int addNode(DFNode node) {
	this.nodes.add(node);
	return this.nodes.size();
    }

    public void removeNode(DFNode node) {
        this.nodes.remove(node);
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
    
    public void finish(DFComponent cpt) {
	for (DFRef ref : this.vars.values()) {
	    cpt.removeRef(ref);
	}
    }

    public DFScope getChild(ASTNode ast) {
	return this.children.get(ast);
    }

    public void cleanup() {
        List<DFNode> removed = new ArrayList<DFNode>();
        for (DFNode node : this.nodes) {
            if (node.canOmit() &&
                node.send.size() == 1 &&
                node.recv.size() == 1) {
                removed.add(node);
            }
        }
        for (DFNode node : removed) {
            DFLink link0 = node.recv.get(0);
            DFLink link1 = node.send.get(0);
            if (link0.type == link1.type &&
		(link0.name == null || link1.name == null)) {
                node.remove();
                String name = link0.name;
                if (name == null) {
                    name = link1.name;
                }
                link0.src.connect(link1.dst, 1, link0.type, name);
            }
        }
	for (DFScope child : this.children.values()) {
	    child.cleanup();
	}
    }			   
}
