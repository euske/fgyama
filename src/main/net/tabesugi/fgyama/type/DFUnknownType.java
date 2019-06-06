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

    public String getTypeName() {
        return "?";
    }

    public boolean equals(DFType type) {
        return false;
    }

    public DFKlass getKlass() {
        return DFBuiltinTypes.getObjectKlass();
    }

    public int canConvertFrom(DFType type, Map<DFMapType, DFType> typeMap) {
        return -1;
    }

    public static final DFType UNKNOWN =
        new DFUnknownType();
}
