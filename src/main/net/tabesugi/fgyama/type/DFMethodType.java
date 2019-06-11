//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFMethodType
//
public class DFMethodType implements DFType {

    private DFType[] _argTypes;
    private DFType _returnType;

    public DFMethodType(DFType[] argTypes, DFType returnType) {
        assert returnType != null;
        _argTypes = argTypes;
        _returnType = returnType;
    }

    @Override
    public String toString() {
        return ("<DFMethodType("+this.getTypeName()+")>");
    }

    public boolean equals(DFType type) {
        if (!(type instanceof DFMethodType)) return false;
        DFMethodType mtype = (DFMethodType)type;
        if (_returnType != null && !_returnType.equals(mtype._returnType)) return false;
        if (_argTypes.length != mtype._argTypes.length) return false;
        for (int i = 0; i < _argTypes.length; i++) {
            if (!_argTypes[i].equals(mtype._argTypes[i])) return false;
        }
        return true;
    }

    public DFKlass toKlass() {
        return null;
    }

    public int canConvertFrom(DFType type, Map<DFMapType, DFType> typeMap) {
        if (!(type instanceof DFMethodType)) return -1;
        DFMethodType mtype = (DFMethodType)type;
        int dist = this.canAccept(mtype._argTypes, typeMap);
	if (dist < 0) return -1;
        if (_returnType != null && mtype._returnType != null) {
            int d = _returnType.canConvertFrom(mtype._returnType, typeMap);
            if (d < 0) return -1;
	    dist += d;
        }
        return dist;
    }

    public int canAccept(DFType[] argTypes, Map<DFMapType, DFType> typeMap) {
        if (_argTypes == null || argTypes == null) return 0;
        if (_argTypes.length != argTypes.length) return -1;
        int dist = 0;
        for (int i = 0; i < _argTypes.length; i++) {
            DFType typeRecv = _argTypes[i];
            DFType typePassed = argTypes[i];
            if (typeRecv == null || typePassed == null) continue;
            int d = typeRecv.canConvertFrom(typePassed, typeMap);
            if (d < 0) return -1;
            dist += d;
        }
        return dist;
    }

    public DFType[] getArgTypes() {
        return _argTypes;
    }

    public DFType getReturnType() {
        return _returnType;
    }

    public String getTypeName() {
        StringBuilder b = new StringBuilder();
        b.append("(");
        for (DFType type : _argTypes) {
            if (type == null) {
                b.append("?");
            } else {
                b.append(type.getTypeName());
            }
        }
        b.append(")");
        if (_returnType == null) {
            b.append("?");
        } else {
            b.append(_returnType.getTypeName());
        }
        return b.toString();
    }
}
