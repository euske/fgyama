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

    private SortedMap<String, DFLocalVarScope> _ast2child =
        new TreeMap<String, DFLocalVarScope>();

    protected DFLocalVarScope(DFVarScope outer, String name) {
        super(outer, name);
    }

    public DFLocalVarScope(DFVarScope outer, SimpleName name) {
        super(outer, name);
    }

    public DFLocalVarScope getChildByAST(ASTNode ast) {
        String key = Utils.encodeASTNode(ast);
        assert _ast2child.containsKey(key);
        return _ast2child.get(key);
    }

    public DFLocalVarScope[] getChildren() {
        DFLocalVarScope[] scopes = new DFLocalVarScope[_ast2child.size()];
        _ast2child.values().toArray(scopes);
        return scopes;
    }

    protected DFLocalVarScope addChild(ASTNode ast) {
        String id = Utils.encodeASTNode(ast);
        //Logger.info("DFLocalVarScope.addChild:", this, ":", id);
        DFLocalVarScope scope = new DFLocalVarScope(this, id);
        _ast2child.put(id, scope);
        return scope;
    }

    private DFRef addVar(SimpleName name, DFType type) {
        //Logger.info("DFLocalVarScope.addVar:", this, ":", name, "->", type);
        return this.addRef("$"+name.getIdentifier(), type);
    }

    public DFRef lookupArgument(int index)
        throws VariableNotFound {
        try {
            return this.lookupRef("#arg"+index);
        } catch (VariableNotFound e) {
            return super.lookupArgument(index);
        }
    }

    public DFRef lookupReturn()
        throws VariableNotFound {
        try {
            return this.lookupRef("#return");
        } catch (VariableNotFound e) {
            return super.lookupReturn();
        }
    }

    public DFRef lookupException()
        throws VariableNotFound {
        try {
            return this.lookupRef("#exception");
        } catch (VariableNotFound e) {
            return super.lookupException();
        }
    }

    /**
     * Lists all the variables defined inside a method.
     */
    @SuppressWarnings("unchecked")
    public void buildMethodDecl(
        DFTypeFinder finder, MethodDeclaration methodDecl)
        throws InvalidSyntax {
        //Logger.info("DFLocalVarScope.build:", this);
        if (methodDecl.getBody() == null) return;
        Type returnType = methodDecl.getReturnType2();
        DFType type;
	if (returnType == null) {
	    type = DFBasicType.VOID;
	} else {
	    type = finder.resolveSafe(returnType);
	}
        this.addRef("#return", type, null);
        this.addRef("#exception", DFBuiltinTypes.getExceptionKlass(), null);
        int i = 0;
        for (SingleVariableDeclaration decl :
                 (List<SingleVariableDeclaration>) methodDecl.parameters()) {
            DFType argType = finder.resolveSafe(decl.getType());
            if (decl.isVarargs()) {
                argType = new DFArrayType(argType, 1);
            }
	    int ndims = decl.getExtraDimensions();
	    if (ndims != 0) {
		argType = new DFArrayType(argType, ndims);
	    }
            this.addRef("#arg"+i, argType, null);
            this.addVar(decl.getName(), argType);
            i++;
        }
        this.buildStmt(finder, methodDecl.getBody());
    }

    public void buildInitializer(
        DFTypeFinder finder, Initializer initializer)
        throws InvalidSyntax {
        this.addRef("#exception", DFBuiltinTypes.getExceptionKlass(), null);
        this.buildStmt(finder, initializer.getBody());
    }

    @SuppressWarnings("unchecked")
    private void buildStmt(DFTypeFinder finder, Statement ast)
        throws InvalidSyntax {
        assert ast != null;

        if (ast instanceof AssertStatement) {

        } else if (ast instanceof Block) {
            Block block = (Block)ast;
            DFLocalVarScope innerScope = this.getChildByAST(ast);
            for (Statement stmt :
                     (List<Statement>) block.statements()) {
                innerScope.buildStmt(finder, stmt);
            }

        } else if (ast instanceof EmptyStatement) {

        } else if (ast instanceof VariableDeclarationStatement) {
            VariableDeclarationStatement varStmt =
                (VariableDeclarationStatement)ast;
            DFType varType = finder.resolveSafe(varStmt.getType());
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>) varStmt.fragments()) {
		int ndims = frag.getExtraDimensions();
                this.addVar(frag.getName(),
			    (ndims != 0)? new DFArrayType(varType, ndims) : varType);
                Expression expr = frag.getInitializer();
                if (expr != null) {
                    this.buildExpr(finder, expr);
                }
            }

        } else if (ast instanceof ExpressionStatement) {
            ExpressionStatement exprStmt = (ExpressionStatement)ast;
            Expression expr = exprStmt.getExpression();
            this.buildExpr(finder, expr);

        } else if (ast instanceof ReturnStatement) {
            ReturnStatement returnStmt = (ReturnStatement)ast;
            Expression expr = returnStmt.getExpression();
            if (expr != null) {
                this.buildExpr(finder, expr);
            }

        } else if (ast instanceof IfStatement) {
            IfStatement ifStmt = (IfStatement)ast;
            Expression expr = ifStmt.getExpression();
            this.buildExpr(finder, expr);
            Statement thenStmt = ifStmt.getThenStatement();
            this.buildStmt(finder, thenStmt);
            Statement elseStmt = ifStmt.getElseStatement();
            if (elseStmt != null) {
                this.buildStmt(finder, elseStmt);
            }

        } else if (ast instanceof SwitchStatement) {
            SwitchStatement switchStmt = (SwitchStatement)ast;
            DFLocalVarScope innerScope = this.getChildByAST(ast);
            Expression expr = switchStmt.getExpression();
            innerScope.buildExpr(finder, expr);
            for (Statement stmt :
                     (List<Statement>) switchStmt.statements()) {
                innerScope.buildStmt(finder, stmt);
            }

        } else if (ast instanceof SwitchCase) {
            SwitchCase switchCase = (SwitchCase)ast;
            Expression expr = switchCase.getExpression();
            if (expr != null) {
                this.buildExpr(finder, expr);
            }

        } else if (ast instanceof WhileStatement) {
            WhileStatement whileStmt = (WhileStatement)ast;
            Expression expr = whileStmt.getExpression();
            this.buildExpr(finder, expr);
            DFLocalVarScope innerScope = this.getChildByAST(ast);
            Statement stmt = whileStmt.getBody();
            innerScope.buildStmt(finder, stmt);

        } else if (ast instanceof DoStatement) {
            DoStatement doStmt = (DoStatement)ast;
            DFLocalVarScope innerScope = this.getChildByAST(ast);
            Statement stmt = doStmt.getBody();
            innerScope.buildStmt(finder, stmt);
            Expression expr = doStmt.getExpression();
            innerScope.buildExpr(finder, expr);

        } else if (ast instanceof ForStatement) {
            ForStatement forStmt = (ForStatement)ast;
            DFLocalVarScope innerScope = this.getChildByAST(ast);
            for (Expression init :
                     (List<Expression>) forStmt.initializers()) {
                innerScope.buildExpr(finder, init);
            }
            Expression expr = forStmt.getExpression();
            if (expr != null) {
                innerScope.buildExpr(finder, expr);
            }
            Statement stmt = forStmt.getBody();
            innerScope.buildStmt(finder, stmt);
            for (Expression update :
                     (List<Expression>) forStmt.updaters()) {
                innerScope.buildExpr(finder, update);
            }

        } else if (ast instanceof EnhancedForStatement) {
            EnhancedForStatement eForStmt = (EnhancedForStatement)ast;
            this.buildExpr(finder, eForStmt.getExpression());
            DFLocalVarScope innerScope = this.getChildByAST(ast);
            SingleVariableDeclaration decl = eForStmt.getParameter();
            DFType varType = finder.resolveSafe(decl.getType());
	    int ndims = decl.getExtraDimensions();
            innerScope.addVar(decl.getName(),
			      (ndims != 0)? new DFArrayType(varType, ndims) : varType);
            Statement stmt = eForStmt.getBody();
            innerScope.buildStmt(finder, stmt);

        } else if (ast instanceof BreakStatement) {

        } else if (ast instanceof ContinueStatement) {

        } else if (ast instanceof LabeledStatement) {
            LabeledStatement labeledStmt = (LabeledStatement)ast;
            Statement stmt = labeledStmt.getBody();
            this.buildStmt(finder, stmt);

        } else if (ast instanceof SynchronizedStatement) {
            SynchronizedStatement syncStmt = (SynchronizedStatement)ast;
            this.buildExpr(finder, syncStmt.getExpression());
            this.buildStmt(finder, syncStmt.getBody());

        } else if (ast instanceof TryStatement) {
            TryStatement tryStmt = (TryStatement)ast;
            DFLocalVarScope innerScope = this.getChildByAST(ast);
            for (VariableDeclarationExpression decl :
                     (List<VariableDeclarationExpression>) tryStmt.resources()) {
                innerScope.buildExpr(finder, decl);
            }
            innerScope.buildStmt(finder, tryStmt.getBody());
            for (CatchClause cc :
                     (List<CatchClause>) tryStmt.catchClauses()) {
                SingleVariableDeclaration decl = cc.getException();
                DFLocalVarScope catchScope = this.getChildByAST(cc);
		DFType varType = finder.resolveSafe(decl.getType());
		int ndims = decl.getExtraDimensions();
                if (ndims != 0) {
                    varType = new DFArrayType(varType, ndims);
                }
		catchScope.addVar(decl.getName(), varType);
                catchScope.buildStmt(finder, cc.getBody());
            }
            Block finBlock = tryStmt.getFinally();
            if (finBlock != null) {
                this.buildStmt(finder, finBlock);
            }

        } else if (ast instanceof ThrowStatement) {
            ThrowStatement throwStmt = (ThrowStatement)ast;
            Expression expr = throwStmt.getExpression();
            if (expr != null) {
                this.buildExpr(finder, expr);
            }

        } else if (ast instanceof ConstructorInvocation) {
            ConstructorInvocation ci = (ConstructorInvocation)ast;
            for (Expression expr :
                     (List<Expression>) ci.arguments()) {
                this.buildExpr(finder, expr);
            }

        } else if (ast instanceof SuperConstructorInvocation) {
            SuperConstructorInvocation sci = (SuperConstructorInvocation)ast;
            for (Expression expr :
                     (List<Expression>) sci.arguments()) {
                this.buildExpr(finder, expr);
            }
        } else if (ast instanceof TypeDeclarationStatement) {
            // Inline classes are processed separately.

        } else {
            // XXX Unsupported.
        }
    }

    /**
     * Lists all the variables defined within an expression.
     */
    @SuppressWarnings("unchecked")
    private void buildExpr(DFTypeFinder finder, Expression ast)
        throws InvalidSyntax {
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
            this.buildExpr(finder, operand);
            if (op == PrefixExpression.Operator.INCREMENT ||
                op == PrefixExpression.Operator.DECREMENT) {
                this.buildAssignment(finder, operand);
            }

        } else if (ast instanceof PostfixExpression) {
            PostfixExpression postfix = (PostfixExpression)ast;
            PostfixExpression.Operator op = postfix.getOperator();
            Expression operand = postfix.getOperand();
            this.buildExpr(finder, operand);
            if (op == PostfixExpression.Operator.INCREMENT ||
                op == PostfixExpression.Operator.DECREMENT) {
                this.buildAssignment(finder, operand);
            }

        } else if (ast instanceof InfixExpression) {
            InfixExpression infix = (InfixExpression)ast;
            InfixExpression.Operator op = infix.getOperator();
            Expression loperand = infix.getLeftOperand();
            this.buildExpr(finder, loperand);
            Expression roperand = infix.getRightOperand();
            this.buildExpr(finder, roperand);

        } else if (ast instanceof ParenthesizedExpression) {
            ParenthesizedExpression paren = (ParenthesizedExpression)ast;
            this.buildExpr(finder, paren.getExpression());

        } else if (ast instanceof Assignment) {
            Assignment assn = (Assignment)ast;
            Assignment.Operator op = assn.getOperator();
            this.buildAssignment(finder, assn.getLeftHandSide());
            if (op != Assignment.Operator.ASSIGN) {
                this.buildExpr(finder, assn.getLeftHandSide());
            }
            this.buildExpr(finder, assn.getRightHandSide());

        } else if (ast instanceof VariableDeclarationExpression) {
            VariableDeclarationExpression decl = (VariableDeclarationExpression)ast;
	    DFType varType = finder.resolveSafe(decl.getType());
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>) decl.fragments()) {
                DFType vt = varType;
		int ndims = frag.getExtraDimensions();
                if (ndims != 0) {
                    vt = new DFArrayType(vt, ndims);
                }
                this.addVar(frag.getName(), vt);
                Expression expr = frag.getInitializer();
                if (expr != null) {
                    this.buildExpr(finder, expr);
                }
            }

        } else if (ast instanceof MethodInvocation) {
            MethodInvocation invoke = (MethodInvocation)ast;
            Expression expr = invoke.getExpression();
            if (expr != null) {
                this.buildExpr(finder, expr);
            }
            for (Expression arg :
                     (List<Expression>) invoke.arguments()) {
                this.buildExpr(finder, arg);
            }

        } else if (ast instanceof SuperMethodInvocation) {
            SuperMethodInvocation si = (SuperMethodInvocation)ast;
            for (Expression arg :
                     (List<Expression>) si.arguments()) {
                this.buildExpr(finder, arg);
            }

        } else if (ast instanceof ArrayCreation) {
            ArrayCreation ac = (ArrayCreation)ast;
            for (Expression dim :
                     (List<Expression>) ac.dimensions()) {
                this.buildExpr(finder, dim);
            }
            ArrayInitializer init = ac.getInitializer();
            if (init != null) {
                this.buildExpr(finder, init);
            }

        } else if (ast instanceof ArrayInitializer) {
            ArrayInitializer init = (ArrayInitializer)ast;
            for (Expression expr :
                     (List<Expression>) init.expressions()) {
                this.buildExpr(finder, expr);
            }

        } else if (ast instanceof ArrayAccess) {
            ArrayAccess aa = (ArrayAccess)ast;
            this.buildExpr(finder, aa.getArray());
            this.buildExpr(finder, aa.getIndex());

        } else if (ast instanceof FieldAccess) {
            FieldAccess fa = (FieldAccess)ast;
            SimpleName fieldName = fa.getName();
            this.buildExpr(finder, fa.getExpression());

        } else if (ast instanceof SuperFieldAccess) {
            SuperFieldAccess sfa = (SuperFieldAccess)ast;
            SimpleName fieldName = sfa.getName();

        } else if (ast instanceof CastExpression) {
            CastExpression cast = (CastExpression)ast;
            this.buildExpr(finder, cast.getExpression());

        } else if (ast instanceof ClassInstanceCreation) {
            ClassInstanceCreation cstr = (ClassInstanceCreation)ast;
            Expression expr = cstr.getExpression();
            if (expr != null) {
                this.buildExpr(finder, expr);
            }
            for (Expression arg :
                     (List<Expression>) cstr.arguments()) {
                this.buildExpr(finder, arg);
            }
            // Anonymous classes are processed separately.

        } else if (ast instanceof ConditionalExpression) {
            ConditionalExpression cond = (ConditionalExpression)ast;
            this.buildExpr(finder, cond.getExpression());
            this.buildExpr(finder, cond.getThenExpression());
            this.buildExpr(finder, cond.getElseExpression());

        } else if (ast instanceof InstanceofExpression) {
            InstanceofExpression instof = (InstanceofExpression)ast;
            this.buildExpr(finder, instof.getLeftOperand());

        } else {
            // LambdaExpression
            // MethodReference
            //  CreationReference
            //  ExpressionMethodReference
            //  SuperMethodReference
            //  TypeMethodReference
            // XXX Unsupported.
        }
    }

    /**
     * Lists all the l-values for an expression.
     */
    @SuppressWarnings("unchecked")
    private void buildAssignment(DFTypeFinder finder, Expression ast)
        throws InvalidSyntax {
        assert ast != null;

        if (ast instanceof Name) {

        } else if (ast instanceof ArrayAccess) {
            ArrayAccess aa = (ArrayAccess)ast;
            this.buildExpr(finder, aa.getArray());
            this.buildExpr(finder, aa.getIndex());

        } else if (ast instanceof FieldAccess) {
            FieldAccess fa = (FieldAccess)ast;
            SimpleName fieldName = fa.getName();
            this.buildExpr(finder, fa.getExpression());

        } else if (ast instanceof SuperFieldAccess) {
            SuperFieldAccess sfa = (SuperFieldAccess)ast;
            SimpleName fieldName = sfa.getName();

        } else {
            // XXX Unsupported.
        }
    }
}
