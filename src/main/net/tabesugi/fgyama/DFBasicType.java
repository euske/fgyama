//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFBasicType
//
public class DFBasicType extends DFType {

    private String _name;

    public DFBasicType(String name) {
        _name = name;
    }

    public DFBasicType(PrimitiveType.Code code) {
        _name = "@"+code.toString();
    }

    @Override
    public String toString() {
	return ("<DFBasicType("+this.getName()+")>");
    }

    public boolean equals(DFType type) {
        return ((type instanceof DFBasicType) &&
                _name.equals(((DFBasicType)type)._name));
    }

    public String getName()
    {
        return _name;
    }

    public int canConvertFrom(DFType type)
    {
        if (!(type instanceof DFBasicType)) return -1;
        DFBasicType btype = (DFBasicType)type;
        if (!_name.equals(btype._name)) return -1;
        return 0;
    }
}
