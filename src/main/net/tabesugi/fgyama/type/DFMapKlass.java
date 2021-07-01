//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFMapKlass
//
public class DFMapKlass extends DFKlass {

    private String _name;
    private String _sig = null;
    private List<Type> _types = null;

    private DFTypeFinder _finder = null;
    private DFKlass _baseKlass = null;

    private DFMapKlass(
        String name, DFTypeSpace outerSpace, DFKlass outerKlass) {
        super(name, outerSpace, outerKlass, null);
        _name = name;
    }

    public DFMapKlass(
        String name, DFTypeSpace outerSpace, DFKlass outerKlass,
        List<Type> types) {
        this(name, outerSpace, outerKlass);
        _types = types;
    }

    public DFMapKlass(
        String name, DFTypeSpace outerSpace, DFKlass outerKlass,
        String sig, DFTypeFinder finder) {
        this(name, outerSpace, outerKlass);
        _sig = sig;
        _finder = finder;
    }

    @Override
    public String toString() {
        return ("<DFMapKlass("+this.getTypeName()+")>");
    }

    @Override
    public boolean equals(DFType type) {
        return (this == type);
    }

    public String getName() {
        return _name;
    }

    @Override
    public boolean isInterface() {
        this.load();
        return _baseKlass.isInterface();
    }

    @Override
    public boolean isResolved() {
        return false;
    }

    @Override
    public boolean isEnum() {
        this.load();
        return _baseKlass.isEnum();
    }

    @Override
    public DFKlass getBaseKlass() {
        this.load();
        return _baseKlass;
    }

    @Override
    public DFKlass[] getBaseIfaces() {
        this.load();
        return _baseKlass.getBaseIfaces();
    }

    @Override
    public DFMethod[] getMethods() {
        this.load();
        return _baseKlass.getMethods();
    }

    @Override
    public FieldRef[] getFields() {
        this.load();
        return _baseKlass.getFields();
    }

    @Override
    public DFKlass getKlass(String id) {
        if (_name.equals(id)) {
            return this;
        } else {
            return super.getKlass(id);
        }
    }

    protected DFKlass parameterize(Map<String, DFKlass> paramTypes) {
        assert false;
        return null;
    }

    protected void setFinder(DFTypeFinder finder) {
        assert _finder == null;
        _finder = finder;
    }

    @Override
    public int canConvertFrom(DFKlass klass, Map<DFMapKlass, DFKlass> typeMap)
        throws TypeIncompatible {
        if (this == klass) return 0;
        this.load();
        if (typeMap == null) {
            return _baseKlass.canConvertFrom(klass, typeMap);
        }
        DFKlass self = typeMap.get(this);
        if (self == null) {
            typeMap.put(this, klass);
            int dist;
            try {
                dist = _baseKlass.canConvertFrom(klass, typeMap);
            } catch (TypeIncompatible e) {
                dist = 9999;    // XXX unchecked conversion.
            }
            return dist;
        } else {
            return self.canConvertFrom(klass, typeMap);
        }
    }

    protected void load() {
        assert _sig == null || _types == null;
        if (_baseKlass != null) return;
        _baseKlass = DFBuiltinTypes.getObjectKlass();
        if (_sig != null) {
            JNITypeParser parser = new JNITypeParser(_sig);
            parser.getTypeSlots();
            try {
                _baseKlass = parser.resolveType(_finder).toKlass();
            } catch (TypeNotFound e) {
                Logger.error(
                    "DFMapKlass.build: TypeNotFound",
                    e.name, _sig, _finder, this);
            }
        } else if (_types != null) {
            try {
                for (Type type : _types) {
                    _baseKlass = _finder.resolve(type).toKlass();
                    break;
                }
            } catch (TypeNotFound e) {
                Logger.error(
                    "DFMapKlass.build: TypeNotFound",
                    e.name, _types, _finder, this);
            }
        }
    }
}
