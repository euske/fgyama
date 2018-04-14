//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFVarRef
//  Place to store a value.
//
public class DFVarRef implements Comparable<DFVarRef> {

    private DFVarScope _scope;
    private String _name;
    private DFTypeRef _type;

    public DFVarRef(DFVarScope scope, String name, DFTypeRef type) {
	_scope = scope;
	_name = name;
	_type = type;
    }

    @Override
    public String toString() {
	if (_type == null) {
	    return ("<DFVarRef("+this.getName()+")>");
	} else {
	    return ("<DFVarRef("+this.getName()+": "+_type.toString()+">");
	}
    }

    @Override
    public int compareTo(DFVarRef ref) {
	return _name.compareTo(ref._name);
    }

    public String getName() {
	return ((_scope == null)?
		_name :
		_scope.getName()+"/"+_name);
    }

    public DFTypeRef getType() {
	return _type;
    }
}
