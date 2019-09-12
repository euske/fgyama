//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFMapType
//
public class DFMapType implements DFType {

    private String _name;
    private DFKlass _boundKlass;

    private String _sig = null;
    private List<Type> _ast = null;

    public DFMapType(String name) {
        _name = name;
        _boundKlass = DFBuiltinTypes.getObjectKlass();
    }

    @Override
    public String toString() {
        if (_sig != null) {
            return ("<DFMapType("+_name+":"+_sig+")>");
        } else if (_ast != null) {
            return ("<DFMapType("+_name+":"+_ast+")>");
        } else {
            return ("<DFMapType("+_name+")>");
        }
    }

    public String getTypeName() {
        return _name;
    }

    public boolean equals(DFType type) {
        return (this == type);
    }

    public DFKlass toKlass() {
        return _boundKlass;
    }

    public int canConvertFrom(DFType type, Map<DFMapType, DFType> typeMap) {
        assert !(type instanceof DFMapType);
        if (typeMap == null) {
            return _boundKlass.canConvertFrom(type, typeMap);
        }
        DFType self = typeMap.get(this);
        if (self == null) {
            int dist = _boundKlass.canConvertFrom(type, typeMap);
            if (dist < 0) return -1;
            typeMap.put(this, type);
            return dist;
        } else {
            return self.canConvertFrom(type, typeMap);
        }
    }

    public void setTypeBounds(String sig) {
        assert _sig == null && _ast == null;
        _sig = sig;
    }

    public void setTypeBounds(List<Type> ast) {
        assert _sig == null && _ast == null;
        _ast = ast;
    }

    public void build(DFTypeFinder finder)
        throws InvalidSyntax {
        assert _sig == null || _ast == null;
	if (_sig != null) {
	    JNITypeParser parser = new JNITypeParser(_sig);
	    try {
		_boundKlass = parser.resolveType(finder).toKlass();
	    } catch (TypeNotFound e) {
		Logger.error(
		    "DFMapType.build: TypeNotFound",
		    this, e.name, _sig, finder);
	    }
	} else if (_ast != null) {
	    try {
		for (Type type : _ast) {
		    _boundKlass = finder.resolve(type).toKlass();
		    break;
		}
	    } catch (TypeNotFound e) {
		Logger.error(
		    "DFMapType.build: TypeNotFound",
		    this, e.name, _ast);
	    }
	}
    }
}
