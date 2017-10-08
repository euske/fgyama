//  Java2DF
//
package net.tabesugi.dfaaa;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFGraph
//
public class DFGraph {

    public String name;
    public DFScope root;

    private List<DFNode> nodes = new ArrayList<DFNode>();

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
	this.nodes.add(node);
	return this.nodes.size();
    }

    public void removeNode(DFNode node) {
        this.nodes.remove(node);
    }

    public DFNode[] getNodes() {
	DFNode[] nodes = new DFNode[this.nodes.size()];
	this.nodes.toArray(nodes);
	Arrays.sort(nodes);
	return nodes;
    }

    public void cleanup() {
	ArrayList<DFNode> removed = new ArrayList<DFNode>();
	for (DFNode node : this.nodes) {
	    if (node.getType() == null) {
		node.rewire();
		removed.add(node);
	    }
	}
	for (DFNode node : removed) {
	    this.nodes.remove(node);
	}
    }
}
