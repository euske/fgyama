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
    private DFTypeSpace _mapTypeSpace;

    public static String getParamNames(DFType[] paramTypes) {
        StringBuilder b = new StringBuilder();
        for (DFType type : paramTypes) {
            if (0 < b.length()) {
                b.append(",");
            }
            b.append(type.getTypeName());
        }
        return "<"+b.toString()+">";
    }

    public DFParamKlass(String name, DFKlass genericKlass,
                        DFMapType[] mapTypes, DFType[] paramTypes) {
        super(name, genericKlass);
        assert genericKlass != null;
        assert mapTypes != null;
        assert paramTypes != null;
        _genericKlass = genericKlass;
        _paramTypes = paramTypes;
        _typeMap = new HashMap<DFMapType, DFType>();
        _mapTypeSpace = new DFTypeSpace(null, name);
        for (int i = 0; i < paramTypes.length; i++) {
            assert mapTypes[i] != null;
            assert paramTypes[i] != null;
            _typeMap.put(mapTypes[i], paramTypes[i]);
            _mapTypeSpace.addKlass(
                mapTypes[i].getTypeName(), (DFKlass)paramTypes[i]);
        }
        DFTypeFinder finder = genericKlass.getBaseFinder();
        assert finder != null;
        finder = new DFTypeFinder(finder, _mapTypeSpace);
        this.setBaseFinder(finder);
    }

    public DFKlass getGeneric() {
        return _genericKlass;
    }

    @Override
    public String toString() {
        return ("<DFParamKlass("+this.getFullName()+")");
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
