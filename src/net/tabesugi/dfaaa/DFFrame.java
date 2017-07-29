//  Java2DF
//
package net.tabesugi.dfaaa;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFFrame
//
public class DFFrame {

    public DFFrame parent;
    public Collection<DFRef> loopRefs;
    
    public DFFrame(DFFrame parent, Collection<DFRef> loopRefs) {
	this.parent = parent;
	this.loopRefs = loopRefs;
    }
}

