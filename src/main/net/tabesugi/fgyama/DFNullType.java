//  Java2DF
//
package net.tabesugi.fgyama;


//  DFNullType
//
public class DFNullType extends DFType {

    public DFNullType() {
    }

    @Override
    public String toString() {
        return ("<DFNullType>");
    }

    public boolean equals(DFType type) {
        return (type instanceof DFNullType);
    }

    public String getName()
    {
        return "@null";
    }

    public int canConvertFrom(DFType type)
    {
        if (type instanceof DFNullType) return 0;
        return -1;
    }

    public static final DFType NULL =
        new DFNullType();
}
