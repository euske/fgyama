//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFType
//
public class DFType {

    private String _name;

    public DFType(String name) {
	_name = name;
    }

    public DFType(Type type) {
	_name = Utils.getTypeName(type);
    }

    public DFType(Name name) {
	_name = "."+name.getFullyQualifiedName();
    }

    public DFType(PrimitiveType.Code code) {
	_name = "@"+code.toString();
    }

    @Override
    public String toString() {
	return ("<"+_name+">");
    }

    public String getName() {
	return _name;
    }

    public static DFType BOOLEAN =
	new DFType(PrimitiveType.BOOLEAN);
    public static DFType CHAR =
	new DFType(PrimitiveType.CHAR);
    public static DFType NULL =
	new DFType("NULL");
    public static DFType NUMBER =
	new DFType("number");
    public static DFType STRING =
	new DFType("String");
    public static DFType TYPE =
	new DFType("Type");
}
