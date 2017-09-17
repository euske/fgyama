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
    
    public DFNode src;
    public DFNode dst;
    public int lid;
    public DFLinkType type;
    public String label;
    
    public DFLink(DFNode src, DFNode dst, int lid, DFLinkType type, String label)
    {
	this.src = src;
	this.dst = dst;
	this.lid = lid;
	this.type = type;
	this.label = label;
    }

    public String toString() {
	return ("<DFLink: "+this.src+"-("+this.label+")-"+this.dst+">");
    }

    public void disconnect()
    {
	this.src.send.remove(this);
	this.dst.recv.remove(this);
    }
}

