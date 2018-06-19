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
	return ("<DFBasicType("+this.getName()+")>");
    }

    public boolean equals(DFType type) {
        return ((type instanceof DFBasicType) &&
                _name.equals(((DFBasicType)type)._name));
    }

    public String getName() {
        return _name;
    }

    public int canConvertFrom(DFType type) {
        if (!(type instanceof DFBasicType)) return -1;
        if (this.equals(type)) return 0;
        return (_numeric && ((DFBasicType)type)._numeric)? 1 : -1;
    }

    public static final DFType NUMBER =
	new DFBasicType("@number");
    public static final DFType BOOLEAN =
	new DFBasicType(PrimitiveType.BOOLEAN);
    public static final DFType BYTE =
	new DFBasicType(PrimitiveType.BYTE);
    public static final DFType CHAR =
	new DFBasicType(PrimitiveType.CHAR);
    public static final DFType DOUBLE =
	new DFBasicType(PrimitiveType.DOUBLE);
    public static final DFType FLOAT =
	new DFBasicType(PrimitiveType.FLOAT);
    public static final DFType INT =
	new DFBasicType(PrimitiveType.INT);
    public static final DFType LONG =
	new DFBasicType(PrimitiveType.LONG);
    public static final DFType SHORT =
	new DFBasicType(PrimitiveType.SHORT);
    public static final DFType VOID =
	new DFBasicType(PrimitiveType.VOID);
    public static final DFType TYPE =
	new DFBasicType("@type");
    public static final DFType ANONYMOUS =
	new DFBasicType("@anonymous");
}
