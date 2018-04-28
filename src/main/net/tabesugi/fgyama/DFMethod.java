//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFMethod
//
public class DFMethod implements Comparable<DFMethod> {

    private DFClassSpace _klass;
    private String _name;
    private DFType _returnType;

    public DFMethod(DFClassSpace klass, String name, DFType returnType) {
	_klass = klass;
	_name = name;
	_returnType = returnType;
    }

    @Override
    public String toString() {
	if (_returnType == null) {
	    return ("<DFMethod("+this.getName()+" -> ?)>");
	} else {
	    return ("<DFMethod("+this.getName()+" -> "+_returnType.toString()+">");
	}
    }

    @Override
    public int compareTo(DFMethod method) {
	return _name.compareTo(method._name);
    }

    public String getName() {
        if (_klass != null) {
            return (_klass.getName()+"/"+_name);
        } else {
            return ("!"+_name);
        }
    }

    public DFType getReturnType() {
	return _returnType;
    }
}
