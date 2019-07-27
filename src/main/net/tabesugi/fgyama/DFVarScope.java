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

    public DFRef lookupThis() {
        assert _outer != null;
        return _outer.lookupThis();
    }

    public DFRef lookupVar(String id)
        throws VariableNotFound {
        if (_outer == null) throw new VariableNotFound(id);
        return _outer.lookupVar(id);
    }

    public DFRef lookupVar(SimpleName name)
        throws VariableNotFound {
        return this.lookupVar(name.getIdentifier());
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
        return new DFRef[] {};
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
    protected void dumpContents(PrintStream out, String indent) {
    }
}
