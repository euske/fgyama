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

    public DFKlass getBaseKlass() {
        // XXX every MapType is default to Object.
        return DFBuiltinTypes.getObjectKlass();
    }

    public boolean equals(DFType type) {
        return (this == type);
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

    public void build(DFTypeFinder finder) {
    }
}
