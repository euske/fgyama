//  Java2DF
//
package net.tabesugi.dfaaa;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFRef
//  Place to store a value.
//
public class DFRef {

    public DFScope scope;
    public String name;
    
    public static DFRef THIS = new DFRef(null, "THIS");
    public static DFRef SUPER = new DFRef(null, "SUPER");
    public static DFRef RETURN = new DFRef(null, "RETURN");
    public static DFRef EXCEPTION = new DFRef(null, "EXCEPTION");
    public static DFRef ARRAY = new DFRef(null, "[]");
    
    public DFRef(DFScope scope, String name) {
	this.scope = scope;
	this.name = name;
    }

    public String toString() {
	return ("<DFRef("+this.label()+")>");
    }

    public String label() {
	return ((this.scope == null)?
		this.name :
		this.scope.name+"."+this.name);
    }
}

