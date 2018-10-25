//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFMethodType
//
public class DFMethodType extends DFType {

    private DFType[] _argTypes;
    private DFType _returnType;

    public DFMethodType(DFType[] argTypes, DFType returnType) {
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

    public int canConvertFrom(DFType type) {
        if (!(type instanceof DFMethodType)) return -1;
        DFMethodType mtype = (DFMethodType)type;
        int dist = 0;
        if (_returnType != null && mtype._returnType != null) {
            dist = _returnType.canConvertFrom(mtype._returnType);
            if (dist < 0) return -1;
        }
        return dist + this.canAccept(mtype._argTypes);
    }

    public int canAccept(DFType[] argTypes) {
        if (_argTypes == null || argTypes == null) return 0;
        if (_argTypes.length != argTypes.length) return -1;
        int dist = 0;
        for (int i = 0; i < _argTypes.length; i++) {
            DFType type0 = _argTypes[i];
            DFType type1 = argTypes[i];
            if (type0 == null || type1 == null) continue;
            int d = type0.canConvertFrom(type1);
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

    public DFMethodType parameterize(Map<DFParamType, DFType> typeMap) {
        DFType returnType = _returnType.parameterize(typeMap);
        boolean changed = (_returnType != returnType);
        DFType[] argTypes = new DFType[_argTypes.length];
        for (int i = 0; i < _argTypes.length; i++) {
            argTypes[i] = _argTypes[i].parameterize(typeMap);
            changed = changed || (_argTypes[i] != argTypes[i]);
        }
        if (changed) {
            return new DFMethodType(argTypes, returnType);
        }
        return this;
    }
}
