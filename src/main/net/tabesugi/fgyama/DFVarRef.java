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
        return _name.compareTo(ref._name);
    }

    public String getFullName() {
        if (_space != null) {
            return (_space.getFullName()+"/"+_name);
        } else {
            return ("!"+_name);
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
