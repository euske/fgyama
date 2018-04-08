//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFVar
//  Variable.
//
public class DFVar extends DFRef {

    public DFVar(DFScope scope, String name, DFType type) {
	super(scope, name, type);
    }
}
