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
    protected DFKlass _baseKlass = null;
    protected DFKlass[] _baseIfaces = null;

    private boolean _loaded;
    private DFTypeSpace _typeSpace;
    private DFTypeSpace _klassSpace;
    private DFKlass _parentKlass;
    private DFVarScope _klassScope = null;

    private DFParamType[] _paramTypes = null;
    private Map<String, DFParamKlass> _paramKlasses =
        new HashMap<String, DFParamKlass>();

    private DFMethod _initializer;
    private List<DFVarRef> _fields =
        new ArrayList<DFVarRef>();
    private List<DFMethod> _methods =
        new ArrayList<DFMethod>();
    private Map<String, DFMethod> _ast2method =
        new HashMap<String, DFMethod>();

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
        _loaded = false;
        if (parentScope != null) {
            _klassScope = new DFKlassScope(this, parentScope, name);
        }
        _initializer = this.addMethod(
            null, "<init>", DFCallStyle.Initializer,
            new DFMethodType(new DFType[] {}, DFBasicType.VOID));
    }

    protected DFKlass(String name, DFKlass genericKlass) {
        _name = name;
        _typeSpace = genericKlass._typeSpace;
        _klassSpace = genericKlass._klassSpace;
        _parentKlass = genericKlass._parentKlass;
        _klassScope = genericKlass._klassScope;
        _baseKlass = genericKlass._baseKlass;
        _initializer = genericKlass._initializer;
        _loaded = true;
    }

    @Override
    public String toString() {
        return ("<DFKlass("+this.getFullName()+")>");
    }

    public String getTypeName() {
        String name = "L"+this.getFullName();
        if (_paramTypes != null && 0 < _paramTypes.length) {
            name += DFParamKlass.getParamName(_paramTypes);
        }
        return name+";";
    }

    public boolean equals(DFType type) {
        return (this == type);
    }

    public int canConvertFrom(DFType type)
    {
        if (type instanceof DFNullType) return 0;
	if (type instanceof DFArrayType) {
	    type = DFRootTypeSpace.getObjectKlass();
	} else if (type == DFBasicType.BYTE) {
	    type = DFRootTypeSpace.getByteKlass();
	} else if (type == DFBasicType.CHAR) {
	    type = DFRootTypeSpace.getCharacterKlass();
	} else if (type == DFBasicType.SHORT) {
	    type = DFRootTypeSpace.getShortKlass();
	} else if (type == DFBasicType.INT) {
	    type = DFRootTypeSpace.getIntegerKlass();
	} else if (type == DFBasicType.LONG) {
	    type = DFRootTypeSpace.getLongKlass();
	} else if (type == DFBasicType.FLOAT) {
	    type = DFRootTypeSpace.getFloatKlass();
	} else if (type == DFBasicType.DOUBLE) {
	    type = DFRootTypeSpace.getDoubleKlass();
	} else if (type == DFBasicType.BOOLEAN) {
	    type = DFRootTypeSpace.getBooleanKlass();
	}
        if (!(type instanceof DFKlass)) return -1;
        // type is-a this.
        return ((DFKlass)type).isSubclassOf(this);
    }

    public DFParamKlass getParamKlass(DFType[] mapTypes) {
        String name = _name+DFParamKlass.getParamName(mapTypes);
        DFParamKlass klass = _paramKlasses.get(name);
        if (klass == null) {
            klass = new DFParamKlass(name, this, _paramTypes, mapTypes);
            _paramKlasses.put(name, klass);
        }
        return klass;
    }

    public void addParamTypes(List<TypeParameter> tps) {
        // Get type parameters.
        _paramTypes = new DFParamType[tps.size()];
        for (int i = 0; i < tps.size(); i++) {
            TypeParameter tp = tps.get(i);
            String id = tp.getName().getIdentifier();
            DFParamType pt = _klassSpace.createParamType(id);
            pt.setAST(tp);
            _paramTypes[i] = pt;
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

    public boolean isEnum() {
        return (_baseKlass instanceof DFParamKlass &&
                ((DFParamKlass)_baseKlass).getGeneric() ==
                DFRootTypeSpace.getEnumKlass());
    }

    public String getFullName() {
        return _typeSpace.getFullName()+_name;
    }

    public DFMethod getInitializer() {
        return _initializer;
    }

    // Special treatment of circular classes. (e.g. java.lang.Enum)
    protected boolean isCircular() {
        if (_paramTypes != null) {
            for (DFParamType pt : _paramTypes) {
                DFKlass klass = pt.getBaseKlass();
                if (klass instanceof DFParamKlass) {
                    if (((DFParamKlass)klass).getGeneric() == this) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public int isSubclassOf(DFKlass klass) {
        if (this == klass) return 0;
        if (_baseKlass != null) {
            int dist = _baseKlass.isSubclassOf(klass);
            if (0 <= dist) return dist+1;
        }
        if (_baseIfaces != null) {
            for (DFKlass iface : _baseIfaces) {
                int dist = iface.isSubclassOf(klass);
                if (0 <= dist) return dist+1;
            }
        }
        return -1;
    }

    protected DFVarRef lookupField(String id)
        throws VariableNotFound {
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

    public DFVarRef lookupField(SimpleName name)
        throws VariableNotFound {
        return this.lookupField(name.getIdentifier());
    }

    protected List<DFVarRef> getFields() {
	return _fields;
    }

    protected List<DFMethod> getMethods() {
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
        throw new MethodNotFound(name.getIdentifier());
    }

    public DFMethod getMethodByAST(ASTNode ast) {
        return _ast2method.get(Utils.encodeASTNode(ast));
    }

    private DFVarRef addField(
        SimpleName name, boolean isStatic, DFType type) {
        return this.addField(name.getIdentifier(), isStatic, type);
    }

    public DFVarRef addField(
        String id, boolean isStatic, DFType type) {
        assert _klassScope != null;
        DFVarRef ref = _klassScope.addRef("."+id, type);
        //Logger.info("DFKlass.addField: "+ref);
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
        //Logger.info("DFKlass.addMethod: "+method);
        _methods.add(method);
        return method;
    }

    public void addOverrides() {
        for (DFMethod method : _methods) {
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
        for (DFMethod method0 : _methods) {
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

    public DFTypeFinder addFinders(DFTypeFinder finder) {
        if (_baseKlass != null) {
            finder = _baseKlass.addFinders(finder);
        }
        if (_baseIfaces != null) {
            for (DFKlass iface : _baseIfaces) {
                if (iface != null) {
                    finder = iface.addFinders(finder);
                }
            }
        }
        if (_klassSpace != null) {
            finder = new DFTypeFinder(finder, _klassSpace);
        }
        return finder;
    }

    public void setAST(ASTNode ast) {
        _ast = ast;
    }

    public void setJarPath(String jarPath, String filePath) {
        _jarPath = jarPath;
        _filePath = filePath;
    }

    public void setLoaded() {
        _loaded = true;
    }

    public void load(DFTypeFinder finder)
        throws TypeNotFound {
        if (_loaded) return;
        this.setLoaded();
        assert _ast != null || _jarPath != null;
        if (_ast != null) {
            try {
                this.buildFromTree(finder, _ast);
            } catch (UnsupportedSyntax e) {
                String astName = e.ast.getClass().getName();
                Logger.error("Error: Unsupported syntax: "+e.name+" ("+astName+")");
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
                Logger.error("Error: Not found: "+_jarPath+"/"+_filePath);
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
        String sig = getSignature(jklass.getAttributes());
        DFTypeFinder finder2 = finder;
        if (sig != null) {
            //Logger.info("jklass: "+jklass.getClassName()+","+jklass.isEnum()+","+sig);
            finder2 = new DFTypeFinder(finder2, _klassSpace);
	    JNITypeParser parser = new JNITypeParser(sig);
	    _paramTypes = JNITypeParser.getParamTypes(sig, _klassSpace);
	    if (_paramTypes != null) {
		parser.buildParamTypes(finder2, _paramTypes);
	    }
	    _baseKlass = (DFKlass)parser.getType(finder2);
	    finder2 = _baseKlass.addFinders(finder2);
	    List<DFKlass> ifaces = new ArrayList<DFKlass>();
	    for (;;) {
		DFKlass iface = (DFKlass)parser.getType(finder2);
		if (iface == null) break;
		finder2 = iface.addFinders(finder2);
		ifaces.add(iface);
	    }
	    _baseIfaces = new DFKlass[ifaces.size()];
	    ifaces.toArray(_baseIfaces);
        } else {
	    String superClass = jklass.getSuperclassName();
	    if (superClass != null && !superClass.equals(jklass.getClassName())) {
		_baseKlass = finder2.lookupKlass(superClass);
		finder2 = _baseKlass.addFinders(finder2);
	    }
	    String[] ifaces = jklass.getInterfaceNames();
	    if (ifaces != null) {
		_baseIfaces = new DFKlass[ifaces.length];
		for (int i = 0; i < ifaces.length; i++) {
		    DFKlass iface = finder2.lookupKlass(ifaces[i]);
		    _baseIfaces[i] = iface;
		    finder2 = iface.addFinders(finder2);
		}
	    }
	}
	finder = this.addFinders(finder);
        for (Field fld : jklass.getFields()) {
            if (fld.isPrivate()) continue;
            sig = getSignature(fld.getAttributes());
	    DFType type;
	    if (sig != null) {
                //Logger.info("fld: "+fld.getName()+","+sig);
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
            DFTypeSpace methodSpace = null;
	    if (sig != null) {
                //Logger.info("meth: "+meth.getName()+","+sig);
                methodSpace = new DFTypeSpace(_klassSpace, meth.getName());
		finder = new DFTypeFinder(finder, methodSpace);
		JNITypeParser parser = new JNITypeParser(sig);
                DFParamType[] paramTypes = JNITypeParser.getParamTypes(sig, methodSpace);
		if (paramTypes != null) {
		    parser.buildParamTypes(finder, paramTypes);
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
            DFCallStyle callStyle;
            if (meth.getName().equals("<init>")) {
                callStyle = DFCallStyle.Constructor;
            } else {
                callStyle = (meth.isStatic())?
                    DFCallStyle.StaticMethod : DFCallStyle.InstanceMethod;
            }
	    this.addMethod(
                methodSpace, meth.getName(), callStyle, methodType);
        }
    }

    @SuppressWarnings("unchecked")
    protected void buildFromTree(DFTypeFinder finder, ASTNode ast)
        throws UnsupportedSyntax, TypeNotFound {
        if (ast instanceof AbstractTypeDeclaration) {
            this.build(finder, (AbstractTypeDeclaration)ast);

        } else if (ast instanceof AnonymousClassDeclaration) {
            AnonymousClassDeclaration decl = (AnonymousClassDeclaration)ast;
            this.build(finder, decl.bodyDeclarations());
        }
    }

    private void build(DFTypeFinder finder, AbstractTypeDeclaration abstTypeDecl)
        throws UnsupportedSyntax, TypeNotFound {
        if (abstTypeDecl instanceof TypeDeclaration) {
            this.build(finder, (TypeDeclaration)abstTypeDecl);

        } else if (abstTypeDecl instanceof EnumDeclaration) {
            this.build(finder, (EnumDeclaration)abstTypeDecl);

        } else if (abstTypeDecl instanceof AnnotationTypeDeclaration) {
            this.build(finder, (AnnotationTypeDeclaration)abstTypeDecl);
        }
    }

    @SuppressWarnings("unchecked")
    private void build(DFTypeFinder finder, TypeDeclaration typeDecl)
        throws UnsupportedSyntax, TypeNotFound {
        //Logger.info("DFKlass.build: "+this+": "+typeDecl.getName());
        // Get superclass.
        try {
            finder = new DFTypeFinder(finder, _klassSpace);
            Type superClass = typeDecl.getSuperclassType();
            if (superClass != null) {
                _baseKlass = finder.resolveKlass(superClass);
                //Logger.info("DFKlass.build: "+this+" extends "+_baseKlass);
                finder = _baseKlass.addFinders(finder);
            } else {
                _baseKlass = DFRootTypeSpace.getObjectKlass();
            }
            // Get interfaces.
            List<Type> ifaces = typeDecl.superInterfaceTypes();
            _baseIfaces = new DFKlass[ifaces.size()];
            for (int i = 0; i < ifaces.size(); i++) {
		DFKlass iface = finder.resolveKlass(ifaces.get(i));
                //Logger.info("DFKlass.build: "+this+" implements "+iface);
                _baseIfaces[i] = iface;
                finder = iface.addFinders(finder);
            }
            // Lookup child klasses.
            this.build(finder, typeDecl.bodyDeclarations());
        } catch (TypeNotFound e) {
            e.setAst(typeDecl);
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private void build(DFTypeFinder finder, EnumDeclaration enumDecl)
        throws UnsupportedSyntax, TypeNotFound {
        //Logger.info("DFKlass.build: "+this+": "+enumDecl.getName());
        // Get superclass.
        try {
            finder = new DFTypeFinder(finder, _klassSpace);
            DFKlass enumKlass = DFRootTypeSpace.getEnumKlass();
            _baseKlass = enumKlass.getParamKlass(new DFType[] { this });
            finder = _baseKlass.addFinders(finder);
            // Get interfaces.
            List<Type> ifaces = enumDecl.superInterfaceTypes();
            _baseIfaces = new DFKlass[ifaces.size()];
            for (int i = 0; i < ifaces.size(); i++) {
		DFKlass iface = finder.resolveKlass(ifaces.get(i));
                _baseIfaces[i] = iface;
                finder = iface.addFinders(finder);
            }
            // Get constants.
            for (EnumConstantDeclaration econst :
                     (List<EnumConstantDeclaration>) enumDecl.enumConstants()) {
                this.addField(econst.getName(), true, this);
            }
            // Lookup child klasses.
            this.build(finder, enumDecl.bodyDeclarations());
        } catch (TypeNotFound e) {
            e.setAst(enumDecl);
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private void build(DFTypeFinder finder, AnnotationTypeDeclaration annotTypeDecl)
        throws UnsupportedSyntax, TypeNotFound {
        //Logger.info("DFKlass.build: "+this+": "+annotTypeDecl.getName());
        // Get superclass.
        try {
            finder = new DFTypeFinder(finder, _klassSpace);
            _baseKlass = DFRootTypeSpace.getObjectKlass();
            finder = _baseKlass.addFinders(finder);
            // Lookup child klasses.
            this.build(finder, annotTypeDecl.bodyDeclarations());
        } catch (TypeNotFound e) {
            e.setAst(annotTypeDecl);
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private void build(DFTypeFinder finder, List<BodyDeclaration> decls)
        throws UnsupportedSyntax, TypeNotFound {
        for (BodyDeclaration body : decls) {
            if (body instanceof AbstractTypeDeclaration) {
                AbstractTypeDeclaration decl = (AbstractTypeDeclaration)body;
                DFKlass klass = _klassSpace.getKlass(decl.getName());
                klass.build(finder, decl);

            } else if (body instanceof FieldDeclaration) {
                FieldDeclaration decl = (FieldDeclaration)body;
                DFType type = finder.resolve(decl.getType());
                for (VariableDeclarationFragment frag :
                         (List<VariableDeclarationFragment>) decl.fragments()) {
                    int ndims = frag.getExtraDimensions();
                    this.addField(frag.getName(), isStatic(decl),
                                  (ndims != 0)? new DFArrayType(type, ndims) : type);
                }

            } else if (body instanceof MethodDeclaration) {
                MethodDeclaration decl = (MethodDeclaration)body;
                List<TypeParameter> tps = decl.typeParameters();
                DFTypeFinder finder2 = finder;
                DFTypeSpace methodSpace = null;
                if (0 < tps.size()) {
                    String name = decl.getName().getIdentifier();
                    methodSpace = new DFTypeSpace(_klassSpace, name);
                    finder2 = new DFTypeFinder(finder, methodSpace);
                    for (int i = 0; i < tps.size(); i++) {
                        TypeParameter tp = tps.get(i);
                        String id = tp.getName().getIdentifier();
                        DFParamType pt = methodSpace.createParamType(id);
                        pt.setAST(tp);
                    }
                }
                DFType[] argTypes = finder2.resolveArgs(decl);
                DFType returnType;
                String name;
                DFCallStyle callStyle;
                if (decl.isConstructor()) {
                    returnType = this;
                    name = "<init>";
                    callStyle = DFCallStyle.Constructor;
                } else {
                    returnType = finder2.resolve(decl.getReturnType2());
                    name = decl.getName().getIdentifier();
                    callStyle = (isStatic(decl))?
                        DFCallStyle.StaticMethod : DFCallStyle.InstanceMethod;
                }
                DFMethod method = this.addMethod(
                    methodSpace, name, callStyle,
                    new DFMethodType(argTypes, returnType));
                _ast2method.put(Utils.encodeASTNode(decl), method);

            } else if (body instanceof EnumConstantDeclaration) {

            } else if (body instanceof AnnotationTypeMemberDeclaration) {
                AnnotationTypeMemberDeclaration decl = (AnnotationTypeMemberDeclaration)body;
                DFType type = finder.resolve(decl.getType());
                this.addField(decl.getName(), isStatic(decl), type);

            } else if (body instanceof Initializer) {

            } else {
                throw new UnsupportedSyntax(body);
            }
        }
    }

    // DFKlassScope
    private class DFKlassScope extends DFVarScope {

        private DFKlass _klass;
        private DFVarRef _this;

        public DFKlassScope(DFKlass klass, DFVarScope parent, String id) {
            super(parent, id);
            _klass = klass;
            _this = this.addRef("#this", klass);
        }

        public String getFullName() {
            return _klass.getFullName();
        }

        public DFVarRef lookupThis() {
            return _this;
        }

        @Override
        protected DFVarRef lookupVar1(String id)
            throws VariableNotFound {
            // try local variables first.
            try {
                return super.lookupVar1(id);
            } catch (VariableNotFound e) {
                // try field names.
                return _klass.lookupField(id);
            }
        }

        // dumpContents (for debugging)
        public void dumpContents(PrintStream out, String indent) {
            super.dumpContents(out, indent);
            for (DFMethod method : _methods) {
                out.println(indent+"defined: "+method);
            }
        }
    }
}
