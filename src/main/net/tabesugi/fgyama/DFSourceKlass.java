//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import java.util.jar.*;
import javax.xml.stream.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


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

    public DFFuncType getFuncType() {
        return new DFFuncType(new DFType[] {}, DFBasicType.VOID);
    }

    protected DFMethod parameterize(Map<String, DFType> paramTypes) {
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
            Logger.error("DFSourceKlass.build: ", e);
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
            Logger.error("DFSourceKlass.listUsedKlasses: ", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void listDefinedKlasses(Collection<DFSourceKlass> defined) {
        if (this.isGeneric()) return;
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
                                this.listDefinedExpr(defined, scope, init);
                                this.setLambdaType(defined, ref.getRefType(), init);
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
            Logger.error("DFSourceKlass.listDefinedKlasses: ", e);
        }
    }

    @Override
    public void writeGraph(Exporter exporter)
        throws EntityNotFound {
        DFLocalScope scope = this.getScope();
        MethodGraph graph = new MethodGraph("K"+exporter.getNewId()+"_"+this.getName());
        DFContext ctx = new DFContext(graph, scope);

        try {
            this.processBodyDecls(graph, ctx, _decls);
        } catch (InvalidSyntax e) {
            Logger.error("DFSourceKlass.writeGraph: ", e);
        }
        exporter.writeGraph(graph);
    }

    public ASTNode getAST() {
        return _ast;
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
        MethodDeclaration methodDecl, DFTypeFinder finder) {
        super(srcklass, callStyle,
              isAbstract, methodId, methodName,
              srcklass.getKlassScope(), finder);

        _methodDecl = methodDecl;
        this.build();
    }

    protected DefinedMethod(
        DefinedMethod genericMethod, Map<String, DFType> paramTypes) {
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
    protected DFMethod parameterize(Map<String, DFType> paramTypes) {
        assert paramTypes != null;
        return new DefinedMethod(this, paramTypes);
    }

    @SuppressWarnings("unchecked")
    private void build() {
        DFTypeFinder finder = this.getFinder();
        MethodScope methodScope = (MethodScope)this.getScope();

        if (this.getGenericMethod() == null) {
            DFMapType[] mapTypes = this.createMapTypes(_methodDecl.typeParameters());
            if (mapTypes != null) {
                this.setMapTypes(mapTypes, finder);
            }
        }

        List<SingleVariableDeclaration> varDecls = _methodDecl.parameters();
        DFType[] argTypes = new DFType[varDecls.size()];
        for (int i = 0; i < varDecls.size(); i++) {
            SingleVariableDeclaration varDecl = varDecls.get(i);
            DFType argType = finder.resolveSafe(varDecl.getType());
            if (varDecl.isVarargs()) {
                argType = DFArrayType.getType(argType, 1);
            }
            argTypes[i] = argType;
        }
        DFType returnType;
        if (_methodDecl.isConstructor()) {
            returnType = this.getKlass();
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
                Logger.error("DFSourceKlass.build:", e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void listUsedKlasses(Collection<DFSourceKlass> klasses) {
        if (this.isGeneric()) return;
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
                Logger.error("DFSourceKlass.listUsedKlasses:", e);
            }
        }
    }

    @Override
    public void listDefinedKlasses(Collection<DFSourceKlass> defined) {
        if (this.isGeneric()) return;
        if (_methodDecl.getBody() == null) return;
        // Constructor changes all the member fields.
        if (this.getCallStyle() == CallStyle.Constructor) {
            if (this.isTransparent()) {
                for (DFKlass.FieldRef ref : this.getKlass().getFields()) {
                    if (!ref.isStatic()) {
                        this.getOutputRefs().add(ref);
                    }
                }
            }
        }
        DFLocalScope scope = this.getScope();
        try {
            this.listDefinedStmt(defined, scope, _methodDecl.getBody());
        } catch (InvalidSyntax e) {
            Logger.error("DFSourceKlass.listDefinedKlasses:", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void writeGraph(Exporter exporter)
        throws EntityNotFound {
        ASTNode body = _methodDecl.getBody();
        if (body == null) return;

        MethodScope scope = (MethodScope)this.getScope();
        MethodGraph graph = new MethodGraph("M"+exporter.getNewId()+"_"+this.getName());
        DFContext ctx = new DFContext(graph, scope);
        int i = 0;
        for (VariableDeclaration decl :
                 (List<VariableDeclaration>)_methodDecl.parameters()) {
            DFRef ref = scope.lookupArgument(i);
            DFNode input = new InputNode(graph, scope, ref, decl);
            ctx.set(input);
            DFNode assign = new AssignNode(
                graph, scope, scope.lookupVar(decl.getName()), decl);
            assign.accept(input);
            ctx.set(assign);
            i++;
        }
        try {
            this.processMethodBody(graph, ctx, body);
        } catch (InvalidSyntax e) {
            Logger.error("DFSourceKlass.writeGraph:", e);
        }
        exporter.writeGraph(graph);
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
            new DFType[] {}, DFArrayType.getType(klass, 1));
    }

    protected DFMethod parameterize(Map<String, DFType> paramTypes) {
        assert false;
        return null;
    }

    public DFFuncType getFuncType() {
        return _funcType;
    }
}


//  DFSourceKlass
//  DFKlass defined in source code.
//
//  Usage:
//    1. new DFSourceKlass()
//    2. setBaseFinder(finder)
//    3. getXXX(), ...
//
//  Implement:
//    parameterize(paramTypes)
//    build()
//
public abstract class DFSourceKlass extends DFKlass {

    // These fields are set at the constructor.
    private DFKlass _outerKlass;  // can be the same as outerSpace, or null.
    private String _filePath;
    private DFVarScope _outerScope;
    private KlassScope _klassScope;

    // This field is available after setBaseFinder(). (Stage2)
    private DFTypeFinder _finder = null;

    // The following fields are available after the klass is loaded. (Stage3)
    private boolean _interface = false;
    private DFKlass _baseKlass = null;
    private DFKlass[] _baseIfaces = null;
    private InitMethod _initMethod = null;

    // Normal constructor.
    protected DFSourceKlass(
        String name, DFTypeSpace outerSpace, DFSourceKlass outerKlass,
        String filePath, DFVarScope outerScope) {
        super(name, outerSpace);

        _outerKlass = outerKlass;
        _filePath = filePath;
        _outerScope = outerScope;
        _klassScope = new KlassScope(outerScope, name);
    }

    // Constructor for a parameterized klass.
    protected DFSourceKlass(
        DFSourceKlass genericKlass, Map<String, DFType> paramTypes) {
        super(genericKlass, paramTypes);

        _outerKlass = genericKlass._outerKlass;
        _filePath = genericKlass._filePath;
        _outerScope = genericKlass._outerScope;
        _klassScope = new KlassScope(genericKlass._outerScope, this.getName());

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

    public String getFilePath() {
        return _filePath;
    }

    public DFVarScope getOuterScope() {
        return _outerScope;
    }

    public DFVarScope getKlassScope() {
        return _klassScope;
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
    public DFMethod findMethod(
        DFMethod.CallStyle callStyle, String id, DFType[] argTypes) {
        this.load();
        DFMethod method = super.findMethod(callStyle, id, argTypes);
        if (method != null) return method;
        if (_outerKlass != null) {
            method = _outerKlass.findMethod(callStyle, id, argTypes);
            if (method != null) return method;
        }
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

    public DFRef getField(String id) {
        this.load();
        DFRef ref = super.getField(id);
        if (ref != null) return ref;
        if (_baseKlass != null) {
            ref = _baseKlass.getField(id);
            if (ref != null) return ref;
        }
        return null;
    }

    public DFMethod getInitMethod() {
        this.load();
        return _initMethod;
    }

    public void overrideMethods() {
        // override the methods.
        for (DFMethod method : this.getMethods()) {
            if (_baseKlass != null) {
                this.overrideMethod(_baseKlass, method);
            }
            if (_baseIfaces != null) {
                for (DFKlass iface : _baseIfaces) {
                    this.overrideMethod(iface, method);
                }
            }
        }
    }

    private void overrideMethod(DFKlass klass, DFMethod method1) {
        for (DFMethod method0 : klass.getMethods()) {
            if (method0.addOverrider(method1)) break;
        }
    }

    public void setBaseFinder(DFTypeFinder baseFinder) {
        assert _finder == null;
        _finder = new DFTypeFinder(this, baseFinder);
        for (DFKlass klass : this.getInnerKlasses()) {
            if (klass instanceof DFSourceKlass) {
                ((DFSourceKlass)klass).setBaseFinder(_finder);
            }
        }
        this.setMapTypeFinder(_finder);
    }

    protected DFTypeFinder getFinder() {
        assert _finder != null;
        return _finder;
    }

    // Only used by DFLambdaKlass.
    protected void setBaseKlass(DFKlass klass) {
        assert klass != null;
        _baseKlass = klass;
    }

    @SuppressWarnings("unchecked")
    protected void buildTypeFromDecls(List<BodyDeclaration> decls)
        throws InvalidSyntax {

        for (BodyDeclaration body : decls) {
            if (body instanceof AbstractTypeDeclaration) {
                AbstractTypeDeclaration abstTypeDecl = (AbstractTypeDeclaration)body;
                String id = abstTypeDecl.getName().getIdentifier();
                DFSourceKlass klass = new AbstTypeDeclKlass(
                    abstTypeDecl, this, this,
                    this.getFilePath(), this.getKlassScope());
                this.addKlass(id, klass);

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
        throws InvalidSyntax {
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
                    this, e.name);
            }
        }
        this.buildMembers(cstr.getAnonymousClassDeclaration().bodyDeclarations());
    }

    @SuppressWarnings("unchecked")
    protected void buildMembersFromTypeDecl(
        TypeDeclaration typeDecl)
        throws InvalidSyntax {
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
                    this, e.name);
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
                    this, e.name);
            }
            _baseIfaces[i] = iface;
        }
        this.buildMembers(typeDecl.bodyDeclarations());
    }

    @SuppressWarnings("unchecked")
    protected void buildMembersFromEnumDecl(
        EnumDeclaration enumDecl)
        throws InvalidSyntax {
        // Get superclass.
        DFKlass enumKlass = DFBuiltinTypes.getEnumKlass();
        _baseKlass = enumKlass.getConcreteKlass(new DFKlass[] { this });
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
                    this, e.name);
            }
            _baseIfaces[i] = iface;
        }
        // Get constants.
        for (EnumConstantDeclaration econst :
                 (List<EnumConstantDeclaration>) enumDecl.enumConstants()) {
            this.addField(this, econst.getName(), true);
        }
        // Enum has a special method "values()".
        DFMethod method = new EnumValuesMethod(this);
        this.addMethod(method, null);
        this.buildMembers(enumDecl.bodyDeclarations());
    }

    @SuppressWarnings("unchecked")
    protected void buildMembersFromAnnotTypeDecl(
        AnnotationTypeDeclaration annotTypeDecl)
        throws InvalidSyntax {
        this.buildMembers(annotTypeDecl.bodyDeclarations());
    }

    @SuppressWarnings("unchecked")
    private void buildMembers(List<BodyDeclaration> decls)
        throws InvalidSyntax {

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
                        ft = DFArrayType.getType(ft, ndims);
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
                DFMethod method = new DefinedMethod(
                    this, callStyle, (stmt == null), id, name, decl, _finder);
                this.addMethod(method, id);

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

    public void listUsedKlasses(Collection<DFSourceKlass> klasses) {
        assert !this.isGeneric();
        if (klasses.contains(this)) return;
        klasses.add(this);
        //Logger.info("listUsedKlasses:", this);
    }

    public void listDefinedKlasses(Collection<DFSourceKlass> defined)
        throws InvalidSyntax {
        assert !this.isGeneric();
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
        assert !this.isGeneric();
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
                DFMethod method = this.getMethod(id);
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

    // ThisRef
    private class ThisRef extends DFRef {
        public ThisRef(DFType type) {
            super(type);
        }

        @Override
        public boolean isLocal() {
            return false;
        }

        @Override
        public String getFullName() {
            return "#this";
        }
    }

    // KlassScope
    private class KlassScope extends DFVarScope {

        private DFRef _this;

        public KlassScope(DFVarScope outer, String id) {
            super(outer, id);
            _this = new ThisRef(DFSourceKlass.this);
        }

        @Override
        public String getScopeName() {
            return DFSourceKlass.this.getTypeName();
        }

        @Override
        public DFRef lookupThis() {
            return _this;
        }

        @Override
        public DFRef lookupVar(String id)
            throws VariableNotFound {
            DFRef ref = DFSourceKlass.this.getField(id);
            if (ref != null) return ref;
            return super.lookupVar(id);
        }

        // dumpContents (for debugging)
        protected void dumpContents(PrintStream out, String indent) {
            super.dumpContents(out, indent);
            for (DFRef ref : DFSourceKlass.this.getFields()) {
                out.println(indent+"defined: "+ref);
            }
            for (DFMethod method : DFSourceKlass.this.getMethods()) {
                out.println(indent+"defined: "+method);
            }
        }
    }
}
