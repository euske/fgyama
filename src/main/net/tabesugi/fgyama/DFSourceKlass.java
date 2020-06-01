//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import java.util.jar.*;
import javax.xml.stream.*;
import org.apache.bcel.*;
import org.apache.bcel.classfile.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFSourceKlass
//
public class DFSourceKlass extends DFKlass {

    private enum LoadState {
	Unloaded,
	Loading,
	Loaded,
    };

    // These fields are available upon construction.
    private DFSourceKlass _outerKlass; // can be the same as outerSpace, or null.

    // These fields are set immediately after construction.
    private String _filePath = null;
    private ASTNode _ast = null;
    private String _jarPath = null;
    private String _entPath = null;

    // This field is available after setFinder(). (Stage2)
    private DFTypeFinder _finder = null;

    // The following fields are available after the klass is loaded. (Stage3)
    private LoadState _state = LoadState.Unloaded;
    private DFMethod _initMethod = null;

    public DFSourceKlass(
        String name, DFTypeSpace outerSpace, DFVarScope outerScope,
        DFSourceKlass outerKlass) {
	super(name, outerSpace, outerScope);
	_outerKlass = outerKlass;
    }

    // Set the klass AST from a source code.
    public void setKlassTree(String filePath, ASTNode ast)
	throws InvalidSyntax {
        _filePath = filePath;
        _ast = ast;
    }

    // Set the klass code from a JAR.
    public void setJarPath(String jarPath, String entPath) {
        _jarPath = jarPath;
        _entPath = entPath;
    }

    // Set the map types from a source code.
    @SuppressWarnings("unchecked")
    public void setMapTypes(List<TypeParameter> tps) {
	DFMapType[] mapTypes = this.getMapTypes(tps);
	if (mapTypes == null) return;
        super.setMapTypes(mapTypes);
    }

    // Set the map types from a JAR.
    public void setMapTypes(String sig) {
        DFMapType[] mapTypes = JNITypeParser.getMapTypes(sig, this);
	if (mapTypes == null) return;
        super.setMapTypes(mapTypes);
    }

    // Constructor for a parameterized klass.
    @Override
    @SuppressWarnings("unchecked")
    protected DFKlass parameterize(DFKlass[] paramTypes)
	throws InvalidSyntax {
        assert paramTypes != null;
        DFSourceKlass genericKlass = this;
        DFSourceKlass klass = new DFSourceKlass(
            genericKlass.getName() + DFKlass.getParamName(paramTypes),
            genericKlass.getOuterSpace(), genericKlass.getOuterScope(),
            genericKlass._outerKlass);
        // A parameterized Klass is NOT accessible from
        // the outer namespace but it creates its own subspace.
        klass._baseKlass = genericKlass._baseKlass;
        klass._genericKlass = genericKlass;
        klass._paramTypes = new ConsistentHashMap<String, DFKlass>();
	List<DFMapType> mapTypes = genericKlass.getMapTypes();
        for (int i = 0; i < paramTypes.length; i++) {
            DFMapType mapType = mapTypes.get(i);
            assert mapType != null;
            DFKlass paramType = paramTypes[i];
            assert paramType != null;
            assert !(paramType instanceof DFMapType);
            klass._paramTypes.put(mapType.getName(), paramType);
        }

        klass._ast = genericKlass._ast;
        klass._filePath = genericKlass._filePath;
        klass._jarPath = genericKlass._jarPath;
        klass._entPath = genericKlass._entPath;
	klass._finder = genericKlass._finder;

	if (klass._jarPath != null) {
            // XXX In case of a .jar class, refer to the same inner classes.
	    for (DFKlass inklass : genericKlass.getInnerKlasses()) {
		klass.addKlass(inklass.getName(), inklass);
	    }
        }

        // not loaded yet!
        assert klass._state == LoadState.Unloaded;

        // load() will recreate the entire subspace.
        return klass;
    }

    @Override
    public void writeXML(XMLStreamWriter writer)
        throws XMLStreamException {
        writer.writeAttribute("path", this.getFilePath());
        super.writeXML(writer);
    }

    @SuppressWarnings("unchecked")
    protected void buildTypeFromDecls(ASTNode ast)
	throws InvalidSyntax {

        List<BodyDeclaration> decls;
        if (ast instanceof AbstractTypeDeclaration) {
            decls = ((AbstractTypeDeclaration)ast).bodyDeclarations();
        } else if (ast instanceof ClassInstanceCreation) {
	    ClassInstanceCreation cstr = (ClassInstanceCreation)ast;
            decls = cstr.getAnonymousClassDeclaration().bodyDeclarations();
        } else {
            throw new InvalidSyntax(ast);
        }

        _initMethod = new DFMethod(
            this, DFMethod.CallStyle.Initializer, false,
            "<clinit>", "<clinit>", this.getKlassScope());
        _initMethod.setTree(ast);
        _initMethod.buildFuncType(this);

        for (BodyDeclaration body : decls) {
	    if (body instanceof AbstractTypeDeclaration) {
                AbstractTypeDeclaration abstTypeDecl = (AbstractTypeDeclaration)body;
                String path = this.getFilePath();
		DFSourceKlass klass = this.buildTypeFromAST(
                    path, abstTypeDecl, this.getKlassScope(), this);
                klass.setKlassTree(path, abstTypeDecl);

	    } else if (body instanceof FieldDeclaration) {
                FieldDeclaration fieldDecl = (FieldDeclaration)body;
                for (VariableDeclarationFragment frag :
                         (List<VariableDeclarationFragment>) fieldDecl.fragments()) {
                    Expression init = frag.getInitializer();
                    if (init != null) {
                        this.buildTypeFromExpr(init, _initMethod, _initMethod.getScope());
                    }
                }

	    } else if (body instanceof MethodDeclaration) {
                MethodDeclaration methodDecl = (MethodDeclaration)body;
                String id = Utils.encodeASTNode(methodDecl);
                String name;
                DFMethod.CallStyle callStyle;
                if (methodDecl.isConstructor()) {
                    name = "<init>";
                    callStyle = DFMethod.CallStyle.Constructor;
                } else {
                    name = methodDecl.getName().getIdentifier();
                    callStyle = (isStatic(methodDecl))?
                        DFMethod.CallStyle.StaticMethod :
                        DFMethod.CallStyle.InstanceMethod;
                }
                Statement stmt = methodDecl.getBody();
                DFMethod method = new DFMethod(
                    this, callStyle, (stmt == null),
		    id, name, this.getKlassScope());
		method.setMapTypes(methodDecl.typeParameters());
                method.setTree(methodDecl);
                this.addMethod(method, id);
                if (stmt != null) {
                    this.buildTypeFromStmt(stmt, method, method.getScope());
                }

	    } else if (body instanceof AnnotationTypeMemberDeclaration) {
		;

	    } else if (body instanceof Initializer) {
		Initializer initializer = (Initializer)body;
                Statement stmt = initializer.getBody();
                if (stmt != null) {
                    this.buildTypeFromStmt(stmt, _initMethod, _initMethod.getScope());
                }

	    } else {
		throw new InvalidSyntax(body);
	    }
	}
    }

    @SuppressWarnings("unchecked")
    protected void buildTypeFromStmt(
        Statement ast,
        DFTypeSpace space, DFLocalScope outerScope)
        throws InvalidSyntax {
        assert ast != null;

        if (ast instanceof AssertStatement) {

        } else if (ast instanceof Block) {
            Block block = (Block)ast;
            DFLocalScope innerScope = outerScope.addChild(ast);
            for (Statement stmt :
                     (List<Statement>) block.statements()) {
                this.buildTypeFromStmt(stmt, space, innerScope);
            }

        } else if (ast instanceof EmptyStatement) {

        } else if (ast instanceof VariableDeclarationStatement) {
            VariableDeclarationStatement varStmt =
                (VariableDeclarationStatement)ast;
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>) varStmt.fragments()) {
                Expression expr = frag.getInitializer();
                if (expr != null) {
                    this.buildTypeFromExpr(expr, space, outerScope);
                }
            }

        } else if (ast instanceof ExpressionStatement) {
            ExpressionStatement exprStmt = (ExpressionStatement)ast;
            this.buildTypeFromExpr(exprStmt.getExpression(), space, outerScope);

        } else if (ast instanceof ReturnStatement) {
            ReturnStatement returnStmt = (ReturnStatement)ast;
            Expression expr = returnStmt.getExpression();
            if (expr != null) {
                this.buildTypeFromExpr(expr, space, outerScope);
            }

        } else if (ast instanceof IfStatement) {
            IfStatement ifStmt = (IfStatement)ast;
            this.buildTypeFromExpr(ifStmt.getExpression(), space, outerScope);
            Statement thenStmt = ifStmt.getThenStatement();
            this.buildTypeFromStmt(thenStmt, space, outerScope);
            Statement elseStmt = ifStmt.getElseStatement();
            if (elseStmt != null) {
                this.buildTypeFromStmt(elseStmt, space, outerScope);
            }

        } else if (ast instanceof SwitchStatement) {
            SwitchStatement switchStmt = (SwitchStatement)ast;
            DFLocalScope innerScope = outerScope.addChild(ast);
            this.buildTypeFromExpr(switchStmt.getExpression(), space, innerScope);
            for (Statement stmt :
                     (List<Statement>) switchStmt.statements()) {
                this.buildTypeFromStmt(stmt, space, innerScope);
            }

        } else if (ast instanceof SwitchCase) {
            SwitchCase switchCase = (SwitchCase)ast;
            Expression expr = switchCase.getExpression();
            if (expr != null) {
                this.buildTypeFromExpr(expr, space, outerScope);
            }

        } else if (ast instanceof WhileStatement) {
            WhileStatement whileStmt = (WhileStatement)ast;
            this.buildTypeFromExpr(whileStmt.getExpression(), space, outerScope);
            DFLocalScope innerScope = outerScope.addChild(ast);
            Statement stmt = whileStmt.getBody();
            this.buildTypeFromStmt(stmt, space, innerScope);

        } else if (ast instanceof DoStatement) {
            DoStatement doStmt = (DoStatement)ast;
            DFLocalScope innerScope = outerScope.addChild(ast);
            Statement stmt = doStmt.getBody();
            this.buildTypeFromStmt(stmt, space, innerScope);
            this.buildTypeFromExpr(doStmt.getExpression(), space, innerScope);

        } else if (ast instanceof ForStatement) {
            ForStatement forStmt = (ForStatement)ast;
            DFLocalScope innerScope = outerScope.addChild(ast);
            for (Expression init :
                     (List<Expression>) forStmt.initializers()) {
                this.buildTypeFromExpr(init, space, innerScope);
            }
            Expression expr = forStmt.getExpression();
            if (expr != null) {
                this.buildTypeFromExpr(expr, space, innerScope);
            }
            Statement stmt = forStmt.getBody();
            this.buildTypeFromStmt(stmt, space, innerScope);
            for (Expression update :
                     (List<Expression>) forStmt.updaters()) {
                this.buildTypeFromExpr(update, space, innerScope);
            }

        } else if (ast instanceof EnhancedForStatement) {
            EnhancedForStatement eForStmt = (EnhancedForStatement)ast;
            this.buildTypeFromExpr(eForStmt.getExpression(), space, outerScope);
            DFLocalScope innerScope = outerScope.addChild(ast);
            this.buildTypeFromStmt(eForStmt.getBody(), space, innerScope);

        } else if (ast instanceof BreakStatement) {

        } else if (ast instanceof ContinueStatement) {

        } else if (ast instanceof LabeledStatement) {
            LabeledStatement labeledStmt = (LabeledStatement)ast;
            Statement stmt = labeledStmt.getBody();
            this.buildTypeFromStmt(stmt, space, outerScope);

        } else if (ast instanceof SynchronizedStatement) {
            SynchronizedStatement syncStmt = (SynchronizedStatement)ast;
            this.buildTypeFromExpr(syncStmt.getExpression(), space, outerScope);
            this.buildTypeFromStmt(syncStmt.getBody(), space, outerScope);

        } else if (ast instanceof TryStatement) {
            TryStatement tryStmt = (TryStatement)ast;
            DFLocalScope innerScope = outerScope.addChild(ast);
            for (VariableDeclarationExpression decl :
                     (List<VariableDeclarationExpression>) tryStmt.resources()) {
                this.buildTypeFromExpr(decl, space, innerScope);
            }
            this.buildTypeFromStmt(tryStmt.getBody(), space, innerScope);
            for (CatchClause cc :
                     (List<CatchClause>) tryStmt.catchClauses()) {
                DFLocalScope catchScope = outerScope.addChild(cc);
                this.buildTypeFromStmt(cc.getBody(), space, catchScope);
            }
            Block finBlock = tryStmt.getFinally();
            if (finBlock != null) {
                this.buildTypeFromStmt(finBlock, space, outerScope);
            }

        } else if (ast instanceof ThrowStatement) {
            ThrowStatement throwStmt = (ThrowStatement)ast;
            Expression expr = throwStmt.getExpression();
            if (expr != null) {
                this.buildTypeFromExpr(expr, space, outerScope);
            }

        } else if (ast instanceof ConstructorInvocation) {
            ConstructorInvocation ci = (ConstructorInvocation)ast;
            for (Expression expr :
                     (List<Expression>) ci.arguments()) {
                this.buildTypeFromExpr(expr, space, outerScope);
            }

        } else if (ast instanceof SuperConstructorInvocation) {
            SuperConstructorInvocation sci = (SuperConstructorInvocation)ast;
            for (Expression expr :
                     (List<Expression>) sci.arguments()) {
                this.buildTypeFromExpr(expr, space, outerScope);
            }

        } else if (ast instanceof TypeDeclarationStatement) {
            TypeDeclarationStatement typeDeclStmt = (TypeDeclarationStatement)ast;
            AbstractTypeDeclaration abstTypeDecl = typeDeclStmt.getDeclaration();
            String path = this.getFilePath();
            DFSourceKlass klass = space.buildTypeFromAST(
		path, abstTypeDecl, outerScope, this);
            klass.setKlassTree(path, abstTypeDecl);

        } else {
            throw new InvalidSyntax(ast);

        }
    }

    @SuppressWarnings("unchecked")
    protected void buildTypeFromExpr(
        Expression expr,
        DFTypeSpace space, DFVarScope outerScope)
        throws InvalidSyntax {
        assert expr != null;

        if (expr instanceof Annotation) {

        } else if (expr instanceof Name) {

        } else if (expr instanceof ThisExpression) {

        } else if (expr instanceof BooleanLiteral) {

        } else if (expr instanceof CharacterLiteral) {

        } else if (expr instanceof NullLiteral) {

        } else if (expr instanceof NumberLiteral) {

        } else if (expr instanceof StringLiteral) {

        } else if (expr instanceof TypeLiteral) {

        } else if (expr instanceof PrefixExpression) {
            PrefixExpression prefix = (PrefixExpression)expr;
            this.buildTypeFromExpr(prefix.getOperand(), space, outerScope);

        } else if (expr instanceof PostfixExpression) {
            PostfixExpression postfix = (PostfixExpression)expr;
            this.buildTypeFromExpr(postfix.getOperand(), space, outerScope);

        } else if (expr instanceof InfixExpression) {
            InfixExpression infix = (InfixExpression)expr;
            this.buildTypeFromExpr(infix.getLeftOperand(), space, outerScope);
            this.buildTypeFromExpr(infix.getRightOperand(), space, outerScope);

        } else if (expr instanceof ParenthesizedExpression) {
            ParenthesizedExpression paren = (ParenthesizedExpression)expr;
            this.buildTypeFromExpr(paren.getExpression(), space, outerScope);

        } else if (expr instanceof Assignment) {
            Assignment assn = (Assignment)expr;
            this.buildTypeFromExpr(assn.getLeftHandSide(), space, outerScope);
            this.buildTypeFromExpr(assn.getRightHandSide(), space, outerScope);

        } else if (expr instanceof VariableDeclarationExpression) {
            VariableDeclarationExpression decl =
                (VariableDeclarationExpression)expr;
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>) decl.fragments()) {
                Expression init = frag.getInitializer();
                if (init != null) {
                    this.buildTypeFromExpr(init, space, outerScope);
                }
            }

        } else if (expr instanceof MethodInvocation) {
            MethodInvocation invoke = (MethodInvocation)expr;
            Expression expr1 = invoke.getExpression();
            if (expr1 != null) {
                this.buildTypeFromExpr(expr1, space, outerScope);
            }
            for (Expression arg : (List<Expression>) invoke.arguments()) {
                this.buildTypeFromExpr(arg, space, outerScope);
            }

        } else if (expr instanceof SuperMethodInvocation) {
            SuperMethodInvocation sinvoke = (SuperMethodInvocation)expr;
            for (Expression arg : (List<Expression>) sinvoke.arguments()) {
                this.buildTypeFromExpr(arg, space, outerScope);
            }

        } else if (expr instanceof ArrayCreation) {
            ArrayCreation ac = (ArrayCreation)expr;
            for (Expression dim : (List<Expression>) ac.dimensions()) {
                this.buildTypeFromExpr(dim, space, outerScope);
            }
            ArrayInitializer init = ac.getInitializer();
            if (init != null) {
                this.buildTypeFromExpr(init, space, outerScope);
            }

        } else if (expr instanceof ArrayInitializer) {
            ArrayInitializer init = (ArrayInitializer)expr;
            for (Expression expr1 : (List<Expression>) init.expressions()) {
                this.buildTypeFromExpr(expr1, space, outerScope);
            }

        } else if (expr instanceof ArrayAccess) {
            ArrayAccess aa = (ArrayAccess)expr;
            this.buildTypeFromExpr(aa.getIndex(), space, outerScope);
            this.buildTypeFromExpr(aa.getArray(), space, outerScope);

        } else if (expr instanceof FieldAccess) {
            FieldAccess fa = (FieldAccess)expr;
            this.buildTypeFromExpr(fa.getExpression(), space, outerScope);

        } else if (expr instanceof SuperFieldAccess) {

        } else if (expr instanceof CastExpression) {
            CastExpression cast = (CastExpression)expr;
            this.buildTypeFromExpr(cast.getExpression(), space, outerScope);

        } else if (expr instanceof ClassInstanceCreation) {
            ClassInstanceCreation cstr = (ClassInstanceCreation)expr;
            Expression expr1 = cstr.getExpression();
            if (expr1 != null) {
                this.buildTypeFromExpr(expr1, space, outerScope);
            }
            for (Expression arg : (List<Expression>) cstr.arguments()) {
                this.buildTypeFromExpr(arg, space, outerScope);
            }
            if (cstr.getAnonymousClassDeclaration() != null) {
                String id = Utils.encodeASTNode(cstr);
                DFSourceKlass anonKlass = new DFSourceKlass(
                    id, space, outerScope, this);
                space.addKlass(id, anonKlass);
                anonKlass.setKlassTree(this.getFilePath(), cstr);
            }

        } else if (expr instanceof ConditionalExpression) {
            ConditionalExpression cond = (ConditionalExpression)expr;
            this.buildTypeFromExpr(cond.getExpression(), space, outerScope);
            this.buildTypeFromExpr(cond.getThenExpression(), space, outerScope);
            this.buildTypeFromExpr(cond.getElseExpression(), space, outerScope);

        } else if (expr instanceof InstanceofExpression) {

        } else if (expr instanceof LambdaExpression) {
            LambdaExpression lambda = (LambdaExpression)expr;
            String id = Utils.encodeASTNode(lambda);
            DFSourceKlass lambdaKlass = new DFLambdaKlass(
		id, space, outerScope, this);
	    space.addKlass(id, lambdaKlass);
            lambdaKlass.setKlassTree(this.getFilePath(), lambda);

        } else if (expr instanceof MethodReference) {
            //  CreationReference
            //  ExpressionMethodReference
            //  SuperMethodReference
            //  TypeMethodReference
            MethodReference methodref = (MethodReference)expr;
            String id = Utils.encodeASTNode(methodref);
            DFSourceKlass methodRefKlass = new DFMethodRefKlass(
		id, space, outerScope, this);
	    space.addKlass(id, methodRefKlass);
            methodRefKlass.setKlassTree(this.getFilePath(), methodref);

        } else {
            // ???
            throw new InvalidSyntax(expr);
        }
    }

    public String getFilePath() {
        return _filePath;
    }
    public ASTNode getTree() {
        return _ast;
    }

    public void setFinder(DFTypeFinder finder) {
        assert _state == LoadState.Unloaded;
        //assert _finder == null || _finder == finder;
	_finder = finder;
    }

    public DFTypeFinder getFinder() {
        if (_outerKlass != null) {
            assert _finder == null;
            return new DFTypeFinder(this, _outerKlass.getFinder());
        } else {
            //assert _finder != null;
            return new DFTypeFinder(this, _finder);
        }
    }

    // Only used by DFLambdaKlass.
    protected void setBaseKlass(DFKlass klass) {
        _baseKlass = klass;
    }

    public boolean isDefined() {
        return (_state == LoadState.Loaded);
    }

    public DFMethod getInitMethod() {
        assert _state == LoadState.Loaded;
        return _initMethod;
    }

    @Override
    public DFMethod findMethod(
        DFMethod.CallStyle callStyle, String id, DFType[] argTypes) {
        assert _state == LoadState.Loaded;
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
        throws InvalidSyntax {
        // an unspecified parameterized klass cannot be loaded.
        if (_state != LoadState.Unloaded) return;
        _state = LoadState.Loading;
        if (_outerKlass != null) {
            _outerKlass.load();
        }
        DFTypeFinder finder = this.getFinder();
        assert finder != null;
        assert _ast != null || _jarPath != null;
        if (this.isGeneric()) {
            // a generic class is only referred to, but not built.
        } else if (_ast != null) {
            this.initScope();
            this.buildTypeFromDecls(_ast);
            this.loadMembersFromAST(finder, _ast);
        } else if (_jarPath != null) {
            this.initScope();
            try {
                JarFile jarfile = new JarFile(_jarPath);
                try {
                    JarEntry je = jarfile.getJarEntry(_entPath);
                    InputStream strm = jarfile.getInputStream(je);
                    JavaClass jklass = new ClassParser(strm, _entPath).parse();
                    this.loadMembersFromJKlass(finder, jklass);
                } finally {
                    jarfile.close();
                }
            } catch (IOException e) {
                Logger.error(
                    "DFKlass.load: IOException",
                    this, _jarPath+"/"+_entPath);
            }
        }
        _state = LoadState.Loaded;
    }

    private void loadMembersFromJKlass(DFTypeFinder finder, JavaClass jklass)
        throws InvalidSyntax {
        //Logger.info("DFKlass.loadMembersFromJKlass:", this, finder);
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
                    "DFKlass.loadMembersFromJKlass: TypeNotFound (baseKlass)",
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
                        "DFKlass.loadMembersFromJKlass: TypeNotFound (iface)",
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
                        "DFKlass.loadMembersFromJKlass: TypeNotFound (baseKlass)",
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
                            "DFKlass.loadMembersFromJKlass: TypeNotFound (iface)",
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
                    "DFKlass.loadMembersFromJKlass: TypeNotFound (field)",
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
		method.setMapTypes(sig);
                if (method.isGeneric()) continue;
		JNITypeParser parser = new JNITypeParser(sig);
		try {
		    funcType = (DFFunctionType)parser.resolveType(method.getFinder());
		} catch (TypeNotFound e) {
		    Logger.error(
                        "DFKlass.loadMembersFromJKlass: TypeNotFound (method)",
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
                            "DFKlass.loadMembersFromJKlass: TypeNotFound (exception)",
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

    @SuppressWarnings("unchecked")
    protected void loadMembersFromAST(DFTypeFinder finder, ASTNode ast)
        throws InvalidSyntax {
        //Logger.info("DFKlass.loadMembersFromAST:", this, finder);
        if (ast instanceof AbstractTypeDeclaration) {
            this.loadMembersFromAbstTypeDecl(finder, (AbstractTypeDeclaration)ast);

        } else if (ast instanceof ClassInstanceCreation) {
            this.loadMembersFromAnonDecl(finder, (ClassInstanceCreation)ast);

        } else {
            throw new InvalidSyntax(ast);
        }
    }

    private void loadMembersFromAbstTypeDecl(
        DFTypeFinder finder, AbstractTypeDeclaration abstTypeDecl)
        throws InvalidSyntax {
        if (abstTypeDecl instanceof TypeDeclaration) {
            this.loadMembersFromTypeDecl(finder, (TypeDeclaration)abstTypeDecl);

        } else if (abstTypeDecl instanceof EnumDeclaration) {
            this.loadMembersFromEnumDecl(finder, (EnumDeclaration)abstTypeDecl);

        } else if (abstTypeDecl instanceof AnnotationTypeDeclaration) {
            this.loadMembersFromAnnotTypeDecl(finder, (AnnotationTypeDeclaration)abstTypeDecl);
        }
    }

    @SuppressWarnings("unchecked")
    private void loadMembersFromTypeDecl(
        DFTypeFinder finder, TypeDeclaration typeDecl)
        throws InvalidSyntax {
        _interface = typeDecl.isInterface();
        // Load base klasses/interfaces.
	// Get superclass.
	Type superClass = typeDecl.getSuperclassType();
	if (superClass == null) {
	    _baseKlass = DFBuiltinTypes.getObjectKlass();
	} else {
	    try {
		_baseKlass = finder.resolve(superClass).toKlass();
		_baseKlass.load();
	    } catch (TypeNotFound e) {
		Logger.error(
                    "DFKlass.loadMembersFromTypeDecl: TypeNotFound (baseKlass)",
                    this, e.name);
	    }
	}
	// Get interfaces.
	List<Type> ifaces = typeDecl.superInterfaceTypes();
	_baseIfaces = new DFKlass[ifaces.size()];
	for (int i = 0; i < ifaces.size(); i++) {
	    DFKlass iface = DFBuiltinTypes.getObjectKlass();
	    try {
		iface = finder.resolve(ifaces.get(i)).toKlass();
	    } catch (TypeNotFound e) {
		Logger.error(
                    "DFKlass.loadMembersFromTypeDecl: TypeNotFound (iface)",
                    this, e.name);
	    }
	    _baseIfaces[i] = iface;
	}
	for (DFKlass iface : _baseIfaces) {
	    iface.load();
	}
	this.loadMembers(finder, typeDecl.bodyDeclarations());
    }

    @SuppressWarnings("unchecked")
    private void loadMembersFromAnonDecl(
        DFTypeFinder finder, ClassInstanceCreation cstr)
        throws InvalidSyntax {
	// Get superclass.
	Type superClass = cstr.getType();
	if (superClass == null) {
	    _baseKlass = DFBuiltinTypes.getObjectKlass();
	} else {
	    try {
		_baseKlass = finder.resolve(superClass).toKlass();
		_baseKlass.load();
	    } catch (TypeNotFound e) {
		Logger.error(
                    "DFKlass.loadMembersFromAnonDecl: TypeNotFound (baseKlass)",
                    this, e.name);
	    }
	}
	this.loadMembers(
	    finder, cstr.getAnonymousClassDeclaration().bodyDeclarations());
    }

    @SuppressWarnings("unchecked")
    private void loadMembersFromEnumDecl(
        DFTypeFinder finder, EnumDeclaration enumDecl)
        throws InvalidSyntax {
        // Load base klasses/interfaces.
	// Get superclass.
	DFKlass enumKlass = DFBuiltinTypes.getEnumKlass();
	_baseKlass = enumKlass.getConcreteKlass(new DFKlass[] { this });
	_baseKlass.load();
	// Get interfaces.
	List<Type> ifaces = enumDecl.superInterfaceTypes();
	_baseIfaces = new DFKlass[ifaces.size()];
	for (int i = 0; i < ifaces.size(); i++) {
	    DFKlass iface = DFBuiltinTypes.getObjectKlass();
	    try {
		iface = finder.resolve(ifaces.get(i)).toKlass();
	    } catch (TypeNotFound e) {
		Logger.error(
                    "DFKlass.loadMembersFromEnumDecl: TypeNotFound (iface)",
                    this, e.name);
	    }
	    _baseIfaces[i] = iface;
	}
	for (DFKlass iface : _baseIfaces) {
	    iface.load();
	}
	// Get constants.
	for (EnumConstantDeclaration econst :
		 (List<EnumConstantDeclaration>) enumDecl.enumConstants()) {
	    this.addField(econst.getName(), true, this);
	}
	// Enum has a special method "values()".
	DFMethod method = new DFMethod(
	    this, DFMethod.CallStyle.InstanceMethod, false,
            "values", "values", null);
	method.setFuncType(
	    new DFFunctionType(new DFType[] {}, DFArrayType.getType(this, 1)));
	this.addMethod(method, null);
	this.loadMembers(finder, enumDecl.bodyDeclarations());
    }

    @SuppressWarnings("unchecked")
    private void loadMembersFromAnnotTypeDecl(
        DFTypeFinder finder, AnnotationTypeDeclaration annotTypeDecl)
        throws InvalidSyntax {
	this.loadMembers(finder, annotTypeDecl.bodyDeclarations());
    }

    @SuppressWarnings("unchecked")
    private void loadMembers(DFTypeFinder finder, List<BodyDeclaration> decls)
        throws InvalidSyntax {
        if (_initMethod != null) {
            _initMethod.setFinder(finder);
        }

        for (BodyDeclaration body : decls) {
            if (body instanceof AbstractTypeDeclaration) {
                // Child klasses are loaded independently.

            } else if (body instanceof FieldDeclaration) {
                FieldDeclaration decl = (FieldDeclaration)body;
                DFType fldType = finder.resolveSafe(decl.getType());
                for (VariableDeclarationFragment frag :
                         (List<VariableDeclarationFragment>) decl.fragments()) {
                    DFType ft = fldType;
                    int ndims = frag.getExtraDimensions();
                    if (ndims != 0) {
                        ft = DFArrayType.getType(ft, ndims);
                    }
                    this.addField(frag.getName(), isStatic(decl), ft);
                }

            } else if (body instanceof MethodDeclaration) {
                MethodDeclaration decl = (MethodDeclaration)body;
                String id = Utils.encodeASTNode(decl);
                DFMethod method = this.getMethod(id);
                method.setFinder(finder);
                method.buildFuncType(this);

            } else if (body instanceof EnumConstantDeclaration) {

            } else if (body instanceof AnnotationTypeMemberDeclaration) {
                AnnotationTypeMemberDeclaration decl =
                    (AnnotationTypeMemberDeclaration)body;
                DFType type = finder.resolveSafe(decl.getType());
                this.addField(decl.getName(), isStatic(decl), type);

            } else if (body instanceof Initializer) {

            } else {
                throw new InvalidSyntax(body);
            }
        }
    }
}
