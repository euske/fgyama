//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFFuncType
//
public class DFFuncType implements DFType {

    private DFType[] _argTypes;
    private DFType _returnType;
    private boolean _varargs;
    private DFKlass[] _exceptions = new DFKlass[] {};

    public DFFuncType(DFType[] argTypes, DFType returnType) {
        assert returnType != null;
        _argTypes = argTypes;
        _returnType = returnType;
    }

    @Override
    public String toString() {
        return ("<DFFuncType("+this.getTypeName()+")>");
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
        if (!(type instanceof DFFuncType)) return false;
        DFFuncType mtype = (DFFuncType)type;
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
    public int canConvertFrom(DFType type, Map<DFMapType, DFType> typeMap)
        throws TypeIncompatible {
        if (!(type instanceof DFFuncType)) throw new TypeIncompatible(this, type);
        DFFuncType mtype = (DFFuncType)type;
        int dist = this.canAccept(mtype._argTypes, typeMap);
        if (_returnType != null && mtype._returnType != null) {
            dist += _returnType.canConvertFrom(mtype._returnType, typeMap);
        }
        return dist;
    }

    public int canAccept(DFType[] argTypes, Map<DFMapType, DFType> typeMap)
        throws TypeIncompatible {
        // Always accept if the signature is unknown.
        if (_argTypes == null || argTypes == null) return 0;
        if (_varargs) {
            // For varargs methods, the minimum number of arguments is required.
            if (argTypes.length < _argTypes.length-1) throw new TypeIncompatible(this, null);
        } else {
            // For fixed-args methods, the exact number of arguments is required.
            if (argTypes.length != _argTypes.length) throw new TypeIncompatible(this, null);
        }
        int dist = 0;
        for (int i = 0; i < argTypes.length; i++) {
            DFType typePassed = argTypes[i];
            DFType typeRecv = this.getArgType(i);
            if (typeRecv == null || typePassed == null) continue;
            dist += typeRecv.canConvertFrom(typePassed, typeMap);
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
