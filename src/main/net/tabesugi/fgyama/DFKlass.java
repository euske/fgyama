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

    protected String _name;

    // Ultimately every klass must have a baseKlass, but
    // they're not defined until it is loaded.
    protected DFKlass _baseKlass = null;
    // Ditto for base Interfaces.
    protected DFKlass[] _baseIfaces = null;

    private DFTypeSpace _typeSpace;
    private DFTypeSpace _klassSpace;
    private DFKlass _parentKlass;
    private DFKlassScope _klassScope;

    private DFMapType[] _mapTypes = null;
    private List<DFParamKlass> _paramKlasses =
        new ArrayList<DFParamKlass>();

    private DFMethod _initializer = null;
    private DFMethod _constructor = null;
    private List<DFRef> _fields =
        new ArrayList<DFRef>();
    private List<DFMethod> _methods =
        new ArrayList<DFMethod>();
    private Map<String, DFLocalVarScope> _ast2scope =
        new HashMap<String, DFLocalVarScope>();

    private boolean _loaded = false;
    private DFTypeFinder _finder = null;
    private String _jarPath = null;
    private String _filePath = null;
    private ASTNode _ast = null;

    public DFKlass(
        String name, DFTypeSpace typeSpace,
        DFKlass parentKlass, DFVarScope parentScope,
        DFKlass baseKlass) {
        _name = name;
        _typeSpace = typeSpace;
        _klassSpace = typeSpace.lookupSpace(name);
        _parentKlass = parentKlass;
        _baseKlass = baseKlass;
        _klassScope = new DFKlassScope(parentScope, name);
    }

    protected DFKlass(String name, DFKlass genericKlass) {
        _name = name;
        _typeSpace = genericKlass._typeSpace;
        _klassSpace = genericKlass._klassSpace;
        _parentKlass = genericKlass._parentKlass;
        _klassScope = genericKlass._klassScope;
        _baseKlass = genericKlass._baseKlass;
        this.setLoaded();
    }

    @Override
    public String toString() {
        return ("<DFKlass("+this.getFullName()+")>");
    }

    public String getTypeName() {
        String name = "L"+this.getFullName();
        if (_mapTypes != null && 0 < _mapTypes.length) {
            name = name + DFParamKlass.getParamNames(_mapTypes);
        }
        return name+";";
    }

    public boolean equals(DFType type) {
        return (this == type);
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

    public DFParamKlass parameterize(DFType[] paramTypes) {
        assert _loaded;
        assert this.isParameterized();
        String name = _name + DFParamKlass.getParamNames(paramTypes);
        try {
            return (DFParamKlass)_klassSpace.getKlass(name);
        } catch (TypeNotFound e) {
            DFParamKlass klass = new DFParamKlass(name, this, _mapTypes, paramTypes);
            _klassSpace.addKlass(klass);
            _paramKlasses.add(klass);
            return klass;
        }
    }

    public DFParamKlass[] getParamKlasses() {
        DFParamKlass[] klasses = new DFParamKlass[_paramKlasses.size()];
        _paramKlasses.toArray(klasses);
        return klasses;
    }

    public void setMapTypes(List<TypeParameter> tps) {
        // Get type parameters.
        _mapTypes = new DFMapType[tps.size()];
        for (int i = 0; i < tps.size(); i++) {
            TypeParameter tp = tps.get(i);
            String id = tp.getName().getIdentifier();
            DFMapType pt = _klassSpace.createMapType(id);
            pt.setTree(tp);
            _mapTypes[i] = pt;
        }
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

    public DFKlass getBaseKlass() {
        return _baseKlass;
    }

    public DFKlass[] getBaseIfaces() {
        return _baseIfaces;
    }

    public boolean isEnum() {
        return (_baseKlass instanceof DFParamKlass &&
                ((DFParamKlass)_baseKlass).getGeneric() ==
                DFBuiltinTypes.getEnumKlass());
    }

    public boolean isParameterized() {
        return (_mapTypes != null && 0 < _mapTypes.length);
    }

    public String getFullName() {
        return _typeSpace.getFullName()+_name;
    }

    public DFMethod getInitializer() {
        return _initializer;
    }

    public DFMethod getConstructor() {
        if (_constructor != null) {
            return _constructor;
        }
        return _baseKlass.getConstructor();
    }

    public int isSubclassOf(DFKlass klass, Map<DFMapType, DFType> typeMap) {
        if (this == klass) return 0;
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

    protected DFRef lookupField(String id)
        throws VariableNotFound {
        assert _loaded;
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
        assert _loaded;
	return _fields;
    }

    public List<DFMethod> getMethods() {
        assert _loaded;
	return _methods;
    }

    private DFMethod lookupMethod1(SimpleName name, DFType[] argTypes) {
        String id = name.getIdentifier();
        int bestDist = -1;
        DFMethod bestMethod = null;
        for (DFMethod method : this.getMethods()) {
            int dist = method.canAccept(id, argTypes);
            if (dist < 0) continue;
            if (bestDist < 0 || dist < bestDist) {
                bestDist = dist;
                bestMethod = method;
            }
        }
        return bestMethod;
    }

    public DFMethod lookupMethod(SimpleName name, DFType[] argTypes)
        throws MethodNotFound {
        assert _loaded;
        DFMethod method = this.lookupMethod1(name, argTypes);
        if (method != null) {
            return method;
        }
        if (_parentKlass != null) {
            try {
                return _parentKlass.lookupMethod(name, argTypes);
            } catch (MethodNotFound e) {
            }
        }
        if (_baseKlass != null) {
            try {
                return _baseKlass.lookupMethod(name, argTypes);
            } catch (MethodNotFound e) {
            }
        }
        if (_baseIfaces != null) {
            for (DFKlass iface : _baseIfaces) {
                try {
                    return iface.lookupMethod(name, argTypes);
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
        DFMethodType methodType) {
        return this.addMethod(
            new DFMethod(this, methodSpace, id, callStyle, methodType));
    }

    private DFMethod addMethod(DFMethod method) {
        //Logger.info("DFKlass.addMethod:", method);
        _methods.add(method);
        return method;
    }

    public void addMethodScope(ASTNode ast, DFLocalVarScope scope) {
        _ast2scope.put(Utils.encodeASTNode(ast), scope);
    }

    private DFLocalVarScope getMethodScope(ASTNode ast) {
	String key = Utils.encodeASTNode(ast);
        assert _ast2scope.containsKey(key);
        return _ast2scope.get(key);
    }

    public void addOverrides() {
        assert _loaded;
        for (DFMethod method : getMethods()) {
            if (_baseKlass != null) {
                _baseKlass.overrideMethod(method, 0);
            }
            if (_baseIfaces != null) {
                for (DFKlass iface : _baseIfaces) {
                    iface.overrideMethod(method, 0);
                }
            }
        }
    }

    private void overrideMethod(DFMethod method1, int level) {
	level++;
        for (DFMethod method0 : getMethods()) {
            if (method0.equals(method1)) {
                method0.addOverride(method1, level);
                break;
            }
        }
        if (_baseKlass != null) {
            _baseKlass.overrideMethod(method1, level);
        }
        if (_baseIfaces != null) {
            for (DFKlass iface : _baseIfaces) {
                iface.overrideMethod(method1, level);
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

    public DFTypeFinder getFinder() {
        return _finder.extend(this);
    }

    public void setFinder(DFTypeFinder finder) {
        //assert _finder == null || _finder == finder;
        _finder = finder;
    }
    public void setTree(ASTNode ast) {
        _ast = ast;
    }
    public void setJarPath(String jarPath, String filePath) {
        _jarPath = jarPath;
        _filePath = filePath;
    }

    protected void setLoaded() {
        assert !_loaded;
        _loaded = true;
    }

    public void load()
        throws TypeNotFound {
        this.load(_finder);
    }
    public void load(DFTypeFinder finder)
        throws TypeNotFound {
        if (_loaded) return;
        this.setLoaded();
        assert finder != null;
        assert _ast != null || _jarPath != null;
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

    private static String getSignature(Attribute[] attrs) {
        for (Attribute attr : attrs) {
            if (attr instanceof org.apache.bcel.classfile.Signature) {
                return ((org.apache.bcel.classfile.Signature)attr).getSignature();
            }
        }
        return null;
    }

    private void buildFromJKlass(DFTypeFinder finder, JavaClass jklass)
        throws TypeNotFound {
        // Setup a temporary finder for looking up a base class
        // that might refer to the parent/inner classes.
        DFTypeFinder finder0 = finder;
        if (_parentKlass != null) {
            finder0 = finder0.extend(_parentKlass);
        }
        finder0 = new DFTypeFinder(finder0, _klassSpace);
        String sig = getSignature(jklass.getAttributes());
        if (sig != null) {
            //Logger.info("jklass:", jklass.getClassName(), jklass.isEnum(), sig);
	    JNITypeParser parser = new JNITypeParser(sig);
	    _mapTypes = JNITypeParser.getMapTypes(sig, _klassSpace);
	    if (_mapTypes != null) {
		parser.buildMapTypes(finder0, _mapTypes);
	    }
	    _baseKlass = (DFKlass)parser.getType(finder0);
	    finder = finder.extend(_baseKlass);
	    List<DFKlass> ifaces = new ArrayList<DFKlass>();
	    for (;;) {
		DFKlass iface = (DFKlass)parser.getType(finder0);
		if (iface == null) break;
		ifaces.add(iface);
		finder = finder.extend(iface);
	    }
	    _baseIfaces = new DFKlass[ifaces.size()];
	    ifaces.toArray(_baseIfaces);
        } else {
	    String superClass = jklass.getSuperclassName();
	    if (superClass != null && !superClass.equals(jklass.getClassName())) {
		_baseKlass = finder0.lookupKlass(superClass);
		finder = finder.extend(_baseKlass);
	    }
	    String[] ifaces = jklass.getInterfaceNames();
	    if (ifaces != null) {
                DFKlass[] baseIfaces = new DFKlass[ifaces.length];
		for (int i = 0; i < ifaces.length; i++) {
		    DFKlass iface = finder0.lookupKlass(ifaces[i]);
		    baseIfaces[i] = iface;
		    finder = finder.extend(iface);
		}
		_baseIfaces = baseIfaces;
	    }
	}
        // Setup a actual finder where the parent/inner classes
        // precede its base class/interfaces.
        finder = new DFTypeFinder(finder, _klassSpace);
        if (_parentKlass != null) {
            finder = finder.extend(_parentKlass);
        }
        for (Field fld : jklass.getFields()) {
            if (fld.isPrivate()) continue;
            sig = getSignature(fld.getAttributes());
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
        for (Method meth : jklass.getMethods()) {
            if (meth.isPrivate()) continue;
            sig = getSignature(meth.getAttributes());
	    DFMethodType methodType;
            DFTypeSpace methodSpace = new DFTypeSpace(_klassSpace, meth.getName());
	    if (sig != null) {
                //Logger.info("meth:", meth.getName(), sig);
		JNITypeParser parser = new JNITypeParser(sig);
                DFMapType[] mapTypes = JNITypeParser.getMapTypes(sig, methodSpace);
		finder = new DFTypeFinder(finder, methodSpace);
		if (mapTypes != null) {
		    parser.buildMapTypes(finder, mapTypes);
		}
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
            if (meth.getName().equals("<init>")) {
                _constructor = this.addMethod(
                    methodSpace, meth.getName(), DFCallStyle.Constructor, methodType);
            } else {
                DFCallStyle callStyle = (meth.isStatic())?
                    DFCallStyle.StaticMethod : DFCallStyle.InstanceMethod;
                this.addMethod(
                    methodSpace, meth.getName(), callStyle, methodType);
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected void buildFromTree(DFTypeFinder finder, ASTNode ast)
        throws UnsupportedSyntax, TypeNotFound {
        if (ast instanceof AbstractTypeDeclaration) {
            this.buildAbstTypeDecl(finder, (AbstractTypeDeclaration)ast);

        } else if (ast instanceof AnonymousClassDeclaration) {
            AnonymousClassDeclaration decl = (AnonymousClassDeclaration)ast;
            this.buildDecls(finder, decl.bodyDeclarations());
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
        //Logger.info("DFKlass.build:", this, ":", typeDecl.getName());
        // Setup a temporary finder for looking up a base class
        // that might refer to the parent/inner classes.
        DFTypeFinder finder0 = finder;
        if (_parentKlass != null) {
            finder0 = finder0.extend(_parentKlass);
        }
        finder0 = new DFTypeFinder(finder0, _klassSpace);
        try {
            // Get superclass.
	    if (_mapTypes != null) {
                for (DFMapType pt : _mapTypes) {
                    pt.load(finder0);
                }
	    }
            Type superClass = typeDecl.getSuperclassType();
            if (superClass != null) {
                _baseKlass = finder0.resolveKlass(superClass);
                //Logger.info("DFKlass.build:", this, "extends", _baseKlass);
                finder = finder.extend(_baseKlass);
            } else {
                _baseKlass = DFBuiltinTypes.getObjectKlass();
            }
            // Get interfaces.
            List<Type> ifaces = typeDecl.superInterfaceTypes();
            DFKlass[] baseIfaces = new DFKlass[ifaces.size()];
            for (int i = 0; i < ifaces.size(); i++) {
		DFKlass iface = finder0.resolveKlass(ifaces.get(i));
                //Logger.info("DFKlass.build:", this, "implements", iface);
                baseIfaces[i] = iface;
                finder = finder.extend(iface);
            }
            _baseIfaces = baseIfaces;
            // Setup a actual finder where the parent/inner classes
            // precede its base class/interfaces.
            if (_parentKlass != null) {
                finder = finder.extend(_parentKlass);
            }
            finder = new DFTypeFinder(finder, _klassSpace);
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
        //Logger.info("DFKlass.build:", this, ":", enumDecl.getName());
        // Setup a temporary finder for looking up a base class
        // that might refer to the parent/inner classes.
        DFTypeFinder finder0 = finder;
        if (_parentKlass != null) {
            finder0 = finder0.extend(_parentKlass);
        }
        finder0 = new DFTypeFinder(finder0, _klassSpace);
        // Get superclass.
        try {
            DFKlass enumKlass = DFBuiltinTypes.getEnumKlass();
            _baseKlass = enumKlass.parameterize(new DFType[] { this });
            finder = finder.extend(_baseKlass);
            // Get interfaces.
            List<Type> ifaces = enumDecl.superInterfaceTypes();
            DFKlass[] baseIfaces = new DFKlass[ifaces.size()];
            for (int i = 0; i < ifaces.size(); i++) {
		DFKlass iface = finder0.resolveKlass(ifaces.get(i));
                baseIfaces[i] = iface;
                finder = finder.extend(iface);
            }
            _baseIfaces = baseIfaces;
            // Get constants.
            for (EnumConstantDeclaration econst :
                     (List<EnumConstantDeclaration>) enumDecl.enumConstants()) {
                this.addField(econst.getName(), true, this);
            }
            // Setup a actual finder where the parent/inner classes
            // precede its base class/interfaces.
            if (_parentKlass != null) {
                finder = finder.extend(_parentKlass);
            }
            finder = new DFTypeFinder(finder, _klassSpace);
            this.buildDecls(finder, enumDecl.bodyDeclarations());
            // Enum has a special method "values()".
            this.addMethod(
                null, "values", DFCallStyle.InstanceMethod,
                new DFMethodType(new DFType[] {}, new DFArrayType(this, 1)));
        } catch (TypeNotFound e) {
            e.setAst(enumDecl);
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private void buildAnnotTypeDecl(
        DFTypeFinder finder, AnnotationTypeDeclaration annotTypeDecl)
        throws UnsupportedSyntax, TypeNotFound {
        //Logger.info("DFKlass.build:", this, ":", annotTypeDecl.getName());
        _baseKlass = DFBuiltinTypes.getObjectKlass();
        finder = finder.extend(_baseKlass);
        if (_parentKlass != null) {
            finder = finder.extend(_parentKlass);
        }
        finder = new DFTypeFinder(finder, _klassSpace);
        try {
            // Lookup child klasses.
            this.buildDecls(finder, annotTypeDecl.bodyDeclarations());
        } catch (TypeNotFound e) {
            e.setAst(annotTypeDecl);
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private void buildDecls(DFTypeFinder finder, List<BodyDeclaration> decls)
        throws UnsupportedSyntax, TypeNotFound {
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
                List<TypeParameter> tps = decl.typeParameters();
                String id = Utils.encodeASTNode(decl);
                DFTypeSpace methodSpace = _klassSpace.lookupSpace(id);
                finder = new DFTypeFinder(finder, methodSpace);
                for (int i = 0; i < tps.size(); i++) {
                    TypeParameter tp = tps.get(i);
                    String id2 = tp.getName().getIdentifier();
                    DFMapType pt = methodSpace.createMapType(id2);
                    pt.setTree(tp);
                    pt.load(finder);
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
                    methodSpace, name, callStyle,
                    new DFMethodType(argTypes, returnType));
		if (decl.getBody() != null) {
		    method.setSrcScope(this.getMethodScope(decl));
		    method.setFinder(finder);
		    method.setTree(decl);
		}
                if (decl.isConstructor()) {
                    _constructor = method;
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
		    new DFMethodType(new DFType[] {}, DFBasicType.VOID));
		_initializer.setSrcScope(this.getMethodScope(initializer));
		_initializer.setFinder(finder);
		_initializer.setTree(initializer);

            } else {
                throw new UnsupportedSyntax(body);
            }
        }
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
