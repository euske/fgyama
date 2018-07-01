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

    public static DFClassSpace OBJECT_CLASS = null;
    public static DFClassSpace ARRAY_CLASS = null;
    public static DFClassSpace STRING_CLASS = null;

    @Override
    public String toString() {
        return ("<DFRootTypeSpace>");
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
                    DFClassSpace klass = this.createClass(name);
                    klass.setJarPath(jarPath, filePath);
                }
            }
        } finally {
            jarfile.close();
        }
    }

    public void loadDefaultClasses()
        throws IOException, EntityNotFound {
        File homeDir = new File(System.getProperty("java.home"));
        File libDir = new File(homeDir, "lib");
        File rtFile = new File(libDir, "rt.jar");
        this.loadJarFile(rtFile.getAbsolutePath());
        OBJECT_CLASS = this.getClass("java.lang.Object");
        ARRAY_CLASS = this.getClass("java.lang.Object");
        STRING_CLASS = this.getClass("java.lang.String");
    }
}
