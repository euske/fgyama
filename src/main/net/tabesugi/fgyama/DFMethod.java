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

    private DFClassType _klass;
    private String _name;
    private DFTypeRef _returnType;

    public DFMethod(DFClassType klass, String name, DFTypeRef returnType) {
	_klass = klass;
	_name = name;
	_returnType = returnType;
    }

    @Override
    public String toString() {
        return ("<DFMethod: "+_name+">");
    }

    @Override
    public int compareTo(DFMethod method) {
	return _name.compareTo(method._name);
    }

    public String getName() {
	return _klass.getName()+"/"+_name;
    }

    public DFTypeRef getReturnType() {
	return _returnType;
    }
}
