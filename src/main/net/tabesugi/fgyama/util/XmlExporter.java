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
    private Element _class;

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
    public void startKlass(DFKlass klass) {
        assert _class == null;
        _class = klass.toXML(this.document);
    }

    @Override
    public void endKlass() {
        assert _class != null;
        _root.appendChild(_class);
        _class = null;
    }

    @Override
    public void writeGraph(DFGraph graph) {
        assert _class != null;
        _class.appendChild(graph.toXML(this.document));
    }
}
