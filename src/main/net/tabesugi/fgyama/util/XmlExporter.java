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

    public XmlExporter(OutputStream stream) {
        super(1);
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
        super.close();
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
    public void writeMethod(DFSourceMethod method, DFGraph graph) {
        assert _klass != null;
        try {
            method.writeXML(_writer, graph);
        } catch (XMLStreamException e) {
            throw new RuntimeException();
        }
    }
}
