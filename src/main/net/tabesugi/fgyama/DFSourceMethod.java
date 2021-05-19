//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import javax.xml.stream.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFSourceMethod
//  DFMethod defined in source code.
//
//  Usage:
//    1. new DFSourceMethod(finder)
//    2. listUsedKlasses()
//    3. listDefinedKlasses()
//    4. expandRefs()
//    5. getDFGraph()
//
public abstract class DFSourceMethod extends DFMethod {

    private DFSourceKlass _srcklass;
    private DFVarScope _outerScope;
    private DFTypeFinder _finder;
    private MethodScope _methodScope;

    private ConsistentHashSet<DFRef> _inputRefs = new ConsistentHashSet<DFRef>();
    private ConsistentHashSet<DFRef> _outputRefs = new ConsistentHashSet<DFRef>();

    // Normal constructor.
    protected DFSourceMethod(
        DFSourceKlass srcklass, CallStyle callStyle, boolean isAbstract,
        String methodId, String methodName,
        DFVarScope outerScope, DFTypeFinder finder) {
        super(srcklass, callStyle, isAbstract, methodId, methodName);

        _srcklass = srcklass;
        _outerScope = outerScope;
        _finder = new DFTypeFinder(this, finder);
        _methodScope = new MethodScope(_outerScope, this.getMethodId());
    }

    // Constructor for a parameterized method.
    protected DFSourceMethod(
        DFSourceMethod genericMethod, Map<String, DFKlass> paramTypes) {
        super(genericMethod, paramTypes);

        _srcklass = genericMethod._srcklass;
        _outerScope = genericMethod._outerScope;
        _finder = new DFTypeFinder(this, genericMethod._finder);
        _methodScope = new MethodScope(_outerScope, this.getMethodId());
    }

    protected DFTypeFinder getFinder() {
        return _finder;
    }

    public DFSourceKlass getSourceKlass() {
        return _srcklass;
    }

    public MethodScope getScope() {
        return _methodScope;
    }

    public Collection<DFRef> getInputRefs() {
        return _inputRefs;
    }

    public Collection<DFRef> getOutputRefs() {
        return _outputRefs;
    }

    /// TypeSpace construction.

    @SuppressWarnings("unchecked")
    protected void buildTypeFromStmt(
        Statement stmt, DFLocalScope outerScope)
        throws InvalidSyntax, EntityDuplicate {
        assert stmt != null;

        if (stmt instanceof AssertStatement) {

        } else if (stmt instanceof Block) {
            Block block = (Block)stmt;
            DFLocalScope innerScope = outerScope.addChild(stmt);
            for (Statement stmt1 :
                     (List<Statement>) block.statements()) {
                this.buildTypeFromStmt(stmt1, innerScope);
            }

        } else if (stmt instanceof EmptyStatement) {

        } else if (stmt instanceof VariableDeclarationStatement) {
            VariableDeclarationStatement varStmt =
                (VariableDeclarationStatement)stmt;
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>) varStmt.fragments()) {
                Expression expr = frag.getInitializer();
                if (expr != null) {
                    this.buildTypeFromExpr(expr, outerScope);
                }
            }

        } else if (stmt instanceof ExpressionStatement) {
            ExpressionStatement exprStmt = (ExpressionStatement)stmt;
            this.buildTypeFromExpr(exprStmt.getExpression(), outerScope);

        } else if (stmt instanceof ReturnStatement) {
            ReturnStatement returnStmt = (ReturnStatement)stmt;
            Expression expr = returnStmt.getExpression();
            if (expr != null) {
                this.buildTypeFromExpr(expr, outerScope);
            }

        } else if (stmt instanceof IfStatement) {
            IfStatement ifStmt = (IfStatement)stmt;
            this.buildTypeFromExpr(ifStmt.getExpression(), outerScope);
            Statement thenStmt = ifStmt.getThenStatement();
            this.buildTypeFromStmt(thenStmt, outerScope);
            Statement elseStmt = ifStmt.getElseStatement();
            if (elseStmt != null) {
                this.buildTypeFromStmt(elseStmt, outerScope);
            }

        } else if (stmt instanceof SwitchStatement) {
            SwitchStatement switchStmt = (SwitchStatement)stmt;
            DFLocalScope innerScope = outerScope.addChild(stmt);
            this.buildTypeFromExpr(switchStmt.getExpression(), innerScope);
            for (Statement stmt1 :
                     (List<Statement>) switchStmt.statements()) {
                this.buildTypeFromStmt(stmt1, innerScope);
            }

        } else if (stmt instanceof SwitchCase) {
            SwitchCase switchCase = (SwitchCase)stmt;
            for (Expression expr :
                     (List<Expression>) switchCase.expressions()) {
                this.buildTypeFromExpr(expr, outerScope);
            }

        } else if (stmt instanceof WhileStatement) {
            WhileStatement whileStmt = (WhileStatement)stmt;
            this.buildTypeFromExpr(whileStmt.getExpression(), outerScope);
            DFLocalScope innerScope = outerScope.addChild(stmt);
            this.buildTypeFromStmt(whileStmt.getBody(), innerScope);

        } else if (stmt instanceof DoStatement) {
            DoStatement doStmt = (DoStatement)stmt;
            DFLocalScope innerScope = outerScope.addChild(stmt);
            this.buildTypeFromStmt(doStmt.getBody(), innerScope);
            this.buildTypeFromExpr(doStmt.getExpression(), innerScope);

        } else if (stmt instanceof ForStatement) {
            ForStatement forStmt = (ForStatement)stmt;
            DFLocalScope innerScope = outerScope.addChild(stmt);
            for (Expression init :
                     (List<Expression>) forStmt.initializers()) {
                this.buildTypeFromExpr(init, innerScope);
            }
            Expression expr = forStmt.getExpression();
            if (expr != null) {
                this.buildTypeFromExpr(expr, innerScope);
            }
            this.buildTypeFromStmt(forStmt.getBody(), innerScope);
            for (Expression update :
                     (List<Expression>) forStmt.updaters()) {
                this.buildTypeFromExpr(update, innerScope);
            }

        } else if (stmt instanceof EnhancedForStatement) {
            EnhancedForStatement eForStmt = (EnhancedForStatement)stmt;
            this.buildTypeFromExpr(eForStmt.getExpression(), outerScope);
            DFLocalScope innerScope = outerScope.addChild(stmt);
            this.buildTypeFromStmt(eForStmt.getBody(), innerScope);

        } else if (stmt instanceof BreakStatement) {

        } else if (stmt instanceof ContinueStatement) {

        } else if (stmt instanceof LabeledStatement) {
            LabeledStatement labeledStmt = (LabeledStatement)stmt;
            this.buildTypeFromStmt(labeledStmt.getBody(), outerScope);

        } else if (stmt instanceof SynchronizedStatement) {
            SynchronizedStatement syncStmt = (SynchronizedStatement)stmt;
            this.buildTypeFromExpr(syncStmt.getExpression(), outerScope);
            this.buildTypeFromStmt(syncStmt.getBody(), outerScope);

        } else if (stmt instanceof TryStatement) {
            TryStatement tryStmt = (TryStatement)stmt;
            DFLocalScope innerScope = outerScope.addChild(stmt);
            for (VariableDeclarationExpression decl :
                     (List<VariableDeclarationExpression>) tryStmt.resources()) {
                this.buildTypeFromExpr(decl, innerScope);
            }
            this.buildTypeFromStmt(tryStmt.getBody(), innerScope);
            for (CatchClause cc :
                     (List<CatchClause>) tryStmt.catchClauses()) {
                DFLocalScope catchScope = outerScope.addChild(cc);
                this.buildTypeFromStmt(cc.getBody(), catchScope);
            }
            Block finBlock = tryStmt.getFinally();
            if (finBlock != null) {
                this.buildTypeFromStmt(finBlock, outerScope);
            }

        } else if (stmt instanceof ThrowStatement) {
            ThrowStatement throwStmt = (ThrowStatement)stmt;
            Expression expr = throwStmt.getExpression();
            if (expr != null) {
                this.buildTypeFromExpr(expr, outerScope);
            }

        } else if (stmt instanceof ConstructorInvocation) {
            ConstructorInvocation ci = (ConstructorInvocation)stmt;
            for (Expression expr :
                     (List<Expression>) ci.arguments()) {
                this.buildTypeFromExpr(expr, outerScope);
            }

        } else if (stmt instanceof SuperConstructorInvocation) {
            SuperConstructorInvocation sci = (SuperConstructorInvocation)stmt;
            for (Expression expr :
                     (List<Expression>) sci.arguments()) {
                this.buildTypeFromExpr(expr, outerScope);
            }

        } else if (stmt instanceof TypeDeclarationStatement) {
            TypeDeclarationStatement typeDeclStmt = (TypeDeclarationStatement)stmt;
            AbstractTypeDeclaration abstTypeDecl = typeDeclStmt.getDeclaration();
            String id = abstTypeDecl.getName().getIdentifier();
            DFSourceKlass klass = new DFTypeDeclKlass(
                abstTypeDecl, this, _srcklass, outerScope,
                _srcklass.getFilePath(), _srcklass.isAnalyze());
            klass.initializeFinder(_finder);
            try {
                this.addKlass(id, klass);
            } catch (TypeDuplicate e) {
                e.setAst(abstTypeDecl);
                throw e;
            }

        } else {
            throw new InvalidSyntax(stmt);

        }
    }

    @SuppressWarnings("unchecked")
    protected void buildTypeFromExpr(
        Expression expr, DFVarScope outerScope)
        throws InvalidSyntax, EntityDuplicate {
        assert expr != null;

        if (expr instanceof Annotation) {

        } else if (expr instanceof Name) {

        } else if (expr instanceof ThisExpression) {

        } else if (expr instanceof BooleanLiteral) {

        } else if (expr instanceof CharacterLiteral) {

        } else if (expr instanceof NullLiteral) {

        } else if (expr instanceof NumberLiteral) {

        } else if (expr instanceof StringLiteral) {

        } else if (expr instanceof TypeLiteral) {

        } else if (expr instanceof PrefixExpression) {
            PrefixExpression prefix = (PrefixExpression)expr;
            this.buildTypeFromExpr(prefix.getOperand(), outerScope);

        } else if (expr instanceof PostfixExpression) {
            PostfixExpression postfix = (PostfixExpression)expr;
            this.buildTypeFromExpr(postfix.getOperand(), outerScope);

        } else if (expr instanceof InfixExpression) {
            InfixExpression infix = (InfixExpression)expr;
            this.buildTypeFromExpr(infix.getLeftOperand(), outerScope);
            this.buildTypeFromExpr(infix.getRightOperand(), outerScope);

        } else if (expr instanceof ParenthesizedExpression) {
            ParenthesizedExpression paren = (ParenthesizedExpression)expr;
            this.buildTypeFromExpr(paren.getExpression(), outerScope);

        } else if (expr instanceof Assignment) {
            Assignment assn = (Assignment)expr;
            this.buildTypeFromExpr(assn.getLeftHandSide(), outerScope);
            this.buildTypeFromExpr(assn.getRightHandSide(), outerScope);

        } else if (expr instanceof VariableDeclarationExpression) {
            VariableDeclarationExpression decl =
                (VariableDeclarationExpression)expr;
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>) decl.fragments()) {
                Expression init = frag.getInitializer();
                if (init != null) {
                    this.buildTypeFromExpr(init, outerScope);
                }
            }

        } else if (expr instanceof MethodInvocation) {
            MethodInvocation invoke = (MethodInvocation)expr;
            Expression expr1 = invoke.getExpression();
            if (expr1 != null) {
                this.buildTypeFromExpr(expr1, outerScope);
            }
            for (Expression arg : (List<Expression>) invoke.arguments()) {
                this.buildTypeFromExpr(arg, outerScope);
            }

        } else if (expr instanceof SuperMethodInvocation) {
            SuperMethodInvocation sinvoke = (SuperMethodInvocation)expr;
            for (Expression arg : (List<Expression>) sinvoke.arguments()) {
                this.buildTypeFromExpr(arg, outerScope);
            }

        } else if (expr instanceof ArrayCreation) {
            ArrayCreation ac = (ArrayCreation)expr;
            for (Expression dim : (List<Expression>) ac.dimensions()) {
                this.buildTypeFromExpr(dim, outerScope);
            }
            ArrayInitializer init = ac.getInitializer();
            if (init != null) {
                this.buildTypeFromExpr(init, outerScope);
            }

        } else if (expr instanceof ArrayInitializer) {
            ArrayInitializer init = (ArrayInitializer)expr;
            for (Expression expr1 : (List<Expression>) init.expressions()) {
                this.buildTypeFromExpr(expr1, outerScope);
            }

        } else if (expr instanceof ArrayAccess) {
            ArrayAccess aa = (ArrayAccess)expr;
            this.buildTypeFromExpr(aa.getIndex(), outerScope);
            this.buildTypeFromExpr(aa.getArray(), outerScope);

        } else if (expr instanceof FieldAccess) {
            FieldAccess fa = (FieldAccess)expr;
            this.buildTypeFromExpr(fa.getExpression(), outerScope);

        } else if (expr instanceof SuperFieldAccess) {

        } else if (expr instanceof CastExpression) {
            CastExpression cast = (CastExpression)expr;
            this.buildTypeFromExpr(cast.getExpression(), outerScope);

        } else if (expr instanceof ClassInstanceCreation) {
            ClassInstanceCreation cstr = (ClassInstanceCreation)expr;
            Expression expr1 = cstr.getExpression();
            if (expr1 != null) {
                this.buildTypeFromExpr(expr1, outerScope);
            }
            for (Expression arg : (List<Expression>) cstr.arguments()) {
                this.buildTypeFromExpr(arg, outerScope);
            }
            if (cstr.getAnonymousClassDeclaration() != null) {
                String id = Utils.encodeASTNode(cstr);
                DFSourceKlass anonKlass = new AnonymousKlass(
                    cstr, this, _srcklass, outerScope);
                anonKlass.initializeFinder(_finder);
                try {
                    this.addKlass(id, anonKlass);
                } catch (TypeDuplicate e) {
                    e.setAst(cstr);
                    throw e;
                }
            }

        } else if (expr instanceof ConditionalExpression) {
            ConditionalExpression cond = (ConditionalExpression)expr;
            this.buildTypeFromExpr(cond.getExpression(), outerScope);
            this.buildTypeFromExpr(cond.getThenExpression(), outerScope);
            this.buildTypeFromExpr(cond.getElseExpression(), outerScope);

        } else if (expr instanceof InstanceofExpression) {

        } else if (expr instanceof LambdaExpression) {
            LambdaExpression lambda = (LambdaExpression)expr;
            String id = Utils.encodeASTNode(lambda);
            DFSourceKlass lambdaKlass = new DFLambdaKlass(
                lambda, this, _srcklass, outerScope);
            lambdaKlass.initializeFinder(_finder);
            try {
                this.addKlass(id, lambdaKlass);
            } catch (TypeDuplicate e) {
                e.setAst(lambda);
                throw e;
            }

        } else if (expr instanceof MethodReference) {
            //  CreationReference
            //  ExpressionMethodReference
            //  SuperMethodReference
            //  TypeMethodReference
            MethodReference methodref = (MethodReference)expr;
            String id = Utils.encodeASTNode(methodref);
            DFSourceKlass methodRefKlass = new DFMethodRefKlass(
                methodref, this, _srcklass, outerScope);
            methodRefKlass.initializeFinder(_finder);
            try {
                this.addKlass(id, methodRefKlass);
            } catch (TypeDuplicate e) {
                e.setAst(methodref);
                throw e;
            }

        } else {
            // ???
            throw new InvalidSyntax(expr);
        }
    }

    /// Enumerate klasses.

    @SuppressWarnings("unchecked")
    protected void listUsedStmt(
        Collection<DFSourceKlass> klasses, Statement stmt)
        throws InvalidSyntax {
        assert stmt != null;

        if (stmt instanceof AssertStatement) {

        } else if (stmt instanceof Block) {
            Block block = (Block)stmt;
            for (Statement stmt1 :
                     (List<Statement>) block.statements()) {
                this.listUsedStmt(klasses, stmt1);
            }

        } else if (stmt instanceof EmptyStatement) {

        } else if (stmt instanceof VariableDeclarationStatement) {
            VariableDeclarationStatement varStmt =
                (VariableDeclarationStatement)stmt;
            DFType varType = _finder.resolveSafe(varStmt.getType());
            if (varType instanceof DFSourceKlass) {
                ((DFSourceKlass)varType).listUsedKlasses(klasses);
            }
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>) varStmt.fragments()) {
                Expression expr = frag.getInitializer();
                if (expr != null) {
                    this.listUsedExpr(klasses, expr);
                }
            }

        } else if (stmt instanceof ExpressionStatement) {
            ExpressionStatement exprStmt = (ExpressionStatement)stmt;
            Expression expr = exprStmt.getExpression();
            this.listUsedExpr(klasses, expr);

        } else if (stmt instanceof ReturnStatement) {
            ReturnStatement returnStmt = (ReturnStatement)stmt;
            Expression expr = returnStmt.getExpression();
            if (expr != null) {
                this.listUsedExpr(klasses, expr);
            }

        } else if (stmt instanceof IfStatement) {
            IfStatement ifStmt = (IfStatement)stmt;
            Expression expr = ifStmt.getExpression();
            this.listUsedExpr(klasses, expr);
            Statement thenStmt = ifStmt.getThenStatement();
            this.listUsedStmt(klasses, thenStmt);
            Statement elseStmt = ifStmt.getElseStatement();
            if (elseStmt != null) {
                this.listUsedStmt(klasses, elseStmt);
            }

        } else if (stmt instanceof SwitchStatement) {
            SwitchStatement switchStmt = (SwitchStatement)stmt;
            Expression expr = switchStmt.getExpression();
            this.listUsedExpr(klasses, expr);
            for (Statement stmt1 :
                     (List<Statement>) switchStmt.statements()) {
                this.listUsedStmt(klasses, stmt1);
            }

        } else if (stmt instanceof SwitchCase) {
            SwitchCase switchCase = (SwitchCase)stmt;
            for (Expression expr :
                     (List<Expression>) switchCase.expressions()) {
                this.listUsedExpr(klasses, expr);
            }

        } else if (stmt instanceof WhileStatement) {
            WhileStatement whileStmt = (WhileStatement)stmt;
            this.listUsedExpr(klasses, whileStmt.getExpression());
            this.listUsedStmt(klasses, whileStmt.getBody());

        } else if (stmt instanceof DoStatement) {
            DoStatement doStmt = (DoStatement)stmt;
            this.listUsedStmt(klasses, doStmt.getBody());
            this.listUsedExpr(klasses, doStmt.getExpression());

        } else if (stmt instanceof ForStatement) {
            ForStatement forStmt = (ForStatement)stmt;
            for (Expression init :
                     (List<Expression>) forStmt.initializers()) {
                this.listUsedExpr(klasses, init);
            }
            Expression expr = forStmt.getExpression();
            if (expr != null) {
                this.listUsedExpr(klasses, expr);
            }
            this.listUsedStmt(klasses, forStmt.getBody());
            for (Expression update :
                     (List<Expression>) forStmt.updaters()) {
                this.listUsedExpr(klasses, update);
            }

        } else if (stmt instanceof EnhancedForStatement) {
            EnhancedForStatement eForStmt = (EnhancedForStatement)stmt;
            this.listUsedExpr(klasses, eForStmt.getExpression());
            SingleVariableDeclaration decl = eForStmt.getParameter();
            DFType varType = _finder.resolveSafe(decl.getType());
            if (varType instanceof DFSourceKlass) {
                ((DFSourceKlass)varType).listUsedKlasses(klasses);
            }
            this.listUsedStmt(klasses, eForStmt.getBody());

        } else if (stmt instanceof BreakStatement) {

        } else if (stmt instanceof ContinueStatement) {

        } else if (stmt instanceof LabeledStatement) {
            LabeledStatement labeledStmt = (LabeledStatement)stmt;
            this.listUsedStmt(klasses, labeledStmt.getBody());

        } else if (stmt instanceof SynchronizedStatement) {
            SynchronizedStatement syncStmt = (SynchronizedStatement)stmt;
            this.listUsedExpr(klasses, syncStmt.getExpression());
            this.listUsedStmt(klasses, syncStmt.getBody());

        } else if (stmt instanceof TryStatement) {
            TryStatement tryStmt = (TryStatement)stmt;
            for (VariableDeclarationExpression decl :
                     (List<VariableDeclarationExpression>) tryStmt.resources()) {
                this.listUsedExpr(klasses, decl);
            }
            this.listUsedStmt(klasses, tryStmt.getBody());
            for (CatchClause cc :
                     (List<CatchClause>) tryStmt.catchClauses()) {
                SingleVariableDeclaration decl = cc.getException();
                DFType varType = _finder.resolveSafe(decl.getType());
                if (varType instanceof DFSourceKlass) {
                    ((DFSourceKlass)varType).listUsedKlasses(klasses);
                }
                this.listUsedStmt(klasses, cc.getBody());
            }
            Block finBlock = tryStmt.getFinally();
            if (finBlock != null) {
                this.listUsedStmt(klasses, finBlock);
            }

        } else if (stmt instanceof ThrowStatement) {
            ThrowStatement throwStmt = (ThrowStatement)stmt;
            Expression expr = throwStmt.getExpression();
            if (expr != null) {
                this.listUsedExpr(klasses, expr);
            }

        } else if (stmt instanceof ConstructorInvocation) {
            ConstructorInvocation ci = (ConstructorInvocation)stmt;
            for (Expression expr :
                     (List<Expression>) ci.arguments()) {
                this.listUsedExpr(klasses, expr);
            }

        } else if (stmt instanceof SuperConstructorInvocation) {
            SuperConstructorInvocation sci = (SuperConstructorInvocation)stmt;
            for (Expression expr :
                     (List<Expression>) sci.arguments()) {
                this.listUsedExpr(klasses, expr);
            }

        } else if (stmt instanceof TypeDeclarationStatement) {
            TypeDeclarationStatement decl = (TypeDeclarationStatement)stmt;
            AbstractTypeDeclaration abstDecl = decl.getDeclaration();
            DFKlass innerType = this.getKlass(abstDecl.getName());
            if (innerType instanceof DFSourceKlass) {
                ((DFSourceKlass)innerType).listUsedKlasses(klasses);
            }

        } else {
            throw new InvalidSyntax(stmt);

        }
    }

    @SuppressWarnings("unchecked")
    protected void listUsedExpr(
        Collection<DFSourceKlass> klasses, Expression expr)
        throws InvalidSyntax {
        assert expr != null;

        if (expr instanceof Annotation) {

        } else if (expr instanceof Name) {

        } else if (expr instanceof ThisExpression) {
            // "this"
            ThisExpression thisExpr = (ThisExpression)expr;
            Name name = thisExpr.getQualifier();
            if (name != null) {
                try {
                    DFKlass klass = _finder.resolveKlass(name);
                    if (klass instanceof DFSourceKlass) {
                        ((DFSourceKlass)klass).listUsedKlasses(klasses);
                    }
                } catch (TypeNotFound e) {
                }
            }

        } else if (expr instanceof BooleanLiteral) {

        } else if (expr instanceof CharacterLiteral) {

        } else if (expr instanceof NullLiteral) {

        } else if (expr instanceof NumberLiteral) {

        } else if (expr instanceof StringLiteral) {

        } else if (expr instanceof TypeLiteral) {
            Type value = ((TypeLiteral)expr).getType();
            try {
                DFType type = _finder.resolve(value);
                if (type instanceof DFSourceKlass) {
                    ((DFSourceKlass)type).listUsedKlasses(klasses);
                }
            } catch (TypeNotFound e) {
            }

        } else if (expr instanceof PrefixExpression) {
            PrefixExpression prefix = (PrefixExpression)expr;
            PrefixExpression.Operator op = prefix.getOperator();
            Expression operand = prefix.getOperand();
            this.listUsedExpr(klasses, operand);

        } else if (expr instanceof PostfixExpression) {
            PostfixExpression postfix = (PostfixExpression)expr;
            PostfixExpression.Operator op = postfix.getOperator();
            Expression operand = postfix.getOperand();
            this.listUsedExpr(klasses, operand);

        } else if (expr instanceof InfixExpression) {
            InfixExpression infix = (InfixExpression)expr;
            InfixExpression.Operator op = infix.getOperator();
            Expression loperand = infix.getLeftOperand();
            this.listUsedExpr(klasses, loperand);
            Expression roperand = infix.getRightOperand();
            this.listUsedExpr(klasses, roperand);

        } else if (expr instanceof ParenthesizedExpression) {
            ParenthesizedExpression paren = (ParenthesizedExpression)expr;
            this.listUsedExpr(klasses, paren.getExpression());

        } else if (expr instanceof Assignment) {
            Assignment assn = (Assignment)expr;
            Assignment.Operator op = assn.getOperator();
            this.listUsedExpr(klasses, assn.getLeftHandSide());
            if (op != Assignment.Operator.ASSIGN) {
                this.listUsedExpr(klasses, assn.getLeftHandSide());
            }
            this.listUsedExpr(klasses, assn.getRightHandSide());

        } else if (expr instanceof VariableDeclarationExpression) {
            VariableDeclarationExpression decl = (VariableDeclarationExpression)expr;
            DFType varType = _finder.resolveSafe(decl.getType());
            if (varType instanceof DFSourceKlass) {
                ((DFSourceKlass)varType).listUsedKlasses(klasses);
            }
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>) decl.fragments()) {
                Expression init = frag.getInitializer();
                if (init != null) {
                    this.listUsedExpr(klasses, init);
                }
            }

        } else if (expr instanceof MethodInvocation) {
            MethodInvocation invoke = (MethodInvocation)expr;
            Expression expr1 = invoke.getExpression();
            if (expr1 instanceof Name) {
                try {
                    DFKlass klass = _finder.resolveKlass((Name)expr1);
                    if (klass instanceof DFSourceKlass) {
                        ((DFSourceKlass)klass).listUsedKlasses(klasses);
                    }
                } catch (TypeNotFound e) {
                }
            } else if (expr1 != null) {
                this.listUsedExpr(klasses, expr1);
            }
            for (Expression arg :
                     (List<Expression>) invoke.arguments()) {
                this.listUsedExpr(klasses, arg);
            }

        } else if (expr instanceof SuperMethodInvocation) {
            SuperMethodInvocation si = (SuperMethodInvocation)expr;
            for (Expression arg :
                     (List<Expression>) si.arguments()) {
                this.listUsedExpr(klasses, arg);
            }

        } else if (expr instanceof ArrayCreation) {
            ArrayCreation ac = (ArrayCreation)expr;
            for (Expression dim :
                     (List<Expression>) ac.dimensions()) {
                this.listUsedExpr(klasses, dim);
            }
            ArrayInitializer init = ac.getInitializer();
            if (init != null) {
                this.listUsedExpr(klasses, init);
            }
            DFType type = _finder.resolveSafe(ac.getType().getElementType());
            if (type instanceof DFSourceKlass) {
                ((DFSourceKlass)type).listUsedKlasses(klasses);
            }

        } else if (expr instanceof ArrayInitializer) {
            ArrayInitializer init = (ArrayInitializer)expr;
            for (Expression expr1 :
                     (List<Expression>) init.expressions()) {
                this.listUsedExpr(klasses, expr1);
            }

        } else if (expr instanceof ArrayAccess) {
            ArrayAccess aa = (ArrayAccess)expr;
            this.listUsedExpr(klasses, aa.getArray());
            this.listUsedExpr(klasses, aa.getIndex());

        } else if (expr instanceof FieldAccess) {
            FieldAccess fa = (FieldAccess)expr;
            SimpleName fieldName = fa.getName();
            this.listUsedExpr(klasses, fa.getExpression());

        } else if (expr instanceof SuperFieldAccess) {
            SuperFieldAccess sfa = (SuperFieldAccess)expr;
            SimpleName fieldName = sfa.getName();

        } else if (expr instanceof CastExpression) {
            CastExpression cast = (CastExpression)expr;
            this.listUsedExpr(klasses, cast.getExpression());
            DFType type = _finder.resolveSafe(cast.getType());
            if (type instanceof DFSourceKlass) {
                ((DFSourceKlass)type).listUsedKlasses(klasses);
            }

        } else if (expr instanceof ClassInstanceCreation) {
            ClassInstanceCreation cstr = (ClassInstanceCreation)expr;
            try {
                DFKlass instKlass;
                if (cstr.getAnonymousClassDeclaration() != null) {
                    String id = Utils.encodeASTNode(cstr);
                    instKlass = this.getKlass(id);
                } else {
                    instKlass = _finder.resolve(cstr.getType()).toKlass();
                }
                if (instKlass instanceof DFSourceKlass) {
                    ((DFSourceKlass)instKlass).listUsedKlasses(klasses);
                }
            } catch (TypeNotFound e) {
            }
            Expression expr1 = cstr.getExpression();
            if (expr1 != null) {
                this.listUsedExpr(klasses, expr1);
            }
            for (Expression arg :
                     (List<Expression>) cstr.arguments()) {
                this.listUsedExpr(klasses, arg);
            }

        } else if (expr instanceof ConditionalExpression) {
            ConditionalExpression cond = (ConditionalExpression)expr;
            this.listUsedExpr(klasses, cond.getExpression());
            this.listUsedExpr(klasses, cond.getThenExpression());
            this.listUsedExpr(klasses, cond.getElseExpression());

        } else if (expr instanceof InstanceofExpression) {
            InstanceofExpression instof = (InstanceofExpression)expr;
            this.listUsedExpr(klasses, instof.getLeftOperand());

        } else if (expr instanceof LambdaExpression) {
            LambdaExpression lambda = (LambdaExpression)expr;
            String id = Utils.encodeASTNode(lambda);
            DFSourceKlass lambdaKlass = (DFSourceKlass)this.getKlass(id);
            // Do not use lambda klasses until defined.
            //lambdaKlass.listUsedKlasses(klasses);

        } else if (expr instanceof MethodReference) {
            //  CreationReference
            //  ExpressionMethodReference
            //  SuperMethodReference
            //  TypeMethodReference
            MethodReference methodref = (MethodReference)expr;
            String id = Utils.encodeASTNode(methodref);
            DFSourceKlass methodRefKlass = (DFSourceKlass)this.getKlass(id);
            // Do not use methodref klasses until defined.
            //methodRefKlass.listUsedKlasses(klasses);

        } else {
            throw new InvalidSyntax(expr);
        }
    }

    /// Enumerate References.

    @SuppressWarnings("unchecked")
    protected void listDefinedStmt(
        Collection<DFSourceKlass> defined,
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
                this.listDefinedStmt(defined, innerScope, cstmt);
            }

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
                    Expression init = frag.getInitializer();
                    if (init != null) {
                        DFType type = this.listDefinedExpr(
                            defined, scope, init, ref.getRefType());
                        ref.setRefType(type);
                    }
                } catch (VariableNotFound e) {
                }
            }

        } else if (stmt instanceof ExpressionStatement) {
            // "foo();"
            ExpressionStatement exprStmt = (ExpressionStatement)stmt;
            this.listDefinedExpr(defined, scope, exprStmt.getExpression());

        } else if (stmt instanceof IfStatement) {
            // "if (c) { ... } else { ... }"
            IfStatement ifStmt = (IfStatement)stmt;
            this.listDefinedExpr(defined, scope, ifStmt.getExpression());
            Statement thenStmt = ifStmt.getThenStatement();
            this.listDefinedStmt(defined, scope, thenStmt);
            Statement elseStmt = ifStmt.getElseStatement();
            if (elseStmt != null) {
                this.listDefinedStmt(defined, scope, elseStmt);
            }

        } else if (stmt instanceof SwitchStatement) {
            // "switch (x) { case 0: ...; }"
            SwitchStatement switchStmt = (SwitchStatement)stmt;
            DFType type = this.listDefinedExpr(
                defined, scope, switchStmt.getExpression());
            if (type == null) {
                type = DFUnknownType.UNKNOWN;
            }
            DFKlass enumKlass = null;
            if (type instanceof DFKlass &&
                ((DFKlass)type).isEnum()) {
                enumKlass = type.toKlass();
            }
            DFLocalScope innerScope = scope.getChildByAST(stmt);
            for (Statement cstmt : (List<Statement>) switchStmt.statements()) {
                if (cstmt instanceof SwitchCase) {
                    SwitchCase switchCase = (SwitchCase)cstmt;
                    for (Expression expr1 :
                             (List<Expression>) switchCase.expressions()) {
                        if (enumKlass != null && expr1 instanceof SimpleName) {
                            // special treatment for enum.
                            DFRef ref = enumKlass.getField((SimpleName)expr1);
                            if (ref != null) {
                                this.addInputRef(ref);
                            }
                        } else {
                            this.listDefinedExpr(defined, innerScope, expr1);
                        }
                    }
                } else {
                    this.listDefinedStmt(defined, innerScope, cstmt);
                }
            }

        } else if (stmt instanceof SwitchCase) {
            // Invalid "case" placement.
            throw new InvalidSyntax(stmt);

        } else if (stmt instanceof WhileStatement) {
            // "while (c) { ... }"
            WhileStatement whileStmt = (WhileStatement)stmt;
            DFLocalScope innerScope = scope.getChildByAST(stmt);
            this.listDefinedExpr(defined, scope, whileStmt.getExpression());
            this.listDefinedStmt(defined, innerScope, whileStmt.getBody());

        } else if (stmt instanceof DoStatement) {
            // "do { ... } while (c);"
            DoStatement doStmt = (DoStatement)stmt;
            DFLocalScope innerScope = scope.getChildByAST(stmt);
            this.listDefinedStmt(defined, innerScope, doStmt.getBody());
            this.listDefinedExpr(defined, scope, doStmt.getExpression());

        } else if (stmt instanceof ForStatement) {
            // "for (i = 0; i < 10; i++) { ... }"
            ForStatement forStmt = (ForStatement)stmt;
            DFLocalScope innerScope = scope.getChildByAST(stmt);
            for (Expression init : (List<Expression>) forStmt.initializers()) {
                this.listDefinedExpr(defined, innerScope, init);
            }
            Expression expr1 = forStmt.getExpression();
            if (expr1 != null) {
                this.listDefinedExpr(defined, innerScope, expr1);
            }
            this.listDefinedStmt(defined, innerScope, forStmt.getBody());
            for (Expression update : (List<Expression>) forStmt.updaters()) {
                this.listDefinedExpr(defined, innerScope, update);
            }

        } else if (stmt instanceof EnhancedForStatement) {
            // "for (x : array) { ... }"
            EnhancedForStatement eForStmt = (EnhancedForStatement)stmt;
            this.listDefinedExpr(defined, scope, eForStmt.getExpression());
            DFLocalScope innerScope = scope.getChildByAST(stmt);
            this.listDefinedStmt(defined, innerScope, eForStmt.getBody());

        } else if (stmt instanceof ReturnStatement) {
            // "return 42;"
            ReturnStatement rtrnStmt = (ReturnStatement)stmt;
            Expression expr1 = rtrnStmt.getExpression();
            if (expr1 != null) {
                DFType type = this.getFuncType().getReturnType();
                this.listDefinedExpr(defined, scope, expr1, type);
            }
            // Return is handled as an Exit, not an output.

        } else if (stmt instanceof BreakStatement) {
            // "break;"

        } else if (stmt instanceof ContinueStatement) {
            // "continue;"

        } else if (stmt instanceof LabeledStatement) {
            // "here:"
            LabeledStatement labeledStmt = (LabeledStatement)stmt;
            this.listDefinedStmt(defined, scope, labeledStmt.getBody());

        } else if (stmt instanceof SynchronizedStatement) {
            // "synchronized (this) { ... }"
            SynchronizedStatement syncStmt = (SynchronizedStatement)stmt;
            this.listDefinedExpr(defined, scope, syncStmt.getExpression());
            this.listDefinedStmt(defined, scope, syncStmt.getBody());

        } else if (stmt instanceof TryStatement) {
            // "try { ... } catch (e) { ... }"
            TryStatement tryStmt = (TryStatement)stmt;
            DFLocalScope tryScope = scope.getChildByAST(tryStmt);
            for (VariableDeclarationExpression decl :
                     (List<VariableDeclarationExpression>) tryStmt.resources()) {
                for (VariableDeclarationFragment frag :
                         (List<VariableDeclarationFragment>) decl.fragments()) {
                    try {
                        DFRef ref = tryScope.lookupVar(frag.getName());
                        Expression init = frag.getInitializer();
                        if (init != null) {
                            DFType type = this.listDefinedExpr(
                                defined, tryScope, init, ref.getRefType());
                            ref.setRefType(type);
                        }
                    } catch (VariableNotFound e) {
                    }
                }
            }
            for (CatchClause cc : (List<CatchClause>) tryStmt.catchClauses()) {
                DFLocalScope catchScope = scope.getChildByAST(cc);
                this.listDefinedStmt(defined, catchScope, cc.getBody());
            }
            this.listDefinedStmt(defined, tryScope, tryStmt.getBody());
            Block finBlock = tryStmt.getFinally();
            if (finBlock != null) {
                this.listDefinedStmt(defined, scope, finBlock);
            }

        } else if (stmt instanceof ThrowStatement) {
            // "throw e;"
            ThrowStatement throwStmt = (ThrowStatement)stmt;
            DFType type = this.listDefinedExpr(
                defined, scope, throwStmt.getExpression());
            // Because an exception can be catched, throw does not
            // necessarily mean this method actually throws as a whole.
            // This should be taken cared of by the "throws" clause.
            //DFRef ref = _methodScope.lookupException(type.toKlass());
            //this.addOutputRef(ref);

        } else if (stmt instanceof ConstructorInvocation) {
            // "this(args)"
            ConstructorInvocation ci = (ConstructorInvocation)stmt;
            DFRef ref = _srcklass.getThisRef();
            this.addInputRef(ref);
            DFKlass klass = ref.getRefType().toKlass();
            int nargs = ci.arguments().size();
            DFType[] argTypes = new DFType[nargs];
            for (int i = 0; i < nargs; i++) {
                Expression arg = (Expression)ci.arguments().get(i);
                DFType type = this.listDefinedExpr(defined, scope, arg);
                if (type == null) return;
                argTypes[i] = type;
            }
            try {
                DFMethod method1 = klass.lookupMethod(
                    CallStyle.Constructor, (String)null, argTypes);
                method1.addCaller(this);
                this.setLambdaType(
                    defined, method1.getFuncType(), ci.arguments());
            } catch (MethodNotFound e) {
            }

        } else if (stmt instanceof SuperConstructorInvocation) {
            // "super(args)"
            SuperConstructorInvocation sci = (SuperConstructorInvocation)stmt;
            DFRef ref = _srcklass.getThisRef();
            this.addInputRef(ref);
            DFKlass baseKlass = _srcklass.getBaseKlass();
            int nargs = sci.arguments().size();
            DFType[] argTypes = new DFType[nargs];
            for (int i = 0; i < nargs; i++) {
                Expression arg = (Expression)sci.arguments().get(i);
                DFType type = this.listDefinedExpr(defined, scope, arg);
                if (type == null) return;
                argTypes[i] = type;
            }
            try {
                DFMethod method1 = baseKlass.lookupMethod(
                    CallStyle.Constructor, (String)null, argTypes);
                method1.addCaller(this);
                this.setLambdaType(
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

    protected DFType listDefinedExpr(
        Collection<DFSourceKlass> defined,
        DFLocalScope scope, Expression expr)
        throws InvalidSyntax {
        return this.listDefinedExpr(defined, scope, expr, null);
    }

    @SuppressWarnings("unchecked")
    protected DFType listDefinedExpr(
        Collection<DFSourceKlass> defined,
        DFLocalScope scope, Expression expr, DFType expected)
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
                    ref = scope.lookupVar((SimpleName)name);
                } catch (VariableNotFound e) {
                    return null;
                }
            } else {
                QualifiedName qname = (QualifiedName)name;
                // Try assuming it's a variable access.
                DFType type = this.listDefinedExpr(
                    defined, scope, qname.getQualifier());
                if (type == null) {
                    // Turned out it's a class variable.
                    try {
                        type = _finder.resolveKlass(qname.getQualifier());
                    } catch (TypeNotFound e) {
                        return null;
                    }
                }
                DFKlass klass = type.toKlass();
                SimpleName fieldName = qname.getName();
                ref = klass.getField(fieldName);
                if (ref == null) return null;
            }
            if (ref instanceof DFKlass.FieldRef) {
                this.addInputRef(_srcklass.getThisRef());
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
                    ref = klass.getThisRef();
                } catch (TypeNotFound e) {
                    return null;
                }
            } else {
                ref = _srcklass.getThisRef();
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
                return null;
            }

        } else if (expr instanceof PrefixExpression) {
            // "++x"
            PrefixExpression prefix = (PrefixExpression)expr;
            PrefixExpression.Operator op = prefix.getOperator();
            Expression operand = prefix.getOperand();
            if (op == PrefixExpression.Operator.INCREMENT ||
                op == PrefixExpression.Operator.DECREMENT) {
                this.listDefinedAssignment(defined, scope, operand);
            }
            return DFNode.inferPrefixType(
                this.listDefinedExpr(defined, scope, operand), op);

        } else if (expr instanceof PostfixExpression) {
            // "y--"
            PostfixExpression postfix = (PostfixExpression)expr;
            PostfixExpression.Operator op = postfix.getOperator();
            Expression operand = postfix.getOperand();
            if (op == PostfixExpression.Operator.INCREMENT ||
                op == PostfixExpression.Operator.DECREMENT) {
                this.listDefinedAssignment(defined, scope, operand);
            }
            return this.listDefinedExpr(defined, scope, operand);

        } else if (expr instanceof InfixExpression) {
            // "a+b"
            InfixExpression infix = (InfixExpression)expr;
            InfixExpression.Operator op = infix.getOperator();
            DFType left = this.listDefinedExpr(
                defined, scope, infix.getLeftOperand());
            DFType right = this.listDefinedExpr(
                defined, scope, infix.getRightOperand());
            if (left == null || right == null) return null;
            return DFNode.inferInfixType(left, op, right);

        } else if (expr instanceof ParenthesizedExpression) {
            // "(expr)"
            ParenthesizedExpression paren = (ParenthesizedExpression)expr;
            return this.listDefinedExpr(defined, scope, paren.getExpression(), expected);

        } else if (expr instanceof Assignment) {
            // "p = q"
            Assignment assn = (Assignment)expr;
            Assignment.Operator op = assn.getOperator();
            if (op != Assignment.Operator.ASSIGN) {
                this.listDefinedExpr(defined, scope, assn.getLeftHandSide());
            }
            DFRef ref = this.listDefinedAssignment(
                defined, scope, assn.getLeftHandSide());
            DFType type = (ref != null)? ref.getRefType() : null;
            return this.listDefinedExpr(
                defined, scope, assn.getRightHandSide(), type);

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
                        DFType type = this.listDefinedExpr(
                            defined, scope, init, ref.getRefType());
                        ref.setRefType(type);
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
                DFRef ref = _srcklass.getThisRef();
                this.addInputRef(ref);
                klass = _srcklass;
                callStyle = CallStyle.InstanceOrStatic;
            } else {
                callStyle = CallStyle.InstanceMethod;
                if (expr1 instanceof Name) {
                    // "ClassName.method()"
                    try {
                        klass = _finder.resolveKlass((Name)expr1);
                        callStyle = CallStyle.StaticMethod;
                    } catch (TypeNotFound e) {
                    }
                }
                if (klass == null) {
                    // "expr.method()"
                    DFType type = this.listDefinedExpr(defined, scope, expr1);
                    if (type == null) return null;
                    klass = type.toKlass();
                }
            }
            int nargs = invoke.arguments().size();
            DFType[] argTypes = new DFType[nargs];
            for (int i = 0; i < nargs; i++) {
                Expression arg = (Expression)invoke.arguments().get(i);
                DFType type = this.listDefinedExpr(defined, scope, arg);
                if (type == null) return null;
                argTypes[i] = type;
            }
            try {
                DFMethod method1 = klass.lookupMethod(
                    callStyle, invoke.getName(), argTypes);
                for (DFMethod m : method1.getOverriders()) {
                    m.addCaller(this);
                }
                this.setLambdaType(
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
                DFType type = this.listDefinedExpr(defined, scope, arg);
                if (type == null) return null;
                argTypes[i] = type;
            }
            DFRef ref = _srcklass.getThisRef();
            this.addInputRef(ref);
            DFKlass baseKlass = _srcklass.getBaseKlass();
            try {
                DFMethod method1 = baseKlass.lookupMethod(
                    CallStyle.InstanceMethod, sinvoke.getName(), argTypes);
                method1.addCaller(this);
                this.setLambdaType(
                    defined, method1.getFuncType(), sinvoke.arguments());
                return method1.getFuncType().getReturnType();
            } catch (MethodNotFound e) {
                return DFUnknownType.UNKNOWN;
            }

        } else if (expr instanceof ArrayCreation) {
            // "new int[10]"
            ArrayCreation ac = (ArrayCreation)expr;
            for (Expression dim : (List<Expression>) ac.dimensions()) {
                this.listDefinedExpr(defined, scope, dim);
            }
            ArrayInitializer init = ac.getInitializer();
            if (init != null) {
                this.listDefinedExpr(defined, scope, init);
            }
            try {
                return _finder.resolve(ac.getType().getElementType());
            } catch (TypeNotFound e) {
                return null;
            }

        } else if (expr instanceof ArrayInitializer) {
            // "{ 5,9,4,0 }"
            ArrayInitializer init = (ArrayInitializer)expr;
            DFType type = null;
            for (Expression expr1 : (List<Expression>) init.expressions()) {
                type = this.listDefinedExpr(defined, scope, expr1);
            }
            return type;

        } else if (expr instanceof ArrayAccess) {
            // "a[0]"
            ArrayAccess aa = (ArrayAccess)expr;
            this.listDefinedExpr(defined, scope, aa.getIndex());
            DFType type = this.listDefinedExpr(defined, scope, aa.getArray());
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
                    type = _finder.resolveKlass((Name)expr1);
                } catch (TypeNotFound e) {
                }
            }
            if (type == null) {
                type = this.listDefinedExpr(defined, scope, expr1);
                if (type == null) return null;
            }
            DFKlass klass = type.toKlass();
            SimpleName fieldName = fa.getName();
            DFRef ref = klass.getField(fieldName);
            if (ref == null) return null;
            this.addInputRef(ref);
            return ref.getRefType();

        } else if (expr instanceof SuperFieldAccess) {
            // "super.baa"
            SuperFieldAccess sfa = (SuperFieldAccess)expr;
            SimpleName fieldName = sfa.getName();
            DFRef ref = _srcklass.getThisRef();
            this.addInputRef(ref);
            DFKlass baseKlass = _srcklass.getBaseKlass();
            DFRef ref2 = baseKlass.getField(fieldName);
            if (ref2 == null) return null;
            this.addInputRef(ref2);
            return ref2.getRefType();

        } else if (expr instanceof CastExpression) {
            // "(String)"
            CastExpression cast = (CastExpression)expr;
            try {
                DFType type = _finder.resolve(cast.getType());
                this.listDefinedExpr(defined, scope, cast.getExpression(), type);
                return type;
            } catch (TypeNotFound e) {
                this.listDefinedExpr(defined, scope, cast.getExpression());
                return null;
            }

        } else if (expr instanceof ClassInstanceCreation) {
            // "new T()"
            ClassInstanceCreation cstr = (ClassInstanceCreation)expr;
            DFKlass instKlass;
            if (cstr.getAnonymousClassDeclaration() != null) {
                String id = Utils.encodeASTNode(cstr);
                instKlass = this.getKlass(id);
                if (instKlass == null) {
                    return null;
                }
            } else {
                try {
                    instKlass = _finder.resolve(cstr.getType()).toKlass();
                } catch (TypeNotFound e) {
                    return null;
                }
            }
            Expression expr1 = cstr.getExpression();
            if (expr1 != null) {
                this.listDefinedExpr(defined, scope, expr1);
            }
            int nargs = cstr.arguments().size();
            DFType[] argTypes = new DFType[nargs];
            for (int i = 0; i < nargs; i++) {
                Expression arg = (Expression)cstr.arguments().get(i);
                DFType type = this.listDefinedExpr(defined, scope, arg);
                if (type == null) return null;
                argTypes[i] = type;
            }
            try {
                DFMethod method1 = instKlass.lookupMethod(
                    CallStyle.Constructor, (String)null, argTypes);
                method1.addCaller(this);
                this.setLambdaType(
                    defined, method1.getFuncType(), cstr.arguments());
            } catch (MethodNotFound e) {
            }
            return instKlass;

        } else if (expr instanceof ConditionalExpression) {
            // "c? a : b"
            ConditionalExpression cond = (ConditionalExpression)expr;
            this.listDefinedExpr(defined, scope, cond.getExpression());
            this.listDefinedExpr(defined, scope, cond.getThenExpression());
            return this.listDefinedExpr(defined, scope, cond.getElseExpression());

        } else if (expr instanceof InstanceofExpression) {
            // "a instanceof A"
            return DFBasicType.BOOLEAN;

        } else if (expr instanceof LambdaExpression) {
            // "x -> { ... }"
            LambdaExpression lambda = (LambdaExpression)expr;
            String id = Utils.encodeASTNode(lambda);
            DFLambdaKlass lambdaKlass = (DFLambdaKlass)this.getKlass(id);
            if (expected != null) {
                lambdaKlass.setBaseKlass(expected.toKlass());
                if (lambdaKlass.isDefined()) {
                    defined.add(lambdaKlass);
                }
            }
            for (DFLambdaKlass.CapturedRef captured :
                     lambdaKlass.getCapturedRefs()) {
                this.addInputRef(captured.getOriginal());
            }
            return lambdaKlass;

        } else if (expr instanceof MethodReference) {
            MethodReference methodref = (MethodReference)expr;
            String id = Utils.encodeASTNode(methodref);
            DFMethodRefKlass methodRefKlass = (DFMethodRefKlass)this.getKlass(id);
            if (expected != null) {
                methodRefKlass.setBaseKlass(expected.toKlass());
                if (methodRefKlass.isDefined()) {
                    defined.add(methodRefKlass);
                }
            }
            return methodRefKlass;

        } else {
            // ???
            throw new InvalidSyntax(expr);
        }
    }

    private DFRef listDefinedAssignment(
        Collection<DFSourceKlass> defined,
        DFLocalScope scope, Expression expr)
        throws InvalidSyntax {
        assert expr != null;

        if (expr instanceof Name) {
            // "a.b"
            Name name = (Name)expr;
            DFRef ref;
            if (name.isSimpleName()) {
                try {
                    ref = scope.lookupVar((SimpleName)name);
                } catch (VariableNotFound e) {
                    return null;
                }
            } else {
                QualifiedName qname = (QualifiedName)name;
                // Try assuming it's a variable access.
                DFType type = this.listDefinedExpr(
                    defined, scope, qname.getQualifier());
                if (type == null) {
                    // Turned out it's a class variable.
                    try {
                        type = _finder.resolveKlass(qname.getQualifier());
                    } catch (TypeNotFound e) {
                        return null;
                    }
                }
                DFKlass klass = type.toKlass();
                SimpleName fieldName = qname.getName();
                ref = klass.getField(fieldName);
                if (ref == null) return null;
            }
            if (ref instanceof DFKlass.FieldRef) {
                this.addInputRef(_srcklass.getThisRef());
            }
            this.addOutputRef(ref);
            return ref;

        } else if (expr instanceof ArrayAccess) {
            // "a[0]"
            ArrayAccess aa = (ArrayAccess)expr;
            DFType type = this.listDefinedExpr(defined, scope, aa.getArray());
            this.listDefinedExpr(defined, scope, aa.getIndex());
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
                    type = _finder.resolveKlass((Name)expr1);
                } catch (TypeNotFound e) {
                }
            }
            if (type == null) {
                type = this.listDefinedExpr(defined, scope, expr1);
                if (type == null) return null;
            }
            DFKlass klass = type.toKlass();
            SimpleName fieldName = fa.getName();
            DFRef ref = klass.getField(fieldName);
            if (ref == null) return null;
            this.addOutputRef(ref);
            return ref;

        } else if (expr instanceof SuperFieldAccess) {
            // "super.baa"
            SuperFieldAccess sfa = (SuperFieldAccess)expr;
            SimpleName fieldName = sfa.getName();
            DFRef ref = _srcklass.getThisRef();
            this.addInputRef(ref);
            DFKlass baseKlass = _srcklass.getBaseKlass();
            DFRef ref2 = baseKlass.getField(fieldName);
            if (ref2 == null) return null;
            this.addOutputRef(ref2);
            return ref2;

        } else if (expr instanceof ParenthesizedExpression) {
            ParenthesizedExpression paren = (ParenthesizedExpression)expr;
            return this.listDefinedAssignment(
                defined, scope, paren.getExpression());

        } else {
            throw new InvalidSyntax(expr);
        }
    }

    private void addInputRef(DFRef ref) {
        DFVarScope scope = ref.getScope();
        // Skip internal variables.
        if (_methodScope == scope || _methodScope.contains(scope)) return;
        _inputRefs.add(ref);
    }

    private void addOutputRef(DFRef ref) {
        DFVarScope scope = ref.getScope();
        // Skip internal variables.
        if (_methodScope == scope || _methodScope.contains(scope)) return;
        _outputRefs.add(ref);
    }

    /// Set Lambda types.

    protected void setLambdaType(
        Collection<DFSourceKlass> defined,
        DFFuncType funcType, List<Expression> exprs)
        throws InvalidSyntax {
        // types or exprs might be shorter than the other. (due to varargs calls)
        for (int i = 0; i < exprs.size(); i++) {
            DFType type = funcType.getArgType(i);
            this.setLambdaType(defined, type, exprs.get(i));
        }
    }

    protected void setLambdaType(
        Collection<DFSourceKlass> defined,
        DFType type, Expression expr)
        throws InvalidSyntax {
        if (expr instanceof ParenthesizedExpression) {
            ParenthesizedExpression paren = (ParenthesizedExpression)expr;
            this.setLambdaType(defined, type, paren.getExpression());

        } else if (expr instanceof LambdaExpression) {
            LambdaExpression lambda = (LambdaExpression)expr;
            String id = Utils.encodeASTNode(lambda);
            DFLambdaKlass lambdaKlass = (DFLambdaKlass)this.getKlass(id);
            lambdaKlass.setBaseKlass(type.toKlass());
            if (lambdaKlass.isDefined()) {
                defined.add(lambdaKlass);
            }

        } else if (expr instanceof MethodReference) {
            MethodReference methodref = (MethodReference)expr;
            String id = Utils.encodeASTNode(methodref);
            DFMethodRefKlass methodRefKlass = (DFMethodRefKlass)this.getKlass(id);
            methodRefKlass.setBaseKlass(type.toKlass());
            if (methodRefKlass.isDefined()) {
                defined.add(methodRefKlass);
            }

        }
    }

    /// Expand References.

    public void expandRefs(Set<DFRef> inputRefs, Set<DFRef> outputRefs) {
        _inputRefs.addAll(inputRefs);
        _outputRefs.addAll(outputRefs);
    }

    /**
     * Performs dataflow analysis for a given method.
     */

    // listUsedKlasses: enumerate all the referenced Klasses.
    public abstract void listUsedKlasses(Collection<DFSourceKlass> klasses)
        throws InvalidSyntax;

    // listDefinedKlasses: list all the internal DFRefs AND fix the lambdas.
    public abstract void listDefinedKlasses(Collection<DFSourceKlass> defined)
        throws InvalidSyntax;

    // getDFGraph: generate dataflow graphs.
    public abstract DFGraph getDFGraph(Exporter exporter)
        throws InvalidSyntax, EntityNotFound;

    public abstract ASTNode getAST();

    public void writeXML(XMLStreamWriter writer, DFGraph graph)
        throws XMLStreamException {
        writer.writeStartElement("method");
        writer.writeAttribute("id", this.getSignature());
        writer.writeAttribute("name", this.getName());
        writer.writeAttribute("style", this.getCallStyle().toString());
        if (this.isAbstract()) {
            writer.writeAttribute("abstract", Boolean.toString(true));
        }
        for (DFMethod caller : this.getCallers()) {
            writer.writeStartElement("caller");
            writer.writeAttribute("id", caller.getSignature());
            writer.writeEndElement();
        }
        for (DFMethod overrider : this.getOverriders()) {
            if (overrider == this) continue;
            writer.writeStartElement("overrider");
            writer.writeAttribute("id", overrider.getSignature());
            writer.writeEndElement();
        }
        for (DFMethod overriding : this.getOverridings()) {
            writer.writeStartElement("overriding");
            writer.writeAttribute("id", overriding.getSignature());
            writer.writeEndElement();
        }
        ASTNode ast = this.getAST();
        if (ast != null) {
            Utils.writeXML(writer, ast);
        }
        DFNode[] nodes = graph.getNodes();
        this.getScope().writeXML(writer, nodes);
        writer.writeEndElement();
    }

    protected class MethodGraph extends DFGraph {

        private String _graphId;

        public MethodGraph(String graphId) {
            super(DFSourceMethod.this);
            _graphId = graphId;
        }

        @Override
        public String toString() {
            return "<MethodGraph ("+_graphId+") "+DFSourceMethod.this+">";
        }

        @Override
        public String getGraphId() {
            return _graphId;
        }
    }

    // MethodScope
    protected class MethodScope extends DFLocalScope {

        private DFRef _return = null;
        private DFRef[] _arguments = null;
        private ConsistentHashMap<DFKlass, DFRef> _this =
            new ConsistentHashMap<DFKlass, DFRef>();
        private ConsistentHashMap<DFType, DFRef> _exceptions =
            new ConsistentHashMap<DFType, DFRef>();

        protected MethodScope(DFVarScope outer, String name) {
            super(outer, name);
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
                String name = "!"+type.getTypeName();
                ref = new InternalRef(type, name);
                _exceptions.put(type, ref);
            }
            return ref;
        }

        public List<DFRef> getExcRefs() {
            return _exceptions.values();
        }

        protected void buildInternalRefs(List<VariableDeclaration> parameters) {
            // could be a wrong funcType when the lambda is undefined.
            DFFuncType funcType = DFSourceMethod.this.getFuncType();
            DFType[] argTypes = funcType.getRealArgTypes();
            _return = new InternalRef(funcType.getReturnType(), "#return");
            _arguments = new InternalRef[argTypes.length];
            int i = 0;
            for (VariableDeclaration decl : parameters) {
                if (argTypes.length <= i) break;
                DFType argType = argTypes[i];
                int ndims = decl.getExtraDimensions();
                if (ndims != 0) {
                    argType = DFArrayType.getArray(argType, ndims);
                }
                String name;
                if (funcType.isVarArg(i)) {
                    name = "varargs";
                } else {
                    name = "arg"+i;
                }
                _arguments[i] = new InternalRef(argType, "#"+name);
                this.addVar(decl.getName(), argType);
                i++;
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
            public DFVarScope getScope() {
                return null;
            }

            @Override
            public String getFullName() {
                return _name;
            }
        }
    }

    // InputNode: represnets a function argument.
    protected class InputNode extends DFNode {

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
    protected class OutputNode extends DFNode {

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
    protected class AssignNode extends DFNode {

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
}


//  AnonymousKlass
//
class AnonymousKlass extends DFSourceKlass {

    private ClassInstanceCreation _cstr;

    @SuppressWarnings("unchecked")
    protected AnonymousKlass(
        ClassInstanceCreation cstr,
        DFTypeSpace outerSpace, DFSourceKlass outerKlass,
        DFVarScope outerScope)
        throws InvalidSyntax, EntityDuplicate {
        super(Utils.encodeASTNode(cstr),
              outerSpace, outerKlass, outerScope,
              outerKlass.getFilePath(), outerKlass.isAnalyze());
        _cstr = cstr;
        this.buildTypeFromDecls(
            cstr.getAnonymousClassDeclaration().bodyDeclarations());
    }

    protected DFKlass parameterize(Map<String, DFKlass> paramTypes) {
        assert false;
        return null;
    }

    public ASTNode getAST() {
        return _cstr;
    }

    protected void build() {
        try {
            this.buildMembersFromAnonDecl(_cstr);
        } catch (InvalidSyntax e) {
            Logger.error(
                "AnonymousKlass.build: InvalidSyntax: ",
                Utils.getASTSource(e.ast), this);
        } catch (EntityDuplicate e) {
            Logger.error(
                "AnonymousKlass.build: EntityDuplicate: ",
                e.name, this);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean listUsedKlasses(Collection<DFSourceKlass> klasses) {
        if (!super.listUsedKlasses(klasses)) return false;
        try {
            this.listUsedDecls(
                klasses, _cstr.getAnonymousClassDeclaration().bodyDeclarations());
        } catch (InvalidSyntax e) {
            Logger.error(
                "AnonymousKlass.listUsedKlasses:",
                Utils.getASTSource(e.ast), this);
        }
        return true;
    }
}
