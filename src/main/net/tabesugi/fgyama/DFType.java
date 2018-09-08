//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFType
//
public interface DFType {

    String getTypeName();
    boolean equals(DFType type);
    int canConvertFrom(DFType type);

}
