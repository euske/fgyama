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

    private Map<String, DFVarRef> _id2ref =
        new HashMap<String, DFVarRef>();

    public DFRootTypeSpace() {
        super("ROOT");
    }

    @Override
    public String toString() {
        return ("<DFRootTypeSpace>");
    }

    public DFVarRef getArrayRef(DFType type) {
        DFVarRef ref;
        if (type instanceof DFArrayType) {
            DFType elemType = ((DFArrayType)type).getElemType();
	    String id = "%:"+elemType.getName();
	    ref = _id2ref.get(id);
	    if (ref == null) {
		ref = new DFVarRef(null, id, elemType);
		_id2ref.put(id, ref);
	    }
        } else {
	    String id = "%:?";
	    ref = _id2ref.get(id);
	    if (ref == null) {
		ref = new DFVarRef(null, id, null);
		_id2ref.put(id, ref);
	    }
        }
        return ref;
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
                    DFClassSpace klass = this.createClass(null, name);
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
        ARRAY_CLASS = new DFClassSpace(space, null, null, "java.lang._Array", OBJECT_CLASS);
        ARRAY_CLASS.addField("length", false, DFBasicType.INT);
        STRING_CLASS = this.getClass("java.lang.String");
    }
}
