//  TestDF
//
package net.tabesugi.dfaaa;
import java.io.*;
import java.util.*;
import org.w3c.dom.*;
import org.custommonkey.xmlunit.*;
import org.junit.Test;

public class TestDF extends XMLTestCase {

    public TestDF(String name) {
	super(name);
	XMLUnit.setIgnoreComments(true);
	XMLUnit.setIgnoreWhitespace(true);
	XMLUnit.setNormalize(true);
    }

    public void compareXml(String javaPath, String xmlPath)
	throws Exception {
	System.err.println("compareXml: "+javaPath+", "+xmlPath);
	XmlExporter exporter = new XmlExporter();
	exporter.startFile(javaPath);
	Java2DF converter = new Java2DF(exporter);
	converter.processFile(javaPath);
	exporter.endFile();
	exporter.close();
	Document refdoc = Utils.readXml(xmlPath);
	assertXMLEqual(refdoc, exporter.document);
    }

    @Test
    public void test1() throws Exception {
	compareXml("samples/f1.java", "samples/f1.xml");
    }
}
