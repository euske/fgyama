//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import java.util.jar.*;
import javax.xml.stream.*;
import org.apache.bcel.*;
import org.apache.bcel.classfile.*;


//  DFJarFileKlass
//  DFKlass defined in .jar file.
//
//  Usage:
//    1. new DFJarFileKlass(finder)
//    2. setJarPath()
//
public class DFJarFileKlass extends DFClsFileKlass {

    // These fields must be set immediately after construction.
    private String _jarPath = null;
    private String _entPath = null;

    // Normal constructor.
    public DFJarFileKlass(
        String name, DFTypeSpace outerSpace, DFKlass outerKlass,
        DFTypeFinder finder) {
        super(name, outerSpace, outerKlass, finder);
    }

    // Protected constructor for a parameterized klass.
    protected DFJarFileKlass(
        DFJarFileKlass genericKlass, Map<String, DFKlass> paramTypes)
        throws TypeDuplicate {
        super(genericKlass, paramTypes);
    }

    // Parameterize the klass.
    @Override
    protected DFKlass parameterize(Map<String, DFKlass> paramTypes) {
        assert paramTypes != null;
        try {
            return new DFJarFileKlass(this, paramTypes);
        } catch (EntityDuplicate e) {
            Logger.error(
                "DFJarFileKlass.parameterize: EntityDuplicate: ",
                e.name, this);
            return this;
        }
    }

    // Set the klass code from a JAR.
    public void setJarPath(String jarPath, String entPath) {
        _jarPath = jarPath;
        _entPath = entPath;
    }

    // getClsFile():
    // Load a jarfile class before inspecting anything about the class.
    @Override
    protected JavaClass getClsFile() {
        assert _jarPath != null;
        assert _entPath != null;
        try {
            JarFile jarfile = new JarFile(_jarPath);
            try {
                JarEntry je = jarfile.getJarEntry(_entPath);
                InputStream strm = jarfile.getInputStream(je);
                return new ClassParser(strm, _entPath).parse();
            } finally {
                jarfile.close();
            }
        } catch (IOException e) {
            Logger.error(
                "DFJarFileKlass.loadJarFile: IOException",
                _jarPath+"/"+_entPath, this);
        }
        return null;
    }
}
