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

    private DFGraph _graph;
    private int _id;
    private DFVarScope _scope;
    private DFType _type;
    private DFRef _ref;

    private DFNode _input = null;
    private SortedMap<String, DFNode> _inputs =
        new TreeMap<String, DFNode>();
    private List<DFNode> _outputs =
        new ArrayList<DFNode>();

    public DFNode(DFGraph graph, DFVarScope scope, DFType type, DFRef ref) {
        assert graph != null;
        assert scope != null;
        assert type != null;
        _graph = graph;
        _id = graph.addNode(this);
        _scope = scope;
        _type = type;
        _ref = ref;
    }

    @Override
    public String toString() {
        return ("<DFNode("+this.getNodeId()+") "+this.getData()+">");
    }

    @Override
    public int compareTo(DFNode node) {
        return _id - node._id;
    }

    public Element toXML(
	Document document, Set<DFNode> input, Set<DFNode> output) {
        Element elem = document.createElement("node");
        elem.setAttribute("id", this.getNodeId());
        if (this.getKind() != null) {
            elem.setAttribute("kind", this.getKind());
        }
        if (this.getData() != null) {
            elem.setAttribute("data", this.getData());
        }
        elem.setAttribute("type", _type.getTypeName());
        if (_ref != null) {
            elem.setAttribute("ref", _ref.getFullName());
            boolean fin = (input != null && input.contains(this));
            boolean fout = (output != null && output.contains(this));
            if (fin && fout) {
                elem.setAttribute("flow", "both");
            } else if (fin) {
                elem.setAttribute("flow", "in");
            } else if (fout) {
                elem.setAttribute("flow", "out");
	    }
        }
        for (DFLink link : this.getLinks()) {
            elem.appendChild(link.toXML(document));
        }
        return elem;
    }

    public DFVarScope getScope() {
        return _scope;
    }

    public DFType getNodeType() {
        return _type;
    }

    public DFRef getRef() {
        return _ref;
    }

    public String getNodeId() {
        return ("N"+_graph.getHash()+"_"+_id);
    }

    public String getKind() {
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
        for (int i = 0; i < labels.length; i++) {
            String label = labels[i];
            DFNode node = _inputs.get(label);
            links[n+i] = new DFLink(this, node, label);
        }
        return links;
    }

    protected boolean hasInput() {
        return _input != null;
    }

    protected void accept(DFNode node) {
        assert node != null;
        assert _input == null;
        _input = node;
        if (_type instanceof DFUnknownType) {
            _type = node.getNodeType();
        }
        node._outputs.add(this);
    }

    protected void accept(DFNode node, String label) {
        assert node != null;
        assert !_inputs.containsKey(label);
        _inputs.put(label, node);
        node._outputs.add(this);
    }

    public void close(DFNode ctx) {
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
