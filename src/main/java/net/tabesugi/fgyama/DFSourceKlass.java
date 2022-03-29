//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import java.util.jar.*;
import javax.xml.stream.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFSourceKlass
//  DFKlass defined in source code.
//
//  Usage:
//    1. new DFSourceKlass()
//    2. initializeFinder(finder)
//    3. getXXX(), ...
//    4. listUsedKlasses(used)
//    5. listDefinedKlasses(defined)
//
//  Implement:
//    parameterize(paramTypes)
//    build()
//
public abstract class DFSourceKlass extends DFKlass {

    // These fields are set at the constructor.
    private String _filePath;
    private boolean _analyze;

    // This field is available after initializeFinder(). (Stage2)
    private boolean _loaded = false;
    private DFTypeFinder _finder = null;

    // The following fields are available after the klass is loaded. (Stage3)
    private boolean _interface = false;
    private DFKlass _baseKlass = null;
    private DFKlass[] _baseIfaces = null;
    private InitMethod _initMethod = null;

    // List of methods.
    private List<DFMethod> _methods =
        new ArrayList<DFMethod>();
    private Map<String, DFMethod> _id2method =
        new HashMap<String, DFMethod>();

    // List of fields.
    private List<FieldRef> _fields =
        new ArrayList<FieldRef>();
    private Map<String, FieldRef> _id2field =
        new HashMap<String, FieldRef>();

    // Normal constructor.
    protected DFSourceKlass(
        String name,
        DFTypeSpace outerSpace, DFSourceKlass outerKlass, DFVarScope outerScope,
        String filePath, boolean analyze) {
        super(name, outerSpace, outerKlass, outerScope);

        _filePath = filePath;
        _analyze = analyze;
    }

    // Constructor for a parameterized klass.
    protected DFSourceKlass(
        DFSourceKlass genericKlass, Map<String, DFKlass> paramTypes) {
        super(genericKlass, paramTypes);

        _filePath = genericKlass._filePath;
        _analyze = genericKlass._analyze;
        _finder = new DFTypeFinder(this, genericKlass._finder);
    }

    @Override
    public void writeXML(XMLStreamWriter writer)
        throws XMLStreamException {
        writer.writeAttribute("path", this.getFilePath());
        super.writeXML(writer);
    }

    @Override
    protected void dumpContents(PrintStream out, String indent) {
        super.dumpContents(out, indent);
        DFKlass baseKlass = this.getBaseKlass();
        if (baseKlass != null) {
            baseKlass.dump(out, indent);
        }
        DFKlass[] baseIfaces = this.getBaseIfaces();
        if (baseIfaces != null) {
            for (DFKlass iface : baseIfaces) {
                if (iface != null) {
                    iface.dump(out, indent);
                }
            }
        }
    }

    public String getFilePath() {
        return _filePath;
    }

    public boolean isAnalyze() {
        return _analyze;
    }

    public abstract ASTNode getAST();

    @Override
    public boolean isInterface() {
        this.load();
        return _interface;
    }

    @Override
    public boolean isEnum() {
        this.load();
        DFKlass baseKlass = this.getBaseKlass();
        return (baseKlass != null &&
                baseKlass.getGenericKlass() == DFBuiltinTypes.getEnumKlass());
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

    public DFMethod getInitMethod() {
        this.load();
        return _initMethod;
    }

    private FieldRef addField(
        DFType type, SimpleName name, boolean isStatic) {
        return this.addField(type, name.getIdentifier(), isStatic);
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

    public void overrideMethods() {
        // override the methods.
        DFKlass baseKlass = this.getBaseKlass();
        if (baseKlass != null) {
            this.overrideMethods(baseKlass);
        }
        DFKlass[] baseIfaces = this.getBaseIfaces();
        if (baseIfaces != null) {
            for (DFKlass iface : baseIfaces) {
                this.overrideMethods(iface);
            }
        }
    }

    private void overrideMethods(DFKlass klass) {
        for (DFMethod overrider : this.getMethods()) {
            for (DFMethod method : klass.getMethods()) {
                // each method of the given klass is overridded by
                // the corresponding method of the this klass.
                if (method.addOverrider(overrider)) break;
            }
        }
    }

    public void initializeFinder(DFTypeFinder parentFinder) {
        assert _finder == null;
        _finder = new DFTypeFinder(this, parentFinder);
        for (DFKlass klass : this.getInnerKlasses()) {
            if (klass instanceof DFSourceKlass) {
                ((DFSourceKlass)klass).initializeFinder(_finder);
            }
        }
    }

    protected DFTypeFinder getFinder() {
        assert _finder != null;
        return _finder;
    }

    protected void load() {
        assert _finder != null;
        if (!_loaded) {
            _loaded = true;
            //Logger.info("build:", this);
            this.build();
        }
    }

    protected abstract void build();

    @SuppressWarnings("unchecked")
    protected void buildTypeFromDecls(List<BodyDeclaration> decls)
        throws InvalidSyntax, EntityDuplicate {

        for (BodyDeclaration body : decls) {
            if (body instanceof AbstractTypeDeclaration) {
                AbstractTypeDeclaration abstTypeDecl = (AbstractTypeDeclaration)body;
                String id = abstTypeDecl.getName().getIdentifier();
                DFSourceKlass klass = new DFTypeDeclKlass(
                    abstTypeDecl, this, this, this.getKlassScope(),
                    _filePath, _analyze);
                try {
                    this.addKlass(id, klass);
                } catch (TypeDuplicate e) {
                    e.setAst(abstTypeDecl);
                    throw e;
                }

            } else if (body instanceof FieldDeclaration) {

            } else if (body instanceof MethodDeclaration) {

            } else if (body instanceof EnumConstantDeclaration) {

            } else if (body instanceof AnnotationTypeMemberDeclaration) {

            } else if (body instanceof Initializer) {

            } else {
                throw new InvalidSyntax(body);
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected void buildMembersFromAnonDecl(
        ClassInstanceCreation cstr)
        throws InvalidSyntax, EntityDuplicate {
        // Get superclass.
        assert _finder != null;
        _baseKlass = DFBuiltinTypes.getObjectKlass();
        Type superClass = cstr.getType();
        if (superClass != null) {
            try {
                _baseKlass = _finder.resolve(superClass).toKlass();
            } catch (TypeNotFound e) {
                Logger.error(
                    "DFKlass.buildMembersFromAnonDecl: TypeNotFound (baseKlass)",
                    Utils.getASTSource(superClass), this);
            }
        }
        this.buildMembers(cstr.getAnonymousClassDeclaration().bodyDeclarations());
    }

    @SuppressWarnings("unchecked")
    protected void buildMembersFromTypeDecl(
        TypeDeclaration typeDecl)
        throws InvalidSyntax, EntityDuplicate {
        _interface = typeDecl.isInterface();
        // Get superclass.
        _baseKlass = DFBuiltinTypes.getObjectKlass();
        Type superClass = typeDecl.getSuperclassType();
        if (superClass != null) {
            try {
                _baseKlass = _finder.resolve(superClass).toKlass();
            } catch (TypeNotFound e) {
                Logger.error(
                    "DFKlass.buildMembersFromTypeDecl: TypeNotFound (baseKlass)",
                    Utils.getASTSource(superClass), this);
            }
        }
        // Get interfaces.
        List<Type> ifaces = typeDecl.superInterfaceTypes();
        _baseIfaces = new DFKlass[ifaces.size()];
        for (int i = 0; i < ifaces.size(); i++) {
            DFKlass iface = DFBuiltinTypes.getObjectKlass();
            try {
                iface = _finder.resolve(ifaces.get(i)).toKlass();
            } catch (TypeNotFound e) {
                Logger.error(
                    "DFKlass.buildMembersFromTypeDecl: TypeNotFound (iface)",
                    Utils.getASTSource(ifaces.get(i)), this);
            }
            _baseIfaces[i] = iface;
        }
        this.buildMembers(typeDecl.bodyDeclarations());
    }

    @SuppressWarnings("unchecked")
    protected void buildMembersFromEnumDecl(
        EnumDeclaration enumDecl)
        throws InvalidSyntax, EntityDuplicate {
        // Get superclass.
        DFKlass enumKlass = DFBuiltinTypes.getEnumKlass();
        _baseKlass = enumKlass.getReifiedKlass(new DFKlass[] { this });
        // Get interfaces.
        List<Type> ifaces = enumDecl.superInterfaceTypes();
        _baseIfaces = new DFKlass[ifaces.size()];
        for (int i = 0; i < ifaces.size(); i++) {
            DFKlass iface = DFBuiltinTypes.getObjectKlass();
            try {
                iface = _finder.resolve(ifaces.get(i)).toKlass();
            } catch (TypeNotFound e) {
                Logger.error(
                    "DFKlass.buildMembersFromEnumDecl: TypeNotFound (iface)",
                    Utils.getASTSource(ifaces.get(i)), this);
            }
            _baseIfaces[i] = iface;
        }
        // Get constants.
        for (EnumConstantDeclaration econst :
                 (List<EnumConstantDeclaration>) enumDecl.enumConstants()) {
            this.addField(this, econst.getName(), true);
        }
        // Enum has a special method "values()".
        this.addMethod(new EnumValuesMethod(this));
        this.buildMembers(enumDecl.bodyDeclarations());
    }

    @SuppressWarnings("unchecked")
    protected void buildMembersFromAnnotTypeDecl(
        AnnotationTypeDeclaration annotTypeDecl)
        throws InvalidSyntax, EntityDuplicate {
        this.buildMembers(annotTypeDecl.bodyDeclarations());
    }

    @SuppressWarnings("unchecked")
    private void buildMembers(List<BodyDeclaration> decls)
        throws InvalidSyntax, EntityDuplicate {

        assert _initMethod == null;
        _initMethod = new InitMethod(this, decls, _finder);

        for (BodyDeclaration body : decls) {
            if (body instanceof AbstractTypeDeclaration) {
                // Child klasses are loaded independently.

            } else if (body instanceof FieldDeclaration) {
                FieldDeclaration decl = (FieldDeclaration)body;
                DFType fldType = _finder.resolveSafe(decl.getType());
                for (VariableDeclarationFragment frag :
                         (List<VariableDeclarationFragment>) decl.fragments()) {
                    DFType ft = fldType;
                    int ndims = frag.getExtraDimensions();
                    if (ndims != 0) {
                        ft = DFArrayType.getArray(ft, ndims);
                    }
                    this.addField(ft, frag.getName(), isStatic(decl));
                }

            } else if (body instanceof MethodDeclaration) {
                MethodDeclaration decl = (MethodDeclaration)body;
                String id = Utils.encodeASTNode(decl);
                String name;
                DFMethod.CallStyle callStyle;
                if (decl.isConstructor()) {
                    name = "<init>";
                    callStyle = DFMethod.CallStyle.Constructor;
                } else {
                    name = decl.getName().getIdentifier();
                    callStyle = (isStatic(decl))?
                        DFMethod.CallStyle.StaticMethod :
                        DFMethod.CallStyle.InstanceMethod;
                }
                Statement stmt = decl.getBody();
                DFTypeSpace space = this.getGenericKlass();
                if (space == null) {
                    space = this;
                }
                DFMethod method = new DefinedMethod(
                    this, callStyle, (stmt == null), id, name, decl, _finder, space);
                _id2method.put(id, method);
                this.addMethod(method);

            } else if (body instanceof EnumConstantDeclaration) {

            } else if (body instanceof AnnotationTypeMemberDeclaration) {
                AnnotationTypeMemberDeclaration decl =
                    (AnnotationTypeMemberDeclaration)body;
                DFType type = _finder.resolveSafe(decl.getType());
                this.addField(type, decl.getName(), isStatic(decl));

            } else if (body instanceof Initializer) {

            } else {
                throw new InvalidSyntax(body);
            }
        }
    }

    // listUsedKlasses: enumerate all the klasses used within this klass.
    public boolean listUsedKlasses(Collection<DFSourceKlass> klasses) {
        if (klasses.contains(this)) return false;
        if (this.getGenericKlass() != null &&
            this.isRecursive(this.getGenericKlass())) return false;
        if (!this.isResolved()) return false;
        klasses.add(this);
        //Logger.info("listUsedKlasses:", this);
        return true;
    }

    // listDefinedKlasses: enumerate newly defined klasses (Lambdas).
    public void listDefinedKlasses(Collection<DFSourceKlass> defined)
        throws InvalidSyntax {
        this.load();
        assert this.isResolved();
        if (_initMethod != null) {
            _initMethod.listDefinedKlasses(defined);
        }
        for (DFMethod method : this.getMethods()) {
            if (method instanceof DFSourceMethod) {
                ((DFSourceMethod)method).listDefinedKlasses(defined);
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected void listUsedDecls(
        Collection<DFSourceKlass> klasses, List<BodyDeclaration> decls)
        throws InvalidSyntax {
        this.load();
        if (_initMethod != null) {
            _initMethod.listUsedKlasses(klasses);
        }
        for (BodyDeclaration body : decls) {
            if (body instanceof AbstractTypeDeclaration) {
                AbstractTypeDeclaration decl = (AbstractTypeDeclaration)body;
                DFKlass innerType = this.getKlass(decl.getName());
                assert innerType instanceof DFSourceKlass;
                ((DFSourceKlass)innerType).listUsedKlasses(klasses);

            } else if (body instanceof FieldDeclaration) {
                FieldDeclaration decl = (FieldDeclaration)body;
                DFType fldType = _finder.resolveSafe(decl.getType());
                if (fldType instanceof DFSourceKlass) {
                    ((DFSourceKlass)fldType).listUsedKlasses(klasses);
                }

            } else if (body instanceof MethodDeclaration) {
                MethodDeclaration decl = (MethodDeclaration)body;
                String id = Utils.encodeASTNode(decl);
                DFMethod method = _id2method.get(id);
                assert method instanceof DFSourceMethod;
                ((DFSourceMethod)method).listUsedKlasses(klasses);

            } else if (body instanceof EnumConstantDeclaration) {

            } else if (body instanceof AnnotationTypeMemberDeclaration) {
                AnnotationTypeMemberDeclaration decl =
                    (AnnotationTypeMemberDeclaration)body;
                DFType type = _finder.resolveSafe(decl.getType());
                if (type instanceof DFSourceKlass) {
                    ((DFSourceKlass)type).listUsedKlasses(klasses);
                }

            } else if (body instanceof Initializer) {

            } else {
                throw new InvalidSyntax(body);
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

    private void addMethod(DFMethod method) {
        _methods.add(method);
    }
}


//  Init (static) method
//
class InitMethod extends DFSourceMethod {

    private ASTNode _ast;
    private List<BodyDeclaration> _decls;

    public InitMethod(
        DFSourceKlass klass,
        List<BodyDeclaration> decls, DFTypeFinder finder) {
        super(klass, CallStyle.Initializer,
              false, "<clinit>", "<clinit>",
              klass.getKlassScope(), finder);

        _ast = klass.getAST();
        _decls = decls;
        this.build();
    }

    public ASTNode getAST() {
        return _ast;
    }

    public DFFuncType getFuncType() {
        return new DFFuncType(new DFType[] {}, DFBasicType.VOID);
    }

    @Override
    protected DFMethod parameterize(Map<String, DFKlass> paramTypes) {
        assert false;
        return null;
    }

    @SuppressWarnings("unchecked")
    private void build() {
        DFTypeFinder finder = this.getFinder();
        DFLocalScope scope = this.getScope();
        try {
            for (BodyDeclaration body : _decls) {
                if (body instanceof FieldDeclaration) {
                    FieldDeclaration decl = (FieldDeclaration)body;
                    for (VariableDeclarationFragment frag :
                             (List<VariableDeclarationFragment>) decl.fragments()) {
                        Expression init = frag.getInitializer();
                        if (init != null) {
                            this.buildTypeFromExpr(init, scope);
                            scope.buildExpr(finder, init);
                        }
                    }
                } else if (body instanceof Initializer) {
                    Initializer initializer = (Initializer)body;
                    Statement stmt = initializer.getBody();
                    if (stmt != null) {
                        this.buildTypeFromStmt(stmt, scope);
                        scope.buildStmt(finder, stmt);
                    }
                }
            }
        } catch (InvalidSyntax e) {
            Logger.error(
                "InitMethod.build: InvalidSyntax: ",
                Utils.getASTSource(e.ast), this);
        } catch (EntityDuplicate e) {
            Logger.error(
                "InitMethod.build: EntityDuplicate: ",
                e.name, this);
        }
    }

    // listUsedKlasses: enumerate all referenced Klasses.
    @SuppressWarnings("unchecked")
    public void listUsedKlasses(Collection<DFSourceKlass> klasses) {
        try {
            for (BodyDeclaration body : _decls) {
                if (body instanceof FieldDeclaration) {
                    FieldDeclaration decl = (FieldDeclaration)body;
                    for (VariableDeclarationFragment frag :
                             (List<VariableDeclarationFragment>) decl.fragments()) {
                        Expression expr = frag.getInitializer();
                        if (expr != null) {
                            this.listUsedExpr(klasses, expr);
                        }
                    }
                } else if (body instanceof Initializer) {
                    Initializer initializer = (Initializer)body;
                    Statement stmt = initializer.getBody();
                    if (stmt != null) {
                        this.listUsedStmt(klasses, stmt);
                    }
                }
            }
        } catch (InvalidSyntax e) {
            Logger.error(
                "InitMethod.listUsedKlasses: ",
                Utils.getASTSource(e.ast), this);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void listDefinedKlasses(Collection<DFSourceKlass> defined) {
        DFLocalScope scope = this.getScope();
        try {
            for (BodyDeclaration body : _decls) {
                if (body instanceof FieldDeclaration) {
                    FieldDeclaration fieldDecl = (FieldDeclaration)body;
                    for (VariableDeclarationFragment frag :
                             (List<VariableDeclarationFragment>) fieldDecl.fragments()) {
                        try {
                            DFRef ref = scope.lookupVar(frag.getName());
                            Expression init = frag.getInitializer();
                            if (init != null) {
                                DFType type = this.listDefinedExpr(
                                    defined, scope, init, ref.getRefType());
                                ref.setRefType(type);
                            }
                        } catch (VariableNotFound e) {
                        }
                    }
                } else if (body instanceof Initializer) {
                    Initializer initializer = (Initializer)body;
                    this.listDefinedStmt(defined, scope, initializer.getBody());
                }
            }
        } catch (InvalidSyntax e) {
            Logger.error(
                "InitMethod.listDefinedKlasses: ",
                Utils.getASTSource(e.ast), this);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public DFGraph getDFGraph(int graphId)
        throws EntityNotFound {
        MethodGraph graph = new MethodGraph("K"+graphId+"_"+this.getName());
        DFLocalScope scope = this.getScope();
        DFContext ctx = new DFContext(graph, scope);

        try {
            graph.processDecls(ctx, _decls);
        } catch (InvalidSyntax e) {
            Logger.error(
                "InitMethod.getDFGraph: ",
                Utils.getASTSource(e.ast), this);
        } catch (MethodNotFound e) {
            e.setMethod(this);
            Logger.error(
                "InitMethod.getDFGraph: MethodNotFound",
                e.name+"("+Utils.join(e.argTypes)+")", this);
            throw e;
        } catch (EntityNotFound e) {
            e.setMethod(this);
            Logger.error(
                "InitMethod.getDFGraph: EntityNotFound",
                e.name, this);
            throw e;
        }

        return graph;
    }
}


//  DefinedMethod
//
class DefinedMethod extends DFSourceMethod {

    private MethodDeclaration _methodDecl;

    private DFFuncType _funcType;

    @SuppressWarnings("unchecked")
    public DefinedMethod(
        DFSourceKlass srcklass, CallStyle callStyle,
        boolean isAbstract, String methodId, String methodName,
        MethodDeclaration methodDecl, DFTypeFinder finder,
        DFTypeSpace outerSpace)
        throws EntityDuplicate {
        super(srcklass, callStyle,
              isAbstract, methodId, methodName,
              srcklass.getKlassScope(), finder);

        _methodDecl = methodDecl;
        outerSpace = outerSpace.addSubSpace(methodId);
        finder = this.getFinder();
        List<TypeParameter> tps = _methodDecl.typeParameters();
        if (!tps.isEmpty()) {
            DFMapKlass[] mapKlasses = new DFMapKlass[tps.size()];
            for (int i = 0; i < tps.size(); i++) {
                TypeParameter tp = tps.get(i);
                String id = tp.getName().getIdentifier();
                DFKlass klass = outerSpace.getKlass(id);
                DFMapKlass mapKlass;
                if (klass != null) {
                    assert klass instanceof DFMapKlass;
                    mapKlass = (DFMapKlass)klass;
                } else {
                    mapKlass = new DFMapKlass(
                        id, outerSpace, this.klass(),
                        tp.typeBounds());
                    mapKlass.setFinder(finder);
                    outerSpace.addKlass(id, mapKlass);
                }
                mapKlasses[i] = mapKlass;
            }
            this.setMapKlasses(mapKlasses);
        }
        this.build();
    }

    protected DefinedMethod(
        DefinedMethod genericMethod, Map<String, DFKlass> paramTypes) {
        super(genericMethod, paramTypes);

        _methodDecl = genericMethod._methodDecl;
        this.build();
    }

    public ASTNode getAST() {
        return _methodDecl;
    }

    public DFFuncType getFuncType() {
        return _funcType;
    }

    @Override
    protected DFMethod parameterize(Map<String, DFKlass> paramTypes) {
        assert paramTypes != null;
        return new DefinedMethod(this, paramTypes);
    }

    @SuppressWarnings("unchecked")
    private void build() {
        DFTypeFinder finder = this.getFinder();
        MethodScope methodScope = this.getScope();

        List<SingleVariableDeclaration> varDecls = _methodDecl.parameters();
        DFType[] argTypes = new DFType[varDecls.size()];
        for (int i = 0; i < varDecls.size(); i++) {
            SingleVariableDeclaration varDecl = varDecls.get(i);
            DFType argType = finder.resolveSafe(varDecl.getType());
            if (varDecl.isVarargs()) {
                argType = DFArrayType.getArray(argType, 1);
            }
            argTypes[i] = argType;
        }
        DFType returnType;
        if (_methodDecl.isConstructor()) {
            returnType = this.klass();
        } else {
            returnType = finder.resolveSafe(_methodDecl.getReturnType2());
        }

        _funcType = new DFFuncType(argTypes, returnType);
        List<Type> excs = _methodDecl.thrownExceptionTypes();
        if (0 < excs.size()) {
            DFKlass[] exceptions = new DFKlass[excs.size()];
            for (int i = 0; i < excs.size(); i++) {
                exceptions[i] = finder.resolveSafe(excs.get(i)).toKlass();
            }
            _funcType.setExceptions(exceptions);
        }
        _funcType.setVarArgs(_methodDecl.isVarargs());

        Statement stmt = _methodDecl.getBody();
        if (stmt != null) {
            try {
                this.buildTypeFromStmt(stmt, methodScope);
                methodScope.buildInternalRefs(_methodDecl.parameters());
                methodScope.buildStmt(finder, stmt);
            } catch (InvalidSyntax e) {
                Logger.error(
                    "DefinedMethod.build: InvalidSyntax: ",
                    Utils.getASTSource(e.ast), this);
            } catch (EntityDuplicate e) {
                Logger.error(
                    "DefinedMethod.build: EntityDuplicate: ",
                    e.name, this);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void listUsedKlasses(Collection<DFSourceKlass> klasses) {
        DFTypeFinder finder = this.getFinder();
        List<SingleVariableDeclaration> varDecls = _methodDecl.parameters();
        for (SingleVariableDeclaration varDecl : varDecls) {
            DFType argType = finder.resolveSafe(varDecl.getType());
            if (argType instanceof DFSourceKlass) {
                ((DFSourceKlass)argType).listUsedKlasses(klasses);
            }
        }
        if (!_methodDecl.isConstructor()) {
            DFType returnType = finder.resolveSafe(_methodDecl.getReturnType2());
            if (returnType instanceof DFSourceKlass) {
                ((DFSourceKlass)returnType).listUsedKlasses(klasses);
            }
        }
        if (_methodDecl.getBody() != null) {
            try {
                this.listUsedStmt(klasses, _methodDecl.getBody());
            } catch (InvalidSyntax e) {
                Logger.error(
                    "DefinedMethod.listUsedKlasses:",
                    Utils.getASTSource(e.ast), this);
            }
        }
    }

    @Override
    public void listDefinedKlasses(Collection<DFSourceKlass> defined) {
        if (_methodDecl.getBody() == null) return;
        // Constructor changes all the member fields.
        if (this.getCallStyle() == CallStyle.Constructor) {
            for (DFKlass.FieldRef ref : this.klass().getFields()) {
                if (!ref.isStatic()) {
                    this.getOutputRefs().add(ref);
                }
            }
        }
        DFLocalScope scope = this.getScope();
        try {
            this.listDefinedStmt(defined, scope, _methodDecl.getBody());
        } catch (InvalidSyntax e) {
            Logger.error(
                "DefinedMethod.listDefinedKlasses:",
                Utils.getASTSource(e.ast), this);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public DFGraph getDFGraph(int graphId)
        throws EntityNotFound {
        ASTNode body = _methodDecl.getBody();
        if (body == null) return null;

        MethodGraph graph = new MethodGraph("M"+graphId+"_"+this.getName());
        MethodScope methodScope = this.getScope();
        DFContext ctx = new DFContext(graph, methodScope);
        DFKlass klass = this.klass();
        int i = 0;
        for (VariableDeclaration decl :
                 (List<VariableDeclaration>)_methodDecl.parameters()) {
            DFRef ref_v = methodScope.lookupArgument(i);
            DFRef ref_a = methodScope.lookupVar(decl.getName());
            DFNode assign = graph.createArgNode(ref_v, ref_a, decl);
            ctx.set(assign);
            i++;
        }

        try {
            graph.processMethodBody(ctx, body);
        } catch (InvalidSyntax e) {
            Logger.error(
                "DefinedMethod.getDFGraph:",
                Utils.getASTSource(e.ast), this);
        } catch (MethodNotFound e) {
            e.setMethod(this);
            Logger.error(
                "DefinedMethod.getDFGraph: MethodNotFound",
                e.name+"("+Utils.join(e.argTypes)+")", this);
            throw e;
        } catch (EntityNotFound e) {
            e.setMethod(this);
            Logger.error(
                "DefinedMethod.getDFGraph: EntityNotFound",
                e.name, this);
            throw e;
        }

        return graph;
    }

    public void writeXML(XMLStreamWriter writer)
        throws XMLStreamException {
        Utils.writeXML(writer, _methodDecl);
    }
}


//  Enum.values() method.
//
class EnumValuesMethod extends DFMethod {

    private DFFuncType _funcType;

    public EnumValuesMethod(DFSourceKlass klass) {
        super(klass, CallStyle.InstanceMethod,
              false, "values", "values");
        _funcType = new DFFuncType(
            new DFType[] {}, DFArrayType.getArray(klass, 1));
    }

    @Override
    protected DFMethod parameterize(Map<String, DFKlass> paramTypes) {
        assert false;
        return null;
    }

    public DFFuncType getFuncType() {
        return _funcType;
    }
}
