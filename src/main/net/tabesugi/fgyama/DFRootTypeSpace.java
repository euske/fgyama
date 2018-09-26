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

    public static DFKlass OBJECT_KLASS = null;
    public static DFKlass ARRAY_KLASS = null;
    public static DFKlass STRING_KLASS = null;
    public static DFKlass ENUM_KLASS = null;
    public static DFKlass TYPE_KLASS = null;

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
                    DFKlass klass = this.createKlass(_global, name);
                    klass.setJarPath(jarPath, filePath);
                }
            }
        } finally {
            jarfile.close();
        }
    }

    public void loadDefaultKlasses()
        throws IOException, TypeNotFound {
        File homeDir = new File(System.getProperty("java.home"));
        File libDir = new File(homeDir, "lib");
        File rtFile = new File(libDir, "rt.jar");
        this.loadJarFile(rtFile.getAbsolutePath());
        DFTypeSpace space = this.lookupSpace("java.lang");
        OBJECT_KLASS = this.getKlass("java.lang.Object");
        ARRAY_KLASS = new DFKlass(
            "java.lang._Array", space, null, _global, OBJECT_KLASS);
        ARRAY_KLASS.addField("length", false, DFBasicType.INT);
        STRING_KLASS = this.getKlass("java.lang.String");
        ENUM_KLASS =  new DFKlass(
            "java.lang._Enum", space, null, _global, OBJECT_KLASS);
        TYPE_KLASS =  new DFKlass(
            "java.lang._Class", space, null, _global, OBJECT_KLASS);
    }
}
