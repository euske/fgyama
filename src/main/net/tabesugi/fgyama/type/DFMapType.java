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
    private DFKlass _boundKlass = null;

    private String _sig = null;
    private List<Type> _types = null;

    private DFMapType(String name, DFTypeSpace outerSpace, DFKlass outerKlass) {
        super(name, outerSpace, null, outerKlass);
        _name = name;
    }

    public DFMapType(
        String name, DFTypeSpace outerSpace, DFKlass outerKlass,
        List<Type> types) {
        this(name, outerSpace, outerKlass);
        _types = types;
    }

    public DFMapType(
        String name, DFTypeSpace outerSpace, DFKlass outerKlass,
        String sig) {
        this(name, outerSpace, outerKlass);
        _sig = sig;
    }

    @Override
    public String toString() {
        return ("<DFMapType("+this.getTypeName()+")>");
    }

    @Override
    public boolean equals(DFType type) {
        return (this == type);
    }

    public String getName() {
        return _name;
    }

    public DFKlass getBoundKlass() {
        assert _boundKlass != null;
        return _boundKlass;
    }

    @Override
    public DFKlass getKlass(String id) {
        if (_name.equals(id)) {
            return this;
        } else {
            return super.getKlass(id);
        }
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

    @Override
    public void build(DFTypeFinder finder)
        throws InvalidSyntax {
        assert _sig == null || _types == null;
        _boundKlass = DFBuiltinTypes.getObjectKlass();
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
