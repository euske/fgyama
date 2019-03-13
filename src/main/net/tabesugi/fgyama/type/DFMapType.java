//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFMapType
//
public class DFMapType extends DFType {

    private String _name;

    private DFKlass _klass = null;
    private String _sig = null;
    private List<Type> _ast = null;

    public DFMapType(String name) {
        _name = name;
    }

    @Override
    public String toString() {
        return ("<DFMapType("+_name+")>");
    }

    public String getTypeName() {
        return _name;
    }

    public boolean equals(DFType type) {
        return (this == type);
    }

    public DFKlass getKlass() {
        return _klass;
    }

    public int canConvertFrom(DFType type, Map<DFMapType, DFType> typeMap) {
        assert !(type instanceof DFMapType);
        if (typeMap.get(this) == null) {
            typeMap.put(this, type);
            return 0;
        } else {
            return typeMap.get(this).canConvertFrom(type, typeMap);
        }
    }

    public DFType parameterize(Map<DFMapType, DFType> typeMap) {
        if (typeMap.containsKey(this)) {
            return typeMap.get(this);
        } else {
            return this;
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
        throws TypeNotFound {
        assert _sig == null || _ast == null;
        if (_sig != null) {
	    JNITypeParser parser = new JNITypeParser(_sig);
            _klass = parser.getType(finder).getKlass();
        } else if (_ast != null) {
            for (Type type : _ast) {
                _klass = finder.resolve(type).getKlass();
                break;
            }
        } else {
            _klass = DFBuiltinTypes.getObjectKlass();
        }
    }
}
