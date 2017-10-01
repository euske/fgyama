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
	try {
	    assertXMLEqual(refdoc, exporter.document);
	} catch (junit.framework.AssertionFailedError e) {
	    OutputStream out = new FileOutputStream(new File(javaPath+".out"));
	    Utils.printXml(out, exporter.document);
	    out.close();
	    throw e;
	}
    }

    @Test
    public void testf1() throws Exception {
	compareXml("samples/f1.java", "samples/f1.xml");
    }
    @Test
    public void testf2() throws Exception {
	compareXml("samples/f2.java", "samples/f2.xml");
    }
    @Test
    public void testf3() throws Exception {
	compareXml("samples/f3.java", "samples/f3.xml");
    }
    @Test
    public void testf4() throws Exception {
	compareXml("samples/f4.java", "samples/f4.xml");
    }
    @Test
    public void testf5() throws Exception {
	compareXml("samples/f5.java", "samples/f5.xml");
    }
    @Test
    public void testf6() throws Exception {
	compareXml("samples/f6.java", "samples/f6.xml");
    }
    @Test
    public void testf7() throws Exception {
	compareXml("samples/f7.java", "samples/f7.xml");
    }
    @Test
    public void testc12() throws Exception {
	compareXml("samples/c12.java", "samples/c12.xml");
    }
}
