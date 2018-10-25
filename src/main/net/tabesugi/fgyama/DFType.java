//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFType
//
public abstract class DFType {

    public abstract String getTypeName();
    public abstract boolean equals(DFType type);
    public abstract int canConvertFrom(DFType type);

    public DFType parameterize(Map<DFParamType, DFType> typeMap) {
        return this;
    }
}
