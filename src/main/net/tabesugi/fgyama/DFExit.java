//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFExit
//  Specifies a designated DFNode coming out of a certain DFFrame.
//
public class DFExit {

    private DFFrame _frame;
    private DFNode _node;
    private boolean _cont;

    public DFExit(DFFrame frame, DFNode node, boolean cont) {
        assert node.getRef() != null;
        _frame = frame;
        _node = node;
        _cont = cont;
    }

    public DFExit(DFFrame frame, DFNode node) {
        this(frame, node, false);
    }

    @Override
    public String toString() {
        return ("<DFExit("+_node.getRef()+") "+_frame+" <- "+_node+">");
    }

    public DFFrame getFrame() {
        return _frame;
    }

    public DFNode getNode() {
        return _node;
    }

    public void setNode(DFNode node) {
        _node = node;
    }

    public boolean isContinue() {
        return _cont;
    }
}
