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

    public DFAnonClassSpace(DFTypeSpace typeSpace, String id, DFClassSpace baseKlass) {
        super(typeSpace, id, baseKlass);
    }

    @Override
    public String toString() {
	return ("<DFAnonClassSpace("+this.getName()+")>");
    }

}
