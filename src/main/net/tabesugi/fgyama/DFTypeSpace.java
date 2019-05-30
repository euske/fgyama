//  Java2DF
//
package net.tabesugi.fgyama;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFTypeSpace
//
interface DFTypeSpace {

    DFTypeSpace lookupSpace(SimpleName name);
    DFTypeSpace lookupSpace(String id);

    DFKlass getKlass(Name name) throws TypeNotFound;
    DFKlass getKlass(String id) throws TypeNotFound;

}
