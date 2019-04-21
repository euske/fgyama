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

    private DFVarScope _outer;
    private String _name;

    private Map<String, DFRef> _id2ref =
        new HashMap<String, DFRef>();

    protected DFVarScope(String name) {
        _outer = null;
        _name = name;
    }

    protected DFVarScope(DFVarScope outer, String name) {
        _outer = outer;
        _name = name;
    }

    public DFVarScope(DFVarScope outer, SimpleName name) {
        this(outer, name.getIdentifier());
    }

    @Override
    public int compareTo(DFVarScope scope) {
        if (scope == null) return +1;
        if (scope == this) return 0;
        if (scope._outer == _outer) {
            return _name.compareTo(scope._name);
        }
        if (_outer == null) return -1;
        return _outer.compareTo(scope._outer);
    }

    @Override
    public String toString() {
        return ("<DFVarScope("+this.getScopeName()+")>");
    }

    public Element toXML(Document document, DFNode[] nodes) {
        Element elem = document.createElement("scope");
        elem.setAttribute("name", this.getScopeName());
        for (DFVarScope child : this.getChildren()) {
            elem.appendChild(child.toXML(document, nodes));
        }
        for (DFNode node : nodes) {
            if (node.getScope() == this) {
		Element e = node.toXML(document);
                elem.appendChild(e);
            }
        }
        return elem;
    }

    public String getScopeName() {
        if (_outer == null) {
            return _name;
        } else {
            return _outer.getScopeName()+"."+_name;
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
        assert _outer != null;
        return _outer.lookupThis();
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
            if (_outer == null) throw e;
            return _outer.lookupVar(name);
        }
    }

    public DFMethod lookupStaticMethod(SimpleName name, DFType[] argTypes)
        throws MethodNotFound {
	if (_outer == null) throw new MethodNotFound(name.getIdentifier(), argTypes);
	return _outer.lookupStaticMethod(name, argTypes);
    }

    public DFRef lookupArgument(int index)
        throws VariableNotFound {
        assert _outer != null;
        return _outer.lookupArgument(index);
    }

    public DFRef lookupReturn()
        throws VariableNotFound {
        assert _outer != null;
        return _outer.lookupReturn();
    }

    public DFRef lookupException()
        throws VariableNotFound {
        assert _outer != null;
        return _outer.lookupException();
    }

    public DFRef lookupArray(DFType type) {
        assert _outer != null;
        return _outer.lookupArray(type);
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
        out.println(indent+this.getScopeName()+" {");
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
