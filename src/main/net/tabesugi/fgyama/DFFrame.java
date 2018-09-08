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
    private DFFrame _parent;

    private Map<String, DFFrame> _ast2child =
        new HashMap<String, DFFrame>();
    private Set<DFVarRef> _inputs =
        new HashSet<DFVarRef>();
    private Set<DFVarRef> _outputs =
        new HashSet<DFVarRef>();
    private List<DFExit> _exits =
        new ArrayList<DFExit>();

    public static final String COND = "@COND";
    public static final String LOOP = "@LOOP";
    public static final String TRY = "@TRY";
    public static final String METHOD = "@METHOD";
    public static final String CLASS = "@CLASS";

    public DFFrame(String label) {
        this(label, null);
    }

    public DFFrame(String label, DFFrame parent) {
        assert(label != null);
        _label = label;
        _parent = parent;
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

    public DFFrame getParent() {
        return _parent;
    }

    public DFFrame getChildByAST(ASTNode ast) {
        String key = Utils.encodeASTNode(ast);
        assert(_ast2child.containsKey(key));
        return _ast2child.get(key);
    }

    public DFFrame find(String label) {
        DFFrame frame = this;
        while (frame != null) {
            if (frame.getLabel().equals(label)) break;
            frame = frame.getParent();
        }
        return frame;
    }

    private void addInput(DFVarRef ref) {
        _inputs.add(ref);
    }

    private void addOutput(DFVarRef ref) {
        _outputs.add(ref);
    }

    public DFVarRef[] getInputs() {
        DFVarRef[] refs = new DFVarRef[_inputs.size()];
        _inputs.toArray(refs);
        Arrays.sort(refs);
        return refs;
    }

    public DFVarRef[] getOutputs() {
        DFVarRef[] refs = new DFVarRef[_outputs.size()];
        _outputs.toArray(refs);
        Arrays.sort(refs);
        return refs;
    }

    public DFVarRef[] getInsAndOuts() {
        Set<DFVarRef> inouts = new HashSet<DFVarRef>(_inputs);
        inouts.retainAll(_outputs);
        DFVarRef[] refs = new DFVarRef[inouts.size()];
        inouts.toArray(refs);
        Arrays.sort(refs);
        return refs;
    }

    public DFExit[] getExits() {
        DFExit[] exits = new DFExit[_exits.size()];
        _exits.toArray(exits);
        return exits;
    }

    public void addExit(DFExit exit) {
        Logger.info("add: "+this+": "+exit);
        _exits.add(exit);
    }

    public void close(DFContext ctx) {
        for (DFExit exit : _exits) {
            Logger.info("closing: "+this+": "+exit);
            if (exit.getFrame() == this) {
                DFNode node = exit.getNode();
                node.finish(ctx);
                ctx.set(node);
            }
        }
    }

    private void finish(DFFrame childFrame) {
        _inputs.addAll(childFrame._inputs);
        _outputs.addAll(childFrame._outputs);
    }

    private void buildAssignment(
        DFTypeFinder finder, DFVarScope scope, Expression expr)
        throws UnsupportedSyntax, EntityNotFound {
        if (expr instanceof Name) {
            Name name = (Name)expr;
            DFVarRef ref;
            if (name.isSimpleName()) {
                ref = scope.lookupVarOrField((SimpleName)name);
            } else {
                QualifiedName qname = (QualifiedName)name;
                DFClass klass;
                try {
                    // Try assuming it's a variable access.
                    DFType type = this.build(finder, scope, qname.getQualifier());
                    if (type == null) return;
                    klass = finder.resolveClass(type);
                } catch (EntityNotFound e) {
                    // Turned out it's a class variable.
                    klass = finder.lookupClass(qname.getQualifier());
                }
                SimpleName fieldName = qname.getName();
                ref = klass.lookupField(fieldName);
            }
            this.addOutput(ref);

        } else if (expr instanceof ArrayAccess) {
            ArrayAccess aa = (ArrayAccess)expr;
            DFType type = this.build(finder, scope, aa.getArray());
            this.build(finder, scope, aa.getIndex());
            if (type == null) return;
            DFVarRef ref = scope.lookupArray(type);
            this.addOutput(ref);

        } else if (expr instanceof FieldAccess) {
            FieldAccess fa = (FieldAccess)expr;
            Expression expr1 = fa.getExpression();
            DFClass klass = null;
            if (expr1 instanceof Name) {
                try {
                    klass = finder.lookupClass((Name)expr1);
                } catch (EntityNotFound e) {
                }
            }
            if (klass == null) {
                DFType type = this.build(finder, scope, expr1);
                if (type == null) return;
                klass = finder.resolveClass(type);
            }
            SimpleName fieldName = fa.getName();
            DFVarRef ref = klass.lookupField(fieldName);
            this.addOutput(ref);

        } else {
            throw new UnsupportedSyntax(expr);
        }
    }

    @SuppressWarnings("unchecked")
    private DFType build(
        DFTypeFinder finder, DFVarScope scope, Expression expr)
        throws UnsupportedSyntax, EntityNotFound {
        if (expr instanceof Annotation) {
            return null;

        } else if (expr instanceof Name) {
            Name name = (Name)expr;
            DFVarRef ref;
            if (name.isSimpleName()) {
                ref = scope.lookupVarOrField((SimpleName)name);
            } else {
                QualifiedName qname = (QualifiedName)name;
                DFClass klass;
                try {
                    // Try assuming it's a variable access.
                    DFType type = this.build(finder, scope, qname.getQualifier());
                    if (type == null) return type;
                    klass = finder.resolveClass(type);
                } catch (EntityNotFound e) {
                    // Turned out it's a class variable.
                    klass = finder.lookupClass(qname.getQualifier());
                }
                SimpleName fieldName = qname.getName();
                ref = klass.lookupField(fieldName);
            }
            this.addInput(ref);
            return ref.getType();

        } else if (expr instanceof ThisExpression) {
            DFVarRef ref = scope.lookupThis();
            this.addInput(ref);
            return ref.getType();

        } else if (expr instanceof BooleanLiteral) {
            return DFBasicType.BOOLEAN;

        } else if (expr instanceof CharacterLiteral) {
            return DFBasicType.CHAR;

        } else if (expr instanceof NullLiteral) {
            return DFNullType.NULL;

        } else if (expr instanceof NumberLiteral) {
            return DFBasicType.NUMBER;

        } else if (expr instanceof StringLiteral) {
            return DFRootTypeSpace.STRING_CLASS;

        } else if (expr instanceof TypeLiteral) {
            return DFBasicType.TYPE;

        } else if (expr instanceof PrefixExpression) {
            PrefixExpression prefix = (PrefixExpression)expr;
            PrefixExpression.Operator op = prefix.getOperator();
            Expression operand = prefix.getOperand();
            if (op == PrefixExpression.Operator.INCREMENT ||
                op == PrefixExpression.Operator.DECREMENT) {
                this.buildAssignment(finder, scope, operand);
            }
            return this.build(finder, scope, operand);

        } else if (expr instanceof PostfixExpression) {
            PostfixExpression postfix = (PostfixExpression)expr;
            PostfixExpression.Operator op = postfix.getOperator();
            Expression operand = postfix.getOperand();
            if (op == PostfixExpression.Operator.INCREMENT ||
                op == PostfixExpression.Operator.DECREMENT) {
                this.buildAssignment(finder, scope, operand);
            }
            return this.build(finder, scope, operand);

        } else if (expr instanceof InfixExpression) {
            InfixExpression infix = (InfixExpression)expr;
            InfixExpression.Operator op = infix.getOperator();
            this.build(finder, scope, infix.getLeftOperand());
            // XXX Todo: implicit type coersion.
            return this.build(finder, scope, infix.getRightOperand());

        } else if (expr instanceof ParenthesizedExpression) {
            ParenthesizedExpression paren = (ParenthesizedExpression)expr;
            return this.build(finder, scope, paren.getExpression());

        } else if (expr instanceof Assignment) {
            Assignment assn = (Assignment)expr;
            Assignment.Operator op = assn.getOperator();
            if (op != Assignment.Operator.ASSIGN) {
                this.build(finder, scope, assn.getLeftHandSide());
            }
            this.buildAssignment(finder, scope, assn.getLeftHandSide());
            return this.build(finder, scope, assn.getRightHandSide());

        } else if (expr instanceof VariableDeclarationExpression) {
            VariableDeclarationExpression decl =
                (VariableDeclarationExpression)expr;
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>) decl.fragments()) {
                DFVarRef ref = scope.lookupVar(frag.getName());
                this.addOutput(ref);
                Expression init = frag.getInitializer();
                if (init != null) {
                    this.build(finder, scope, init);
                }
            }
            return null; // XXX what do?

        } else if (expr instanceof MethodInvocation) {
            MethodInvocation invoke = (MethodInvocation)expr;
            Expression expr1 = invoke.getExpression();
            DFClass klass = null;
            if (expr1 == null) {
                DFVarRef ref = scope.lookupThis();
                this.addInput(ref);
                klass = finder.resolveClass(ref.getType());
            } else {
                if (expr1 instanceof Name) {
                    try {
                        klass = finder.lookupClass((Name)expr1);
                    } catch (EntityNotFound e) {
                    }
                }
                if (klass == null) {
                    DFType type = this.build(finder, scope, expr1);
                    if (type == null) return type;
                    klass = finder.resolveClass(type);
                }
            }
            List<DFType> typeList = new ArrayList<DFType>();
            for (Expression arg : (List<Expression>) invoke.arguments()) {
                DFType type = this.build(finder, scope, arg);
                if (type == null) return type;
                typeList.add(type);
            }
            DFType[] argTypes = new DFType[typeList.size()];
            typeList.toArray(argTypes);
            DFMethod method = klass.lookupMethod(invoke.getName(), argTypes);
            if (method == null) return null;
            return method.getReturnType();

        } else if (expr instanceof SuperMethodInvocation) {
            SuperMethodInvocation sinvoke = (SuperMethodInvocation)expr;
            List<DFType> typeList = new ArrayList<DFType>();
            for (Expression arg : (List<Expression>) sinvoke.arguments()) {
                DFType type = this.build(finder, scope, arg);
                if (type == null) return type;
                typeList.add(type);
            }
            DFType[] argTypes = new DFType[typeList.size()];
            typeList.toArray(argTypes);
            DFClass klass =
                finder.resolveClass(scope.lookupThis().getType());
            DFClass baseKlass = klass.getBase();
            DFMethod method = baseKlass.lookupMethod(sinvoke.getName(), argTypes);
            if (method == null) return null;
            return method.getReturnType();

        } else if (expr instanceof ArrayCreation) {
            ArrayCreation ac = (ArrayCreation)expr;
            for (Expression dim : (List<Expression>) ac.dimensions()) {
                this.build(finder, scope, dim);
            }
            ArrayInitializer init = ac.getInitializer();
            if (init != null) {
                this.build(finder, scope, init);
            }
            return finder.resolve(ac.getType().getElementType());

        } else if (expr instanceof ArrayInitializer) {
            ArrayInitializer init = (ArrayInitializer)expr;
            DFType type = null;
            for (Expression expr1 : (List<Expression>) init.expressions()) {
                type = this.build(finder, scope, expr1);
            }
            return type;

        } else if (expr instanceof ArrayAccess) {
            ArrayAccess aa = (ArrayAccess)expr;
            this.build(finder, scope, aa.getIndex());
            DFType type = this.build(finder, scope, aa.getArray());
            if (type instanceof DFArrayType) {
                type = ((DFArrayType)type).getElemType();
                DFVarRef ref = scope.lookupArray(type);
                this.addInput(ref);
            }
            return type;

        } else if (expr instanceof FieldAccess) {
            FieldAccess fa = (FieldAccess)expr;
            Expression expr1 = fa.getExpression();
            DFClass klass = null;
            if (expr1 instanceof Name) {
                try {
                    klass = finder.lookupClass((Name)expr1);
                } catch (EntityNotFound e) {
                }
            }
            if (klass == null) {
                DFType type = this.build(finder, scope, expr1);
                if (type == null) return type;
                klass = finder.resolveClass(type);
            }
            SimpleName fieldName = fa.getName();
            DFVarRef ref = klass.lookupField(fieldName);
            this.addInput(ref);
            return ref.getType();

        } else if (expr instanceof SuperFieldAccess) {
            SuperFieldAccess sfa = (SuperFieldAccess)expr;
            SimpleName fieldName = sfa.getName();
            DFClass klass =
                finder.resolveClass(scope.lookupThis().getType());
            DFVarRef ref = klass.lookupField(fieldName);
            this.addInput(ref);
            return ref.getType();

        } else if (expr instanceof CastExpression) {
            CastExpression cast = (CastExpression)expr;
            this.build(finder, scope, cast.getExpression());
            return finder.resolve(cast.getType());

        } else if (expr instanceof ClassInstanceCreation) {
            ClassInstanceCreation cstr = (ClassInstanceCreation)expr;
            AnonymousClassDeclaration anonDecl = cstr.getAnonymousClassDeclaration();
            assert(anonDecl == null); // XXX anonymous class unsupported.
            Expression expr1 = cstr.getExpression();
            if (expr1 != null) {
                this.build(finder, scope, expr1);
            }
            for (Expression arg : (List<Expression>) cstr.arguments()) {
                this.build(finder, scope, arg);
            }
            return finder.resolve(cstr.getType());

        } else if (expr instanceof ConditionalExpression) {
            ConditionalExpression cond = (ConditionalExpression)expr;
            this.build(finder, scope, cond.getExpression());
            this.build(finder, scope, cond.getThenExpression());
            return this.build(finder, scope, cond.getElseExpression());

        } else if (expr instanceof InstanceofExpression) {
            return DFBasicType.BOOLEAN;

        } else if (expr instanceof LambdaExpression) {
            LambdaExpression lambda = (LambdaExpression)expr;
            String id = "lambda";
            ASTNode body = lambda.getBody();
            DFTypeSpace anonSpace = new DFTypeSpace(scope.getFullName()+"/"+id);
            DFClass anonKlass = new DFAnonClass(anonSpace, scope, id, null);
            if (body instanceof Statement) {
                // XXX TODO Statement lambda
            } else if (body instanceof Expression) {
                // XXX TODO Expresssion lambda
            } else {
                throw new UnsupportedSyntax(body);
            }
            return anonKlass;

        } else if (expr instanceof MethodReference) {
            // MethodReference
            //  CreationReference
            //  ExpressionMethodReference
            //  SuperMethodReference
            //  TypeMethodReference
            throw new UnsupportedSyntax(expr);

        } else {
            // ???
            throw new UnsupportedSyntax(expr);
        }
    }

    @SuppressWarnings("unchecked")
    public void build(
        DFTypeFinder finder, DFVarScope scope, Statement stmt)
        throws UnsupportedSyntax, EntityNotFound {

        if (stmt instanceof AssertStatement) {

        } else if (stmt instanceof Block) {
            DFVarScope childScope = scope.getChildByAST(stmt);
            Block block = (Block)stmt;
            for (Statement cstmt :
                     (List<Statement>) block.statements()) {
                this.build(finder, childScope, cstmt);
            }

        } else if (stmt instanceof EmptyStatement) {

        } else if (stmt instanceof VariableDeclarationStatement) {
            VariableDeclarationStatement varStmt =
                (VariableDeclarationStatement)stmt;
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>) varStmt.fragments()) {
                DFVarRef ref = scope.lookupVar(frag.getName());
                this.addOutput(ref);
                Expression init = frag.getInitializer();
                if (init != null) {
                    this.build(finder, scope, init);
                }
            }

        } else if (stmt instanceof ExpressionStatement) {
            ExpressionStatement exprStmt = (ExpressionStatement)stmt;
            this.build(finder, scope, exprStmt.getExpression());

        } else if (stmt instanceof IfStatement) {
            IfStatement ifStmt = (IfStatement)stmt;
            this.build(finder, scope, ifStmt.getExpression());
            Statement thenStmt = ifStmt.getThenStatement();
            DFFrame thenFrame = this.addChild(DFFrame.COND, thenStmt);
            thenFrame.build(finder, scope, thenStmt);
            this.finish(thenFrame);
            Statement elseStmt = ifStmt.getElseStatement();
            if (elseStmt != null) {
                DFFrame elseFrame = this.addChild(DFFrame.COND, elseStmt);
                elseFrame.build(finder, scope, elseStmt);
                this.finish(elseFrame);
            }

        } else if (stmt instanceof SwitchStatement) {
            SwitchStatement switchStmt = (SwitchStatement)stmt;
            this.build(finder, scope, switchStmt.getExpression());
            DFVarScope childScope = scope.getChildByAST(stmt);
            DFFrame childFrame = this.addChild(DFFrame.LOOP, stmt);
            for (Statement cstmt :
                     (List<Statement>) switchStmt.statements()) {
                childFrame.build(finder, childScope, cstmt);
            }
            this.finish(childFrame);

        } else if (stmt instanceof SwitchCase) {
            SwitchCase switchCase = (SwitchCase)stmt;
            Expression expr = switchCase.getExpression();
            if (expr != null) {
                this.build(finder, scope, switchCase.getExpression());
            }

        } else if (stmt instanceof WhileStatement) {
            WhileStatement whileStmt = (WhileStatement)stmt;
            DFVarScope childScope = scope.getChildByAST(stmt);
            DFFrame childFrame = this.addChild(DFFrame.LOOP, stmt);
            childFrame.build(finder, scope, whileStmt.getExpression());
            childFrame.build(finder, childScope, whileStmt.getBody());
            this.finish(childFrame);

        } else if (stmt instanceof DoStatement) {
            DoStatement doStmt = (DoStatement)stmt;
            DFVarScope childScope = scope.getChildByAST(stmt);
            DFFrame childFrame = this.addChild(DFFrame.LOOP, stmt);
            childFrame.build(finder, childScope, doStmt.getBody());
            childFrame.build(finder, scope, doStmt.getExpression());
            this.finish(childFrame);

        } else if (stmt instanceof ForStatement) {
            ForStatement forStmt = (ForStatement)stmt;
            DFVarScope childScope = scope.getChildByAST(stmt);
            for (Expression init : (List<Expression>) forStmt.initializers()) {
                this.build(finder, childScope, init);
            }
            DFFrame childFrame = this.addChild(DFFrame.LOOP, stmt);
            Expression expr = forStmt.getExpression();
            if (expr != null) {
                childFrame.build(finder, childScope, expr);
            }
            childFrame.build(finder, childScope, forStmt.getBody());
            for (Expression update : (List<Expression>) forStmt.updaters()) {
                childFrame.build(finder, childScope, update);
            }
            this.finish(childFrame);

        } else if (stmt instanceof EnhancedForStatement) {
            EnhancedForStatement eForStmt = (EnhancedForStatement)stmt;
            DFVarScope childScope = scope.getChildByAST(stmt);
            SingleVariableDeclaration decl = eForStmt.getParameter();
            DFVarRef ref = childScope.lookupVar(decl.getName());
            //this.addOutput(ref);
            DFFrame childFrame = this.addChild(DFFrame.LOOP, stmt);
            childFrame.build(finder, childScope, eForStmt.getExpression());
            childFrame.build(finder, childScope, eForStmt.getBody());
            this.finish(childFrame);

        } else if (stmt instanceof ReturnStatement) {
            ReturnStatement rtrnStmt = (ReturnStatement)stmt;
            Expression expr = rtrnStmt.getExpression();
            if (expr != null) {
                this.build(finder, scope, expr);
            }
            this.addOutput(scope.lookupReturn());

        } else if (stmt instanceof BreakStatement) {

        } else if (stmt instanceof ContinueStatement) {

        } else if (stmt instanceof LabeledStatement) {
            LabeledStatement labeledStmt = (LabeledStatement)stmt;
            SimpleName labelName = labeledStmt.getLabel();
            String label = labelName.getIdentifier();
            DFFrame childFrame = this.addChild(label, stmt);
            childFrame.build(finder, scope, labeledStmt.getBody());
            this.finish(childFrame);

        } else if (stmt instanceof SynchronizedStatement) {
            SynchronizedStatement syncStmt = (SynchronizedStatement)stmt;
            this.build(finder, scope, syncStmt.getBody());

        } else if (stmt instanceof TryStatement) {
            TryStatement tryStmt = (TryStatement)stmt;
            Block block = tryStmt.getBody();
            DFFrame tryFrame = this.addChild(DFFrame.TRY, stmt);
            tryFrame.build(finder, scope, block);
            this.finish(tryFrame);
            for (CatchClause cc :
                     (List<CatchClause>) tryStmt.catchClauses()) {
                SingleVariableDeclaration decl = cc.getException();
                DFVarScope childScope = scope.getChildByAST(cc);
                DFVarRef ref = childScope.lookupVar(decl.getName());
                //this.addOutput(ref);
                this.build(finder, childScope, cc.getBody());
            }
            Block finBlock = tryStmt.getFinally();
            if (finBlock != null) {
                this.build(finder, scope, finBlock);
            }

        } else if (stmt instanceof ThrowStatement) {
            ThrowStatement throwStmt = (ThrowStatement)stmt;
            this.build(finder, scope, throwStmt.getExpression());
            this.addOutput(scope.lookupException());

        } else if (stmt instanceof ConstructorInvocation) {
            ConstructorInvocation ci = (ConstructorInvocation)stmt;
            for (Expression arg : (List<Expression>) ci.arguments()) {
                this.build(finder, scope, arg);
            }

        } else if (stmt instanceof SuperConstructorInvocation) {
            SuperConstructorInvocation sci = (SuperConstructorInvocation)stmt;
            for (Expression arg : (List<Expression>) sci.arguments()) {
                this.build(finder, scope, arg);
            }

        } else if (stmt instanceof TypeDeclarationStatement) {

        } else {
            throw new UnsupportedSyntax(stmt);

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
        for (DFVarRef ref : this.getInputs()) {
            inputs.append(" "+ref);
        }
        out.println(i2+"inputs:"+inputs);
        StringBuilder outputs = new StringBuilder();
        for (DFVarRef ref : this.getOutputs()) {
            outputs.append(" "+ref);
        }
        out.println(i2+"outputs:"+outputs);
        StringBuilder inouts = new StringBuilder();
        for (DFVarRef ref : this.getInsAndOuts()) {
            inouts.append(" "+ref);
        }
        out.println(i2+"in/outs:"+inouts);
        for (DFFrame frame : _ast2child.values()) {
            frame.dump(out, i2);
        }
        out.println(indent+"}");
    }
}
