//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;
import org.w3c.dom.*;


//  DFLink
//
class DFLink {

    private DFNode _dst;
    private DFNode _src;
    private String _label;

    public DFLink(DFNode dst, DFNode src, String label) {
        _dst = dst;
        _src = src;
        _label = label;
    }

    @Override
    public String toString() {
        return ("<DFLink "+_dst+"<-"+_src+">");
    }

    public Element toXML(Document document) {
        Element elem = document.createElement("link");
        elem.setAttribute("src", _src.getNodeId());
        if (_label != null) {
            elem.setAttribute("label", _label);
        }
        return elem;
    }
}


//  DFNode
//
public class DFNode implements Comparable<DFNode> {

    private DFGraph _graph;
    private int _nid;
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
        _nid = graph.addNode(this);
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
        return _nid - node._nid;
    }

    public Element toXML(Document document) {
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
        return (_graph.getGraphId()+"_N"+_nid);
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

    public static DFType inferPrefixType(
        DFType type, PrefixExpression.Operator op) {
        if (op == PrefixExpression.Operator.NOT) {
            return DFBasicType.BOOLEAN;
        } else {
            return type;
        }
    }

    public static DFType inferInfixType(
        DFType left, InfixExpression.Operator op, DFType right) {
        if (op == InfixExpression.Operator.EQUALS ||
            op == InfixExpression.Operator.NOT_EQUALS ||
            op == InfixExpression.Operator.LESS ||
            op == InfixExpression.Operator.GREATER ||
            op == InfixExpression.Operator.LESS_EQUALS ||
            op == InfixExpression.Operator.GREATER_EQUALS ||
            op == InfixExpression.Operator.CONDITIONAL_AND ||
            op == InfixExpression.Operator.CONDITIONAL_OR) {
            return DFBasicType.BOOLEAN;
        } else if (op == InfixExpression.Operator.PLUS &&
                   (left == DFBuiltinTypes.getStringKlass() ||
                    right == DFBuiltinTypes.getStringKlass())) {
            return DFBuiltinTypes.getStringKlass();
        } else if (left instanceof DFUnknownType ||
                   right instanceof DFUnknownType) {
            return (left instanceof DFUnknownType)? right : left;
        } else if (0 <= left.canConvertFrom(right, null)) {
            return left;
        } else {
            return right;
        }
    }
}
