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
    private int _rank;

    private DFBasicType(PrimitiveType.Code code, int rank) {
        _code = code;
        _rank = rank;
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
        return (this == type);
    }

    public int canConvertFrom(DFType type) {
	// Auto-unboxing.
	if ((_code == PrimitiveType.BYTE &&
	     type == DFRootTypeSpace.getByteKlass()) ||
	    (_code == PrimitiveType.CHAR &&
	     type == DFRootTypeSpace.getCharacterKlass()) ||
	    (_code == PrimitiveType.SHORT &&
	     type == DFRootTypeSpace.getShortKlass()) ||
	    (_code == PrimitiveType.INT &&
	     type == DFRootTypeSpace.getIntegerKlass()) ||
	    (_code == PrimitiveType.LONG &&
	     type == DFRootTypeSpace.getLongKlass()) ||
	    (_code == PrimitiveType.FLOAT &&
	     type == DFRootTypeSpace.getFloatKlass()) ||
	    (_code == PrimitiveType.DOUBLE &&
	     type == DFRootTypeSpace.getDoubleKlass()) ||
	    (_code == PrimitiveType.BOOLEAN &&
	     type == DFRootTypeSpace.getBooleanKlass())) {
	    return 0;
	}
        if (this == type) return 0;
        if (!(type instanceof DFBasicType)) return -1;
        int rank = ((DFBasicType)type)._rank;
        if (this._rank == 0 || rank == 0) return -1;
        return (this._rank - rank);
    }

    public boolean isNumeric() {
        return (_rank != 0);
    }

    public static final DFBasicType BYTE =
        new DFBasicType(PrimitiveType.BYTE, 1);
    public static final DFBasicType CHAR =
        new DFBasicType(PrimitiveType.CHAR, 1);
    public static final DFBasicType SHORT =
        new DFBasicType(PrimitiveType.SHORT, 2);
    public static final DFBasicType INT =
        new DFBasicType(PrimitiveType.INT, 3);
    public static final DFBasicType LONG =
        new DFBasicType(PrimitiveType.LONG, 4);
    public static final DFBasicType FLOAT =
        new DFBasicType(PrimitiveType.FLOAT, 5);
    public static final DFBasicType DOUBLE =
        new DFBasicType(PrimitiveType.DOUBLE, 6);
    public static final DFBasicType BOOLEAN =
        new DFBasicType(PrimitiveType.BOOLEAN, 0);
    public static final DFBasicType VOID =
        new DFBasicType(PrimitiveType.VOID, 0);

    public static DFBasicType getType(PrimitiveType.Code code)
        throws TypeNotFound {
	if (code == PrimitiveType.BYTE) {
	    return BYTE;
	} else if (code == PrimitiveType.CHAR) {
	    return CHAR;
	} else if (code == PrimitiveType.SHORT) {
	    return SHORT;
	} else if (code == PrimitiveType.INT) {
	    return INT;
	} else if (code == PrimitiveType.LONG) {
	    return LONG;
	} else if (code == PrimitiveType.FLOAT) {
	    return FLOAT;
	} else if (code == PrimitiveType.DOUBLE) {
	    return DOUBLE;
	} else if (code == PrimitiveType.BOOLEAN) {
	    return BOOLEAN;
	} else if (code == PrimitiveType.VOID) {
	    return VOID;
	} else {
	    throw new TypeNotFound(code.toString());
	}
    }
}
