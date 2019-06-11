//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFType
//
interface DFType {

    String getTypeName();
    boolean equals(DFType type);
    int canConvertFrom(DFType type, Map<DFMapType, DFType> typeMap);

    DFKlass toKlass();

}
