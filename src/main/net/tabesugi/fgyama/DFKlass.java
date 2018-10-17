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

    private DFTypeSpace _typeSpace;
    private DFTypeSpace _childSpace = null;
    private DFVarScope _scope = null;

    private DFParamType[] _paramTypes = null;
    private Map<String, DFParamKlass> _paramKlasses =
        new HashMap<String, DFParamKlass>();

    private List<DFMethod> _methods =
        new ArrayList<DFMethod>();
    private Map<String, DFMethod> _ast2method =
        new HashMap<String, DFMethod>();

    private String _jarPath = null;
    private String _filePath = null;
    private boolean _loaded = true;

    public DFKlass(
        String name, DFTypeSpace typeSpace,
        DFTypeSpace childSpace, DFVarScope parentScope,
        DFKlass baseKlass) {
        _name = name;
        _typeSpace = typeSpace;
        _childSpace = childSpace;
        _baseKlass = baseKlass;
        if (parentScope != null) {
            _scope = new DFKlassScope(this, parentScope, name);
        }
    }

    protected DFKlass(String name, DFKlass genericKlass) {
        _name = name;
        _typeSpace = genericKlass._typeSpace;
        _childSpace = genericKlass._childSpace;
        _scope = genericKlass._scope;
        _baseKlass = genericKlass._baseKlass;
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
        if (!(type instanceof DFKlass)) return -1;
        return ((DFKlass)type).isSubclassOf(this);
    }

    public void setParamTypes(DFParamType[] paramTypes) {
        _paramTypes = paramTypes;
    }

    public DFParamKlass getParamKlass(DFType[] argTypes) {
        String name = DFParamKlass.getParamName(argTypes)+_name;
        DFParamKlass klass = _paramKlasses.get(name);
        if (klass == null) {
            klass = new DFParamKlass(name, this, argTypes);
            _paramKlasses.put(name, klass);
        }
        return klass;
    }

    public String getKlassName() {
        return _name;
    }

    public DFTypeSpace getChildSpace() {
        return _childSpace;
    }

    public DFVarScope getScope() {
        return _scope;
    }

    public DFKlass getBase() {
        return _baseKlass;
    }

    public boolean isEnum() {
        return _baseKlass == DFRootTypeSpace.ENUM_KLASS;
    }

    public String getFullName() {
        return _typeSpace.getFullName()+_name;
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
        if (_scope != null) {
            try {
                return _scope.lookupRef("."+id);
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

    private DFMethod lookupMethod1(SimpleName name, DFType[] argTypes) {
        String id = name.getIdentifier();
        int bestDist = -1;
        DFMethod bestMethod = null;
        for (DFMethod method : _methods) {
            int dist = method.canAccept(id, argTypes);
            if (dist < 0) continue;
            if (bestDist < 0 || dist < bestDist) {
                bestDist = dist;
                bestMethod = method;
            }
        }
        return bestMethod;
    }

    public DFMethod lookupMethod(SimpleName name, DFType[] argTypes) {
        DFKlass klass = this;
        while (klass != null) {
            DFMethod method = klass.lookupMethod1(name, argTypes);
            if (method != null) {
                return method;
            }
            klass = klass._baseKlass;
        }
        return null;
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
        assert _scope != null;
        DFVarRef ref = _scope.addRef("."+id, type);
        //Logger.info("DFKlass.addField: "+ref);
        return ref;
    }

    private DFMethod addMethod(
        DFTypeSpace methodSpace, SimpleName name, boolean isStatic,
        DFType[] argTypes, DFType returnType) {
        String id = name.getIdentifier();
        DFMethod method = new DFMethod(
            this, methodSpace, id, isStatic,
            new DFMethodType(argTypes, returnType));
        return this.addMethod(method);
    }

    private DFMethod addMethod(
        DFTypeSpace methodSpace, String id, boolean isStatic,
        DFType[] argTypes, DFType returnType) {
        DFMethod method = new DFMethod(
            this, methodSpace, id, isStatic,
            new DFMethodType(argTypes, returnType));
        return this.addMethod(method);
    }

    private DFMethod addMethod(DFMethod method) {
        //Logger.info("DFKlass.addMethod: "+method);
        _methods.add(method);
        return method;
    }

    public void addOverrides() {
        for (DFMethod method : _methods) {
            if (_baseKlass != null) {
                _baseKlass.overrideMethod(method);
            }
            if (_baseIfaces != null) {
                for (DFKlass iface : _baseIfaces) {
                    iface.overrideMethod(method);
                }
            }
        }
    }

    private void overrideMethod(DFMethod method1) {
        for (DFMethod method0 : _methods) {
            if (method0.equals(method1)) {
                method0.addOverride(method1);
                //Logger.info("DFKlass.overrideMethod: "+method0+" : "+method1);
                break;
            }
        }
        if (_baseKlass != null) {
            _baseKlass.overrideMethod(method1);
        }
        if (_baseIfaces != null) {
            for (DFKlass iface : _baseIfaces) {
                iface.overrideMethod(method1);
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

    public void setJarPath(String jarPath, String filePath) {
        _jarPath = jarPath;
        _filePath = filePath;
        _loaded = false;
    }

    public void load(DFTypeFinder finder) throws TypeNotFound {
        if (_loaded) return;
        _loaded = true;
        try {
            JarFile jarfile = new JarFile(_jarPath);
            try {
                JarEntry je = jarfile.getJarEntry(_filePath);
                InputStream strm = jarfile.getInputStream(je);
                JavaClass jklass = new ClassParser(strm, _filePath).parse();
                this.load(finder, jklass);
            } finally {
                jarfile.close();
            }
        } catch (IOException e) {
            Logger.error("Error: Not found: "+_jarPath+"/"+_filePath);
            throw new TypeNotFound(this.getFullName());
        }
    }

    private void load(DFTypeFinder finder, JavaClass jklass)
        throws TypeNotFound {
        String superClass = jklass.getSuperclassName();
        if (superClass != null && !superClass.equals(jklass.getClassName())) {
            _baseKlass = finder.lookupKlass(superClass);
        }
        String[] ifaces = jklass.getInterfaceNames();
        if (ifaces != null) {
            _baseIfaces = new DFKlass[ifaces.length];
            for (int i = 0; i < ifaces.length; i++) {
                _baseIfaces[i] = finder.lookupKlass(ifaces[i]);
            }
        }
        for (Field fld : jklass.getFields()) {
            if (fld.isPrivate()) continue;
            DFType type = finder.resolve(fld.getType());
            _scope.addRef("."+fld.getName(), type);
        }
        for (Method meth : jklass.getMethods()) {
            if (meth.isPrivate()) continue;
            org.apache.bcel.generic.Type[] args = meth.getArgumentTypes();
            DFType[] argTypes = new DFType[args.length];
            for (int i = 0; i < args.length; i++) {
                argTypes[i] = finder.resolve(args[i]);
            }
            DFType returnType = finder.resolve(meth.getReturnType());
            this.addMethod(null, meth.getName(), meth.isStatic(),
                           argTypes, returnType);
        }
    }

    public DFTypeFinder addFinders(DFTypeFinder finder) {
        if (_baseKlass != null) {
            finder = _baseKlass.addFinders(finder);
        }
        if (_baseIfaces != null) {
            for (DFKlass iface : _baseIfaces) {
                finder = iface.addFinders(finder);
            }
        }
        if (_childSpace != null) {
            finder = new DFTypeFinder(finder, _childSpace);
        }
        return finder;
    }

    @SuppressWarnings("unchecked")
    public void build(DFTypeFinder finder, TypeDeclaration typeDecl)
        throws UnsupportedSyntax, TypeNotFound {
        //Logger.info("DFKlass.build: "+this+": "+typeDecl.getName());
        // Get superclass.
        try {
            finder = new DFTypeFinder(finder, _childSpace);
            Type superClass = typeDecl.getSuperclassType();
            if (superClass != null) {
                _baseKlass = finder.resolveKlass(superClass);
                //Logger.info("DFKlass.build: "+this+" extends "+_baseKlass);
                finder = _baseKlass.addFinders(finder);
            } else {
                _baseKlass = DFRootTypeSpace.OBJECT_KLASS;
            }
            // Get interfaces.
            List<Type> ifaces = typeDecl.superInterfaceTypes();
            _baseIfaces = new DFKlass[ifaces.size()];
            for (int i = 0; i < ifaces.size(); i++) {
                _baseIfaces[i] = finder.resolveKlass(ifaces.get(i));
                finder = _baseIfaces[i].addFinders(finder);
            }
            // Get type parameters.
            List<TypeParameter> tps = typeDecl.typeParameters();
            for (int i = 0; i < _paramTypes.length; i++) {
                DFParamType pt = _paramTypes[i];
                pt.build(finder, tps.get(i));
            }
            // Lookup child klasses.
            for (BodyDeclaration body :
                     (List<BodyDeclaration>) typeDecl.bodyDeclarations()) {
                this.build(finder, body);
            }
        } catch (TypeNotFound e) {
            e.setAst(typeDecl);
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    public void build(DFTypeFinder finder, EnumDeclaration enumDecl)
        throws UnsupportedSyntax, TypeNotFound {
        //Logger.info("DFKlass.build: "+this+": "+enumDecl.getName());
        // Get superclass.
        try {
            finder = new DFTypeFinder(finder, _childSpace);
            _baseKlass = DFRootTypeSpace.ENUM_KLASS;
            // Get constants.
            for (EnumConstantDeclaration econst :
                     (List<EnumConstantDeclaration>) enumDecl.enumConstants()) {
                this.build(finder, econst);
            }
            // Get interfaces.
            List<Type> ifaces = enumDecl.superInterfaceTypes();
            _baseIfaces = new DFKlass[ifaces.size()];
            for (int i = 0; i < ifaces.size(); i++) {
                _baseIfaces[i] = finder.resolveKlass(ifaces.get(i));
            }
            // Lookup child klasses.
            for (BodyDeclaration body :
                     (List<BodyDeclaration>) enumDecl.bodyDeclarations()) {
                this.build(finder, body);
            }
        } catch (TypeNotFound e) {
            e.setAst(enumDecl);
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    public void build(DFTypeFinder finder, AnnotationTypeDeclaration annotTypeDecl)
        throws UnsupportedSyntax, TypeNotFound {
        //Logger.info("DFKlass.build: "+this+": "+annotTypeDecl.getName());
        // Get superclass.
        try {
            finder = new DFTypeFinder(finder, _childSpace);
            _baseKlass = DFRootTypeSpace.OBJECT_KLASS;
            // Lookup child klasses.
            for (BodyDeclaration body :
                     (List<BodyDeclaration>) annotTypeDecl.bodyDeclarations()) {
                this.build(finder, body);
            }
        } catch (TypeNotFound e) {
            e.setAst(annotTypeDecl);
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    public void build(DFTypeFinder finder, BodyDeclaration body)
        throws UnsupportedSyntax, TypeNotFound {
        assert body != null;

        if (body instanceof TypeDeclaration) {
            TypeDeclaration decl = (TypeDeclaration)body;
            DFKlass klass = _childSpace.getKlass(decl.getName());
            klass.build(finder, decl);

        } else if (body instanceof EnumDeclaration) {
            EnumDeclaration decl = (EnumDeclaration)body;
            DFKlass klass = _childSpace.getKlass(decl.getName());
            klass.build(finder, decl);

        } else if (body instanceof AnnotationTypeDeclaration) {
            AnnotationTypeDeclaration decl = (AnnotationTypeDeclaration)body;
            DFKlass klass = _childSpace.getKlass(decl.getName());
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
                methodSpace = new DFTypeSpace(_childSpace, name);
                finder2 = new DFTypeFinder(finder, methodSpace);
                for (int i = 0; i < tps.size(); i++) {
                    TypeParameter tp = tps.get(i);
                    String id = tp.getName().getIdentifier();
                    DFParamType pt = new DFParamType(id, methodSpace, i);
                    methodSpace.addParamType(id, pt);
                    pt.build(finder2, tp);
                }
            }
            DFType[] argTypes = finder2.resolveList(decl);
            DFType returnType;
            if (decl.isConstructor()) {
                returnType = this;
                // XXX treat method name specially.
            } else {
                returnType = finder2.resolve(decl.getReturnType2());
            }
            DFMethod method = this.addMethod(
                methodSpace, decl.getName(), isStatic(decl),
                argTypes, returnType);
            _ast2method.put(Utils.encodeASTNode(decl), method);

        } else if (body instanceof EnumConstantDeclaration) {
            EnumConstantDeclaration decl = (EnumConstantDeclaration)body;
            // XXX ignore AnonymousClassDeclaration
            this.addField(decl.getName(), false, this);

        } else if (body instanceof AnnotationTypeMemberDeclaration) {
            AnnotationTypeMemberDeclaration decl = (AnnotationTypeMemberDeclaration)body;
            DFType type = finder.resolve(decl.getType());
            this.addField(decl.getName(), isStatic(decl), type);

        } else if (body instanceof Initializer) {

        } else {
            throw new UnsupportedSyntax(body);
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

        public DFVarRef lookupVar(SimpleName name)
            throws VariableNotFound {
            // try local variables first.
            try {
                return super.lookupVar(name);
            } catch (VariableNotFound e) {
                // try field names.
                return _klass.lookupField(name.getIdentifier());
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
