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

    private Map<DFVarRef, DFVarRef> _paramFields =
        new HashMap<DFVarRef, DFVarRef>();
    private List<DFMethod> _paramMethods = null;

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
        DFVarRef ref0 = _genericKlass.lookupField(id);
	DFVarRef ref1 = _paramFields.get(ref0);
	if (ref1 == null) {
	    ref1 = ref0.parameterize(_argTypes);
	    _paramFields.put(ref0, ref1);
	}
	return ref1;
    }

    @Override
    protected List<DFMethod> getMethods() {
	if (_paramMethods == null) {
	    _paramMethods = new ArrayList<DFMethod>();
	    for (DFMethod method : _genericKlass.getMethods()) {
		method = method.parameterize(_argTypes);
		_paramMethods.add(method);
	    }
	}
	return _paramMethods;
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
