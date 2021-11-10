//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import java.util.jar.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
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
        String jarPath = jarFile.getName();
        try {
            for (Enumeration<JarEntry> es = jarFile.entries(); es.hasMoreElements(); ) {
                JarEntry jarEntry = es.nextElement();
                String entPath = jarEntry.getName();
                if (entPath.endsWith(".class")) {
                    try {
                        String entName = entPath.substring(0, entPath.length()-6);
                        DFJarFileKlass klass = this.addJarFileKlass(entName);
                        klass.setJarPath(jarPath, entPath);
                    } catch (EntityDuplicate e) {
                        Logger.info("loadJarFile: duplicate: ", e.name, jarFile, jarEntry);
                    }
                }
            }
        } finally {
            jarFile.close();
        }
    }

    private DFJarFileKlass addJarFileKlass(String entName)
        throws IOException, EntityDuplicate {
        int i = entName.indexOf('$');
        String fullName = entName.substring(0, (0 <= i)? i : entName.length());
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
            i = entName.indexOf('$', i0);
            String name = entName.substring(i0, (0 <= i)? i : entName.length());
            DFJarFileKlass child = (DFJarFileKlass)klass.getInnerKlass(name);
            if (child == null) {
                child = new DFJarFileKlass(name, klass, klass, finder);
                klass.addInnerKlass(name, child);
            }
            klass = child;
            finder = new DFTypeFinder(klass, finder);
        }
        return klass;
    }

    public void loadModule(Path modPath)
        throws IOException {
        Logger.info("Loading:", modPath);
        Files.walkFileTree(modPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(
                    Path path, BasicFileAttributes attrs) {
                    int n = path.getNameCount();
                    assert 3 <= n;
                    Path subpath = path.subpath(2, n);
                    String entPath = subpath.toString();
                    if (entPath.endsWith(".class") &&
                        !entPath.equals("module-info.class")) {
                        try {
                            String entName = entPath.substring(0, entPath.length()-6);
                            DFModuleKlass klass = addModuleKlass(entName);
                            klass.setModulePath(path, subpath);
                        } catch (EntityDuplicate e) {
                            Logger.info("loadModule: duplicate: ", e.name, path);
                        } catch (IOException e) {
                            Logger.error("loadModule: IOException: ", path);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
    }

    private DFModuleKlass addModuleKlass(String entName)
        throws IOException, EntityDuplicate {
        int i = entName.indexOf('$');
        String fullName = entName.substring(0, (0 <= i)? i : entName.length());
        int j = fullName.lastIndexOf('/');
        String spaceName = fullName.substring(0, j).replace('/', '.');
        String klassName = fullName.substring(j+1);
        DFTypeSpace space = this.addSubSpace(spaceName);
        // Create a top-level klass.
        DFTypeFinder finder = _finder;
        DFModuleKlass klass = (DFModuleKlass)space.getKlass(klassName);
        if (klass == null) {
            klass = new DFModuleKlass(klassName, space, null, finder);
            space.addKlass(klassName, klass);
            finder = new DFTypeFinder(klass, finder);
        }
        while (0 <= i) {
            // Create inner klasses.
            // Each inner klass is a child of the previous klass in a path.
            int i0 = i+1;
            i = entName.indexOf('$', i0);
            String name = entName.substring(i0, (0 <= i)? i : entName.length());
            DFModuleKlass child = (DFModuleKlass)klass.getInnerKlass(name);
            if (child == null) {
                child = new DFModuleKlass(name, klass, klass, finder);
                klass.addInnerKlass(name, child);
            }
            klass = child;
            finder = new DFTypeFinder(klass, finder);
        }
        return klass;
    }

}
