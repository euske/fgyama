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


//  DFKlass
//
public class DFKlass implements DFType, Comparable<DFKlass> {

    // These fields are available upon construction.
    private String _name;
    private DFTypeSpace _outerSpace;
    private DFKlass _outerKlass;
    private DFVarScope _outerScope;
    private DFTypeCollection _klassSpace;
    private DFKlassScope _klassScope;
    private Map<String, DFLocalVarScope> _methodScopes =
        new HashMap<String, DFLocalVarScope>();

    // These fields are set immediately.
    private ASTNode _ast = null;
    private String _filePath = null;
    private String _jarPath = null;
    private String _entPath = null;

    // These fields are available after setMapTypes().
    private DFMapType[] _mapTypes = null;
    private DFTypeCollection _mapTypeSpace = null;
    private Map<String, DFKlass> _paramKlasses =
        new TreeMap<String, DFKlass>();

    // These fields are available only for parameterized klasses.
    private DFKlass _genericKlass = null;
    private DFType[] _paramTypes = null;
    private DFTypeCollection _paramTypeSpace = null;

    // This field is available after setBaseFinder(). (Pass2)
    private DFTypeFinder _baseFinder = null;

    // The following fields are available after the klass is loaded.
    private boolean _built = false;
    private DFKlass _baseKlass = null;
    private DFKlass[] _baseIfaces = null;
    private DFMethod _initializer = null;
    private List<DFRef> _fields = new ArrayList<DFRef>();
    private List<DFMethod> _methods = new ArrayList<DFMethod>();

    public DFKlass(
        String name, DFTypeSpace outerSpace,
        DFKlass outerKlass, DFVarScope outerScope) {
        _name = name;
        _outerSpace = outerSpace;
        _outerKlass = outerKlass;
	_outerScope = outerScope;
        _klassSpace = new DFTypeCollection(_outerSpace, _name);
        _klassScope = new DFKlassScope(_outerScope, _name);
    }

    protected DFKlass(
        String name, DFTypeCollection outerSpace,
        DFKlass outerKlass, DFVarScope outerScope,
        DFKlass baseKlass) {
        this(name, outerSpace, outerKlass, outerScope);
        _baseKlass = baseKlass;
        _built = true;
    }

    // Constructor for a parameterized klass.
    @SuppressWarnings("unchecked")
    private DFKlass(
        DFKlass genericKlass, DFType[] paramTypes) {
        assert genericKlass != null;
        assert paramTypes != null;
        // A parameterized Klass is NOT accessible from
        // the outer namespace but it creates its own subspace.
        _name = genericKlass._name;
        _outerSpace = genericKlass._outerSpace;
        _outerKlass = genericKlass._outerKlass;
        _outerScope = genericKlass._outerScope;
        String subname = _name + getParamName(paramTypes);
        _klassSpace = new DFTypeCollection(_outerSpace, subname);
        _klassScope = new DFKlassScope(_outerScope, subname);

        _genericKlass = genericKlass;
        _paramTypes = paramTypes;
        _paramTypeSpace = new DFTypeCollection(null, subname);
        for (int i = 0; i < _paramTypes.length; i++) {
            DFMapType mapType = genericKlass._mapTypes[i];
            DFType paramType = _paramTypes[i];
            assert mapType != null;
            assert paramType != null;
            _paramTypeSpace.addKlass(
                mapType.getTypeName(),
                paramType.getKlass());
        }

        _ast = genericKlass._ast;
        _filePath = genericKlass._filePath;
        _jarPath = genericKlass._jarPath;
        _entPath = genericKlass._entPath;

        _baseFinder = genericKlass._baseFinder;
        // Recreate the entire subspace.
        try {
            if (_ast instanceof AbstractTypeDeclaration) {
                _klassSpace.buildDecls(
                    this, _klassScope,
                    ((AbstractTypeDeclaration)_ast).bodyDeclarations());
            } else if (_ast instanceof AnonymousClassDeclaration) {
                _klassSpace.buildDecls(
                    this, _klassScope,
                    ((AnonymousClassDeclaration)_ast).bodyDeclarations());
            }
            // XXX what to do with .jar classes?
        } catch (UnsupportedSyntax e) {
        }

        // not loaded yet!
        assert !_built;
    }

    @Override
    public String toString() {
        return ("<DFKlass("+this.getTypeName()+")>");
    }

    @Override
    public int compareTo(DFKlass klass) {
        if (this == klass) return 0;
        int x = _outerSpace.compareTo(klass._outerSpace);
        if (x != 0) return x;
        return getTypeName().compareTo(klass.getTypeName());
    }

    public Element toXML(Document document) {
        Element elem = document.createElement("class");
        elem.setAttribute("path", this.getFilePath());
        elem.setAttribute("name", this.getTypeName());
        if (_baseKlass != null) {
            elem.setAttribute("extends", _baseKlass.getTypeName());
        }
        if (_baseIfaces != null && 0 < _baseIfaces.length) {
            StringBuilder b = new StringBuilder();
            for (DFKlass iface : _baseIfaces) {
                if (0 < b.length()) {
                    b.append(" ");
                }
                b.append(iface.getTypeName());
            }
            elem.setAttribute("implements", b.toString());
        }
        return elem;
    }

    public String getTypeName() {
        String name = "L"+_outerSpace.getSpaceName()+_name;
        if (_mapTypes != null) {
            name = name + getParamName(_mapTypes);
        }
        if (_paramTypes != null) {
            name = name + getParamName(_paramTypes);
        }
        return name+";";
    }

    public boolean equals(DFType type) {
        return (this == type);
    }

    public DFKlass getKlass() {
        return this;
    }

    public int canConvertFrom(DFType type, Map<DFMapType, DFType> typeMap) {
        if (type instanceof DFNullType) return 0;
        DFKlass klass = type.getKlass();
        if (klass == null) return -1;
        // type is-a this.
        return klass.isSubclassOf(this, typeMap);
    }

    public int isSubclassOf(DFKlass klass, Map<DFMapType, DFType> typeMap) {
        if (this == klass) return 0;
        if (_genericKlass == klass || klass._genericKlass == this ||
            (_genericKlass != null && _genericKlass == klass._genericKlass)) {
            // A<T> isSubclassOf B<S>?
            // types0: T
            DFType[] types0 = (_mapTypes != null)? _mapTypes : _paramTypes;
            assert types0 != null;
            // types1: S
            DFType[] types1 = (klass._mapTypes != null)? klass._mapTypes : klass._paramTypes;
            assert types1 != null;
            //assert types0.length == types1.length;
            // T isSubclassOf S? -> S canConvertFrom T?
            int dist = 0;
            for (int i = 0; i < Math.min(types0.length, types1.length); i++) {
                int d = types1[i].canConvertFrom(types0[i], typeMap);
                if (d < 0) return -1;
                dist += d;
            }
            return dist;
        }
        if (_baseKlass != null) {
            int dist = _baseKlass.isSubclassOf(klass, typeMap);
            if (0 <= dist) return dist+1;
        }
        if (_baseIfaces != null) {
            for (DFKlass iface : _baseIfaces) {
                int dist = iface.isSubclassOf(klass, typeMap);
                if (0 <= dist) return dist+1;
            }
        }
        return -1;
    }

    @SuppressWarnings("unchecked")
    public void setMapTypes(List<TypeParameter> tps) {
        // Get type parameters.
        assert _mapTypes == null;
        assert _paramTypes == null;
        _mapTypes = DFTypeCollection.getMapTypes(tps);
        if (_mapTypes != null) {
            _mapTypeSpace = DFTypeCollection.createMapTypeSpace(_mapTypes);
        }
    }

    public void setMapTypes(String sig) {
        assert _mapTypes == null;
        assert _paramTypes == null;
        _mapTypes = JNITypeParser.getMapTypes(sig);
        if (_mapTypes != null) {
            _mapTypeSpace = DFTypeCollection.createMapTypeSpace(_mapTypes);
        }
    }

    public boolean isGeneric() {
        if (_mapTypes != null) return true;
        if (_outerKlass != null) return _outerKlass.isGeneric();
        return false;
    }

    public DFKlass parameterize(DFType[] paramTypes) {
        assert _mapTypes != null;
        assert paramTypes.length <= _mapTypes.length;
        if (paramTypes.length < _mapTypes.length) {
            DFType[] types = new DFType[_mapTypes.length];
            for (int i = 0; i < _mapTypes.length; i++) {
                if (i < paramTypes.length) {
                    types[i] = paramTypes[i];
                } else {
                    types[i] = _mapTypes[i].getKlass();
                }
            }
            paramTypes = types;
        }
        String name = getParamName(paramTypes);
        DFKlass klass = _paramKlasses.get(name);
        if (klass == null) {
            klass = new DFKlass(this, paramTypes);
            _paramKlasses.put(name, klass);
        }
        return klass;
    }

    public void setJarPath(String jarPath, String entPath) {
        _jarPath = jarPath;
        _entPath = entPath;
    }

    public void setTree(String filePath, ASTNode ast) {
        _filePath = filePath;
        _ast = ast;
    }

    public String getFilePath() {
        return _filePath;
    }
    public ASTNode getTree() {
        return _ast;
    }

    public String getKlassName() {
        return _name;
    }

    public DFKlass getOuterKlass() {
        return _outerKlass;
    }

    public DFTypeCollection getKlassSpace() {
        return _klassSpace;
    }

    public DFVarScope getKlassScope() {
        return _klassScope;
    }

    public void putMethodScope(ASTNode ast, DFLocalVarScope scope) {
	String key = Utils.encodeASTNode(ast);
        _methodScopes.put(key, scope);
    }

    private DFLocalVarScope getMethodScope(ASTNode ast) {
	String key = Utils.encodeASTNode(ast);
        assert _methodScopes.containsKey(key);
        return _methodScopes.get(key);
    }

    public void setBaseFinder(DFTypeFinder finder) {
        assert !_built;
        //assert _baseFinder == null || _baseFinder == finder;
	_baseFinder = finder;
    }

    public DFTypeFinder addFinders(DFTypeFinder finder)
        throws TypeNotFound {
        assert _built;
        if (_baseKlass != null) {
            finder = _baseKlass.addFinders(finder);
        }
        if (_baseIfaces != null) {
            for (DFKlass iface : _baseIfaces) {
                finder = iface.addFinders(finder);
            }
        }
        finder = new DFTypeFinder(_klassSpace, finder);
        return finder;
    }

    public DFTypeFinder getBaseFinder()
        throws TypeNotFound {
        if (_outerKlass != null) {
            return _outerKlass.getBaseFinder();
        } else {
            assert _baseFinder != null;
            DFTypeFinder finder = this.addFinders(_baseFinder);
            return new DFTypeFinder(_klassSpace, finder);
        }
    }

    public DFTypeFinder getInternalFinder()
        throws TypeNotFound {
        assert _built;
        DFTypeFinder finder = this.addFinders(this.getBaseFinder());
        if (_mapTypeSpace != null) {
            finder = new DFTypeFinder(_mapTypeSpace, finder);
        }
        if (_paramTypeSpace != null) {
            finder = new DFTypeFinder(_paramTypeSpace, finder);
        }
        return finder;
    }

    public DFKlass getBaseKlass() {
        assert _built;
        return _baseKlass;
    }

    public DFKlass[] getBaseIfaces() {
        assert _built;
        return _baseIfaces;
    }

    public boolean isEnum() {
        assert _built;
        return (_baseKlass._genericKlass ==
                DFBuiltinTypes.getEnumKlass());
    }

    public DFMethod getInitializer() {
        assert _built;
        return _initializer;
    }

    protected DFRef lookupField(String id)
        throws VariableNotFound {
        assert _built;
        if (_klassScope != null) {
            try {
                return _klassScope.lookupRef("."+id);
            } catch (VariableNotFound e) {
            }
        }
        if (_baseKlass != null) {
            try {
                return _baseKlass.lookupField(id);
            } catch (VariableNotFound e) {
            }
        }
        if (_baseIfaces != null) {
            for (DFKlass iface : _baseIfaces) {
                try {
                    return iface.lookupField(id);
                } catch (VariableNotFound e) {
                }
            }
        }
        throw new VariableNotFound("."+id);
    }

    public DFRef lookupField(SimpleName name)
        throws VariableNotFound {
        return this.lookupField(name.getIdentifier());
    }

    protected List<DFRef> getFields() {
        assert _built;
	return _fields;
    }

    public List<DFMethod> getMethods() {
        assert _built;
	return _methods;
    }

    private DFMethod lookupMethod1(
        DFCallStyle callStyle, SimpleName name, DFType[] argTypes) {
        String id = (name == null)? null : name.getIdentifier();
        int bestDist = -1;
        DFMethod bestMethod = null;
        for (DFMethod method1 : this.getMethods()) {
            DFCallStyle callStyle1 = method1.getCallStyle();
            if (!(callStyle == callStyle1 ||
                  (callStyle == DFCallStyle.InstanceOrStatic &&
                   (callStyle1 == DFCallStyle.InstanceMethod ||
                    callStyle1 == DFCallStyle.StaticMethod)))) continue;
            if (id != null && !id.equals(method1.getName())) continue;
            int dist = method1.canAccept(argTypes);
            if (dist < 0) continue;
            if (bestDist < 0 || dist < bestDist) {
                bestDist = dist;
                bestMethod = method1;
            }
        }
        return bestMethod;
    }

    public DFMethod lookupMethod(
        DFCallStyle callStyle, SimpleName name, DFType[] argTypes)
        throws MethodNotFound {
        assert _built;
        DFMethod method = this.lookupMethod1(callStyle, name, argTypes);
        if (method != null) {
            return method;
        }
        if (_outerKlass != null) {
            try {
                return _outerKlass.lookupMethod(callStyle, name, argTypes);
            } catch (MethodNotFound e) {
            }
        }
        if (_baseKlass != null) {
            try {
                return _baseKlass.lookupMethod(callStyle, name, argTypes);
            } catch (MethodNotFound e) {
            }
        }
        if (_baseIfaces != null) {
            for (DFKlass iface : _baseIfaces) {
                try {
                    return iface.lookupMethod(callStyle, name, argTypes);
                } catch (MethodNotFound e) {
                }
            }
        }
        String id = (name == null)? callStyle.toString() : name.getIdentifier();
        throw new MethodNotFound(id, argTypes);
    }

    protected DFRef addField(
        SimpleName name, boolean isStatic, DFType type) {
        return this.addField(name.getIdentifier(), isStatic, type);
    }

    protected DFRef addField(
        String id, boolean isStatic, DFType type) {
        assert _klassScope != null;
        DFRef ref = _klassScope.addRef("."+id, type);
        //Logger.info("DFKlass.addField:", ref);
	_fields.add(ref);
        return ref;
    }

    private DFMethod addMethod(
        DFTypeCollection methodSpace, String id, DFCallStyle callStyle,
        DFMapType[] mapTypes, DFTypeFinder finder,
        DFMethodType methodType) {
        return this.addMethod(
            new DFMethod(
                this, methodSpace, id, callStyle,
                mapTypes, finder, methodType));
    }

    private DFMethod addMethod(DFMethod method) {
        //Logger.info("DFKlass.addMethod:", method);
        _methods.add(method);
        // override the outer methods.
        if (_baseKlass != null) {
            _baseKlass.overrideMethod(method);
        }
        if (_baseIfaces != null) {
            for (DFKlass iface : _baseIfaces) {
                iface.overrideMethod(method);
            }
        }
        return method;
    }

    private void overrideMethod(DFMethod method1) {
        for (DFMethod method0 : getMethods()) {
            if (method0.equals(method1)) {
                method0.addOverride(method1);
                break;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private boolean isStatic(BodyDeclaration body) {
        for (IExtendedModifier imod :
                 (List<IExtendedModifier>) body.modifiers()) {
            if (imod.isModifier()) {
                if (((Modifier)imod).isStatic()) return true;
            }
        }
        return false;
    }

    public boolean isBuilt() {
        return _built;
    }

    public void load()
        throws TypeNotFound {
        if (_built) return;
        _built = true;
        if (_outerKlass != null) {
            _outerKlass.load();
        }
        DFTypeFinder finder = this.getBaseFinder();
        if (finder == null) Logger.error("!!!", this, _outerKlass);
        assert _ast != null || _jarPath != null;
        if (_mapTypeSpace != null) {
            assert _mapTypes != null;
	    finder = new DFTypeFinder(_mapTypeSpace, finder);
	    _mapTypeSpace.buildMapTypes(finder, _mapTypes);
        }
        if (_paramTypeSpace != null) {
            finder = new DFTypeFinder(_paramTypeSpace, finder);
        }
        // a generic class is only referred to, but not built.
        if (_ast != null) {
            try {
                this.buildFromTree(finder, _ast);
            } catch (UnsupportedSyntax e) {
                String astName = e.ast.getClass().getName();
                Logger.error("Error: Unsupported syntax:", e.name, "("+astName+")");
                throw new TypeNotFound(this.getTypeName());
            }
        } else if (_jarPath != null) {
            try {
                JarFile jarfile = new JarFile(_jarPath);
                try {
                    JarEntry je = jarfile.getJarEntry(_entPath);
                    InputStream strm = jarfile.getInputStream(je);
                    JavaClass jklass = new ClassParser(strm, _entPath).parse();
                    this.buildFromJKlass(finder, jklass);
                } finally {
                    jarfile.close();
                }
            } catch (IOException e) {
                Logger.error("Error: Not found:", _jarPath+"/"+_entPath);
                throw new TypeNotFound(this.getTypeName());
            }
        }
    }

    private void buildFromJKlass(DFTypeFinder finder, JavaClass jklass)
        throws TypeNotFound {
        //Logger.info("DFKlass.buildFromJKlass:", this, finder);
        // Load base klasses/interfaces.
        String sig = Utils.getJKlassSignature(jklass.getAttributes());
        if (sig != null) {
            //Logger.info("jklass:", this, sig);
	    JNITypeParser parser = new JNITypeParser(sig);
	    _baseKlass = (DFKlass)parser.getType(finder);
            _baseKlass.load();
	    finder = _baseKlass.addFinders(finder);
	    List<DFKlass> ifaces = new ArrayList<DFKlass>();
	    for (;;) {
		DFKlass iface = (DFKlass)parser.getType(finder);
		if (iface == null) break;
                iface.load();
		ifaces.add(iface);
		finder = iface.addFinders(finder);
	    }
	    _baseIfaces = new DFKlass[ifaces.size()];
	    ifaces.toArray(_baseIfaces);
        } else {
	    String superClass = jklass.getSuperclassName();
	    if (superClass != null && !superClass.equals(jklass.getClassName())) {
		_baseKlass = finder.lookupKlass(superClass);
                _baseKlass.load();
		finder = _baseKlass.addFinders(finder);
	    }
	    String[] ifaces = jklass.getInterfaceNames();
	    if (ifaces != null) {
                DFKlass[] baseIfaces = new DFKlass[ifaces.length];
		for (int i = 0; i < ifaces.length; i++) {
		    DFKlass iface = finder.lookupKlass(ifaces[i]);
                    iface.load();
		    baseIfaces[i] = iface;
		    finder = iface.addFinders(finder);
		}
		_baseIfaces = baseIfaces;
	    }
	}
        // Extend a TypeFinder for this klass.
        if (_outerKlass != null) {
            _outerKlass.load();
            finder = _outerKlass.addFinders(finder);
        }
        finder = new DFTypeFinder(_klassSpace, finder);
        // Define fields.
        for (Field fld : jklass.getFields()) {
            if (fld.isPrivate()) continue;
            sig = Utils.getJKlassSignature(fld.getAttributes());
	    DFType type;
	    if (sig != null) {
                //Logger.info("fld:", fld.getName(), sig);
		JNITypeParser parser = new JNITypeParser(sig);
		type = parser.getType(finder);
	    } else {
		type = finder.resolve(fld.getType());
	    }
	    this.addField(fld.getName(), fld.isStatic(), type);
        }
        // Define methods.
        for (Method meth : jklass.getMethods()) {
            if (meth.isPrivate()) continue;
            sig = Utils.getJKlassSignature(meth.getAttributes());
	    DFMethodType methodType;
            DFTypeCollection methodSpace = new DFTypeCollection(_klassSpace, meth.getName());
            DFMapType[] mapTypes = null;
	    if (sig != null) {
                //Logger.info("meth:", meth.getName(), sig);
                mapTypes = JNITypeParser.getMapTypes(sig);
                if (mapTypes != null) {
                    DFTypeCollection mapTypeSpace = DFTypeCollection.createMapTypeSpace(mapTypes);
		    finder = new DFTypeFinder(mapTypeSpace, finder);
		    mapTypeSpace.buildMapTypes(finder, mapTypes);
                }
		JNITypeParser parser = new JNITypeParser(sig);
		finder = new DFTypeFinder(methodSpace, finder);
		methodType = (DFMethodType)parser.getType(finder);
	    } else {
		org.apache.bcel.generic.Type[] args = meth.getArgumentTypes();
		DFType[] argTypes = new DFType[args.length];
		for (int i = 0; i < args.length; i++) {
		    argTypes[i] = finder.resolve(args[i]);
		}
		DFType returnType = finder.resolve(meth.getReturnType());
		methodType = new DFMethodType(argTypes, returnType);
	    }
            DFCallStyle callStyle;
            if (meth.getName().equals("<init>")) {
                callStyle = DFCallStyle.Constructor;
            } else if (meth.isStatic()) {
                callStyle = DFCallStyle.StaticMethod;
            } else {
                callStyle = DFCallStyle.InstanceMethod;
            }
            this.addMethod(
                methodSpace, meth.getName(), callStyle,
                mapTypes, finder, methodType);
        }
    }

    @SuppressWarnings("unchecked")
    protected void buildFromTree(DFTypeFinder finder, ASTNode ast)
        throws UnsupportedSyntax, TypeNotFound {
        //Logger.info("DFKlass.buildFromTree:", this, finder);
        if (ast instanceof AbstractTypeDeclaration) {
            this.buildAbstTypeDecl(finder, (AbstractTypeDeclaration)ast);

        } else if (ast instanceof AnonymousClassDeclaration) {
            this.buildAnonDecl(finder, (AnonymousClassDeclaration)ast);
        }
    }

    private void buildAbstTypeDecl(
        DFTypeFinder finder, AbstractTypeDeclaration abstTypeDecl)
        throws UnsupportedSyntax, TypeNotFound {
        if (abstTypeDecl instanceof TypeDeclaration) {
            this.buildTypeDecl(finder, (TypeDeclaration)abstTypeDecl);

        } else if (abstTypeDecl instanceof EnumDeclaration) {
            this.buildEnumDecl(finder, (EnumDeclaration)abstTypeDecl);

        } else if (abstTypeDecl instanceof AnnotationTypeDeclaration) {
            this.buildAnnotTypeDecl(finder, (AnnotationTypeDeclaration)abstTypeDecl);
        }
    }

    @SuppressWarnings("unchecked")
    private void buildTypeDecl(
        DFTypeFinder finder, TypeDeclaration typeDecl)
        throws UnsupportedSyntax, TypeNotFound {
        // Load base klasses/interfaces.
        try {
            // Get superclass.
            Type superClass = typeDecl.getSuperclassType();
            if (superClass != null) {
                _baseKlass = finder.resolve(superClass).getKlass();
            } else {
                _baseKlass = DFBuiltinTypes.getObjectKlass();
            }
            _baseKlass.load();
            finder = _baseKlass.addFinders(finder);
            // Get interfaces.
            List<Type> ifaces = typeDecl.superInterfaceTypes();
            DFKlass[] baseIfaces = new DFKlass[ifaces.size()];
            for (int i = 0; i < ifaces.size(); i++) {
		DFKlass iface = finder.resolve(ifaces.get(i)).getKlass();
                //Logger.info("DFKlass.build:", this, "implements", iface);
                baseIfaces[i] = iface;
                iface.load();
                finder = iface.addFinders(finder);
            }
            _baseIfaces = baseIfaces;
            this.buildDecls(finder, typeDecl.bodyDeclarations());
        } catch (TypeNotFound e) {
            e.setAst(typeDecl);
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private void buildEnumDecl(
        DFTypeFinder finder, EnumDeclaration enumDecl)
        throws UnsupportedSyntax, TypeNotFound {
        // Load base klasses/interfaces.
        try {
            // Get superclass.
            DFKlass enumKlass = DFBuiltinTypes.getEnumKlass();
            _baseKlass = enumKlass.parameterize(new DFType[] { this });
            _baseKlass.load();
            finder = _baseKlass.addFinders(finder);
            // Get interfaces.
            List<Type> ifaces = enumDecl.superInterfaceTypes();
            DFKlass[] baseIfaces = new DFKlass[ifaces.size()];
            for (int i = 0; i < ifaces.size(); i++) {
		DFKlass iface = finder.resolve(ifaces.get(i)).getKlass();
                iface.load();
                baseIfaces[i] = iface;
                finder = iface.addFinders(finder);
            }
            _baseIfaces = baseIfaces;
            // Get constants.
            for (EnumConstantDeclaration econst :
                     (List<EnumConstantDeclaration>) enumDecl.enumConstants()) {
                this.addField(econst.getName(), true, this);
            }
            // Enum has a special method "values()".
            this.addMethod(
                null, "values", DFCallStyle.InstanceMethod, null, finder,
                new DFMethodType(new DFType[] {}, new DFArrayType(this, 1)));
            this.buildDecls(finder, enumDecl.bodyDeclarations());
        } catch (TypeNotFound e) {
            e.setAst(enumDecl);
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private void buildAnnotTypeDecl(
        DFTypeFinder finder, AnnotationTypeDeclaration annotTypeDecl)
        throws UnsupportedSyntax, TypeNotFound {
        try {
            // Get superclass.
            _baseKlass = DFBuiltinTypes.getObjectKlass();
            _baseKlass.load();
            finder = _baseKlass.addFinders(finder);
            this.buildDecls(finder, annotTypeDecl.bodyDeclarations());
        } catch (TypeNotFound e) {
            e.setAst(annotTypeDecl);
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private void buildAnonDecl(
        DFTypeFinder finder, AnonymousClassDeclaration anonDecl)
        throws UnsupportedSyntax, TypeNotFound {
        try {
            // Get superclass.
            _baseKlass = DFBuiltinTypes.getObjectKlass();
            _baseKlass.load();
            finder = _baseKlass.addFinders(finder);
            this.buildDecls(finder, anonDecl.bodyDeclarations());
        } catch (TypeNotFound e) {
            e.setAst(anonDecl);
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private void buildDecls(DFTypeFinder finder, List<BodyDeclaration> decls)
        throws UnsupportedSyntax, TypeNotFound {
        // Extend a TypeFinder for the child klasses.
        if (_outerKlass != null) {
            finder = _outerKlass.addFinders(finder);
        }
        finder = new DFTypeFinder(_klassSpace, finder);
        for (BodyDeclaration body : decls) {
            if (body instanceof AbstractTypeDeclaration) {
                // Child klasses are loaded independently.

            } else if (body instanceof FieldDeclaration) {
                FieldDeclaration decl = (FieldDeclaration)body;
                DFType fldType = finder.resolve(decl.getType());
                for (VariableDeclarationFragment frag :
                         (List<VariableDeclarationFragment>) decl.fragments()) {
                    DFType ft = fldType;
                    int ndims = frag.getExtraDimensions();
                    if (ndims != 0) {
                        ft = new DFArrayType(ft, ndims);
                    }
                    this.addField(frag.getName(), isStatic(decl), ft);
                }

            } else if (body instanceof MethodDeclaration) {
                MethodDeclaration decl = (MethodDeclaration)body;
                String id = Utils.encodeASTNode(decl);
                DFTypeCollection methodSpace = _klassSpace.lookupSpace(id);
                finder = new DFTypeFinder(methodSpace, finder);
                List<TypeParameter> tps = decl.typeParameters();
                DFMapType[] mapTypes = null;
                if (0 < tps.size()) {
                    mapTypes = new DFMapType[tps.size()];
                    for (int i = 0; i < tps.size(); i++) {
                        TypeParameter tp = tps.get(i);
                        String id2 = tp.getName().getIdentifier();
                        mapTypes[i] = new DFMapType(id2);
                        mapTypes[i].setTypeBounds(tp.typeBounds());
                    }
                    DFTypeCollection mapTypeSpace = DFTypeCollection.createMapTypeSpace(mapTypes);
		    finder = new DFTypeFinder(mapTypeSpace, finder);
		    mapTypeSpace.buildMapTypes(finder, mapTypes);
                }
                DFType[] argTypes = finder.resolveArgs(decl);
                DFType returnType;
                String name;
                DFCallStyle callStyle;
                if (decl.isConstructor()) {
                    returnType = this;
                    name = "<init>";
                    callStyle = DFCallStyle.Constructor;
                } else {
                    returnType = finder.resolve(decl.getReturnType2());
                    name = decl.getName().getIdentifier();
                    callStyle = (isStatic(decl))?
                        DFCallStyle.StaticMethod : DFCallStyle.InstanceMethod;
                }
                DFMethod method = this.addMethod(
                    methodSpace, name, callStyle, mapTypes, finder,
                    new DFMethodType(argTypes, returnType));
		if (decl.getBody() != null) {
		    method.setScope(this.getMethodScope(decl));
		    method.setTree(decl);
		}
                for (DFKlass klass : methodSpace.getKlasses()) {
                    klass.setBaseFinder(finder);
                }

            } else if (body instanceof EnumConstantDeclaration) {

            } else if (body instanceof AnnotationTypeMemberDeclaration) {
                AnnotationTypeMemberDeclaration decl =
                    (AnnotationTypeMemberDeclaration)body;
                DFType type = finder.resolve(decl.getType());
                this.addField(decl.getName(), isStatic(decl), type);

            } else if (body instanceof Initializer) {
                Initializer initializer = (Initializer)body;
                DFTypeCollection methodSpace = _klassSpace.lookupSpace("<clinit>");
                _initializer = new DFMethod(
		    this, methodSpace, "<clinit>", DFCallStyle.Initializer,
                    null, finder,
		    new DFMethodType(new DFType[] {}, DFBasicType.VOID));
		_initializer.setScope(this.getMethodScope(initializer));
		_initializer.setTree(initializer);
                for (DFKlass klass : methodSpace.getKlasses()) {
                    klass.setBaseFinder(finder);
                }

            } else {
                throw new UnsupportedSyntax(body);
            }
        }
    }

    public static String getParamName(DFType[] paramTypes) {
        StringBuilder b = new StringBuilder();
        for (DFType type : paramTypes) {
            if (0 < b.length()) {
                b.append(",");
            }
            b.append(type.getTypeName());
        }
        return "<"+b.toString()+">";
    }

    // DFKlassScope
    private class DFKlassScope extends DFVarScope {

        private DFRef _this;

        public DFKlassScope(DFVarScope outer, String id) {
            super(outer, id);
            _this = this.addRef("#this", DFKlass.this, null);
        }

        @Override
        public String getScopeName() {
            return DFKlass.this.getTypeName();
        }

        @Override
        public DFRef lookupThis() {
            return _this;
        }

        @Override
        protected DFRef lookupVar1(String id)
            throws VariableNotFound {
            // try local variables first.
            try {
                return super.lookupVar1(id);
            } catch (VariableNotFound e) {
                // try field names.
                return DFKlass.this.lookupField(id);
            }
        }

        // dumpContents (for debugging)
        public void dumpContents(PrintStream out, String indent) {
            super.dumpContents(out, indent);
            for (DFMethod method : DFKlass.this.getMethods()) {
                out.println(indent+"defined: "+method);
            }
        }
    }
}
