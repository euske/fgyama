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
public abstract class DFExit implements Comparable<DFExit> {

    private DFFrame _frame;
    private DFNode _node;

    public DFExit(DFFrame frame, DFNode node) {
        assert node.getRef() != null;
        _frame = frame;
        _node = node;
    }

    @Override
    public String toString() {
        return ("<DFExit("+_node.getRef()+") "+_frame+" <- "+_node+">");
    }

    @Override
    public int compareTo(DFExit exit) {
        return _node.compareTo(exit._node);
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
}
