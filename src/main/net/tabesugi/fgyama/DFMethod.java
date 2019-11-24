//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import javax.xml.stream.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


// InputNode: represnets a function argument.
class InputNode extends DFNode {

    public InputNode(
        DFGraph graph, DFVarScope scope, DFRef ref,
        ASTNode ast) {
        super(graph, scope, ref.getRefType(), ref, ast);
    }

    @Override
    public String getKind() {
        return "input";
    }
}

// OutputNode: represents a return value.
class OutputNode extends DFNode {

    public OutputNode(
        DFGraph graph, DFVarScope scope, DFRef ref,
        ASTNode ast) {
        super(graph, scope, ref.getRefType(), ref, ast);
    }

    @Override
    public String getKind() {
        return "output";
    }
}

// AssignNode:
class AssignNode extends DFNode {

    public AssignNode(
        DFGraph graph, DFVarScope scope, DFRef ref,
        ASTNode ast) {
        super(graph, scope, ref.getRefType(), ref, ast);
    }

    @Override
    public String getKind() {
        return "assign_var";
    }
}


//  DFMethod
//
public class DFMethod extends DFTypeSpace implements Comparable<DFMethod> {

    public enum CallStyle {
        Constructor,
        InstanceMethod,
        StaticMethod,
        Lambda,
        InstanceOrStatic,           // for search only.
        Initializer;

        @Override
        public String toString() {
            switch (this) {
            case InstanceMethod:
                return "instance";
            case StaticMethod:
                return "static";
            case Initializer:
                return "initializer";
            case Constructor:
                return "constructor";
            case Lambda:
                return "lambda";
            default:
                return null;
            }
        }
    }

    private DFKlass _klass;
    private String _name;
    private CallStyle _callStyle;
    private MethodScope _scope;
    private boolean _abstract;

    private DFTypeFinder _finder = null;
    private DFMapType[] _mapTypes = null;
    private Map<String, DFType> _mapTypeMap = null;
    private DFFunctionType _funcType = null;

    private ConsistentHashSet<DFRef> _inputRefs = new ConsistentHashSet<DFRef>();
    private ConsistentHashSet<DFRef> _outputRefs = new ConsistentHashSet<DFRef>();
    private ConsistentHashSet<DFMethod> _callers =
        new ConsistentHashSet<DFMethod>();

    private ASTNode _ast = null;

    // List of subclass' methods overriding this method.
    private List<DFMethod> _overriders = new ArrayList<DFMethod>();
    // List of superclass' methods being overriden by this method.
    private List<DFMethod> _overriding = new ArrayList<DFMethod>();

    private static boolean _defaultTransparent = false;

    public static void setDefaultTransparent(boolean transparent) {
        _defaultTransparent = transparent;
    }

    public DFMethod(
        DFKlass klass, String id, CallStyle callStyle,
        String name, DFVarScope outer, boolean isAbstract) {
        super(id, klass);
        _klass = klass;
        _name = name;
        _callStyle = callStyle;
        _scope = new MethodScope(outer, id);
        _abstract = isAbstract;
    }

    @Override
    public String toString() {
        return ("<DFMethod("+this.getSignature()+")>");
    }

    @Override
    public int compareTo(DFMethod method) {
        if (this == method) return 0;
        return _name.compareTo(method._name);
    }

    public boolean equals(DFMethod method) {
        if (!_name.equals(method._name)) return false;
        return _funcType.equals(method._funcType);
    }

    public String getName() {
        return _name;
    }

    public String getSignature() {
        String name;
        if (_klass != null) {
            name = _klass.getTypeName()+"."+_name;
        } else {
            name = "!"+_name;
        }
        if (_funcType != null) {
            name += _funcType.getTypeName();
        }
        return name;
    }

    public CallStyle getCallStyle() {
        return _callStyle;
    }

    public boolean isAbstract() {
        return _abstract;
    }

    public void setFuncType(DFFunctionType funcType) {
        _funcType = funcType;
    }

    public DFFunctionType getFuncType() {
        return _funcType;
    }

    @Override
    public DFType getType(String id) {
        if (_mapTypeMap != null) {
            DFType type = _mapTypeMap.get(id);
            if (type != null) return type;
        }
        return super.getType(id);
    }

    public int canAccept(DFType[] argTypes) {
        Map<DFMapType, DFType> typeMap = new HashMap<DFMapType, DFType>();
        return _funcType.canAccept(argTypes, typeMap);
    }

    public void setFinder(DFTypeFinder finder) {
        _finder = new DFTypeFinder(this, finder);
    }

    public void setMapTypes(DFMapType[] mapTypes)
	throws InvalidSyntax {
	assert _finder != null;
        _mapTypes = mapTypes;
        _mapTypeMap = new HashMap<String, DFType>();
        for (DFMapType mapType : _mapTypes) {
	    // Set the Object type temporarily for circular definition.
            _mapTypeMap.put(mapType.getTypeName(), DFBuiltinTypes.getObjectKlass());
	    mapType.build(_finder);
            _mapTypeMap.put(mapType.getTypeName(), mapType.toKlass());
        }
    }

    public boolean addOverrider(DFMethod method) {
        if (method._callStyle != CallStyle.Lambda &&
            !_name.equals(method._name)) return false;
        if (!_funcType.equals(method._funcType)) return false;
	//Logger.info("DFMethod.addOverrider:", this, "<-", method);
        _overriders.add(method);
        method._overriding.add(this);
        return true;
    }

    private void listOverriders(List<Overrider> overriders, int prio) {
        overriders.add(new Overrider(this, prio));
        for (DFMethod method : _overriders) {
            method.listOverriders(overriders, prio+1);
        }
    }

    private List<DFMethod> _allOverriders = null;
    public List<DFMethod> getOverriders() {
	// Cache for future reference.
	if (_allOverriders == null) {
	    List<Overrider> overriders = new ArrayList<Overrider>();
	    this.listOverriders(overriders, 0);
	    Collections.sort(overriders);
	    _allOverriders = new ArrayList<DFMethod>();
	    for (Overrider overrider : overriders) {
		_allOverriders.add(overrider.method);
	    }
	}
        return _allOverriders;
    }

    public List<DFMethod> getOverridings() {
	return _overriding;
    }

    public void addCaller(DFMethod method) {
        _callers.add(method);
    }

    public ConsistentHashSet<DFMethod> getCallers() {
	return _callers;
    }

    public DFTypeFinder getFinder() {
        return _finder;
    }

    public void setTree(ASTNode ast) {
	_ast = ast;
    }

    public ASTNode getTree() {
	return _ast;
    }

    public DFLocalScope getScope() {
        return _scope;
    }

    @SuppressWarnings("unchecked")
    public void buildScope()
        throws InvalidSyntax {
	if (_ast == null) return;
	assert _scope != null;
	if (_ast instanceof MethodDeclaration) {
	    _scope.buildMethodDecl(
                _finder, (MethodDeclaration)_ast);
	} else if (_ast instanceof AbstractTypeDeclaration) {
	    _scope.buildBodyDecls(
                _finder, ((AbstractTypeDeclaration)_ast).bodyDeclarations());
        } else if (_ast instanceof ClassInstanceCreation) {
	    ClassInstanceCreation cstr = (ClassInstanceCreation)_ast;
            _scope.buildBodyDecls(
		_finder, cstr.getAnonymousClassDeclaration().bodyDeclarations());
        } else if (_ast instanceof LambdaExpression) {
            _scope.buildLambda(_finder, (LambdaExpression)_ast);
	}  else {
	    throw new InvalidSyntax(_ast);
	}
        //_scope.dump();
    }

    public boolean isTransparent() {
        return _defaultTransparent;
    }

    public Collection<DFRef> getInputRefs() {
        assert this.isTransparent();
        return _inputRefs;
    }

    public Collection<DFRef> getOutputRefs() {
        assert this.isTransparent();
        return _outputRefs;
    }

    @SuppressWarnings("unchecked")
    public void enumRefs(List<DFKlass> defined)
        throws InvalidSyntax {
	if (_ast == null) return;
	assert _scope != null;
	if (_ast instanceof MethodDeclaration) {
	    this.enumRefsMethodDecl(
                defined, _scope, (MethodDeclaration)_ast);
	} else if (_ast instanceof AbstractTypeDeclaration) {
	    this.enumRefsBodyDecls(
                defined, _scope,
                ((AbstractTypeDeclaration)_ast).bodyDeclarations());
        } else if (_ast instanceof ClassInstanceCreation) {
	    ClassInstanceCreation cstr = (ClassInstanceCreation)_ast;
	    this.enumRefsBodyDecls(
		defined, _scope,
                cstr.getAnonymousClassDeclaration().bodyDeclarations());
        } else if (_ast instanceof LambdaExpression) {
            this.enumRefsLambda(
                defined, _scope, (LambdaExpression)_ast);
	}  else {
	    throw new InvalidSyntax(_ast);
	}
    }

    @SuppressWarnings("unchecked")
    private void enumRefsMethodDecl(
	List<DFKlass> defined,
        DFLocalScope scope, MethodDeclaration methodDecl)
        throws InvalidSyntax {
        if (methodDecl.getBody() == null) return;
        // Constructor changes all the member fields.
        if (_callStyle == CallStyle.Constructor) {
            for (DFKlass.FieldRef ref : _klass.getFields()) {
                if (!ref.isStatic()) {
                    _outputRefs.add(ref);
                }
            }
        }
        this.enumRefsStmt(defined, scope, methodDecl.getBody());
    }

    @SuppressWarnings("unchecked")
    private void enumRefsLambda(
	List<DFKlass> defined,
        DFLocalScope scope, LambdaExpression lambda)
        throws InvalidSyntax {
        ASTNode body = lambda.getBody();
        if (body instanceof Statement) {
            this.enumRefsStmt(defined, scope, (Statement)body);
        } else if (body instanceof Expression) {
            this.enumRefsExpr(defined, scope, (Expression)body);
        } else {
            throw new InvalidSyntax(body);
        }
    }

    @SuppressWarnings("unchecked")
    private void enumRefsBodyDecls(
	List<DFKlass> defined,
        DFLocalScope scope, List<BodyDeclaration> decls)
        throws InvalidSyntax {
        for (BodyDeclaration body : decls) {
	    if (body instanceof FieldDeclaration) {
                FieldDeclaration fieldDecl = (FieldDeclaration)body;
                for (VariableDeclarationFragment frag :
                         (List<VariableDeclarationFragment>) fieldDecl.fragments()) {
		    try {
			DFRef ref = scope.lookupVar(frag.getName());
			Expression init = frag.getInitializer();
			if (init != null) {
			    this.enumRefsExpr(defined, scope, init);
			    this.fixateLambda(defined, ref.getRefType(), init);
			}
		    } catch (VariableNotFound e) {
		    }
		}
            } else if (body instanceof Initializer) {
                Initializer initializer = (Initializer)body;
                this.enumRefsStmt(defined, scope, initializer.getBody());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void enumRefsStmt(
	List<DFKlass> defined,
        DFLocalScope scope, Statement stmt)
        throws InvalidSyntax {
        assert stmt != null;

        if (stmt instanceof AssertStatement) {
	    // "assert x;"

        } else if (stmt instanceof Block) {
	    // "{ ... }"
            Block block = (Block)stmt;
            DFLocalScope innerScope = scope.getChildByAST(stmt);
            for (Statement cstmt :
                     (List<Statement>) block.statements()) {
                this.enumRefsStmt(defined, innerScope, cstmt);
            }

        } else if (stmt instanceof EmptyStatement) {

        } else if (stmt instanceof VariableDeclarationStatement) {
	    // "int a = 2;"
            VariableDeclarationStatement varStmt =
                (VariableDeclarationStatement)stmt;
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>) varStmt.fragments()) {
                //_outputRefs.add(scope.lookupVar(frag.getName()));
                try {
                    DFRef ref = scope.lookupVar(frag.getName());
                    Expression init = frag.getInitializer();
                    if (init != null) {
                        this.enumRefsExpr(defined, scope, init);
                        this.fixateLambda(defined, ref.getRefType(), init);
                    }
                } catch (VariableNotFound e) {
                }
            }

        } else if (stmt instanceof ExpressionStatement) {
	    // "foo();"
            ExpressionStatement exprStmt = (ExpressionStatement)stmt;
            this.enumRefsExpr(defined, scope, exprStmt.getExpression());

        } else if (stmt instanceof IfStatement) {
	    // "if (c) { ... } else { ... }"
            IfStatement ifStmt = (IfStatement)stmt;
            this.enumRefsExpr(defined, scope, ifStmt.getExpression());
            Statement thenStmt = ifStmt.getThenStatement();
            this.enumRefsStmt(defined, scope, thenStmt);
            Statement elseStmt = ifStmt.getElseStatement();
            if (elseStmt != null) {
                this.enumRefsStmt(defined, scope, elseStmt);
            }

        } else if (stmt instanceof SwitchStatement) {
	    // "switch (x) { case 0: ...; }"
            SwitchStatement switchStmt = (SwitchStatement)stmt;
            DFType type = this.enumRefsExpr(
                defined, scope, switchStmt.getExpression());
            if (type == null) {
                type = DFUnknownType.UNKNOWN;
            }
            DFKlass enumKlass = null;
            if (type instanceof DFKlass &&
                ((DFKlass)type).isEnum()) {
                enumKlass = type.toKlass();
                enumKlass.load();
            }
            DFLocalScope innerScope = scope.getChildByAST(stmt);
            for (Statement cstmt : (List<Statement>) switchStmt.statements()) {
                if (cstmt instanceof SwitchCase) {
                    SwitchCase switchCase = (SwitchCase)cstmt;
                    Expression expr = switchCase.getExpression();
                    if (expr != null) {
                        if (enumKlass != null && expr instanceof SimpleName) {
                            // special treatment for enum.
                            try {
                                DFRef ref = enumKlass.lookupField((SimpleName)expr);
                                _inputRefs.add(ref);
                            } catch (VariableNotFound e) {
                            }
                        } else {
                            this.enumRefsExpr(defined, innerScope, expr);
                        }
                    }
                } else {
                    this.enumRefsStmt(defined, innerScope, cstmt);
                }
            }

        } else if (stmt instanceof SwitchCase) {
            // Invalid "case" placement.
            throw new InvalidSyntax(stmt);

        } else if (stmt instanceof WhileStatement) {
	    // "while (c) { ... }"
            WhileStatement whileStmt = (WhileStatement)stmt;
            DFLocalScope innerScope = scope.getChildByAST(stmt);
            this.enumRefsExpr(defined, scope, whileStmt.getExpression());
            this.enumRefsStmt(defined, innerScope, whileStmt.getBody());

        } else if (stmt instanceof DoStatement) {
	    // "do { ... } while (c);"
            DoStatement doStmt = (DoStatement)stmt;
            DFLocalScope innerScope = scope.getChildByAST(stmt);
            this.enumRefsStmt(defined, innerScope, doStmt.getBody());
            this.enumRefsExpr(defined, scope, doStmt.getExpression());

        } else if (stmt instanceof ForStatement) {
	    // "for (i = 0; i < 10; i++) { ... }"
            ForStatement forStmt = (ForStatement)stmt;
            DFLocalScope innerScope = scope.getChildByAST(stmt);
            for (Expression init : (List<Expression>) forStmt.initializers()) {
                this.enumRefsExpr(defined, innerScope, init);
            }
            Expression expr = forStmt.getExpression();
            if (expr != null) {
                this.enumRefsExpr(defined, innerScope, expr);
            }
            this.enumRefsStmt(defined, innerScope, forStmt.getBody());
            for (Expression update : (List<Expression>) forStmt.updaters()) {
                this.enumRefsExpr(defined, innerScope, update);
            }

        } else if (stmt instanceof EnhancedForStatement) {
	    // "for (x : array) { ... }"
            EnhancedForStatement eForStmt = (EnhancedForStatement)stmt;
            this.enumRefsExpr(defined, scope, eForStmt.getExpression());
            DFLocalScope innerScope = scope.getChildByAST(stmt);
            this.enumRefsStmt(defined, innerScope, eForStmt.getBody());

        } else if (stmt instanceof ReturnStatement) {
	    // "return 42;"
            ReturnStatement rtrnStmt = (ReturnStatement)stmt;
            Expression expr = rtrnStmt.getExpression();
            if (expr != null) {
                this.enumRefsExpr(defined, scope, expr);
            }
            // Return is handled as an Exit, not an output.

        } else if (stmt instanceof BreakStatement) {
	    // "break;"

        } else if (stmt instanceof ContinueStatement) {
	    // "continue;"

        } else if (stmt instanceof LabeledStatement) {
	    // "here:"
            LabeledStatement labeledStmt = (LabeledStatement)stmt;
            this.enumRefsStmt(defined, scope, labeledStmt.getBody());

        } else if (stmt instanceof SynchronizedStatement) {
	    // "synchronized (this) { ... }"
            SynchronizedStatement syncStmt = (SynchronizedStatement)stmt;
            this.enumRefsExpr(defined, scope, syncStmt.getExpression());
            this.enumRefsStmt(defined, scope, syncStmt.getBody());

        } else if (stmt instanceof TryStatement) {
	    // "try { ... } catch (e) { ... }"
            TryStatement tryStmt = (TryStatement)stmt;
            for (CatchClause cc : (List<CatchClause>) tryStmt.catchClauses()) {
                DFLocalScope catchScope = scope.getChildByAST(cc);
                this.enumRefsStmt(defined, catchScope, cc.getBody());
            }
            DFLocalScope tryScope = scope.getChildByAST(tryStmt);
            this.enumRefsStmt(defined, tryScope, tryStmt.getBody());
            Block finBlock = tryStmt.getFinally();
            if (finBlock != null) {
                this.enumRefsStmt(defined, scope, finBlock);
            }

        } else if (stmt instanceof ThrowStatement) {
	    // "throw e;"
            ThrowStatement throwStmt = (ThrowStatement)stmt;
            DFType type = this.enumRefsExpr(
                defined, scope, throwStmt.getExpression());
            // Because an exception can be catched, throw does not
            // necessarily mean this method actually throws as a whole.
            // This should be taken cared of by the "throws" clause.
            //DFRef ref = _scope.lookupException(type.toKlass());
            //_outputRefs.add(ref);

        } else if (stmt instanceof ConstructorInvocation) {
	    // "this(args)"
            ConstructorInvocation ci = (ConstructorInvocation)stmt;
            DFRef ref = scope.lookupThis();
            //_inputRefs.add(ref);
	    DFKlass klass = ref.getRefType().toKlass();
            klass.load();
	    int nargs = ci.arguments().size();
	    DFType[] argTypes = new DFType[nargs];
            for (int i = 0; i < nargs; i++) {
		Expression arg = (Expression)ci.arguments().get(i);
                DFType type = this.enumRefsExpr(defined, scope, arg);
                if (type == null) return;
                argTypes[i] = type;
            }
            try {
		DFMethod method1 = klass.lookupMethod(
		    CallStyle.Constructor, (String)null, argTypes);
		this.fixateLambda(
                    defined, method1.getFuncType(), ci.arguments());
            } catch (MethodNotFound e) {
	    }

        } else if (stmt instanceof SuperConstructorInvocation) {
	    // "super(args)"
            SuperConstructorInvocation sci = (SuperConstructorInvocation)stmt;
            DFRef ref = scope.lookupThis();
            //_inputRefs.add(ref);
	    DFKlass klass = ref.getRefType().toKlass();
            DFKlass baseKlass = klass.getBaseKlass();
            baseKlass.load();
	    int nargs = sci.arguments().size();
	    DFType[] argTypes = new DFType[nargs];
            for (int i = 0; i < nargs; i++) {
		Expression arg = (Expression)sci.arguments().get(i);
                DFType type = this.enumRefsExpr(defined, scope, arg);
                if (type == null) return;
                argTypes[i] = type;
            }
            try {
		DFMethod method1 = baseKlass.lookupMethod(
		    CallStyle.Constructor, (String)null, argTypes);
                method1.addCaller(this);
		this.fixateLambda(
                    defined, method1.getFuncType(), sci.arguments());
            } catch (MethodNotFound e) {
	    }

        } else if (stmt instanceof TypeDeclarationStatement) {
	    // "class K { ... }"
            // Inline classes are processed separately.

        } else {
            throw new InvalidSyntax(stmt);

        }
    }

    @SuppressWarnings("unchecked")
    private DFType enumRefsExpr(
	List<DFKlass> defined,
        DFLocalScope scope, Expression expr)
        throws InvalidSyntax {
        assert expr != null;

        if (expr instanceof Annotation) {
            // "@Annotation"
            return null;

        } else if (expr instanceof Name) {
            // "a.b"
            Name name = (Name)expr;
            DFRef ref;
            try {
                if (name.isSimpleName()) {
                    ref = scope.lookupVar((SimpleName)name);
                } else {
                    QualifiedName qname = (QualifiedName)name;
                    // Try assuming it's a variable access.
                    DFType type = this.enumRefsExpr(
                        defined, scope, qname.getQualifier());
                    if (type == null) {
                        // Turned out it's a class variable.
                        try {
			    type = _finder.lookupType(qname.getQualifier());
                        } catch (TypeNotFound e) {
                            return null;
                        }
                    }
                    DFKlass klass = type.toKlass();
                    klass.load();
                    SimpleName fieldName = qname.getName();
                    ref = klass.lookupField(fieldName);
                }
            } catch (VariableNotFound e) {
                return null;
            }
            if (!ref.isLocal()) {
                _inputRefs.add(ref);
            }
            return ref.getRefType();

        } else if (expr instanceof ThisExpression) {
            // "this"
            ThisExpression thisExpr = (ThisExpression)expr;
            Name name = thisExpr.getQualifier();
            DFRef ref;
            if (name != null) {
                try {
                    DFType type = _finder.lookupType(name);
                    ref = type.toKlass().getKlassScope().lookupThis();
                } catch (TypeNotFound e) {
                    return null;
                }
            } else {
                ref = scope.lookupThis();
            }
            //_inputRefs.add(ref);
            return ref.getRefType();

        } else if (expr instanceof BooleanLiteral) {
            // "true", "false"
            return DFBasicType.BOOLEAN;

        } else if (expr instanceof CharacterLiteral) {
            // "'c'"
            return DFBasicType.CHAR;

        } else if (expr instanceof NullLiteral) {
            // "null"
            return DFNullType.NULL;

        } else if (expr instanceof NumberLiteral) {
            // "42"
            return DFBasicType.INT;

        } else if (expr instanceof StringLiteral) {
            // ""abc""
            return DFBuiltinTypes.getStringKlass();

        } else if (expr instanceof TypeLiteral) {
            // "A.class"
	    Type value = ((TypeLiteral)expr).getType();
            try {
                DFType typeval = _finder.resolve(value);
                DFKlass klass = DFBuiltinTypes.getClassKlass().parameterize(
		    new DFType[] { typeval });
		klass.load();
		return klass;
            } catch (TypeNotFound e) {
		return null;
            }

        } else if (expr instanceof PrefixExpression) {
            // "++x"
            PrefixExpression prefix = (PrefixExpression)expr;
            PrefixExpression.Operator op = prefix.getOperator();
            Expression operand = prefix.getOperand();
            if (op == PrefixExpression.Operator.INCREMENT ||
                op == PrefixExpression.Operator.DECREMENT) {
                this.enumRefsAssignment(defined, scope, operand);
            }
            return DFNode.inferPrefixType(
                this.enumRefsExpr(defined, scope, operand), op);

        } else if (expr instanceof PostfixExpression) {
            // "y--"
            PostfixExpression postfix = (PostfixExpression)expr;
            PostfixExpression.Operator op = postfix.getOperator();
            Expression operand = postfix.getOperand();
            if (op == PostfixExpression.Operator.INCREMENT ||
                op == PostfixExpression.Operator.DECREMENT) {
                this.enumRefsAssignment(defined, scope, operand);
            }
            return this.enumRefsExpr(defined, scope, operand);

        } else if (expr instanceof InfixExpression) {
            // "a+b"
            InfixExpression infix = (InfixExpression)expr;
            InfixExpression.Operator op = infix.getOperator();
            DFType left = this.enumRefsExpr(
                defined, scope, infix.getLeftOperand());
            DFType right = this.enumRefsExpr(
                defined, scope, infix.getRightOperand());
            if (left == null || right == null) return null;
            return DFNode.inferInfixType(left, op, right);

        } else if (expr instanceof ParenthesizedExpression) {
            // "(expr)"
            ParenthesizedExpression paren = (ParenthesizedExpression)expr;
            return this.enumRefsExpr(defined, scope, paren.getExpression());

        } else if (expr instanceof Assignment) {
            // "p = q"
            Assignment assn = (Assignment)expr;
            Assignment.Operator op = assn.getOperator();
            if (op != Assignment.Operator.ASSIGN) {
                this.enumRefsExpr(defined, scope, assn.getLeftHandSide());
            }
            DFRef ref = this.enumRefsAssignment(
                defined, scope, assn.getLeftHandSide());
            if (ref != null) {
                this.fixateLambda(
                    defined, ref.getRefType(), assn.getRightHandSide());
            }
            return this.enumRefsExpr(defined, scope, assn.getRightHandSide());

        } else if (expr instanceof VariableDeclarationExpression) {
            // "int a=2"
            VariableDeclarationExpression decl =
                (VariableDeclarationExpression)expr;
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>) decl.fragments()) {
                try {
                    DFRef ref = scope.lookupVar(frag.getName());
                    //_outputRefs.add(ref);
                    Expression init = frag.getInitializer();
                    if (init != null) {
                        this.enumRefsExpr(defined, scope, init);
                        this.fixateLambda(defined, ref.getRefType(), init);
                    }
                } catch (VariableNotFound e) {
                }
            }
            return null; // XXX what type?

        } else if (expr instanceof MethodInvocation) {
            MethodInvocation invoke = (MethodInvocation)expr;
            Expression expr1 = invoke.getExpression();
            CallStyle callStyle;
            DFKlass klass = null;
            if (expr1 == null) {
                // "method()"
                DFRef ref = scope.lookupThis();
                //_inputRefs.add(ref);
                klass = ref.getRefType().toKlass();
                callStyle = CallStyle.InstanceOrStatic;
            } else {
                callStyle = CallStyle.InstanceMethod;
                if (expr1 instanceof Name) {
                    // "ClassName.method()"
                    try {
                        klass = _finder.lookupType((Name)expr1).toKlass();
                        callStyle = CallStyle.StaticMethod;
                    } catch (TypeNotFound e) {
                    }
                }
                if (klass == null) {
                    // "expr.method()"
                    DFType type = this.enumRefsExpr(defined, scope, expr1);
                    if (type == null) return null;
                    klass = type.toKlass();
                }
            }
            klass.load();
	    int nargs = invoke.arguments().size();
	    DFType[] argTypes = new DFType[nargs];
            for (int i = 0; i < nargs; i++) {
		Expression arg = (Expression)invoke.arguments().get(i);
                DFType type = this.enumRefsExpr(defined, scope, arg);
                if (type == null) return null;
                argTypes[i] = type;
            }
            try {
                DFMethod method1 = klass.lookupMethod(
                    callStyle, invoke.getName(), argTypes);
		for (DFMethod m : method1.getOverriders()) {
		    m.addCaller(this);
		}
                this.fixateLambda(
                    defined, method1.getFuncType(), invoke.arguments());
                return method1.getFuncType().getReturnType();
            } catch (MethodNotFound e) {
                return DFUnknownType.UNKNOWN;
            }

        } else if (expr instanceof SuperMethodInvocation) {
            // "super.method()"
            SuperMethodInvocation sinvoke = (SuperMethodInvocation)expr;
	    int nargs = sinvoke.arguments().size();
	    DFType[] argTypes = new DFType[nargs];
            for (int i = 0; i < nargs; i++) {
		Expression arg = (Expression)sinvoke.arguments().get(i);
                DFType type = this.enumRefsExpr(defined, scope, arg);
                if (type == null) return null;
                argTypes[i] = type;
            }
            DFRef ref = scope.lookupThis();
            //_inputRefs.add(ref);
            DFKlass klass = ref.getRefType().toKlass();
            klass.load();
            DFKlass baseKlass = klass.getBaseKlass();
            baseKlass.load();
            try {
                DFMethod method1 = baseKlass.lookupMethod(
                    CallStyle.InstanceMethod, sinvoke.getName(), argTypes);
                method1.addCaller(this);
                this.fixateLambda(
                    defined, method1.getFuncType(), sinvoke.arguments());
                return method1.getFuncType().getReturnType();
            } catch (MethodNotFound e) {
                return DFUnknownType.UNKNOWN;
            }

        } else if (expr instanceof ArrayCreation) {
            // "new int[10]"
            ArrayCreation ac = (ArrayCreation)expr;
            for (Expression dim : (List<Expression>) ac.dimensions()) {
                this.enumRefsExpr(defined, scope, dim);
            }
            ArrayInitializer init = ac.getInitializer();
            if (init != null) {
                this.enumRefsExpr(defined, scope, init);
            }
            try {
                DFType type = _finder.resolve(ac.getType().getElementType());
                type.toKlass().load();
                return type;
            } catch (TypeNotFound e) {
                return null;
            }

        } else if (expr instanceof ArrayInitializer) {
            // "{ 5,9,4,0 }"
            ArrayInitializer init = (ArrayInitializer)expr;
            DFType type = null;
            for (Expression expr1 : (List<Expression>) init.expressions()) {
                type = this.enumRefsExpr(defined, scope, expr1);
            }
            return type;

        } else if (expr instanceof ArrayAccess) {
            // "a[0]"
            ArrayAccess aa = (ArrayAccess)expr;
            this.enumRefsExpr(defined, scope, aa.getIndex());
            DFType type = this.enumRefsExpr(defined, scope, aa.getArray());
            if (type instanceof DFArrayType) {
                DFRef ref = scope.lookupArray(type);
                _inputRefs.add(ref);
                type = ((DFArrayType)type).getElemType();
            }
            return type;

        } else if (expr instanceof FieldAccess) {
            // "(expr).foo"
            FieldAccess fa = (FieldAccess)expr;
            Expression expr1 = fa.getExpression();
            DFType type = null;
            if (expr1 instanceof Name) {
                try {
                    type = _finder.lookupType((Name)expr1);
                } catch (TypeNotFound e) {
                }
            }
            if (type == null) {
                type = this.enumRefsExpr(defined, scope, expr1);
                if (type == null) return null;
            }
            DFKlass klass = type.toKlass();
            klass.load();
            SimpleName fieldName = fa.getName();
            try {
                DFRef ref = klass.lookupField(fieldName);
                _inputRefs.add(ref);
                return ref.getRefType();
            } catch (VariableNotFound e) {
                return null;
            }

        } else if (expr instanceof SuperFieldAccess) {
            // "super.baa"
            SuperFieldAccess sfa = (SuperFieldAccess)expr;
            SimpleName fieldName = sfa.getName();
            DFRef ref = scope.lookupThis();
            //_inputRefs.add(ref);
            DFKlass klass = ref.getRefType().toKlass().getBaseKlass();
            klass.load();
            try {
                DFRef ref2 = klass.lookupField(fieldName);
                _inputRefs.add(ref2);
                return ref2.getRefType();
            } catch (VariableNotFound e) {
                return null;
            }

        } else if (expr instanceof CastExpression) {
            // "(String)"
            CastExpression cast = (CastExpression)expr;
            this.enumRefsExpr(defined, scope, cast.getExpression());
            try {
                DFType type = _finder.resolve(cast.getType());
                type.toKlass().load();
                return type;
            } catch (TypeNotFound e) {
                return null;
            }

        } else if (expr instanceof ClassInstanceCreation) {
            // "new T()"
            ClassInstanceCreation cstr = (ClassInstanceCreation)expr;
            DFType instType;
            if (cstr.getAnonymousClassDeclaration() != null) {
                String id = Utils.encodeASTNode(cstr);
                instType = this.getType(id);
                if (instType == null) {
		    return null;
		}
            } else {
                try {
                    instType = _finder.resolve(cstr.getType());
                } catch (TypeNotFound e) {
                    return null;
                }
            }
            DFKlass instKlass = instType.toKlass();
            instKlass.load();
            Expression expr1 = cstr.getExpression();
            if (expr1 != null) {
                this.enumRefsExpr(defined, scope, expr1);
            }
	    int nargs = cstr.arguments().size();
	    DFType[] argTypes = new DFType[nargs];
            for (int i = 0; i < nargs; i++) {
		Expression arg = (Expression)cstr.arguments().get(i);
                DFType type = this.enumRefsExpr(defined, scope, arg);
                if (type == null) return null;
                argTypes[i] = type;
            }
            try {
		DFMethod method1 = instKlass.lookupMethod(
		    CallStyle.Constructor, (String)null, argTypes);
                method1.addCaller(this);
		this.fixateLambda(
                    defined, method1.getFuncType(), cstr.arguments());
            } catch (MethodNotFound e) {
	    }
	    return instType;

        } else if (expr instanceof ConditionalExpression) {
            // "c? a : b"
            ConditionalExpression cond = (ConditionalExpression)expr;
            this.enumRefsExpr(defined, scope, cond.getExpression());
            this.enumRefsExpr(defined, scope, cond.getThenExpression());
            return this.enumRefsExpr(defined, scope, cond.getElseExpression());

        } else if (expr instanceof InstanceofExpression) {
            // "a instanceof A"
            return DFBasicType.BOOLEAN;

        } else if (expr instanceof LambdaExpression) {
            // "x -> { ... }"
            LambdaExpression lambda = (LambdaExpression)expr;
            String id = Utils.encodeASTNode(lambda);
            DFLambdaKlass lambdaKlass = (DFLambdaKlass)this.getType(id);
            lambdaKlass.load();
            for (DFLambdaKlass.CapturedRef captured :
                     lambdaKlass.getCapturedRefs()) {
                _inputRefs.add(captured.getOriginal());
            }
            return lambdaKlass;

        } else if (expr instanceof ExpressionMethodReference) {
            ExpressionMethodReference methodref = (ExpressionMethodReference)expr;
            String id = Utils.encodeASTNode(methodref);
            DFMethodRefKlass methodRefKlass = (DFMethodRefKlass)this.getType(id);
            methodRefKlass.load();
            Expression expr1 = methodref.getExpression();
            DFType type = null;
            if (expr1 instanceof Name) {
                try {
                    type = _finder.lookupType((Name)expr1);
                } catch (TypeNotFound e) {
                }
            }
            if (type == null) {
                type = this.enumRefsExpr(defined, scope, expr1);
            }
            if (type != null) {
                DFKlass klass = type.toKlass();
                methodRefKlass.setRefKlass(klass);
                if (methodRefKlass.isDefined()) {
                    defined.add(methodRefKlass);
                }
            }
            return methodRefKlass;

        } else if (expr instanceof CreationReference) {
            CreationReference methodref = (CreationReference)expr;
            String id = Utils.encodeASTNode(methodref);
            DFMethodRefKlass methodRefKlass = (DFMethodRefKlass)this.getType(id);
            methodRefKlass.load();
            try {
                DFKlass klass = _finder.resolve(methodref.getType()).toKlass();
                methodRefKlass.setRefKlass(klass);
                if (methodRefKlass.isDefined()) {
                    defined.add(methodRefKlass);
                }
            } catch (TypeNotFound e) {
            }
            return methodRefKlass;

        } else if (expr instanceof SuperMethodReference) {
            SuperMethodReference methodref = (SuperMethodReference)expr;
            String id = Utils.encodeASTNode(methodref);
            DFMethodRefKlass methodRefKlass = (DFMethodRefKlass)this.getType(id);
            methodRefKlass.load();
            try {
                DFKlass klass = _finder.lookupType(methodref.getQualifier()).toKlass();
                klass = klass.getBaseKlass();
                methodRefKlass.setRefKlass(klass);
                if (methodRefKlass.isDefined()) {
                    defined.add(methodRefKlass);
                }
            } catch (TypeNotFound e) {
            }
            return methodRefKlass;

        } else if (expr instanceof TypeMethodReference) {
            TypeMethodReference methodref = (TypeMethodReference)expr;
            String id = Utils.encodeASTNode(methodref);
            DFMethodRefKlass methodRefKlass = (DFMethodRefKlass)this.getType(id);
            methodRefKlass.load();
            try {
                DFKlass klass = _finder.resolve(methodref.getType()).toKlass();
                methodRefKlass.setRefKlass(klass);
                if (methodRefKlass.isDefined()) {
                    defined.add(methodRefKlass);
                }
            } catch (TypeNotFound e) {
            }
            return methodRefKlass;

        } else {
            // ???
            throw new InvalidSyntax(expr);
        }
    }

    private DFRef enumRefsAssignment(
	List<DFKlass> defined,
        DFLocalScope scope, Expression expr)
        throws InvalidSyntax {
        assert expr != null;

        if (expr instanceof Name) {
	    // "a.b"
            Name name = (Name)expr;
            DFRef ref;
            try {
                if (name.isSimpleName()) {
                    ref = scope.lookupVar((SimpleName)name);
                } else {
                    QualifiedName qname = (QualifiedName)name;
                    // Try assuming it's a variable access.
                    DFType type = this.enumRefsExpr(
                        defined, scope, qname.getQualifier());
                    if (type == null) {
                        // Turned out it's a class variable.
                        try {
                            type = _finder.lookupType(qname.getQualifier());
                        } catch (TypeNotFound e) {
                            return null;
                        }
                    }
                    //_inputRefs.add(scope.lookupThis());
                    DFKlass klass = type.toKlass();
                    klass.load();
                    SimpleName fieldName = qname.getName();
                    ref = klass.lookupField(fieldName);
                }
            } catch (VariableNotFound e) {
                return null;
            }
            if (!ref.isLocal()) {
                _outputRefs.add(ref);
            }
            return ref;

        } else if (expr instanceof ArrayAccess) {
	    // "a[0]"
            ArrayAccess aa = (ArrayAccess)expr;
            DFType type = this.enumRefsExpr(defined, scope, aa.getArray());
            this.enumRefsExpr(defined, scope, aa.getIndex());
            if (type instanceof DFArrayType) {
                DFRef ref = scope.lookupArray(type);
                _outputRefs.add(ref);
                return ref;
            }
            return null;

        } else if (expr instanceof FieldAccess) {
	    // "(expr).foo"
            FieldAccess fa = (FieldAccess)expr;
            Expression expr1 = fa.getExpression();
            DFType type = null;
            if (expr1 instanceof Name) {
                try {
                    type = _finder.lookupType((Name)expr1);
                } catch (TypeNotFound e) {
                }
            }
            if (type == null) {
                type = this.enumRefsExpr(defined, scope, expr1);
                if (type == null) return null;
            }
            DFKlass klass = type.toKlass();
            klass.load();
            SimpleName fieldName = fa.getName();
            try {
                DFRef ref = klass.lookupField(fieldName);
                _outputRefs.add(ref);
                return ref;
            } catch (VariableNotFound e) {
                return null;
            }

        } else if (expr instanceof SuperFieldAccess) {
	    // "super.baa"
            SuperFieldAccess sfa = (SuperFieldAccess)expr;
            SimpleName fieldName = sfa.getName();
            DFRef ref = scope.lookupThis();
            //_inputRefs.add(ref);
            DFKlass klass = ref.getRefType().toKlass().getBaseKlass();
            klass.load();
            try {
                DFRef ref2 = klass.lookupField(fieldName);
                _outputRefs.add(ref2);
                return ref2;
            } catch (VariableNotFound e) {
                return null;
            }

        } else if (expr instanceof ParenthesizedExpression) {
	    ParenthesizedExpression paren = (ParenthesizedExpression)expr;
	    return this.enumRefsAssignment(
                defined, scope, paren.getExpression());

        } else {
            throw new InvalidSyntax(expr);
        }
    }

    private void fixateLambda(
	List<DFKlass> defined,
        DFFunctionType funcType, List<Expression> exprs)
	throws InvalidSyntax {
        // types or exprs might be shorter than the other. (due to varargs calls)
	for (int i = 0; i < exprs.size(); i++) {
            DFType type = funcType.getArgType(i);
	    this.fixateLambda(defined, type, exprs.get(i));
	}
    }

    private void fixateLambda(
	List<DFKlass> defined,
        DFType type, Expression expr)
	throws InvalidSyntax {
	if (expr instanceof ParenthesizedExpression) {
            ParenthesizedExpression paren = (ParenthesizedExpression)expr;
	    fixateLambda(defined, type, paren.getExpression());

        } else if (expr instanceof LambdaExpression) {
            LambdaExpression lambda = (LambdaExpression)expr;
            String id = Utils.encodeASTNode(lambda);
            DFLambdaKlass lambdaKlass = (DFLambdaKlass)this.getType(id);
	    lambdaKlass.load();
	    lambdaKlass.setBaseKlass(type.toKlass());
            if (lambdaKlass.isDefined()) {
                defined.add(lambdaKlass);
            }

        } else if (expr instanceof MethodReference) {
            MethodReference methodref = (MethodReference)expr;
            String id = Utils.encodeASTNode(methodref);
            DFMethodRefKlass methodRefKlass = (DFMethodRefKlass)this.getType(id);
	    methodRefKlass.load();
	    methodRefKlass.setBaseKlass(type.toKlass());
            if (methodRefKlass.isDefined()) {
                defined.add(methodRefKlass);
            }

	}
    }

    public boolean expandRefs(DFMethod callee) {
        boolean added = false;
        for (DFRef ref : callee._inputRefs) {
            if (!_inputRefs.contains(ref)) {
                _inputRefs.add(ref);
                added = true;
            }
        }
        for (DFRef ref : callee._outputRefs) {
            if (!_outputRefs.contains(ref)) {
                _outputRefs.add(ref);
                added = true;
            }
        }
        return added;
    }

    // Overrider
    private class Overrider implements Comparable<Overrider> {

	public DFMethod method;
	public int level;

	public Overrider(DFMethod method, int level) {
	    this.method = method;
	    this.level = level;
	}

	@Override
	public String toString() {
	    return ("<Overrider: "+this.method+" ("+this.level+")>");
	}

	@Override
	public int compareTo(Overrider overrider) {
	    if (this.level != overrider.level) {
		return overrider.level - this.level;
	    } else {
		return this.method.compareTo(overrider.method);
	    }
	}
    }

    /**
     * Performs dataflow analysis for a given method.
     */
    @SuppressWarnings("unchecked")
    public DFGraph processKlassBody(Counter counter)
        throws InvalidSyntax, EntityNotFound {
        // lookup base/inner klasses.
        assert _ast != null;
        List<BodyDeclaration> decls;
        if (_ast instanceof AbstractTypeDeclaration) {
            decls = ((AbstractTypeDeclaration)_ast).bodyDeclarations();
        } else if (_ast instanceof ClassInstanceCreation) {
	    ClassInstanceCreation cstr = (ClassInstanceCreation)_ast;
            decls = cstr.getAnonymousClassDeclaration().bodyDeclarations();
        } else {
            throw new InvalidSyntax(_ast);
        }

        assert _scope != null;
        assert _finder != null;
        MethodGraph graph = new MethodGraph("K"+counter.getNewId()+"_"+_name);
        DFContext ctx = new DFContext(graph, _scope);

        // Create input nodes.
        if (this.isTransparent()) {
            for (DFRef ref : this.getInputRefs()) {
                DFNode input = new InputNode(graph, _scope, ref, null);
                ctx.set(input);
            }
        }

        graph.processBodyDecls(
            ctx, _scope, _klass, decls);

        // Create output nodes.
        if (this.isTransparent()) {
            for (DFRef ref : this.getOutputRefs()) {
                DFNode output = new OutputNode(graph, _scope, ref, null);
                output.accept(ctx.get(ref));
            }
        }

        graph.cleanup(null);
        return graph;
    }

    @SuppressWarnings("unchecked")
    public DFGraph processMethod(Counter counter)
        throws InvalidSyntax, EntityNotFound {
        if (_ast == null) return null;

        assert _scope != null;
        assert _finder != null;
        MethodGraph graph = new MethodGraph("M"+counter.getNewId()+"_"+_name);
        DFContext ctx = new DFContext(graph, _scope);

        ASTNode body;
        // Create input nodes.
        if (_ast instanceof MethodDeclaration) {
            MethodDeclaration methodDecl = (MethodDeclaration)_ast;
            body = methodDecl.getBody();
            if (body == null) return null;
            this.addInputMethodDecl(ctx, graph, methodDecl);
        } else if (_ast instanceof LambdaExpression) {
            LambdaExpression lambda = (LambdaExpression)_ast;
            body = lambda.getBody();
            this.addInputLambda(ctx, graph, lambda);
        } else {
            throw new InvalidSyntax(_ast);
        }
        ConsistentHashSet<DFNode> preserved = new ConsistentHashSet<DFNode>();
        {
            DFRef ref = _scope.lookupThis();
            DFNode input = new InputNode(graph, _scope, ref, null);
            ctx.set(input);
            preserved.add(input);
        }
        if (this.isTransparent()) {
            for (DFRef ref : this.getInputRefs()) {
                DFNode input = new InputNode(graph, _scope, ref, null);
                ctx.set(input);
                preserved.add(input);
            }
        }

        try {
            graph.processMethodBody(ctx, _scope, body);
        } catch (MethodNotFound e) {
            e.setMethod(this);
            Logger.error(
                "DFMethod.processMethod: MethodNotFound",
                this, e.name+"("+Utils.join(e.argTypes)+")");
            throw e;
        } catch (EntityNotFound e) {
            e.setMethod(this);
            Logger.error(
                "DFMethod.processMethod: EntityNotFound",
                this, e.name);
            throw e;
        }

        // Create output nodes.
        {
            DFRef ref = _scope.lookupReturn();
            if (ctx.getLast(ref) != null) {
                DFNode output = new OutputNode(graph, _scope, ref, null);
                output.accept(ctx.getLast(ref));
                preserved.add(output);
            }
        }
        for (DFRef ref : _scope.getExcRefs()) {
            if (ctx.getLast(ref) != null) {
                DFNode output = new OutputNode(graph, _scope, ref, null);
                output.accept(ctx.getLast(ref));
                preserved.add(output);
            }
        }
        if (this.isTransparent()) {
            for (DFRef ref : this.getOutputRefs()) {
                DFNode output = new OutputNode(graph, _scope, ref, null);
                output.accept(ctx.get(ref));
                preserved.add(output);
            }
        }

        // Do not remove input/output nodes.
        graph.cleanup(preserved);
        return graph;
    }

    @SuppressWarnings("unchecked")
    private void addInputMethodDecl(
        DFContext ctx, DFGraph graph, MethodDeclaration methodDecl)
        throws InvalidSyntax, EntityNotFound {
        int i = 0;
        for (VariableDeclaration decl :
                 (List<VariableDeclaration>)methodDecl.parameters()) {
            DFRef ref = _scope.lookupArgument(i);
            DFNode input = new InputNode(graph, _scope, ref, decl);
            ctx.set(input);
            DFNode assign = new AssignNode(
                graph, _scope, _scope.lookupVar(decl.getName()), decl);
            assign.accept(input);
            ctx.set(assign);
            i++;
        }
    }

    @SuppressWarnings("unchecked")
    private void addInputLambda(
        DFContext ctx, DFGraph graph, LambdaExpression lambda)
        throws InvalidSyntax, EntityNotFound {
        int i = 0;
        for (VariableDeclaration decl :
                 (List<VariableDeclaration>)lambda.parameters()) {
            DFRef ref = _scope.lookupArgument(i);
            DFNode input = new InputNode(graph, _scope, ref, decl);
            ctx.set(input);
            DFNode assign = new AssignNode(
                graph, _scope, _scope.lookupVar(decl.getName()), decl);
            assign.accept(input);
            ctx.set(assign);
            i++;
        }
    }

    private class MethodGraph extends DFGraph {

	private String _graphId;

	private List<DFNode> _nodes =
	    new ArrayList<DFNode>();

	public MethodGraph(String graphId) {
            super(DFMethod.this._finder);
	    _graphId = graphId;
	}

        @Override
	public String getGraphId() {
	    return _graphId;
	}

        @Override
	public int addNode(DFNode node) {
	    _nodes.add(node);
	    return _nodes.size();
	}

	public void cleanup(Set<DFNode> preserved) {
	    Set<DFNode> toremove = new HashSet<DFNode>();
	    while (true) {
		boolean changed = false;
		for (DFNode node : _nodes) {
		    if (preserved != null && preserved.contains(node)) continue;
		    if (toremove.contains(node)) continue;
		    if (node.purge()) {
			toremove.add(node);
			changed = true;
		    }
		}
		if (!changed) break;
	    }
	    for (DFNode node : toremove) {
		_nodes.remove(node);
	    }
            Collections.sort(_nodes);
	}

        @Override
	public void writeXML(XMLStreamWriter writer)
	    throws XMLStreamException {
	    DFMethod method = DFMethod.this;
	    writer.writeStartElement("method");
	    writer.writeAttribute("name", method.getSignature());
	    writer.writeAttribute("style", method.getCallStyle().toString());
	    writer.writeAttribute("abstract", Boolean.toString(method.isAbstract()));
	    for (DFMethod caller : method.getCallers()) {
		writer.writeStartElement("caller");
		writer.writeAttribute("name", caller.getSignature());
		writer.writeEndElement();
	    }
	    for (DFMethod overrider : method.getOverriders()) {
		if (overrider == method) continue;
		writer.writeStartElement("overrider");
		writer.writeAttribute("name", overrider.getSignature());
		writer.writeEndElement();
	    }
	    for (DFMethod overriding : method.getOverridings()) {
		writer.writeStartElement("overriding");
		writer.writeAttribute("name", overriding.getSignature());
		writer.writeEndElement();
	    }
	    if (method._ast != null) {
		Utils.writeXML(writer, method.getTree());
	    }
	    DFNode[] nodes = new DFNode[_nodes.size()];
	    _nodes.toArray(nodes);
	    method.getScope().writeXML(writer, nodes);
	    writer.writeEndElement();
	}
    }

    private class MethodScope extends DFLocalScope {

        private InternalRef _return = null;
        private InternalRef[] _arguments = null;
        private ConsistentHashMap<DFType, DFRef> _exceptions =
            new ConsistentHashMap<DFType, DFRef>();

        protected MethodScope(DFVarScope outer, String name) {
            super(outer, name);
        }

	public boolean isDefined() {
	    return _return != null && _arguments != null;
	}

        public DFRef lookupArgument(int index) {
	    assert _arguments != null;
            return _arguments[index];
        }

        @Override
        public DFRef lookupReturn() {
	    assert _return != null;
            return _return;
        }

        @Override
        public DFRef lookupException(DFType type) {
            DFRef ref = _exceptions.get(type);
            if (ref == null) {
                ref = new InternalRef(type, type.getTypeName());
                _exceptions.put(type, ref);
            }
            return ref;
        }

        public List<DFRef> getExcRefs() {
            return _exceptions.values();
        }

        private void buildInternalRefs(List<VariableDeclaration> parameters) {
	    DFFunctionType funcType = DFMethod.this.getFuncType();
            _return = new InternalRef(funcType.getReturnType(), "return");
            _arguments = new InternalRef[parameters.size()];
            DFType[] argTypes = funcType.getRealArgTypes();
            int i = 0;
            for (VariableDeclaration decl : parameters) {
                DFType argType = argTypes[i];
                int ndims = decl.getExtraDimensions();
                if (ndims != 0) {
                    argType = DFArrayType.getType(argType, ndims);
                }
                String name;
                if (funcType.isVarArg(i)) {
                    name = "varargs";
                } else {
                    name = "arg"+i;
                }
                _arguments[i] = new InternalRef(argType, name);
                this.addVar(decl.getName(), argType);
                i++;
            }
        }

        /**
         * Lists all the variables defined inside a method.
         */
        @SuppressWarnings("unchecked")
        public void buildMethodDecl(
            DFTypeFinder finder, MethodDeclaration methodDecl)
            throws InvalidSyntax {
            //Logger.info("MethodScope.buildMethodDecl:", this);
            Statement stmt = methodDecl.getBody();
            if (stmt == null) return;
            this.buildInternalRefs(methodDecl.parameters());
            this.buildStmt(finder, stmt);
        }

        @SuppressWarnings("unchecked")
        public void buildLambda(
            DFTypeFinder finder, LambdaExpression lambda)
            throws InvalidSyntax {
            //Logger.info("MethodScope.buildLambda:", this);
            ASTNode body = lambda.getBody();
            this.buildInternalRefs(lambda.parameters());
            if (body instanceof Statement) {
                this.buildStmt(finder, (Statement)body);
            } else if (body instanceof Expression) {
                this.buildExpr(finder, (Expression)body);
            } else {
                throw new InvalidSyntax(body);
            }
        }

        public void buildBodyDecls(
            DFTypeFinder finder, List<BodyDeclaration> decls)
            throws InvalidSyntax {
            for (BodyDeclaration body : decls) {
                if (body instanceof Initializer) {
                    Initializer initializer = (Initializer)body;
                    this.buildStmt(finder, initializer.getBody());
                }
            }
        }

        // Special references that are used in a method.
        // (not a real variable.)
        private class InternalRef extends DFRef {

            private String _name;

            public InternalRef(DFType type, String name) {
                super(type);
                _name = name;
            }

            @Override
            public boolean isLocal() {
                return false;
            }

            @Override
            public String getFullName() {
                return "#"+_name;
            }
        }
    }
}
