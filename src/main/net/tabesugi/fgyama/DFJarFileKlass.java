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
public class DFJarFileKlass extends DFKlass {

    // These fields are available upon construction.
    private DFTypeFinder _finder;
    private boolean _loaded = false;

    // These fields must be set immediately after construction.
    private String _jarPath = null;
    private String _entPath = null;
    private JavaClass _jklass = null;

    // The following fields are available after the klass is loaded. (Stage3)
    private boolean _interface = false;
    private DFKlass _baseKlass = null;
    private DFKlass[] _baseIfaces = null;

    private Map<String, DFJarFileKlass> _id2jarklass =
        new ConsistentHashMap<String, DFJarFileKlass>();

    private List<DFMethod> _methods =
        new ArrayList<DFMethod>();

    // List of fields.
    private List<FieldRef> _fields =
        new ArrayList<FieldRef>();
    private Map<String, FieldRef> _id2field =
        new HashMap<String, FieldRef>();

    // Normal constructor.
    public DFJarFileKlass(
        String name, DFTypeSpace outerSpace, DFKlass outerKlass,
        DFTypeFinder finder) {
        super(name, outerSpace, outerKlass, null);
        _finder = new DFTypeFinder(this, finder);
    }

    // Protected constructor for a parameterized klass.
    protected DFJarFileKlass(
        DFJarFileKlass genericKlass, Map<String, DFKlass> paramTypes)
        throws TypeDuplicate {
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
        this.load();
        return _interface;
    }

    @Override
    public boolean isEnum() {
        this.load();
        return (_baseKlass != null &&
                _baseKlass.getGenericKlass() == DFBuiltinTypes.getEnumKlass());
    }

    @Override
    public DFKlass getBaseKlass() {
        this.load();
        return _baseKlass;
    }

    @Override
    public DFKlass[] getBaseIfaces() {
        this.load();
        return _baseIfaces;
    }

    @Override
    public DFMethod[] getMethods() {
        this.load();
        DFMethod[] methods = new DFMethod[_methods.size()];
        _methods.toArray(methods);
        return methods;
    }

    @Override
    public DFMethod lookupMethod(
        DFMethod.CallStyle callStyle, String id,
        DFType[] argTypes, DFType returnType)
        throws MethodNotFound {
        this.load();
        return super.lookupMethod(callStyle, id, argTypes, returnType);
    }

    @Override
    public DFMethod createFallbackMethod(
        DFMethod.CallStyle callStyle, String id,
        DFType[] argTypes, DFType returnType) {
        DFMethod method = super.createFallbackMethod(
            callStyle, id, argTypes, returnType);
        this.addMethod(method);
        return method;
    }

    private FieldRef addField(
        DFType type, String id, boolean isStatic) {
        return this.addField(new FieldRef(type, id, isStatic));
    }

    private FieldRef addField(FieldRef ref) {
        _fields.add(ref);
        _id2field.put(ref.getName(), ref);
        return ref;
    }

    @Override
    public FieldRef[] getFields() {
        this.load();
        FieldRef[] fields = new FieldRef[_fields.size()];
        _fields.toArray(fields);
        return fields;
    }

    @Override
    public FieldRef getField(String id) {
        this.load();
        FieldRef ref = _id2field.get(id);
        if (ref != null) return ref;
        return super.getField(id);
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

    @Override
    public DFKlass getKlass(String id) {
        this.loadJarFile();
        return super.getKlass(id);
    }

    @Override
    public DFKlass getReifiedKlass(DFKlass[] argTypes) {
        this.loadJarFile();
        return super.getReifiedKlass(argTypes);
    }

    @Override
    public boolean isResolved() {
        this.loadJarFile();
        return super.isResolved();
    }

    // for loading nested klasses.

    protected DFJarFileKlass addInnerKlass(String id, DFJarFileKlass klass)
        throws TypeDuplicate {
        super.addKlass(id, klass);
        assert id.indexOf('.') < 0;
        _id2jarklass.put(id, klass);
        return klass;
    }

    protected DFJarFileKlass getInnerKlass(String id) {
        assert id.indexOf('.') < 0;
        return _id2jarklass.get(id);
    }

    // loadJarFile():
    // Load a jarfile class before inspecting anything about the class.
    private void loadJarFile() {
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
                "DFJarFileKlass.loadJarFile: IOException",
                _jarPath+"/"+_entPath, this);
            return;
        }

        String sig = Utils.getJKlassSignature(_jklass.getAttributes());
        if (sig != null) {
            JNITypeParser parser = new JNITypeParser(sig);
            JNITypeParser.TypeSlot[] slots = parser.getTypeSlots();
            if (slots != null) {
                ConsistentHashMap<String, DFKlass> typeSlots =
                    new ConsistentHashMap<String, DFKlass>();
                for (JNITypeParser.TypeSlot slot : slots) {
                    DFMapKlass klass = new DFMapKlass(
                        slot.id, this, this, slot.sig, _finder);
                    typeSlots.put(slot.id, klass);
                }
                this.setTypeSlots(typeSlots);
            }
        }
    }

    protected void load() {
        this.loadJarFile();
        if (!_loaded) {
            _loaded = true;
            //Logger.info("build:", this);
            this.build();
        }
    }

    protected void build() {
        assert _jklass != null;
        //Logger.info("DFJarFileKlass.build:", this);
        _interface = _jklass.isInterface();

        String sig = Utils.getJKlassSignature(_jklass.getAttributes());
        if (this == DFBuiltinTypes.getObjectKlass()) {
            _baseKlass = null;

        } else if (sig != null) {
            _baseKlass = DFBuiltinTypes.getObjectKlass();
            //Logger.info("jklass:", this, sig);
            JNITypeParser parser = new JNITypeParser(sig);
            parser.getTypeSlots();
            try {
                _baseKlass = parser.resolveType(_finder).toKlass();
            } catch (TypeNotFound e) {
                Logger.error(
                    "DFJarFileKlass.build: TypeNotFound (baseKlass)",
                    e.name, sig, this);
            }
            List<DFKlass> ifaces = new ArrayList<DFKlass>();
            for (;;) {
                DFType iface = DFBuiltinTypes.getObjectKlass();
                try {
                    iface = parser.resolveType(_finder);
                } catch (TypeNotFound e) {
                    Logger.error(
                        "DFJarFileKlass.build: TypeNotFound (iface)",
                        e.name, sig, this);
                }
                if (iface == null) break;
                ifaces.add(iface.toKlass());
            }
            _baseIfaces = new DFKlass[ifaces.size()];
            ifaces.toArray(_baseIfaces);

        } else {
            _baseKlass = DFBuiltinTypes.getObjectKlass();
            String superClass = _jklass.getSuperclassName();
            if (superClass != null && !superClass.equals(_jklass.getClassName())) {
                try {
                    _baseKlass = _finder.resolveKlass(superClass);
                } catch (TypeNotFound e) {
                    Logger.error(
                        "DFJarFileKlass.build: TypeNotFound (baseKlass)",
                        e.name, this);
                }
            }
            String[] ifaces = _jklass.getInterfaceNames();
            if (ifaces != null) {
                _baseIfaces = new DFKlass[ifaces.length];
                for (int i = 0; i < ifaces.length; i++) {
                    DFKlass iface = DFBuiltinTypes.getObjectKlass();
                    try {
                        iface = _finder.resolveKlass(ifaces[i]);
                    } catch (TypeNotFound e) {
                        Logger.error(
                            "DFJarFileKlass.build: TypeNotFound (iface)",
                            e.name, this);
                    }
                    _baseIfaces[i] = iface;
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
                    parser.getTypeSlots();
                    type = parser.resolveType(_finder);
                } else {
                    type = _finder.resolve(fld.getType());
                }
            } catch (TypeNotFound e) {
                Logger.error(
                    "DFJarFileKlass.build: TypeNotFound (field)",
                    e.name, sig, this);
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
            this.addMethod(method);
        }
    }

    private void addMethod(DFMethod method) {
        _methods.add(method);
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
