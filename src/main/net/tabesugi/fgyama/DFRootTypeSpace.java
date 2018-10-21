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

    private DFKlass _object = null;
    private DFKlass _class = null;
    private DFKlass _enum = null;
    private DFKlass _string = null;
    private DFKlass _array = null;

    private DFGlobalVarScope _global = new DFGlobalVarScope();

    public DFRootTypeSpace() {
        super(null, "ROOT");
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
        _object = this.getKlass("java.lang.Object");
        _class = this.getKlass("java.lang.Class");
        _enum = this.getKlass("java.lang.Enum");
        _string = this.getKlass("java.lang.String");
        _array = new DFKlass(
            "java.lang._Array", space, null, _global, _object);
        _array.addField("length", false, DFBasicType.INT);
    }

    public static DFRootTypeSpace DEFAULT = new DFRootTypeSpace();
    public static DFKlass getObjectKlass() {
        return DEFAULT._object;
    }
    public static DFKlass getClassKlass() {
        return DEFAULT._class;
    }
    public static DFKlass getEnumKlass() {
        return DEFAULT._enum;
    }
    public static DFKlass getStringKlass() {
        return DEFAULT._string;
    }
    public static DFKlass getArrayKlass() {
        return DEFAULT._array;
    }

}
