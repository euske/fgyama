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
public class XmlExporter extends Exporter {

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

    @Override
    public void close()
	throws IOException {
	this.document.appendChild(_root);
	this.document.normalizeDocument();
    }

    @Override
    public void startFile(String path)
	throws IOException {
	_file = this.document.createElement("file");
	_file.setAttribute("path", path);
    }

    @Override
    public void endFile()
	throws IOException {
	_root.appendChild(_file);
	_file = null;
    }
    
    @Override
    public void writeError(String funcName, String astName)
	throws IOException {
	Element failure = this.document.createElement("error");
	failure.setAttribute("func", funcName);
	failure.setAttribute("ast", astName);
	_file.appendChild(failure);
    }
    
    @Override
    public void writeGraph(DFGraph graph)
	throws IOException {
	_file.appendChild(graph.toXML(this.document));
    }
}
