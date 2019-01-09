//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;
import org.w3c.dom.*;


//  DFLocalVarScope
//
public class DFLocalVarScope extends DFVarScope {

    private List<DFLocalVarScope> _children =
        new ArrayList<DFLocalVarScope>();
    private Map<String, DFLocalVarScope> _ast2child =
        new HashMap<String, DFLocalVarScope>();

    protected DFLocalVarScope(DFVarScope parent, String name) {
        super(parent, name);
    }

    public DFLocalVarScope(DFVarScope parent, SimpleName name) {
        super(parent, name);
    }

    public DFLocalVarScope getChildByAST(ASTNode ast) {
        String key = Utils.encodeASTNode(ast);
        assert _ast2child.containsKey(key);
        return _ast2child.get(key);
    }

    public DFLocalVarScope[] getChildren() {
        DFLocalVarScope[] scopes = new DFLocalVarScope[_children.size()];
        _children.toArray(scopes);
        return scopes;
    }

    protected DFLocalVarScope addChild(String basename, ASTNode ast) {
        String id = basename + _children.size();
        //Logger.info("DFLocalVarScope.addChild: "+this+": "+id);
        DFLocalVarScope scope = new DFLocalVarScope(this, id);
        _children.add(scope);
        _ast2child.put(Utils.encodeASTNode(ast), scope);
        return scope;
    }

    private DFVarRef addVar(SimpleName name, DFType type) {
        //Logger.info("DFLocalVarScope.addVar: "+this+": "+name+" -> "+type);
        return this.addRef("$"+name.getIdentifier(), type);
    }

    /**
     * Lists all the variables defined inside a method.
     */
    @SuppressWarnings("unchecked")
    public void build(DFTypeFinder finder, MethodDeclaration methodDecl)
        throws UnsupportedSyntax, TypeNotFound {
        //Logger.info("DFLocalVarScope.build: "+this);
        Type returnType = methodDecl.getReturnType2();
        DFType type = (returnType == null)? DFBasicType.VOID : finder.resolve(returnType);
        this.addRef("#return", type);
        this.addRef("#exception", DFBuiltinTypes.getExceptionKlass());
        int i = 0;
        for (SingleVariableDeclaration decl :
                 (List<SingleVariableDeclaration>) methodDecl.parameters()) {
            // XXX Ignore modifiers.
            DFType argType = finder.resolve(decl.getType());
            if (decl.isVarargs()) {
                argType = new DFArrayType(argType, 1);
            }
	    int ndims = decl.getExtraDimensions();
	    if (ndims != 0) {
		argType = new DFArrayType(argType, ndims);
	    }
            this.addRef("#arg"+i, argType);
            this.addVar(decl.getName(), argType);
            i++;
        }
        this.build(finder, methodDecl.getBody());
    }

    public void build(DFTypeFinder finder, Initializer initializer)
        throws UnsupportedSyntax, TypeNotFound {
        this.addRef("#exception", DFBuiltinTypes.getExceptionKlass());
        this.build(finder, initializer.getBody());
    }

    @SuppressWarnings("unchecked")
    public void build(DFTypeFinder finder, Statement ast)
        throws UnsupportedSyntax, TypeNotFound {
        assert ast != null;

        if (ast instanceof AssertStatement) {

        } else if (ast instanceof Block) {
            Block block = (Block)ast;
            DFLocalVarScope childScope = this.getChildByAST(ast);
            for (Statement stmt :
                     (List<Statement>) block.statements()) {
                childScope.build(finder, stmt);
            }

        } else if (ast instanceof EmptyStatement) {

        } else if (ast instanceof VariableDeclarationStatement) {
            VariableDeclarationStatement varStmt =
                (VariableDeclarationStatement)ast;
            // XXX Ignore modifiers.
            DFType varType = finder.resolve(varStmt.getType());
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>) varStmt.fragments()) {
		int ndims = frag.getExtraDimensions();
                this.addVar(frag.getName(),
			    (ndims != 0)? new DFArrayType(varType, ndims) : varType);
                Expression expr = frag.getInitializer();
                if (expr != null) {
                    this.build(finder, expr);
                }
            }

        } else if (ast instanceof ExpressionStatement) {
            ExpressionStatement exprStmt = (ExpressionStatement)ast;
            Expression expr = exprStmt.getExpression();
            this.build(finder, expr);

        } else if (ast instanceof ReturnStatement) {
            ReturnStatement returnStmt = (ReturnStatement)ast;
            Expression expr = returnStmt.getExpression();
            if (expr != null) {
                this.build(finder, expr);
            }

        } else if (ast instanceof IfStatement) {
            IfStatement ifStmt = (IfStatement)ast;
            Expression expr = ifStmt.getExpression();
            this.build(finder, expr);
            Statement thenStmt = ifStmt.getThenStatement();
            this.build(finder, thenStmt);
            Statement elseStmt = ifStmt.getElseStatement();
            if (elseStmt != null) {
                this.build(finder, elseStmt);
            }

        } else if (ast instanceof SwitchStatement) {
            SwitchStatement switchStmt = (SwitchStatement)ast;
            DFLocalVarScope childScope = this.getChildByAST(ast);
            Expression expr = switchStmt.getExpression();
            childScope.build(finder, expr);
            for (Statement stmt :
                     (List<Statement>) switchStmt.statements()) {
                childScope.build(finder, stmt);
            }

        } else if (ast instanceof SwitchCase) {
            SwitchCase switchCase = (SwitchCase)ast;
            Expression expr = switchCase.getExpression();
            if (expr != null) {
                this.build(finder, expr);
            }

        } else if (ast instanceof WhileStatement) {
            WhileStatement whileStmt = (WhileStatement)ast;
            Expression expr = whileStmt.getExpression();
            this.build(finder, expr);
            DFLocalVarScope childScope = this.getChildByAST(ast);
            Statement stmt = whileStmt.getBody();
            childScope.build(finder, stmt);

        } else if (ast instanceof DoStatement) {
            DoStatement doStmt = (DoStatement)ast;
            DFLocalVarScope childScope = this.getChildByAST(ast);
            Statement stmt = doStmt.getBody();
            childScope.build(finder, stmt);
            Expression expr = doStmt.getExpression();
            childScope.build(finder, expr);

        } else if (ast instanceof ForStatement) {
            ForStatement forStmt = (ForStatement)ast;
            DFLocalVarScope childScope = this.getChildByAST(ast);
            for (Expression init :
                     (List<Expression>) forStmt.initializers()) {
                childScope.build(finder, init);
            }
            Expression expr = forStmt.getExpression();
            if (expr != null) {
                childScope.build(finder, expr);
            }
            Statement stmt = forStmt.getBody();
            childScope.build(finder, stmt);
            for (Expression update :
                     (List<Expression>) forStmt.updaters()) {
                childScope.build(finder, update);
            }

        } else if (ast instanceof EnhancedForStatement) {
            EnhancedForStatement eForStmt = (EnhancedForStatement)ast;
            this.build(finder, eForStmt.getExpression());
            DFLocalVarScope childScope = this.getChildByAST(ast);
            SingleVariableDeclaration decl = eForStmt.getParameter();
            // XXX Ignore modifiers.
            DFType varType = finder.resolve(decl.getType());
	    int ndims = decl.getExtraDimensions();
            childScope.addVar(decl.getName(),
			      (ndims != 0)? new DFArrayType(varType, ndims) : varType);
            Statement stmt = eForStmt.getBody();
            childScope.build(finder, stmt);

        } else if (ast instanceof BreakStatement) {

        } else if (ast instanceof ContinueStatement) {

        } else if (ast instanceof LabeledStatement) {
            LabeledStatement labeledStmt = (LabeledStatement)ast;
            SimpleName labelName = labeledStmt.getLabel();
            String label = labelName.getIdentifier();
            Statement stmt = labeledStmt.getBody();
            this.build(finder, stmt);

        } else if (ast instanceof SynchronizedStatement) {
            SynchronizedStatement syncStmt = (SynchronizedStatement)ast;
            Block block = syncStmt.getBody();
            this.build(finder, block);

        } else if (ast instanceof TryStatement) {
            TryStatement tryStmt = (TryStatement)ast;
            DFLocalVarScope childScope = this.getChildByAST(ast);
            for (VariableDeclarationExpression decl :
                     (List<VariableDeclarationExpression>) tryStmt.resources()) {
                childScope.build(finder, decl);
            }
            childScope.build(finder, tryStmt.getBody());
            for (CatchClause cc :
                     (List<CatchClause>) tryStmt.catchClauses()) {
                SingleVariableDeclaration decl = cc.getException();
                DFLocalVarScope catchScope = this.getChildByAST(cc);
                // XXX Ignore modifiers.
                DFType varType = finder.resolve(decl.getType());
		int ndims = decl.getExtraDimensions();
		catchScope.addVar(decl.getName(),
				  (ndims != 0)? new DFArrayType(varType, ndims) : varType);
                catchScope.build(finder, cc.getBody());
            }
            Block finBlock = tryStmt.getFinally();
            if (finBlock != null) {
                this.build(finder, finBlock);
            }

        } else if (ast instanceof ThrowStatement) {
            ThrowStatement throwStmt = (ThrowStatement)ast;
            Expression expr = throwStmt.getExpression();
            if (expr != null) {
                this.build(finder, expr);
            }

        } else if (ast instanceof ConstructorInvocation) {
            ConstructorInvocation ci = (ConstructorInvocation)ast;
            for (Expression expr :
                     (List<Expression>) ci.arguments()) {
                this.build(finder, expr);
            }

        } else if (ast instanceof SuperConstructorInvocation) {
            SuperConstructorInvocation sci = (SuperConstructorInvocation)ast;
            for (Expression expr :
                     (List<Expression>) sci.arguments()) {
                this.build(finder, expr);
            }
        } else if (ast instanceof TypeDeclarationStatement) {

        } else {
            throw new UnsupportedSyntax(ast);

        }
    }

    /**
     * Lists all the variables defined within an expression.
     */
    @SuppressWarnings("unchecked")
    public void build(DFTypeFinder finder, Expression ast)
        throws UnsupportedSyntax, TypeNotFound {
        assert ast != null;

        if (ast instanceof Annotation) {

        } else if (ast instanceof Name) {

        } else if (ast instanceof ThisExpression) {

        } else if (ast instanceof BooleanLiteral) {

        } else if (ast instanceof CharacterLiteral) {

        } else if (ast instanceof NullLiteral) {

        } else if (ast instanceof NumberLiteral) {

        } else if (ast instanceof StringLiteral) {

        } else if (ast instanceof TypeLiteral) {

        } else if (ast instanceof PrefixExpression) {
            PrefixExpression prefix = (PrefixExpression)ast;
            PrefixExpression.Operator op = prefix.getOperator();
            Expression operand = prefix.getOperand();
            this.build(finder, operand);
            if (op == PrefixExpression.Operator.INCREMENT ||
                op == PrefixExpression.Operator.DECREMENT) {
                this.buildLeft(finder, operand);
            }

        } else if (ast instanceof PostfixExpression) {
            PostfixExpression postfix = (PostfixExpression)ast;
            PostfixExpression.Operator op = postfix.getOperator();
            Expression operand = postfix.getOperand();
            this.build(finder, operand);
            if (op == PostfixExpression.Operator.INCREMENT ||
                op == PostfixExpression.Operator.DECREMENT) {
                this.buildLeft(finder, operand);
            }

        } else if (ast instanceof InfixExpression) {
            InfixExpression infix = (InfixExpression)ast;
            InfixExpression.Operator op = infix.getOperator();
            Expression loperand = infix.getLeftOperand();
            this.build(finder, loperand);
            Expression roperand = infix.getRightOperand();
            this.build(finder, roperand);

        } else if (ast instanceof ParenthesizedExpression) {
            ParenthesizedExpression paren = (ParenthesizedExpression)ast;
            this.build(finder, paren.getExpression());

        } else if (ast instanceof Assignment) {
            Assignment assn = (Assignment)ast;
            Assignment.Operator op = assn.getOperator();
            this.buildLeft(finder, assn.getLeftHandSide());
            if (op != Assignment.Operator.ASSIGN) {
                this.build(finder, assn.getLeftHandSide());
            }
            this.build(finder, assn.getRightHandSide());

        } else if (ast instanceof VariableDeclarationExpression) {
            VariableDeclarationExpression decl = (VariableDeclarationExpression)ast;
            // XXX Ignore modifiers.
            DFType varType = finder.resolve(decl.getType());
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>) decl.fragments()) {
		int ndims = frag.getExtraDimensions();
                this.addVar(frag.getName(),
			    (ndims != 0)? new DFArrayType(varType, ndims) : varType);
                Expression expr = frag.getInitializer();
                if (expr != null) {
                    this.build(finder, expr);
                }
            }

        } else if (ast instanceof MethodInvocation) {
            MethodInvocation invoke = (MethodInvocation)ast;
            Expression expr = invoke.getExpression();
            if (expr != null) {
                this.build(finder, expr);
            }
            for (Expression arg :
                     (List<Expression>) invoke.arguments()) {
                this.build(finder, arg);
            }

        } else if (ast instanceof SuperMethodInvocation) {
            SuperMethodInvocation si = (SuperMethodInvocation)ast;
            for (Expression arg :
                     (List<Expression>) si.arguments()) {
                this.build(finder, arg);
            }

        } else if (ast instanceof ArrayCreation) {
            ArrayCreation ac = (ArrayCreation)ast;
            for (Expression dim :
                     (List<Expression>) ac.dimensions()) {
                this.build(finder, dim);
            }
            ArrayInitializer init = ac.getInitializer();
            if (init != null) {
                this.build(finder, init);
            }

        } else if (ast instanceof ArrayInitializer) {
            ArrayInitializer init = (ArrayInitializer)ast;
            for (Expression expr :
                     (List<Expression>) init.expressions()) {
                this.build(finder, expr);
            }

        } else if (ast instanceof ArrayAccess) {
            ArrayAccess aa = (ArrayAccess)ast;
            this.build(finder, aa.getArray());
            this.build(finder, aa.getIndex());

        } else if (ast instanceof FieldAccess) {
            FieldAccess fa = (FieldAccess)ast;
            SimpleName fieldName = fa.getName();
            this.build(finder, fa.getExpression());

        } else if (ast instanceof SuperFieldAccess) {
            SuperFieldAccess sfa = (SuperFieldAccess)ast;
            SimpleName fieldName = sfa.getName();

        } else if (ast instanceof CastExpression) {
            CastExpression cast = (CastExpression)ast;
            this.build(finder, cast.getExpression());

        } else if (ast instanceof ClassInstanceCreation) {
            ClassInstanceCreation cstr = (ClassInstanceCreation)ast;
            Expression expr = cstr.getExpression();
            if (expr != null) {
                this.build(finder, expr);
            }
            for (Expression arg :
                     (List<Expression>) cstr.arguments()) {
                this.build(finder, arg);
            }
            // XXX Ignored getAnonymousClassDeclaration().

        } else if (ast instanceof ConditionalExpression) {
            ConditionalExpression cond = (ConditionalExpression)ast;
            this.build(finder, cond.getExpression());
            this.build(finder, cond.getThenExpression());
            this.build(finder, cond.getElseExpression());

        } else if (ast instanceof InstanceofExpression) {
            InstanceofExpression instof = (InstanceofExpression)ast;
            this.build(finder, instof.getLeftOperand());

        } else {
            // LambdaExpression
            // MethodReference
            //  CreationReference
            //  ExpressionMethodReference
            //  SuperMethodReference
            //  TypeMethodReference
            throw new UnsupportedSyntax(ast);

        }
    }

    /**
     * Lists all the l-values for an expression.
     */
    @SuppressWarnings("unchecked")
    public void buildLeft(DFTypeFinder finder, Expression ast)
        throws UnsupportedSyntax, TypeNotFound {
        assert ast != null;

        if (ast instanceof Name) {

        } else if (ast instanceof ArrayAccess) {
            ArrayAccess aa = (ArrayAccess)ast;
            this.build(finder, aa.getArray());
            this.build(finder, aa.getIndex());

        } else if (ast instanceof FieldAccess) {
            FieldAccess fa = (FieldAccess)ast;
            SimpleName fieldName = fa.getName();
            this.build(finder, fa.getExpression());

        } else if (ast instanceof SuperFieldAccess) {
            SuperFieldAccess sfa = (SuperFieldAccess)ast;
            SimpleName fieldName = sfa.getName();

        } else {
            throw new UnsupportedSyntax(ast);

        }
    }
}
