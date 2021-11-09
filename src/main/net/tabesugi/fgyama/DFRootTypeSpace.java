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


//  DFRootTypeSpace
//
public class DFRootTypeSpace extends DFTypeSpace {

    private DFTypeFinder _finder;

    public DFRootTypeSpace() {
        super("ROOT", null);
        _finder = new DFTypeFinder(this);
    }

    public DFTypeSpace getSubSpace(PackageDeclaration pkgDecl) {
        if (pkgDecl == null) {
            return this;
        } else {
            return this.getSubSpace(pkgDecl.getName());
        }
    }

    public DFTypeSpace addSubSpace(PackageDeclaration pkgDecl) {
        if (pkgDecl == null) {
            return this;
        } else {
            return this.addSubSpace(pkgDecl.getName());
        }
    }

    public DFKlass getRootKlass(Name name) {
        if (name.isSimpleName()) {
            return this.getKlass((SimpleName)name);
        } else {
            QualifiedName qname = (QualifiedName)name;
            DFTypeSpace space = this.getSubSpace(qname.getQualifier());
            if (space == null) return null;
            return space.getKlass(qname.getName());
        }
    }

    @Override
    public String toString() {
        return ("<DFRootTypeSpace>");
    }

    @Override
    public String getSpaceName() {
        return "";
    }

    public void loadJarFile(File file)
        throws IOException {
        Logger.info("Loading:", file);
        JarFile jarFile = new JarFile(file);
        try {
            for (Enumeration<JarEntry> es = jarFile.entries(); es.hasMoreElements(); ) {
                JarEntry jarEntry = es.nextElement();
                try {
                    addFile(jarFile, jarEntry);
                } catch (EntityDuplicate e) {
                    Logger.info("loadJarFile: duplicate: ", e.name, jarFile, jarEntry);
                }
            }
        } finally {
            jarFile.close();
        }
    }

    private void addFile(JarFile jarFile, JarEntry jarEntry)
        throws IOException, EntityDuplicate {
        String jarPath = jarFile.getName();
        String entPath = jarEntry.getName();
        if (!entPath.endsWith(".class")) return;
        String s = entPath.substring(0, entPath.length()-6);
        int i = s.indexOf('$');
        String fullName = s.substring(0, (0 <= i)? i : s.length());
        int j = fullName.lastIndexOf('/');
        String spaceName = fullName.substring(0, j).replace('/', '.');
        String klassName = fullName.substring(j+1);
        DFTypeSpace space = this.addSubSpace(spaceName);
        // Create a top-level klass.
        DFTypeFinder finder = _finder;
        DFJarFileKlass klass = (DFJarFileKlass)space.getKlass(klassName);
        if (klass == null) {
            klass = new DFJarFileKlass(klassName, space, null, finder);
            space.addKlass(klassName, klass);
            finder = new DFTypeFinder(klass, finder);
        }
        while (0 <= i) {
            // Create inner klasses.
            // Each inner klass is a child of the previous klass in a path.
            int i0 = i+1;
            i = s.indexOf('$', i0);
            String name = s.substring(i0, (0 <= i)? i : s.length());
            DFJarFileKlass child = (DFJarFileKlass)klass.getInnerKlass(name);
            if (child == null) {
                child = new DFJarFileKlass(name, klass, klass, finder);
                klass.addInnerKlass(name, child);
            }
            klass = child;
            finder = new DFTypeFinder(klass, finder);
        }
        klass.setJarPath(jarPath, entPath);
    }
}
