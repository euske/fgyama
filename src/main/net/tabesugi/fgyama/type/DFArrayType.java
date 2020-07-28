//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFArrayType
//
public class DFArrayType implements DFType {

    private static Map<String, DFArrayType> _types =
        new HashMap<String, DFArrayType>();

    public static DFArrayType getType(DFType elemType, int ndims) {
        Logger.info("DFArrayType:", elemType, ndims);
        String key = elemType.getTypeName()+":"+ndims;
        DFArrayType type = _types.get(key);
        if (type == null) {
            type = new DFArrayType(elemType, ndims);
            _types.put(key, type);
        }
        return type;
    }

    private DFType _elemType;
    private int _ndims;

    // DFArrayType
    private DFArrayType(DFType elemType, int ndims) {
        assert 0 < ndims;
        _elemType = elemType;
        _ndims = ndims;
    }

    @Override
    public String toString() {
        return ("<DFArrayType("+this.getTypeName()+")>");
    }

    @Override
    public String getTypeName() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < _ndims; i++) {
            b.append("[");
        }
        return b.toString()+_elemType.getTypeName();
    }

    @Override
    public boolean equals(DFType type) {
        return ((type instanceof DFArrayType) &&
                _elemType.equals(((DFArrayType)type)._elemType) &&
                _ndims == ((DFArrayType)type)._ndims);
    }

    @Override
    public DFKlass toKlass() {
        return DFBuiltinTypes.getArrayKlass();
    }

    @Override
    public int canConvertFrom(DFType type, Map<DFMapType, DFType> typeMap)
        throws TypeIncompatible {
        if (type instanceof DFNullType) return 0;
        if (!(type instanceof DFArrayType)) throw new TypeIncompatible(this, type);
        DFArrayType atype = (DFArrayType)type;
        if (_ndims != atype._ndims) throw new TypeIncompatible(this, type);
        return _elemType.canConvertFrom(atype._elemType, typeMap);
    }

    public DFType getElemType() {
        if (_ndims == 1) {
            return _elemType;
        } else {
            return DFArrayType.getType(_elemType, _ndims-1);
        }
    }
}
