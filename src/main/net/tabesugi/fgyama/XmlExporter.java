//  Java2DF
//
package net.tabesugi.fgyama;
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
            _root = this.document.createElement("fgyama");
        } catch (ParserConfigurationException e) {
            throw new RuntimeException();
        }
    }

    @Override
    public void close() {
        this.document.appendChild(_root);
        this.document.normalizeDocument();
    }

    @Override
    public void startFile(String path) {
        _file = this.document.createElement("file");
        _file.setAttribute("path", path);
    }

    @Override
    public void endFile() {
        _root.appendChild(_file);
        _file = null;
    }

    @Override
    public void writeError(String funcName, String astName) {
        Element failure = this.document.createElement("error");
        failure.setAttribute("func", funcName);
        failure.setAttribute("ast", astName);
        _file.appendChild(failure);
    }

    @Override
    public void writeGraph(DFGraph graph) {
        // Remove redundant nodes.
        graph.cleanup();
        _file.appendChild(graph.toXML(this.document));
    }
}
