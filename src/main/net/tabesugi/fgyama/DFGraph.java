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
    private DFMethod _method;
    private ASTNode _ast;

    private String _hash = null;
    private List<DFNode> _nodes =
        new ArrayList<DFNode>();

    // DFGraph for a method.
    public DFGraph(
        DFVarScope root, DFMethod method, ASTNode ast) {
        _root = root;
        _method = method;
        _ast = ast;
    }

    @Override
    public String toString() {
        return ("<DFGraph("+_root.getFullName()+")>");
    }

    public String getHash() {
        if (_hash == null) {
            if (_method != null) {
                _hash = Utils.hashString(_method.getSignature());
            } else {
                _hash = Utils.hashString(_root.getFullName());
            }
        }
        return _hash;
    }

    public Element toXML(Document document) {
        Element elem = document.createElement("method");
	Set<DFNode> input = null;
	Set<DFNode> output = null;
        if (_method != null) {
            elem.setAttribute("name", _method.getSignature());
            elem.setAttribute("style", _method.getCallStyle().toString());
	    DFFrame frame = _method.getFrame();
	    input = frame.getInputNodes();
	    output = frame.getOutputNodes();
	    for (DFMethod caller : _method.getCallers()) {
		Element ecaller = document.createElement("caller");
		ecaller.setAttribute("name", caller.getSignature());
		elem.appendChild(ecaller);
	    }
        } else {
            elem.setAttribute("name", _root.getFullName());
            elem.setAttribute("style", DFCallStyle.Initializer.toString());
        }
        if (_ast != null) {
            Element east = document.createElement("ast");
            east.setAttribute("type", Integer.toString(_ast.getNodeType()));
            east.setAttribute("start", Integer.toString(_ast.getStartPosition()));
            east.setAttribute("length", Integer.toString(_ast.getLength()));
            elem.appendChild(east);
        }
        DFNode[] nodes = new DFNode[_nodes.size()];
        _nodes.toArray(nodes);
        Arrays.sort(nodes);
        elem.appendChild(_root.toXML(document, nodes, input, output));
        return elem;
    }

    private static String getNodeIds(DFNode[] nodes) {
        List<String> nodeIds = new ArrayList<String>();
        for (DFNode node : nodes) {
            DFVarRef ref = node.getRef();
            if (ref != null && !ref.isLocal()) {
                nodeIds.add(node.getNodeId());
            }
        }
        String[] names = new String[nodeIds.size()];
        nodeIds.toArray(names);
        Arrays.sort(names);
        return Utils.join(" ", names);
    }

    public int addNode(DFNode node) {
        _nodes.add(node);
        return _nodes.size();
    }

    public void cleanup() {
        Set<DFNode> removed = new HashSet<DFNode>();
        for (DFNode node : _nodes) {
            if (node.getKind() == null && node.purge()) {
                removed.add(node);
            }
        }
        // Do not remove input/output nodes.
        if (_method != null) {
	    DFFrame frame = _method.getFrame();
	    for (DFNode node : frame.getInputNodes()) {
		removed.remove(node);
	    }
	    for (DFNode node : frame.getOutputNodes()) {
		removed.remove(node);
	    }
	}
        for (DFNode node : removed) {
            _nodes.remove(node);
        }
    }
}
