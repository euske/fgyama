//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.apache.bcel.*;
import org.apache.bcel.classfile.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;
import org.w3c.dom.*;


//  DFRootTypeSpace
//
public class DFRootTypeSpace extends DFTypeSpace {

    public static DFClass OBJECT_CLASS = null;
    public static DFClass ARRAY_CLASS = null;
    public static DFClass STRING_CLASS = null;

    private DFGlobalVarScope _global = new DFGlobalVarScope();

    public DFRootTypeSpace() {
        super("ROOT");
    }

    @Override
    public String toString() {
        return ("<DFRootTypeSpace>");
    }

    public DFGlobalVarScope getGlobalScope() {
        return _global;
    }

    public void loadJarFile(String jarPath)
        throws IOException {
        Logger.info("Loading: "+jarPath);
        JarFile jarfile = new JarFile(jarPath);
        try {
            for (Enumeration<JarEntry> es = jarfile.entries(); es.hasMoreElements(); ) {
                JarEntry je = es.nextElement();
                String filePath = je.getName();
                if (filePath.endsWith(".class")) {
                    String name = filePath.substring(0, filePath.length()-6);
                    name = name.replace('/', '.').replace('$', '.');
                    DFClass klass = this.createClass(_global, name);
                    klass.setJarPath(jarPath, filePath);
                }
            }
        } finally {
            jarfile.close();
        }
    }

    public void loadDefaultClasses()
        throws IOException, TypeNotFound {
        File homeDir = new File(System.getProperty("java.home"));
        File libDir = new File(homeDir, "lib");
        File rtFile = new File(libDir, "rt.jar");
        this.loadJarFile(rtFile.getAbsolutePath());
        OBJECT_CLASS = this.getClass("java.lang.Object");
        DFTypeSpace space = this.lookupSpace("java.lang");
        ARRAY_CLASS = new DFClass(
            space, null, _global, "java.lang._Array", OBJECT_CLASS);
        ARRAY_CLASS.addField("length", false, DFBasicType.INT);
        STRING_CLASS = this.getClass("java.lang.String");
    }
}
