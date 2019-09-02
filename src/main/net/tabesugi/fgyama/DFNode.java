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
    private int _nid;
    private DFVarScope _scope;
    private DFType _type;
    private DFRef _ref;
    private ASTNode _ast;

    private DFNode _value = null;
    private List<DFLink> _links =
        new ArrayList<DFLink>();
    private List<DFNode> _outputs =
        new ArrayList<DFNode>();

    public DFNode(
        DFGraph graph, DFVarScope scope, DFType type, DFRef ref,
        ASTNode ast) {
        assert graph != null;
        assert scope != null;
        assert type != null;
        _graph = graph;
        _nid = graph.addNode(this);
        _scope = scope;
        _type = type;
        _ref = ref;
        _ast = ast;
    }

    @Override
    public String toString() {
        return ("<DFNode("+this.getNodeId()+") "+this.getKind()+" "+this.getData()+">");
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
        if (_ast != null) {
            Element east = document.createElement("ast");
            int start = _ast.getStartPosition();
            int end = start + _ast.getLength();
            east.setAttribute("type", Integer.toString(_ast.getNodeType()));
            east.setAttribute("start", Integer.toString(start));
            east.setAttribute("end", Integer.toString(end));
            elem.appendChild(east);
        }
        for (DFLink link : _links) {
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
        DFLink link = new DFLink(node, label);
        _links.add(link);
        node._outputs.add(this);
    }

    public boolean canPurge() {
        return (this.getKind() == null && this.hasValue());
    }

    public void unlink() {
        assert _value != null;
        unlink(_value);
    }

    protected void unlink(DFNode value) {
        for (DFNode node : _outputs) {
            if (node._value == this) {
                node._value = value;
                value._outputs.add(node);
            }
            for (DFLink link : node._links) {
                if (link.getSrc() == this) {
                    link.setSrc(value);
                    value._outputs.add(node);
                }
            }
        }
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

    //  DFLink
    //
    private class DFLink {

        private DFNode _src;
        private String _label;

        public DFLink(DFNode src, String label) {
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
            return ("<DFLink "+DFNode.this+"<-"+_src+">");
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
}
