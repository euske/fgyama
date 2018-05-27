//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFType
//
public abstract class DFType {

    public abstract boolean equals(DFType type);
    public abstract String getName();
    public abstract int canConvertFrom(DFType type);

    @Override
    public String toString() {
	return ("<DFType("+this.getName()+")>");
    }

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
    public static final DFType NULL =
	new DFBasicType("@null");
    public static final DFType NUMBER =
	new DFBasicType("@number");
    public static final DFType TYPE =
	new DFBasicType("@type");
    public static final DFType ANONYMOUS =
	new DFBasicType("@anonymous");
    public static final DFType STRING =
	new DFBasicType(".String");
}
