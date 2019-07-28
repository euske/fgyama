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

    protected DFNode getSrc() {
        return _src;
    }

    protected void setSrc(DFNode node) {
        _src = node;
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

    private DFNode _value = null;
    private List<DFLink> _links =
        new ArrayList<DFLink>();
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
        DFLink[] links = new DFLink[_links.size()];
        _links.toArray(links);
        return links;
    }

    protected boolean hasValue() {
        return _value != null;
    }

    protected void accept(DFNode node) {
        this.accept(node, null);
    }

    protected void accept(DFNode node, String label) {
        assert node != null;
        if (label == null) {
            assert _value == null;
            _value = node;
            if (_type instanceof DFUnknownType) {
                _type = node.getNodeType();
            }
        }
        DFLink link = new DFLink(this, node, label);
        _links.add(link);
        node._outputs.add(this);
    }

    public boolean purge() {
        if (_value == null) return false;
        for (DFNode node : _outputs) {
            if (node._value == this) {
                node._value = _value;
                _value._outputs.add(node);
            }
            for (DFLink link : node._links) {
                if (link.getSrc() == this) {
                    link.setSrc(_value);
                    _value._outputs.add(node);
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
