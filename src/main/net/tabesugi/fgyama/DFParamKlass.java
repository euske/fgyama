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
    private DFType[] _mapTypes;
    private Map<DFParamType, DFType> _typeMap;

    private Map<DFVarRef, DFVarRef> _paramFields =
        new HashMap<DFVarRef, DFVarRef>();
    private List<DFMethod> _paramMethods = null;

    public DFParamKlass(String name, DFKlass genericKlass,
                        DFParamType[] paramTypes, DFType[] mapTypes) {
        super(name, genericKlass);
        _genericKlass = genericKlass;
        _mapTypes = mapTypes;
        _typeMap = new HashMap<DFParamType, DFType>();
        for (int i = 0; i < mapTypes.length; i++) {
            _typeMap.put(paramTypes[i], mapTypes[i]);
        }
    }

    public static String getParamName(DFType[] mapTypes) {
        StringBuilder b = new StringBuilder();
        for (DFType type : mapTypes) {
            if (0 < b.length()) {
                b.append(",");
            }
            b.append(type.getTypeName());
        }
        return "<"+b.toString()+">";
    }

    public DFKlass getGeneric() {
        return _genericKlass;
    }

    public DFType[] getMapTypes() {
        return _mapTypes;
    }

    @Override
    public String toString() {
        return ("<DFParamKlass("+this.getFullName()+")");
    }

    @Override
    protected DFVarRef lookupField(String id)
        throws VariableNotFound {
        DFVarRef ref0 = _genericKlass.lookupField(id);
	DFVarRef ref1 = _paramFields.get(ref0);
	if (ref1 == null) {
	    ref1 = ref0.parameterize(_typeMap);
	    _paramFields.put(ref0, ref1);
	}
	return ref1;
    }

    @Override
    protected List<DFMethod> getMethods() {
	if (_paramMethods == null) {
	    _paramMethods = new ArrayList<DFMethod>();
	    for (DFMethod method0 : _genericKlass.getMethods()) {
		DFMethod method1 = method0.parameterize(_typeMap);
		_paramMethods.add(method1);
	    }
	}
	return _paramMethods;
    }

    @Override
    public DFTypeFinder addFinders(DFTypeFinder finder) {
        return _genericKlass.addFinders(finder);
    }

    public int isSubclassOf(DFKlass klass) {
        if (!(klass instanceof DFParamKlass)) {
	    // A<T> isSubclassOf B -> A isSubclassOf B.
	    return _genericKlass.isSubclassOf(klass);
	}
	// A<T> isSubclassOf B<S>?
        DFParamKlass pklass = (DFParamKlass)klass;
        if (_mapTypes.length != pklass._mapTypes.length) return -1;
	// A isSubclassOf B?
        int dist = _genericKlass.isSubclassOf(pklass._genericKlass);
        if (dist < 0) return -1;
        for (int i = 0; i < _mapTypes.length; i++) {
	    // T isSubclassOf S? -> S canConvertFrom T?
            int d = pklass._mapTypes[i].canConvertFrom(_mapTypes[i]);
            if (d < 0) return -1;
            dist += d;
        }
        return dist;
    }
}
