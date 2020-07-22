//  Java2DF
//
package net.tabesugi.fgyama;
import java.util.*;


//  DFNullType
//
public class DFNullType implements DFType {

    private DFNullType() {
    }

    @Override
    public String toString() {
        return ("<DFNullType>");
    }

    @Override
    public String getTypeName() {
        return "@null";
    }

    @Override
    public boolean equals(DFType type) {
        return (type instanceof DFNullType);
    }

    @Override
    public DFKlass toKlass() {
        return DFBuiltinTypes.getObjectKlass();
    }

    @Override
    public int canConvertFrom(DFType type, Map<DFMapType, DFKlass> typeMap)
        throws TypeIncompatible {
        if (type instanceof DFNullType) return 0;
        throw new TypeIncompatible(this, type);
    }

    public static final DFType NULL =
        new DFNullType();
}
