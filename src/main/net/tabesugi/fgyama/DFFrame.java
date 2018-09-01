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

    public DFFrame addChild(String label, ASTNode ast) {
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
        if (label == null) return this;
        DFFrame frame = this;
        while (frame.getParent() != null) {
            if (frame.getLabel() != null &&
                frame.getLabel().equals(label)) break;
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
        _exits.add(exit);
    }

    public void finish(DFComponent cpt) {
        for (DFExit exit : _exits) {
            if (exit.getFrame() == this) {
                DFNode node = exit.getNode();
                node.finish(cpt);
                cpt.set(node);
            }
        }
    }

    private void buildAssignment(
        DFTypeFinder finder, DFVarSpace varSpace, Expression expr)
        throws UnsupportedSyntax, EntityNotFound {
        if (expr instanceof Name) {
            Name name = (Name)expr;
            DFVarRef ref;
            if (name.isSimpleName()) {
                ref = varSpace.lookupVarOrField((SimpleName)name);
            } else {
                QualifiedName qname = (QualifiedName)name;
                DFClassSpace klass;
                try {
                    // Try assuming it's a variable access.
                    DFType type = this.build(finder, varSpace, qname.getQualifier());
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
            DFType type = this.build(finder, varSpace, aa.getArray());
            this.build(finder, varSpace, aa.getIndex());
            if (type == null) return;
            DFVarRef ref = varSpace.lookupArray(type);
            this.addOutput(ref);

        } else if (expr instanceof FieldAccess) {
            FieldAccess fa = (FieldAccess)expr;
            Expression expr1 = fa.getExpression();
            DFClassSpace klass = null;
            if (expr1 instanceof Name) {
                try {
                    klass = finder.lookupClass((Name)expr1);
                } catch (EntityNotFound e) {
                }
            }
            if (klass == null) {
                DFType type = this.build(finder, varSpace, expr1);
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
    public DFType build(
        DFTypeFinder finder, DFVarSpace varSpace, Expression expr)
        throws UnsupportedSyntax, EntityNotFound {
        if (expr instanceof Annotation) {
            return null;

        } else if (expr instanceof Name) {
            Name name = (Name)expr;
            DFVarRef ref;
            if (name.isSimpleName()) {
                ref = varSpace.lookupVarOrField((SimpleName)name);
            } else {
                QualifiedName qname = (QualifiedName)name;
                DFClassSpace klass;
                try {
                    // Try assuming it's a variable access.
                    DFType type = this.build(finder, varSpace, qname.getQualifier());
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
            DFVarRef ref = varSpace.lookupThis();
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
            return new DFClassType(DFRootTypeSpace.STRING_CLASS);

        } else if (expr instanceof TypeLiteral) {
            return DFBasicType.TYPE;

        } else if (expr instanceof PrefixExpression) {
            PrefixExpression prefix = (PrefixExpression)expr;
            PrefixExpression.Operator op = prefix.getOperator();
            Expression operand = prefix.getOperand();
            if (op == PrefixExpression.Operator.INCREMENT ||
                op == PrefixExpression.Operator.DECREMENT) {
                this.buildAssignment(finder, varSpace, operand);
            }
            return this.build(finder, varSpace, operand);

        } else if (expr instanceof PostfixExpression) {
            PostfixExpression postfix = (PostfixExpression)expr;
            PostfixExpression.Operator op = postfix.getOperator();
            Expression operand = postfix.getOperand();
            if (op == PostfixExpression.Operator.INCREMENT ||
                op == PostfixExpression.Operator.DECREMENT) {
                this.buildAssignment(finder, varSpace, operand);
            }
            return this.build(finder, varSpace, operand);

        } else if (expr instanceof InfixExpression) {
            InfixExpression infix = (InfixExpression)expr;
            InfixExpression.Operator op = infix.getOperator();
            this.build(finder, varSpace, infix.getLeftOperand());
            // XXX Todo: implicit type coersion.
            return this.build(finder, varSpace, infix.getRightOperand());

        } else if (expr instanceof ParenthesizedExpression) {
            ParenthesizedExpression paren = (ParenthesizedExpression)expr;
            return this.build(finder, varSpace, paren.getExpression());

        } else if (expr instanceof Assignment) {
            Assignment assn = (Assignment)expr;
            Assignment.Operator op = assn.getOperator();
            if (op != Assignment.Operator.ASSIGN) {
                this.build(finder, varSpace, assn.getLeftHandSide());
            }
            this.buildAssignment(finder, varSpace, assn.getLeftHandSide());
            return this.build(finder, varSpace, assn.getRightHandSide());

        } else if (expr instanceof VariableDeclarationExpression) {
            VariableDeclarationExpression decl =
                (VariableDeclarationExpression)expr;
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>) decl.fragments()) {
                DFVarRef ref = varSpace.lookupVar(frag.getName());
                this.addOutput(ref);
                Expression init = frag.getInitializer();
                if (init != null) {
                    this.build(finder, varSpace, init);
                }
            }
            return null; // XXX what do?

        } else if (expr instanceof MethodInvocation) {
            MethodInvocation invoke = (MethodInvocation)expr;
            Expression expr1 = invoke.getExpression();
            DFClassSpace klass = null;
            if (expr1 == null) {
                DFVarRef ref = varSpace.lookupThis();
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
                    DFType type = this.build(finder, varSpace, expr1);
                    if (type == null) return type;
                    klass = finder.resolveClass(type);
                }
            }
            List<DFType> typeList = new ArrayList<DFType>();
            for (Expression arg : (List<Expression>) invoke.arguments()) {
                DFType type = this.build(finder, varSpace, arg);
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
                DFType type = this.build(finder, varSpace, arg);
                if (type == null) return type;
                typeList.add(type);
            }
            DFType[] argTypes = new DFType[typeList.size()];
            typeList.toArray(argTypes);
            DFClassSpace klass =
                finder.resolveClass(varSpace.lookupThis().getType());
            DFClassSpace baseKlass = klass.getBase();
            DFMethod method = baseKlass.lookupMethod(sinvoke.getName(), argTypes);
            if (method == null) return null;
            return method.getReturnType();

        } else if (expr instanceof ArrayCreation) {
            ArrayCreation ac = (ArrayCreation)expr;
            for (Expression dim : (List<Expression>) ac.dimensions()) {
                this.build(finder, varSpace, dim);
            }
            ArrayInitializer init = ac.getInitializer();
            if (init != null) {
                this.build(finder, varSpace, init);
            }
            return finder.resolve(ac.getType().getElementType());

        } else if (expr instanceof ArrayInitializer) {
            ArrayInitializer init = (ArrayInitializer)expr;
            DFType type = null;
            for (Expression expr1 : (List<Expression>) init.expressions()) {
                type = this.build(finder, varSpace, expr1);
            }
            return type;

        } else if (expr instanceof ArrayAccess) {
            ArrayAccess aa = (ArrayAccess)expr;
            this.build(finder, varSpace, aa.getIndex());
            DFType type = this.build(finder, varSpace, aa.getArray());
            if (type == null) return type;
            DFVarRef ref = varSpace.lookupArray(type);
            this.addInput(ref);
            return type;

        } else if (expr instanceof FieldAccess) {
            FieldAccess fa = (FieldAccess)expr;
            Expression expr1 = fa.getExpression();
            DFClassSpace klass = null;
            if (expr1 instanceof Name) {
                try {
                    klass = finder.lookupClass((Name)expr1);
                } catch (EntityNotFound e) {
                }
            }
            if (klass == null) {
                DFType type = this.build(finder, varSpace, expr1);
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
            DFClassSpace klass =
                finder.resolveClass(varSpace.lookupThis().getType());
            DFVarRef ref = klass.lookupField(fieldName);
            this.addInput(ref);
            return ref.getType();

        } else if (expr instanceof CastExpression) {
            CastExpression cast = (CastExpression)expr;
            this.build(finder, varSpace, cast.getExpression());
            return finder.resolve(cast.getType());

        } else if (expr instanceof ClassInstanceCreation) {
            ClassInstanceCreation cstr = (ClassInstanceCreation)expr;
            AnonymousClassDeclaration anonDecl = cstr.getAnonymousClassDeclaration();
            assert(anonDecl == null); // XXX anonymous class unsupported.
            Expression expr1 = cstr.getExpression();
            if (expr1 != null) {
                this.build(finder, varSpace, expr1);
            }
            for (Expression arg : (List<Expression>) cstr.arguments()) {
                this.build(finder, varSpace, arg);
            }
            return finder.resolve(cstr.getType());

        } else if (expr instanceof ConditionalExpression) {
            ConditionalExpression cond = (ConditionalExpression)expr;
            this.build(finder, varSpace, cond.getExpression());
            this.build(finder, varSpace, cond.getThenExpression());
            return this.build(finder, varSpace, cond.getElseExpression());

        } else if (expr instanceof InstanceofExpression) {
            return DFBasicType.BOOLEAN;

        } else if (expr instanceof LambdaExpression) {
            LambdaExpression lambda = (LambdaExpression)expr;
            String id = "lambda";
            ASTNode body = lambda.getBody();
            DFTypeSpace anonSpace = new DFTypeSpace(varSpace.getFullName()+"/"+id);
            DFClassSpace anonKlass = new DFAnonClassSpace(
                anonSpace, varSpace, id, null);
            if (body instanceof Statement) {
                // XXX TODO Statement lambda
            } else if (body instanceof Expression) {
                // XXX TODO Expresssion lambda
            } else {
                throw new UnsupportedSyntax(body);
            }
            return new DFClassType(anonKlass);

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
        DFTypeFinder finder, DFVarSpace varSpace, Statement stmt)
        throws UnsupportedSyntax, EntityNotFound {

        if (stmt instanceof AssertStatement) {

        } else if (stmt instanceof Block) {
            DFVarSpace childSpace = varSpace.getChildByAST(stmt);
            Block block = (Block)stmt;
            for (Statement cstmt :
                     (List<Statement>) block.statements()) {
                this.build(finder, childSpace, cstmt);
            }

        } else if (stmt instanceof EmptyStatement) {

        } else if (stmt instanceof VariableDeclarationStatement) {
            VariableDeclarationStatement varStmt =
                (VariableDeclarationStatement)stmt;
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>) varStmt.fragments()) {
                DFVarRef ref = varSpace.lookupVar(frag.getName());
                this.addOutput(ref);
                Expression init = frag.getInitializer();
                if (init != null) {
                    this.build(finder, varSpace, init);
                }
            }

        } else if (stmt instanceof ExpressionStatement) {
            ExpressionStatement exprStmt = (ExpressionStatement)stmt;
            this.build(finder, varSpace, exprStmt.getExpression());

        } else if (stmt instanceof ReturnStatement) {
            this.addOutput(varSpace.lookupReturn());

        } else if (stmt instanceof IfStatement) {
            IfStatement ifStmt = (IfStatement)stmt;
            this.build(finder, varSpace, ifStmt.getExpression());
            Statement thenStmt = ifStmt.getThenStatement();
            DFFrame thenFrame = this.addChild(DFFrame.COND, thenStmt);
            thenFrame.build(finder, varSpace, thenStmt);
            Statement elseStmt = ifStmt.getElseStatement();
            if (elseStmt != null) {
                DFFrame elseFrame = this.addChild(DFFrame.COND, elseStmt);
                elseFrame.build(finder, varSpace, elseStmt);
            }

        } else if (stmt instanceof SwitchStatement) {
            SwitchStatement switchStmt = (SwitchStatement)stmt;
            this.build(finder, varSpace, switchStmt.getExpression());
            DFVarSpace childSpace = varSpace.getChildByAST(stmt);
            DFFrame childFrame = this.addChild(DFFrame.LOOP, stmt);
            for (Statement cstmt :
                     (List<Statement>) switchStmt.statements()) {
                childFrame.build(finder, childSpace, cstmt);
            }

        } else if (stmt instanceof SwitchCase) {
            SwitchCase switchCase = (SwitchCase)stmt;
            Expression expr = switchCase.getExpression();
            if (expr != null) {
                this.build(finder, varSpace, switchCase.getExpression());
            }

        } else if (stmt instanceof WhileStatement) {
            WhileStatement whileStmt = (WhileStatement)stmt;
            this.build(finder, varSpace, whileStmt.getExpression());
            DFVarSpace childSpace = varSpace.getChildByAST(stmt);
            DFFrame childFrame = this.addChild(DFFrame.LOOP, stmt);
            childFrame.build(finder, childSpace, whileStmt.getBody());

        } else if (stmt instanceof DoStatement) {
            DoStatement doStmt = (DoStatement)stmt;
            this.build(finder, varSpace, doStmt.getExpression());
            DFVarSpace childSpace = varSpace.getChildByAST(stmt);
            DFFrame childFrame = this.addChild(DFFrame.LOOP, stmt);
            childFrame.build(finder, childSpace, doStmt.getBody());

        } else if (stmt instanceof ForStatement) {
            ForStatement forStmt = (ForStatement)stmt;
            DFVarSpace childSpace = varSpace.getChildByAST(stmt);
            for (Expression init : (List<Expression>) forStmt.initializers()) {
                this.build(finder, childSpace, init);
            }
            DFFrame childFrame = this.addChild(DFFrame.LOOP, stmt);
            childFrame.build(finder, childSpace, forStmt.getExpression());
            childFrame.build(finder, childSpace, forStmt.getBody());
            for (Expression update : (List<Expression>) forStmt.updaters()) {
                childFrame.build(finder, childSpace, update);
            }

        } else if (stmt instanceof EnhancedForStatement) {
            EnhancedForStatement eForStmt = (EnhancedForStatement)stmt;
            DFVarSpace childSpace = varSpace.getChildByAST(stmt);
            DFFrame childFrame = this.addChild(DFFrame.LOOP, stmt);
            childFrame.build(finder, childSpace, eForStmt.getExpression());
            childFrame.build(finder, childSpace, eForStmt.getBody());

        } else if (stmt instanceof BreakStatement) {

        } else if (stmt instanceof ContinueStatement) {

        } else if (stmt instanceof LabeledStatement) {
            LabeledStatement labeledStmt = (LabeledStatement)stmt;
            SimpleName labelName = labeledStmt.getLabel();
            String label = labelName.getIdentifier();
            DFFrame childFrame = this.addChild(label, stmt);
            childFrame.build(finder, varSpace, labeledStmt.getBody());

        } else if (stmt instanceof SynchronizedStatement) {
            SynchronizedStatement syncStmt = (SynchronizedStatement)stmt;
            this.build(finder, varSpace, syncStmt.getBody());

        } else if (stmt instanceof TryStatement) {
            TryStatement tryStmt = (TryStatement)stmt;
            Block block = tryStmt.getBody();
            DFFrame tryFrame = this.addChild(DFFrame.TRY, stmt);
            tryFrame.build(finder, varSpace, block);
            for (CatchClause cc :
                     (List<CatchClause>) tryStmt.catchClauses()) {
                // XXX add catch variables.
                DFVarSpace childSpace = varSpace.getChildByAST(cc);
                this.build(finder, childSpace, cc.getBody());
            }
            Block finBlock = tryStmt.getFinally();
            if (finBlock != null) {
                this.build(finder, varSpace, finBlock);
            }

        } else if (stmt instanceof ThrowStatement) {
            ThrowStatement throwStmt = (ThrowStatement)stmt;
            this.build(finder, varSpace, throwStmt.getExpression());
            this.addOutput(varSpace.lookupException());

        } else if (stmt instanceof ConstructorInvocation) {
            ConstructorInvocation ci = (ConstructorInvocation)stmt;
            for (Expression arg : (List<Expression>) ci.arguments()) {
                this.build(finder, varSpace, arg);
            }

        } else if (stmt instanceof SuperConstructorInvocation) {
            SuperConstructorInvocation sci = (SuperConstructorInvocation)stmt;
            for (Expression arg : (List<Expression>) sci.arguments()) {
                this.build(finder, varSpace, arg);
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
        for (DFVarRef ref : _inputs) {
            inputs.append(" "+ref);
        }
        out.println(i2+"inputs:"+inputs);
        StringBuilder outputs = new StringBuilder();
        for (DFVarRef ref : _outputs) {
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
