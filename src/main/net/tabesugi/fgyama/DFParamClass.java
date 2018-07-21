//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFParamClass
//
public class DFParamClass extends DFClassSpace {

    private DFClassSpace _genericKlass;
    private DFType[] _argTypes;

    public DFParamClass(
        DFClassSpace genericKlass, DFType[] argTypes) {
        super(null, null, null, null);
        _genericKlass = genericKlass;
        _argTypes = argTypes;
    }

    @Override
    public String toString() {
        return ("<DFParamClass("+this.getFullName()+")>");
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
    public DFMethod[] lookupMethods(SimpleName name, DFType[] argTypes) {
        DFMethod[] methods = _genericKlass.lookupMethods(name, argTypes);
        if (methods != null) {
            for (int i = 0; i < methods.length; i++) {
                methods[i] = methods[i].parameterize(_argTypes);
            }
        }
        return methods;
    }

    @Override
    public DFTypeFinder addFinders(DFTypeFinder finder) {
        return _genericKlass.addFinders(finder);
    }

    public int isSubclassOf(DFClassSpace klass) {
        if (!(klass instanceof DFParamClass)) return -1;
        DFParamClass pklass = (DFParamClass)klass;
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
