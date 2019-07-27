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
public abstract class DFRef implements Comparable<DFRef> {

    private DFType _type;

    public DFRef(DFType type) {
        _type = type;
    }

    @Override
    public String toString() {
        if (_type == null) {
            return ("<DFRef("+this.getFullName()+")>");
        } else {
            return ("<DFRef("+this.getFullName()+": "+_type.toString()+">");
        }
    }

    @Override
    public int compareTo(DFRef ref) {
        if (ref == this) return 0;
        return this.getFullName().compareTo(ref.getFullName());
    }

    public boolean isLocal() {
        return false;
    }

    public boolean isInternal() {
        return false;
    }

    public DFType getRefType() {
        return _type;
    }

    public abstract String getFullName();
}
