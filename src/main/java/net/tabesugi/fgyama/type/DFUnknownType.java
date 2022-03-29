//  Java2DF
//
package net.tabesugi.fgyama;
import java.util.*;


//  DFUnknownType
//
public class DFUnknownType implements DFType {

    private DFUnknownType() {
    }

    @Override
    public String toString() {
        return ("<DFUnknownType>");
    }

    @Override
    public String getTypeName() {
        return "?";
    }

    @Override
    public boolean equals(DFType type) {
        return false;
    }

    @Override
    public DFKlass toKlass() {
        return DFBuiltinTypes.getObjectKlass();
    }

    @Override
    public int canConvertFrom(DFType type, Map<DFMapKlass, DFKlass> typeMap)
        throws TypeIncompatible {
        throw new TypeIncompatible(this, type);
    }

    public static final DFType UNKNOWN =
        new DFUnknownType();
}
