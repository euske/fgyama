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

    public String name;
    public DFScope root;

    private Map<Integer, DFNode> _nodes = new HashMap<Integer, DFNode>();

    public DFGraph(String name) {
	this.name = name;
    }

    public String toString() {
	return ("<DFGraph("+this.name+")>");
    }

    public void setRoot(DFScope scope) {
	this.root = scope;
    }

    public int addNode(DFNode node) {
	int id = _nodes.size()+1;
	_nodes.put(id, node);
	return id;
    }

    public void cleanup() {
	ArrayList<Integer> removed = new ArrayList<Integer>();
	for (DFNode node : _nodes.values()) {
	    if (node.getType() == null && node.purge()) {
		removed.add(node.id);
	    }
	}
	for (int id : removed) {
	    _nodes.remove(id);
	}
    }

    public Element toXML(Document document) {
	Element elem = document.createElement("graph");
	elem.setAttribute("name", this.name);
	DFNode[] nodes = new DFNode[_nodes.size()];
	_nodes.values().toArray(nodes);
	Arrays.sort(nodes);
	elem.appendChild(this.writeScope(document, nodes, this.root));
	return elem;
    }

    private Element writeScope(Document document, DFNode[] nodes, DFScope scope) {
	Element elem = document.createElement("scope");
	elem.setAttribute("name", scope.name);
	for (DFScope child : scope.getChildren()) {
	    elem.appendChild(this.writeScope(document, nodes, child));
	}
	for (DFNode node : nodes) {
	    if (node.scope == scope) {
		elem.appendChild(node.toXML(document));
	    }
	}
	return elem;
    }
}
