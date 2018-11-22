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

    private static DFRootTypeSpace _rootSpace = null;
    private static Java2DF _converter = null;

    public UnitTestDF(String name)
        throws Exception {
	super(name);
	XMLUnit.setIgnoreComments(true);
	XMLUnit.setIgnoreWhitespace(true);
	XMLUnit.setNormalize(true);
        if (_rootSpace == null) {
            _rootSpace = new DFRootTypeSpace();
        }
        if (_converter == null) {
            _converter = new Java2DF(_rootSpace);
        }
    }

    public void compareXml(String[] javaPaths, String xmlPath)
	throws Exception {
	XmlExporter exporter = new XmlExporter();
        _converter.setExporter(exporter);
        CompilationUnit[] cunits = new CompilationUnit[javaPaths.length];
        for (int i = 0; i < javaPaths.length; i++) {
            System.err.println("compareXml: "+javaPaths[i]+", "+xmlPath);
            cunits[i] = Utils.parseFile(javaPaths[i]);
        }
        List<DFKlass> klasses = new ArrayList<DFKlass>();
        for (int i = 0; i < cunits.length; i++) {
            _converter.buildTypeSpace(klasses, javaPaths[i], cunits[i]);
        }
        for (int i = 0; i < cunits.length; i++) {
            _converter.buildKlassSpace(cunits[i]);
        }
        for (DFKlass klass : klasses) {
            klass.addOverrides();
        }
        for (int i = 0; i < cunits.length; i++) {
            exporter.startFile(javaPaths[i]);
            _converter.buildGraphs(javaPaths[i], cunits[i]);
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
    public void test_07_basic_efor() throws Exception {
        compareXml(TESTDATA+"/basic_efor.java", TESTDATA+"/basic_efor.graph");
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
    public void test_16_basic_poly() throws Exception {
        compareXml(TESTDATA+"/basic_poly.java", TESTDATA+"/basic_poly.graph");
    }
    @Test
    public void test_17_basic_enum() throws Exception {
        compareXml(TESTDATA+"/basic_enum.java", TESTDATA+"/basic_enum.graph");
    }
    @Test
    public void test_18_basic_staticimport() throws Exception {
        compareXml(TESTDATA+"/basic_staticimport.java", TESTDATA+"/basic_staticimport.graph");
    }
    @Test
    public void test_19_basic_exception() throws Exception {
        compareXml(TESTDATA+"/basic_exception.java", TESTDATA+"/basic_exception.graph");
    }

    @Test
    public void test_19_multi_xref() throws Exception {
	compareXml(
            new String[] {
                TESTDATA+"/multi/dom/meep/multi_xref1.java",
                TESTDATA+"/multi/dom/meep/multi_xref2.java",
                TESTDATA+"/multi/dom/dood/multi_xref3.java",
            },
            TESTDATA+"/multi/multi_xref.graph");
    }
}
