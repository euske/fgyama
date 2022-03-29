//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import javax.xml.stream.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFNode
//
public class DFNode implements Comparable<DFNode> {

    private DFGraph _graph;
    private int _nid;
    private DFVarScope _scope;
    private DFType _type;
    private DFRef _ref;
    private ASTNode _ast;
    private Edge _edge0;

    private List<Edge> _edges =
        new ArrayList<Edge>();
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
        return ("<DFNode("+this.getNodeId()+") "+this.getKind()+">");
    }

    @Override
    public int compareTo(DFNode node) {
        return _nid - node._nid;
    }

    public void writeXML(XMLStreamWriter writer)
        throws XMLStreamException {
        writer.writeStartElement("node");
        writer.writeAttribute("id", this.getNodeId());
        if (this.getKind() != null) {
            writer.writeAttribute("kind", this.getKind());
        }
        if (this.getData() != null) {
            writer.writeAttribute("data", this.getData());
        }
        writer.writeAttribute("type", _type.getTypeName());
        if (_ref != null) {
            writer.writeAttribute("ref", _ref.getFullName());
        }
        if (_ast != null) {
            Utils.writeXML(writer, _ast);
        }
        for (Edge edge : _edges) {
            edge.writeXML(writer);
        }
        writer.writeEndElement();
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

    public boolean hasValue() {
        return _edge0 != null;
    }

    public Edge accept(DFNode node) {
        return this.accept(node, null);
    }

    public Edge accept(DFNode node, String label) {
        assert node != null;
        assert node != this;
        Edge edge = new Edge(node, label);
        _edges.add(edge);
        node._outputs.add(this);
        if (label == null) {
            assert _edge0 == null;
            if (_type instanceof DFUnknownType) {
                _type = node.getNodeType();
            }
            _edge0 = edge;
        }
        return edge;
    }

    public boolean canMerge() {
        return false;
    }

    public void merge(DFNode node) {
    }

    public boolean purge() {
        if (this.getKind() == null && this.hasValue()) {
            assert _edge0 != null;
            this.disconnect(_edge0._src);
            return true;
        }
        return false;
    }

    protected void disconnect(DFNode src) {
        assert this != src;
        for (Edge edge : _edges) {
            edge._src._outputs.remove(this);
        }
        for (DFNode node : _outputs) {
            for (Edge edge : node._edges) {
                if (edge._src == this) {
                    edge._src = src;
                    src._outputs.add(node);
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
        } else {
            try {
                left.canConvertFrom(right, null);
                return left;
            } catch (TypeIncompatible e) {
                return right;
            }
        }
    }

    //  Edge
    //
    public class Edge {

        private DFNode _src;
        private String _label;

        public Edge(DFNode src, String label) {
            _src = src;
            _label = label;
        }

        public DFNode getSrc() {
            return _src;
        }

        public DFNode getDst() {
            return DFNode.this;
        }

        protected boolean hasLabel(String label) {
            return ((_label == null && label == null) ||
                    (_label != null && _label.equals(label)));
        }

        @Override
        public String toString() {
            return ("<Edge "+DFNode.this+"<-"+_src+">");
        }

        public void writeXML(XMLStreamWriter writer)
            throws XMLStreamException {
            writer.writeStartElement("accept");
            writer.writeAttribute("src", _src.getNodeId());
            if (_label != null) {
                writer.writeAttribute("label", _label);
            }
            writer.writeEndElement();
        }
    }
}
