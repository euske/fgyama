//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFFrame
//
public class DFFrame {

    private DFMethod _method;
    private String _label;
    private DFFrame _outer;

    private Map<String, DFFrame> _ast2child =
        new HashMap<String, DFFrame>();
    private ConsistentHashSet<DFRef> _inputRefs =
        new ConsistentHashSet<DFRef>();
    private ConsistentHashSet<DFRef> _outputRefs =
        new ConsistentHashSet<DFRef>();
    private List<DFExit> _exits =
        new ArrayList<DFExit>();

    public static final String BREAKABLE = "@BREAKABLE";
    public static final String CATCHABLE = "@CATCHABLE";
    public static final String RETURNABLE = "@RETURNABLE";

    public DFFrame(DFMethod method, String label) {
        this(method, label, null);
    }

    private DFFrame(DFMethod method, String label, DFFrame outer) {
        assert label != null;
	_method = method;
        _label = label;
        _outer = outer;
    }

    @Override
    public String toString() {
        return ("<DFFrame("+_method+":"+_label+")>");
    }

    private DFFrame addChild(String label, ASTNode ast) {
        DFFrame frame = new DFFrame(_method, label, this);
        _ast2child.put(Utils.encodeASTNode(ast), frame);
        return frame;
    }

    public String getLabel() {
        return _label;
    }

    public DFFrame getChildByAST(ASTNode ast) {
        String key = Utils.encodeASTNode(ast);
        assert _ast2child.containsKey(key);
        return _ast2child.get(key);
    }

    public DFFrame find(String label) {
        DFFrame frame = this;
        while (frame != null) {
            if (frame.getLabel().equals(label)) break;
            frame = frame._outer;
        }
        return frame;
    }

    public boolean expandRefs(DFFrame innerFrame) {
        boolean added = false;
        for (DFRef ref : innerFrame._inputRefs) {
            if (ref.isLocal()) continue;
            if (!_inputRefs.contains(ref)) {
                _inputRefs.add(ref);
                added = true;
            }
        }
        for (DFRef ref : innerFrame._outputRefs) {
            if (ref.isLocal()) continue;
            if (!_outputRefs.contains(ref)) {
                _outputRefs.add(ref);
                added = true;
            }
        }
        return added;
    }

    private void addInputRef(DFRef ref) {
        _inputRefs.add(ref);
    }

    private void addOutputRef(DFRef ref) {
        _outputRefs.add(ref);
    }

    private void expandLocalRefs(DFFrame innerFrame) {
        _inputRefs.addAll(innerFrame._inputRefs);
        _outputRefs.addAll(innerFrame._outputRefs);
    }

    private void removeRefs(DFLocalScope innerScope) {
        for (DFRef ref : innerScope.getRefs()) {
            _inputRefs.remove(ref);
            _outputRefs.remove(ref);
        }
    }

    public Collection<DFRef> getInputRefs() {
        return _inputRefs;
    }

    public Collection<DFRef> getOutputRefs() {
        return _outputRefs;
    }

    public DFExit[] getExits() {
        DFExit[] exits = new DFExit[_exits.size()];
        _exits.toArray(exits);
        return exits;
    }

    public void addExit(DFExit exit) {
        //Logger.info("DFFrame.addExit:", this, ":", exit);
        _exits.add(exit);
    }

    @SuppressWarnings("unchecked")
    public void buildMethodDecl(
        DFTypeFinder finder, DFMethod method, DFLocalScope scope,
        MethodDeclaration methodDecl)
        throws InvalidSyntax {
        if (methodDecl.getBody() == null) return;
        // Reference the arguments.
        int i = 0;
        for (VariableDeclaration decl :
                 (List<VariableDeclaration>) methodDecl.parameters()) {
            try {
                DFRef ref = scope.lookupArgument(i);
                this.addInputRef(ref);
            } catch (VariableNotFound e) {
                Logger.error(
                    "DFFrame.buildMethodDecl: VariableNotFound (arg)",
                    this, e.name, decl);
            }
            i++;
        }
        // Constructor changes all the member fields.
        if (method.getCallStyle() == DFMethod.CallStyle.Constructor) {
            DFKlass klass = method.getKlass();
            for (DFKlass.FieldRef ref : klass.getFields()) {
                if (!ref.isStatic()) {
                    this.addOutputRef(ref);
                }
            }
        }
        this.buildStmt(finder, method, scope, methodDecl.getBody());
    }

    @SuppressWarnings("unchecked")
    public void buildLambda(
        DFTypeFinder finder, DFMethod method, DFLocalScope scope,
        LambdaExpression lambda)
        throws InvalidSyntax {
        // Reference the arguments.
        int i = 0;
        for (VariableDeclaration decl :
                 (List<VariableDeclaration>) lambda.parameters()) {
            try {
                DFRef ref = scope.lookupArgument(i);
                this.addInputRef(ref);
            } catch (VariableNotFound e) {
                Logger.error(
                    "DFFrame.buildLambda: VariableNotFound (arg)",
                    this, e.name, decl);
            }
            i++;
        }
        ASTNode body = lambda.getBody();
        if (body instanceof Statement) {
            this.buildStmt(finder, method, scope, (Statement)body);
        } else if (body instanceof Expression) {
            this.buildExpr(finder, method, scope, (Expression)body);
        } else {
            throw new InvalidSyntax(body);
        }
    }

    @SuppressWarnings("unchecked")
    public void buildBodyDecls(
        DFTypeFinder finder, DFMethod method, DFLocalScope scope,
        List<BodyDeclaration> decls)
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
			    this.buildExpr(finder, method, method.getScope(), init);
			    this.fixateType(method, init, ref.getRefType());
			}
		    } catch (VariableNotFound e) {
			Logger.error(
			    "DFFrame.buildBodyDecls: VariableNotFound (decl)",
			    this, e.name, frag);
		    }
		}
            } else if (body instanceof Initializer) {
                Initializer initializer = (Initializer)body;
                DFLocalScope innerScope = scope.getChildByAST(body);
                this.buildStmt(finder, method, innerScope, initializer.getBody());
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void buildStmt(
        DFTypeFinder finder, DFMethod method, DFLocalScope scope,
        Statement stmt)
        throws InvalidSyntax {
        assert stmt != null;

        if (stmt instanceof AssertStatement) {
	    // "assert x;"

        } else if (stmt instanceof Block) {
	    // "{ ... }"
            DFLocalScope innerScope = scope.getChildByAST(stmt);
            Block block = (Block)stmt;
            for (Statement cstmt :
                     (List<Statement>) block.statements()) {
                this.buildStmt(finder, method, innerScope, cstmt);
            }
            this.removeRefs(innerScope);

        } else if (stmt instanceof EmptyStatement) {

        } else if (stmt instanceof VariableDeclarationStatement) {
	    // "int a = 2;"
            VariableDeclarationStatement varStmt =
                (VariableDeclarationStatement)stmt;
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>) varStmt.fragments()) {
                try {
                    DFRef ref = scope.lookupVar(frag.getName());
                    this.addOutputRef(ref);
                } catch (VariableNotFound e) {
                    Logger.error(
                        "DFFrame.buildStmt: VariableNotFound (decl)",
                        this, e.name, frag);
                }
                Expression init = frag.getInitializer();
                if (init != null) {
                    this.buildExpr(finder, method, scope, init);
                }
            }

        } else if (stmt instanceof ExpressionStatement) {
	    // "foo();"
            ExpressionStatement exprStmt = (ExpressionStatement)stmt;
            this.buildExpr(finder, method, scope, exprStmt.getExpression());

        } else if (stmt instanceof IfStatement) {
	    // "if (c) { ... } else { ... }"
            IfStatement ifStmt = (IfStatement)stmt;
            this.buildExpr(finder, method, scope, ifStmt.getExpression());
            Statement thenStmt = ifStmt.getThenStatement();
            DFFrame thenFrame = this.addChild("@THEN", thenStmt);
            thenFrame.buildStmt(finder, method, scope, thenStmt);
            this.expandLocalRefs(thenFrame);
            Statement elseStmt = ifStmt.getElseStatement();
            if (elseStmt != null) {
                DFFrame elseFrame = this.addChild("@ELSE", elseStmt);
                elseFrame.buildStmt(finder, method, scope, elseStmt);
                this.expandLocalRefs(elseFrame);
            }

        } else if (stmt instanceof SwitchStatement) {
	    // "switch (x) { case 0: ...; }"
            SwitchStatement switchStmt = (SwitchStatement)stmt;
            DFType type = this.buildExpr(
                finder, method, scope, switchStmt.getExpression());
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
            DFFrame innerFrame = this.addChild(DFFrame.BREAKABLE, stmt);
            for (Statement cstmt :
                     (List<Statement>) switchStmt.statements()) {
                if (cstmt instanceof SwitchCase) {
                    SwitchCase switchCase = (SwitchCase)cstmt;
                    Expression expr = switchCase.getExpression();
                    if (expr != null) {
                        if (enumKlass != null && expr instanceof SimpleName) {
                            // special treatment for enum.
                            try {
                                DFRef ref = enumKlass.lookupField((SimpleName)expr);
                                this.addInputRef(ref);
                            } catch (VariableNotFound e) {
                                Logger.error(
                                    "DFFrame.buildStmt: VariableNotFound (switch)",
                                    this, e.name, expr);
                            }
                        } else {
                            innerFrame.buildExpr(finder, method, innerScope, expr);
                        }
                    }
                } else {
                    innerFrame.buildStmt(finder, method, innerScope, cstmt);
                }
            }
            innerFrame.removeRefs(innerScope);
            this.expandLocalRefs(innerFrame);

        } else if (stmt instanceof SwitchCase) {
            // Invalid "case" placement.
            throw new InvalidSyntax(stmt);

        } else if (stmt instanceof WhileStatement) {
	    // "while (c) { ... }"
            WhileStatement whileStmt = (WhileStatement)stmt;
            DFLocalScope innerScope = scope.getChildByAST(stmt);
            DFFrame innerFrame = this.addChild(DFFrame.BREAKABLE, stmt);
            innerFrame.buildExpr(finder, method, scope, whileStmt.getExpression());
            innerFrame.buildStmt(finder, method, innerScope, whileStmt.getBody());
            innerFrame.removeRefs(innerScope);
            this.expandLocalRefs(innerFrame);

        } else if (stmt instanceof DoStatement) {
	    // "do { ... } while (c);"
            DoStatement doStmt = (DoStatement)stmt;
            DFLocalScope innerScope = scope.getChildByAST(stmt);
            DFFrame innerFrame = this.addChild(DFFrame.BREAKABLE, stmt);
            innerFrame.buildStmt(finder, method, innerScope, doStmt.getBody());
            innerFrame.buildExpr(finder, method, scope, doStmt.getExpression());
            innerFrame.removeRefs(innerScope);
            this.expandLocalRefs(innerFrame);

        } else if (stmt instanceof ForStatement) {
	    // "for (i = 0; i < 10; i++) { ... }"
            ForStatement forStmt = (ForStatement)stmt;
            DFLocalScope innerScope = scope.getChildByAST(stmt);
            for (Expression init : (List<Expression>) forStmt.initializers()) {
                this.buildExpr(finder, method, innerScope, init);
            }
            DFFrame innerFrame = this.addChild(DFFrame.BREAKABLE, stmt);
            Expression expr = forStmt.getExpression();
            if (expr != null) {
                innerFrame.buildExpr(finder, method, innerScope, expr);
            }
            innerFrame.buildStmt(finder, method, innerScope, forStmt.getBody());
            for (Expression update : (List<Expression>) forStmt.updaters()) {
                innerFrame.buildExpr(finder, method, innerScope, update);
            }
            innerFrame.removeRefs(innerScope);
            this.expandLocalRefs(innerFrame);

        } else if (stmt instanceof EnhancedForStatement) {
	    // "for (x : array) { ... }"
            EnhancedForStatement eForStmt = (EnhancedForStatement)stmt;
            this.buildExpr(finder, method, scope, eForStmt.getExpression());
            DFLocalScope innerScope = scope.getChildByAST(stmt);
            DFFrame innerFrame = this.addChild(DFFrame.BREAKABLE, stmt);
            innerFrame.buildStmt(finder, method, innerScope, eForStmt.getBody());
            innerFrame.removeRefs(innerScope);
            this.expandLocalRefs(innerFrame);

        } else if (stmt instanceof ReturnStatement) {
	    // "return 42;"
            ReturnStatement rtrnStmt = (ReturnStatement)stmt;
            Expression expr = rtrnStmt.getExpression();
            if (expr != null) {
                this.buildExpr(finder, method, scope, expr);
                this.addOutputRef(scope.lookupReturn());
            }

        } else if (stmt instanceof BreakStatement) {
	    // "break;"

        } else if (stmt instanceof ContinueStatement) {
	    // "continue;"

        } else if (stmt instanceof LabeledStatement) {
	    // "here:"
            LabeledStatement labeledStmt = (LabeledStatement)stmt;
            SimpleName labelName = labeledStmt.getLabel();
            String label = labelName.getIdentifier();
            DFFrame innerFrame = this.addChild(label, stmt);
            innerFrame.buildStmt(finder, method, scope, labeledStmt.getBody());
            this.expandLocalRefs(innerFrame);

        } else if (stmt instanceof SynchronizedStatement) {
	    // "synchronized (this) { ... }"
            SynchronizedStatement syncStmt = (SynchronizedStatement)stmt;
            this.buildExpr(finder, method, scope, syncStmt.getExpression());
            this.buildStmt(finder, method, scope, syncStmt.getBody());

        } else if (stmt instanceof TryStatement) {
	    // "try { ... } catch (e) { ... }"
            TryStatement tryStmt = (TryStatement)stmt;
            DFLocalScope tryScope = scope.getChildByAST(stmt);
            DFFrame tryFrame = this.addChild(DFFrame.CATCHABLE, stmt);
            tryFrame.buildStmt(finder, method, tryScope, tryStmt.getBody());
            tryFrame.removeRefs(tryScope);
            this.expandLocalRefs(tryFrame);
            for (CatchClause cc :
                     (List<CatchClause>) tryStmt.catchClauses()) {
                DFLocalScope catchScope = scope.getChildByAST(cc);
                DFFrame catchFrame = this.addChild("@CATCH", cc);
                catchFrame.buildStmt(finder, method, catchScope, cc.getBody());
                catchFrame.removeRefs(catchScope);
                this.expandLocalRefs(catchFrame);
            }
            Block finBlock = tryStmt.getFinally();
            if (finBlock != null) {
                this.buildStmt(finder, method, scope, finBlock);
            }

        } else if (stmt instanceof ThrowStatement) {
	    // "throw e;"
            ThrowStatement throwStmt = (ThrowStatement)stmt;
            DFType type = this.buildExpr(finder, method, scope, throwStmt.getExpression());
	    if (type == null) return;
            this.addOutputRef(scope.lookupException(type));

        } else if (stmt instanceof ConstructorInvocation) {
	    // "this(args)"
            ConstructorInvocation ci = (ConstructorInvocation)stmt;
            DFRef ref = scope.lookupThis();
            this.addInputRef(ref);
	    DFKlass klass = ref.getRefType().toKlass();
            klass.load();
            List<DFType> typeList = new ArrayList<DFType>();
            for (Expression arg : (List<Expression>) ci.arguments()) {
                DFType type = this.buildExpr(finder, method, scope, arg);
                if (type == null) return;
                typeList.add(type);
            }
            DFType[] argTypes = new DFType[typeList.size()];
            typeList.toArray(argTypes);
            try {
		DFMethod method1 = klass.lookupMethod(
		    DFMethod.CallStyle.Constructor, null, argTypes);
		this.fixateType(method, ci.arguments(), method1.getFuncType().getArgTypes());
            } catch (MethodNotFound e) {
		Logger.error(
		    "DFFrame.buildExpr: MethodNotFound (ci)",
		    this, e.name, ci);
	    }

        } else if (stmt instanceof SuperConstructorInvocation) {
	    // "super(args)"
            SuperConstructorInvocation sci = (SuperConstructorInvocation)stmt;
            DFRef ref = scope.lookupThis();
            this.addInputRef(ref);
	    DFKlass klass = ref.getRefType().toKlass();
            DFKlass baseKlass = klass.getBaseKlass();
            baseKlass.load();
            List<DFType> typeList = new ArrayList<DFType>();
            for (Expression arg : (List<Expression>) sci.arguments()) {
                DFType type = this.buildExpr(finder, method, scope, arg);
                if (type == null) return;
                typeList.add(type);
            }
            DFType[] argTypes = new DFType[typeList.size()];
            typeList.toArray(argTypes);
            try {
		DFMethod method1 = baseKlass.lookupMethod(
		    DFMethod.CallStyle.Constructor, null, argTypes);
                method1.addCaller(method);
		this.fixateType(method, sci.arguments(), method1.getFuncType().getArgTypes());
            } catch (MethodNotFound e) {
		Logger.error(
		    "DFFrame.buildExpr: MethodNotFound (sci)",
		    this, e.name, sci);
	    }

        } else if (stmt instanceof TypeDeclarationStatement) {
	    // "class K { ... }"
            // Inline classes are processed separately.

        } else {
            throw new InvalidSyntax(stmt);

        }
    }

    @SuppressWarnings("unchecked")
    public DFType buildExpr(
        DFTypeFinder finder, DFMethod method, DFLocalScope scope,
        Expression expr)
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
                    DFType type = this.buildExpr(
                        finder, method, scope, qname.getQualifier());
                    if (type == null) {
                        // Turned out it's a class variable.
                        try {
                            type = finder.lookupType(qname.getQualifier());
                        } catch (TypeNotFound e) {
			    // Do not display an error message as this could be
			    // recursively called from another buildExpr()
			    //Logger.error(
			    //    "DFFrame.buildExpr: VariableNotFound (name)",
			    //    this, e.name, name);
                            return null;
                        }
                    }
                    DFKlass klass = type.toKlass();
                    klass.load();
                    SimpleName fieldName = qname.getName();
                    ref = klass.lookupField(fieldName);
                }
            } catch (VariableNotFound e) {
		// Do not display an error message as this could be
		// recursively called from another buildExpr()
		//Logger.error(
		//    "DFFrame.buildExpr: VariableNotFound (name)",
		//    this, e.name, name);
                return null;
            }
            this.addInputRef(ref);
            return ref.getRefType();

        } else if (expr instanceof ThisExpression) {
            // "this"
            ThisExpression thisExpr = (ThisExpression)expr;
            Name name = thisExpr.getQualifier();
            DFRef ref;
            if (name != null) {
                try {
                    DFType type = finder.lookupType(name);
                    ref = type.toKlass().getKlassScope().lookupThis();
                } catch (TypeNotFound e) {
                    Logger.error(
                        "DFFrame.buildExpr: TypeNotFound (this)",
                        this, e.name, expr);
                    return null;
                }
            } else {
                ref = scope.lookupThis();
            }
            this.addInputRef(ref);
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
            return DFBuiltinTypes.getClassKlass();

        } else if (expr instanceof PrefixExpression) {
            // "++x"
            PrefixExpression prefix = (PrefixExpression)expr;
            PrefixExpression.Operator op = prefix.getOperator();
            Expression operand = prefix.getOperand();
            if (op == PrefixExpression.Operator.INCREMENT ||
                op == PrefixExpression.Operator.DECREMENT) {
                this.buildAssignment(finder, method, scope, operand);
            }
            return DFNode.inferPrefixType(
                this.buildExpr(finder, method, scope, operand), op);

        } else if (expr instanceof PostfixExpression) {
            // "y--"
            PostfixExpression postfix = (PostfixExpression)expr;
            PostfixExpression.Operator op = postfix.getOperator();
            Expression operand = postfix.getOperand();
            if (op == PostfixExpression.Operator.INCREMENT ||
                op == PostfixExpression.Operator.DECREMENT) {
                this.buildAssignment(finder, method, scope, operand);
            }
            return this.buildExpr(finder, method, scope, operand);

        } else if (expr instanceof InfixExpression) {
            // "a+b"
            InfixExpression infix = (InfixExpression)expr;
            InfixExpression.Operator op = infix.getOperator();
            DFType left = this.buildExpr(
                finder, method, scope, infix.getLeftOperand());
            DFType right = this.buildExpr(
                finder, method, scope, infix.getRightOperand());
            if (left == null || right == null) return null;
            return DFNode.inferInfixType(left, op, right);

        } else if (expr instanceof ParenthesizedExpression) {
            // "(expr)"
            ParenthesizedExpression paren = (ParenthesizedExpression)expr;
            return this.buildExpr(finder, method, scope, paren.getExpression());

        } else if (expr instanceof Assignment) {
            // "p = q"
            Assignment assn = (Assignment)expr;
            Assignment.Operator op = assn.getOperator();
            if (op != Assignment.Operator.ASSIGN) {
                this.buildExpr(finder, method, scope, assn.getLeftHandSide());
            }
            DFRef ref = this.buildAssignment(finder, method, scope, assn.getLeftHandSide());
            if (ref != null) {
                this.fixateType(method, assn.getRightHandSide(), ref.getRefType());
            }
            return this.buildExpr(finder, method, scope, assn.getRightHandSide());

        } else if (expr instanceof VariableDeclarationExpression) {
            // "int a=2"
            VariableDeclarationExpression decl =
                (VariableDeclarationExpression)expr;
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>) decl.fragments()) {
                try {
                    DFRef ref = scope.lookupVar(frag.getName());
                    this.addOutputRef(ref);
                    Expression init = frag.getInitializer();
                    if (init != null) {
                        this.buildExpr(finder, method, scope, init);
                    }
                } catch (VariableNotFound e) {
                    Logger.error(
                        "DFFrame.buildExpr: VariableNotFound (decl)",
                        this, e.name, frag);
                }
            }
            return null; // XXX what type?

        } else if (expr instanceof MethodInvocation) {
            MethodInvocation invoke = (MethodInvocation)expr;
            Expression expr1 = invoke.getExpression();
            DFMethod.CallStyle callStyle;
            DFKlass klass = null;
            if (expr1 == null) {
                // "method()"
                DFRef ref = scope.lookupThis();
                this.addInputRef(ref);
                klass = ref.getRefType().toKlass();
                callStyle = DFMethod.CallStyle.InstanceOrStatic;
            } else {
                callStyle = DFMethod.CallStyle.InstanceMethod;
                if (expr1 instanceof Name) {
                    // "ClassName.method()"
                    try {
                        klass = finder.lookupType((Name)expr1).toKlass();
                        callStyle = DFMethod.CallStyle.StaticMethod;
                    } catch (TypeNotFound e) {
                    }
                }
                if (klass == null) {
                    // "expr.method()"
                    DFType type = this.buildExpr(finder, method, scope, expr1);
                    if (type == null) {
			Logger.error(
			    "DFFrame.buildExpr: Type unknown (invoke)",
			    this, expr1);
			return null;
		    }
                    klass = type.toKlass();
                }
            }
            klass.load();
            List<DFType> typeList = new ArrayList<DFType>();
            for (Expression arg : (List<Expression>) invoke.arguments()) {
                DFType type = this.buildExpr(finder, method, scope, arg);
                if (type == null) {
		    Logger.error(
			"DFFrame.buildExpr: Type unknown (invoke)",
			this, arg);
		    return null;
		}
                typeList.add(type);
            }
            DFType[] argTypes = new DFType[typeList.size()];
            typeList.toArray(argTypes);
            try {
                DFMethod method1 = klass.lookupMethod(
                    callStyle, invoke.getName(), argTypes);
		for (DFMethod m : method1.getOverriders()) {
		    m.addCaller(method);
		}
                this.fixateType(method, invoke.arguments(), method1.getFuncType().getArgTypes());
                return method1.getFuncType().getReturnType();
            } catch (MethodNotFound e) {
		Logger.error(
		    "DFFrame.buildExpr: MethodNotFound (invoke)",
		    this, e.name, invoke);
                return DFUnknownType.UNKNOWN;
            }

        } else if (expr instanceof SuperMethodInvocation) {
            // "super.method()"
            SuperMethodInvocation sinvoke = (SuperMethodInvocation)expr;
            List<DFType> typeList = new ArrayList<DFType>();
            for (Expression arg : (List<Expression>) sinvoke.arguments()) {
                DFType type = this.buildExpr(finder, method, scope, arg);
                if (type == null) {
		    Logger.error(
			"DFFrame.buildExpr: Type unknown (sinvoke)",
			this, arg);
		    return null;
		}
                typeList.add(type);
            }
            DFType[] argTypes = new DFType[typeList.size()];
            typeList.toArray(argTypes);
            DFRef ref = scope.lookupThis();
            this.addInputRef(ref);
            DFKlass klass = ref.getRefType().toKlass();
            klass.load();
            DFKlass baseKlass = klass.getBaseKlass();
            baseKlass.load();
            try {
                DFMethod method1 = baseKlass.lookupMethod(
                    DFMethod.CallStyle.InstanceMethod, sinvoke.getName(), argTypes);
                method1.addCaller(method);
                this.fixateType(method, sinvoke.arguments(), method1.getFuncType().getArgTypes());
                return method1.getFuncType().getReturnType();
            } catch (MethodNotFound e) {
		Logger.error(
		    "DFFrame.buildExpr: MethodNotFound (sinvoke)",
		    this, e.name, sinvoke);
                return DFUnknownType.UNKNOWN;
            }

        } else if (expr instanceof ArrayCreation) {
            // "new int[10]"
            ArrayCreation ac = (ArrayCreation)expr;
            for (Expression dim : (List<Expression>) ac.dimensions()) {
                this.buildExpr(finder, method, scope, dim);
            }
            ArrayInitializer init = ac.getInitializer();
            if (init != null) {
                this.buildExpr(finder, method, scope, init);
            }
            try {
                DFType type = finder.resolve(ac.getType().getElementType());
                type.toKlass().load();
                return type;
            } catch (TypeNotFound e) {
                Logger.error(
                    "DFFrame.buildExpr: TypeNotFound (array)",
                    this, e.name, expr);
                return null;
            }

        } else if (expr instanceof ArrayInitializer) {
            // "{ 5,9,4,0 }"
            ArrayInitializer init = (ArrayInitializer)expr;
            DFType type = null;
            for (Expression expr1 : (List<Expression>) init.expressions()) {
                type = this.buildExpr(finder, method, scope, expr1);
            }
            return type;

        } else if (expr instanceof ArrayAccess) {
            // "a[0]"
            ArrayAccess aa = (ArrayAccess)expr;
            this.buildExpr(finder, method, scope, aa.getIndex());
            DFType type = this.buildExpr(finder, method, scope, aa.getArray());
            if (type instanceof DFArrayType) {
                DFRef ref = scope.lookupArray(type);
                this.addInputRef(ref);
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
                    type = finder.lookupType((Name)expr1);
                } catch (TypeNotFound e) {
                }
            }
            if (type == null) {
                type = this.buildExpr(finder, method, scope, expr1);
                if (type == null) {
		    Logger.error(
			"DFFrame.buildExpr: Type unknown (fieldeccess)",
			this, expr1);
		    return null;
		}
            }
            DFKlass klass = type.toKlass();
            klass.load();
            SimpleName fieldName = fa.getName();
            try {
                DFRef ref = klass.lookupField(fieldName);
                this.addInputRef(ref);
                return ref.getRefType();
            } catch (VariableNotFound e) {
                Logger.error(
                    "DFFrame.buildExpr: VariableNotFound (fieldref)",
                    this, e.name, expr);
                return null;
            }

        } else if (expr instanceof SuperFieldAccess) {
            // "super.baa"
            SuperFieldAccess sfa = (SuperFieldAccess)expr;
            SimpleName fieldName = sfa.getName();
            DFRef ref = scope.lookupThis();
            this.addInputRef(ref);
            DFKlass klass = ref.getRefType().toKlass().getBaseKlass();
            klass.load();
            try {
                DFRef ref2 = klass.lookupField(fieldName);
                this.addInputRef(ref2);
                return ref2.getRefType();
            } catch (VariableNotFound e) {
                Logger.error(
                    "DFFrame.buildExpr: VariableNotFound (superfieldref)",
                    this, e.name, expr);
                return null;
            }

        } else if (expr instanceof CastExpression) {
            // "(String)"
            CastExpression cast = (CastExpression)expr;
            this.buildExpr(finder, method, scope, cast.getExpression());
            try {
                DFType type = finder.resolve(cast.getType());
                type.toKlass().load();
                return type;
            } catch (TypeNotFound e) {
                Logger.error(
                    "DFFrame.buildExpr: TypeNotFound (cast)",
                    this, e.name, expr);
                return null;
            }

        } else if (expr instanceof ClassInstanceCreation) {
            // "new T()"
            ClassInstanceCreation cstr = (ClassInstanceCreation)expr;
            AnonymousClassDeclaration anonDecl =
                cstr.getAnonymousClassDeclaration();
            DFType instType;
            if (anonDecl != null) {
                String id = Utils.encodeASTNode(anonDecl);
                instType = method.getType(id);
                if (instType == null) {
                    Logger.error(
                        "DFFrame.buildExpr: Type unknown (anondecl)",
                        this, anonDecl);
		    return null;
		}
            } else {
                try {
                    instType = finder.resolve(cstr.getType());
                } catch (TypeNotFound e) {
                    Logger.error(
                        "DFFrame.buildExpr: TypeNotFound (new)",
                        this, e.name, expr);
                    return null;
                }
            }
            DFKlass instKlass = instType.toKlass();
            instKlass.load();
            Expression expr1 = cstr.getExpression();
            if (expr1 != null) {
                this.buildExpr(finder, method, scope, expr1);
            }
            List<DFType> typeList = new ArrayList<DFType>();
            for (Expression arg : (List<Expression>) cstr.arguments()) {
                DFType type = this.buildExpr(finder, method, scope, arg);
                if (type == null) {
                    Logger.error(
                        "DFFrame.buildExpr: Type unknown (new)",
                        this, arg);
		    return null;
		}
                typeList.add(type);
            }
            DFType[] argTypes = new DFType[typeList.size()];
            typeList.toArray(argTypes);
            try {
		DFMethod method1 = instKlass.lookupMethod(
		    DFMethod.CallStyle.Constructor, null, argTypes);
                method1.addCaller(method);
		this.fixateType(method, cstr.arguments(), method1.getFuncType().getArgTypes());
            } catch (MethodNotFound e) {
		Logger.error(
		    "DFFrame.buildExpr: MethodNotFound (cstr)",
		    this, e.name, cstr);
	    }
	    return instType;

        } else if (expr instanceof ConditionalExpression) {
            // "c? a : b"
            ConditionalExpression cond = (ConditionalExpression)expr;
            this.buildExpr(finder, method, scope, cond.getExpression());
            this.buildExpr(finder, method, scope, cond.getThenExpression());
            return this.buildExpr(finder, method, scope, cond.getElseExpression());

        } else if (expr instanceof InstanceofExpression) {
            // "a instanceof A"
            return DFBasicType.BOOLEAN;

        } else if (expr instanceof LambdaExpression) {
            // "x -> { ... }"
            LambdaExpression lambda = (LambdaExpression)expr;
            String id = Utils.encodeASTNode(lambda);
            DFType lambdaType = method.getType(id);
            assert lambdaType instanceof DFFunctionalKlass;
            for (DFFunctionalKlass.CapturedRef captured :
                     ((DFFunctionalKlass)lambdaType).getCapturedRefs()) {
                this.addInputRef(captured.getOriginal());
            }
            return lambdaType;

        } else if (expr instanceof MethodReference) {
            // MethodReference
            MethodReference methodref = (MethodReference)expr;
            //  CreationReference
            //  ExpressionMethodReference
            //  SuperMethodReference
            //  TypeMethodReference
            String id = Utils.encodeASTNode(methodref);
            // XXX TODO MethodReference
            return method.getType(id);

        } else {
            // ???
            throw new InvalidSyntax(expr);
        }
    }

    private DFRef buildAssignment(
        DFTypeFinder finder, DFMethod method, DFLocalScope scope,
        Expression expr)
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
                    DFType type = this.buildExpr(
                        finder, method, scope, qname.getQualifier());
                    if (type == null) {
                        // Turned out it's a class variable.
                        try {
                            type = finder.lookupType(qname.getQualifier());
                        } catch (TypeNotFound e) {
			    Logger.error(
				"DFFrame.buildAssignment: VariableNotFound (name)",
				this, e.name, name);
                            return null;
                        }
                    }
                    this.addInputRef(scope.lookupThis());
                    DFKlass klass = type.toKlass();
                    klass.load();
                    SimpleName fieldName = qname.getName();
                    ref = klass.lookupField(fieldName);
                }
            } catch (VariableNotFound e) {
		Logger.error(
		    "DFFrame.buildAssignment: VariableNotFound (name)",
		    this, e.name, name);
                return null;
            }
            this.addOutputRef(ref);
            return ref;

        } else if (expr instanceof ArrayAccess) {
	    // "a[0]"
            ArrayAccess aa = (ArrayAccess)expr;
            DFType type = this.buildExpr(finder, method, scope, aa.getArray());
            this.buildExpr(finder, method, scope, aa.getIndex());
            if (type instanceof DFArrayType) {
                DFRef ref = scope.lookupArray(type);
                this.addOutputRef(ref);
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
                    type = finder.lookupType((Name)expr1);
                } catch (TypeNotFound e) {
                }
            }
            if (type == null) {
                type = this.buildExpr(finder, method, scope, expr1);
                if (type == null) return null;
            }
            DFKlass klass = type.toKlass();
            klass.load();
            SimpleName fieldName = fa.getName();
            try {
                DFRef ref = klass.lookupField(fieldName);
                this.addOutputRef(ref);
                return ref;
            } catch (VariableNotFound e) {
                Logger.error(
                    "DFFrame.buildAssigmnent: VariableNotFound (fieldassign)",
                    this, e.name, expr);
                return null;
            }

        } else if (expr instanceof SuperFieldAccess) {
	    // "super.baa"
            SuperFieldAccess sfa = (SuperFieldAccess)expr;
            SimpleName fieldName = sfa.getName();
            DFRef ref = scope.lookupThis();
            this.addInputRef(ref);
            DFKlass klass = ref.getRefType().toKlass().getBaseKlass();
            klass.load();
            try {
                DFRef ref2 = klass.lookupField(fieldName);
                this.addOutputRef(ref2);
                return ref2;
            } catch (VariableNotFound e) {
                Logger.error(
                    "DFFrame.buildAssigmnent: VariableNotFound (superfieldassign)",
                    this, e.name, expr);
                return null;
            }

        } else if (expr instanceof ParenthesizedExpression) {
	    ParenthesizedExpression paren = (ParenthesizedExpression)expr;
	    return this.buildAssignment(finder, method, scope, paren.getExpression());

        } else {
            throw new InvalidSyntax(expr);
        }
    }

    private void fixateType(DFTypeSpace typeSpace, Expression expr, DFType type) {
	if (expr instanceof ParenthesizedExpression) {
            ParenthesizedExpression paren = (ParenthesizedExpression)expr;
	    fixateType(typeSpace, paren.getExpression(), type);

        } else if (expr instanceof LambdaExpression) {
            LambdaExpression lambda = (LambdaExpression)expr;
            String id = Utils.encodeASTNode(lambda);
            DFFunctionalKlass lambdaKlass =
		(DFFunctionalKlass)typeSpace.getType(id);
	    lambdaKlass.setBaseKlass(type.toKlass());
	}
    }

    private void fixateType(DFTypeSpace typeSpace, List<Expression> exprs, DFType[] types) {
	for (int i = 0; i < exprs.size(); i++) {
	    this.fixateType(typeSpace, exprs.get(i), types[i]);
	}
    }

    // dump: for debugging.
    public void dump() {
        dump(System.err, "");
    }
    public void dump(PrintStream out, String indent) {
        out.println(indent+_label+" {");
        String i2 = indent + "  ";
        StringBuilder inputs = new StringBuilder();
        for (DFRef ref : this.getInputRefs()) {
            inputs.append(" "+ref);
        }
        out.println(i2+"inputs:"+inputs);
        StringBuilder outputs = new StringBuilder();
        for (DFRef ref : this.getOutputRefs()) {
            outputs.append(" "+ref);
        }
        out.println(i2+"outputs:"+outputs);
        for (DFFrame frame : _ast2child.values()) {
            frame.dump(out, i2);
        }
        out.println(indent+"}");
    }
}
