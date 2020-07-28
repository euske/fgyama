//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;


//  DFType
//
interface DFType {

    String getTypeName();
    boolean equals(DFType type);
    int canConvertFrom(DFType type, Map<DFMapType, DFKlass> typeMap)
        throws TypeIncompatible;

    DFKlass toKlass();

}
