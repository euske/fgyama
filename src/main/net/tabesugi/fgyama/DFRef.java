//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFRef
//  Place to store a value.
//
public abstract class DFRef {

    private DFType _type;

    public DFRef(DFType type) {
        _type = type;
    }

    @Override
    public String toString() {
        if (_type == null) {
            return ("<DFRef "+this.getFullName()+" (?)>");
        } else {
            return ("<DFRef "+this.getFullName()+" ("+_type.toString()+")>");
        }
    }

    public DFType getRefType() {
        return _type;
    }

    public abstract DFVarScope getScope();

    public abstract String getFullName();
}
