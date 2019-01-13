//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;
import org.w3c.dom.*;


//  DFVarScope
//  Mapping from name -> reference.
//
public class DFVarScope implements Comparable<DFVarScope> {

    private DFVarScope _parent;
    private String _name;

    private Map<String, DFRef> _id2ref =
        new HashMap<String, DFRef>();

    protected DFVarScope(String name) {
        _parent = null;
        _name = name;
    }

    protected DFVarScope(DFVarScope parent, String name) {
        _parent = parent;
        _name = name;
    }

    public DFVarScope(DFVarScope parent, SimpleName name) {
        this(parent, name.getIdentifier());
    }

    @Override
    public int compareTo(DFVarScope scope) {
        if (scope == null) return +1;
        if (scope == this) return 0;
        if (scope._parent == _parent) {
            return _name.compareTo(scope._name);
        }
        if (_parent == null) return -1;
        return _parent.compareTo(scope._parent);
    }

    @Override
    public String toString() {
        return ("<DFVarScope("+this.getFullName()+")>");
    }

    public Element toXML(
	Document document, DFNode[] nodes,
	Set<DFNode> input, Set<DFNode> output) {
        Element elem = document.createElement("scope");
        elem.setAttribute("name", this.getFullName());
        for (DFVarScope child : this.getChildren()) {
            elem.appendChild(child.toXML(document, nodes, input, output));
        }
        for (DFNode node : nodes) {
            if (node.getScope() == this) {
		Element e = node.toXML(document, input, output);
                elem.appendChild(e);
            }
        }
        return elem;
    }

    public String getFullName() {
        if (_parent == null) {
            return _name;
        } else {
            return _parent.getFullName()+"."+_name;
        }
    }

    protected DFRef addRef(String id, DFType type) {
        return this.addRef(id, type, this);
    }
    protected DFRef addRef(String id, DFType type, DFVarScope scope) {
        DFRef ref = _id2ref.get(id);
        if (ref == null) {
            ref = new DFRef(scope, id, type);
            _id2ref.put(id, ref);
        }
        return ref;
    }

    protected DFRef lookupRef(String id)
        throws VariableNotFound {
        DFRef ref = _id2ref.get(id);
        if (ref != null) {
            return ref;
        } else {
            throw new VariableNotFound(id);
        }
    }

    public DFRef lookupThis() {
        assert _parent != null;
        return _parent.lookupThis();
    }

    protected DFRef lookupVar1(String id)
        throws VariableNotFound {
        return this.lookupRef("$"+id);
    }

    public DFRef lookupVar(SimpleName name)
        throws VariableNotFound {
        try {
            return this.lookupVar1(name.getIdentifier());
        } catch (VariableNotFound e) {
            if (_parent == null) throw e;
            return _parent.lookupVar(name);
        }
    }

    public DFMethod lookupStaticMethod(SimpleName name, DFType[] argTypes)
        throws MethodNotFound {
	if (_parent == null) throw new MethodNotFound(name.getIdentifier(), argTypes);
	return _parent.lookupStaticMethod(name, argTypes);
    }

    public DFRef lookupArgument(int index)
        throws VariableNotFound {
        try {
            return this.lookupRef("#arg"+index);
        } catch (VariableNotFound e) {
            if (_parent == null) throw e;
            return _parent.lookupArgument(index);
        }
    }

    public DFRef lookupReturn()
        throws VariableNotFound {
        try {
            return this.lookupRef("#return");
        } catch (VariableNotFound e) {
            if (_parent == null) throw e;
            return _parent.lookupReturn();
        }
    }

    public DFRef lookupException()
        throws VariableNotFound {
        try {
            return this.lookupRef("#exception");
        } catch (VariableNotFound e) {
            if (_parent == null) throw e;
            return _parent.lookupException();
        }
    }

    public DFRef lookupArray(DFType type) {
        return _parent.lookupArray(type);
    }

    public DFRef[] getRefs() {
        DFRef[] refs = new DFRef[_id2ref.size()];
        _id2ref.values().toArray(refs);
        return refs;
    }

    public DFLocalVarScope getChildByAST(ASTNode ast) {
        return null;
    }

    public DFVarScope[] getChildren() {
        return new DFVarScope[] {};
    }

    // dump: for debugging.
    public void dump() {
        dump(System.err, "");
    }
    public void dump(PrintStream out, String indent) {
        out.println(indent+this.getFullName()+" {");
        String i2 = indent + "  ";
        this.dumpContents(out, i2);
        for (DFVarScope scope : this.getChildren()) {
            scope.dump(out, i2);
        }
        out.println(indent+"}");
    }
    public void dumpContents(PrintStream out, String indent) {
        for (DFRef ref : _id2ref.values()) {
            out.println(indent+"defined: "+ref);
        }
    }
}
