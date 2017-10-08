//  Java2DF
//
package net.tabesugi.dfaaa;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFNode
//
public abstract class DFNode implements Comparable<DFNode> {

    public DFScope scope;
    public DFRef ref;
    public int id;
    public String name;
    
    private List<DFNode> outputs = new ArrayList<DFNode>();
    private DFNode input = null;
    private Map<String, DFNode> inputs = new HashMap<String, DFNode>();
    
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
    
    public String getName() {
	return ("N"+this.scope.name+"_"+id);
    }

    abstract public String getType();
    
    public String getData() {
	return null;
    }

    public DFLink[] getLinks() {
	List<DFLink> extra = this.getExtraLinks();
	DFLink[] links = new DFLink[extra.size()+this.inputs.size()];
	for (int i = 0; i < extra.size(); i++) {
	    links[i] = extra.get(i);
	}
	String[] labels = new String[this.inputs.size()];
	this.inputs.keySet().toArray(labels);
	Arrays.sort(labels);
	for (int i = 0; i < labels.length; i++) {
	    String label = labels[i];
	    DFNode node = this.inputs.get(label);
	    links[extra.size()+i] = new DFLink(this, node, label);
	}
	return links;
    }

    protected List<DFLink> getExtraLinks() {
	List<DFLink> extra = new ArrayList<DFLink>();
	if (this.input != null) {
	    extra.add(new DFLink(this, this.input, null));
	}	
	return extra;
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
