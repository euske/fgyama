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
    public List<DFNode> nodes = new ArrayList<DFNode>();

    public DFGraph(String name) {
	this.name = name;
    }

    public String toString() {
	return ("<DFGraph("+this.name+")>");
    }

    public void setRoot(DFScope scope) {
	this.root = scope;
    }
    
    public void addNode(DFNode node) {
	this.nodes.add(node);
    }

    public void removeNode(DFNode node) {
        this.nodes.remove(node);
    }

    public void cleanup() {
        List<DFNode> removed = new ArrayList<DFNode>();
        for (DFNode node : this.nodes) {
            if (node.canOmit() &&
                node.send.size() == 1 &&
                node.recv.size() == 1) {
                removed.add(node);
            }
        }
        for (DFNode node : removed) {
            DFLink link0 = node.recv.get(0);
            DFLink link1 = node.send.get(0);
            if (link0.type == link1.type &&
		(link0.name == null || link1.name == null)) {
                node.remove();
                String name = link0.name;
                if (name == null) {
                    name = link1.name;
                }
                link0.src.connect(link1.dst, 1, link0.type, name);
            }
        }
    }			   
}
