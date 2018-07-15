//  UnitTestDF
//
import java.io.*;
import java.util.*;
import org.w3c.dom.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;
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

    public void compareXml(String[] javaPaths, String xmlPath)
	throws Exception {
        DFRootTypeSpace rootSpace = new DFRootTypeSpace();
        rootSpace.loadDefaultClasses();
	XmlExporter exporter = new XmlExporter();
	Java2DF converter = new Java2DF(rootSpace, exporter);
        CompilationUnit[] cunits = new CompilationUnit[javaPaths.length];
        for (int i = 0; i < javaPaths.length; i++) {
            System.err.println("compareXml: "+javaPaths[i]+", "+xmlPath);
            cunits[i] = converter.parseFile(javaPaths[i]);
        }
        for (int i = 0; i < cunits.length; i++) {
            converter.buildTypeSpace(null, cunits[i]);
        }
        for (int i = 0; i < cunits.length; i++) {
            converter.buildClassSpace(cunits[i]);
        }
        for (int i = 0; i < cunits.length; i++) {
            exporter.startFile(javaPaths[i]);
            converter.buildGraphs(cunits[i]);
            exporter.endFile();
        }
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

    public void compareXml(String javaPath, String xmlPath)
	throws Exception {
        compareXml(new String[] { javaPath }, xmlPath);
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
        try {
            compareXml(TESTDATA+"/basic_ops.java", TESTDATA+"/basic_ops.graph");
            fail("EntityNotFound expected");
        } catch (EntityNotFound e) {
        }
    }
    @Test
    public void test_13_basic_names() throws Exception {
	compareXml(TESTDATA+"/basic_names.java", TESTDATA+"/basic_names.graph");
    }
    @Test
    public void test_14_basic_funcs() throws Exception {
	compareXml(TESTDATA+"/basic_funcs.java", TESTDATA+"/basic_funcs.graph");
    }
    @Test
    public void test_15_basic_generics() throws Exception {
	compareXml(TESTDATA+"/basic_generics.java", TESTDATA+"/basic_generics.graph");
    }

    @Test
    public void test_16_multi_xref() throws Exception {
	compareXml(
            new String[] {
                TESTDATA+"/multi/dom/meep/multi_xref1.java",
                TESTDATA+"/multi/dom/meep/multi_xref2.java",
                TESTDATA+"/multi/dom/dood/multi_xref3.java",
            },
            TESTDATA+"/multi/multi_xref.graph");
    }
}
