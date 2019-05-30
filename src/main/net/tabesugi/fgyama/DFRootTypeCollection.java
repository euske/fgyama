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


//  DFRootTypeCollection
//
public class DFRootTypeCollection extends DFTypeCollection {

    private DFTypeFinder _finder;

    public DFRootTypeCollection() {
        super(null, "ROOT");
        _finder = new DFTypeFinder(this);
    }

    public DFTypeCollection lookupSpace(PackageDeclaration pkgDecl) {
        if (pkgDecl == null) {
            return this;
        } else {
            return this.lookupSpace(pkgDecl.getName());
        }
    }

    public DFTypeCollection lookupSpace(Name name) {
        return this.lookupSpace(name.getFullyQualifiedName());
    }

    @Override
    public String toString() {
        return ("<DFRootTypeCollection>");
    }

    @Override
    public String getSpaceName() {
        return "";
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
        String entPath = jarEntry.getName();
        if (!entPath.endsWith(".class")) return;
        String s = entPath.substring(0, entPath.length()-6);
        int i = s.indexOf('$');
        String fullName = s.substring(0, (0 <= i)? i : s.length());
        int j = fullName.lastIndexOf('/');
	String spaceName = fullName.substring(0, j).replace('/', '.');
	String klassName = fullName.substring(j+1);
        DFTypeCollection space = this.lookupSpace(spaceName);
        // Create a top-level klass.
        DFKlass klass = space.createKlass(null, null, klassName);
        DFTypeFinder finder = _finder;
        while (0 <= i) {
            // Create inner klasses.
            // Each inner klass is a child of the previous klass in a path.
            int i0 = i+1;
            i = s.indexOf('$', i0);
            String name = s.substring(i0, (0 <= i)? i : s.length());
            space = klass.getKlassSpace();
            klass = space.createKlass(klass, klass.getKlassScope(), name);
            finder = finder.extend(space);
        }
        klass.setBaseFinder(finder);
        klass.setJarPath(jarPath, entPath);
        InputStream strm = jarFile.getInputStream(jarEntry);
        JavaClass jklass = new ClassParser(strm, entPath).parse();
        String sig = Utils.getJKlassSignature(jklass.getAttributes());
        if (sig != null) {
            klass.setMapTypes(sig);
        }
    }
}
