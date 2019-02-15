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

    private DFTypeFinder _finder;

    public DFRootTypeSpace() {
        super(null, "ROOT");
        _finder = new DFTypeFinder(this);
    }

    @Override
    public String toString() {
        return ("<DFRootTypeSpace>");
    }

    public void loadJarFile(String jarPath)
        throws IOException {
        Logger.info("Loading:", jarPath);
        JarFile jarFile = new JarFile(jarPath);
        try {
            for (Enumeration<JarEntry> es = jarFile.entries(); es.hasMoreElements(); ) {
                JarEntry jarEntry = es.nextElement();
                addFile(jarFile, jarEntry);
            }
        } finally {
            jarFile.close();
        }
    }

    private void addFile(JarFile jarFile, JarEntry jarEntry)
        throws IOException {
        String jarPath = jarFile.getName();
        String filePath = jarEntry.getName();
        if (!filePath.endsWith(".class")) return;
        String s = filePath.substring(0, filePath.length()-6);
        int i = s.indexOf('$');
        String fullName = s.substring(0, (0 <= i)? i : s.length());
        int j = fullName.lastIndexOf('/');
        DFTypeSpace space = this.lookupSpace(fullName.substring(0, j).replace('/', '.'));
        DFKlass klass = space.createKlass(null, null, fullName.substring(j+1));
        klass.setBaseFinder(_finder);
        while (0 <= i) {
            int i0 = i+1;
            i = s.indexOf('$', i0);
            String name = s.substring(i0, (0 <= i)? i : s.length());
            space = klass.getKlassSpace();
            klass = space.createKlass(klass, klass.getKlassScope(), name);
            klass.setBaseFinder(_finder);
        }
        klass.setJarPath(jarPath, filePath);
        InputStream strm = jarFile.getInputStream(jarEntry);
        JavaClass jklass = new ClassParser(strm, filePath).parse();
        String sig = Utils.getJKlassSignature(jklass.getAttributes());
        if (sig != null) {
            klass.setMapTypes(sig);
        }
    }
}
