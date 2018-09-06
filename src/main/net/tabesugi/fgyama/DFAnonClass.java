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
	DFTypeSpace typeSpace,
	DFVarSpace parent, String id, DFClass baseKlass) {
        super(typeSpace, typeSpace, parent, id, baseKlass);
    }

    @Override
    public String toString() {
        return ("<DFAnonClass("+this.getFullName()+")>");
    }

}
