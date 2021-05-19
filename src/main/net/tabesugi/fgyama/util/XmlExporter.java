//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import javax.xml.stream.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  XmlExporter
//
public class XmlExporter extends Exporter {

    private XMLStreamWriter _writer;
    private DFKlass _klass = null;
    private int _baseId = 1;

    public XmlExporter(OutputStream stream) {
        try {
            XMLOutputFactory factory = XMLOutputFactory.newFactory();
            _writer = factory.createXMLStreamWriter(stream, "utf-8");
            _writer.writeStartDocument();
            _writer.writeStartElement("fgyama");
        } catch (XMLStreamException e) {
            throw new RuntimeException();
        }
    }

    @Override
    public void close() {
        try {
            _writer.writeEndElement();
            _writer.writeEndDocument();
            _writer.close();
        } catch (XMLStreamException e) {
            throw new RuntimeException();
        }
    }

    @Override
    public void startKlass(DFSourceKlass klass) {
        assert _klass == null;
        _klass = klass;
        try {
            _writer.writeStartElement("class");
            klass.writeXML(_writer);
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void endKlass() {
        assert _klass != null;
        try {
            _writer.writeEndElement();
        } catch (XMLStreamException e) {
            throw new RuntimeException();
        }
        _klass = null;
    }

    @Override
    public void writeMethod(DFSourceMethod method)
        throws InvalidSyntax, EntityNotFound {
        assert _klass != null;
        DFGraph graph = method.getDFGraph(_baseId);
        if (graph == null) return;
        _baseId++;
        try {
            _writer.writeStartElement("method");
            method.writeXML(_writer, graph);
            _writer.writeEndElement();
        } catch (XMLStreamException e) {
            throw new RuntimeException();
        }
    }
}
