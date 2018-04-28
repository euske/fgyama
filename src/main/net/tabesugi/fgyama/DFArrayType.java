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

    public String getName()
    {
        return _elemType.getName()+"[]";
    }

    public DFType getElemType()
    {
        return _elemType;
    }
}
