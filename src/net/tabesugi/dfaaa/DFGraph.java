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

    private Map<Integer, DFNode> nodes = new HashMap<Integer, DFNode>();

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
	int id = this.nodes.size()+1;
	this.nodes.put(id, node);
	return id;
    }

    public DFNode[] getNodes() {
	DFNode[] nodes = new DFNode[this.nodes.size()];
	this.nodes.values().toArray(nodes);
	Arrays.sort(nodes);
	return nodes;
    }

    public void cleanup() {
	ArrayList<Integer> removed = new ArrayList<Integer>();
	for (DFNode node : this.nodes.values()) {
	    if (node.getType() == null && node.purge()) {
		removed.add(node.id);
	    }
	}
	for (int id : removed) {
	    this.nodes.remove(id);
	}
    }
}
