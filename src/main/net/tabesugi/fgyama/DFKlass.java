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


//  DFKlass
//
public class DFKlass extends DFType {

    // These fields are available upon construction.
    private String _name;
    private DFTypeSpace _parentSpace;
    private DFKlass _parentKlass;
    private DFVarScope _parentScope;
    private DFTypeSpace _klassSpace;
    private DFKlassScope _klassScope;
    private List<DFKlass> _childKlasses =
        new ArrayList<DFKlass>();
    private Map<String, DFLocalVarScope> _methodScopes =
        new HashMap<String, DFLocalVarScope>();

    // These fields are set immediately.
    private ASTNode _ast = null;
    private String _jarPath = null;
    private String _filePath = null;

    // These fields are available after setMapTypes().
    private DFMapType[] _mapTypes = null;

    // These fields are available only for parameterized klasses.
    private DFKlass _genericKlass = null;
    private DFType[] _paramTypes = null;

    // This field is available after setBaseFinder(). (Pass2)
    private DFTypeFinder _baseFinder = null;

    // The following fields are available after the klass is loaded.
    private boolean _built = false;
    private DFKlass _baseKlass = null;
    private DFKlass[] _baseIfaces = null;
    private DFMethod _initializer = null;
    private List<DFRef> _fields = null;
    private List<DFMethod> _methods = null;

    public DFKlass(
        String name, DFTypeSpace parentSpace,
        DFKlass parentKlass, DFVarScope parentScope) {
        _name = name;
        _parentSpace = parentSpace;
        _parentKlass = parentKlass;
	_parentScope = parentScope;
        _klassSpace = parentSpace.lookupSpace(name);
        _klassScope = new DFKlassScope(parentScope, name);

        if (_parentKlass != null) {
            _parentKlass._childKlasses.add(this);
        }
    }

    // Constructor for a parameterized klass.
    private DFKlass(
        String name, DFKlass genericKlass,
        DFType[] paramTypes) {
        assert genericKlass != null;
        assert paramTypes != null;
        _name = name;
        _parentSpace = genericKlass._parentSpace;
        _parentKlass = genericKlass._parentKlass;
        _parentScope = genericKlass._parentScope;
        _klassSpace = _parentSpace.lookupSpace(name);
        _klassScope = new DFKlassScope(_parentScope, name);

        if (_parentKlass != null) {
            _parentKlass._childKlasses.add(this);
        }

        _genericKlass = genericKlass;
        _paramTypes = paramTypes;

        _ast = genericKlass._ast;
        _jarPath = genericKlass._jarPath;
        _filePath = genericKlass._filePath;

        assert _mapTypes == null;
        DFMapType[] mapTypes = genericKlass._mapTypes;

        // not loaded yet!
        assert !_built;

        DFTypeSpace mapTypeSpace = new DFTypeSpace(null, name);
        for (int i = 0; i < paramTypes.length; i++) {
            assert mapTypes[i] != null;
            assert paramTypes[i] != null;
            mapTypeSpace.addKlass(
                mapTypes[i].getTypeName(),
                paramTypes[i].getKlass());
        }
        DFTypeFinder finder = genericKlass._baseFinder;
        assert finder != null;
        _baseFinder = new DFTypeFinder(finder, mapTypeSpace);
    }

    @Override
    public String toString() {
        return ("<DFKlass("+this.getFullName()+")>");
    }

    public String getTypeName() {
        String name = "L"+this.getFullName();
        if (_mapTypes != null && 0 < _mapTypes.length) {
            name = name + getParamNames(_mapTypes);
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
	if (type instanceof DFArrayType) {
	    type = DFBuiltinTypes.getArrayKlass();
	} else if (type == DFBasicType.BYTE) {
	    type = DFBuiltinTypes.getByteKlass();
	} else if (type == DFBasicType.CHAR) {
	    type = DFBuiltinTypes.getCharacterKlass();
	} else if (type == DFBasicType.SHORT) {
	    type = DFBuiltinTypes.getShortKlass();
	} else if (type == DFBasicType.INT) {
	    type = DFBuiltinTypes.getIntegerKlass();
	} else if (type == DFBasicType.LONG) {
	    type = DFBuiltinTypes.getLongKlass();
	} else if (type == DFBasicType.FLOAT) {
	    type = DFBuiltinTypes.getFloatKlass();
	} else if (type == DFBasicType.DOUBLE) {
	    type = DFBuiltinTypes.getDoubleKlass();
	} else if (type == DFBasicType.BOOLEAN) {
	    type = DFBuiltinTypes.getBooleanKlass();
	}
        if (!(type instanceof DFKlass)) return -1;
        // type is-a this.
        return ((DFKlass)type).isSubclassOf(this, typeMap);
    }

    public int isSubclassOf(DFKlass klass, Map<DFMapType, DFType> typeMap) {
        if (this == klass) return 0;
        if (_genericKlass != null && klass._genericKlass != null) {
            // A<T> isSubclassOf B<S>?
            if (_paramTypes.length != klass._paramTypes.length) return -1;
            // A isSubclassOf B?
            int dist = _genericKlass.isSubclassOf(klass._genericKlass, typeMap);
            if (dist < 0) return -1;
            for (int i = 0; i < _paramTypes.length; i++) {
                // T isSubclassOf S? -> S canConvertFrom T?
                int d = klass._paramTypes[i].canConvertFrom(_paramTypes[i], typeMap);
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

    public String getFullName() {
        return _parentSpace.getFullName()+_name;
    }

    @SuppressWarnings("unchecked")
    public void setMapTypes(List<TypeParameter> tps) {
        // Get type parameters.
        assert _mapTypes == null;
        assert _paramTypes == null;
        if (0 < tps.size()) {
            _mapTypes = new DFMapType[tps.size()];
            for (int i = 0; i < tps.size(); i++) {
                TypeParameter tp = tps.get(i);
                String id = tp.getName().getIdentifier();
                _mapTypes[i] = new DFMapType(id);
                _mapTypes[i].setTypeBounds(tp.typeBounds());
            }
        }
    }

    public void setMapTypes(String sig) {
        assert _mapTypes == null;
        assert _paramTypes == null;
        _mapTypes = JNITypeParser.getMapTypes(sig, _klassSpace);
    }

    public boolean isGeneric() {
        if (_mapTypes != null) return true;
        if (_parentKlass != null) return _parentKlass.isGeneric();
        return false;
    }

    public DFKlass parameterize(DFType[] paramTypes) {
        assert _mapTypes != null;
        String name = _name + getParamNames(paramTypes);
        try {
            return _parentSpace.getKlass(name);
        } catch (TypeNotFound e) {
            DFKlass klass = new DFKlass(name, this, paramTypes);
            _parentSpace.addKlass(name, klass);
            return klass;
        }
    }

    public void setJarPath(String jarPath, String filePath) {
        _jarPath = jarPath;
        _filePath = filePath;
    }

    public void setTree(ASTNode ast) {
        _ast = ast;
    }

    public ASTNode getTree() {
        return _ast;
    }

    public String getKlassName() {
        return _name;
    }

    public DFKlass getParentKlass() {
        return _parentKlass;
    }

    public DFTypeSpace getKlassSpace() {
        return _klassSpace;
    }

    public DFVarScope getKlassScope() {
        return _klassScope;
    }

    public void putMethodScope(ASTNode ast, DFLocalVarScope scope) {
        _methodScopes.put(Utils.encodeASTNode(ast), scope);
    }

    private DFLocalVarScope getMethodScope(ASTNode ast) {
	String key = Utils.encodeASTNode(ast);
        assert _methodScopes.containsKey(key);
        return _methodScopes.get(key);
    }

    public void setBaseFinder(DFTypeFinder finder) {
        assert _baseFinder == null || _baseFinder == finder;
	_baseFinder = finder;
    }

    public DFTypeFinder getBaseFinder() {
        return _baseFinder;
    }

    public DFTypeFinder getFinder()
        throws TypeNotFound {
        return _baseFinder.extend(this);
    }

    public void setBaseKlass(DFKlass baseKlass) {
        _baseKlass = baseKlass;
    }
    public DFKlass getBaseKlass() {
        assert _built;
        return _baseKlass;
    }

    public void setBaseIfaces(DFKlass[] baseIfaces) {
        _baseIfaces = baseIfaces;
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
            int dist = method1.canAccept(id, argTypes);
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
        if (_parentKlass != null) {
            try {
                return _parentKlass.lookupMethod(callStyle, name, argTypes);
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
        throw new MethodNotFound(name.getIdentifier(), argTypes);
    }

    private DFRef addField(
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
        DFTypeSpace methodSpace, String id, DFCallStyle callStyle,
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
        // override the parent methods.
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

    public void load()
        throws TypeNotFound {
        this.build(_baseFinder);
    }

    public boolean isBuilt() {
        return _built;
    }

    protected void setBuilt() {
        assert !_built;
        _built = true;
        _fields = new ArrayList<DFRef>();
        _methods =  new ArrayList<DFMethod>();
    }

    public void build(DFTypeFinder finder)
        throws TypeNotFound {
        if (_built) return;
        this.setBuilt();
        assert finder != null;
        assert _ast != null || _jarPath != null;
        if (_mapTypes != null) {
            DFTypeSpace mapTypeSpace = new DFTypeSpace(null, _name);
            finder = new DFTypeFinder(finder, mapTypeSpace);
            for (int i = 0; i < _mapTypes.length; i++) {
                DFMapType mapType = _mapTypes[i];
                mapType.build(finder);
                mapTypeSpace.addKlass(
                    mapType.getTypeName(),
                    mapType.getKlass());
            }
        }
        // a generic class is only referred to, but not built.
        if (_ast != null) {
            try {
                this.buildFromTree(finder, _ast);
            } catch (UnsupportedSyntax e) {
                String astName = e.ast.getClass().getName();
                Logger.error("Error: Unsupported syntax:", e.name, "("+astName+")");
                throw new TypeNotFound(this.getFullName());
            }
        } else if (_jarPath != null) {
            try {
                JarFile jarfile = new JarFile(_jarPath);
                try {
                    JarEntry je = jarfile.getJarEntry(_filePath);
                    InputStream strm = jarfile.getInputStream(je);
                    JavaClass jklass = new ClassParser(strm, _filePath).parse();
                    this.buildFromJKlass(finder, jklass);
                } finally {
                    jarfile.close();
                }
            } catch (IOException e) {
                Logger.error("Error: Not found:", _jarPath+"/"+_filePath);
                throw new TypeNotFound(this.getFullName());
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
	    finder = finder.extend(_baseKlass);
	    List<DFKlass> ifaces = new ArrayList<DFKlass>();
	    for (;;) {
		DFKlass iface = (DFKlass)parser.getType(finder);
		if (iface == null) break;
                iface.load();
		ifaces.add(iface);
		finder = finder.extend(iface);
	    }
	    _baseIfaces = new DFKlass[ifaces.size()];
	    ifaces.toArray(_baseIfaces);
        } else {
	    String superClass = jklass.getSuperclassName();
	    if (superClass != null && !superClass.equals(jklass.getClassName())) {
		_baseKlass = finder.lookupKlass(superClass);
                _baseKlass.load();
		finder = finder.extend(_baseKlass);
	    }
	    String[] ifaces = jklass.getInterfaceNames();
	    if (ifaces != null) {
                DFKlass[] baseIfaces = new DFKlass[ifaces.length];
		for (int i = 0; i < ifaces.length; i++) {
		    DFKlass iface = finder.lookupKlass(ifaces[i]);
                    iface.load();
		    baseIfaces[i] = iface;
		    finder = finder.extend(iface);
		}
		_baseIfaces = baseIfaces;
	    }
	}
        // Extend a TypeFinder for this klass.
        if (_parentKlass != null) {
            finder = finder.extend(_parentKlass);
        }
        finder = new DFTypeFinder(finder, _klassSpace);
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
            DFTypeSpace methodSpace = new DFTypeSpace(_klassSpace, meth.getName());
            DFMapType[] mapTypes = null;
	    if (sig != null) {
                //Logger.info("meth:", meth.getName(), sig);
                mapTypes = JNITypeParser.getMapTypes(sig, methodSpace);
                if (mapTypes != null) continue; // XXX
		JNITypeParser parser = new JNITypeParser(sig);
		finder = new DFTypeFinder(finder, methodSpace);
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
            finder = finder.extend(_baseKlass);
            // Get interfaces.
            List<Type> ifaces = typeDecl.superInterfaceTypes();
            DFKlass[] baseIfaces = new DFKlass[ifaces.size()];
            for (int i = 0; i < ifaces.size(); i++) {
		DFKlass iface = finder.resolve(ifaces.get(i)).getKlass();
                //Logger.info("DFKlass.build:", this, "implements", iface);
                baseIfaces[i] = iface;
                iface.load();
                finder = finder.extend(iface);
            }
            _baseIfaces = baseIfaces;
            // Extend a TypeFinder for this klass.
            if (_parentKlass != null) {
                finder = finder.extend(_parentKlass);
            }
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
            finder = finder.extend(_baseKlass);
            // Get interfaces.
            List<Type> ifaces = enumDecl.superInterfaceTypes();
            DFKlass[] baseIfaces = new DFKlass[ifaces.size()];
            for (int i = 0; i < ifaces.size(); i++) {
		DFKlass iface = finder.resolve(ifaces.get(i)).getKlass();
                iface.load();
                baseIfaces[i] = iface;
                finder = finder.extend(iface);
            }
            _baseIfaces = baseIfaces;
            // Extend a TypeFinder for this klass.
            if (_parentKlass != null) {
                finder = finder.extend(_parentKlass);
            }
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
            finder = finder.extend(_baseKlass);
            // Extend a TypeFinder for this klass.
            if (_parentKlass != null) {
                finder = finder.extend(_parentKlass);
            }
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
            finder = finder.extend(_baseKlass);
            // Extend a TypeFinder for this klass.
            if (_parentKlass != null) {
                finder = finder.extend(_parentKlass);
            }
            this.buildDecls(finder, anonDecl.bodyDeclarations());
        } catch (TypeNotFound e) {
            e.setAst(anonDecl);
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private void buildDecls(DFTypeFinder finder, List<BodyDeclaration> decls)
        throws UnsupportedSyntax, TypeNotFound {
        _klassSpace.buildDecls(this, _klassScope, decls);
        finder = new DFTypeFinder(finder, _klassSpace);
        for (DFKlass child : _klassSpace.getKlasses()) {
            child.setBaseFinder(finder);
        }
        _klassScope.build();
        for (BodyDeclaration body : decls) {
            if (body instanceof AbstractTypeDeclaration) {
                AbstractTypeDeclaration decl = (AbstractTypeDeclaration)body;
                // Do nothing for a child klass.
                // (They will be loaded/built independently.)

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
                String id = "method"+Utils.encodeASTNode(decl);
                DFTypeSpace methodSpace = _klassSpace.lookupSpace(id);
                finder = new DFTypeFinder(finder, methodSpace);
                List<TypeParameter> tps = decl.typeParameters();
                DFMapType[] mapTypes = null;
                if (0 < tps.size()) {
                    mapTypes = new DFMapType[tps.size()];
                    for (int i = 0; i < tps.size(); i++) {
                        TypeParameter tp = tps.get(i);
                        String id2 = tp.getName().getIdentifier();
                        mapTypes[i] = new DFMapType(id2);
                    }
                }
                if (mapTypes != null) continue; // XXX
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

            } else if (body instanceof EnumConstantDeclaration) {

            } else if (body instanceof AnnotationTypeMemberDeclaration) {
                AnnotationTypeMemberDeclaration decl =
                    (AnnotationTypeMemberDeclaration)body;
                DFType type = finder.resolve(decl.getType());
                this.addField(decl.getName(), isStatic(decl), type);

            } else if (body instanceof Initializer) {
                Initializer initializer = (Initializer)body;
                DFTypeSpace methodSpace = _klassSpace.lookupSpace("<clinit>");
                _initializer = new DFMethod(
		    this, methodSpace, "<clinit>", DFCallStyle.Initializer,
                    null, finder,
		    new DFMethodType(new DFType[] {}, DFBasicType.VOID));
		_initializer.setScope(this.getMethodScope(initializer));
		_initializer.setTree(initializer);

            } else {
                throw new UnsupportedSyntax(body);
            }
        }
    }

    private static String getParamNames(DFType[] paramTypes) {
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

        public DFKlassScope(DFVarScope parent, String id) {
            super(parent, id);
        }

        public String getFullName() {
            return DFKlass.this.getFullName();
        }

        public void build() {
            _this = this.addRef("#this", DFKlass.this, null);
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
