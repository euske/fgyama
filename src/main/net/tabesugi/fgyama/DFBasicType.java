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

    public String getName()
    {
        return _name;
    }
}
