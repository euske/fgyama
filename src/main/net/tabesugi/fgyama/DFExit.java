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

    private DFFrame _frame;
    private DFNode _node;
    private boolean _cont;

    public DFExit(DFFrame frame, DFNode node) {
        this(frame, node, false);
    }

    public DFExit(DFFrame frame, DFNode node, boolean cont) {
        _frame = frame;
        _node = node;
        _cont = cont;
    }

    @Override
    public String toString() {
        return ("<DFExit: "+_frame+" <- "+_node.getRef()+">");
    }

    public DFFrame getFrame() {
        return _frame;
    }

    public DFNode getNode() {
        return _node;
    }

    public boolean isCont() {
        return _cont;
    }

    public DFExit wrap(DFNode node) {
        return new DFExit(_frame, node, _cont);
    }
}
