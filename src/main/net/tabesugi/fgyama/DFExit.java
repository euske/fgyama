//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFExit
//
public class DFExit {

    private DFNode _node;
    private DFFrame _frame;
    private boolean _cont;

    public DFExit(DFNode node, DFFrame frame) {
	this(node, frame, false);
    }

    public DFExit(DFNode node, DFFrame frame, boolean cont) {
	_node = node;
	_frame = frame;
	_cont = cont;
    }

    public String toString() {
	return ("<DFExit: "+_node+" -> "+_frame+">");
    }

    public DFNode getNode() {
        return _node;
    }

    public DFFrame getFrame() {
        return _frame;
    }

    public boolean isCont() {
        return _cont;
    }

    public DFExit wrap(DFNode node) {
	return new DFExit(node, _frame, _cont);
    }
}
