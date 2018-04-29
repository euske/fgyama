//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFClassType
//
public class DFClassType extends DFType {

    private DFClassSpace _klass;

    public DFClassType(DFClassSpace klass) {
        _klass = klass;
    }

    // DFArrayType
    public DFClassType(DFClassSpace klass, int ndims) {
        _klass = klass;
    }

    // DFCompoundType
    public DFClassType(DFClassSpace klass, DFType[] types) {
        _klass = klass;
    }

    public String getName()
    {
        return _klass.getName();
    }

    public int canConvertFrom(DFType type)
    {
        if (!(type instanceof DFClassType)) return -1;
        DFClassType ctype = (DFClassType)type;
        return _klass.isBaseOf(ctype._klass);
    }

    public DFClassSpace getKlass()
    {
        return _klass;
    }
}
