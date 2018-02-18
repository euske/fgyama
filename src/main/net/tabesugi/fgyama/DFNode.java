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

    private DFScope _scope;
    private DFRef _ref;
    private int _id;

    private DFNode _input = null;
    private Map<String, DFNode> _inputs = new HashMap<String, DFNode>();
    private List<DFNode> _outputs = new ArrayList<DFNode>();

    public DFNode(DFScope scope, DFRef ref) {
	_scope = scope;
	_id = scope.getGraph().addNode(this);
	_ref = ref;
    }

    @Override
    public String toString() {
	return ("<DFNode("+this.getName()+") "+this.getData()+">");
    }

    @Override
    public int compareTo(DFNode node) {
	return _id - node._id;
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
	if (_ref != null) {
	    elem.setAttribute("ref", _ref.getName());
	}
	for (DFLink link : this.getLinks()) {
	    elem.appendChild(link.toXML(document));
	}
	return elem;
    }

    public DFScope getScope() {
        return _scope;
    }

    public DFRef getRef() {
        return _ref;
    }

    public int getId() {
        return _id;
    }

    public String getName() {
	return ("N"+_scope.getName()+"_"+_id);
    }

    public String getType() {
	return null;
    }

    public String getData() {
	return null;
    }

    public DFLink[] getLinks() {
	int n = (_input != null)? 1 : 0;
	DFLink[] links = new DFLink[n+_inputs.size()];
	if (_input != null) {
	    links[0] = new DFLink(this, _input, null);
	}
	String[] labels = new String[_inputs.size()];
	_inputs.keySet().toArray(labels);
	Arrays.sort(labels);
	for (int i = 0; i < labels.length; i++) {
	    String label = labels[i];
	    DFNode node = _inputs.get(label);
	    links[n+i] = new DFLink(this, node, label);
	}
	return links;
    }

    protected void accept(DFNode node) {
	assert _input == null;
	_input = node;
	node._outputs.add(this);
    }

    protected void accept(DFNode node, String label) {
	assert !_inputs.containsKey(label);
	_inputs.put(label, node);
	node._outputs.add(this);
    }

    public void finish(DFComponent cpt) {
    }

    public boolean purge() {
	if (_input == null) return false;
	for (DFNode node : _outputs) {
	    if (node._input == this) {
		node._input = _input;
		_input._outputs.add(node);
	    }
	    for (Map.Entry<String, DFNode> entry : node._inputs.entrySet()) {
		if (entry.getValue() == this) {
		    entry.setValue(_input);
		    _input._outputs.add(node);
		}
	    }
	}
	return true;
    }
}
