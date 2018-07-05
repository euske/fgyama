//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFAnonClassSpace
//
public class DFAnonClassSpace extends DFClassSpace {

    public DFAnonClassSpace(
	DFTypeSpace typeSpace,
	DFVarSpace parent, String id, DFClassSpace baseKlass) {
        super(typeSpace, typeSpace, parent, id, baseKlass);
    }

    @Override
    public String toString() {
        return ("<DFAnonClassSpace("+this.getFullName()+")>");
    }

}
