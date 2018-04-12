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

    public DFTypeRef(String name) {
	_name = name;
    }

    public DFTypeRef(Type type) {
	_name = Utils.getTypeName(type);
    }

    public DFTypeRef(Name name) {
	_name = "."+name.getFullyQualifiedName();
    }

    public DFTypeRef(PrimitiveType.Code code) {
	_name = "@"+code.toString();
    }

    @Override
    public String toString() {
	return ("<"+_name+">");
    }

    public String getName() {
	return _name;
    }

    public static DFTypeRef BOOLEAN =
	new DFTypeRef(PrimitiveType.BOOLEAN);
    public static DFTypeRef CHAR =
	new DFTypeRef(PrimitiveType.CHAR);
    public static DFTypeRef NULL =
	new DFTypeRef("NULL");
    public static DFTypeRef NUMBER =
	new DFTypeRef("number");
    public static DFTypeRef STRING =
	new DFTypeRef("String");
    public static DFTypeRef TYPE =
	new DFTypeRef("Type");
}
