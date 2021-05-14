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

    private List<DFFrame> _children =
        new ArrayList<DFFrame>();
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
        _children.add(frame);
        _ast2child.put(Utils.encodeASTNode(ast), frame);
        return frame;
    }

    private DFFrame addChild(DFKlass catchKlass, ASTNode ast, DFLocalScope scope) {
        DFFrame frame = new DFFrame(
            this, ast, catchKlass.getTypeName(), catchKlass, scope);
        _children.add(frame);
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
            if (frame._catchKlass != null) {
                try {
                    frame._catchKlass.canConvertFrom(catchKlass, null);
                    break;
                } catch (TypeIncompatible e) {
                }
            }
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

    private void listInputRefs(Set<DFRef> refs) {
        for (DFFrame frame : _children) {
            frame.listInputRefs(refs);
        }
        for (DFRef ref : _inputRefs) {
            DFVarScope scope = ref.getScope();
            if (!_scope.contains(scope)) {
                refs.add(ref);
            }
        }
    }
    public Collection<DFRef> getInputRefs() {
        ConsistentHashSet<DFRef> refs = new ConsistentHashSet<DFRef>();
        this.listInputRefs(refs);
        return refs;
    }

    private void listOutputRefs(Set<DFRef> refs) {
        for (DFFrame frame : _children) {
            frame.listOutputRefs(refs);
        }
        for (DFRef ref : _outputRefs) {
            DFVarScope scope = ref.getScope();
            if (!_scope.contains(scope)) {
                refs.add(ref);
            }
        }
    }
    public Collection<DFRef> getOutputRefs() {
        ConsistentHashSet<DFRef> refs = new ConsistentHashSet<DFRef>();
        this.listOutputRefs(refs);
        return refs;
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

        } else if (stmt instanceof EmptyStatement) {

        } else if (stmt instanceof VariableDeclarationStatement) {
            // "int a = 2;"
            VariableDeclarationStatement varStmt =
                (VariableDeclarationStatement)stmt;
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>) varStmt.fragments()) {
                try {
                    DFRef ref = _scope.lookupVar(frag.getName());
                    this.addOutputRef(ref);
                } catch (VariableNotFound e) {
                    Logger.error(
                        "DFFrame.buildStmt: VariableNotFound (decl)",
                        Utils.getASTSource(frag.getName()), this);
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
            Statement elseStmt = ifStmt.getElseStatement();
            if (elseStmt != null) {
                DFFrame elseFrame = ifFrame.addChild("@ELSE", elseStmt, _scope);
                elseFrame.buildStmt(elseStmt);
            }

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
            }
            DFLocalScope switchScope = _scope.getChildByAST(stmt);
            DFFrame switchFrame = this.addChild(DFFrame.BREAKABLE, stmt, switchScope);
            DFFrame caseFrame = null;
            for (Statement cstmt : (List<Statement>) switchStmt.statements()) {
                if (cstmt instanceof SwitchCase) {
                    caseFrame = switchFrame.addChild("@CASE", cstmt, switchScope);
                    SwitchCase switchCase = (SwitchCase)cstmt;
                    Expression expr = switchCase.getExpression();
                    if (expr != null) {
                        if (enumKlass != null && expr instanceof SimpleName) {
                            // special treatment for enum.
                            DFRef ref = enumKlass.getField((SimpleName)expr);
                            if (ref != null) {
                                this.addInputRef(ref);
                            } else {
                                Logger.error(
                                    "DFFrame.buildStmt: VariableNotFound (switch)",
                                    Utils.getASTSource(expr), this);
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

        } else if (stmt instanceof DoStatement) {
            // "do { ... } while (c);"
            DoStatement doStmt = (DoStatement)stmt;
            DFLocalScope innerScope = _scope.getChildByAST(stmt);
            DFFrame innerFrame = this.addChild(DFFrame.BREAKABLE, stmt, innerScope);
            innerFrame.buildStmt(doStmt.getBody());
            this.buildExpr(doStmt.getExpression());

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

        } else if (stmt instanceof EnhancedForStatement) {
            // "for (x : array) { ... }"
            EnhancedForStatement eForStmt = (EnhancedForStatement)stmt;
            this.buildExpr(eForStmt.getExpression());
            DFLocalScope innerScope = _scope.getChildByAST(stmt);
            DFFrame innerFrame = this.addChild(DFFrame.BREAKABLE, stmt, innerScope);
            innerFrame.buildStmt(eForStmt.getBody());

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

        } else if (stmt instanceof SynchronizedStatement) {
            // "synchronized (this) { ... }"
            SynchronizedStatement syncStmt = (SynchronizedStatement)stmt;
            this.buildExpr(syncStmt.getExpression());
            this.buildStmt(syncStmt.getBody());

        } else if (stmt instanceof TryStatement) {
            // "try { ... } catch (e) { ... }"
            TryStatement tryStmt = (TryStatement)stmt;
            List<CatchClause> catches = (List<CatchClause>) tryStmt.catchClauses();
            DFLocalScope tryScope = _scope.getChildByAST(tryStmt);
            DFFrame tryFrame = this.addChild("@TRY", tryStmt, tryScope);
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
                        Utils.getASTSource(decl.getType()), this);
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
            this.addInputRef(ref);
            DFKlass klass = ref.getRefType().toKlass();
            int nargs = ci.arguments().size();
            DFType[] argTypes = new DFType[nargs];
            for (int i = 0; i < nargs; i++) {
                Expression arg = (Expression)ci.arguments().get(i);
                DFType type = this.buildExpr(arg);
                if (type == null) {
                    Logger.error(
                        "DFFrame.buildExpr: Type unknown (ci)",
                        Utils.getASTSource(arg), this);
                    return;
                }
                argTypes[i] = type;
            }
            DFMethod method1 = klass.findMethod(
                DFMethod.CallStyle.Constructor, (String)null, argTypes);
            if (method1 != null) {
                for (DFMethod m : method1.getOverriders()) {
                    if (m instanceof DFSourceMethod) {
                        DFSourceMethod srcm = (DFSourceMethod)m;
                        if (srcm.isTransparent()) {
                            this.addInputRefs(srcm.getInputRefs());
                            this.addOutputRefs(srcm.getOutputRefs());
                        }
                    }
                }
            } else {
                Logger.error(
                    "DFFrame.buildExpr: MethodNotFound (ci)",
                    Utils.getASTSource(ci), this);
            }

        } else if (stmt instanceof SuperConstructorInvocation) {
            // "super(args)"
            SuperConstructorInvocation sci = (SuperConstructorInvocation)stmt;
            DFRef ref = _scope.lookupThis();
            this.addInputRef(ref);
            DFKlass klass = ref.getRefType().toKlass();
            DFKlass baseKlass = klass.getBaseKlass();
            int nargs = sci.arguments().size();
            DFType[] argTypes = new DFType[nargs];
            for (int i = 0; i < nargs; i++) {
                Expression arg = (Expression)sci.arguments().get(i);
                DFType type = this.buildExpr(arg);
                if (type == null) {
                    Logger.error(
                        "DFFrame.buildExpr: Type unknown (sci)",
                        Utils.getASTSource(arg), this);
                    return;
                }
                argTypes[i] = type;
            }
            DFMethod method1 = baseKlass.findMethod(
                DFMethod.CallStyle.Constructor, (String)null, argTypes);
            if (method1 != null) {
                if (method1 instanceof DFSourceMethod) {
                    DFSourceMethod srcmethod = (DFSourceMethod)method1;
                    if (srcmethod.isTransparent()) {
                        this.addInputRefs(srcmethod.getInputRefs());
                        this.addOutputRefs(srcmethod.getOutputRefs());
                    }
                }
            } else {
                Logger.error(
                    "DFFrame.buildExpr: MethodNotFound (sci)",
                    Utils.getASTSource(sci), this);
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
            if (name.isSimpleName()) {
                try {
                    ref = _scope.lookupVar((SimpleName)name);
                } catch (VariableNotFound e) {
                    // Do not display an error message as this could be
                    // recursively called from another buildExpr()
                    //Logger.error(
                    //    "DFFrame.buildExpr: VariableNotFound (name)",
                    //    this, e.name, name);
                    return null;
                }
            } else {
                QualifiedName qname = (QualifiedName)name;
                // Try assuming it's a variable access.
                DFType type = this.buildExpr(qname.getQualifier());
                if (type == null) {
                    // Turned out it's a class variable.
                    try {
                        type = _finder.resolveKlass(qname.getQualifier());
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
                SimpleName fieldName = qname.getName();
                ref = klass.getField(fieldName);
                if (ref == null) return null;
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
                    DFKlass klass = _finder.resolveKlass(name);
                    ref = klass.getKlassScope().lookupThis();
                } catch (TypeNotFound e) {
                    Logger.error(
                        "DFFrame.buildExpr: TypeNotFound (this)",
                        Utils.getASTSource(name), this);
                    return null;
                }
            } else {
                ref = _scope.lookupThis();
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
            // returns Class<A>.
            Type value = ((TypeLiteral)expr).getType();
            try {
                DFKlass typeval = _finder.resolve(value).toKlass();
                DFKlass klass = DFBuiltinTypes.getClassKlass();
                return klass.getReifiedKlass(new DFKlass[] { typeval });
            } catch (TypeNotFound e) {
                Logger.error(
                    "DFFrame.buildExpr: TypeNotFound (const)",
                    Utils.getASTSource(value), this);
                return null;
            }

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
                    this.addOutputRef(ref);
                    Expression init = frag.getInitializer();
                    if (init != null) {
                        this.buildExpr(init);
                    }
                } catch (VariableNotFound e) {
                    Logger.error(
                        "DFFrame.buildExpr: VariableNotFound (decl)",
                        Utils.getASTSource(frag.getName()), this);
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
                this.addInputRef(ref);
                klass = ref.getRefType().toKlass();
                callStyle = DFMethod.CallStyle.InstanceOrStatic;
            } else {
                callStyle = DFMethod.CallStyle.InstanceMethod;
                if (expr1 instanceof Name) {
                    // "ClassName.method()"
                    try {
                        klass = _finder.resolveKlass((Name)expr1);
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
                            Utils.getASTSource(expr1), this);
                        return null;
                    }
                    klass = type.toKlass();
                }
            }
            int nargs = invoke.arguments().size();
            DFType[] argTypes = new DFType[nargs];
            for (int i = 0; i < nargs; i++) {
                Expression arg = (Expression)invoke.arguments().get(i);
                DFType type = this.buildExpr(arg);
                if (type == null) {
                    Logger.error(
                        "DFFrame.buildExpr: Type unknown (invoke)",
                        Utils.getASTSource(arg), this);
                    return null;
                }
                argTypes[i] = type;
            }
            DFMethod method1 = klass.findMethod(
                callStyle, invoke.getName(), argTypes);
            if (method1 != null) {
                for (DFMethod m : method1.getOverriders()) {
                    if (m instanceof DFSourceMethod) {
                        DFSourceMethod srcm = (DFSourceMethod)m;
                        if (srcm.isTransparent()) {
                            this.addInputRefs(srcm.getInputRefs());
                            this.addOutputRefs(srcm.getOutputRefs());
                        }
                    }
                }
                return method1.getFuncType().getReturnType();
            } else {
                Logger.error(
                    "DFFrame.buildExpr: MethodNotFound (invoke)",
                    Utils.getASTSource(invoke), this);
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
                        Utils.getASTSource(arg), this);
                    return null;
                }
                argTypes[i] = type;
            }
            DFRef ref = _scope.lookupThis();
            this.addInputRef(ref);
            DFKlass klass = ref.getRefType().toKlass();
            DFKlass baseKlass = klass.getBaseKlass();
            DFMethod method1 = baseKlass.findMethod(
                DFMethod.CallStyle.InstanceMethod, sinvoke.getName(), argTypes);
            if (method1 != null) {
                if (method1 instanceof DFSourceMethod) {
                    DFSourceMethod srcmethod = (DFSourceMethod)method1;
                    if (srcmethod.isTransparent()) {
                        this.addInputRefs(srcmethod.getInputRefs());
                        this.addOutputRefs(srcmethod.getOutputRefs());
                    }
                }
                return method1.getFuncType().getReturnType();
            } else {
                Logger.error(
                    "DFFrame.buildExpr: MethodNotFound (sinvoke)",
                    Utils.getASTSource(sinvoke), this);
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
                return _finder.resolve(ac.getType());
            } catch (TypeNotFound e) {
                Logger.error(
                    "DFFrame.buildExpr: TypeNotFound (array)",
                    Utils.getASTSource(ac.getType()), this);
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
                    type = _finder.resolveKlass((Name)expr1);
                } catch (TypeNotFound e) {
                }
            }
            if (type == null) {
                type = this.buildExpr(expr1);
                if (type == null) {
                    Logger.error(
                        "DFFrame.buildExpr: Type unknown (fieldaccess)",
                        Utils.getASTSource(expr1), this);
                    return null;
                }
            }
            DFKlass klass = type.toKlass();
            SimpleName fieldName = fa.getName();
            DFRef ref = klass.getField(fieldName);
            if (ref != null) {
                this.addInputRef(ref);
                return ref.getRefType();
            } else {
                Logger.error(
                    "DFFrame.buildExpr: VariableNotFound (fieldref)",
                    Utils.getASTSource(fieldName), this);
                return null;
            }

        } else if (expr instanceof SuperFieldAccess) {
            // "super.baa"
            SuperFieldAccess sfa = (SuperFieldAccess)expr;
            SimpleName fieldName = sfa.getName();
            DFRef ref = _scope.lookupThis();
            this.addInputRef(ref);
            DFKlass klass = ref.getRefType().toKlass().getBaseKlass();
            DFRef ref2 = klass.getField(fieldName);
            if (ref2 != null) {
                this.addInputRef(ref2);
                return ref2.getRefType();
            } else {
                Logger.error(
                    "DFFrame.buildExpr: VariableNotFound (superfieldref)",
                    Utils.getASTSource(fieldName), this);
                return null;
            }

        } else if (expr instanceof CastExpression) {
            // "(String)"
            CastExpression cast = (CastExpression)expr;
            this.buildExpr(cast.getExpression());
            try {
                return _finder.resolve(cast.getType());
            } catch (TypeNotFound e) {
                Logger.error(
                    "DFFrame.buildExpr: TypeNotFound (cast)",
                    Utils.getASTSource(cast.getType()), this);
                return null;
            }

        } else if (expr instanceof ClassInstanceCreation) {
            // "new T()"
            ClassInstanceCreation cstr = (ClassInstanceCreation)expr;
            DFKlass instKlass;
            if (cstr.getAnonymousClassDeclaration() != null) {
                String id = Utils.encodeASTNode(cstr);
                try {
                    instKlass = _finder.resolveKlass(id);
                } catch (TypeNotFound e) {
                    Logger.error(
                        "DFFrame.buildExpr: Type unknown (anondecl)",
                        Utils.getASTSource(cstr), this);
                    return null;
                }
            } else {
                try {
                    instKlass = _finder.resolve(cstr.getType()).toKlass();
                } catch (TypeNotFound e) {
                    Logger.error(
                        "DFFrame.buildExpr: TypeNotFound (new)",
                        Utils.getASTSource(cstr.getType()), this);
                    return null;
                }
            }
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
                        Utils.getASTSource(arg), this);
                    return null;
                }
                argTypes[i] = type;
            }
            DFMethod method1 = instKlass.findMethod(
                DFMethod.CallStyle.Constructor, (String)null, argTypes);
            if (method1 != null) {
                for (DFMethod m : method1.getOverriders()) {
                    if (m instanceof DFSourceMethod) {
                        DFSourceMethod srcm = (DFSourceMethod)m;
                        if (srcm.isTransparent()) {
                            this.addInputRefs(srcm.getInputRefs());
                            this.addOutputRefs(srcm.getOutputRefs());
                        }
                    }
                }
            } else {
                Logger.error(
                    "DFFrame.buildExpr: MethodNotFound (cstr)",
                    Utils.getASTSource(cstr), this);
            }
            return instKlass;

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
                DFKlass lambdaKlass = _finder.resolveKlass(id);
                assert lambdaKlass instanceof DFLambdaKlass;
                for (DFLambdaKlass.CapturedRef captured :
                         ((DFLambdaKlass)lambdaKlass).getCapturedRefs()) {
                    this.addInputRef(captured.getOriginal());
                }
                return lambdaKlass;
            } catch (TypeNotFound e) {
                Logger.error(
                    "DFFrame.buildExpr: TypeNotFound (lambda)",
                    Utils.getASTSource(lambda), this);
                return null;
            }

        } else if (expr instanceof ExpressionMethodReference) {
            ExpressionMethodReference methodref = (ExpressionMethodReference)expr;
            this.buildExpr(methodref.getExpression());
            String id = Utils.encodeASTNode(methodref);
            try {
                DFKlass methodRefKlass = _finder.resolveKlass(id);
                assert methodRefKlass instanceof DFMethodRefKlass;
                return methodRefKlass;
            } catch (TypeNotFound e) {
                Logger.error(
                    "DFFrame.buildExpr: TypeNotFound (methodref)",
                    Utils.getASTSource(methodref), this);
                return null;
            }

        } else if (expr instanceof MethodReference) {
            MethodReference methodref = (MethodReference)expr;
            //  CreationReference
            //  SuperMethodReference
            //  TypeMethodReference
            String id = Utils.encodeASTNode(methodref);
            try {
                DFKlass methodRefKlass = _finder.resolveKlass(id);
                assert methodRefKlass instanceof DFMethodRefKlass;
                return methodRefKlass;
            } catch (TypeNotFound e) {
                Logger.error(
                    "DFFrame.buildExpr: TypeNotFound (methodref)",
                    Utils.getASTSource(methodref), this);
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
            if (name.isSimpleName()) {
                try {
                    ref = _scope.lookupVar((SimpleName)name);
                } catch (VariableNotFound e) {
                    Logger.error(
                        "DFFrame.buildAssignment: VariableNotFound (name)",
                        Utils.getASTSource(name), this);
                    return null;
                }
            } else {
                QualifiedName qname = (QualifiedName)name;
                // Try assuming it's a variable access.
                DFType type = this.buildExpr(qname.getQualifier());
                if (type == null) {
                    // Turned out it's a class variable.
                    try {
                        type = _finder.resolveKlass(qname.getQualifier());
                    } catch (TypeNotFound e) {
                        Logger.error(
                            "DFFrame.buildAssignment: VariableNotFound (name)",
                            Utils.getASTSource(qname.getQualifier()), this);
                        return null;
                    }
                }
                this.addInputRef(_scope.lookupThis());
                DFKlass klass = type.toKlass();
                SimpleName fieldName = qname.getName();
                ref = klass.getField(fieldName);
                if (ref == null) {
                    Logger.error(
                        "DFFrame.buildAssignment: VariableNotFound (name)",
                        Utils.getASTSource(fieldName), this);
                    return null;
                }
            }
            this.addOutputRef(ref);
            return ref;

        } else if (expr instanceof ArrayAccess) {
            // "a[0]"
            ArrayAccess aa = (ArrayAccess)expr;
            DFType type = this.buildExpr(aa.getArray());
            this.buildExpr(aa.getIndex());
            if (type instanceof DFArrayType) {
                DFRef ref = _scope.lookupArray(type);
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
                    type = _finder.resolveKlass((Name)expr1);
                } catch (TypeNotFound e) {
                }
            }
            if (type == null) {
                type = this.buildExpr(expr1);
                if (type == null) return null;
            }
            DFKlass klass = type.toKlass();
            SimpleName fieldName = fa.getName();
            DFRef ref = klass.getField(fieldName);
            if (ref != null) {
                this.addOutputRef(ref);
                return ref;
            } else {
                Logger.error(
                    "DFFrame.buildAssigmnent: VariableNotFound (fieldassign)",
                    Utils.getASTSource(fieldName), this);
                return null;
            }

        } else if (expr instanceof SuperFieldAccess) {
            // "super.baa"
            SuperFieldAccess sfa = (SuperFieldAccess)expr;
            SimpleName fieldName = sfa.getName();
            DFRef ref = _scope.lookupThis();
            this.addInputRef(ref);
            DFKlass klass = ref.getRefType().toKlass().getBaseKlass();
            DFRef ref2 = klass.getField(fieldName);
            if (ref2 != null) {
                this.addOutputRef(ref2);
                return ref2;
            } else {
                Logger.error(
                    "DFFrame.buildAssigmnent: VariableNotFound (superfieldassign)",
                    Utils.getASTSource(fieldName), this);
                return null;
            }

        } else if (expr instanceof ParenthesizedExpression) {
            ParenthesizedExpression paren = (ParenthesizedExpression)expr;
            return this.buildAssignment(paren.getExpression());

        } else {
            throw new InvalidSyntax(expr);
        }
    }

    private void addInputRefs(Collection<DFRef> refs) {
        _inputRefs.addAll(refs);
    }

    private void addOutputRefs(Collection<DFRef> refs) {
        _outputRefs.addAll(refs);
    }

    private void addInputRef(DFRef ref) {
        _inputRefs.add(ref);
    }

    private void addOutputRef(DFRef ref) {
        _outputRefs.add(ref);
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
        for (DFFrame frame : _children) {
            frame.dump(out, i2);
        }
        out.println(indent+"}");
    }
}
