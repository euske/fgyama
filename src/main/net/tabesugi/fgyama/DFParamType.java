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

    private DFClassSpace _klass;
    private int _index;
    private String _name;
    private DFClassSpace[] _bases = null;

    public DFParamType(DFClassSpace klass, int index, String name) {
        _klass = klass;
        _index = index;
        _name = name;
    }

    @Override
    public String toString() {
        return ("<ParamType:"+_name+">");
    }

    public boolean equals(DFType type) {
        return type == this;
    }

    public String getName() {
        return "T"+_klass.getFullName()+"/"+_name;
    }

    public int canConvertFrom(DFType type) {
        if (type instanceof DFNullType) return 0;
        if (type instanceof DFClassType) {
            DFClassType ctype = (DFClassType)type;
            if (_bases.length == 0) return 0;
            return _bases[0].isBaseOf(ctype.getKlass());
        } else if (type instanceof DFParamType) {
            DFParamType ptype = (DFParamType)type;
            if (_bases.length == 0) return 0;
            if (ptype._bases.length == 0) return -1;
            // XXX check interfaces.
            return _bases[0].isBaseOf(ptype._bases[0]);
        }
        return -1;
    }

    public int getIndex() {
        return _index;
    }

    public void setBases(DFClassSpace[] bases) {
        _bases = bases;
    }
}
