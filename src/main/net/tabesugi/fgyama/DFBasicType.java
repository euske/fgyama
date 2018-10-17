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

    private PrimitiveType.Code _code;

    public DFBasicType(PrimitiveType.Code code) {
        _code = code;
    }

    @Override
    public String toString() {
        return ("<DFBasicType("+this.getTypeName()+")>");
    }

    public String getTypeName() {
        if (_code == PrimitiveType.BYTE) {
            return "B";
        } else if (_code == PrimitiveType.CHAR) {
            return "C";
        } else if (_code == PrimitiveType.SHORT) {
            return "S";
        } else if (_code == PrimitiveType.INT) {
            return "I";
        } else if (_code == PrimitiveType.LONG) {
            return "J";
        } else if (_code == PrimitiveType.FLOAT) {
            return "F";
        } else if (_code == PrimitiveType.DOUBLE) {
            return "D";
        } else if (_code == PrimitiveType.BOOLEAN) {
            return "Z";
        } else {
            return "V";
        }
    }

    public boolean equals(DFType type) {
        return ((type instanceof DFBasicType) &&
                (_code == ((DFBasicType)type)._code));
    }

    public int canConvertFrom(DFType type) {
        if (!(type instanceof DFBasicType)) return -1;
        if (this.equals(type)) return 0;
        return (this.isNumeric() && ((DFBasicType)type).isNumeric())? 1 : -1;
    }

    public boolean isNumeric() {
        return ((_code == PrimitiveType.BYTE) ||
                (_code == PrimitiveType.CHAR) ||
                (_code == PrimitiveType.SHORT) ||
                (_code == PrimitiveType.INT) ||
                (_code == PrimitiveType.LONG) ||
                (_code == PrimitiveType.FLOAT) ||
                (_code == PrimitiveType.DOUBLE));
    }

    public static final DFBasicType BYTE =
        new DFBasicType(PrimitiveType.BYTE);
    public static final DFBasicType CHAR =
        new DFBasicType(PrimitiveType.CHAR);
    public static final DFBasicType SHORT =
        new DFBasicType(PrimitiveType.SHORT);
    public static final DFBasicType INT =
        new DFBasicType(PrimitiveType.INT);
    public static final DFBasicType LONG =
        new DFBasicType(PrimitiveType.LONG);
    public static final DFBasicType FLOAT =
        new DFBasicType(PrimitiveType.FLOAT);
    public static final DFBasicType DOUBLE =
        new DFBasicType(PrimitiveType.DOUBLE);
    public static final DFBasicType BOOLEAN =
        new DFBasicType(PrimitiveType.BOOLEAN);
    public static final DFBasicType VOID =
        new DFBasicType(PrimitiveType.VOID);
}
