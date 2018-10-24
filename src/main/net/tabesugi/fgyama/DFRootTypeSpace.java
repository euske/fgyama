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
                addFile(jarPath, filePath);
            }
        } finally {
            jarfile.close();
        }
    }

    private void addFile(String jarPath, String filePath) {
        if (!filePath.endsWith(".class")) return;
        String name = filePath.substring(0, filePath.length()-6);
        String fullName = name.replace('/', '.').replace('$', '.');
        int i = fullName.lastIndexOf('.');
        List<DFTypeSpace> a = new ArrayList<DFTypeSpace>();
        int j = name.indexOf('$');
        while (0 <= j) {
            String x = name.substring(0, j);
            a.add(this.lookupSpace(x.replace('/', '.').replace('$', '.')));
            j = name.indexOf('$', j+1);
        }
        DFTypeSpace space = this.lookupSpace(fullName.substring(0, i));
        DFKlass klass = space.createKlass(_global, fullName.substring(i+1));
        DFTypeSpace[] extra = new DFTypeSpace[a.size()];
        a.toArray(extra);
        klass.setJarPath(jarPath, filePath, extra);
    }

    private void loadDefaultKlasses()
        throws IOException, TypeNotFound {
        File homeDir = new File(System.getProperty("java.home"));
        File libDir = new File(homeDir, "lib");
        File rtFile = new File(libDir, "rt.jar");
        this.loadJarFile(rtFile.getAbsolutePath());
        DFTypeSpace java_lang = this.lookupSpace("java.lang");
        _object = this.getKlass("java.lang.Object");
        _class = this.getKlass("java.lang.Class");
        _enum = this.getKlass("java.lang.Enum");
        _string = this.getKlass("java.lang.String");
        _array = java_lang.createKlass(_global, "_Array");
        _array.addField("length", false, DFBasicType.INT);
    }

    public static DFRootTypeSpace getSingleton()
        throws IOException, TypeNotFound {
        if (_default == null) {
            _default = new DFRootTypeSpace();
            _default.loadDefaultKlasses();
        }
        return _default;
    }

    private static DFRootTypeSpace _default = null;

    public static DFKlass getObjectKlass() {
        return _default._object;
    }
    public static DFKlass getClassKlass() {
        return _default._class;
    }
    public static DFKlass getEnumKlass() {
        return _default._enum;
    }
    public static DFKlass getStringKlass() {
        return _default._string;
    }
    public static DFKlass getArrayKlass() {
        return _default._array;
    }

}
