//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFArrayType
//
public class DFArrayType extends DFType {

    private DFType _elemType;
    private int _ndims;

    // DFArrayType
    public DFArrayType(DFType elemType, int ndims) {
        assert(0 < ndims);
        _elemType = elemType;
        _ndims = ndims;
    }

    @Override
    public String toString() {
        return ("<DFArrayType("+this.getTypeName()+")>");
    }

    public String getTypeName()
    {
        String name = _elemType.getTypeName();
        for (int i = 0; i < _ndims; i++) {
            name += "[]";
        }
        return name;
    }

    public boolean equals(DFType type) {
        return ((type instanceof DFArrayType) &&
                _elemType.equals(((DFArrayType)type)._elemType) &&
                _ndims == ((DFArrayType)type)._ndims);
    }

    public int canConvertFrom(DFType type)
    {
        if (!(type instanceof DFArrayType)) return -1;
        DFArrayType atype = (DFArrayType)type;
        if (_ndims != atype._ndims) return -1;
        return _elemType.canConvertFrom(atype._elemType);
    }

    public DFType getElemType()
    {
        if (_ndims == 1) {
            return _elemType;
        } else {
            return new DFArrayType(_elemType, _ndims-1);
        }
    }
}
