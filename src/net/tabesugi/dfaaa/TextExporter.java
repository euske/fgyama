//  Java2DF
//
package net.tabesugi.dfaaa;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  TextExporter
//
class TextExporter {

    public BufferedWriter writer;
    
    public TextExporter(OutputStream stream) {
	this.writer = new BufferedWriter(new OutputStreamWriter(stream));
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
    
    public void writeFailure(String funcName, String astName)
	throws IOException {
	this.writer.write("!"+funcName+","+astName+"\n");
	this.writer.flush();
    }
    
    public void writeGraph(DFScope scope)
	throws IOException {
	if (scope.parent == null) {
	    this.writer.write("@"+scope.name+"\n");
	} else {
	    this.writer.write(":"+scope.name+","+scope.parent.name+"\n");
	}
	for (DFNode node : scope.nodes) {
	    this.writer.write("+"+scope.name);
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
	for (DFNode node : scope.nodes) {
	    for (DFLink link : node.send) {
		this.writer.write("-"+link.src.name()+","+link.dst.name());
		this.writer.write(","+link.lid+","+link.type.ordinal());
		if (link.name != null) {
		    this.writer.write(","+link.name);;
		}
		this.writer.newLine();
	    }
	}
	for (DFScope child : scope.children.values()) {
	    this.writeGraph(child);
	}
	if (scope.parent == null) {
	    this.writer.newLine();
	}
	this.writer.flush();
    }
}
