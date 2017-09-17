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

    public OutputStream stream;
    public Document document;
    
    private Element _file;
    
    public XmlExporter(OutputStream stream) {
	this.stream = stream;
	try {
	    this.document = Utils.createXml();
	} catch (ParserConfigurationException e) {
	    throw new RuntimeException();
	}
    }

    public void close()
	throws IOException {
	Utils.printXml(this.stream, this.document);
    }

    public void startFile(String path)
	throws IOException {
	_file = this.document.createElement("file");
	_file.setAttribute("path", path);
    }

    public void endFile()
	throws IOException {
	this.document.appendChild(_file);
	_file = null;
    }
    
    public void writeError(String funcName, String astName)
	throws IOException {
	Element failure = this.document.createElement("error");
	failure.setAttribute("func", funcName);
	failure.setAttribute("ast", astName);
	_file.appendChild(failure);
    }

    private Element writeScope(DFScope scope) {
	Element escope = this.document.createElement("scope");
	escope.setAttribute("name", scope.name);
	for (DFScope child : scope.children()) {
	    escope.appendChild(this.writeScope(child));
	}
	return escope;
    }
    
    public void writeGraph(DFGraph graph)
	throws IOException {
	Element egraph = this.document.createElement("graph");
	egraph.setAttribute("name", graph.name);
	egraph.appendChild(this.writeScope(graph.root));
	for (DFNode node : graph.nodes) {
	    Element enode = this.document.createElement("node");
	    enode.setAttribute("scope", node.scope.name);
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
		    enode.setAttribute("astType", Utils.getASTNodeTypeName(ast.getNodeType()));
		    enode.setAttribute("astStart", Integer.toString(ast.getStartPosition()));
		    enode.setAttribute("astLength", Integer.toString(ast.getLength()));
		}
	    }
	    for (DFLink link : node.send) {
		Element elink = this.document.createElement("link");
		elink.setAttribute("id", Integer.toString(link.lid));
		elink.setAttribute("type", link.type.toString());
		elink.setAttribute("src", link.src.name());
		elink.setAttribute("dst", link.dst.name());
		if (link.name != null) {
		    elink.setAttribute("name", link.name);
		}
		enode.appendChild(elink);
	    }
	    egraph.appendChild(enode);
	}
	_file.appendChild(egraph);
    }
}
