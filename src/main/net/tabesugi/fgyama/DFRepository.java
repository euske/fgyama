//  DFRepository
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.apache.bcel.*;
import org.apache.bcel.classfile.*;


//  DFRepository
//
public class DFRepository {

    private static Map<String, JavaClass> _name2jklass =
	new HashMap<String, JavaClass>();
    private static Map<String, String> _name2jar =
	new HashMap<String, String>();

    private static String class2file(String name) {
	return name.replace('.', '/')+".class";
    }

    private static String file2class(String name) {
	if (name.endsWith(".class")) {
	    return name.substring(0, name.length()-6).replace('/', '.');
	} else {
	    return null;
	}
    }

    public static void loadJarFile(String jarpath)
	throws IOException {
        Utils.logit("Loading: "+jarpath);
	JarFile jarfile = new JarFile(jarpath);
	try {
	    for (Enumeration<JarEntry> es = jarfile.entries(); es.hasMoreElements(); ) {
		JarEntry je = es.nextElement();
		String name = file2class(je.getName());
		if (name != null) {
		    _name2jar.put(name, jarpath);
		}
	    }
	} finally {
	    jarfile.close();
	}
    }

    public static JavaClass loadJavaClass(String name) {
	JavaClass jklass = _name2jklass.get(name);
	if (jklass != null) {
	    return jklass;
	}
	String jarpath = _name2jar.get(name);
	if (jarpath == null) {
	    return null;
	}
	try {
	    JarFile jarfile = new JarFile(jarpath);
	    try {
		String path = class2file(name);
		JarEntry je = jarfile.getJarEntry(path);
		InputStream strm = jarfile.getInputStream(je);
		jklass = new ClassParser(strm, path).parse();
		_name2jklass.put(name, jklass);
		Utils.logit("Loaded: "+jklass.getClassName());
	    } finally {
		jarfile.close();
	    }
	} catch (IOException e) {
	    Utils.logit("Error: Corrupt: "+name+" ("+jarpath+")");
	    return null;
	}
	return jklass;
    }

    public static void loadBuiltinClasses(DFTypeSpace typeSpace) {
	for (String name : _name2jar.keySet()) {
            int i = name.lastIndexOf('.');
            assert(0 <= i);
	    if (name.substring(0, i).equals("java.lang")) {
                JavaClass jklass = loadJavaClass(name);
                assert(jklass != null);
                typeSpace.loadRootClass(jklass);
	    }
	}
    }
}
