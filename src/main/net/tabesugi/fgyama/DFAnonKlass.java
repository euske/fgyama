//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFAnonKlass
//
public class DFAnonKlass extends DFKlass {

    public DFAnonKlass(
	String name,
        DFTypeSpace typeSpace,
	DFKlass parentKlass,
	DFVarScope parentScope,
        DFKlass baseKlass) {
        super(name, typeSpace, parentKlass, parentScope, baseKlass);
    }

    @Override
    public String toString() {
        return ("<DFAnonKlass("+this.getFullName()+")>");
    }

}
