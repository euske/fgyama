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
    public void test_basic_f1() throws Exception {
	compareXml("testdata/basic_f1.java", "testdata/basic_f1.graph");
    }
    @Test
    public void test_basic_f2() throws Exception {
	compareXml("testdata/basic_f2.java", "testdata/basic_f2.graph");
    }
    @Test
    public void test_basic_f3() throws Exception {
	compareXml("testdata/basic_f3.java", "testdata/basic_f3.graph");
    }
    @Test
    public void test_basic_f4() throws Exception {
	compareXml("testdata/basic_f4.java", "testdata/basic_f4.graph");
    }
    @Test
    public void test_basic_f5() throws Exception {
	compareXml("testdata/basic_f5.java", "testdata/basic_f5.graph");
    }
}
