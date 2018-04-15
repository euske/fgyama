//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFTypeRef
//
public class DFTypeRef {

    private String _name;

    private DFTypeRef(String name) {
	_name = name;
    }

    public DFTypeRef(PrimitiveType.Code code) {
	_name = getTypeName(code);
    }

    public DFTypeRef(Name name) {
	_name = getTypeName(name);
    }

    public DFTypeRef(Type type) {
	_name = getTypeName(type);
    }

    @Override
    public String toString() {
	return ("<"+_name+">");
    }

    public String getName() {
	return _name;
    }

    public static String getTypeName(PrimitiveType.Code code) {
        return ("@"+code.toString());
    }
    public static String getTypeName(Name name) {
        return ("."+name.getFullyQualifiedName());
    }
    public static String getTypeName(Type type) {
	if (type instanceof PrimitiveType) {
            PrimitiveType ptype = (PrimitiveType)type;
            return getTypeName(ptype.getPrimitiveTypeCode());
	} else if (type instanceof SimpleType) {
            SimpleType stype = (SimpleType)type;
            return getTypeName(stype.getName());
	} else if (type instanceof ArrayType) {
            ArrayType atype = (ArrayType)type;
	    String name = getTypeName(atype.getElementType());
	    int ndims = atype.getDimensions();
	    for (int i = 0; i < ndims; i++) {
		name += "[]";
	    }
	    return name;
	} else if (type instanceof ParameterizedType) {
            ParameterizedType ptype = (ParameterizedType)type;
            // ignore ptype.typeArguments()
	    return getTypeName(ptype.getType());
	} else {
	    return null;
	}
    }

    public static DFTypeRef BOOLEAN =
	new DFTypeRef(PrimitiveType.BOOLEAN);
    public static DFTypeRef CHAR =
	new DFTypeRef(PrimitiveType.CHAR);
    public static DFTypeRef NULL =
	new DFTypeRef("@null");
    public static DFTypeRef NUMBER =
	new DFTypeRef("@number");
    public static DFTypeRef TYPE =
	new DFTypeRef("@type");
    public static DFTypeRef STRING =
	new DFTypeRef(".String");
}
