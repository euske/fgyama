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
    private DFType[] _paramTypes;
    private Map<DFMapType, DFType> _typeMap;

    private Map<DFVarRef, DFVarRef> _paramFields =
        new HashMap<DFVarRef, DFVarRef>();
    private List<DFMethod> _paramMethods = null;

    public DFParamKlass(String name, DFKlass genericKlass,
                        DFMapType[] mapTypes, DFType[] paramTypes) {
        super(name, genericKlass);
        assert genericKlass != null;
        assert mapTypes != null;
        assert paramTypes != null;
        _genericKlass = genericKlass;
        _paramTypes = paramTypes;
        _typeMap = new HashMap<DFMapType, DFType>();
        for (int i = 0; i < mapTypes.length; i++) {
            assert mapTypes[i] != null;
            assert paramTypes[i] != null;
            _typeMap.put(mapTypes[i], paramTypes[i]);
        }
    }

    public static String getParamName(DFType[] paramTypes) {
        StringBuilder b = new StringBuilder();
        for (DFType type : paramTypes) {
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

    public DFType[] getParamTypes() {
        return _paramTypes;
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
    public List<DFMethod> getMethods() {
	if (_paramMethods == null) {
	    _paramMethods = new ArrayList<DFMethod>();
	    for (DFMethod method0 : _genericKlass.getMethods()) {
		DFMethod method1 = method0.parameterize(_typeMap);
		_paramMethods.add(method1);
	    }
	}
	return _paramMethods;
    }

    public int isSubclassOf(DFKlass klass, Map<DFMapType, DFType> typeMap) {
        if (!(klass instanceof DFParamKlass)) {
	    // A<T> isSubclassOf B -> A isSubclassOf B.
	    return _genericKlass.isSubclassOf(klass, typeMap);
	}
	// A<T> isSubclassOf B<S>?
        DFParamKlass pklass = (DFParamKlass)klass;
        if (_paramTypes.length != pklass._paramTypes.length) return -1;
	// A isSubclassOf B?
        int dist = _genericKlass.isSubclassOf(pklass._genericKlass, typeMap);
        if (dist < 0) return -1;
        for (int i = 0; i < _paramTypes.length; i++) {
	    // T isSubclassOf S? -> S canConvertFrom T?
            int d = pklass._paramTypes[i].canConvertFrom(_paramTypes[i], typeMap);
            if (d < 0) return -1;
            dist += d;
        }
        return dist;
    }
}
