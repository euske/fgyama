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
        _elemType = elemType;
        _ndims = ndims;
    }

    @Override
    public String toString() {
        return ("<DFArrayType("+this.getName()+")>");
    }

    public boolean equals(DFType type) {
        return ((type instanceof DFArrayType) &&
                _elemType.equals(((DFArrayType)type)._elemType) &&
                _ndims == ((DFArrayType)type)._ndims);
    }

    public String getName()
    {
        return _elemType.getName()+"[]";
    }

    public int canConvertFrom(DFType type)
    {
        if (!(type instanceof DFArrayType)) return -1;
        DFArrayType atype = (DFArrayType)type;
        if (_ndims != atype._ndims) return -1;
        // The element type should be exact match.
        if (_elemType.canConvertFrom(atype._elemType) != 0) return -1;
        return 0;
    }

    public DFType getElemType()
    {
        return _elemType;
    }
}
