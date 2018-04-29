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
    private DFType[] _argTypes;
    private DFType _returnType;

    public DFMethod(
        DFClassSpace klass, String name,
        DFType[] argTypes, DFType returnType) {
	_klass = klass;
	_name = name;
        _argTypes = argTypes;
	_returnType = returnType;
    }

    @Override
    public String toString() {
	if (_returnType == null) {
	    return ("<DFMethod("+this.getName()+" -> ?)>");
	} else {
	    return ("<DFMethod("+this.getName()+" -> "+_returnType.getName()+">");
	}
    }

    @Override
    public int compareTo(DFMethod method) {
	return _name.compareTo(method._name);
    }

    public String getName() {
        String name;
        if (_klass != null) {
            name = (_klass.getName()+"/"+_name);
        } else {
            name = ("!"+_name);
        }
        if (_argTypes == null) {
            name += "?";
        } else {
            for (DFType type : _argTypes) {
                name += ":"+type.getName();
            }
        }
        return name;
    }

    public DFType getReturnType() {
	return _returnType;
    }

    public int canAccept(String name, DFType[] argTypes) {
        if (!_name.equals(name)) return -1;
        if (_argTypes == null || argTypes == null) return 0;
        if (_argTypes.length != argTypes.length) return -1;
        int dist = 0;
        for (int i = 0; i < _argTypes.length; i++) {
            DFType type0 = _argTypes[i];
            DFType type1 = argTypes[i];
            if (type0 == null || type1 == null) continue;
            int d = type0.canConvertFrom(type1);
            if (d < 0) return -1;
            dist += d;
        }
        return dist;
    }
}
