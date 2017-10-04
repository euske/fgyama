//  Java2DF
//
package net.tabesugi.dfaaa;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  TextExporter
//
class TextExporter extends Exporter {

    public BufferedWriter writer;
    
    public TextExporter(OutputStream stream) {
	this.writer = new BufferedWriter(new OutputStreamWriter(stream));
    }

    public void close() 
	throws IOException {
	this.writer.close();
    }

    public void startFile(String path)
	throws IOException {
	this.writer.write("#"+path+"\n");
	this.writer.flush();
    }

    public void endFile()
	throws IOException {
	this.writer.flush();
    }
    
    public void writeError(String funcName, String astName)
	throws IOException {
	this.writer.write("!"+funcName+","+astName+"\n");
	this.writer.flush();
    }

    private void writeScope(DFScope scope)
	throws IOException {
	this.writer.write(":"+scope.name);
	if (scope.parent != null) {
	    this.writer.write(","+scope.parent.name);
	}
	this.writer.newLine();
	for (DFScope child : scope.children()) {
	    this.writeScope(child);
	}
    }
    
    public void writeGraph(DFGraph graph)
	throws IOException {
	this.writer.write("@"+graph.name+"\n");
	this.writeScope(graph.root);
	for (DFNode node : graph.nodes()) {
	    this.writer.write("+"+node.scope.name);
	    this.writer.write(","+node.name());
	    this.writer.write(","+node.type().ordinal());
	    String label = node.label();
	    if (label != null) {
		this.writer.write(","+Utils.sanitize(label));
	    } else {
		this.writer.write(",");
	    }
	    if (node.ref != null) {
		this.writer.write(","+node.ref.label());
	    } else {
		this.writer.write(",");
	    }
	    if (node instanceof ProgNode) {
		ProgNode prognode = (ProgNode)node;
		ASTNode ast = prognode.ast;
		if (ast != null) {
		    int type = ast.getNodeType();
		    int start = ast.getStartPosition();
		    int length = ast.getLength();
		    this.writer.write(","+type+","+start+","+length);
		}
	    }
	    this.writer.newLine();
	}
	for (DFNode node : graph.nodes()) {
	    for (DFLink link : node.links()) {
		this.writer.write("-"+link.src.name()+","+link.dst.name());
		this.writer.write(","+link.deg+","+link.type.ordinal());
		if (link.label != null) {
		    this.writer.write(","+link.label);;
		}
		this.writer.newLine();
	    }
	}
	this.writer.newLine();
	this.writer.flush();
    }
}
