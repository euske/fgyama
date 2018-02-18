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

    private Type _type;

    public DFVar(DFScope scope, String name, Type type) {
	super(scope, name);
	_type = type;
    }

    @Override
    public String toString() {
	return ("<DFVar("+this.getName()+"): "+_type+">");
    }
}
