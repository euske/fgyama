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
//    3. load()
//
public class DFJarFileKlass extends DFKlass {

    // These fields are available upon construction.
    private DFTypeFinder _finder;

    // These fields must be set immediately after construction.
    private String _jarPath = null;
    private String _entPath = null;
    private JavaClass _jklass = null;

    // The following fields are available after the klass is loaded. (Stage3)
    private boolean _interface = false;
    private DFKlass _baseKlass = null;
    private DFKlass[] _baseIfaces = null;

    // Normal constructor.
    public DFJarFileKlass(
        String name, DFTypeSpace outerSpace,
        DFTypeFinder finder) {
        super(name, outerSpace);
        _finder = new DFTypeFinder(this, finder);
    }

    // Protected constructor for a parameterized klass.
    protected DFJarFileKlass(
        DFJarFileKlass genericKlass, Map<String, DFKlass> paramTypes) {
        super(genericKlass, paramTypes);

        _finder = new DFTypeFinder(this, genericKlass._finder);
        _jklass = genericKlass._jklass;
        // XXX In case of a .jar class, refer to the same inner classes.
        for (DFKlass inklass : genericKlass.getInnerKlasses()) {
            this.addKlass(inklass.getName(), inklass);
        }
    }

    // Set the klass code from a JAR.
    public void setJarPath(String jarPath, String entPath) {
        _jarPath = jarPath;
        _entPath = entPath;
    }

    @Override
    public boolean isInterface() {
        assert this.isLoaded();
        return _interface;
    }

    @Override
    public boolean isEnum() {
        assert this.isLoaded();
        return (_baseKlass != null &&
                _baseKlass.getGenericKlass() == DFBuiltinTypes.getEnumKlass());
    }

    @Override
    public DFKlass getBaseKlass() {
        assert this.isLoaded();
        return _baseKlass;
    }

    @Override
    public DFKlass[] getBaseIfaces() {
        assert this.isLoaded();
        return _baseIfaces;
    }

    @Override
    public DFMethod findMethod(
        DFMethod.CallStyle callStyle, String id, DFType[] argTypes) {
        assert this.isLoaded();
        DFMethod method = super.findMethod(callStyle, id, argTypes);
        if (method != null) return method;
        if (_baseKlass != null) {
            method = _baseKlass.findMethod(callStyle, id, argTypes);
            if (method != null) return method;
        }
        if (_baseIfaces != null) {
            for (DFKlass iface : _baseIfaces) {
                method = iface.findMethod(callStyle, id, argTypes);
                if (method != null) return method;
            }
        }
        return null;
    }

    // Parameterize the klass.
    @Override
    protected DFKlass parameterize(Map<String, DFKlass> paramTypes)
        throws InvalidSyntax {
        assert paramTypes != null;
        return new DFJarFileKlass(this, paramTypes);
    }

    @Override
    public DFKlass getConcreteKlass(DFKlass[] argTypes)
        throws InvalidSyntax {
        this.preload();
        return super.getConcreteKlass(argTypes);
    }

    private void preload() throws InvalidSyntax {
        if (_jklass != null) return;

        assert this.getGenericKlass() == null;
        assert _jarPath != null;
        assert _entPath != null;
        try {
            JarFile jarfile = new JarFile(_jarPath);
            try {
                JarEntry je = jarfile.getJarEntry(_entPath);
                InputStream strm = jarfile.getInputStream(je);
                _jklass = new ClassParser(strm, _entPath).parse();
            } finally {
                jarfile.close();
            }
        } catch (IOException e) {
            Logger.error(
                "DFJarFileKlass.preload: IOException",
                this, _jarPath+"/"+_entPath);
            return;
        }

        String sig = Utils.getJKlassSignature(_jklass.getAttributes());
        if (sig != null) {
            JNITypeParser parser = new JNITypeParser(sig);
            DFMapType[] mapTypes = parser.createMapTypes(this, _finder);
            if (mapTypes != null) {
                this.setMapTypes(mapTypes);
            }
        }
    }

    @Override
    protected void build() throws InvalidSyntax {
        this.preload();
        assert _jklass != null;
        //Logger.info("DFJarFileKlass.build:", this);
        _interface = _jklass.isInterface();

        // Load base klasses/interfaces.
        String sig = Utils.getJKlassSignature(_jklass.getAttributes());
        if (this == DFBuiltinTypes.getObjectKlass()) {
            _baseKlass = null;

        } else if (sig != null) {
            _baseKlass = DFBuiltinTypes.getObjectKlass();
            //Logger.info("jklass:", this, sig);
            JNITypeParser parser = new JNITypeParser(sig);
            parser.skipMapTypes();
            try {
                _baseKlass = parser.resolveType(_finder).toKlass();
            } catch (TypeNotFound e) {
                Logger.error(
                    "DFJarFileKlass.build: TypeNotFound (baseKlass)",
                    this, e.name, sig);
            }
            _baseKlass.load();
            List<DFKlass> ifaces = new ArrayList<DFKlass>();
            for (;;) {
                DFType iface = DFBuiltinTypes.getObjectKlass();
                try {
                    iface = parser.resolveType(_finder);
                } catch (TypeNotFound e) {
                    Logger.error(
                        "DFJarFileKlass.build: TypeNotFound (iface)",
                        this, e.name, sig);
                }
                if (iface == null) break;
                ifaces.add(iface.toKlass());
            }
            _baseIfaces = new DFKlass[ifaces.size()];
            ifaces.toArray(_baseIfaces);
            for (DFKlass iface : _baseIfaces) {
                iface.load();
            }

        } else {
            _baseKlass = DFBuiltinTypes.getObjectKlass();
            String superClass = _jklass.getSuperclassName();
            if (superClass != null && !superClass.equals(_jklass.getClassName())) {
                try {
                    _baseKlass = _finder.lookupKlass(superClass);
                } catch (TypeNotFound e) {
                    Logger.error(
                        "DFJarFileKlass.build: TypeNotFound (baseKlass)",
                        this, e.name);
                }
            }
            _baseKlass.load();
            String[] ifaces = _jklass.getInterfaceNames();
            if (ifaces != null) {
                _baseIfaces = new DFKlass[ifaces.length];
                for (int i = 0; i < ifaces.length; i++) {
                    DFKlass iface = DFBuiltinTypes.getObjectKlass();
                    try {
                        iface = _finder.lookupKlass(ifaces[i]);
                    } catch (TypeNotFound e) {
                        Logger.error(
                            "DFJarFileKlass.build: TypeNotFound (iface)",
                            this, e.name);
                    }
                    _baseIfaces[i] = iface;
                }
                for (DFKlass iface : _baseIfaces) {
                    iface.load();
                }
            }
        }

        // Define fields.
        for (Field fld : _jklass.getFields()) {
            if (fld.isPrivate()) continue;
            sig = Utils.getJKlassSignature(fld.getAttributes());
            DFType type;
            try {
                if (sig != null) {
                    //Logger.info("fld:", fld.getName(), sig);
                    JNITypeParser parser = new JNITypeParser(sig);
                    parser.skipMapTypes();
                    type = parser.resolveType(_finder);
                } else {
                    type = _finder.resolve(fld.getType());
                }
            } catch (TypeNotFound e) {
                Logger.error(
                    "DFJarFileKlass.build: TypeNotFound (field)",
                    this, e.name, sig);
                type = DFUnknownType.UNKNOWN;
            }
            this.addField(type, fld.getName(), fld.isStatic());
        }

        // Define methods.
        for (Method meth : _jklass.getMethods()) {
            if (meth.isPrivate()) continue;
            String name = meth.getName();
            DFMethod.CallStyle callStyle;
            if (meth.getName().equals("<init>")) {
                callStyle = DFMethod.CallStyle.Constructor;
            } else if (meth.isStatic()) {
                callStyle = DFMethod.CallStyle.StaticMethod;
            } else {
                callStyle = DFMethod.CallStyle.InstanceMethod;
            }
            String id = name+":"+meth.getNameIndex();
            DFMethod method = new DFJarFileMethod(
                this, callStyle, meth.isAbstract(),
                id, name, meth, _finder);
            this.addMethod(method, null);
        }
    }

    @Override
    protected void dumpContents(PrintStream out, String indent) {
        super.dumpContents(out, indent);
        if (_baseKlass != null) {
            _baseKlass.dump(out, indent);
        }
        if (_baseIfaces != null) {
            for (DFKlass iface : _baseIfaces) {
                if (iface != null) {
                    iface.dump(out, indent);
                }
            }
        }
    }
}
