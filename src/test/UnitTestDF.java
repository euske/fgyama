//  UnitTestDF
//
import java.io.*;
import java.util.*;
import org.w3c.dom.*;
import org.custommonkey.xmlunit.*;
import org.junit.Test;
import net.tabesugi.fgyama.*;

public class UnitTestDF extends XMLTestCase {

    public static final String TESTDATA = "tests";

    public UnitTestDF(String name) {
	super(name);
	XMLUnit.setIgnoreComments(true);
	XMLUnit.setIgnoreWhitespace(true);
	XMLUnit.setNormalize(true);
    }

    public void compareXml(String javaPath, String xmlPath)
	throws Exception {
        compareXml(javaPath, xmlPath, false, null);
    }
    public void compareXml(String javaPath, String xmlPath,
			   boolean resolve, String[] srcPath)
	throws Exception {
	System.err.println("compareXml: "+javaPath+", "+xmlPath);
	XmlExporter exporter = new XmlExporter();
	exporter.startFile(javaPath);
	Java2DF converter = new Java2DF(exporter, resolve);
	converter.processFile(null, srcPath, javaPath);
	exporter.endFile();
	exporter.close();
	Document refdoc = Utils.readXml(xmlPath);
	try {
	    assertXMLEqual(refdoc, exporter.document);
	} catch (junit.framework.AssertionFailedError e) {
	    OutputStream out = new FileOutputStream(new File(xmlPath+".err"));
	    Utils.printXml(out, exporter.document);
	    out.close();
	    throw e;
	}
    }

    @Test
    public void test_01_basic_return() throws Exception {
	compareXml(TESTDATA+"/basic_return.java", TESTDATA+"/basic_return.graph");
    }
    @Test
    public void test_02_basic_assign() throws Exception {
	compareXml(TESTDATA+"/basic_assign.java", TESTDATA+"/basic_assign.graph");
    }
    @Test
    public void test_03_basic_if() throws Exception {
	compareXml(TESTDATA+"/basic_if.java", TESTDATA+"/basic_if.graph");
    }
    @Test
    public void test_04_basic_while() throws Exception {
	compareXml(TESTDATA+"/basic_while.java", TESTDATA+"/basic_while.graph");
    }
    @Test
    public void test_05_basic_do() throws Exception {
	compareXml(TESTDATA+"/basic_do.java", TESTDATA+"/basic_do.graph");
    }
    @Test
    public void test_06_basic_for() throws Exception {
	compareXml(TESTDATA+"/basic_for.java", TESTDATA+"/basic_for.graph");
    }
    @Test
    public void test_07_basic_xfor() throws Exception {
	compareXml(TESTDATA+"/basic_xfor.java", TESTDATA+"/basic_xfor.graph");
    }
    @Test
    public void test_08_basic_break() throws Exception {
	compareXml(TESTDATA+"/basic_break.java", TESTDATA+"/basic_break.graph");
    }
    @Test
    public void test_09_basic_continue() throws Exception {
	compareXml(TESTDATA+"/basic_continue.java", TESTDATA+"/basic_continue.graph");
    }
    @Test
    public void test_10_basic_switch() throws Exception {
	compareXml(TESTDATA+"/basic_switch.java", TESTDATA+"/basic_switch.graph");
    }
    @Test
    public void test_11_basic_methods() throws Exception {
	compareXml(TESTDATA+"/basic_methods.java", TESTDATA+"/basic_methods.graph");
    }
    @Test
    public void test_12_basic_ops() throws Exception {
	compareXml(TESTDATA+"/basic_ops.java", TESTDATA+"/basic_ops.graph");
    }
    @Test
    public void test_13_basic_names() throws Exception {
	compareXml(TESTDATA+"/basic_names.java", TESTDATA+"/basic_names.graph",
		   true, null);
    }

    @Test
    public void test_14_canonical_name() throws Exception {
	String name = PackageNameExtractor.getCanonicalName(TESTDATA+"/basic_names.java");
	assertEquals(name, "dom.meep.Foo");
    }

    @Test
    public void test_15_multi_xref() throws Exception {
	String[] srcpath = new String[] { TESTDATA+"/multi" };
	compareXml(TESTDATA+"/multi/dom/meep/multi_xref1.java",
		   TESTDATA+"/multi/dom/meep/multi_xref1.graph",
		   true, srcpath);
	compareXml(TESTDATA+"/multi/dom/meep/multi_xref2.java",
		   TESTDATA+"/multi/dom/meep/multi_xref2.graph",
		   true, srcpath);
    }
}
