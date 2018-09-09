//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFParamType
//
public class DFParamType extends DFType {

    private DFTypeSpace _typeSpace;
    private int _index;
    private String _name;
    private DFKlass[] _bases = null;

    public DFParamType(DFTypeSpace typeSpace, int index, String name) {
        assert typeSpace != null;
        _typeSpace = typeSpace;
        _index = index;
        _name = name;
    }

    @Override
    public String toString() {
        return ("<ParamType:"+_name+">");
    }

    public String getTypeName() {
        return "T"+_typeSpace.getFullName()+"/"+_name;
    }

    public boolean equals(DFType type) {
        return type == this;
    }

    public int canConvertFrom(DFType type) {
        if (type instanceof DFNullType) return 0;
        if (type instanceof DFKlass) {
            DFKlass klass = (DFKlass)type;
            if (_bases.length == 0) return 0;
            return klass.isSubclassOf(_bases[0]);
        } else if (type instanceof DFParamType) {
            DFParamType ptype = (DFParamType)type;
            if (_typeSpace != ptype._typeSpace) return -1;
            if (_bases.length == 0) return 0;
            if (ptype._bases.length == 0) return -1;
            // XXX check interfaces.
            return ptype._bases[0].isSubclassOf(_bases[0]);
        }
        return -1;
    }

    public int getIndex() {
        return _index;
    }

    public void setBases(DFKlass[] bases) {
        _bases = bases;
    }
}
