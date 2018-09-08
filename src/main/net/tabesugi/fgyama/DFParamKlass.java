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

    public DFParamKlass(DFKlass genericKlass, DFType[] argTypes) {
        super(genericKlass.getKlassName()+"<>", genericKlass);
        _argTypes = argTypes;
    }

    @Override
    public String toString() {
        return ("<DFParamKlass("+this.getFullName()+")>");
    }

    @Override
    public String getFullName() {
        return _genericKlass.getFullName()+"<>";
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
