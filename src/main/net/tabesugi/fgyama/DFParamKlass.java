//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFParamKlass
//
public class DFParamKlass extends DFKlass {

    private DFKlass _genericKlass;
    private DFType[] _argTypes;

    public DFParamKlass(String name, DFKlass genericKlass, DFType[] argTypes) {
        super(name, genericKlass);
        _genericKlass = genericKlass;
        _argTypes = argTypes;
    }

    public static String getParamName(DFType[] argTypes) {
        StringBuilder b = new StringBuilder();
        for (DFType type : argTypes) {
            if (0 < b.length()) {
                b.append(",");
            }
            b.append(type.getTypeName());
        }
        return "<"+b.toString()+">";
    }

    @Override
    public String toString() {
        return ("<DFParamKlass("+this.getFullName()+", "+getParamName(_argTypes)+")>");
    }

    @Override
    protected DFVarRef lookupField(String id)
        throws VariableNotFound {
        DFVarRef ref = _genericKlass.lookupField(id);
        return ref.parameterize(_argTypes);
    }

    @Override
    public DFVarRef lookupField(SimpleName name)
        throws VariableNotFound {
        DFVarRef ref = _genericKlass.lookupField(name);
        return ref.parameterize(_argTypes);
    }

    @Override
    public DFMethod lookupMethod(SimpleName name, DFType[] argTypes) {
        DFMethod method = _genericKlass.lookupMethod(name, argTypes);
        if (method != null) {
            method = method.parameterize(_argTypes);
        }
        return method;
    }

    @Override
    public DFTypeFinder addFinders(DFTypeFinder finder) {
        return _genericKlass.addFinders(finder);
    }

    public int isSubclassOf(DFKlass klass) {
        if (!(klass instanceof DFParamKlass)) return -1;
        DFParamKlass pklass = (DFParamKlass)klass;
        if (_argTypes.length != pklass._argTypes.length) return -1;
        int dist = pklass._genericKlass.isSubclassOf(_genericKlass);
        if (dist < 0) return dist;
        for (int i = 0; i < _argTypes.length; i++) {
            int d = _argTypes[i].canConvertFrom(pklass._argTypes[i]);
            if (d < 0) return d;
            dist += d;
        }
        return dist;
    }
}
