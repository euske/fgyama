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
    private DFTypeFinder _finder;
    private DFKlass _baseKlass = null;

    private String _sig = null;
    private List<Type> _types = null;

    private DFMapType(
        String name, DFTypeSpace outerSpace, DFTypeFinder finder) {
        super(name, outerSpace);
        _name = name;
        _finder = finder;
    }

    public DFMapType(
        String name, DFTypeSpace outerSpace, DFTypeFinder finder,
        List<Type> types) {
        this(name, outerSpace, finder);
        _types = types;
    }

    public DFMapType(
        String name, DFTypeSpace outerSpace, DFTypeFinder finder,
        String sig) {
        this(name, outerSpace, finder);
        _sig = sig;
    }

    protected DFKlass parameterize(Map<String, DFKlass> paramTypes) {
        assert false;
        return null;
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

    public boolean isInterface() {
        this.load();
        return _baseKlass.isInterface();
    }

    public boolean isEnum() {
        this.load();
        return _baseKlass.isEnum();
    }

    public DFKlass getBaseKlass() {
        this.load();
        return _baseKlass;
    }

    public DFKlass[] getBaseIfaces() {
        return null;
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
            return _baseKlass.isSubclassOf(klass, typeMap);
        }
        DFKlass self = typeMap.get(this);
        if (self == null) {
            int dist = _baseKlass.isSubclassOf(klass, typeMap);
            if (dist < 0) return -1;
            typeMap.put(this, klass);
            return dist;
        } else {
            return self.isSubclassOf(klass, typeMap);
        }
    }

    protected void build() {
        assert _sig == null || _types == null;
        _baseKlass = DFBuiltinTypes.getObjectKlass();
        if (_sig != null) {
            JNITypeParser parser = new JNITypeParser(_sig);
            parser.skipMapTypes();
            try {
                _baseKlass = parser.resolveType(_finder).toKlass();
            } catch (TypeNotFound e) {
                Logger.error(
                    "DFMapType.build: TypeNotFound",
                    this, e.name, _sig, _finder);
            }
        } else if (_types != null) {
            try {
                for (Type type : _types) {
                    _baseKlass = _finder.resolve(type).toKlass();
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
