//  Java2DF
//
package net.tabesugi.dfaaa;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFLink
//
class DFLink implements Comparable<DFLink> {
    
    public DFNode src;
    public DFNode dst;
    public int deg;
    public DFLinkType type;
    public String label;
    
    public DFLink(DFNode src, DFNode dst, int deg)
    {
	this(src, dst, deg, DFLinkType.DataFlow);
    }
    
    public DFLink(DFNode src, DFNode dst, int deg, DFLinkType type)
    {
	this(src, dst, deg, type, null);
    }
    
    public DFLink(DFNode src, DFNode dst, int deg, DFLinkType type, String label)
    {
	this.src = src;
	this.dst = dst;
	this.deg = deg;
	this.type = type;
	this.label = label;
    }

    @Override
    public String toString() {
	return ("<DFLink#"+this.deg+": "+this.src+"-("+this.label+")-"+this.dst+">");
    }

    @Override
    public int compareTo(DFLink link) {
	return this.dst.compareTo(link.dst);
    }
}

