//  Java2DF
//
package net.tabesugi.dfaaa;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFLabel
//
public class DFLabel {

    public String name;

    public DFLabel(String name) {
	this.name = name;
    }

    public String toString() {
	return this.name+":";
    }
    
    public static DFLabel BREAK = new DFLabel("BREAK");
    public static DFLabel CONTINUE = new DFLabel("CONTINUE");
}

