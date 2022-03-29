//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import javax.xml.stream.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFVarScope
//  Mapping from name -> reference.
//
public abstract class DFVarScope {

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
    public String toString() {
        return ("<DFVarScope("+this.getScopeName()+")>");
    }

    protected void writeInnerXML(XMLStreamWriter writer, DFNode[] nodes)
        throws XMLStreamException {
        for (DFNode node : nodes) {
            if (node.getScope() == this) {
                node.writeXML(writer);
            }
        }
    }

    public void writeXML(XMLStreamWriter writer, DFNode[] nodes)
        throws XMLStreamException {
        writer.writeStartElement("scope");
        writer.writeAttribute("name", this.getScopeName());
        this.writeInnerXML(writer, nodes);
        writer.writeEndElement();
    }

    public String getScopeName() {
        if (_outer == null) {
            return _name;
        } else {
            return _outer.getScopeName()+"."+_name;
        }
    }

    // Returns true if this scope *strictly* contains the given scope.
    public boolean contains(DFVarScope scope) {
        while (scope != null) {
            scope = scope._outer;
            if (scope == this) return true;
        }
        return false;
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

    public DFRef lookupArray(DFType type) {
        assert _outer != null;
        return _outer.lookupArray(type);
    }

    public DFRef lookupReturn() {
        assert _outer != null;
        return _outer.lookupReturn();
    }

    public DFRef lookupException(DFType type) {
        assert _outer != null;
        return _outer.lookupException(type);
    }

    public DFRef lookupBypass() {
        assert _outer != null;
        return _outer.lookupBypass();
    }

    public DFMethod lookupStaticMethod(
        SimpleName name, DFType[] argTypes, DFType returnType)
        throws MethodNotFound {
        if (_outer == null) throw new MethodNotFound(name, argTypes, returnType);
        return _outer.lookupStaticMethod(name, argTypes, returnType);
    }

    // dump: for debugging.
    public void dump() {
        dump(System.err, "");
    }
    public void dump(PrintStream out, String indent) {
        out.println(indent+this.getScopeName()+" {");
        this.dumpContents(out, indent+"  ");
        out.println(indent+"}");
    }
    protected void dumpContents(PrintStream out, String indent) {
    }
}
