//  Java2DF
//
package net.tabesugi.dfaaa;
import java.io.*;
import java.util.*;
import javax.xml.parsers.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;
import org.w3c.dom.*;


//  XmlExporter
//
class XmlExporter extends Exporter {

    public Document document;
    
    private Element _root;
    private Element _file;
    
    public XmlExporter() {
	try {
	    this.document = Utils.createXml();
	    _root = this.document.createElement("dfaaa");
	} catch (ParserConfigurationException e) {
	    throw new RuntimeException();
	}
    }

    public void close()
	throws IOException {
	this.document.appendChild(_root);
	this.document.normalizeDocument();
    }

    public void startFile(String path)
	throws IOException {
	_file = this.document.createElement("file");
	_file.setAttribute("path", path);
    }

    public void endFile()
	throws IOException {
	_root.appendChild(_file);
	_file = null;
    }
    
    public void writeError(String funcName, String astName)
	throws IOException {
	Element failure = this.document.createElement("error");
	failure.setAttribute("func", funcName);
	failure.setAttribute("ast", astName);
	_file.appendChild(failure);
    }

    private Element writeScope(DFGraph graph, DFScope scope) {
	Element escope = this.document.createElement("scope");
	escope.setAttribute("name", scope.name);
	for (DFScope child : scope.children()) {
	    escope.appendChild(this.writeScope(graph, child));
	}
	for (DFNode node : graph.nodes()) {
	    if (node.scope != scope) continue;
	    Element enode = this.document.createElement("node");
	    enode.setAttribute("name", node.name());
	    enode.setAttribute("type", node.type().toString());
	    String label = node.label();
	    if (label != null) {
		enode.setAttribute("label", label);
	    }
	    if (node.ref != null) {
		enode.setAttribute("ref", node.ref.label());
	    }
	    if (node instanceof ProgNode) {
		ProgNode prognode = (ProgNode)node;
		ASTNode ast = prognode.ast;
		if (ast != null) {
		    Element east = this.document.createElement("ast");
		    east.setAttribute("type", Utils.getASTNodeTypeName(ast.getNodeType()));
		    east.setAttribute("start", Integer.toString(ast.getStartPosition()));
		    east.setAttribute("length", Integer.toString(ast.getLength()));
		    enode.appendChild(east);
		}
	    }
	    for (DFLink link : node.links()) {
		Element elink = this.document.createElement("link");
		elink.setAttribute("deg", Integer.toString(link.deg));
		elink.setAttribute("type", link.type.toString());
		//elink.setAttribute("src", link.src.name());
		elink.setAttribute("dst", link.dst.name());
		if (link.label != null) {
		    elink.setAttribute("label", link.label);
		}
		enode.appendChild(elink);
	    }
	    escope.appendChild(enode);
	}
	return escope;
    }
    
    public void writeGraph(DFGraph graph)
	throws IOException {
	Element egraph = this.document.createElement("graph");
	egraph.setAttribute("name", graph.name);
	egraph.appendChild(this.writeScope(graph, graph.root));
	_file.appendChild(egraph);
    }
}
