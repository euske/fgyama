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

    public static DFParamType[] createParamTypes(
        DFTypeSpace typeSpace, List<TypeParameter> tps) {
        DFParamType[] pts = new DFParamType[tps.size()];
        for (int i = 0; i < tps.size(); i++) {
            String id = tps.get(i).getName().getIdentifier();
            DFParamType pt = new DFParamType(typeSpace, i, id);
            pts[i] = pt;
        }
        return pts;
    }

    private DFTypeSpace _typeSpace;
    private int _index;
    private String _name;
    private DFClassSpace[] _bases = null;

    public DFParamType(DFTypeSpace typeSpace, int index, String name) {
        assert(typeSpace != null);
        _typeSpace = typeSpace;
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

    public String getBaseName() {
        return _name;
    }

    public String getName() {
        return "T"+_typeSpace.getFullName()+"/"+_name;
    }

    public int canConvertFrom(DFType type) {
        if (type instanceof DFNullType) return 0;
        if (type instanceof DFClassType) {
            DFClassType ctype = (DFClassType)type;
            if (_bases.length == 0) return 0;
            return ctype.getKlass().isSubclassOf(_bases[0]);
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

    public void setBases(DFClassSpace[] bases) {
        _bases = bases;
    }
}
