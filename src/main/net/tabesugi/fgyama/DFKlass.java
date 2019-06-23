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
public class DFKlass extends DFTypeSpace implements DFType, Comparable<DFKlass> {

    // These fields are available upon construction.
    private String _name;
    private DFTypeSpace _outerSpace;
    private DFKlass _outerKlass; // can be the same as outerSpace, or null.
    private DFVarScope _outerScope;
    private DFKlassScope _klassScope;

    // These fields are set immediately.
    private ASTNode _ast = null;
    private String _filePath = null;
    private String _jarPath = null;
    private String _entPath = null;

    // These fields are available after setMapTypes().
    private DFMapType[] _mapTypes = null;
    private Map<String, DFType> _mapTypeMap = null;
    private Map<String, DFKlass> _paramKlasses =
        new TreeMap<String, DFKlass>();

    // These fields are available only for parameterized klasses.
    private DFKlass _genericKlass = null;
    private DFType[] _paramTypes = null;
    private Map<String, DFType> _paramTypeMap = null;

    // This field is available after setFinder(). (Pass2)
    private DFTypeFinder _finder = null;

    // The following fields are available after the klass is loaded.
    private boolean _built = false;
    private DFKlass _baseKlass = null;
    private DFKlass[] _baseIfaces = null;
    private DFMethod _initMethod = null;
    private List<DFRef> _fields = new ArrayList<DFRef>();
    private List<DFMethod> _methods = new ArrayList<DFMethod>();
    private Map<String, DFMethod> _id2method =
        new HashMap<String, DFMethod>();

    public DFKlass(
        String name, DFTypeSpace outerSpace,
        DFKlass outerKlass, DFVarScope outerScope) {
	super(name, outerSpace);
        _name = name;
        _outerSpace = outerSpace;
        _outerKlass = outerKlass;
	_outerScope = outerScope;
        _klassScope = new DFKlassScope(_outerScope, _name);
    }

    protected DFKlass(
        String name, DFTypeSpace outerSpace,
        DFKlass outerKlass, DFVarScope outerScope,
        DFKlass baseKlass) {
        this(name, outerSpace, outerKlass, outerScope);
        _baseKlass = baseKlass;
        _built = true;
    }

    // Constructor for a parameterized klass.
    @SuppressWarnings("unchecked")
    private DFKlass(
        DFKlass genericKlass, DFType[] paramTypes)
	throws InvalidSyntax {
	super(genericKlass._name + getParamName(paramTypes), genericKlass._outerSpace);
        assert genericKlass != null;
        assert paramTypes != null;
        // A parameterized Klass is NOT accessible from
        // the outer namespace but it creates its own subspace.
        _name = genericKlass._name;
        _outerSpace = genericKlass._outerSpace;
        _outerKlass = genericKlass._outerKlass;
        _outerScope = genericKlass._outerScope;
        String subname = genericKlass._name + getParamName(paramTypes);
        _klassScope = new DFKlassScope(_outerScope, subname);

        _genericKlass = genericKlass;
        _paramTypes = paramTypes;
        _paramTypeMap = new HashMap<String, DFType>();
        for (int i = 0; i < _paramTypes.length; i++) {
            DFMapType mapType = genericKlass._mapTypes[i];
            DFType paramType = _paramTypes[i];
            assert mapType != null;
            assert paramType != null;
            _paramTypeMap.put(mapType.getTypeName(), paramType);
        }

        _ast = genericKlass._ast;
        _filePath = genericKlass._filePath;
        _jarPath = genericKlass._jarPath;
        _entPath = genericKlass._entPath;

        _finder = genericKlass._finder;
        // Recreate the entire subspace.
	if (_ast != null) {
	    this.buildTypeFromDecls(_ast);
	} else {
            // In case of a .jar class, refer to the same inner classes.
	    for (Map.Entry<String,DFKlass> e : genericKlass.getKlasses()) {
		String id = e.getKey();
		DFKlass klass = e.getValue();
		this.addKlass(id, klass);
	    }
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

    public DFKlass toKlass() {
        return this;
    }

    public int canConvertFrom(DFType type, Map<DFMapType, DFType> typeMap) {
        if (type instanceof DFNullType) return 0;
        DFKlass klass = type.toKlass();
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
        _mapTypes = DFTypeSpace.getMapTypes(tps);
        if (_mapTypes != null) {
            _mapTypeMap = new HashMap<String, DFType>();
            for (DFMapType mapType : _mapTypes) {
                _mapTypeMap.put(
                    mapType.getTypeName(),
                    DFBuiltinTypes.getObjectKlass());
            }
        }
    }

    public void setMapTypes(String sig) {
        assert _mapTypes == null;
        assert _paramTypes == null;
        _mapTypes = JNITypeParser.getMapTypes(sig);
        if (_mapTypes != null) {
            _mapTypeMap = new HashMap<String, DFType>();
            for (DFMapType mapType : _mapTypes) {
                _mapTypeMap.put(
                    mapType.getTypeName(),
                    DFBuiltinTypes.getObjectKlass());
            }
        }
    }

    public DFKlass parameterize(DFType[] paramTypes)
	throws InvalidSyntax {
        assert _mapTypes != null;
        assert paramTypes.length <= _mapTypes.length;
        if (paramTypes.length < _mapTypes.length) {
            DFType[] types = new DFType[_mapTypes.length];
            for (int i = 0; i < _mapTypes.length; i++) {
                if (i < paramTypes.length) {
                    types[i] = paramTypes[i];
                } else {
                    types[i] = _mapTypes[i].toKlass();
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

    public void setKlassTree(String filePath, ASTNode ast)
	throws InvalidSyntax {
        _filePath = filePath;
        _ast = ast;
	this.buildTypeFromDecls(ast);
    }

    @SuppressWarnings("unchecked")
    private void buildTypeFromDecls(ASTNode ast)
	throws InvalidSyntax {

        List<BodyDeclaration> decls;
        if (ast instanceof AbstractTypeDeclaration) {
            decls = ((AbstractTypeDeclaration)ast).bodyDeclarations();
        } else if (ast instanceof AnonymousClassDeclaration) {
            decls = ((AnonymousClassDeclaration)ast).bodyDeclarations();
        } else {
            throw new InvalidSyntax(ast);
        }

        DFLocalVarScope initScope = new DFLocalVarScope(_klassScope, "<clinit>");
        _initMethod = new DFMethod(
            this, "<clinit>", DFCallStyle.Initializer, "<clinit>", initScope);
        _initMethod.setFuncType(
            new DFFunctionType(new DFType[] {}, DFBasicType.VOID));
        _initMethod.setTree(ast);

        for (BodyDeclaration body : decls) {
	    if (body instanceof AbstractTypeDeclaration) {
                AbstractTypeDeclaration abstTypeDecl = (AbstractTypeDeclaration)body;
                String path = this.getFilePath();
		DFKlass klass = this.buildTypeFromTree(
                    path, abstTypeDecl, this, _klassScope);
                klass.setKlassTree(path, abstTypeDecl);

	    } else if (body instanceof FieldDeclaration) {
                FieldDeclaration fieldDecl = (FieldDeclaration)body;
                for (VariableDeclarationFragment frag :
                         (List<VariableDeclarationFragment>) fieldDecl.fragments()) {
                    Expression init = frag.getInitializer();
                    if (init != null) {
                        this.buildTypeFromExpr(init, this, _klassScope);
                    }
                }

	    } else if (body instanceof MethodDeclaration) {
                MethodDeclaration methodDecl = (MethodDeclaration)body;
                String id = Utils.encodeASTNode(methodDecl);
                String name;
                DFCallStyle callStyle;
                if (methodDecl.isConstructor()) {
                    name = "<init>";
                    callStyle = DFCallStyle.Constructor;
                } else {
                    name = methodDecl.getName().getIdentifier();
                    callStyle = (isStatic(methodDecl))?
                        DFCallStyle.StaticMethod : DFCallStyle.InstanceMethod;
                }
                DFLocalVarScope scope = new DFLocalVarScope(_klassScope, id);
                DFMethod method = new DFMethod(this, id, callStyle, name, scope);
                this.addMethod(method, id);
                Statement stmt = methodDecl.getBody();
                if (stmt != null) {
                    this.buildTypeFromStmt(stmt, method, scope);
                }

	    } else if (body instanceof AnnotationTypeMemberDeclaration) {
		;

	    } else if (body instanceof Initializer) {
		Initializer initializer = (Initializer)body;
                String id = Utils.encodeASTNode(initializer);
                DFLocalVarScope innerScope = initScope.addChild(body);
                Statement stmt = initializer.getBody();
                if (stmt != null) {
                    this.buildTypeFromStmt(stmt, _initMethod, innerScope);
                }

	    } else {
		throw new InvalidSyntax(body);
	    }
	}
    }

    @SuppressWarnings("unchecked")
    private void buildTypeFromStmt(
        Statement ast,
        DFTypeSpace space, DFLocalVarScope outerScope)
        throws InvalidSyntax {
        assert ast != null;

        if (ast instanceof AssertStatement) {

        } else if (ast instanceof Block) {
            Block block = (Block)ast;
            DFLocalVarScope innerScope = outerScope.addChild(ast);
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
            DFLocalVarScope innerScope = outerScope.addChild(ast);
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
            DFLocalVarScope innerScope = outerScope.addChild(ast);
            Statement stmt = whileStmt.getBody();
            this.buildTypeFromStmt(stmt, space, innerScope);

        } else if (ast instanceof DoStatement) {
            DoStatement doStmt = (DoStatement)ast;
            DFLocalVarScope innerScope = outerScope.addChild(ast);
            Statement stmt = doStmt.getBody();
            this.buildTypeFromStmt(stmt, space, innerScope);
            this.buildTypeFromExpr(doStmt.getExpression(), space, innerScope);

        } else if (ast instanceof ForStatement) {
            ForStatement forStmt = (ForStatement)ast;
            DFLocalVarScope innerScope = outerScope.addChild(ast);
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
            DFLocalVarScope innerScope = outerScope.addChild(ast);
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
            DFLocalVarScope innerScope = outerScope.addChild(ast);
            for (VariableDeclarationExpression decl :
                     (List<VariableDeclarationExpression>) tryStmt.resources()) {
                this.buildTypeFromExpr(decl, space, innerScope);
            }
            this.buildTypeFromStmt(tryStmt.getBody(), space, innerScope);
            for (CatchClause cc :
                     (List<CatchClause>) tryStmt.catchClauses()) {
                DFLocalVarScope catchScope = outerScope.addChild(cc);
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
            DFKlass klass = space.buildTypeFromTree(path, abstTypeDecl, this, outerScope);
            klass.setKlassTree(path, abstTypeDecl);

        } else {
            throw new InvalidSyntax(ast);

        }
    }

    @SuppressWarnings("unchecked")
    private void buildTypeFromExpr(
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
            AnonymousClassDeclaration anonDecl =
                cstr.getAnonymousClassDeclaration();
            if (anonDecl != null) {
                String id = Utils.encodeASTNode(anonDecl);
                DFKlass anonKlass = space.createKlass(this, outerScope, id);
                anonKlass.setKlassTree(this.getFilePath(), anonDecl);
            }

        } else if (expr instanceof ConditionalExpression) {
            ConditionalExpression cond = (ConditionalExpression)expr;
            this.buildTypeFromExpr(cond.getExpression(), space, outerScope);
            this.buildTypeFromExpr(cond.getThenExpression(), space, outerScope);
            this.buildTypeFromExpr(cond.getElseExpression(), space, outerScope);

        } else if (expr instanceof InstanceofExpression) {

        } else if (expr instanceof LambdaExpression) {
            LambdaExpression lambda = (LambdaExpression)expr;
            ASTNode body = lambda.getBody();
            if (body instanceof Statement) {
            } else if (body instanceof Expression) {
            } else {
                throw new InvalidSyntax(body);
            }
	    // XXX TODO LambdaExpression

        } else if (expr instanceof MethodReference) {
            //  CreationReference
            //  ExpressionMethodReference
            //  SuperMethodReference
            //  TypeMethodReference
            // XXX TODO MethodReference

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

    public DFVarScope getKlassScope() {
        return _klassScope;
    }

    public void setFinder(DFTypeFinder finder) {
        assert !_built;
        //assert _finder == null || _finder == finder;
	_finder = new DFTypeFinder(this, finder);
    }

    public DFTypeFinder getFinder() {
        if (_outerKlass != null) {
            assert _finder == null;
            return new DFTypeFinder(this, _outerKlass.getFinder());
        } else {
            return _finder;
        }
    }

    public DFType getType(String id)
        throws TypeNotFound {
        if (_mapTypeMap != null) {
            DFType type = _mapTypeMap.get(id);
            if (type != null) return type;
        }
        if (_paramTypeMap != null) {
            DFType type = _paramTypeMap.get(id);
            if (type != null) return type;
        }
        try {
            return super.getType(id);
        } catch (TypeNotFound e) {
        }
        if (_baseKlass != null) {
            try {
                return _baseKlass.getType(id);
            } catch (TypeNotFound e) {
            }
        }
        if (_baseIfaces != null) {
            for (DFKlass iface : _baseIfaces) {
                if (iface != null) {
                    try {
                        return iface.getType(id);
                    } catch (TypeNotFound e) {
                    }
                }
            }
        }
        throw new TypeNotFound(this.getSpaceName()+id);
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
        return (_baseKlass != null &&
		_baseKlass._genericKlass ==
                DFBuiltinTypes.getEnumKlass());
    }

    public DFMethod getInitMethod() {
        assert _built;
        return _initMethod;
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

    public DFMethod getMethod(String key) {
        return _id2method.get(key);
    }

    private DFMethod addMethod(DFMethod method, String key) {
        //Logger.info("DFKlass.addMethod:", method);
        _methods.add(method);
        if (key != null) {
            _id2method.put(key, method);
        }
        return method;
    }

    public void overrideMethods() {
        // override the methods.
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
        throws InvalidSyntax {
        if (_built) return;
        _built = true;
        if (_outerKlass != null) {
            _outerKlass.load();
        }
        DFTypeFinder finder = this.getFinder();
        if (finder == null) Logger.error("!!!", this, _outerKlass);
        assert _ast != null || _jarPath != null;
        if (_mapTypeMap != null) {
            assert _mapTypes != null;
            for (DFMapType mapType : _mapTypes) {
		mapType.build(finder);
                _mapTypeMap.put(mapType.getTypeName(), mapType.toKlass());
            }
        }
        // a generic class is only referred to, but not built.
        if (_ast != null) {
	    this.buildMembersFromTree(finder, _ast);
        } else if (_jarPath != null) {
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
                Logger.error("File Not found:", _jarPath+"/"+_entPath);
            }
        }
    }

    private void buildMembersFromJKlass(DFTypeFinder finder, JavaClass jklass)
        throws InvalidSyntax {
        //Logger.info("DFKlass.buildMembersFromJKlass:", this, finder);
        // Load base klasses/interfaces.
        String sig = Utils.getJKlassSignature(jklass.getAttributes());
	if (this == DFBuiltinTypes.getObjectKlass()) {
	    ;
	} else if (sig != null) {
            //Logger.info("jklass:", this, sig);
	    JNITypeParser parser = new JNITypeParser(sig);
	    _baseKlass = DFBuiltinTypes.getObjectKlass();
	    try {
		_baseKlass = parser.getType(finder).toKlass();
	    } catch (TypeNotFound e) {
		Logger.error("TypeNotFound", e.name);
	    }
	    _baseKlass.load();
	    List<DFKlass> ifaces = new ArrayList<DFKlass>();
	    for (;;) {
		DFType iface = DFBuiltinTypes.getObjectKlass();
		try {
		    iface = parser.getType(finder);
		} catch (TypeNotFound e) {
		    Logger.error("TypeNotFound", e.name);
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
		    Logger.error("TypeNotFound", e.name);
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
			Logger.error("TypeNotFound", e.name);
		    }
		    _baseIfaces[i] = iface;
		}
                for (DFKlass iface : _baseIfaces) {
                    iface.load();
                }
	    }
	}
        // Extend a TypeFinder for this klass.
        if (_outerKlass != null) {
            _outerKlass.load();
        }
        finder = new DFTypeFinder(this, finder);
        // Define fields.
        for (Field fld : jklass.getFields()) {
            if (fld.isPrivate()) continue;
            sig = Utils.getJKlassSignature(fld.getAttributes());
	    DFType type;
	    try {
		if (sig != null) {
		    //Logger.info("fld:", fld.getName(), sig);
		    JNITypeParser parser = new JNITypeParser(sig);
		    type = parser.getType(finder);
		} else {
		    type = finder.resolve(fld.getType());
		}
	    } catch (TypeNotFound e) {
		Logger.error("TypeNotFound", e.name);
		type = DFUnknownType.UNKNOWN;
	    }
	    this.addField(fld.getName(), fld.isStatic(), type);
        }
        // Define methods.
        for (Method meth : jklass.getMethods()) {
            if (meth.isPrivate()) continue;
            sig = Utils.getJKlassSignature(meth.getAttributes());
            String name = meth.getName();
            DFCallStyle callStyle;
            if (meth.getName().equals("<init>")) {
                callStyle = DFCallStyle.Constructor;
            } else if (meth.isStatic()) {
                callStyle = DFCallStyle.StaticMethod;
            } else {
                callStyle = DFCallStyle.InstanceMethod;
            }
            DFMethod method = new DFMethod(this, sig, callStyle, name, null);
            method.setFinder(finder);
            DFFunctionType funcType;
	    if (sig != null) {
                //Logger.info("meth:", meth.getName(), sig);
                DFMapType[] mapTypes = JNITypeParser.getMapTypes(sig);
                if (mapTypes != null) {
                    method.setMapTypes(mapTypes);
                }
		JNITypeParser parser = new JNITypeParser(sig);
		try {
		    funcType = (DFFunctionType)parser.getType(method.getFinder());
		} catch (TypeNotFound e) {
		    Logger.error("TypeNotFound", e.name);
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
            ExceptionTable excTable = meth.getExceptionTable();
            if (excTable != null) {
                String[] excNames = excTable.getExceptionNames();
                DFType[] exceptions = new DFType[excNames.length];
                for (int i = 0; i < excNames.length; i++) {
		    DFType type;
		    try {
			type = finder.lookupType(excNames[i]);
		    } catch (TypeNotFound e) {
			Logger.error("TypeNotFound", e.name);
			type = DFUnknownType.UNKNOWN;
		    }
		    exceptions[i] = type;
                }
                funcType.setExceptions(exceptions);
            }
            method.setFuncType(funcType);
            this.addMethod(method, null);
        }
    }

    @SuppressWarnings("unchecked")
    protected void buildMembersFromTree(DFTypeFinder finder, ASTNode ast)
        throws InvalidSyntax {
        //Logger.info("DFKlass.buildMembersFromTree:", this, finder);
        if (ast instanceof AbstractTypeDeclaration) {
            this.buildMembersFromAbstTypeDecl(finder, (AbstractTypeDeclaration)ast);

        } else if (ast instanceof AnonymousClassDeclaration) {
            this.buildMembersFromAnonDecl(finder, (AnonymousClassDeclaration)ast);
        }
    }

    private void buildMembersFromAbstTypeDecl(
        DFTypeFinder finder, AbstractTypeDeclaration abstTypeDecl)
        throws InvalidSyntax {
        if (abstTypeDecl instanceof TypeDeclaration) {
            this.buildMembersFromTypeDecl(finder, (TypeDeclaration)abstTypeDecl);

        } else if (abstTypeDecl instanceof EnumDeclaration) {
            this.buildMembersFromEnumDecl(finder, (EnumDeclaration)abstTypeDecl);

        } else if (abstTypeDecl instanceof AnnotationTypeDeclaration) {
            this.buildMembersFromAnnotTypeDecl(finder, (AnnotationTypeDeclaration)abstTypeDecl);
        }
    }

    @SuppressWarnings("unchecked")
    private void buildMembersFromTypeDecl(
        DFTypeFinder finder, TypeDeclaration typeDecl)
        throws InvalidSyntax {
        // Load base klasses/interfaces.
	// Get superclass.
	_baseKlass = DFBuiltinTypes.getObjectKlass();
	Type superClass = typeDecl.getSuperclassType();
	if (superClass != null) {
	    try {
		_baseKlass = finder.resolve(superClass).toKlass();
	    } catch (TypeNotFound e) {
		Logger.error("TypeNotFound", e.name);
	    }
	}
	_baseKlass.load();
	// Get interfaces.
	List<Type> ifaces = typeDecl.superInterfaceTypes();
	_baseIfaces = new DFKlass[ifaces.size()];
	for (int i = 0; i < ifaces.size(); i++) {
	    DFKlass iface = DFBuiltinTypes.getObjectKlass();
	    try {
		iface = finder.resolve(ifaces.get(i)).toKlass();
	    } catch (TypeNotFound e) {
		Logger.error("TypeNotFound", e.name);
	    }
	    _baseIfaces[i] = iface;
	}
	for (DFKlass iface : _baseIfaces) {
	    iface.load();
	}
	this.buildMembers(finder, typeDecl.bodyDeclarations());
    }

    @SuppressWarnings("unchecked")
    private void buildMembersFromEnumDecl(
        DFTypeFinder finder, EnumDeclaration enumDecl)
        throws InvalidSyntax {
        // Load base klasses/interfaces.
	// Get superclass.
	DFKlass enumKlass = DFBuiltinTypes.getEnumKlass();
	_baseKlass = enumKlass.parameterize(new DFType[] { this });
	_baseKlass.load();
	// Get interfaces.
	List<Type> ifaces = enumDecl.superInterfaceTypes();
	_baseIfaces = new DFKlass[ifaces.size()];
	for (int i = 0; i < ifaces.size(); i++) {
	    DFKlass iface = DFBuiltinTypes.getObjectKlass();
	    try {
		iface = finder.resolve(ifaces.get(i)).toKlass();
	    } catch (TypeNotFound e) {
		Logger.error("TypeNotFound", e.name);
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
	    this, "values", DFCallStyle.InstanceMethod, "values", null);
	method.setFinder(finder);
	method.setFuncType(
	    new DFFunctionType(new DFType[] {}, new DFArrayType(this, 1)));
	this.addMethod(method, null);
	this.buildMembers(finder, enumDecl.bodyDeclarations());
    }

    @SuppressWarnings("unchecked")
    private void buildMembersFromAnnotTypeDecl(
        DFTypeFinder finder, AnnotationTypeDeclaration annotTypeDecl)
        throws InvalidSyntax {
	// Get superclass.
	_baseKlass = DFBuiltinTypes.getObjectKlass();
	_baseKlass.load();
	this.buildMembers(finder, annotTypeDecl.bodyDeclarations());
    }

    @SuppressWarnings("unchecked")
    private void buildMembersFromAnonDecl(
        DFTypeFinder finder, AnonymousClassDeclaration anonDecl)
        throws InvalidSyntax {
	// Get superclass.
	_baseKlass = DFBuiltinTypes.getObjectKlass();
	_baseKlass.load();
	this.buildMembers(finder, anonDecl.bodyDeclarations());
    }

    @SuppressWarnings("unchecked")
    private void buildMembers(DFTypeFinder finder, List<BodyDeclaration> decls)
        throws InvalidSyntax {
        _initMethod.setFinder(finder);

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
                        ft = new DFArrayType(ft, ndims);
                    }
                    this.addField(frag.getName(), isStatic(decl), ft);
                }

            } else if (body instanceof MethodDeclaration) {
                MethodDeclaration decl = (MethodDeclaration)body;
                String id = Utils.encodeASTNode(decl);
                DFMethod method = this.getMethod(id);
                method.setFinder(finder);
                List<TypeParameter> tps = decl.typeParameters();
                if (0 < tps.size()) {
                    DFMapType[] mapTypes = new DFMapType[tps.size()];
                    for (int i = 0; i < tps.size(); i++) {
                        TypeParameter tp = tps.get(i);
                        String id2 = tp.getName().getIdentifier();
                        mapTypes[i] = new DFMapType(id2);
                        mapTypes[i].setTypeBounds(tp.typeBounds());
                    }
                    method.setMapTypes(mapTypes);
                }
                DFTypeFinder finder2 = method.getFinder();
		List<SingleVariableDeclaration> varDecls = decl.parameters();
                DFType[] argTypes = new DFType[varDecls.size()];
		for (int i = 0; i < varDecls.size(); i++) {
		    SingleVariableDeclaration varDecl = varDecls.get(i);
		    argTypes[i] = finder2.resolveSafe(varDecl.getType());
		}
                DFType returnType;
                if (decl.isConstructor()) {
                    returnType = this;
                } else {
		    returnType = finder2.resolveSafe(decl.getReturnType2());
                }
                DFFunctionType funcType = new DFFunctionType(argTypes, returnType);
                List<Type> excs = decl.thrownExceptionTypes();
                if (0 < excs.size()) {
                    DFType[] exceptions = new DFType[excs.size()];
                    for (int i = 0; i < excs.size(); i++) {
			exceptions[i] = finder.resolveSafe(excs.get(i));
                    }
                    funcType.setExceptions(exceptions);
                }
                method.setFuncType(funcType);
                method.setTree(decl);

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

    protected void dumpContents(PrintStream out, String indent) {
        super.dumpContents(out, indent);
        if (_mapTypeMap != null) {
            for (Map.Entry<String,DFType> e : _mapTypeMap.entrySet()) {
                out.println(indent+"map: "+e.getKey()+" "+e.getValue());
            }
        }
        if (_paramTypeMap != null) {
            for (Map.Entry<String,DFType> e : _paramTypeMap.entrySet()) {
                out.println(indent+"param: "+e.getKey()+" "+e.getValue());
            }
        }
        if (_genericKlass != null) {
            _genericKlass.dump(out, indent);
        }
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
        protected void dumpContents(PrintStream out, String indent) {
            super.dumpContents(out, indent);
            for (DFMethod method : DFKlass.this.getMethods()) {
                out.println(indent+"defined: "+method);
            }
        }
    }
}
