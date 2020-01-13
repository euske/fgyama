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
    private DFTypeSpace _outerSpace;
    private DFKlass _boundKlass;

    private String _sig = null;
    private List<Type> _types = null;

    public DFMapType(String name, DFTypeSpace outerSpace) {
        _name = name;
	_outerSpace = outerSpace;
        _boundKlass = DFBuiltinTypes.getObjectKlass();
    }

    @Override
    public String toString() {
        String name = _outerSpace.getSpaceName()+_name;
        if (_sig != null) {
            return ("<DFMapType("+name+":"+_sig+")>");
        } else if (_types != null) {
            return ("<DFMapType("+name+":"+_types+")>");
        } else {
            return ("<DFMapType("+name+")>");
        }
    }

    @Override
    public boolean equals(DFType type) {
        return (this == type);
    }

    @Override
    public DFKlass toKlass() {
        return _boundKlass;
    }

    @Override
    public String getTypeName() {
	//return _outerSpace.getSpaceName()+_name;
	return _name;
    }

    public String getName() {
	return _name;
    }

    @Override
    public int canConvertFrom(DFType type, Map<DFMapType, DFType> typeMap) {
	if (this == type) return 0;
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
        assert _sig == null && _types == null;
        _sig = sig;
    }

    public void setTypeBounds(List<Type> types) {
        assert _sig == null && _types == null;
        _types = types;
    }

    public void build(DFTypeFinder finder)
        throws InvalidSyntax {
        assert _sig == null || _types == null;
	if (_sig != null) {
	    JNITypeParser parser = new JNITypeParser(_sig);
	    try {
		_boundKlass = parser.resolveType(finder).toKlass();
	    } catch (TypeNotFound e) {
		Logger.error(
		    "DFMapType.build: TypeNotFound",
		    this, e.name, _sig, finder);
	    }
	} else if (_types != null) {
	    try {
		for (Type type : _types) {
		    _boundKlass = finder.resolve(type).toKlass();
		    break;
		}
	    } catch (TypeNotFound e) {
		Logger.error(
		    "DFMapType.build: TypeNotFound",
		    this, e.name, _types);
	    }
	}
    }
}
