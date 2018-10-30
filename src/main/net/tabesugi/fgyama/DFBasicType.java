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

    private DFBasicType(PrimitiveType.Code code) {
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
