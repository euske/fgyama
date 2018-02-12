//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;
import org.w3c.dom.*;


//  DFNode
//
public class DFNode implements Comparable<DFNode> {

    public DFScope scope;
    public DFRef ref;
    public int id;
    public String name;

    private DFNode input = null;
    private Map<String, DFNode> inputs = new HashMap<String, DFNode>();
    private List<DFNode> outputs = new ArrayList<DFNode>();

    public DFNode(DFScope scope, DFRef ref) {
	this.scope = scope;
	this.id = this.scope.graph.addNode(this);
	this.ref = ref;
    }

    @Override
    public String toString() {
	return ("<DFNode("+this.getName()+") "+this.getData()+">");
    }

    @Override
    public int compareTo(DFNode node) {
	return this.id - node.id;
    }

    public Element toXML(Document document) {
	Element elem = document.createElement("node");
	elem.setAttribute("name", this.getName());
	if (this.getType() != null) {
	    elem.setAttribute("type", this.getType());
	}
	if (this.getData() != null) {
	    elem.setAttribute("data", this.getData());
	}
	if (this.ref != null) {
	    elem.setAttribute("ref", this.ref.getName());
	}
	for (DFLink link : this.getLinks()) {
	    elem.appendChild(link.toXML(document));
	}
	return elem;
    }

    public String getName() {
	return ("N"+this.scope.name+"_"+id);
    }

    public String getType() {
	return null;
    }

    public String getData() {
	return null;
    }

    public DFLink[] getLinks() {
	int n = (this.input != null)? 1 : 0;
	DFLink[] links = new DFLink[n+this.inputs.size()];
	if (this.input != null) {
	    links[0] = new DFLink(this, this.input, null);
	}
	String[] labels = new String[this.inputs.size()];
	this.inputs.keySet().toArray(labels);
	Arrays.sort(labels);
	for (int i = 0; i < labels.length; i++) {
	    String label = labels[i];
	    DFNode node = this.inputs.get(label);
	    links[n+i] = new DFLink(this, node, label);
	}
	return links;
    }

    protected void accept(DFNode node) {
	assert this.input == null;
	this.input = node;
	node.outputs.add(this);
    }

    protected void accept(DFNode node, String label) {
	assert !this.inputs.containsKey(label);
	this.inputs.put(label, node);
	node.outputs.add(this);
    }

    public void finish(DFComponent cpt) {
    }

    public boolean purge() {
	if (this.input == null) return false;
	for (DFNode node : this.outputs) {
	    if (node.input == this) {
		node.input = this.input;
		this.input.outputs.add(node);
	    }
	    for (Map.Entry<String, DFNode> entry : node.inputs.entrySet()) {
		if (entry.getValue() == this) {
		    entry.setValue(this.input);
		    this.input.outputs.add(node);
		}
	    }
	}
	return true;
    }
}
