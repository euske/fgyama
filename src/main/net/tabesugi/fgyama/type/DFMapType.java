//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFMapType
//
public class DFMapType extends DFKlass {

    private String _name;
    private DFKlass _boundKlass;

    private String _sig = null;
    private List<Type> _types = null;

    public DFMapType(String name, DFTypeSpace outerSpace) {
        super(name, outerSpace, null, null);
        _name = name;
        _boundKlass = DFBuiltinTypes.getObjectKlass();
    }

    @Override
    public String toString() {
        if (_sig != null) {
            return ("<DFMapType("+this.getTypeName()+" extends "+_sig+")>");
        } else if (_types != null) {
            return ("<DFMapType("+this.getTypeName()+" extends "+_types+")>");
        } else {
            return ("<DFMapType("+this.getTypeName()+")>");
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

    public String getName() {
        return _name;
    }

    @Override
    public int isSubclassOf(DFKlass klass, Map<DFMapType, DFKlass> typeMap) {
        if (this == klass) return 0;
        assert !(klass instanceof DFMapType);
        if (typeMap == null) {
            return _boundKlass.isSubclassOf(klass, typeMap);
        }
        DFKlass self = typeMap.get(this);
        if (self == null) {
            int dist = _boundKlass.isSubclassOf(klass, typeMap);
            if (dist < 0) return -1;
            typeMap.put(this, klass);
            return dist;
        } else {
            return self.isSubclassOf(klass, typeMap);
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
