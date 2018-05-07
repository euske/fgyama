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
    public static final DFType CHAR =
	new DFBasicType(PrimitiveType.CHAR);
    public static final DFType NULL =
	new DFBasicType("@null");
    public static final DFType NUMBER =
	new DFBasicType("@number");
    public static final DFType TYPE =
	new DFBasicType("@type");
    public static final DFType STRING =
	new DFBasicType(".String");
}
