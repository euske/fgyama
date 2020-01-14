//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFFunctionType
//
public class DFFunctionType implements DFType {

    private DFType[] _argTypes;
    private DFType _returnType;
    private boolean _varargs;
    private DFKlass[] _exceptions = new DFKlass[] {};

    public DFFunctionType(DFType[] argTypes, DFType returnType) {
        assert returnType != null;
        _argTypes = argTypes;
        _returnType = returnType;
    }

    @Override
    public String toString() {
        return ("<DFFunctionType("+this.getTypeName()+")>");
    }

    @Override
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

    @Override
    public boolean equals(DFType type) {
        if (!(type instanceof DFFunctionType)) return false;
        DFFunctionType mtype = (DFFunctionType)type;
        if (_returnType != null && !_returnType.equals(mtype._returnType)) return false;
        // Should we check if it's varargs??
        if (_argTypes.length != mtype._argTypes.length) return false;
        for (int i = 0; i < _argTypes.length; i++) {
            if (!_argTypes[i].equals(mtype._argTypes[i])) return false;
        }
        if (_exceptions.length != mtype._exceptions.length) return false;
        for (int i = 0; i < _exceptions.length; i++) {
            if (!_exceptions[i].equals(mtype._exceptions[i])) return false;
        }
        return true;
    }

    @Override
    public DFKlass toKlass() {
        return null;
    }

    @Override
    public int canConvertFrom(DFType type, Map<DFMapType, DFKlass> typeMap) {
        if (!(type instanceof DFFunctionType)) return -1;
        DFFunctionType mtype = (DFFunctionType)type;
        int dist = this.canAccept(mtype._argTypes, typeMap);
	if (dist < 0) return -1;
        if (_returnType != null && mtype._returnType != null) {
            int d = _returnType.canConvertFrom(mtype._returnType, typeMap);
            if (d < 0) return -1;
	    dist += d;
        }
        return dist;
    }

    public int canAccept(DFType[] argTypes, Map<DFMapType, DFKlass> typeMap) {
        // Always accept if the signature is unknown.
        if (_argTypes == null || argTypes == null) return 0;
        if (_varargs) {
            // For varargs methods, the minimum number of arguments is required.
            if (argTypes.length < _argTypes.length-1) return -1;
        } else {
            // For fixed-args methods, the exact number of arguments is required.
            if (argTypes.length != _argTypes.length) return -1;
        }
        int dist = 0;
        for (int i = 0; i < argTypes.length; i++) {
            DFType typePassed = argTypes[i];
            DFType typeRecv = this.getArgType(i);
            if (typeRecv == null || typePassed == null) continue;
            int d = typeRecv.canConvertFrom(typePassed, typeMap);
            if (d < 0) return -1;
            dist += d;
        }
        return dist;
    }

    public DFType getArgType(int i) {
        if (isVarArg(i)) {
            // For varargs methods, the last argument is declared as an array.
            DFType type = _argTypes[_argTypes.length - 1];
            assert type instanceof DFArrayType;
            return ((DFArrayType)type).getElemType();
        }
        return _argTypes[i];
    }

    public DFType[] getRealArgTypes() {
        return _argTypes;
    }

    public void setVarArgs(boolean varargs) {
        _varargs = varargs;
    }

    public boolean isVarArg(int i) {
        return (_varargs && (_argTypes.length-1) <= i);
    }

    public DFType getReturnType() {
        return _returnType;
    }

    public void setExceptions(DFKlass[] exceptions) {
        _exceptions = exceptions;
    }

    public DFKlass[] getExceptions() {
        return _exceptions;
    }
}
