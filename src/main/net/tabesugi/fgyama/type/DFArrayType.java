//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFArrayType
//
public class DFArrayType extends DFKlass {

    private static Map<String, DFArrayType> _types =
        new HashMap<String, DFArrayType>();

    public static DFArrayType getArray(DFType elemType, int ndims) {
        DFArrayType array = null;
        for (int i = 0; i < ndims; i++) {
            String key = elemType.getTypeName();
            array = _types.get(key);
            if (array == null) {
                array = new DFArrayType(elemType);
                _types.put(key, array);
            }
            elemType = array;
        }
        return array;
    }

    private DFType _elemType;

    // DFArrayType
    private DFArrayType(DFType elemType) {
        super(elemType.getTypeName(), null);
        _elemType = elemType;
    }

    @Override
    public String getTypeName() {
        return "["+_elemType.getTypeName();
    }

    @Override
    public boolean isInterface() {
        return false;
    }

    @Override
    public boolean isEnum() {
        return false;
    }

    @Override
    public DFKlass getBaseKlass() {
        return DFBuiltinTypes.getObjectKlass();
    }

    @Override
    public DFKlass[] getBaseIfaces() {
        return null;
    }

    @Override
    protected DFKlass parameterize(Map<String, DFKlass> paramTypes) {
        assert false;
        return null;
    }

    @Override
    public int canConvertFrom(DFType type, Map<DFMapType, DFKlass> typeMap)
        throws TypeIncompatible {
        if (type instanceof DFNullType) return 0;
        if (!(type instanceof DFArrayType)) throw new TypeIncompatible(this, type);
        DFArrayType atype = (DFArrayType)type;
        return _elemType.canConvertFrom(atype._elemType, typeMap);
    }

    public DFType getElemType() {
        return _elemType;
    }

    protected void build() {
        this.addField(DFBasicType.INT, "length", false);
    }
}
