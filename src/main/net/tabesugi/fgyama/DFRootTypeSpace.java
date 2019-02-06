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
    }

}
