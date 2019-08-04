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

    private ConsistentHashSet<DFNode> _inputNodes = null;
    private ConsistentHashSet<DFNode> _outputNodes = null;

    public static final String ANONYMOUS = "@ANONYMOUS";
    public static final String BREAKABLE = "@BREAKABLE";
    public static final String CATCHABLE = "@CATCHABLE";
    public static final String RETURNABLE = "@RETURNABLE";

    public DFFrame(String label) {
        this(label, null);
    }

    public DFFrame(String label, DFFrame outer) {
        assert label != null;
        _label = label;
        _outer = outer;
    }

    @Override
    public String toString() {
        return ("<DFFrame("+_label+")>");
    }

    private DFFrame addChild(String label, ASTNode ast) {
        DFFrame frame = new DFFrame(label, this);
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

    public void finish(DFContext ctx) {
        assert _inputNodes == null;
        _inputNodes = new ConsistentHashSet<DFNode>();
        for (DFRef ref : _inputRefs) {
            DFNode node = ctx.getFirst(ref);
            assert node != null;
            _inputNodes.add(node);
        }
        assert _outputNodes == null;
        _outputNodes = new ConsistentHashSet<DFNode>();
        for (DFRef ref : _outputRefs) {
            DFNode node = ctx.get(ref);
            assert node != null;
            _outputNodes.add(node);
        }
    }

    public Iterable<DFNode> getInputNodes() {
        assert _inputNodes != null;
        List<DFNode> nodes = new ArrayList<DFNode>();
        for (DFNode node : _inputNodes) {
            DFRef ref = node.getRef();
            if (!ref.isLocal() || ref.isInternal()) {
                nodes.add(node);
            }
        }
        return nodes;
    }

    public Iterable<DFNode> getOutputNodes() {
        assert _outputNodes != null;
        List<DFNode> nodes = new ArrayList<DFNode>();
        for (DFNode node : _outputNodes) {
            DFRef ref = node.getRef();
            if (!ref.isLocal() || ref.isInternal()) {
                nodes.add(node);
            }
        }
        return nodes;
    }

    @SuppressWarnings("unchecked")
    public void buildMethodDecl(
        DFTypeFinder finder, DFMethod method, DFLocalScope scope,
        MethodDeclaration methodDecl)
        throws InvalidSyntax {
        if (methodDecl.getBody() == null) return;
        int i = 0;
        for (SingleVariableDeclaration decl :
                 (List<SingleVariableDeclaration>) methodDecl.parameters()) {
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
        this.buildStmt(finder, method, scope, methodDecl.getBody());
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
                    Expression init = frag.getInitializer();
                    if (init != null) {
                        this.buildExpr(finder, method, method.getScope(), init);
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
    private void buildStmt(
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
            DFFrame thenFrame = this.addChild(DFFrame.ANONYMOUS, thenStmt);
            thenFrame.buildStmt(finder, method, scope, thenStmt);
            this.expandLocalRefs(thenFrame);
            Statement elseStmt = ifStmt.getElseStatement();
            if (elseStmt != null) {
                DFFrame elseFrame = this.addChild(DFFrame.ANONYMOUS, elseStmt);
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
            }
            try {
                this.addOutputRef(scope.lookupReturn());
            } catch (VariableNotFound e) {
                Logger.error(
                    "DFFrame.buildStmt: VariableNotFound (return)",
                    this, e.name);
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
                DFFrame catchFrame = this.addChild(DFFrame.ANONYMOUS, cc);
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
            this.buildExpr(finder, method, scope, throwStmt.getExpression());
            try {
                this.addOutputRef(scope.lookupException());
            } catch (VariableNotFound e) {
                Logger.error(
                    "DFFrame.buildStmt: VariableNotFound (throw)",
                    this, e.name);
            }

        } else if (stmt instanceof ConstructorInvocation) {
	    // "this(args)"
            ConstructorInvocation ci = (ConstructorInvocation)stmt;
            for (Expression arg : (List<Expression>) ci.arguments()) {
                this.buildExpr(finder, method, scope, arg);
            }

        } else if (stmt instanceof SuperConstructorInvocation) {
	    // "super(args)"
            SuperConstructorInvocation sci = (SuperConstructorInvocation)stmt;
            for (Expression arg : (List<Expression>) sci.arguments()) {
                this.buildExpr(finder, method, scope, arg);
            }

        } else if (stmt instanceof TypeDeclarationStatement) {
	    // "class K { ... }"
            // Inline classes are processed separately.

        } else {
            throw new InvalidSyntax(stmt);

        }
    }

    @SuppressWarnings("unchecked")
    private DFType buildExpr(
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
                            return null;
                        }
                    }
                    DFKlass klass = type.toKlass();
                    klass.load();
                    SimpleName fieldName = qname.getName();
                    ref = klass.lookupField(fieldName);
                }
            } catch (VariableNotFound e) {
                //Logger.error("VariableNotFound", e.name);
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
            this.buildAssignment(finder, method, scope, assn.getLeftHandSide());
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
            DFCallStyle callStyle;
            DFKlass klass = null;
            if (expr1 == null) {
                // "method()"
                DFRef ref = scope.lookupThis();
                this.addInputRef(ref);
                klass = ref.getRefType().toKlass();
                callStyle = DFCallStyle.InstanceOrStatic;
            } else {
                callStyle = DFCallStyle.InstanceMethod;
                if (expr1 instanceof Name) {
                    // "ClassName.method()"
                    try {
                        klass = finder.lookupType((Name)expr1).toKlass();
                        callStyle = DFCallStyle.StaticMethod;
                    } catch (TypeNotFound e) {
                    }
                }
                if (klass == null) {
                    // "expr.method()"
                    DFType type = this.buildExpr(finder, method, scope, expr1);
                    if (type == null) return type;
                    klass = type.toKlass();
                }
            }
            klass.load();
            List<DFType> typeList = new ArrayList<DFType>();
            for (Expression arg : (List<Expression>) invoke.arguments()) {
                DFType type = this.buildExpr(finder, method, scope, arg);
                if (type == null) return type;
                typeList.add(type);
            }
            DFType[] argTypes = new DFType[typeList.size()];
            typeList.toArray(argTypes);
            try {
                DFMethod method1 = klass.lookupMethod(
                    callStyle, invoke.getName(), argTypes);
                method1.addCaller(method);
                return method1.getFuncType().getReturnType();
            } catch (MethodNotFound e) {
                return DFUnknownType.UNKNOWN;
            }

        } else if (expr instanceof SuperMethodInvocation) {
            // "super.method()"
            SuperMethodInvocation sinvoke = (SuperMethodInvocation)expr;
            List<DFType> typeList = new ArrayList<DFType>();
            for (Expression arg : (List<Expression>) sinvoke.arguments()) {
                DFType type = this.buildExpr(finder, method, scope, arg);
                if (type == null) return type;
                typeList.add(type);
            }
            DFType[] argTypes = new DFType[typeList.size()];
            typeList.toArray(argTypes);
            DFKlass klass = scope.lookupThis().getRefType().toKlass();
            klass.load();
            DFKlass baseKlass = klass.getBaseKlass();
            baseKlass.load();
            try {
                DFMethod method1 = baseKlass.lookupMethod(
                    DFCallStyle.InstanceMethod, sinvoke.getName(), argTypes);
                method1.addCaller(method);
                return method1.getFuncType().getReturnType();
            } catch (MethodNotFound e) {
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
                if (type == null) return type;
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
            DFKlass klass = scope.lookupThis().getRefType().toKlass().getBaseKlass();
            klass.load();
            try {
                DFRef ref = klass.lookupField(fieldName);
                this.addInputRef(ref);
                return ref.getRefType();
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
                if (instType == null) return null;
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
            instType.toKlass().load();
            Expression expr1 = cstr.getExpression();
            if (expr1 != null) {
                this.buildExpr(finder, method, scope, expr1);
            }
            for (Expression arg : (List<Expression>) cstr.arguments()) {
                this.buildExpr(finder, method, scope, arg);
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
            ASTNode body = lambda.getBody();
            if (body instanceof Statement) {
            } else if (body instanceof Expression) {
            } else {
                throw new InvalidSyntax(body);
            }
            // XXX TODO LambdaExpression
            String id = Utils.encodeASTNode(lambda);
            return method.getType(id);

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

    private void buildAssignment(
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
                            return;
                        }
                    }
                    DFKlass klass = type.toKlass();
                    klass.load();
                    SimpleName fieldName = qname.getName();
                    ref = klass.lookupField(fieldName);
                }
            } catch (VariableNotFound e) {
                //Logger.error("VariableNotFound", e.name);
                return;
            }
            this.addOutputRef(ref);

        } else if (expr instanceof ArrayAccess) {
	    // "a[0]"
            ArrayAccess aa = (ArrayAccess)expr;
            DFType type = this.buildExpr(finder, method, scope, aa.getArray());
            this.buildExpr(finder, method, scope, aa.getIndex());
            if (type instanceof DFArrayType) {
                DFRef ref = scope.lookupArray(type);
                this.addOutputRef(ref);
            }

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
                if (type == null) return;
            }
            DFKlass klass = type.toKlass();
            klass.load();
            SimpleName fieldName = fa.getName();
            try {
                DFRef ref = klass.lookupField(fieldName);
                this.addOutputRef(ref);
            } catch (VariableNotFound e) {
                Logger.error(
                    "DFFrame.buildAssigmnent: VariableNotFound (fieldassign)",
                    this, e.name, expr);
            }

        } else if (expr instanceof SuperFieldAccess) {
	    // "super.baa"
            SuperFieldAccess sfa = (SuperFieldAccess)expr;
            SimpleName fieldName = sfa.getName();
            DFKlass klass = scope.lookupThis().getRefType().toKlass().getBaseKlass();
            klass.load();
            try {
                DFRef ref = klass.lookupField(fieldName);
                this.addOutputRef(ref);
            } catch (VariableNotFound e) {
                Logger.error(
                    "DFFrame.buildAssigmnent: VariableNotFound (superfieldassign)",
                    this, e.name, expr);
            }

        } else if (expr instanceof ParenthesizedExpression) {
	    ParenthesizedExpression paren = (ParenthesizedExpression)expr;
	    this.buildAssignment(finder, method, scope, paren.getExpression());

        } else {
            throw new InvalidSyntax(expr);
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
