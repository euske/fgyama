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
    private boolean _cont;

    public DFExit(DFNode node) {
        this(node, false);
    }

    public DFExit(DFNode node, boolean cont) {
        _node = node;
        _cont = cont;
    }

    @Override
    public String toString() {
        return ("<DFExit: "+_node+">");
    }

    public DFNode getNode() {
        return _node;
    }

    public boolean isCont() {
        return _cont;
    }

    public DFExit wrap(DFNode node) {
        return new DFExit(node, _cont);
    }
}
