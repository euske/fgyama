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

    private DFFrame _outer;
    private ASTNode _ast;
    private DFTypeFinder _finder;
    private String _label;
    private DFKlass _catchKlass;
    private DFLocalScope _scope;

    private Map<String, DFFrame> _ast2child =
        new HashMap<String, DFFrame>();
    private ConsistentHashSet<DFRef> _inputRefs =
        new ConsistentHashSet<DFRef>();
    private ConsistentHashSet<DFRef> _outputRefs =
        new ConsistentHashSet<DFRef>();
    private List<DFExit> _exits =
        new ArrayList<DFExit>();

    public static final String BREAKABLE = "@BREAKABLE";
    public static final String RETURNABLE = "@RETURNABLE";

    // Frame whose name starts with '@'
    // is not selectable with labels and therefore anonymous.
    public DFFrame(DFTypeFinder finder, String label, DFLocalScope scope) {
        assert label != null;
        _outer = null;
        _ast = null;
	_finder = finder;
        _label = label;
        _catchKlass = null;
        _scope = scope;
    }

    private DFFrame(
        DFFrame outer, ASTNode ast, String label,
        DFKlass catchKlass, DFLocalScope scope) {
        assert label != null;
        _outer = outer;
        _ast = ast;
	_finder = outer._finder;
        _label = label;
        _catchKlass = catchKlass;
        _scope = scope;
    }

    @Override
    public String toString() {
        if (_ast != null) {
            return ("<DFFrame("+_label+" "+Utils.encodeASTNode(_ast)+")>");
        } else {
            return ("<DFFrame("+_label+")>");
        }
    }

    private DFFrame addChild(String label, ASTNode ast, DFLocalScope scope) {
        DFFrame frame = new DFFrame(
            this, ast, label, null, scope);
        _ast2child.put(Utils.encodeASTNode(ast), frame);
        return frame;
    }

    private DFFrame addChild(DFKlass catchKlass, ASTNode ast, DFLocalScope scope) {
        DFFrame frame = new DFFrame(
            this, ast, catchKlass.getTypeName(), catchKlass, scope);
        _ast2child.put(Utils.encodeASTNode(ast), frame);
        return frame;
    }

    public DFFrame getOuterFrame() {
        return _outer;
    }

    public String getLabel() {
        return _label;
    }

    public DFKlass getCatchKlass() {
        return _catchKlass;
    }

    public DFFrame getChildByAST(ASTNode ast) {
        String key = Utils.encodeASTNode(ast);
        assert _ast2child.containsKey(key);
        return _ast2child.get(key);
    }

    // Returns any upper Frame that has a given label.
    public DFFrame find(String label) {
        DFFrame frame = this;
        while (frame != null) {
            if (frame._label.equals(label)) break;
            frame = frame._outer;
        }
        return frame;
    }

    // Returns any upper Frame that catches a given type (and its subclasses).
    public DFFrame find(DFKlass catchKlass) {
        DFFrame frame = this;
        while (frame != null) {
            if (frame._catchKlass != null &&
                0 <= catchKlass.isSubclassOf(frame._catchKlass, null)) break;
            frame = frame._outer;
        }
        return frame;
    }

    public void addExit(DFExit exit) {
        //Logger.info("DFFrame.addExit:", this, ":", exit);
        _exits.add(exit);
    }

    public List<DFExit> getExits() {
        return _exits;
    }

    public Collection<DFRef> getInputRefs() {
        return _inputRefs;
    }

    public Collection<DFRef> getOutputRefs() {
        return _outputRefs;
    }

    @SuppressWarnings("unchecked")
    public void buildStmt(Statement stmt)
        throws InvalidSyntax {
        assert stmt != null;

        if (stmt instanceof AssertStatement) {
	    // "assert x;"

        } else if (stmt instanceof Block) {
	    // "{ ... }"
            Block block = (Block)stmt;
            DFLocalScope innerScope = _scope.getChildByAST(stmt);
            DFFrame innerFrame = this.addChild("@BLOCK", block, innerScope);
            for (Statement cstmt :
                     (List<Statement>) block.statements()) {
                innerFrame.buildStmt(cstmt);
            }
            this.expandLocalRefs(innerFrame);

        } else if (stmt instanceof EmptyStatement) {

        } else if (stmt instanceof VariableDeclarationStatement) {
	    // "int a = 2;"
            VariableDeclarationStatement varStmt =
                (VariableDeclarationStatement)stmt;
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>) varStmt.fragments()) {
                try {
                    DFRef ref = _scope.lookupVar(frag.getName());
                    _outputRefs.add(ref);
                } catch (VariableNotFound e) {
                    Logger.error(
                        "DFFrame.buildStmt: VariableNotFound (decl)",
                        this, e.name, frag);
                }
                Expression init = frag.getInitializer();
                if (init != null) {
                    this.buildExpr(init);
                }
            }

        } else if (stmt instanceof ExpressionStatement) {
	    // "foo();"
            ExpressionStatement exprStmt = (ExpressionStatement)stmt;
            this.buildExpr(exprStmt.getExpression());

        } else if (stmt instanceof IfStatement) {
	    // "if (c) { ... } else { ... }"
            IfStatement ifStmt = (IfStatement)stmt;
            DFFrame ifFrame = this.addChild("@IF", ifStmt, _scope);
            ifFrame.buildExpr(ifStmt.getExpression());
            Statement thenStmt = ifStmt.getThenStatement();
            DFFrame thenFrame = ifFrame.addChild("@THEN", thenStmt, _scope);
            thenFrame.buildStmt(thenStmt);
            ifFrame.expandLocalRefs(thenFrame);
            Statement elseStmt = ifStmt.getElseStatement();
            if (elseStmt != null) {
                DFFrame elseFrame = ifFrame.addChild("@ELSE", elseStmt, _scope);
                elseFrame.buildStmt(elseStmt);
                ifFrame.expandLocalRefs(elseFrame);
            }
            this.expandLocalRefs(ifFrame);

        } else if (stmt instanceof SwitchStatement) {
	    // "switch (x) { case 0: ...; }"
            SwitchStatement switchStmt = (SwitchStatement)stmt;
            DFType type = this.buildExpr(switchStmt.getExpression());
            if (type == null) {
                type = DFUnknownType.UNKNOWN;
            }
            DFKlass enumKlass = null;
            if (type instanceof DFKlass &&
                ((DFKlass)type).isEnum()) {
                enumKlass = type.toKlass();
                enumKlass.load();
            }
            DFLocalScope switchScope = _scope.getChildByAST(stmt);
            DFFrame switchFrame = this.addChild(DFFrame.BREAKABLE, stmt, switchScope);
            DFFrame caseFrame = null;
            for (Statement cstmt : (List<Statement>) switchStmt.statements()) {
                if (cstmt instanceof SwitchCase) {
                    if (caseFrame != null) {
                        switchFrame.expandLocalRefs(caseFrame);
                    }
                    caseFrame = switchFrame.addChild("@CASE", cstmt, switchScope);
                    SwitchCase switchCase = (SwitchCase)cstmt;
                    Expression expr = switchCase.getExpression();
                    if (expr != null) {
                        if (enumKlass != null && expr instanceof SimpleName) {
                            // special treatment for enum.
                            try {
                                DFRef ref = enumKlass.lookupField((SimpleName)expr);
                                _inputRefs.add(ref);
                            } catch (VariableNotFound e) {
                                Logger.error(
                                    "DFFrame.buildStmt: VariableNotFound (switch)",
                                    this, e.name, expr);
                            }
                        } else {
                            caseFrame.buildExpr(expr);
                        }
                    }
                } else {
                    if (caseFrame == null) {
                        // no "case" statement.
                        throw new InvalidSyntax(cstmt);
                    }
                    caseFrame.buildStmt(cstmt);
                }
            }
            if (caseFrame != null) {
                switchFrame.expandLocalRefs(caseFrame);
            }
            this.expandLocalRefs(switchFrame);

        } else if (stmt instanceof SwitchCase) {
            // Invalid "case" placement.
            throw new InvalidSyntax(stmt);

        } else if (stmt instanceof WhileStatement) {
	    // "while (c) { ... }"
            WhileStatement whileStmt = (WhileStatement)stmt;
            DFLocalScope innerScope = _scope.getChildByAST(stmt);
            DFFrame innerFrame = this.addChild(DFFrame.BREAKABLE, stmt, innerScope);
            this.buildExpr(whileStmt.getExpression());
            innerFrame.buildStmt(whileStmt.getBody());
            this.expandLocalRefs(innerFrame);

        } else if (stmt instanceof DoStatement) {
	    // "do { ... } while (c);"
            DoStatement doStmt = (DoStatement)stmt;
            DFLocalScope innerScope = _scope.getChildByAST(stmt);
            DFFrame innerFrame = this.addChild(DFFrame.BREAKABLE, stmt, innerScope);
            innerFrame.buildStmt(doStmt.getBody());
            this.buildExpr(doStmt.getExpression());
            this.expandLocalRefs(innerFrame);

        } else if (stmt instanceof ForStatement) {
	    // "for (i = 0; i < 10; i++) { ... }"
            ForStatement forStmt = (ForStatement)stmt;
            DFLocalScope innerScope = _scope.getChildByAST(stmt);
            DFFrame innerFrame = this.addChild(DFFrame.BREAKABLE, stmt, innerScope);
            for (Expression init : (List<Expression>) forStmt.initializers()) {
                innerFrame.buildExpr(init);
            }
            Expression expr = forStmt.getExpression();
            if (expr != null) {
                innerFrame.buildExpr(expr);
            }
            innerFrame.buildStmt(forStmt.getBody());
            for (Expression update : (List<Expression>) forStmt.updaters()) {
                innerFrame.buildExpr(update);
            }
            this.expandLocalRefs(innerFrame);

        } else if (stmt instanceof EnhancedForStatement) {
	    // "for (x : array) { ... }"
            EnhancedForStatement eForStmt = (EnhancedForStatement)stmt;
            this.buildExpr(eForStmt.getExpression());
            DFLocalScope innerScope = _scope.getChildByAST(stmt);
            DFFrame innerFrame = this.addChild(DFFrame.BREAKABLE, stmt, innerScope);
            innerFrame.buildStmt(eForStmt.getBody());
            this.expandLocalRefs(innerFrame);

        } else if (stmt instanceof ReturnStatement) {
	    // "return 42;"
            ReturnStatement rtrnStmt = (ReturnStatement)stmt;
            Expression expr = rtrnStmt.getExpression();
            if (expr != null) {
                this.buildExpr(expr);
            }
            // Return is handled as an Exit, not an output.

        } else if (stmt instanceof BreakStatement) {
	    // "break;"

        } else if (stmt instanceof ContinueStatement) {
	    // "continue;"

        } else if (stmt instanceof LabeledStatement) {
	    // "here:"
            LabeledStatement labeledStmt = (LabeledStatement)stmt;
            SimpleName labelName = labeledStmt.getLabel();
            String label = labelName.getIdentifier();
            DFFrame innerFrame = this.addChild(label, stmt, _scope);
            innerFrame.buildStmt(labeledStmt.getBody());
            this.expandLocalRefs(innerFrame);

        } else if (stmt instanceof SynchronizedStatement) {
	    // "synchronized (this) { ... }"
            SynchronizedStatement syncStmt = (SynchronizedStatement)stmt;
            this.buildExpr(syncStmt.getExpression());
            this.buildStmt(syncStmt.getBody());

        } else if (stmt instanceof TryStatement) {
	    // "try { ... } catch (e) { ... }"
            TryStatement tryStmt = (TryStatement)stmt;
            List<CatchClause> catches = (List<CatchClause>) tryStmt.catchClauses();
            DFFrame tryFrame = this;
            DFLocalScope tryScope = _scope.getChildByAST(tryStmt);
            // Construct Frames in reverse order.
            for (int i = catches.size()-1; 0 <= i; i--) {
                CatchClause cc = catches.get(i);
                SingleVariableDeclaration decl = cc.getException();
                DFKlass catchKlass = DFBuiltinTypes.getExceptionKlass();
                try {
                    catchKlass = _finder.resolve(decl.getType()).toKlass();
                } catch (TypeNotFound e) {
                    Logger.error(
                        "DFFrame.buildExpr: TypeNotFound (catch)",
                        this, e.name, decl);
                }
                tryFrame = tryFrame.addChild(catchKlass, tryStmt, tryScope);
            }
            tryFrame.buildStmt(tryStmt.getBody());
            for (CatchClause cc : catches) {
                DFLocalScope catchScope = _scope.getChildByAST(cc);
                DFFrame catchFrame = this.addChild("@CATCH", cc, catchScope);
                catchFrame.buildStmt(cc.getBody());
            }
            Block finBlock = tryStmt.getFinally();
            if (finBlock != null) {
                this.buildStmt(finBlock);
            }

        } else if (stmt instanceof ThrowStatement) {
	    // "throw e;"
            // Throw is handled as an Exit, not an output.

        } else if (stmt instanceof ConstructorInvocation) {
	    // "this(args)"
            ConstructorInvocation ci = (ConstructorInvocation)stmt;
            DFRef ref = _scope.lookupThis();
            _inputRefs.add(ref);
	    DFKlass klass = ref.getRefType().toKlass();
            klass.load();
	    int nargs = ci.arguments().size();
	    DFType[] argTypes = new DFType[nargs];
            for (int i = 0; i < nargs; i++) {
		Expression arg = (Expression)ci.arguments().get(i);
                DFType type = this.buildExpr(arg);
                if (type == null) {
		    Logger.error(
			"DFFrame.buildExpr: Type unknown (ci)",
			this, arg);
		    return;
		}
                argTypes[i] = type;
            }
            try {
		DFMethod method1 = klass.lookupMethod(
		    DFMethod.CallStyle.Constructor, null, argTypes);
                for (DFMethod m : method1.getOverriders()) {
                    if (m.isTransparent()) {
                        _inputRefs.addAll(m.getInputRefs());
                        _outputRefs.addAll(m.getOutputRefs());
                    }
                }
            } catch (MethodNotFound e) {
		Logger.error(
		    "DFFrame.buildExpr: MethodNotFound (ci)",
		    this, e.name, ci);
	    }

        } else if (stmt instanceof SuperConstructorInvocation) {
	    // "super(args)"
            SuperConstructorInvocation sci = (SuperConstructorInvocation)stmt;
            DFRef ref = _scope.lookupThis();
            _inputRefs.add(ref);
	    DFKlass klass = ref.getRefType().toKlass();
            DFKlass baseKlass = klass.getBaseKlass();
            baseKlass.load();
	    int nargs = sci.arguments().size();
	    DFType[] argTypes = new DFType[nargs];
            for (int i = 0; i < nargs; i++) {
		Expression arg = (Expression)sci.arguments().get(i);
                DFType type = this.buildExpr(arg);
                if (type == null) {
		    Logger.error(
			"DFFrame.buildExpr: Type unknown (sci)",
			this, arg);
		    return;
		}
                argTypes[i] = type;
            }
            try {
		DFMethod method1 = baseKlass.lookupMethod(
		    DFMethod.CallStyle.Constructor, null, argTypes);
                if (method1.isTransparent()) {
                    _inputRefs.addAll(method1.getInputRefs());
                    _outputRefs.addAll(method1.getOutputRefs());
                }
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
    public DFType buildExpr(Expression expr)
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
                    ref = _scope.lookupVar((SimpleName)name);
                } else {
                    QualifiedName qname = (QualifiedName)name;
                    // Try assuming it's a variable access.
                    DFType type = this.buildExpr(qname.getQualifier());
                    if (type == null) {
                        // Turned out it's a class variable.
                        try {
			    type = _finder.lookupType(qname.getQualifier());
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
            _inputRefs.add(ref);
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
                    Logger.error(
                        "DFFrame.buildExpr: TypeNotFound (this)",
                        this, e.name, expr);
                    return null;
                }
            } else {
                ref = _scope.lookupThis();
            }
            _inputRefs.add(ref);
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
                this.buildAssignment(operand);
            }
            return DFNode.inferPrefixType(
                this.buildExpr(operand), op);

        } else if (expr instanceof PostfixExpression) {
            // "y--"
            PostfixExpression postfix = (PostfixExpression)expr;
            PostfixExpression.Operator op = postfix.getOperator();
            Expression operand = postfix.getOperand();
            if (op == PostfixExpression.Operator.INCREMENT ||
                op == PostfixExpression.Operator.DECREMENT) {
                this.buildAssignment(operand);
            }
            return this.buildExpr(operand);

        } else if (expr instanceof InfixExpression) {
            // "a+b"
            InfixExpression infix = (InfixExpression)expr;
            InfixExpression.Operator op = infix.getOperator();
            DFType left = this.buildExpr(infix.getLeftOperand());
            DFType right = this.buildExpr(infix.getRightOperand());
            if (left == null || right == null) return null;
            return DFNode.inferInfixType(left, op, right);

        } else if (expr instanceof ParenthesizedExpression) {
            // "(expr)"
            ParenthesizedExpression paren = (ParenthesizedExpression)expr;
            return this.buildExpr(paren.getExpression());

        } else if (expr instanceof Assignment) {
            // "p = q"
            Assignment assn = (Assignment)expr;
            Assignment.Operator op = assn.getOperator();
            this.buildAssignment(assn.getLeftHandSide());
            return this.buildExpr(assn.getRightHandSide());

        } else if (expr instanceof VariableDeclarationExpression) {
            // "int a=2"
            VariableDeclarationExpression decl =
                (VariableDeclarationExpression)expr;
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>) decl.fragments()) {
                try {
                    DFRef ref = _scope.lookupVar(frag.getName());
                    _outputRefs.add(ref);
                    Expression init = frag.getInitializer();
                    if (init != null) {
                        this.buildExpr(init);
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
                DFRef ref = _scope.lookupThis();
                _inputRefs.add(ref);
                klass = ref.getRefType().toKlass();
                callStyle = DFMethod.CallStyle.InstanceOrStatic;
            } else {
                callStyle = DFMethod.CallStyle.InstanceMethod;
                if (expr1 instanceof Name) {
                    // "ClassName.method()"
                    try {
                        klass = _finder.lookupType((Name)expr1).toKlass();
                        callStyle = DFMethod.CallStyle.StaticMethod;
                    } catch (TypeNotFound e) {
                    }
                }
                if (klass == null) {
                    // "expr.method()"
                    DFType type = this.buildExpr(expr1);
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
	    int nargs = invoke.arguments().size();
	    DFType[] argTypes = new DFType[nargs];
            for (int i = 0; i < nargs; i++) {
		Expression arg = (Expression)invoke.arguments().get(i);
                DFType type = this.buildExpr(arg);
                if (type == null) {
		    Logger.error(
			"DFFrame.buildExpr: Type unknown (invoke)",
			this, arg);
		    return null;
		}
                argTypes[i] = type;
            }
            try {
                DFMethod method1 = klass.lookupMethod(
                    callStyle, invoke.getName(), argTypes);
                for (DFMethod m : method1.getOverriders()) {
                    if (m.isTransparent()) {
                        _inputRefs.addAll(m.getInputRefs());
                        _outputRefs.addAll(m.getOutputRefs());
                    }
                }
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
	    int nargs = sinvoke.arguments().size();
	    DFType[] argTypes = new DFType[nargs];
            for (int i = 0; i < nargs; i++) {
		Expression arg = (Expression)sinvoke.arguments().get(i);
                DFType type = this.buildExpr(arg);
                if (type == null) {
		    Logger.error(
			"DFFrame.buildExpr: Type unknown (sinvoke)",
			this, arg);
		    return null;
		}
                argTypes[i] = type;
            }
            DFRef ref = _scope.lookupThis();
            _inputRefs.add(ref);
            DFKlass klass = ref.getRefType().toKlass();
            klass.load();
            DFKlass baseKlass = klass.getBaseKlass();
            baseKlass.load();
            try {
                DFMethod method1 = baseKlass.lookupMethod(
                    DFMethod.CallStyle.InstanceMethod, sinvoke.getName(), argTypes);
                if (method1.isTransparent()) {
                    _inputRefs.addAll(method1.getInputRefs());
                    _outputRefs.addAll(method1.getOutputRefs());
                }
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
                this.buildExpr(dim);
            }
            ArrayInitializer init = ac.getInitializer();
            if (init != null) {
                this.buildExpr(init);
            }
            try {
                DFType type = _finder.resolve(ac.getType().getElementType());
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
                type = this.buildExpr(expr1);
            }
            return type;

        } else if (expr instanceof ArrayAccess) {
            // "a[0]"
            ArrayAccess aa = (ArrayAccess)expr;
            this.buildExpr(aa.getIndex());
            DFType type = this.buildExpr(aa.getArray());
            if (type instanceof DFArrayType) {
                DFRef ref = _scope.lookupArray(type);
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
                type = this.buildExpr(expr1);
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
                _inputRefs.add(ref);
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
            DFRef ref = _scope.lookupThis();
            _inputRefs.add(ref);
            DFKlass klass = ref.getRefType().toKlass().getBaseKlass();
            klass.load();
            try {
                DFRef ref2 = klass.lookupField(fieldName);
                _inputRefs.add(ref2);
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
            this.buildExpr(cast.getExpression());
            try {
                DFType type = _finder.resolve(cast.getType());
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
            DFType instType;
            if (cstr.getAnonymousClassDeclaration() != null) {
                String id = Utils.encodeASTNode(cstr);
                try {
                    instType = _finder.lookupType(id);
                } catch (TypeNotFound e) {
                    Logger.error(
                        "DFFrame.buildExpr: Type unknown (anondecl)",
                        this, e.name, cstr);
		    return null;
		}
            } else {
                try {
                    instType = _finder.resolve(cstr.getType());
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
                this.buildExpr(expr1);
            }
	    int nargs = cstr.arguments().size();
	    DFType[] argTypes = new DFType[nargs];
            for (int i = 0; i < nargs; i++) {
		Expression arg = (Expression)cstr.arguments().get(i);
                DFType type = this.buildExpr(arg);
                if (type == null) {
                    Logger.error(
                        "DFFrame.buildExpr: Type unknown (new)",
                        this, arg);
		    return null;
		}
                argTypes[i] = type;
            }
            try {
		DFMethod method1 = instKlass.lookupMethod(
		    DFMethod.CallStyle.Constructor, null, argTypes);
                for (DFMethod m : method1.getOverriders()) {
                    if (m.isTransparent()) {
                        _inputRefs.addAll(m.getInputRefs());
                        _outputRefs.addAll(m.getOutputRefs());
                    }
                }
            } catch (MethodNotFound e) {
		Logger.error(
		    "DFFrame.buildExpr: MethodNotFound (cstr)",
		    this, e.name, cstr);
	    }
	    return instType;

        } else if (expr instanceof ConditionalExpression) {
            // "c? a : b"
            ConditionalExpression cond = (ConditionalExpression)expr;
            this.buildExpr(cond.getExpression());
            this.buildExpr(cond.getThenExpression());
            return this.buildExpr(cond.getElseExpression());

        } else if (expr instanceof InstanceofExpression) {
            // "a instanceof A"
            return DFBasicType.BOOLEAN;

        } else if (expr instanceof LambdaExpression) {
            // "x -> { ... }"
            LambdaExpression lambda = (LambdaExpression)expr;
            String id = Utils.encodeASTNode(lambda);
            try {
                DFType lambdaType = _finder.lookupType(id);
                assert lambdaType instanceof DFLambdaKlass;
                for (DFLambdaKlass.CapturedRef captured :
                         ((DFLambdaKlass)lambdaType).getCapturedRefs()) {
                    _inputRefs.add(captured.getOriginal());
                }
                return lambdaType;
            } catch (TypeNotFound e) {
                Logger.error(
                    "DFFrame.buildExpr: TypeNotFound (lambda)",
                    this, e.name, lambda);
                return null;
            }

        } else if (expr instanceof MethodReference) {
            // MethodReference
            MethodReference methodref = (MethodReference)expr;
            //  CreationReference
            //  ExpressionMethodReference
            //  SuperMethodReference
            //  TypeMethodReference
            String id = Utils.encodeASTNode(methodref);
            // XXX TODO MethodReference
            try {
                return _finder.lookupType(id);
            } catch (TypeNotFound e) {
                Logger.error(
                    "DFFrame.buildExpr: TypeNotFound (methodref)",
                    this, e.name, methodref);
                return null;
            }

        } else {
            // ???
            throw new InvalidSyntax(expr);
        }
    }

    private DFRef buildAssignment(Expression expr)
        throws InvalidSyntax {
        assert expr != null;

        if (expr instanceof Name) {
	    // "a.b"
            Name name = (Name)expr;
            DFRef ref;
            try {
                if (name.isSimpleName()) {
                    ref = _scope.lookupVar((SimpleName)name);
                } else {
                    QualifiedName qname = (QualifiedName)name;
                    // Try assuming it's a variable access.
                    DFType type = this.buildExpr(qname.getQualifier());
                    if (type == null) {
                        // Turned out it's a class variable.
                        try {
                            type = _finder.lookupType(qname.getQualifier());
                        } catch (TypeNotFound e) {
			    Logger.error(
				"DFFrame.buildAssignment: VariableNotFound (name)",
				this, e.name, name);
                            return null;
                        }
                    }
                    _inputRefs.add(_scope.lookupThis());
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
            _outputRefs.add(ref);
            return ref;

        } else if (expr instanceof ArrayAccess) {
	    // "a[0]"
            ArrayAccess aa = (ArrayAccess)expr;
            DFType type = this.buildExpr(aa.getArray());
            this.buildExpr(aa.getIndex());
            if (type instanceof DFArrayType) {
                DFRef ref = _scope.lookupArray(type);
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
                type = this.buildExpr(expr1);
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
                Logger.error(
                    "DFFrame.buildAssigmnent: VariableNotFound (fieldassign)",
                    this, e.name, expr);
                return null;
            }

        } else if (expr instanceof SuperFieldAccess) {
	    // "super.baa"
            SuperFieldAccess sfa = (SuperFieldAccess)expr;
            SimpleName fieldName = sfa.getName();
            DFRef ref = _scope.lookupThis();
            _inputRefs.add(ref);
            DFKlass klass = ref.getRefType().toKlass().getBaseKlass();
            klass.load();
            try {
                DFRef ref2 = klass.lookupField(fieldName);
                _outputRefs.add(ref2);
                return ref2;
            } catch (VariableNotFound e) {
                Logger.error(
                    "DFFrame.buildAssigmnent: VariableNotFound (superfieldassign)",
                    this, e.name, expr);
                return null;
            }

        } else if (expr instanceof ParenthesizedExpression) {
	    ParenthesizedExpression paren = (ParenthesizedExpression)expr;
	    return this.buildAssignment(paren.getExpression());

        } else {
            throw new InvalidSyntax(expr);
        }
    }

    private void expandLocalRefs(DFFrame innerFrame) {
        for (DFRef ref : innerFrame._inputRefs) {
            if (_scope.hasRef(ref)) {
                _inputRefs.add(ref);
            }
        }
        for (DFRef ref : innerFrame._outputRefs) {
            if (_scope.hasRef(ref)) {
                _outputRefs.add(ref);
            }
        }
    }

    // dump: for debugging.
    public void dump() {
        dump(System.err, "");
    }
    public void dump(PrintStream out, String indent) {
        out.println(indent+this+" {");
        String i2 = indent + "  ";
        for (DFRef ref : this.getInputRefs()) {
            out.println(i2+"input: "+ref);
        }
        for (DFRef ref : this.getOutputRefs()) {
            out.println(i2+"output: "+ref);
        }
        for (DFExit exit : this.getExits()) {
            out.println(i2+"exit: "+exit);
        }
        for (DFFrame frame : _ast2child.values()) {
            frame.dump(out, i2);
        }
        out.println(indent+"}");
    }
}
