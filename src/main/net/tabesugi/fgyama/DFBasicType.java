//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFBasicType
//
public class DFBasicType extends DFType {

    private boolean _numeric;
    private String _name;

    public DFBasicType(String name) {
        _numeric = name.equals("@number");
        _name = name;
    }

    public DFBasicType(PrimitiveType.Code code) {
        _numeric = (
            (code == PrimitiveType.BYTE) || (code == PrimitiveType.CHAR) ||
            (code == PrimitiveType.DOUBLE) || (code == PrimitiveType.FLOAT) ||
            (code == PrimitiveType.INT) || (code == PrimitiveType.LONG) ||
            (code == PrimitiveType.SHORT));;
        _name = "@"+code.toString();
    }

    @Override
    public String toString() {
        return ("<DFBasicType("+this.getTypeName()+")>");
    }

    public String getTypeName() {
        return _name;
    }

    public boolean equals(DFType type) {
        return ((type instanceof DFBasicType) &&
                _name.equals(((DFBasicType)type)._name));
    }

    public int canConvertFrom(DFType type) {
        if (!(type instanceof DFBasicType)) return -1;
        if (this.equals(type)) return 0;
        return (_numeric && ((DFBasicType)type)._numeric)? 1 : -1;
    }

    public static final DFBasicType NUMBER =
        new DFBasicType("@number");
    public static final DFBasicType BOOLEAN =
        new DFBasicType(PrimitiveType.BOOLEAN);
    public static final DFBasicType BYTE =
        new DFBasicType(PrimitiveType.BYTE);
    public static final DFBasicType CHAR =
        new DFBasicType(PrimitiveType.CHAR);
    public static final DFBasicType DOUBLE =
        new DFBasicType(PrimitiveType.DOUBLE);
    public static final DFBasicType FLOAT =
        new DFBasicType(PrimitiveType.FLOAT);
    public static final DFBasicType INT =
        new DFBasicType(PrimitiveType.INT);
    public static final DFBasicType LONG =
        new DFBasicType(PrimitiveType.LONG);
    public static final DFBasicType SHORT =
        new DFBasicType(PrimitiveType.SHORT);
    public static final DFBasicType VOID =
        new DFBasicType(PrimitiveType.VOID);
}
