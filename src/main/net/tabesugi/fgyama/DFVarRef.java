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

    private DFVarSpace _space;
    private String _name;
    private DFType _type;

    public DFVarRef(DFVarSpace space, String name, DFType type) {
        assert(2 <= name.length());
        _space = space;
        _name = name;
        _type = type;
    }

    @Override
    public String toString() {
        if (_type == null) {
            return ("<DFVarRef("+this.getFullName()+")>");
        } else {
            return ("<DFVarRef("+this.getFullName()+": "+_type.toString()+">");
        }
    }

    @Override
    public int compareTo(DFVarRef ref) {
        if (ref == this) return 0;
        if (ref._space == _space) {
            return _name.compareTo(ref._name);
        }
        if (_space == null) return -1;
        return _space.compareTo(ref._space);
    }

    public String getFullName() {
        if (_space != null) {
            return (_name.substring(0,1)+_space.getFullName()+"/"+
                    _name.substring(1));
        } else {
            return _name;
        }
    }

    public DFType getType() {
        return _type;
    }

    public DFVarRef parameterize(DFType[] types) {
        if (_type instanceof DFParamType) {
            int index = ((DFParamType)_type).getIndex();
            return new DFVarRef(_space, _name, types[index]);
        }
        return this;
    }
}
