//  Java2DF
//
package net.tabesugi.fgyama;


//  DFNullType
//
public class DFNullType extends DFType {

    private DFNullType() {
    }

    @Override
    public String toString() {
        return ("<DFNullType>");
    }

    public String getTypeName() {
        return "@null";
    }

    public boolean equals(DFType type) {
        return (type instanceof DFNullType);
    }

    public int canConvertFrom(DFType type) {
        if (type instanceof DFNullType) return 0;
        return -1;
    }

    public static final DFType NULL =
        new DFNullType();
}
