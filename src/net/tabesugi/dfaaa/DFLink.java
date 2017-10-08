//  Java2DF
//
package net.tabesugi.dfaaa;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFLink
//
class DFLink {
    
    public DFNode dst;
    public DFNode src;
    public String label;
    
    public DFLink(DFNode dst, DFNode src, String label)
    {
	this.dst = dst;
	this.src = src;
	this.label = label;
    }

    @Override
    public String toString() {
	return ("<DFLink "+this.dst+"<-"+this.src+">");
    }
}
