//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;
import org.w3c.dom.*;


//  DFGraph
//
public class DFGraph {

    private DFVarScope _root;
    private DFFrame _frame;
    private DFMethod _method;

    private Map<Integer, DFNode> _nodes =
        new HashMap<Integer, DFNode>();

    // DFGraph for a method.
    public DFGraph(DFVarScope root, DFFrame frame, DFMethod method) {
        _root = root;
        _frame = frame;
        _method = method;
    }

    // DFGraph for a class static block.
    public DFGraph(DFVarScope root, DFFrame frame) {
        this(root, frame, null);
    }

    @Override
    public String toString() {
        return ("<DFGraph("+_root.getFullName()+")>");
    }

    public Element toXML(Document document) {
        Element elem = document.createElement("graph");
        if (_method != null) {
            elem.setAttribute("name", _method.getSignature());
        } else {
            elem.setAttribute("name", _root.getFullName());
        }
        elem.setAttribute("ins", list2str(_frame.getInputs()));
        elem.setAttribute("outs", list2str(_frame.getOutputs()));
        DFNode[] nodes = new DFNode[_nodes.size()];
        _nodes.values().toArray(nodes);
        Arrays.sort(nodes);
        elem.appendChild(_root.toXML(document, nodes));
        return elem;
    }

    private static String list2str(DFVarRef[] refs) {
        int n = 0;
        String data = "";
        for (DFVarRef ref : refs) {
            if (0 < n) {
                data += " ";
            }
            data += ref.getRefName();
            n++;
        }
        return data;
    }

    public int addNode(DFNode node) {
        int id = _nodes.size()+1;
        _nodes.put(id, node);
        return id;
    }

    public void cleanup() {
        ArrayList<Integer> removed = new ArrayList<Integer>();
        for (DFNode node : _nodes.values()) {
            if (node.getKind() == null && node.purge()) {
                removed.add(node.getId());
            }
        }
        for (int id : removed) {
            _nodes.remove(id);
        }
    }
}
