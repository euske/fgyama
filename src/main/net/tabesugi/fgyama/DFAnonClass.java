//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFAnonClass
//
public class DFAnonClass extends DFClass {

    public DFAnonClass(
	String name,
        DFTypeSpace typeSpace,
	DFVarScope parentScope) {
        super(name, typeSpace, typeSpace, parentScope);
    }

    public DFAnonClass(
	String name,
        DFTypeSpace typeSpace,
	DFVarScope parentScope,
        DFClass baseKlass) {
        super(name, typeSpace, typeSpace, parentScope, baseKlass);
    }

    @Override
    public String toString() {
        return ("<DFAnonClass("+this.getFullName()+")>");
    }

}
