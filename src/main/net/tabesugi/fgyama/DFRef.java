//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFRef
//  Place to store a value.
//
public class DFRef implements Comparable<DFRef> {

    public DFScope scope;
    public String name;

    public DFRef(DFScope scope, String name) {
	this.scope = scope;
	this.name = name;
    }

    @Override
    public String toString() {
	return ("<DFRef("+this.getName()+")>");
    }

    @Override
    public int compareTo(DFRef ref) {
	return this.name.compareTo(ref.name);
    }

    public String getName() {
	return ((this.scope == null)?
		this.name :
		this.scope.name+":"+this.name);
    }
}
