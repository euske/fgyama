//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import java.util.jar.*;
import java.nio.file.*;
import javax.xml.stream.*;
import org.apache.bcel.*;
import org.apache.bcel.classfile.*;


//  DFModuleKlass
//  DFKlass defined in .jmod file.
//
//  Usage:
//    1. new DFModuleKlass(finder)
//    2. setModulePath()
//
public class DFModuleKlass extends DFClsFileKlass {

    // These fields must be set immediately after construction.
    private Path _modPath = null;
    private Path _entPath = null;

    // Normal constructor.
    public DFModuleKlass(
        String name, DFTypeSpace outerSpace, DFKlass outerKlass,
        DFTypeFinder finder) {
        super(name, outerSpace, outerKlass, finder);
    }

    // Protected constructor for a parameterized klass.
    protected DFModuleKlass(
        DFModuleKlass genericKlass, Map<String, DFKlass> paramTypes)
        throws TypeDuplicate {
        super(genericKlass, paramTypes);
    }

    // Parameterize the klass.
    @Override
    protected DFKlass parameterize(Map<String, DFKlass> paramTypes) {
        assert paramTypes != null;
        try {
            return new DFModuleKlass(this, paramTypes);
        } catch (EntityDuplicate e) {
            Logger.error(
                "DFModuleKlass.parameterize: EntityDuplicate: ",
                e.name, this);
            return this;
        }
    }

    // Set the module path.
    public void setModulePath(Path modPath, Path entPath) {
        _modPath = modPath;
        _entPath = entPath;
    }

    // getClsFile():
    // Load a jarfile class before inspecting anything about the class.
    @Override
    protected JavaClass getClsFile() {
        assert _modPath != null;
        assert _entPath != null;
        try {
            String name = _entPath.toString();
            InputStream strm = Files.newInputStream(_modPath, StandardOpenOption.READ);
            return new ClassParser(strm, name).parse();
        } catch (IOException e) {
            Logger.error(
                "DFModuleKlass.getClsFile: IOException",
                _modPath, this);
        }
        return null;
    }
}
