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
    public List<DFNode> nodes;
    public List<DFScope> children;

    public Map<String, DFVar> vars;
    public Set<DFRef> inputs;
    public Set<DFRef> outputs;

    public static int baseId = 0;
    public static int genId() {
	return baseId++;
    }

    public DFScope(String name) {
	this(name, null);
    }

    public DFScope(DFScope parent) {
	this("S"+genId(), parent);
    }

    public static DFRef THIS = new DFRef(null, "THIS");
    public static DFRef SUPER = new DFRef(null, "SUPER");
    public static DFRef RETURN = new DFRef(null, "RETURN");
    public static DFRef ARRAY = new DFRef(null, "[]");
    
    public DFScope(String name, DFScope parent) {
	this.name = name;
	this.parent = parent;
	if (parent != null) {
	    parent.children.add(this);
	}
	this.nodes = new ArrayList<DFNode>();
	this.children = new ArrayList<DFScope>();
	this.vars = new HashMap<String, DFVar>();
	this.inputs = new HashSet<DFRef>();
	this.outputs = new HashSet<DFRef>();
    }

    public String toString() {
	return ("<DFScope("+this.name+")>");
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
	StringBuilder inputs = new StringBuilder();
	for (DFRef ref : this.inputs) {
	    inputs.append(" "+ref);
	}
	out.println(i2+"inputs:"+inputs);
	StringBuilder outputs = new StringBuilder();
	for (DFRef ref : this.outputs) {
	    outputs.append(" "+ref);
	}
	out.println(i2+"outputs:"+outputs);
	StringBuilder loops = new StringBuilder();
	for (DFRef ref : this.getAllRefs()) {
	    loops.append(" "+ref);
	}
	out.println(i2+"loops:"+loops);
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

    public DFRef lookupThis() {
	return THIS;
    }
    
    public DFRef lookupSuper() {
	return SUPER;
    }
    
    public DFRef lookupReturn() {
	return RETURN;
    }
    
    public DFRef lookupArray() {
	return ARRAY;
    }
    
    public DFRef lookupField(String name) {
	return this.lookupVar("."+name);
    }
    
    public void finish(DFComponent cpt) {
	for (DFRef ref : this.vars.values()) {
	    cpt.removeRef(ref);
	}
    }

    public void addInput(DFRef ref) {
	if (this.parent != null) {
	    this.parent.addInput(ref);
	}
	this.inputs.add(ref);
    }

    public void addOutput(DFRef ref) {
	if (this.parent != null) {
	    this.parent.addOutput(ref);
	}
	this.outputs.add(ref);
    }

    public Set<DFRef> getAllRefs() {
	Set<DFRef> refs = new HashSet<DFRef>(this.inputs);
	refs.retainAll(this.outputs);
	return refs;
    }

    public void cleanup() {
        List<DFNode> removed = new ArrayList<DFNode>();
        for (DFNode node : this.nodes) {
            if (node.type() == DFNodeType.None &&
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
	for (DFScope child : this.children) {
	    child.cleanup();
	}
    }			   
}

