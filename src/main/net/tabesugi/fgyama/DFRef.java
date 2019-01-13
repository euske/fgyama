//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFRef
//  Place to store a value.
//
public class DFRef implements Comparable<DFRef> {

    private DFVarScope _scope;
    private String _name;
    private DFType _type;

    public DFRef(DFVarScope scope, String name, DFType type) {
        assert 2 <= name.length();
        _scope = scope;
        _name = name;
        _type = type;
    }

    @Override
    public String toString() {
        if (_type == null) {
            return ("<DFRef("+this.getFullName()+")>");
        } else {
            return ("<DFRef("+this.getFullName()+": "+_type.toString()+">");
        }
    }

    @Override
    public int compareTo(DFRef ref) {
        if (ref == this) return 0;
        if (ref._scope == _scope) {
            return _name.compareTo(ref._name);
        }
        if (_scope == null) return -1;
        return _scope.compareTo(ref._scope);
    }

    public boolean isLocal() {
        return (_scope instanceof DFLocalVarScope);
    }

    public boolean isTemporary() {
        return (_scope == null);
    }

    public String getName() {
	return _name;
    }

    public String getFullName() {
        if (_scope != null) {
            return _scope.getFullName()+"/"+_name;
        } else {
            return _name;
        }
    }

    public DFType getRefType() {
        return _type;
    }

    public DFRef parameterize(Map<DFMapType, DFType> typeMap) {
        if (typeMap.containsKey(_type)) {
            return new DFRef(_scope, _name, typeMap.get(_type));
        }
        return this;
    }
}
