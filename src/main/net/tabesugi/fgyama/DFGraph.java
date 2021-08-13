//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import javax.xml.stream.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFGraph
//
public abstract class DFGraph {

    public abstract String getGraphId();

    /// General graph operations.

    private DFSourceMethod _method;
    private DFTypeFinder _finder;
    private List<DFNode> _nodes =
        new ArrayList<DFNode>();
    private DFNode _passInNode = null;
    private DFNode _passOutNode = null;

    public DFGraph(DFSourceMethod method) {
        _method = method;
        _finder = method.getFinder();
    }

    @Override
    public String toString() {
        return "<DFGraph ("+_method+")>";
    }

    public int addNode(DFNode node) {
        _nodes.add(node);
        return _nodes.size();
    }

    public DFNode createArgNode(DFRef ref_v, DFRef ref_a, ASTNode ast) {
        DFVarScope scope = _method.getScope();
        DFNode input = new InputNode(this, scope, ref_v, ast);
        DFNode assign = new VarAssignNode(this, scope, ref_a, ast);
        assign.accept(input);
        return assign;
    }

    private void cleanup() {
        Set<DFNode> toremove = new HashSet<DFNode>();
        while (true) {
            boolean changed = false;
            for (DFNode node : _nodes) {
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
    }

    private DFNode getPassInNode() {
        if (_passInNode == null) {
            _passInNode = new PassInNode(this, _method.getScope());
        }
        return _passInNode;
    }

    private DFNode getPassOutNode() {
        if (_passOutNode == null) {
            _passOutNode = new PassOutNode(this, _method.getScope());
        }
        return _passOutNode;
    }

    public void writeXML(XMLStreamWriter writer)
        throws XMLStreamException {
        DFNode[] nodes = new DFNode[_nodes.size()];
        _nodes.toArray(nodes);
        Arrays.sort(nodes);
        _method.getScope().writeXML(writer, nodes);
    }

    /**
     * Creates a value node.
     */
    @SuppressWarnings("unchecked")
    private void processStatement(
        DFContext ctx, DFLocalScope scope, DFFrame frame,
        Statement stmt)
        throws InvalidSyntax, EntityNotFound {
        assert stmt != null;

        if (stmt instanceof AssertStatement) {
            // "assert x;"

        } else if (stmt instanceof Block) {
            // "{ ... }"
            processBlock(ctx, scope, frame, (Block)stmt);

        } else if (stmt instanceof EmptyStatement) {

        } else if (stmt instanceof VariableDeclarationStatement) {
            // "int a = 2;"
            processVariableDeclarationStatement(
                ctx, scope, frame, (VariableDeclarationStatement)stmt);

        } else if (stmt instanceof ExpressionStatement) {
            // "foo();"
            processExpressionStatement(
                ctx, scope, frame, (ExpressionStatement)stmt);

        } else if (stmt instanceof IfStatement) {
            // "if (c) { ... } else { ... }"
            processIfStatement(
                ctx, scope, frame, (IfStatement)stmt);

        } else if (stmt instanceof SwitchStatement) {
            // "switch (x) { case 0: ...; }"
            processSwitchStatement(
                ctx, scope, frame, (SwitchStatement)stmt);

        } else if (stmt instanceof SwitchCase) {
            // Invalid "case" placement.
            throw new InvalidSyntax(stmt);

        } else if (stmt instanceof WhileStatement) {
            // "while (c) { ... }"
            processWhileStatement(
                ctx, scope, frame, (WhileStatement)stmt);

        } else if (stmt instanceof DoStatement) {
            // "do { ... } while (c);"
            processDoStatement(
                ctx, scope, frame, (DoStatement)stmt);

        } else if (stmt instanceof ForStatement) {
            // "for (i = 0; i < 10; i++) { ... }"
            processForStatement(
                ctx, scope, frame, (ForStatement)stmt);

        } else if (stmt instanceof EnhancedForStatement) {
            // "for (x : array) { ... }"
            processEnhancedForStatement(
                ctx, scope, frame, (EnhancedForStatement)stmt);

        } else if (stmt instanceof ReturnStatement) {
            // "return 42;"
            ReturnStatement rtrnStmt = (ReturnStatement)stmt;
            DFFrame dstFrame = frame.find(DFFrame.RETURNABLE);
            assert dstFrame != null;
            Expression expr = rtrnStmt.getExpression();
            if (expr != null) {
                DFRef ref = scope.lookupReturn();
                ReturnNode ret = new ReturnNode(this, scope, ref, rtrnStmt);
                ret.accept(processExpression(ctx, scope, frame, expr, ref.getRefType()));
                frame.addExit(new ReturnExit(dstFrame, ret));
            }
            for (DFRef ref : dstFrame.getOutputRefs()) {
                frame.addExit(new ReturnExit(dstFrame, ctx.get(ref)));
            }

        } else if (stmt instanceof BreakStatement) {
            // "break;"
            BreakStatement breakStmt = (BreakStatement)stmt;
            SimpleName labelName = breakStmt.getLabel();
            String dstLabel = (labelName != null)?
                labelName.getIdentifier() : DFFrame.BREAKABLE;
            DFFrame dstFrame = frame.find(dstLabel);
            for (DFRef ref : dstFrame.getOutputRefs()) {
                frame.addExit(new BreakExit(dstFrame, ctx.get(ref)));
            }

        } else if (stmt instanceof ContinueStatement) {
            // "continue;"
            ContinueStatement contStmt = (ContinueStatement)stmt;
            SimpleName labelName = contStmt.getLabel();
            String dstLabel = (labelName != null)?
                labelName.getIdentifier() : DFFrame.BREAKABLE;
            DFFrame dstFrame = frame.find(dstLabel);
            for (DFRef ref : dstFrame.getOutputRefs()) {
                frame.addExit(new ContinueExit(dstFrame, ctx.get(ref)));
            }

        } else if (stmt instanceof LabeledStatement) {
            // "here:"
            LabeledStatement labeledStmt = (LabeledStatement)stmt;
            DFFrame labeledFrame = frame.getChildByAST(labeledStmt);
            processStatement(
                ctx, scope, labeledFrame, labeledStmt.getBody());
            this.endBreaks(ctx, frame, labeledFrame);

        } else if (stmt instanceof SynchronizedStatement) {
            // "synchronized (this) { ... }"
            SynchronizedStatement syncStmt = (SynchronizedStatement)stmt;
            processExpression(
                ctx, scope, frame, syncStmt.getExpression());
            processStatement(
                ctx, scope, frame, syncStmt.getBody());

        } else if (stmt instanceof TryStatement) {
            // "try { ... } catch (e) { ... }"
            processTryStatement(
                ctx, scope, frame, (TryStatement)stmt);

        } else if (stmt instanceof ThrowStatement) {
            // "throw e;"
            ThrowStatement throwStmt = (ThrowStatement)stmt;
            DFNode exc = processExpression(
                ctx, scope, frame, throwStmt.getExpression());
            DFKlass excKlass = exc.getNodeType().toKlass();
            DFRef excRef = scope.lookupException(excKlass);
            ThrowNode thrown = new ThrowNode(this, scope, excRef, stmt);
            thrown.accept(exc);
            // Find out the catch clause. If not, the entire method throws.
            DFFrame dstFrame = frame.find(excKlass);
            if (dstFrame == null) {
                dstFrame = frame.find(DFFrame.RETURNABLE);
                assert dstFrame != null;
            }
            frame.addExit(new ThrowExit(dstFrame, thrown, excKlass));
            for (DFRef ref : dstFrame.getOutputRefs()) {
                frame.addExit(new ThrowExit(dstFrame, ctx.get(ref), excKlass));
            }

        } else if (stmt instanceof ConstructorInvocation) {
            // "this(args)"
            ConstructorInvocation ci = (ConstructorInvocation)stmt;
            DFKlass klass = _method.klass();
            DFNode obj = ctx.get(klass.getThisRef());
            int nargs = ci.arguments().size();
            DFNode[] args = new DFNode[nargs];
            DFType[] argTypes = new DFType[nargs];
            for (int i = 0; i < nargs; i++) {
                Expression arg = (Expression)ci.arguments().get(i);
                DFNode node = processExpression(ctx, scope, frame, arg);
                args[i] = node;
                argTypes[i] = node.getNodeType();
            }
            DFMethod constructor = klass.lookupMethod(
                DFMethod.CallStyle.Constructor, (String)null, argTypes, null);
            DFMethod[] methods = new DFMethod[] { constructor };
            DFFuncType funcType = constructor.getFuncType();
            MethodCallNode call = new MethodCallNode(
                this, scope, ci, funcType, methods);
            call.setArgs(args);
            this.connectMethodRefs(ctx, scope, call, obj, methods);
            this.catchExceptions(scope, frame, call, funcType.getExceptions());

        } else if (stmt instanceof SuperConstructorInvocation) {
            // "super(args)"
            SuperConstructorInvocation sci = (SuperConstructorInvocation)stmt;
            DFKlass klass = _method.klass();
            DFNode obj = ctx.get(klass.getThisRef());
            int nargs = sci.arguments().size();
            DFNode[] args = new DFNode[nargs];
            DFType[] argTypes = new DFType[nargs];
            for (int i = 0; i < nargs; i++) {
                Expression arg = (Expression)sci.arguments().get(i);
                DFNode node = processExpression(ctx, scope, frame, arg);
                args[i] = node;
                argTypes[i] = node.getNodeType();
            }
            DFKlass baseKlass = klass.getBaseKlass();
            assert baseKlass != null;
            DFMethod constructor = baseKlass.lookupMethod(
                DFMethod.CallStyle.Constructor, (String)null, argTypes, null);
            DFMethod[] methods = new DFMethod[] { constructor };
            DFFuncType funcType = constructor.getFuncType();
            MethodCallNode call = new MethodCallNode(
                this, scope, sci, funcType, methods);
            call.setArgs(args);
            this.connectMethodRefs(ctx, scope, call, obj, methods);
            this.catchExceptions(scope, frame, call, funcType.getExceptions());

        } else if (stmt instanceof TypeDeclarationStatement) {
            // "class K { ... }"
            // Inline classes are processed separately.

        } else {
            throw new InvalidSyntax(stmt);
        }
    }

    private DFNode processExpression(
        DFContext ctx, DFLocalScope scope, DFFrame frame,
        Expression expr)
        throws InvalidSyntax, EntityNotFound {
        return processExpression(ctx, scope, frame, expr, null);
    }

    @SuppressWarnings("unchecked")
    private DFNode processExpression(
        DFContext ctx, DFLocalScope scope, DFFrame frame,
        Expression expr, DFType expected)
        throws InvalidSyntax, EntityNotFound {
        assert expr != null;
        // expected can be null.

        try {
            if (expr instanceof Annotation) {
                // "@Annotation"
                return null;

            } else if (expr instanceof Name) {
                // "a.b"
                Name name = (Name)expr;
                if (name.isSimpleName()) {
                    DFRef ref = scope.lookupVar((SimpleName)name);
                    DFNode node;
                    if (ref instanceof DFKlass.FieldRef) {
                        DFKlass klass = _method.klass();
                        DFNode obj = ctx.get(klass.getThisRef());
                        node = new FieldRefNode(this, scope, ref, expr, obj);
                    } else {
                        node = new VarRefNode(this, scope, ref, expr);
                    }
                    node.accept(ctx.get(ref));
                    return node;

                } else {
                    QualifiedName qname = (QualifiedName)name;
                    DFNode obj = null;
                    DFKlass klass;
                    try {
                        // Try assuming it's a variable access.
                        obj = processExpression(
                            ctx, scope, frame, qname.getQualifier());
                        klass = obj.getNodeType().toKlass();
                    } catch (EntityNotFound e) {
                        // Turned out it's a class variable.
                        klass = _finder.resolveKlass(qname.getQualifier());
                    }
                    SimpleName fieldName = qname.getName();
                    DFRef ref = klass.getField(fieldName);
                    if (ref == null) throw new VariableNotFound("."+fieldName);
                    DFNode node = new FieldRefNode(this, scope, ref, qname, obj);
                    node.accept(ctx.get(ref));
                    return node;
                }

            } else if (expr instanceof ThisExpression) {
                // "this"
                ThisExpression thisExpr = (ThisExpression)expr;
                Name name = thisExpr.getQualifier();
                DFKlass klass;
                if (name != null) {
                    klass = _finder.resolveKlass(name);
                } else {
                    klass = _method.klass();
                }
                DFRef ref = klass.getThisRef();
                DFNode node = new VarRefNode(this, scope, ref, expr);
                node.accept(ctx.get(ref));
                return node;

            } else if (expr instanceof BooleanLiteral) {
                // "true", "false"
                boolean value = ((BooleanLiteral)expr).booleanValue();
                return new ConstNode(
                    this, scope, DFBasicType.BOOLEAN,
                    expr, Boolean.toString(value));

            } else if (expr instanceof CharacterLiteral) {
                // "'c'"
                char value = ((CharacterLiteral)expr).charValue();
                return new ConstNode(
                    this, scope, DFBasicType.CHAR,
                    expr, Utils.quote(value));

            } else if (expr instanceof NullLiteral) {
                // "null"
                return new ConstNode(
                    this, scope, DFNullType.NULL,
                    expr, "null");

            } else if (expr instanceof NumberLiteral) {
                // "42"
                String value = ((NumberLiteral)expr).getToken();
                return new ConstNode(
                    this, scope, DFBasicType.INT,
                    expr, value);

            } else if (expr instanceof StringLiteral) {
                // ""abc""
                String value = ((StringLiteral)expr).getLiteralValue();
                return new ConstNode(
                    this, scope,
                    DFBuiltinTypes.getStringKlass(),
                    expr, Utils.quote(value));

            } else if (expr instanceof TypeLiteral) {
                // "A.class"
                // returns Class<A>.
                Type value = ((TypeLiteral)expr).getType();
                DFKlass typeval = _finder.resolve(value).toKlass();
                DFKlass klass = DFBuiltinTypes.getClassKlass();
                return new ConstNode(
                    this, scope,
                    klass.getReifiedKlass(new DFKlass[] { typeval }),
                    expr, Utils.getTypeName(value));

            } else if (expr instanceof PrefixExpression) {
                PrefixExpression prefix = (PrefixExpression)expr;
                PrefixExpression.Operator op = prefix.getOperator();
                Expression operand = prefix.getOperand();
                if (op == PrefixExpression.Operator.INCREMENT ||
                    op == PrefixExpression.Operator.DECREMENT) {
                    // "++x"
                    DFNode assign = processAssignment(ctx, scope, frame, operand);
                    DFRef ref = assign.getRef();
                    DFNode node = new PrefixNode(
                        this, scope, ref.getRefType(), ref, expr, op);
                    node.accept(processExpression(ctx, scope, frame, operand));
                    assign.accept(node);
                    ctx.set(assign);
                    return node;

                } else {
                    // "!a", "+a", "-a", "~a"
                    DFNode value = processExpression(ctx, scope, frame, operand);
                    DFType type2 = DFNode.inferPrefixType(value.getNodeType(), op);
                    DFNode node = new PrefixNode(
                        this, scope, type2, null, expr, op);
                    node.accept(value);
                    return node;
                }

            } else if (expr instanceof PostfixExpression) {
                // "y--"
                PostfixExpression postfix = (PostfixExpression)expr;
                PostfixExpression.Operator op = postfix.getOperator();
                Expression operand = postfix.getOperand();
                assert (op == PostfixExpression.Operator.INCREMENT ||
                        op == PostfixExpression.Operator.DECREMENT);
                DFNode assign = processAssignment(ctx, scope, frame, operand);
                DFNode node = new PostfixNode(
                    this, scope, assign.getRef(), expr, op);
                node.accept(processExpression(ctx, scope, frame, operand));
                assign.accept(node);
                ctx.set(assign);
                return node;

            } else if (expr instanceof InfixExpression) {
                // "a+b"
                InfixExpression infix = (InfixExpression)expr;
                InfixExpression.Operator op = infix.getOperator();
                DFNode lvalue = processExpression(
                    ctx, scope, frame, infix.getLeftOperand());
                DFNode rvalue = processExpression(
                    ctx, scope, frame, infix.getRightOperand());
                DFType type2 = DFNode.inferInfixType(
                    lvalue.getNodeType(), op, rvalue.getNodeType());
                return new InfixNode(
                    this, scope, type2, expr, op, lvalue, rvalue);

            } else if (expr instanceof ParenthesizedExpression) {
                // "(expr)"
                ParenthesizedExpression paren = (ParenthesizedExpression)expr;
                return processExpression(
                    ctx, scope, frame, paren.getExpression());

            } else if (expr instanceof Assignment) {
                // "p = q"
                Assignment assn = (Assignment)expr;
                Assignment.Operator op = assn.getOperator();
                DFNode assign = processAssignment(
                    ctx, scope, frame, assn.getLeftHandSide());
                DFNode rvalue = processExpression(
                    ctx, scope, frame, assn.getRightHandSide(), assign.getNodeType());
                DFNode lvalue = null;
                if (op != Assignment.Operator.ASSIGN) {
                    lvalue = ctx.get(assign.getRef());
                }
                assign.accept(new AssignOpNode(
                                  this, scope, assign.getRef(), assn,
                                  op, lvalue, rvalue));
                ctx.set(assign);
                return assign;

            } else if (expr instanceof VariableDeclarationExpression) {
                // "int a=2"
                VariableDeclarationExpression decl =
                    (VariableDeclarationExpression)expr;
                return processVariableDeclaration(
                    ctx, scope, frame, decl.fragments());

            } else if (expr instanceof MethodInvocation) {
                MethodInvocation invoke = (MethodInvocation)expr;
                Expression expr1 = invoke.getExpression();
                DFMethod.CallStyle callStyle;
                DFNode obj = null;
                DFKlass instKlass = null;
                if (expr1 == null) {
                    // "method()"
                    instKlass = _method.klass();
                    obj = ctx.get(instKlass.getThisRef());
                    callStyle = DFMethod.CallStyle.InstanceOrStatic;
                } else {
                    callStyle = DFMethod.CallStyle.InstanceMethod;
                    if (expr1 instanceof Name) {
                        // "ClassName.method()"
                        try {
                            instKlass = _finder.resolveKlass((Name)expr1);
                            callStyle = DFMethod.CallStyle.StaticMethod;
                        } catch (TypeNotFound e) {
                        }
                    }
                    if (instKlass == null) {
                        // "expr.method()"
                        obj = processExpression(ctx, scope, frame, expr1);
                        instKlass = obj.getNodeType().toKlass();
                    }
                }
                assert instKlass != null;
                int nargs = invoke.arguments().size();
                DFNode[] args = new DFNode[nargs];
                DFType[] argTypes = new DFType[nargs];
                for (int i = 0; i < nargs; i++) {
                    Expression arg = (Expression)invoke.arguments().get(i);
                    DFNode node = processExpression(ctx, scope, frame, arg);
                    args[i] = node;
                    argTypes[i] = node.getNodeType();
                }
                // XXX ignored: invoke.typeArguments().
                DFMethod method;
                try {
                    method = instKlass.lookupMethod(
                        callStyle, invoke.getName(), argTypes, expected);
                } catch (MethodNotFound e) {
                    // try static imports.
                    method = scope.lookupStaticMethod(
                        invoke.getName(), argTypes, expected);
                }
                List<DFMethod> overriders = method.getOverriders();
                DFMethod[] methods = new DFMethod[overriders.size()];
                overriders.toArray(methods);
                DFFuncType funcType = method.getFuncType();
                MethodCallNode call = new MethodCallNode(
                    this, scope, invoke, funcType, methods);
                call.setArgs(args);
                this.connectMethodRefs(ctx, scope, call, obj, methods);
                this.catchExceptions(scope, frame, call, funcType.getExceptions());
                return new ReceiveNode(this, scope, call, invoke);

            } else if (expr instanceof SuperMethodInvocation) {
                // "super.method()"
                SuperMethodInvocation sinvoke = (SuperMethodInvocation)expr;
                DFKlass klass = _method.klass();
                DFNode obj = ctx.get(klass.getThisRef());
                int nargs = sinvoke.arguments().size();
                DFNode[] args = new DFNode[nargs];
                DFType[] argTypes = new DFType[nargs];
                for (int i = 0; i < nargs; i++) {
                    Expression arg = (Expression)sinvoke.arguments().get(i);
                    DFNode node = processExpression(ctx, scope, frame, arg);
                    args[i] = node;
                    argTypes[i] = node.getNodeType();
                }
                DFKlass baseKlass = klass.getBaseKlass();
                assert baseKlass != null;
                DFMethod method = baseKlass.lookupMethod(
                    DFMethod.CallStyle.InstanceMethod,
                    sinvoke.getName(), argTypes, expected);
                DFMethod[] methods = new DFMethod[] { method };
                DFFuncType funcType = method.getFuncType();
                MethodCallNode call = new MethodCallNode(
                    this, scope, sinvoke, funcType, methods);
                call.setArgs(args);
                this.connectMethodRefs(ctx, scope, call, obj, methods);
                this.catchExceptions(scope, frame, call, funcType.getExceptions());
                return new ReceiveNode(this, scope, call, sinvoke);

            } else if (expr instanceof ArrayCreation) {
                // "new int[10]"
                ArrayCreation ac = (ArrayCreation)expr;
                DFType arrayType = _finder.resolve(ac.getType());
                for (Expression dim : (List<Expression>) ac.dimensions()) {
                    // XXX value is not used (for now).
                    processExpression(ctx, scope, frame, dim);
                }
                ArrayInitializer init = ac.getInitializer();
                if (init != null) {
                    return processExpression(ctx, scope, frame, init, arrayType);
                } else {
                    return new ValueSetNode(this, scope, arrayType, expr);
                }

            } else if (expr instanceof ArrayInitializer) {
                // "{ 5, 9, 4, ... }"
                ArrayInitializer init = (ArrayInitializer)expr;
                DFType type = (expected != null)? expected : DFUnknownType.UNKNOWN;
                DFNode array = new ValueSetNode(this, scope, type, expr);
                DFType elemType = (expected instanceof DFArrayType)?
                    ((DFArrayType)expected).getElemType() : null;
                DFRef ref = scope.lookupArray(array.getNodeType());
                List<Expression> exprs = (List<Expression>) init.expressions();
                int i = 0;
                for (Expression expr1 : exprs) {
                    DFNode value = processExpression(ctx, scope, frame, expr1, elemType);
                    DFNode index = new ConstNode(
                        this, scope, DFBasicType.INT, null, Integer.toString(i++));
                    DFNode node = new ArrayAssignNode(
                        this, scope, ref, expr1, array, index);
                    node.accept(value);
                }
                return array;

            } else if (expr instanceof ArrayAccess) {
                // "a[0]"
                ArrayAccess aa = (ArrayAccess)expr;
                DFNode array = processExpression(
                    ctx, scope, frame, aa.getArray());
                DFNode index = processExpression(
                    ctx, scope, frame, aa.getIndex());
                DFRef ref = scope.lookupArray(array.getNodeType());
                DFNode node = new ArrayRefNode(
                    this, scope, ref, aa, array, index);
                node.accept(ctx.get(ref));
                return node;

            } else if (expr instanceof FieldAccess) {
                // "(expr).foo"
                FieldAccess fa = (FieldAccess)expr;
                Expression expr1 = fa.getExpression();
                DFNode obj = null;
                DFKlass instKlass = null;
                if (expr1 instanceof Name) {
                    try {
                        instKlass = _finder.resolveKlass((Name)expr1);
                    } catch (TypeNotFound e) {
                    }
                }
                if (instKlass == null) {
                    obj = processExpression(ctx, scope, frame, expr1);
                    instKlass = obj.getNodeType().toKlass();
                }
                SimpleName fieldName = fa.getName();
                DFRef ref = instKlass.getField(fieldName);
                if (ref == null) throw new VariableNotFound("."+fieldName);
                DFNode node = new FieldRefNode(this, scope, ref, fa, obj);
                node.accept(ctx.get(ref));
                return node;

            } else if (expr instanceof SuperFieldAccess) {
                // "super.baa"
                SuperFieldAccess sfa = (SuperFieldAccess)expr;
                SimpleName fieldName = sfa.getName();
                DFKlass klass = _method.klass();
                DFNode obj = ctx.get(klass.getThisRef());
                DFKlass baseKlass = klass.getBaseKlass();
                DFRef ref = baseKlass.getField(fieldName);
                if (ref == null) throw new VariableNotFound("."+fieldName);
                DFNode node = new FieldRefNode(this, scope, ref, sfa, obj);
                node.accept(ctx.get(ref));
                return node;

            } else if (expr instanceof CastExpression) {
                // "(String)"
                CastExpression cast = (CastExpression)expr;
                DFType castType = _finder.resolve(cast.getType());
                DFNode node = new TypeCastNode(this, scope, castType, cast);
                node.accept(processExpression(
                                ctx, scope, frame, cast.getExpression()));
                return node;

            } else if (expr instanceof ClassInstanceCreation) {
                // "new T()"
                ClassInstanceCreation cstr = (ClassInstanceCreation)expr;
                DFKlass instKlass;
                if (cstr.getAnonymousClassDeclaration() != null) {
                    // Anonymous classes are processed separately.
                    String id = Utils.encodeASTNode(cstr);
                    instKlass = _finder.resolveKlass(id);
                } else {
                    instKlass = _finder.resolve(cstr.getType()).toKlass();
                }
                Expression expr1 = cstr.getExpression();
                DFNode obj = null;
                if (expr1 != null) {
                    obj = processExpression(ctx, scope, frame, expr1);
                }
                int nargs = cstr.arguments().size();
                DFNode[] args = new DFNode[nargs];
                DFType[] argTypes = new DFType[nargs];
                for (int i = 0; i < nargs; i++) {
                    Expression arg = (Expression)cstr.arguments().get(i);
                    DFNode node = processExpression(ctx, scope, frame, arg);
                    args[i] = node;
                    argTypes[i] = node.getNodeType();
                }
                DFMethod constructor = instKlass.lookupMethod(
                    DFMethod.CallStyle.Constructor, (String)null, argTypes, null);
                DFMethod[] methods = new DFMethod[] { constructor };
                DFFuncType funcType = constructor.getFuncType();
                CreateObjectNode call = new CreateObjectNode(
                    this, scope, instKlass, constructor, cstr);
                call.setArgs(args);
                this.connectMethodRefs(ctx, scope, call, obj, methods);
                this.catchExceptions(scope, frame, call, funcType.getExceptions());
                return new ReceiveNode(this, scope, call, cstr);

            } else if (expr instanceof ConditionalExpression) {
                // "c? a : b"
                ConditionalExpression cond = (ConditionalExpression)expr;
                DFNode condValue = processExpression(
                    ctx, scope, frame, cond.getExpression());
                DFNode trueValue = processExpression(
                    ctx, scope, frame, cond.getThenExpression());
                DFNode falseValue = processExpression(
                    ctx, scope, frame, cond.getElseExpression());
                JoinNode join = new JoinNode(
                    this, scope, trueValue.getNodeType(), null, expr, condValue);
                join.recv(true, trueValue);
                join.recv(false, falseValue);
                return join;

            } else if (expr instanceof InstanceofExpression) {
                // "a instanceof A"
                InstanceofExpression instof = (InstanceofExpression)expr;
                DFType instType = _finder.resolve(instof.getRightOperand());
                DFNode node = new TypeCheckNode(this, scope, instof, instType);
                node.accept(processExpression(
                                ctx, scope, frame, instof.getLeftOperand()));
                return node;

            } else if (expr instanceof LambdaExpression) {
                // "x -> { ... }"
                LambdaExpression lambda = (LambdaExpression)expr;
                String id = Utils.encodeASTNode(lambda);
                DFKlass lambdaKlass = _finder.resolveKlass(id);
                assert lambdaKlass instanceof DFLambdaKlass;
                // Capture values.
                CaptureNode node = new CaptureNode(this, scope, lambdaKlass, lambda);
                for (DFLambdaKlass.CapturedRef captured :
                         ((DFLambdaKlass)lambdaKlass).getCapturedRefs()) {
                    node.accept(ctx.get(captured.getOriginal()),
                                captured.getFullName());
                }
                return node;

            } else if (expr instanceof ExpressionMethodReference) {
                ExpressionMethodReference methodref = (ExpressionMethodReference)expr;
                String id = Utils.encodeASTNode(methodref);
                DFKlass methodRefKlass = _finder.resolveKlass(id);
                assert methodRefKlass instanceof DFMethodRefKlass;
                CaptureNode node = new CaptureNode(this, scope, methodRefKlass, methodref);
                try {
                    // Try assuming it's an ExpresionMethodReference.
                    // Capture "this".
                    DFNode obj = processExpression(
                        ctx, scope, frame, methodref.getExpression());
                    DFKlass klass = obj.getNodeType().toKlass();
                    node.accept(obj, "#"+klass.getTypeName());
                } catch (EntityNotFound e) {
                    // Turned out it's a TypeMethodReference.
                }
                return node;

            } else if (expr instanceof MethodReference) {
                //  CreationReference
                //  SuperMethodReference
                //  TypeMethodReference
                MethodReference methodref = (MethodReference)expr;
                String id = Utils.encodeASTNode(methodref);
                DFKlass methodRefKlass = _finder.resolveKlass(id);
                assert methodRefKlass instanceof DFMethodRefKlass;
                return new CaptureNode(this, scope, methodRefKlass, methodref);

            } else {
                throw new InvalidSyntax(expr);
            }

        } catch (EntityNotFound e) {
            e.setAst(expr);
            throw e;
        }
    }

    /**
     * Creates an assignment node.
     */
    @SuppressWarnings("unchecked")
    private DFNode processAssignment(
        DFContext ctx, DFLocalScope scope, DFFrame frame,
        Expression expr)
        throws InvalidSyntax, EntityNotFound {
        assert expr != null;

        if (expr instanceof Name) {
            // "a.b"
            Name name = (Name)expr;
            if (name.isSimpleName()) {
                DFRef ref = scope.lookupVar((SimpleName)name);
                DFNode node;
                if (ref instanceof DFKlass.FieldRef) {
                    DFKlass klass = _method.klass();
                    DFNode obj = ctx.get(klass.getThisRef());
                    return new FieldAssignNode(this, scope, ref, expr, obj);
                } else {
                    return new VarAssignNode(this, scope, ref, expr);
                }
            } else {
                QualifiedName qname = (QualifiedName)name;
                DFNode obj = null;
                DFType type = null;
                try {
                    // Try assuming it's a variable access.
                    obj = processExpression(
                        ctx, scope, frame, qname.getQualifier());
                    type = obj.getNodeType();
                } catch (EntityNotFound e) {
                    // Turned out it's a class variable.
                    type = _finder.resolveKlass(qname.getQualifier());
                }
                DFKlass klass = type.toKlass();
                SimpleName fieldName = qname.getName();
                DFRef ref = klass.getField(fieldName);
                if (ref == null) throw new VariableNotFound("."+fieldName);
                return new FieldAssignNode(this, scope, ref, expr, obj);
            }

        } else if (expr instanceof ArrayAccess) {
            // "a[0]"
            ArrayAccess aa = (ArrayAccess)expr;
            DFNode array = processExpression(
                ctx, scope, frame, aa.getArray());
            DFNode index = processExpression(
                ctx, scope, frame, aa.getIndex());
            DFRef ref = scope.lookupArray(array.getNodeType());
            return new ArrayAssignNode(
                this, scope, ref, expr, array, index);

        } else if (expr instanceof FieldAccess) {
            // "(expr).foo"
            FieldAccess fa = (FieldAccess)expr;
            Expression expr1 = fa.getExpression();
            DFNode obj = processExpression(ctx, scope, frame, expr1);
            DFKlass klass = obj.getNodeType().toKlass();
            SimpleName fieldName = fa.getName();
            DFRef ref = klass.getField(fieldName);
            if (ref == null) throw new VariableNotFound("."+fieldName);
            return new FieldAssignNode(this, scope, ref, expr, obj);

        } else if (expr instanceof SuperFieldAccess) {
            // "super.baa"
            SuperFieldAccess sfa = (SuperFieldAccess)expr;
            SimpleName fieldName = sfa.getName();
            DFKlass klass = _method.klass();
            DFNode obj = ctx.get(klass.getThisRef());
            DFKlass baseKlass = klass.getBaseKlass();
            DFRef ref = baseKlass.getField(fieldName);
            if (ref == null) throw new VariableNotFound("."+fieldName);
            return new FieldAssignNode(this, scope, ref, expr, obj);

        } else if (expr instanceof ParenthesizedExpression) {
            ParenthesizedExpression paren = (ParenthesizedExpression)expr;
            return processAssignment(
                ctx, scope, frame, paren.getExpression());

        } else {
            throw new InvalidSyntax(expr);
        }
    }

    /**
     * Creates a new variable node.
     */
    private DFNode processVariableDeclaration(
        DFContext ctx, DFLocalScope scope, DFFrame frame,
        List<VariableDeclarationFragment> frags)
        throws InvalidSyntax, EntityNotFound {

        DFNode value = null;
        for (VariableDeclarationFragment frag : frags) {
            DFRef ref = scope.lookupVar(frag.getName());
            Expression init = frag.getInitializer();
            if (init != null) {
                value = processExpression(
                    ctx, scope, frame, init, ref.getRefType());
                if (value != null) {
                    DFNode assign = new VarAssignNode(this, scope, ref, frag);
                    assign.accept(value);
                    ctx.set(assign);
                }
            }
        }
        return value;
    }

    /**
     * Expands the graph for the loop variables.
     */
    private void processLoop(
        DFContext ctx, DFLocalScope scope, DFFrame frame,
        ASTNode ast, DFNode condValue,
        DFFrame loopFrame, DFContext loopCtx, boolean preTest)
        throws InvalidSyntax {

        String loopId = Utils.encodeASTNode(ast);
        // Add three nodes (Begin, Repeat and End) for each variable.
        Map<DFRef, LoopBeginNode> begins =
            new HashMap<DFRef, LoopBeginNode>();
        Map<DFRef, LoopRepeatNode> repeats =
            new HashMap<DFRef, LoopRepeatNode>();
        Map<DFRef, DFNode> ends =
            new HashMap<DFRef, DFNode>();
        Collection<DFRef> loopRefs = loopFrame.getOutputRefs();
        for (DFRef ref : loopRefs) {
            DFNode src = ctx.get(ref);
            LoopBeginNode begin = new LoopBeginNode(
                this, scope, ref, ast, loopId, src);
            LoopRepeatNode repeat = new LoopRepeatNode(
                this, scope, ref, ast, loopId);
            LoopEndNode end = new LoopEndNode(
                this, scope, ref, ast, loopId, condValue);
            end.setRepeat(repeat);
            begins.put(ref, begin);
            ends.put(ref, end);
            repeats.put(ref, repeat);
        }

        if (preTest) {  // Repeat -> [S] -> Begin -> End
            // Connect the repeats to the loop inputs.
            for (DFNode input : loopCtx.getFirsts()) {
                if (input.hasValue()) continue;
                DFRef ref = input.getRef();
                DFNode src = repeats.get(ref);
                if (src == null) {
                    src = ctx.get(ref);
                }
                input.accept(src);
            }
            // Connect the loop outputs to the begins.
            for (DFRef ref : loopRefs) {
                DFNode output = loopCtx.get(ref);
                if (output != null) {
                    LoopBeginNode begin = begins.get(ref);
                    if (begin != null) {
                        begin.setCont(output);
                    } else {
                        //assert !loopRefs.contains(ref);
                        ctx.set(output);
                    }
                }
            }
            // Connect the beings and ends.
            for (DFRef ref : loopRefs) {
                LoopBeginNode begin = begins.get(ref);
                DFNode end = ends.get(ref);
                end.accept(begin);
            }

        } else {  // Begin -> [S] -> End -> Repeat
            // Connect the begins to the loop inputs.
            for (DFNode input : loopCtx.getFirsts()) {
                if (input.hasValue()) continue;
                DFRef ref = input.getRef();
                DFNode src = begins.get(ref);
                if (src == null) {
                    src = ctx.get(ref);
                }
                input.accept(src);
            }
            // Connect the loop outputs to the ends.
            for (DFRef ref : loopRefs) {
                DFNode output = loopCtx.get(ref);
                if (output != null) {
                    DFNode dst = ends.get(ref);
                    if (dst != null) {
                        dst.accept(output);
                    } else {
                        //assert !loopRefs.contains(ref);
                        ctx.set(output);
                    }
                }
            }
            // Connect the repeats and begins.
            for (DFRef ref : loopRefs) {
                LoopRepeatNode repeat = repeats.get(ref);
                LoopBeginNode begin = begins.get(ref);
                begin.setCont(repeat);
            }
        }

        // Redirect the continue statements.
        assert frame != loopFrame;
        for (DFExit exit : loopFrame.getExits()) {
            DFNode node = exit.getNode();
            DFRef ref = node.getRef();
            if (exit.getFrame() != loopFrame) {
                if (!((node instanceof JoinNode) &&
                      ((JoinNode)node).canMerge())) {
                    JoinNode join = new JoinNode(
                        this, scope, ref.getRefType(), ref, null, condValue);
                    join.recv(true, node);
                    exit.setNode(join);
                }
            } else if (exit instanceof ContinueExit) {
                DFNode end = ends.get(ref);
                if (end == null) {
                    end = ctx.get(ref);
                }
                if (node.canMerge()) {
                    node.merge(end);
                }
                ends.put(ref, node);
            }
        }

        // Closing the loop.
        for (DFRef ref : loopRefs) {
            DFNode end = ends.get(ref);
            LoopRepeatNode repeat = repeats.get(ref);
            ctx.set(end);
            repeat.setEnd(end);
        }
        this.endBreaks(ctx, frame, loopFrame);
    }

    /// Statement processors.
    @SuppressWarnings("unchecked")
    private void processBlock(
        DFContext ctx, DFLocalScope scope, DFFrame frame,
        Block block)
        throws InvalidSyntax, EntityNotFound {
        DFLocalScope innerScope = scope.getChildByAST(block);
        DFFrame innerFrame = frame.getChildByAST(block);
        for (Statement cstmt : (List<Statement>) block.statements()) {
            processStatement(ctx, innerScope, innerFrame, cstmt);
        }
        this.endBreaks(ctx, frame, innerFrame);
    }

    @SuppressWarnings("unchecked")
    private void processVariableDeclarationStatement(
        DFContext ctx, DFLocalScope scope, DFFrame frame,
        VariableDeclarationStatement varStmt)
        throws InvalidSyntax, EntityNotFound {
        processVariableDeclaration(
            ctx, scope, frame, varStmt.fragments());
    }

    private void processExpressionStatement(
        DFContext ctx, DFLocalScope scope, DFFrame frame,
        ExpressionStatement exprStmt)
        throws InvalidSyntax, EntityNotFound {
        processExpression(
            ctx, scope, frame, exprStmt.getExpression());
    }

    private void processIfStatement(
        DFContext ctx, DFLocalScope scope, DFFrame frame,
        IfStatement ifStmt)
        throws InvalidSyntax, EntityNotFound {
        DFNode condValue = processExpression(
            ctx, scope, frame, ifStmt.getExpression());
        DFFrame ifFrame = frame.getChildByAST(ifStmt);

        Statement thenStmt = ifStmt.getThenStatement();
        DFContext thenCtx = new DFContext(this, scope);
        DFFrame thenFrame = ifFrame.getChildByAST(thenStmt);
        processStatement(thenCtx, scope, thenFrame, thenStmt);

        Statement elseStmt = ifStmt.getElseStatement();
        DFContext elseCtx = null;
        DFFrame elseFrame = null;
        if (elseStmt != null) {
            elseFrame = ifFrame.getChildByAST(elseStmt);
            elseCtx = new DFContext(this, scope);
            processStatement(elseCtx, scope, elseFrame, elseStmt);
        }

        // Combines two contexts into one.
        // A JoinNode is added to each variable.

        if (thenCtx != null) {
            for (DFNode src : thenCtx.getFirsts()) {
                if (src.hasValue()) continue;
                src.accept(ctx.get(src.getRef()));
            }
        }
        if (elseCtx != null) {
            for (DFNode src : elseCtx.getFirsts()) {
                if (src.hasValue()) continue;
                src.accept(ctx.get(src.getRef()));
            }
        }

        // Attach a JoinNode to each variable.
        for (DFRef ref : ifFrame.getOutputRefs()) {
            JoinNode join = new JoinNode(
                this, scope, ref.getRefType(), ref, ifStmt, condValue);
            if (thenCtx != null) {
                DFNode dst = thenCtx.get(ref);
                if (dst != null) {
                    join.recv(true, dst);
                }
            }
            if (elseCtx != null) {
                DFNode dst = elseCtx.get(ref);
                if (dst != null) {
                    join.recv(false, dst);
                }
            }
            if (thenCtx == null || elseCtx == null) {
                join.merge(ctx.get(ref));
            }
            ctx.set(join);
        }

        // Take care of exits.
        if (thenFrame != null) {
            assert frame != thenFrame;
            for (DFExit exit : thenFrame.getExits()) {
                DFNode node = exit.getNode();
                DFRef ref = node.getRef();
                JoinNode join = new JoinNode(
                    this, scope, ref.getRefType(), ref, null, condValue);
                join.recv(true, node);
                exit.setNode(join);
                frame.addExit(exit);
            }
        }
        if (elseFrame != null) {
            assert frame != elseFrame;
            for (DFExit exit : elseFrame.getExits()) {
                DFNode node = exit.getNode();
                DFRef ref = node.getRef();
                JoinNode join = new JoinNode(
                    this, scope, ref.getRefType(), ref, null, condValue);
                join.recv(false, node);
                exit.setNode(join);
                frame.addExit(exit);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void processSwitchStatement(
        DFContext ctx, DFLocalScope scope, DFFrame frame,
        SwitchStatement switchStmt)
        throws InvalidSyntax, EntityNotFound {
        DFNode switchValue = processExpression(
            ctx, scope, frame, switchStmt.getExpression());
        DFType type = switchValue.getNodeType();
        DFKlass enumKlass = null;
        if (type instanceof DFKlass &&
            ((DFKlass)type).isEnum()) {
            enumKlass = type.toKlass();
        }
        DFLocalScope switchScope = scope.getChildByAST(switchStmt);
        DFFrame switchFrame = frame.getChildByAST(switchStmt);
        SwitchCase switchCase = null;
        DFFrame caseFrame = null;
        CaseNode caseNode = null;
        DFContext caseCtx = null;
        for (Statement cstmt : (List<Statement>) switchStmt.statements()) {
            assert cstmt != null;
            if (cstmt instanceof SwitchCase) {
                if (caseFrame != null) {
                    assert switchCase != null;
                    assert caseNode != null;
                    assert caseCtx != null;
                    processSwitchCase(
                        ctx, switchScope, switchFrame, switchCase,
                        caseNode, caseCtx, caseFrame);
                }
                switchCase = (SwitchCase)cstmt;
                caseFrame = switchFrame.getChildByAST(switchCase);
                caseNode = new CaseNode(this, switchScope, cstmt);
                caseNode.accept(switchValue);
                caseCtx = new DFContext(this, switchScope);
                for (Expression expr :
                         (List<Expression>) switchCase.expressions()) {
                    if (enumKlass != null && expr instanceof SimpleName) {
                        // special treatment for enum.
                        DFRef ref = enumKlass.getField((SimpleName)expr);
                        if (ref == null) throw new VariableNotFound("."+expr);
                        DFNode node = new FieldRefNode(this, scope, ref, expr, null);
                        node.accept(ctx.get(ref));
                        caseNode.addMatch(node);
                    } else {
                        caseNode.addMatch(
                            processExpression(
                                ctx, switchScope, caseFrame, expr));
                    }
                }
            } else {
                if (caseFrame == null) {
                    // no "case" statement.
                    throw new InvalidSyntax(cstmt);
                }
                processStatement(
                    caseCtx, switchScope, caseFrame, cstmt);
            }
        }
        if (caseFrame != null) {
            assert switchCase != null;
            assert caseNode != null;
            assert caseCtx != null;
            processSwitchCase(
                ctx, switchScope, switchFrame, switchCase,
                caseNode, caseCtx, caseFrame);
        }
        this.endBreaks(ctx, frame, switchFrame);
    }

    private void processSwitchCase(
        DFContext ctx, DFLocalScope scope, DFFrame frame,
        SwitchCase switchCase, DFNode caseNode,
        DFContext caseCtx, DFFrame caseFrame) {

        for (DFNode src : caseCtx.getFirsts()) {
            if (src.hasValue()) continue;
            src.accept(ctx.get(src.getRef()));
        }

        // Take care of exits.
        for (DFExit exit : caseFrame.getExits()) {
            assert exit.getFrame() != caseFrame;
            DFNode node = exit.getNode();
            DFRef ref = node.getRef();
            JoinNode join = new JoinNode(
                this, scope, ref.getRefType(), ref, switchCase, caseNode);
            join.recv(true, node);
            exit.setNode(join);
            frame.addExit(exit);
        }
    }

    private void processWhileStatement(
        DFContext ctx, DFLocalScope scope, DFFrame frame,
        WhileStatement whileStmt)
        throws InvalidSyntax, EntityNotFound {
        DFLocalScope loopScope = scope.getChildByAST(whileStmt);
        DFFrame loopFrame = frame.getChildByAST(whileStmt);
        DFContext loopCtx = new DFContext(this, loopScope);
        DFNode condValue = processExpression(
            loopCtx, scope, loopFrame, whileStmt.getExpression());
        processStatement(
            loopCtx, loopScope, loopFrame, whileStmt.getBody());
        processLoop(
            ctx, loopScope, frame, whileStmt,
            condValue, loopFrame, loopCtx, true);
    }

    private void processDoStatement(
        DFContext ctx, DFLocalScope scope, DFFrame frame,
        DoStatement doStmt)
        throws InvalidSyntax, EntityNotFound {
        DFLocalScope loopScope = scope.getChildByAST(doStmt);
        DFFrame loopFrame = frame.getChildByAST(doStmt);
        DFContext loopCtx = new DFContext(this, loopScope);
        processStatement(
            loopCtx, loopScope, loopFrame, doStmt.getBody());
        DFNode condValue = processExpression(
            loopCtx, loopScope, loopFrame, doStmt.getExpression());
        processLoop(
            ctx, loopScope, frame, doStmt,
            condValue, loopFrame, loopCtx, false);
    }

    @SuppressWarnings("unchecked")
    private void processForStatement(
        DFContext ctx, DFLocalScope scope, DFFrame frame,
        ForStatement forStmt)
        throws InvalidSyntax, EntityNotFound {
        DFLocalScope loopScope = scope.getChildByAST(forStmt);
        DFContext loopCtx = new DFContext(this, loopScope);
        for (Expression init : (List<Expression>) forStmt.initializers()) {
            processExpression(ctx, loopScope, frame, init);
        }
        DFFrame loopFrame = frame.getChildByAST(forStmt);
        Expression expr = forStmt.getExpression();
        DFNode condValue;
        if (expr != null) {
            condValue = processExpression(loopCtx, loopScope, loopFrame, expr);
        } else {
            condValue = new ConstNode(this, loopScope, DFBasicType.BOOLEAN, null, "true");
        }
        processStatement(
            loopCtx, loopScope, loopFrame, forStmt.getBody());
        for (Expression update : (List<Expression>) forStmt.updaters()) {
            processExpression(
                loopCtx, loopScope, loopFrame, update);
        }
        processLoop(
            ctx, loopScope, frame, forStmt,
            condValue, loopFrame, loopCtx, true);
    }

    @SuppressWarnings("unchecked")
    private void processEnhancedForStatement(
        DFContext ctx, DFLocalScope scope, DFFrame frame,
        EnhancedForStatement eForStmt)
        throws InvalidSyntax, EntityNotFound {
        Expression expr = eForStmt.getExpression();
        DFLocalScope loopScope = scope.getChildByAST(eForStmt);
        DFFrame loopFrame = frame.getChildByAST(eForStmt);
        DFContext loopCtx = new DFContext(this, loopScope);
        SingleVariableDeclaration decl = eForStmt.getParameter();
        DFRef ref = loopScope.lookupVar(decl.getName());
        DFNode iterValue = new IterNode(this, loopScope, ref, expr);
        iterValue.accept(processExpression(ctx, scope, frame, expr));
        VarAssignNode assign = new VarAssignNode(this, loopScope, ref, expr);
        assign.accept(iterValue);
        loopCtx.set(assign);
        processStatement(
            loopCtx, loopScope, loopFrame, eForStmt.getBody());
        processLoop(
            ctx, loopScope, frame, eForStmt,
            iterValue, loopFrame, loopCtx, true);
    }

    @SuppressWarnings("unchecked")
    private void processTryStatement(
        DFContext ctx, DFLocalScope scope, DFFrame frame,
        TryStatement tryStmt)
        throws InvalidSyntax, EntityNotFound {
        List<CatchClause> catches = (List<CatchClause>)tryStmt.catchClauses();

        // Find the innermost catch frame so that
        // it can catch all the specified Exceptions.
        DFFrame tryFrame = frame.getChildByAST(tryStmt);
        for (int i = catches.size()-1; 0 <= i; i--) {
            tryFrame = tryFrame.getChildByAST(tryStmt);
        }
        // Execute the try clause.
        DFLocalScope tryScope = scope.getChildByAST(tryStmt);
        DFContext tryCtx = new DFContext(this, tryScope);
        processStatement(
            tryCtx, tryScope, tryFrame, tryStmt.getBody());
        for (DFNode src : tryCtx.getFirsts()) {
            if (src.hasValue()) continue;
            src.accept(ctx.get(src.getRef()));
        }
        // Catch each specified Exception in order.
        List<CatchNode> cats = new ArrayList<CatchNode>();
        for (CatchClause cc : catches) {
            SingleVariableDeclaration decl = cc.getException();
            DFLocalScope catchScope = scope.getChildByAST(cc);
            DFKlass catchKlass = tryFrame.getCatchKlass();
            assert catchKlass != null;
            DFRef catchRef = catchScope.lookupVar(decl.getName());
            CatchNode cat = new CatchNode(this, catchScope, catchRef, decl);
            cats.add(cat);
            // Take care of exits.
            DFRef excRef = scope.lookupException(catchKlass);
            DFFrame parentFrame = tryFrame.getOuterFrame();
            for (DFExit exit : tryFrame.getExits()) {
                DFNode src = exit.getNode();
                if (exit.getFrame() == tryFrame) {
                    // Exception caught.
                    assert exit instanceof ThrowExit;
                    DFRef ref = src.getRef();
                    if (ref == excRef) {
                        cat.accept(src);
                    } else {
                        DFNode dst = ctx.getLast(ref);
                        if (dst == null) {
                            dst = src;
                        } else if (dst.canMerge()) {
                            dst.merge(src);
                        } else if (src.canMerge()) {
                            src.merge(dst);
                            dst = src;
                        } else {
                            Logger.error("DFGraph.catch: Conflict:", dst+" <- "+src);
                            continue;
                        }
                        ctx.set(dst);
                    }
                } else {
                    // Further thrown outwards.
                    parentFrame.addExit(exit);
                }
            }
            tryFrame = parentFrame;
        }
        assert tryFrame.getOuterFrame() == frame;

        // Actual execution of catch clauses are performed in the outside frame.
        for (int i = 0; i < catches.size(); i++) {
            CatchClause cc = catches.get(i);
            DFLocalScope catchScope = scope.getChildByAST(cc);
            DFFrame catchFrame = frame.getChildByAST(cc);
            DFContext catchCtx = new DFContext(this, catchScope);
            CatchNode cat = cats.get(i);
            catchCtx.set(cat);
            // Execute the catch clause.
            processStatement(
                catchCtx, catchScope, catchFrame, cc.getBody());
            for (DFNode src : catchCtx.getFirsts()) {
                if (src.hasValue()) continue;
                src.accept(ctx.get(src.getRef()));
            }
            for (DFExit exit : catchFrame.getExits()) {
                DFNode src = exit.getNode();
                CatchJoin join = new CatchJoin(
                    this, scope, cc, src, cat.getNodeType().toKlass());
                exit.setNode(join);
                frame.addExit(exit);
            }
        }

        // XXX Take care of ALL exits from tryFrame.
        Block finBlock = tryStmt.getFinally();
        if (finBlock != null) {
            processStatement(ctx, scope, frame, finBlock);
        }
    }

    // connectMethodRefs: connect input/output nodes for methods.
    private void connectMethodRefs(
        DFContext ctx, DFLocalScope scope,
        CallNode call, DFNode obj, DFMethod[] methods) {

        ConsistentHashSet<DFRef> inRefs = new ConsistentHashSet<DFRef>();
        inRefs.add(scope.lookupBypass());
        for (DFMethod method1 : methods) {
            if (method1 instanceof DFSourceMethod) {
                DFSourceMethod srcmethod = (DFSourceMethod)method1;
                inRefs.addAll(srcmethod.getInputRefs());
            }
        }
        if (obj != null) {
            inRefs.add(obj.getNodeType().toKlass().getThisRef());
        }
        for (DFRef ref : inRefs) {
            if (ref instanceof DFKlass.ThisRef && obj != null) {
                call.accept(obj, ref.getFullName());
            } else {
                call.accept(ctx.get(ref), ref.getFullName());
            }
        }

        ConsistentHashSet<DFRef> outRefs = new ConsistentHashSet<DFRef>();
        outRefs.add(scope.lookupBypass());
        for (DFMethod method1 : methods) {
            if (method1 instanceof DFSourceMethod) {
                DFSourceMethod srcmethod = (DFSourceMethod)method1;
                outRefs.addAll(srcmethod.getOutputRefs());
            }
        }
        for (DFRef ref : outRefs) {
            ctx.set(new ReceiveNode(this, scope, call, null, ref));
        }
    }

    // catchExceptions: catch exceptions raised by a calling method.
    private void catchExceptions(
        DFLocalScope scope, DFFrame frame, DFNode node, DFKlass[] exceptions) {
        for (DFKlass excKlass : exceptions) {
            DFRef excRef = scope.lookupException(excKlass);
            DFFrame dstFrame = frame.find(excKlass);
            if (dstFrame == null) {
                dstFrame = frame.find(DFFrame.RETURNABLE);
            }
            DFNode thrown = new ThrowNode(this, scope, excRef, null);
            thrown.accept(node, excRef.getFullName());
            frame.addExit(new ThrowExit(dstFrame, thrown, excKlass));
        }
    }

    // endBreaks: ends a BREAKABLE Frame.
    private void endBreaks(
        DFContext ctx, DFFrame outerFrame, DFFrame endFrame) {
        // endFrame.getLabel() can be either @BREAKABLE or a label.
        assert outerFrame != endFrame;
        ConsistentHashMap<DFRef, List<DFExit>> ref2exits =
            new ConsistentHashMap<DFRef, List<DFExit>>();
        for (DFExit exit : endFrame.getExits()) {
            if (exit.getFrame() != endFrame) {
                // Pass through the outer frame.
                outerFrame.addExit(exit);
            } else if (exit instanceof ContinueExit) {
                // Ignore continueExit.
                ;
            } else {
                assert exit instanceof BreakExit;
                DFNode src = exit.getNode();
                DFRef ref = src.getRef();
                List<DFExit> a = ref2exits.get(ref);
                if (a == null) {
                    a = new ArrayList<DFExit>();
                    ref2exits.put(ref, a);
                }
                a.add(exit);
            }
        }
        for (DFRef ref : ref2exits.keys()) {
            List<DFExit> a = ref2exits.get(ref);
            Collections.sort(a, new ExitComparator(a));
            for (DFExit exit : a) {
                DFNode src = exit.getNode();
                DFNode dst = ctx.getLast(ref);
                if (dst == null) {
                    ctx.set(src);
                } else if (src == dst) {
                    ;
                } else if (dst.canMerge()) {
                    dst.merge(src);
                } else if (src.canMerge()) {
                    src.merge(dst);
                    ctx.set(src);
                } else {
                    Logger.error("DFGraph.endBreaks: cannot merge:", ref, dst, src);
                    //assert false;
                }
            }
        }
    }

    private void closeFrame(
        DFContext ctx, DFLocalScope scope, DFFrame frame) {
        ConsistentHashMap<DFRef, List<DFExit>> ref2exits =
            new ConsistentHashMap<DFRef, List<DFExit>>();
        for (DFExit exit : frame.getExits()) {
            assert exit.getFrame() == frame;
            DFNode src = exit.getNode();
            DFRef ref = src.getRef();
            List<DFExit> a = ref2exits.get(ref);
            if (a == null) {
                a = new ArrayList<DFExit>();
                ref2exits.put(ref, a);
            }
            a.add(exit);
        }
        for (DFRef ref : ref2exits.keys()) {
            List<DFExit> a = ref2exits.get(ref);
            Collections.sort(a, new ExitComparator(a));
            for (DFExit exit : a) {
                assert (exit instanceof ReturnExit ||
                        exit instanceof ThrowExit);
                DFNode src = exit.getNode();
                DFNode dst = ctx.getLast(ref);
                if (exit instanceof ThrowExit) {
                    DFKlass excKlass = ((ThrowExit)exit).getExcKlass();
                    if (dst == null) {
                        ctx.set(src);
                    } else {
                        CatchJoin join = new CatchJoin(
                            this, scope, null, src, excKlass);
                        join.merge(dst);
                        ctx.set(join);
                    }
                } else {
                    if (dst == null) {
                        //Logger.info(" set", src);
                        ctx.set(src);
                    } else if (src == dst) {
                        ;
                    } else if (dst.canMerge()) {
                        dst.merge(src);
                        //Logger.info(" merge", dst, "<-", src);
                    } else if (src.canMerge()) {
                        //Logger.info(" merge", src, "<-", dst);
                        src.merge(dst);
                        ctx.set(src);
                    } else {
                        Logger.error("DFGraph.closeFrame: cannot merge:", ref, dst, src);
                        //assert false;
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void processDecls(
        DFContext ctx, List<BodyDeclaration> decls)
        throws InvalidSyntax, EntityNotFound {

        DFSourceMethod.MethodScope scope = _method.getScope();
        DFFrame frame = new DFFrame(_method, _finder, scope);

        // Create input nodes.
        for (DFRef ref : _method.getInputRefs()) {
            DFNode input = new InputNode(this, scope, ref, null);
            ctx.set(input);
        }

        for (BodyDeclaration body : decls) {
            if (body instanceof AbstractTypeDeclaration) {
                // Inner classes are processed separately.

            } else if (body instanceof FieldDeclaration) {
                FieldDeclaration fieldDecl = (FieldDeclaration)body;
                for (VariableDeclarationFragment frag :
                         (List<VariableDeclarationFragment>) fieldDecl.fragments()) {
                    DFRef ref = _method.klass().getField(frag.getName());
                    if (ref == null) throw new VariableNotFound("."+frag.getName());
                    DFNode value = null;
                    Expression init = frag.getInitializer();
                    if (init != null) {
                        value = processExpression(
                            ctx, scope, frame, init, ref.getRefType());
                    }
                    if (value == null) {
                        // uninitialized field: default = null.
                        value = new ConstNode(
                            this, scope,
                            DFNullType.NULL, null, "null");
                    }
                    DFNode assign = new VarAssignNode(
                        this, scope, ref, frag);
                    assign.accept(value);
                }

            } else if (body instanceof MethodDeclaration) {

            } else if (body instanceof EnumConstantDeclaration) {

            } else if (body instanceof AnnotationTypeMemberDeclaration) {

            } else if (body instanceof Initializer) {
                Initializer initializer = (Initializer)body;
                frame.buildStmt(initializer.getBody());
                processStatement(ctx, scope, frame, initializer.getBody());

            } else {
                throw new InvalidSyntax(body);
            }
        }

        this.closeFrame(ctx, scope, frame);

        // Create output nodes.
        for (DFRef ref : _method.getOutputRefs()) {
            DFNode output = new OutputNode(this, scope, ref, null);
            output.accept(ctx.get(ref));
        }

        for (DFRef ref : _method.getPassInRefs()) {
            DFNode node = ctx.getFirst(ref);
            if (node != null) {
                node.accept(this.getPassInNode(), ref.getFullName());
            }
        }
        {
            DFNode node = ctx.getFirst(scope.lookupBypass());
            if (node != null) {
                node.accept(this.getPassInNode());
            }
        }

        for (DFRef ref : _method.getPassOutRefs()) {
            DFNode node = ctx.getLast(ref);
            if (node != null) {
                this.getPassOutNode().accept(node, ref.getFullName());
            }
        }
        {
            DFNode node = ctx.getLast(scope.lookupBypass());
            if (node != null) {
                this.getPassOutNode().accept(node);
            }
        }

        this.cleanup();
    }

    public void processMethodBody(
        DFContext ctx, ASTNode body)
        throws InvalidSyntax, EntityNotFound {

        DFSourceMethod.MethodScope scope = _method.getScope();
        DFFrame frame = new DFFrame(_method, _finder, scope);

        // Create input nodes.
        for (DFRef ref : _method.getInputRefs()) {
            DFNode input = new InputNode(this, scope, ref, null);
            ctx.set(input);
        }

        if (body instanceof Statement) {
            frame.buildStmt((Statement)body);
            processStatement(
                ctx, scope, frame, (Statement)body);
        } else if (body instanceof Expression) {
            frame.buildExpr((Expression)body);
            DFRef ref = scope.lookupReturn();
            ReturnNode ret = new ReturnNode(this, scope, ref, body);
            ret.accept(processExpression(
                           ctx, scope, frame, (Expression)body));
            frame.addExit(new ReturnExit(frame, ret));
        }

        this.closeFrame(ctx, scope, frame);

        // Create output nodes.
        {
            DFRef ref = scope.lookupReturn();
            if (ctx.getLast(ref) != null) {
                DFNode output = new OutputNode(this, scope, ref, null);
                output.accept(ctx.getLast(ref));
            }
        }
        for (DFRef ref : scope.getExcRefs()) {
            if (ctx.getLast(ref) != null) {
                DFNode output = new OutputNode(this, scope, ref, null);
                output.accept(ctx.getLast(ref));
            }
        }
        for (DFRef ref : _method.getOutputRefs()) {
            DFNode output = new OutputNode(this, scope, ref, null);
            output.accept(ctx.get(ref));
        }

        for (DFRef ref : _method.getPassInRefs()) {
            DFNode node = ctx.getFirst(ref);
            if (node != null) {
                node.accept(this.getPassInNode(), ref.getFullName());
            }
        }
        {
            DFNode node = ctx.getFirst(scope.lookupBypass());
            if (node != null) {
                node.accept(this.getPassInNode());
            }
        }

        for (DFRef ref : _method.getPassOutRefs()) {
            DFNode node = ctx.getLast(ref);
            if (node != null) {
                this.getPassOutNode().accept(node, ref.getFullName());
            }
        }
        {
            DFNode node = ctx.getLast(scope.lookupBypass());
            if (node != null) {
                this.getPassOutNode().accept(node);
            }
        }

        // Do not remove input/output nodes.
        this.cleanup();
    }
}

// ExitComparator
class ExitComparator implements Comparator<DFExit> {

    private Map<DFExit, Integer> _index =
        new HashMap<DFExit, Integer>();

    public ExitComparator(List<DFExit> exits) {
        for (DFExit exit : exits) {
            _index.put(exit, _index.size());
        }
    }

    public int compare(DFExit exit0, DFExit exit1) {
        if (exit0 instanceof BreakExit ||
            exit0 instanceof ReturnExit) {
            boolean m0 = exit0.getNode().canMerge();
            boolean m1 = exit1.getNode().canMerge();
            if (m0 && !m1) {
                return +1;
            } else if (!m0 && m1) {
                return -1;
            }
        }
        // preserve the original order.
        int index0 = _index.get(exit0);
        int index1 = _index.get(exit1);
        return (index0 - index1);
    }
}

// BreakExit
class BreakExit extends DFExit {

    public BreakExit(DFFrame frame, DFNode node) {
        super(frame, node);
        // frame.getLabel() can be either @BREAKABLE or a label.
    }
}

// ContinueExit
class ContinueExit extends DFExit {

    public ContinueExit(DFFrame frame, DFNode node) {
        super(frame, node);
        // frame.getLabel() can be either @BREAKABLE or a label.
    }
}

// ReturnExit
class ReturnExit extends DFExit {

    public ReturnExit(DFFrame frame, DFNode node) {
        super(frame, node);
        assert frame.getLabel() == DFFrame.RETURNABLE;
    }
}

// ThrowExit
class ThrowExit extends DFExit {

    private DFKlass _excKlass;

    public ThrowExit(DFFrame frame, DFNode node, DFKlass excKlass) {
        super(frame, node);
        _excKlass = excKlass;
    }

    public DFKlass getExcKlass() {
        return _excKlass;
    }
}

// SingleAssignNode:
class SingleAssignNode extends DFNode {

    public SingleAssignNode(
        DFGraph graph, DFVarScope scope, DFRef ref,
        ASTNode ast) {
        super(graph, scope, ref.getRefType(), ref, ast);
    }
}

// VarAssignNode:
class VarAssignNode extends SingleAssignNode {

    public VarAssignNode(
        DFGraph graph, DFVarScope scope, DFRef ref,
        ASTNode ast) {
        super(graph, scope, ref, ast);
    }

    @Override
    public String getKind() {
        return "assign_var";
    }
}

// ArrayAssignNode:
class ArrayAssignNode extends DFNode {

    public ArrayAssignNode(
        DFGraph graph, DFVarScope scope, DFRef ref,
        ASTNode ast, DFNode array, DFNode index) {
        super(graph, scope, ref.getRefType(), ref, ast);
        this.accept(array, "array");
        this.accept(index, "index");
    }

    @Override
    public String getKind() {
        return "assign_array";
    }
}

// FieldAssignNode:
class FieldAssignNode extends DFNode {

    public FieldAssignNode(
        DFGraph graph, DFVarScope scope, DFRef ref,
        ASTNode ast, DFNode obj) {
        super(graph, scope, ref.getRefType(), ref, ast);
        if (obj != null) {
            this.accept(obj, "obj");
        }
    }

    @Override
    public String getKind() {
        return "assign_field";
    }
}

// VarRefNode: represnets a variable reference.
class VarRefNode extends DFNode {

    public VarRefNode(
        DFGraph graph, DFVarScope scope, DFRef ref,
        ASTNode ast) {
        super(graph, scope, ref.getRefType(), ref, ast);
    }

    @Override
    public String getKind() {
        return "ref_var";
    }
}

// ArrayRefNode
class ArrayRefNode extends DFNode {

    public ArrayRefNode(
        DFGraph graph, DFVarScope scope, DFRef ref,
        ASTNode ast, DFNode array, DFNode index) {
        super(graph, scope, ref.getRefType(), ref, ast);
        this.accept(array, "array");
        this.accept(index, "index");
    }

    @Override
    public String getKind() {
        return "ref_array";
    }
}

// FieldRefNode
class FieldRefNode extends DFNode {

    public FieldRefNode(
        DFGraph graph, DFVarScope scope, DFRef ref,
        ASTNode ast, DFNode obj) {
        super(graph, scope, ref.getRefType(), ref, ast);
        if (obj != null) {
            this.accept(obj, "obj");
        }
    }

    @Override
    public String getKind() {
        return "ref_field";
    }
}

// PrefixNode
class PrefixNode extends DFNode {

    public PrefixExpression.Operator op;

    public PrefixNode(
        DFGraph graph, DFVarScope scope, DFType type, DFRef ref,
        ASTNode ast, PrefixExpression.Operator op) {
        super(graph, scope, type, ref, ast);
        this.op = op;
    }

    @Override
    public String getKind() {
        return "op_prefix";
    }

    @Override
    public String getData() {
        return this.op.toString();
    }
}

// PostfixNode
class PostfixNode extends DFNode {

    public PostfixExpression.Operator op;

    public PostfixNode(
        DFGraph graph, DFVarScope scope, DFRef ref,
        ASTNode ast, PostfixExpression.Operator op) {
        super(graph, scope, ref.getRefType(), ref, ast);
        this.op = op;
    }

    @Override
    public String getKind() {
        return "op_postfix";
    }

    @Override
    public String getData() {
        return this.op.toString();
    }
}

// InfixNode
class InfixNode extends DFNode {

    public InfixExpression.Operator op;

    public InfixNode(
        DFGraph graph, DFVarScope scope, DFType type,
        ASTNode ast, InfixExpression.Operator op,
        DFNode lvalue, DFNode rvalue) {
        super(graph, scope, type, null, ast);
        this.op = op;
        this.accept(lvalue, "L");
        this.accept(rvalue, "R");
    }

    @Override
    public String getKind() {
        return "op_infix";
    }

    @Override
    public String getData() {
        return this.op.toString();
    }
}

// TypeCastNode
class TypeCastNode extends DFNode {

    public TypeCastNode(
        DFGraph graph, DFVarScope scope, DFType type,
        ASTNode ast) {
        super(graph, scope, type, null, ast);
        assert type != null;
    }

    @Override
    public String getKind() {
        return "op_typecast";
    }

    @Override
    public String getData() {
        return this.getNodeType().getTypeName();
    }
}

// TypeCheckNode
class TypeCheckNode extends DFNode {

    public DFType type;

    public TypeCheckNode(
        DFGraph graph, DFVarScope scope,
        ASTNode ast, DFType type) {
        super(graph, scope, DFBasicType.BOOLEAN, null, ast);
        assert type != null;
        this.type = type;
    }

    @Override
    public String getKind() {
        return "op_typecheck";
    }

    @Override
    public String getData() {
        return this.getNodeType().getTypeName();
    }
}

// CaseNode
class CaseNode extends DFNode {

    public List<DFNode> matches = new ArrayList<DFNode>();

    public CaseNode(
        DFGraph graph, DFVarScope scope,
        ASTNode ast) {
        super(graph, scope, DFUnknownType.UNKNOWN, null, ast);
    }

    @Override
    public String getKind() {
        return "case";
    }

    @Override
    public String getData() {
        if (this.matches.isEmpty()) {
            return "default";
        } else {
            return "case("+this.matches.size()+")";
        }
    }

    public void addMatch(DFNode node) {
        String label = "match"+this.matches.size();
        this.accept(node, label);
        this.matches.add(node);
    }
}

// AssignOpNode
class AssignOpNode extends SingleAssignNode {

    public Assignment.Operator op;

    public AssignOpNode(
        DFGraph graph, DFVarScope scope, DFRef ref,
        ASTNode ast, Assignment.Operator op,
        DFNode lvalue, DFNode rvalue) {
        super(graph, scope, ref, ast);
        this.op = op;
        if (lvalue != null) {
            this.accept(lvalue, "L");
        }
        this.accept(rvalue, "R");
    }

    @Override
    public String getKind() {
        return "op_assign";
    }

    @Override
    public String getData() {
        return this.op.toString();
    }
}

// ConstNode: represents a constant value.
class ConstNode extends DFNode {

    public String data;

    public ConstNode(
        DFGraph graph, DFVarScope scope, DFType type,
        ASTNode ast, String data) {
        super(graph, scope, type, null, ast);
        this.data = data;
    }

    @Override
    public String getKind() {
        return "value";
    }

    @Override
    public String getData() {
        return this.data;
    }
}

// ValueSetNode: represents an array.
class ValueSetNode extends DFNode {

    public ValueSetNode(
        DFGraph graph, DFVarScope scope, DFType type,
        ASTNode ast) {
        super(graph, scope, type, null, ast);
    }

    @Override
    public String getKind() {
        return "valueset";
    }
}

// CaptureNode: represents variable captures (for lambdas).
class CaptureNode extends DFNode {

    public CaptureNode(
        DFGraph graph, DFVarScope scope, DFType type,
        ASTNode ast) {
        super(graph, scope, type, null, ast);
    }

    @Override
    public String getKind() {
        return "capture";
    }
}

// JoinNode
class JoinNode extends DFNode {

    private DFNode.Edge _edgeCond;
    private DFNode.Edge _edgeTrue = null;
    private DFNode.Edge _edgeFalse = null;

    public JoinNode(
        DFGraph graph, DFVarScope scope, DFType type, DFRef ref,
        ASTNode ast, DFNode cond) {
        super(graph, scope, type, ref, ast);
        _edgeCond = this.accept(cond, "cond");
    }

    @Override
    public String getKind() {
        return "join";
    }

    public void recv(boolean cond, DFNode node) {
        if (cond) {
            assert _edgeTrue == null;
            _edgeTrue = this.accept(node, "true");
        } else {
            assert _edgeFalse == null;
            _edgeFalse = this.accept(node, "false");
        }
    }

    @Override
    public boolean canMerge() {
        return (_edgeTrue == null || _edgeFalse == null);
    }

    @Override
    public void merge(DFNode node) {
        if (_edgeTrue == null) {
            assert _edgeFalse != null;
            _edgeTrue = this.accept(node, "true");
        } else if (_edgeFalse == null) {
            assert _edgeTrue != null;
            _edgeFalse = this.accept(node, "false");
        } else {
            Logger.error("JoinNode: cannot merge:", this, node);
            assert false;
        }
    }

    @Override
    public boolean purge() {
        if (_edgeTrue == null) {
            assert _edgeFalse != null;
            this.disconnect(_edgeFalse.getSrc());
            return true;
        } else if (_edgeFalse == null) {
            assert _edgeTrue != null;
            this.disconnect(_edgeTrue.getSrc());
            return true;
        } else {
            DFNode srcTrue = _edgeTrue.getSrc();
            if (srcTrue instanceof JoinNode &&
                ((JoinNode)srcTrue)._edgeCond.getSrc() == _edgeCond.getSrc() &&
                ((JoinNode)srcTrue)._edgeFalse != null &&
                ((JoinNode)srcTrue)._edgeFalse.getSrc() == _edgeFalse.getSrc()) {
                this.disconnect(srcTrue);
                return true;
            }
            DFNode srcFalse = _edgeFalse.getSrc();
            if (srcFalse instanceof JoinNode &&
                ((JoinNode)srcFalse)._edgeCond.getSrc() == _edgeCond.getSrc() &&
                ((JoinNode)srcFalse)._edgeTrue != null &&
                ((JoinNode)srcFalse)._edgeTrue.getSrc() == _edgeTrue.getSrc()) {
                this.disconnect(srcFalse);
                return true;
            }
            return false;
        }
    }
}

// LoopNode
class LoopNode extends DFNode {

    public String loopId;

    public LoopNode(
        DFGraph graph, DFVarScope scope, DFRef ref,
        ASTNode ast, String loopId) {
        super(graph, scope, ref.getRefType(), ref, ast);
        this.loopId = loopId;
    }

    @Override
    public String getData() {
        return this.loopId;
    }
}

// LoopBeginNode
class LoopBeginNode extends LoopNode {

    public LoopBeginNode(
        DFGraph graph, DFVarScope scope, DFRef ref,
        ASTNode ast, String loopId, DFNode init) {
        super(graph, scope, ref, ast, loopId);
        this.accept(init, "init");
    }

    @Override
    public String getKind() {
        return "begin";
    }

    public void setCont(DFNode cont) {
        this.accept(cont, "cont");
    }
}

// LoopEndNode
class LoopEndNode extends LoopNode {

    public LoopEndNode(
        DFGraph graph, DFVarScope scope, DFRef ref,
        ASTNode ast, String loopId, DFNode cond) {
        super(graph, scope, ref, ast, loopId);
        this.accept(cond, "cond");
    }

    @Override
    public String getKind() {
        return "end";
    }

    public void setRepeat(LoopRepeatNode repeat) {
        this.accept(repeat, "_repeat");
    }
}

// LoopRepeatNode
class LoopRepeatNode extends LoopNode {

    public LoopRepeatNode(
        DFGraph graph, DFVarScope scope, DFRef ref,
        ASTNode ast, String loopId) {
        super(graph, scope, ref, ast, loopId);
    }

    @Override
    public String getKind() {
        return "repeat";
    }

    public void setEnd(DFNode end) {
        this.accept(end, "_end");
    }
}

// IterNode
class IterNode extends DFNode {

    public IterNode(
        DFGraph graph, DFVarScope scope, DFRef ref,
        ASTNode ast) {
        super(graph, scope, ref.getRefType(), ref, ast);
    }

    @Override
    public String getKind() {
        return "op_iter";
    }
}

// CallNode
abstract class CallNode extends DFNode {

    public DFFuncType funcType;
    public DFNode[] args;

    public CallNode(
        DFGraph graph, DFVarScope scope, DFType type, DFRef ref,
        ASTNode ast, DFFuncType funcType) {
        super(graph, scope, type, ref, ast);
        this.funcType = funcType;
        this.args = null;
    }

    @Override
    public String getKind() {
        return "call";
    }

    public void setArgs(DFNode[] args) {
        assert this.args == null;
        for (int i = 0; i < args.length; i++) {
            String label = "#arg"+i;
            this.accept(args[i], label);
        }
        this.args = args;
    }
}

// MethodCallNode
class MethodCallNode extends CallNode {

    public DFMethod[] methods;

    public MethodCallNode(
        DFGraph graph, DFVarScope scope,
        ASTNode ast, DFFuncType funcType,
        DFMethod[] methods) {
        super(graph, scope, funcType.getSafeReturnType(), null,
              ast, funcType);
        this.methods = methods;
    }

    @Override
    public String getData() {
        StringBuilder b = new StringBuilder();
        for (DFMethod method : this.methods) {
            if (0 < b.length()) {
                b.append(" ");
            }
            b.append(method.getSignature());
        }
        return b.toString();
    }
}

// ReceiveNode:
class ReceiveNode extends DFNode {

    public ReceiveNode(
        DFGraph graph, DFVarScope scope, CallNode call,
        ASTNode ast) {
        super(graph, scope, call.getNodeType(), null, ast);
        this.accept(call);
    }

    public ReceiveNode(
        DFGraph graph, DFVarScope scope, CallNode call,
        ASTNode ast, DFRef ref) {
        super(graph, scope, ref.getRefType(), ref, ast);
        this.accept(call, ref.getFullName());
    }

    @Override
    public String getKind() {
        return "receive";
    }
}

// CreateObjectNode
class CreateObjectNode extends CallNode {

    public DFMethod constructor;

    public CreateObjectNode(
        DFGraph graph, DFVarScope scope, DFType type, DFMethod constructor,
        ASTNode ast) {
        super(graph, scope, type, null,
              ast, constructor.getFuncType());
        this.constructor = constructor;
    }

    @Override
    public String getKind() {
        return "new";
    }

    @Override
    public String getData() {
        return this.constructor.getSignature();
    }
}

// ReturnNode:
class ReturnNode extends SingleAssignNode {

    public ReturnNode(
        DFGraph graph, DFVarScope scope, DFRef ref,
        ASTNode ast) {
        super(graph, scope, ref, ast);
    }

    @Override
    public String getKind() {
        return "return";
    }
}

// ThrowNode
class ThrowNode extends SingleAssignNode {

    public ThrowNode(
        DFGraph graph, DFVarScope scope, DFRef ref, ASTNode ast) {
        super(graph, scope, ref, ast);
    }

    @Override
    public String getKind() {
        return "throw";
    }
}

// CatchNode
class CatchNode extends SingleAssignNode {

    private int _nexcs = 0;

    public CatchNode(
        DFGraph graph, DFVarScope scope, DFRef ref,
        ASTNode ast) {
        super(graph, scope, ref, ast);
    }

    @Override
    public Edge accept(DFNode node) {
        String label = "exc"+(_nexcs++);
        return this.accept(node, label);
    }

    @Override
    public String getKind() {
        return "catch";
    }
}

// CatchJoin
class CatchJoin extends DFNode {

    public CatchJoin(
        DFGraph graph, DFVarScope scope, ASTNode ast,
        DFNode node, DFKlass catchKlass) {
        super(graph, scope, node.getNodeType(), node.getRef(), ast);
        this.accept(node, catchKlass.getTypeName());
    }

    @Override
    public String getKind() {
        return "catchjoin";
    }

    @Override
    public boolean canMerge() {
        return !this.hasValue();
    }

    @Override
    public void merge(DFNode node) {
        assert !this.hasValue();
        this.accept(node);
    }

}

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

// PassInNode: represnets an input bypass.
class PassInNode extends DFNode {

    public PassInNode(
        DFGraph graph, DFVarScope scope) {
        super(graph, scope, DFUnknownType.UNKNOWN, null, null);
    }

    @Override
    public String getKind() {
        return "passin";
    }
}

// PassOutNode: represnets an output bypass.
class PassOutNode extends DFNode {

    public PassOutNode(
        DFGraph graph, DFVarScope scope) {
        super(graph, scope, DFUnknownType.UNKNOWN, null, null);
    }

    @Override
    public String getKind() {
        return "passout";
    }
}
