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
//
public class DFJarFileKlass extends DFKlass {

    // These fields are set immediately after construction.
    private String _jarPath = null;
    private String _entPath = null;

    // The following fields are available after the klass is loaded. (Stage3)
    private boolean _interface = false;
    private DFKlass _baseKlass = null;
    private DFKlass[] _baseIfaces = null;


    public DFJarFileKlass(
        String name, DFTypeSpace outerSpace, DFVarScope outerScope,
        DFJarFileKlass outerKlass) {
	super(name, outerSpace, outerScope, outerKlass);
    }

    protected DFJarFileKlass(
        DFJarFileKlass genericKlass, DFKlass[] paramTypes) {
        super(genericKlass, paramTypes);

        _jarPath = genericKlass._jarPath;
        _entPath = genericKlass._entPath;
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

    // Set the map types from a JAR.
    public void setMapTypes(String sig) {
        DFMapType[] mapTypes = JNITypeParser.getMapTypes(sig, this);
	if (mapTypes == null) return;
        this.setMapTypes(mapTypes);
    }

    // Constructor for a parameterized klass.
    @Override
    protected DFKlass parameterize(DFKlass[] paramTypes)
	throws InvalidSyntax {
        assert paramTypes != null;
        return new DFJarFileKlass(this, paramTypes);
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

    @Override
    public boolean isInterface() {
        assert this.isDefined();
        return _interface;
    }

    @Override
    public boolean isEnum() {
        assert this.isDefined();
        return (_baseKlass != null &&
		_baseKlass.getGenericKlass() == DFBuiltinTypes.getEnumKlass());
    }

    @Override
    public DFKlass getBaseKlass() {
        assert this.isDefined();
	if (_baseKlass != null) return _baseKlass;
        return super.getBaseKlass();
    }

    @Override
    public DFKlass[] getBaseIfaces() {
        assert this.isDefined();
        return _baseIfaces;
    }

    @Override
    public DFMethod findMethod(
        DFMethod.CallStyle callStyle, String id, DFType[] argTypes) {
        assert this.isDefined();
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

    @Override
    public void build()
        throws InvalidSyntax {
        super.build();
        DFTypeFinder finder = this.getFinder();
        assert finder != null;
        assert _jarPath != null;
        if (this.isGeneric()) {
            // a generic class is only referred to, but not built.
        } else {
            this.initScope();
            try {
                JarFile jarfile = new JarFile(_jarPath);
                try {
                    JarEntry je = jarfile.getJarEntry(_entPath);
                    InputStream strm = jarfile.getInputStream(je);
                    JavaClass jklass = new ClassParser(strm, _entPath).parse();
                    this.buildMembersFromJKlass(finder, jklass);
                } finally {
                    jarfile.close();
                }
            } catch (IOException e) {
                Logger.error(
                    "DFKlass.load: IOException",
                    this, _jarPath+"/"+_entPath);
            }
        }
    }

    private void buildMembersFromJKlass(DFTypeFinder finder, JavaClass jklass)
        throws InvalidSyntax {
        //Logger.info("DFKlass.buildMembersFromJKlass:", this, finder);
        _interface = jklass.isInterface();

        // Load base klasses/interfaces.
        String sig = Utils.getJKlassSignature(jklass.getAttributes());
	if (this == DFBuiltinTypes.getObjectKlass()) {
	    ;
	} else if (sig != null) {
            //Logger.info("jklass:", this, sig);
	    _baseKlass = DFBuiltinTypes.getObjectKlass();
	    JNITypeParser parser = new JNITypeParser(sig);
	    try {
		_baseKlass = parser.resolveType(finder).toKlass();
	    } catch (TypeNotFound e) {
		Logger.error(
                    "DFKlass.buildMembersFromJKlass: TypeNotFound (baseKlass)",
                    this, e.name, sig);
	    }
	    _baseKlass.load();
	    List<DFKlass> ifaces = new ArrayList<DFKlass>();
	    for (;;) {
		DFType iface = DFBuiltinTypes.getObjectKlass();
		try {
		    iface = parser.resolveType(finder);
		} catch (TypeNotFound e) {
		    Logger.error(
                        "DFKlass.buildMembersFromJKlass: TypeNotFound (iface)",
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
	    String superClass = jklass.getSuperclassName();
	    if (superClass != null && !superClass.equals(jklass.getClassName())) {
		try {
		    _baseKlass = finder.lookupType(superClass).toKlass();
		} catch (TypeNotFound e) {
		    Logger.error(
                        "DFKlass.buildMembersFromJKlass: TypeNotFound (baseKlass)",
                        this, e.name);
		}
	    }
	    _baseKlass.load();
	    String[] ifaces = jklass.getInterfaceNames();
	    if (ifaces != null) {
                _baseIfaces = new DFKlass[ifaces.length];
		for (int i = 0; i < ifaces.length; i++) {
		    DFKlass iface = DFBuiltinTypes.getObjectKlass();
		    try {
			iface = finder.lookupType(ifaces[i]).toKlass();
		    } catch (TypeNotFound e) {
			Logger.error(
                            "DFKlass.buildMembersFromJKlass: TypeNotFound (iface)",
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
        for (Field fld : jklass.getFields()) {
            if (fld.isPrivate()) continue;
            sig = Utils.getJKlassSignature(fld.getAttributes());
	    DFType type;
	    try {
		if (sig != null) {
		    //Logger.info("fld:", fld.getName(), sig);
		    JNITypeParser parser = new JNITypeParser(sig);
		    type = parser.resolveType(finder);
		} else {
		    type = finder.resolve(fld.getType());
		}
	    } catch (TypeNotFound e) {
		Logger.error(
                    "DFKlass.buildMembersFromJKlass: TypeNotFound (field)",
                    this, e.name, sig);
		type = DFUnknownType.UNKNOWN;
	    }
	    this.addField(fld.getName(), fld.isStatic(), type);
        }
        // Define methods.
        for (Method meth : jklass.getMethods()) {
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
            DFMethod method = new DFMethod(
                this, callStyle, meth.isAbstract(),
		id, name, null);
            method.setFinder(finder);
            DFFunctionType funcType;
            sig = Utils.getJKlassSignature(meth.getAttributes());
	    if (sig != null) {
                //Logger.info("meth:", meth.getName(), sig);
                DFMapType[] mapTypes = JNITypeParser.getMapTypes(sig, method);
		method.setMapTypes(mapTypes);
                if (method.isGeneric()) continue;
		JNITypeParser parser = new JNITypeParser(sig);
		try {
		    funcType = (DFFunctionType)parser.resolveType(method.getFinder());
		} catch (TypeNotFound e) {
		    Logger.error(
                        "DFKlass.buildMembersFromJKlass: TypeNotFound (method)",
                        this, e.name, sig);
		    continue;
		}
	    } else {
		org.apache.bcel.generic.Type[] args = meth.getArgumentTypes();
		DFType[] argTypes = new DFType[args.length];
		for (int i = 0; i < args.length; i++) {
		    argTypes[i] = finder.resolveSafe(args[i]);
		}
		DFType returnType = finder.resolveSafe(meth.getReturnType());
                funcType = new DFFunctionType(argTypes, returnType);
	    }
            // For varargs methods, the last argument is declared as an array
            // so no special treatment is required here.
            funcType.setVarArgs(meth.isVarArgs());
            ExceptionTable excTable = meth.getExceptionTable();
            if (excTable != null) {
                String[] excNames = excTable.getExceptionNames();
                DFKlass[] exceptions = new DFKlass[excNames.length];
                for (int i = 0; i < excNames.length; i++) {
		    DFType type;
		    try {
			type = finder.lookupType(excNames[i]);
		    } catch (TypeNotFound e) {
			Logger.error(
                            "DFKlass.buildMembersFromJKlass: TypeNotFound (exception)",
                            this, e.name);
			type = DFUnknownType.UNKNOWN;
		    }
		    exceptions[i] = type.toKlass();
                }
                funcType.setExceptions(exceptions);
            }
            method.setFuncType(funcType);
            this.addMethod(method, null);
        }
    }
}
