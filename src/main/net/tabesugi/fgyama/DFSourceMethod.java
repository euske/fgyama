//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import javax.xml.stream.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  AnonymousKlass
//
class AnonymousKlass extends DFSourceKlass {

    private ClassInstanceCreation _cstr;

    @SuppressWarnings("unchecked")
    protected AnonymousKlass(
        ClassInstanceCreation cstr,
        DFTypeSpace outerSpace, DFSourceKlass outerKlass,
        String filePath, DFVarScope outerScope)
        throws InvalidSyntax {
        super(Utils.encodeASTNode(cstr),
              outerSpace, outerKlass, filePath, outerScope);
        _cstr = cstr;
        this.buildTypeFromDecls(
            cstr.getAnonymousClassDeclaration().bodyDeclarations());
    }

    protected DFKlass parameterize(Map<String, DFKlass> paramTypes)
        throws InvalidSyntax {
        assert false;
        return null;
    }

    protected void build() throws InvalidSyntax {
        this.buildMembersFromAnonDecl(_cstr);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void loadKlasses(Collection<DFSourceKlass> klasses)
        throws InvalidSyntax {
        if (klasses.contains(this)) return;
        super.loadKlasses(klasses);
        this.loadKlassesDecls(
            klasses, _cstr.getAnonymousClassDeclaration().bodyDeclarations());
    }
}


//  DFSourceMethod
//  DFMethod defined in source code.
//
//  Usage:
//    1. new DFSourceMethod(finder)
//    2. loadKlasses()
//    3. enumRefs()
//    4. expandRefs()
//    5. writeGraph()
//
public abstract class DFSourceMethod extends DFMethod {

    private DFSourceKlass _srcklass;
    private DFVarScope _outerScope;
    private DFTypeFinder _finder;
    private MethodScope _methodScope;

    private ConsistentHashSet<DFRef> _inputRefs = new ConsistentHashSet<DFRef>();
    private ConsistentHashSet<DFRef> _outputRefs = new ConsistentHashSet<DFRef>();

    private static boolean _defaultTransparent = false;

    public static void setDefaultTransparent(boolean transparent) {
        _defaultTransparent = transparent;
    }

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
        DFSourceMethod genericMethod, Map<String, DFKlass> paramTypes)
        throws InvalidSyntax {
        super(genericMethod, paramTypes);

        _srcklass = genericMethod._srcklass;
        _outerScope = genericMethod._outerScope;
        _finder = new DFTypeFinder(this, genericMethod._finder);
        _methodScope = new MethodScope(_outerScope, this.getMethodId());
    }

    public boolean isTransparent() {
        return _defaultTransparent;
    }

    protected DFTypeFinder getFinder() {
        return _finder;
    }

    public DFLocalScope getScope() {
        return _methodScope;
    }

    public Collection<DFRef> getInputRefs() {
        assert this.isTransparent();
        return _inputRefs;
    }

    public Collection<DFRef> getOutputRefs() {
        assert this.isTransparent();
        return _outputRefs;
    }

    /// TypeSpace construction.

    @SuppressWarnings("unchecked")
    protected void buildTypeFromStmt(
        Statement stmt, DFLocalScope outerScope)
        throws InvalidSyntax {
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
            Expression expr = switchCase.getExpression();
            if (expr != null) {
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
            DFSourceKlass klass = new AbstTypeDeclKlass(
                abstTypeDecl, this, _srcklass,
                _srcklass.getFilePath(), outerScope);
            klass.setBaseFinder(_finder);
            this.addKlass(id, klass);

        } else {
            throw new InvalidSyntax(stmt);

        }
    }

    @SuppressWarnings("unchecked")
    protected void buildTypeFromExpr(
        Expression expr, DFVarScope outerScope)
        throws InvalidSyntax {
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
                    cstr,
                    this, _srcklass, _srcklass.getFilePath(), outerScope);
                this.addKlass(id, anonKlass);
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
            lambdaKlass.setBaseFinder(_finder);
            this.addKlass(id, lambdaKlass);

        } else if (expr instanceof MethodReference) {
            //  CreationReference
            //  ExpressionMethodReference
            //  SuperMethodReference
            //  TypeMethodReference
            MethodReference methodref = (MethodReference)expr;
            String id = Utils.encodeASTNode(methodref);
            DFSourceKlass methodRefKlass = new DFMethodRefKlass(
                methodref, this, _srcklass, outerScope);
            methodRefKlass.setBaseFinder(_finder);
            this.addKlass(id, methodRefKlass);

        } else {
            // ???
            throw new InvalidSyntax(expr);
        }
    }

    /// Load klasses.

    @SuppressWarnings("unchecked")
    protected void loadKlassesStmt(
        Collection<DFSourceKlass> klasses, Statement stmt)
        throws InvalidSyntax {
        assert stmt != null;

        if (stmt instanceof AssertStatement) {

        } else if (stmt instanceof Block) {
            Block block = (Block)stmt;
            for (Statement stmt1 :
                     (List<Statement>) block.statements()) {
                this.loadKlassesStmt(klasses, stmt1);
            }

        } else if (stmt instanceof EmptyStatement) {

        } else if (stmt instanceof VariableDeclarationStatement) {
            VariableDeclarationStatement varStmt =
                (VariableDeclarationStatement)stmt;
            DFType varType = _finder.resolveSafe(varStmt.getType());
            if (varType instanceof DFSourceKlass) {
                ((DFSourceKlass)varType).loadKlasses(klasses);
            }
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>) varStmt.fragments()) {
                Expression expr = frag.getInitializer();
                if (expr != null) {
                    this.loadKlassesExpr(klasses, expr);
                }
            }

        } else if (stmt instanceof ExpressionStatement) {
            ExpressionStatement exprStmt = (ExpressionStatement)stmt;
            Expression expr = exprStmt.getExpression();
            this.loadKlassesExpr(klasses, expr);

        } else if (stmt instanceof ReturnStatement) {
            ReturnStatement returnStmt = (ReturnStatement)stmt;
            Expression expr = returnStmt.getExpression();
            if (expr != null) {
                this.loadKlassesExpr(klasses, expr);
            }

        } else if (stmt instanceof IfStatement) {
            IfStatement ifStmt = (IfStatement)stmt;
            Expression expr = ifStmt.getExpression();
            this.loadKlassesExpr(klasses, expr);
            Statement thenStmt = ifStmt.getThenStatement();
            this.loadKlassesStmt(klasses, thenStmt);
            Statement elseStmt = ifStmt.getElseStatement();
            if (elseStmt != null) {
                this.loadKlassesStmt(klasses, elseStmt);
            }

        } else if (stmt instanceof SwitchStatement) {
            SwitchStatement switchStmt = (SwitchStatement)stmt;
            Expression expr = switchStmt.getExpression();
            this.loadKlassesExpr(klasses, expr);
            for (Statement stmt1 :
                     (List<Statement>) switchStmt.statements()) {
                this.loadKlassesStmt(klasses, stmt1);
            }

        } else if (stmt instanceof SwitchCase) {
            SwitchCase switchCase = (SwitchCase)stmt;
            Expression expr = switchCase.getExpression();
            if (expr != null) {
                this.loadKlassesExpr(klasses, expr);
            }

        } else if (stmt instanceof WhileStatement) {
            WhileStatement whileStmt = (WhileStatement)stmt;
            this.loadKlassesExpr(klasses, whileStmt.getExpression());
            this.loadKlassesStmt(klasses, whileStmt.getBody());

        } else if (stmt instanceof DoStatement) {
            DoStatement doStmt = (DoStatement)stmt;
            this.loadKlassesStmt(klasses, doStmt.getBody());
            this.loadKlassesExpr(klasses, doStmt.getExpression());

        } else if (stmt instanceof ForStatement) {
            ForStatement forStmt = (ForStatement)stmt;
            for (Expression init :
                     (List<Expression>) forStmt.initializers()) {
                this.loadKlassesExpr(klasses, init);
            }
            Expression expr = forStmt.getExpression();
            if (expr != null) {
                this.loadKlassesExpr(klasses, expr);
            }
            this.loadKlassesStmt(klasses, forStmt.getBody());
            for (Expression update :
                     (List<Expression>) forStmt.updaters()) {
                this.loadKlassesExpr(klasses, update);
            }

        } else if (stmt instanceof EnhancedForStatement) {
            EnhancedForStatement eForStmt = (EnhancedForStatement)stmt;
            this.loadKlassesExpr(klasses, eForStmt.getExpression());
            SingleVariableDeclaration decl = eForStmt.getParameter();
            DFType varType = _finder.resolveSafe(decl.getType());
            if (varType instanceof DFSourceKlass) {
                ((DFSourceKlass)varType).loadKlasses(klasses);
            }
            this.loadKlassesStmt(klasses, eForStmt.getBody());

        } else if (stmt instanceof BreakStatement) {

        } else if (stmt instanceof ContinueStatement) {

        } else if (stmt instanceof LabeledStatement) {
            LabeledStatement labeledStmt = (LabeledStatement)stmt;
            this.loadKlassesStmt(klasses, labeledStmt.getBody());

        } else if (stmt instanceof SynchronizedStatement) {
            SynchronizedStatement syncStmt = (SynchronizedStatement)stmt;
            this.loadKlassesExpr(klasses, syncStmt.getExpression());
            this.loadKlassesStmt(klasses, syncStmt.getBody());

        } else if (stmt instanceof TryStatement) {
            TryStatement tryStmt = (TryStatement)stmt;
            for (VariableDeclarationExpression decl :
                     (List<VariableDeclarationExpression>) tryStmt.resources()) {
                this.loadKlassesExpr(klasses, decl);
            }
            this.loadKlassesStmt(klasses, tryStmt.getBody());
            for (CatchClause cc :
                     (List<CatchClause>) tryStmt.catchClauses()) {
                SingleVariableDeclaration decl = cc.getException();
                DFType varType = _finder.resolveSafe(decl.getType());
                if (varType instanceof DFSourceKlass) {
                    ((DFSourceKlass)varType).loadKlasses(klasses);
                }
                this.loadKlassesStmt(klasses, cc.getBody());
            }
            Block finBlock = tryStmt.getFinally();
            if (finBlock != null) {
                this.loadKlassesStmt(klasses, finBlock);
            }

        } else if (stmt instanceof ThrowStatement) {
            ThrowStatement throwStmt = (ThrowStatement)stmt;
            Expression expr = throwStmt.getExpression();
            if (expr != null) {
                this.loadKlassesExpr(klasses, expr);
            }

        } else if (stmt instanceof ConstructorInvocation) {
            ConstructorInvocation ci = (ConstructorInvocation)stmt;
            for (Expression expr :
                     (List<Expression>) ci.arguments()) {
                this.loadKlassesExpr(klasses, expr);
            }

        } else if (stmt instanceof SuperConstructorInvocation) {
            SuperConstructorInvocation sci = (SuperConstructorInvocation)stmt;
            for (Expression expr :
                     (List<Expression>) sci.arguments()) {
                this.loadKlassesExpr(klasses, expr);
            }

        } else if (stmt instanceof TypeDeclarationStatement) {
            TypeDeclarationStatement decl = (TypeDeclarationStatement)stmt;
            AbstractTypeDeclaration abstDecl = decl.getDeclaration();
            DFKlass innerType = this.getKlass(abstDecl.getName());
            if (innerType instanceof DFSourceKlass) {
                ((DFSourceKlass)innerType).loadKlasses(klasses);
            }

        } else {
            throw new InvalidSyntax(stmt);

        }
    }

    @SuppressWarnings("unchecked")
    protected void loadKlassesExpr(
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
                    DFType type = _finder.lookupType(name);
                    if (type instanceof DFSourceKlass) {
                        ((DFSourceKlass)type).loadKlasses(klasses);
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
                    ((DFSourceKlass)type).loadKlasses(klasses);
                }
            } catch (TypeNotFound e) {
            }

        } else if (expr instanceof PrefixExpression) {
            PrefixExpression prefix = (PrefixExpression)expr;
            PrefixExpression.Operator op = prefix.getOperator();
            Expression operand = prefix.getOperand();
            this.loadKlassesExpr(klasses, operand);

        } else if (expr instanceof PostfixExpression) {
            PostfixExpression postfix = (PostfixExpression)expr;
            PostfixExpression.Operator op = postfix.getOperator();
            Expression operand = postfix.getOperand();
            this.loadKlassesExpr(klasses, operand);

        } else if (expr instanceof InfixExpression) {
            InfixExpression infix = (InfixExpression)expr;
            InfixExpression.Operator op = infix.getOperator();
            Expression loperand = infix.getLeftOperand();
            this.loadKlassesExpr(klasses, loperand);
            Expression roperand = infix.getRightOperand();
            this.loadKlassesExpr(klasses, roperand);

        } else if (expr instanceof ParenthesizedExpression) {
            ParenthesizedExpression paren = (ParenthesizedExpression)expr;
            this.loadKlassesExpr(klasses, paren.getExpression());

        } else if (expr instanceof Assignment) {
            Assignment assn = (Assignment)expr;
            Assignment.Operator op = assn.getOperator();
            this.loadKlassesExpr(klasses, assn.getLeftHandSide());
            if (op != Assignment.Operator.ASSIGN) {
                this.loadKlassesExpr(klasses, assn.getLeftHandSide());
            }
            this.loadKlassesExpr(klasses, assn.getRightHandSide());

        } else if (expr instanceof VariableDeclarationExpression) {
            VariableDeclarationExpression decl = (VariableDeclarationExpression)expr;
            DFType varType = _finder.resolveSafe(decl.getType());
            if (varType instanceof DFSourceKlass) {
                ((DFSourceKlass)varType).loadKlasses(klasses);
            }
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>) decl.fragments()) {
                Expression init = frag.getInitializer();
                if (init != null) {
                    this.loadKlassesExpr(klasses, init);
                }
            }

        } else if (expr instanceof MethodInvocation) {
            MethodInvocation invoke = (MethodInvocation)expr;
            Expression expr1 = invoke.getExpression();
            if (expr1 instanceof Name) {
                try {
                    DFType type = _finder.lookupType((Name)expr1);
                    if (type instanceof DFSourceKlass) {
                        ((DFSourceKlass)type).loadKlasses(klasses);
                    }
                } catch (TypeNotFound e) {
                }
            } else if (expr1 != null) {
                this.loadKlassesExpr(klasses, expr1);
            }
            for (Expression arg :
                     (List<Expression>) invoke.arguments()) {
                this.loadKlassesExpr(klasses, arg);
            }

        } else if (expr instanceof SuperMethodInvocation) {
            SuperMethodInvocation si = (SuperMethodInvocation)expr;
            for (Expression arg :
                     (List<Expression>) si.arguments()) {
                this.loadKlassesExpr(klasses, arg);
            }

        } else if (expr instanceof ArrayCreation) {
            ArrayCreation ac = (ArrayCreation)expr;
            for (Expression dim :
                     (List<Expression>) ac.dimensions()) {
                this.loadKlassesExpr(klasses, dim);
            }
            ArrayInitializer init = ac.getInitializer();
            if (init != null) {
                this.loadKlassesExpr(klasses, init);
            }
            DFType type = _finder.resolveSafe(ac.getType().getElementType());
            if (type instanceof DFSourceKlass) {
                ((DFSourceKlass)type).loadKlasses(klasses);
            }

        } else if (expr instanceof ArrayInitializer) {
            ArrayInitializer init = (ArrayInitializer)expr;
            for (Expression expr1 :
                     (List<Expression>) init.expressions()) {
                this.loadKlassesExpr(klasses, expr1);
            }

        } else if (expr instanceof ArrayAccess) {
            ArrayAccess aa = (ArrayAccess)expr;
            this.loadKlassesExpr(klasses, aa.getArray());
            this.loadKlassesExpr(klasses, aa.getIndex());

        } else if (expr instanceof FieldAccess) {
            FieldAccess fa = (FieldAccess)expr;
            SimpleName fieldName = fa.getName();
            this.loadKlassesExpr(klasses, fa.getExpression());

        } else if (expr instanceof SuperFieldAccess) {
            SuperFieldAccess sfa = (SuperFieldAccess)expr;
            SimpleName fieldName = sfa.getName();

        } else if (expr instanceof CastExpression) {
            CastExpression cast = (CastExpression)expr;
            this.loadKlassesExpr(klasses, cast.getExpression());
            DFType type = _finder.resolveSafe(cast.getType());
            if (type instanceof DFSourceKlass) {
                ((DFSourceKlass)type).loadKlasses(klasses);
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
                    ((DFSourceKlass)instKlass).loadKlasses(klasses);
                }
            } catch (TypeNotFound e) {
            }
            Expression expr1 = cstr.getExpression();
            if (expr1 != null) {
                this.loadKlassesExpr(klasses, expr1);
            }
            for (Expression arg :
                     (List<Expression>) cstr.arguments()) {
                this.loadKlassesExpr(klasses, arg);
            }

        } else if (expr instanceof ConditionalExpression) {
            ConditionalExpression cond = (ConditionalExpression)expr;
            this.loadKlassesExpr(klasses, cond.getExpression());
            this.loadKlassesExpr(klasses, cond.getThenExpression());
            this.loadKlassesExpr(klasses, cond.getElseExpression());

        } else if (expr instanceof InstanceofExpression) {
            InstanceofExpression instof = (InstanceofExpression)expr;
            this.loadKlassesExpr(klasses, instof.getLeftOperand());

        } else if (expr instanceof LambdaExpression) {
            LambdaExpression lambda = (LambdaExpression)expr;
            String id = Utils.encodeASTNode(lambda);
            DFSourceKlass lambdaKlass = (DFSourceKlass)this.getKlass(id);
            // Do not use lambda klasses until defined.

        } else if (expr instanceof MethodReference) {
            //  CreationReference
            //  ExpressionMethodReference
            //  SuperMethodReference
            //  TypeMethodReference
            MethodReference methodref = (MethodReference)expr;
            String id = Utils.encodeASTNode(methodref);
            DFSourceKlass methodRefKlass = (DFSourceKlass)this.getKlass(id);
            // Do not use methodref klasses until defined.

        } else {
            throw new InvalidSyntax(expr);
        }
    }

    /// Enum References.

    @SuppressWarnings("unchecked")
    protected void enumRefsStmt(
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
                this.enumRefsStmt(defined, innerScope, cstmt);
            }

        } else if (stmt instanceof EmptyStatement) {

        } else if (stmt instanceof VariableDeclarationStatement) {
            // "int a = 2;"
            VariableDeclarationStatement varStmt =
                (VariableDeclarationStatement)stmt;
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>) varStmt.fragments()) {
                //_outputRefs.add(scope.lookupVar(frag.getName()));
                try {
                    DFRef ref = scope.lookupVar(frag.getName());
                    Expression init = frag.getInitializer();
                    if (init != null) {
                        this.enumRefsExpr(defined, scope, init);
                        this.setLambdaType(defined, ref.getRefType(), init);
                    }
                } catch (VariableNotFound e) {
                }
            }

        } else if (stmt instanceof ExpressionStatement) {
            // "foo();"
            ExpressionStatement exprStmt = (ExpressionStatement)stmt;
            this.enumRefsExpr(defined, scope, exprStmt.getExpression());

        } else if (stmt instanceof IfStatement) {
            // "if (c) { ... } else { ... }"
            IfStatement ifStmt = (IfStatement)stmt;
            this.enumRefsExpr(defined, scope, ifStmt.getExpression());
            Statement thenStmt = ifStmt.getThenStatement();
            this.enumRefsStmt(defined, scope, thenStmt);
            Statement elseStmt = ifStmt.getElseStatement();
            if (elseStmt != null) {
                this.enumRefsStmt(defined, scope, elseStmt);
            }

        } else if (stmt instanceof SwitchStatement) {
            // "switch (x) { case 0: ...; }"
            SwitchStatement switchStmt = (SwitchStatement)stmt;
            DFType type = this.enumRefsExpr(
                defined, scope, switchStmt.getExpression());
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
            for (Statement cstmt : (List<Statement>) switchStmt.statements()) {
                if (cstmt instanceof SwitchCase) {
                    SwitchCase switchCase = (SwitchCase)cstmt;
                    Expression expr1 = switchCase.getExpression();
                    if (expr1 != null) {
                        if (enumKlass != null && expr1 instanceof SimpleName) {
                            // special treatment for enum.
                            DFRef ref = enumKlass.getField((SimpleName)expr1);
                            if (ref != null) {
                                _inputRefs.add(ref);
                            }
                        } else {
                            this.enumRefsExpr(defined, innerScope, expr1);
                        }
                    }
                } else {
                    this.enumRefsStmt(defined, innerScope, cstmt);
                }
            }

        } else if (stmt instanceof SwitchCase) {
            // Invalid "case" placement.
            throw new InvalidSyntax(stmt);

        } else if (stmt instanceof WhileStatement) {
            // "while (c) { ... }"
            WhileStatement whileStmt = (WhileStatement)stmt;
            DFLocalScope innerScope = scope.getChildByAST(stmt);
            this.enumRefsExpr(defined, scope, whileStmt.getExpression());
            this.enumRefsStmt(defined, innerScope, whileStmt.getBody());

        } else if (stmt instanceof DoStatement) {
            // "do { ... } while (c);"
            DoStatement doStmt = (DoStatement)stmt;
            DFLocalScope innerScope = scope.getChildByAST(stmt);
            this.enumRefsStmt(defined, innerScope, doStmt.getBody());
            this.enumRefsExpr(defined, scope, doStmt.getExpression());

        } else if (stmt instanceof ForStatement) {
            // "for (i = 0; i < 10; i++) { ... }"
            ForStatement forStmt = (ForStatement)stmt;
            DFLocalScope innerScope = scope.getChildByAST(stmt);
            for (Expression init : (List<Expression>) forStmt.initializers()) {
                this.enumRefsExpr(defined, innerScope, init);
            }
            Expression expr1 = forStmt.getExpression();
            if (expr1 != null) {
                this.enumRefsExpr(defined, innerScope, expr1);
            }
            this.enumRefsStmt(defined, innerScope, forStmt.getBody());
            for (Expression update : (List<Expression>) forStmt.updaters()) {
                this.enumRefsExpr(defined, innerScope, update);
            }

        } else if (stmt instanceof EnhancedForStatement) {
            // "for (x : array) { ... }"
            EnhancedForStatement eForStmt = (EnhancedForStatement)stmt;
            this.enumRefsExpr(defined, scope, eForStmt.getExpression());
            DFLocalScope innerScope = scope.getChildByAST(stmt);
            this.enumRefsStmt(defined, innerScope, eForStmt.getBody());

        } else if (stmt instanceof ReturnStatement) {
            // "return 42;"
            ReturnStatement rtrnStmt = (ReturnStatement)stmt;
            Expression expr1 = rtrnStmt.getExpression();
            if (expr1 != null) {
                this.enumRefsExpr(defined, scope, expr1);
            }
            // Return is handled as an Exit, not an output.

        } else if (stmt instanceof BreakStatement) {
            // "break;"

        } else if (stmt instanceof ContinueStatement) {
            // "continue;"

        } else if (stmt instanceof LabeledStatement) {
            // "here:"
            LabeledStatement labeledStmt = (LabeledStatement)stmt;
            this.enumRefsStmt(defined, scope, labeledStmt.getBody());

        } else if (stmt instanceof SynchronizedStatement) {
            // "synchronized (this) { ... }"
            SynchronizedStatement syncStmt = (SynchronizedStatement)stmt;
            this.enumRefsExpr(defined, scope, syncStmt.getExpression());
            this.enumRefsStmt(defined, scope, syncStmt.getBody());

        } else if (stmt instanceof TryStatement) {
            // "try { ... } catch (e) { ... }"
            TryStatement tryStmt = (TryStatement)stmt;
            for (CatchClause cc : (List<CatchClause>) tryStmt.catchClauses()) {
                DFLocalScope catchScope = scope.getChildByAST(cc);
                this.enumRefsStmt(defined, catchScope, cc.getBody());
            }
            DFLocalScope tryScope = scope.getChildByAST(tryStmt);
            this.enumRefsStmt(defined, tryScope, tryStmt.getBody());
            Block finBlock = tryStmt.getFinally();
            if (finBlock != null) {
                this.enumRefsStmt(defined, scope, finBlock);
            }

        } else if (stmt instanceof ThrowStatement) {
            // "throw e;"
            ThrowStatement throwStmt = (ThrowStatement)stmt;
            DFType type = this.enumRefsExpr(
                defined, scope, throwStmt.getExpression());
            // Because an exception can be catched, throw does not
            // necessarily mean this method actually throws as a whole.
            // This should be taken cared of by the "throws" clause.
            //DFRef ref = _methodScope.lookupException(type.toKlass());
            //_outputRefs.add(ref);

        } else if (stmt instanceof ConstructorInvocation) {
            // "this(args)"
            ConstructorInvocation ci = (ConstructorInvocation)stmt;
            DFRef ref = scope.lookupThis();
            //_inputRefs.add(ref);
            DFKlass klass = ref.getRefType().toKlass();
            klass.load();
            int nargs = ci.arguments().size();
            DFType[] argTypes = new DFType[nargs];
            for (int i = 0; i < nargs; i++) {
                Expression arg = (Expression)ci.arguments().get(i);
                DFType type = this.enumRefsExpr(defined, scope, arg);
                if (type == null) return;
                argTypes[i] = type;
            }
            DFMethod method1 = klass.findMethod(
                CallStyle.Constructor, (String)null, argTypes);
            if (method1 != null) {
                this.setLambdaType(
                    defined, method1.getFuncType(), ci.arguments());
            }

        } else if (stmt instanceof SuperConstructorInvocation) {
            // "super(args)"
            SuperConstructorInvocation sci = (SuperConstructorInvocation)stmt;
            DFRef ref = scope.lookupThis();
            //_inputRefs.add(ref);
            DFKlass klass = ref.getRefType().toKlass();
            DFKlass baseKlass = klass.getBaseKlass();
            baseKlass.load();
            int nargs = sci.arguments().size();
            DFType[] argTypes = new DFType[nargs];
            for (int i = 0; i < nargs; i++) {
                Expression arg = (Expression)sci.arguments().get(i);
                DFType type = this.enumRefsExpr(defined, scope, arg);
                if (type == null) return;
                argTypes[i] = type;
            }
            DFMethod method1 = baseKlass.findMethod(
                CallStyle.Constructor, (String)null, argTypes);
            if (method1 != null) {
                method1.addCaller(this);
                this.setLambdaType(
                    defined, method1.getFuncType(), sci.arguments());
            }

        } else if (stmt instanceof TypeDeclarationStatement) {
            // "class K { ... }"
            // Inline classes are processed separately.

        } else {
            throw new InvalidSyntax(stmt);

        }
    }

    @SuppressWarnings("unchecked")
    protected DFType enumRefsExpr(
        Collection<DFSourceKlass> defined,
        DFLocalScope scope, Expression expr)
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
                DFType type = this.enumRefsExpr(
                    defined, scope, qname.getQualifier());
                if (type == null) {
                    // Turned out it's a class variable.
                    try {
                        type = _finder.lookupType(qname.getQualifier());
                    } catch (TypeNotFound e) {
                        return null;
                    }
                }
                DFKlass klass = type.toKlass();
                klass.load();
                SimpleName fieldName = qname.getName();
                ref = klass.getField(fieldName);
                if (ref == null) return null;
            }
            if (!ref.isLocal()) {
                _inputRefs.add(ref);
            }
            return ref.getRefType();

        } else if (expr instanceof ThisExpression) {
            // "this"
            ThisExpression thisExpr = (ThisExpression)expr;
            Name name = thisExpr.getQualifier();
            DFRef ref;
            if (name != null) {
                try {
                    DFKlass klass = _finder.lookupType(name).toKlass();
                    assert klass instanceof DFSourceKlass;
                    ref = ((DFSourceKlass)klass).getKlassScope().lookupThis();
                } catch (TypeNotFound e) {
                    return null;
                }
            } else {
                ref = scope.lookupThis();
            }
            //_inputRefs.add(ref);
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
            Type value = ((TypeLiteral)expr).getType();
            try {
                DFKlass typeval = _finder.resolve(value).toKlass();
                DFKlass klass = DFBuiltinTypes.getClassKlass().getConcreteKlass(
                    new DFKlass[] { typeval });
                klass.load();
                return klass;
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
                this.enumRefsAssignment(defined, scope, operand);
            }
            return DFNode.inferPrefixType(
                this.enumRefsExpr(defined, scope, operand), op);

        } else if (expr instanceof PostfixExpression) {
            // "y--"
            PostfixExpression postfix = (PostfixExpression)expr;
            PostfixExpression.Operator op = postfix.getOperator();
            Expression operand = postfix.getOperand();
            if (op == PostfixExpression.Operator.INCREMENT ||
                op == PostfixExpression.Operator.DECREMENT) {
                this.enumRefsAssignment(defined, scope, operand);
            }
            return this.enumRefsExpr(defined, scope, operand);

        } else if (expr instanceof InfixExpression) {
            // "a+b"
            InfixExpression infix = (InfixExpression)expr;
            InfixExpression.Operator op = infix.getOperator();
            DFType left = this.enumRefsExpr(
                defined, scope, infix.getLeftOperand());
            DFType right = this.enumRefsExpr(
                defined, scope, infix.getRightOperand());
            if (left == null || right == null) return null;
            return DFNode.inferInfixType(left, op, right);

        } else if (expr instanceof ParenthesizedExpression) {
            // "(expr)"
            ParenthesizedExpression paren = (ParenthesizedExpression)expr;
            return this.enumRefsExpr(defined, scope, paren.getExpression());

        } else if (expr instanceof Assignment) {
            // "p = q"
            Assignment assn = (Assignment)expr;
            Assignment.Operator op = assn.getOperator();
            if (op != Assignment.Operator.ASSIGN) {
                this.enumRefsExpr(defined, scope, assn.getLeftHandSide());
            }
            DFRef ref = this.enumRefsAssignment(
                defined, scope, assn.getLeftHandSide());
            if (ref != null) {
                this.setLambdaType(
                    defined, ref.getRefType(), assn.getRightHandSide());
            }
            return this.enumRefsExpr(defined, scope, assn.getRightHandSide());

        } else if (expr instanceof VariableDeclarationExpression) {
            // "int a=2"
            VariableDeclarationExpression decl =
                (VariableDeclarationExpression)expr;
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>) decl.fragments()) {
                try {
                    DFRef ref = scope.lookupVar(frag.getName());
                    //_outputRefs.add(ref);
                    Expression init = frag.getInitializer();
                    if (init != null) {
                        this.enumRefsExpr(defined, scope, init);
                        this.setLambdaType(defined, ref.getRefType(), init);
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
                DFRef ref = scope.lookupThis();
                //_inputRefs.add(ref);
                klass = ref.getRefType().toKlass();
                callStyle = CallStyle.InstanceOrStatic;
            } else {
                callStyle = CallStyle.InstanceMethod;
                if (expr1 instanceof Name) {
                    // "ClassName.method()"
                    try {
                        klass = _finder.lookupType((Name)expr1).toKlass();
                        callStyle = CallStyle.StaticMethod;
                    } catch (TypeNotFound e) {
                    }
                }
                if (klass == null) {
                    // "expr.method()"
                    DFType type = this.enumRefsExpr(defined, scope, expr1);
                    if (type == null) return null;
                    klass = type.toKlass();
                }
            }
            klass.load();
            int nargs = invoke.arguments().size();
            DFType[] argTypes = new DFType[nargs];
            for (int i = 0; i < nargs; i++) {
                Expression arg = (Expression)invoke.arguments().get(i);
                DFType type = this.enumRefsExpr(defined, scope, arg);
                if (type == null) return null;
                argTypes[i] = type;
            }
            DFMethod method1 = klass.findMethod(
                callStyle, invoke.getName(), argTypes);
            if (method1 == null) return DFUnknownType.UNKNOWN;
            for (DFMethod m : method1.getOverriders()) {
                m.addCaller(this);
            }
            this.setLambdaType(
                defined, method1.getFuncType(), invoke.arguments());
            return method1.getFuncType().getReturnType();

        } else if (expr instanceof SuperMethodInvocation) {
            // "super.method()"
            SuperMethodInvocation sinvoke = (SuperMethodInvocation)expr;
            int nargs = sinvoke.arguments().size();
            DFType[] argTypes = new DFType[nargs];
            for (int i = 0; i < nargs; i++) {
                Expression arg = (Expression)sinvoke.arguments().get(i);
                DFType type = this.enumRefsExpr(defined, scope, arg);
                if (type == null) return null;
                argTypes[i] = type;
            }
            DFRef ref = scope.lookupThis();
            //_inputRefs.add(ref);
            DFKlass klass = ref.getRefType().toKlass();
            klass.load();
            DFKlass baseKlass = klass.getBaseKlass();
            baseKlass.load();
            DFMethod method1 = baseKlass.findMethod(
                CallStyle.InstanceMethod, sinvoke.getName(), argTypes);
            if (method1 == null) return DFUnknownType.UNKNOWN;
            method1.addCaller(this);
            this.setLambdaType(
                defined, method1.getFuncType(), sinvoke.arguments());
            return method1.getFuncType().getReturnType();

        } else if (expr instanceof ArrayCreation) {
            // "new int[10]"
            ArrayCreation ac = (ArrayCreation)expr;
            for (Expression dim : (List<Expression>) ac.dimensions()) {
                this.enumRefsExpr(defined, scope, dim);
            }
            ArrayInitializer init = ac.getInitializer();
            if (init != null) {
                this.enumRefsExpr(defined, scope, init);
            }
            try {
                DFType type = _finder.resolve(ac.getType().getElementType());
                type.toKlass().load();
                return type;
            } catch (TypeNotFound e) {
                return null;
            }

        } else if (expr instanceof ArrayInitializer) {
            // "{ 5,9,4,0 }"
            ArrayInitializer init = (ArrayInitializer)expr;
            DFType type = null;
            for (Expression expr1 : (List<Expression>) init.expressions()) {
                type = this.enumRefsExpr(defined, scope, expr1);
            }
            return type;

        } else if (expr instanceof ArrayAccess) {
            // "a[0]"
            ArrayAccess aa = (ArrayAccess)expr;
            this.enumRefsExpr(defined, scope, aa.getIndex());
            DFType type = this.enumRefsExpr(defined, scope, aa.getArray());
            if (type instanceof DFArrayType) {
                DFRef ref = scope.lookupArray(type);
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
                type = this.enumRefsExpr(defined, scope, expr1);
                if (type == null) return null;
            }
            DFKlass klass = type.toKlass();
            klass.load();
            SimpleName fieldName = fa.getName();
            DFRef ref = klass.getField(fieldName);
            if (ref == null) return null;
            _inputRefs.add(ref);
            return ref.getRefType();

        } else if (expr instanceof SuperFieldAccess) {
            // "super.baa"
            SuperFieldAccess sfa = (SuperFieldAccess)expr;
            SimpleName fieldName = sfa.getName();
            DFRef ref = scope.lookupThis();
            //_inputRefs.add(ref);
            DFKlass klass = ref.getRefType().toKlass().getBaseKlass();
            klass.load();
            DFRef ref2 = klass.getField(fieldName);
            if (ref2 == null) return null;
            _inputRefs.add(ref2);
            return ref2.getRefType();

        } else if (expr instanceof CastExpression) {
            // "(String)"
            CastExpression cast = (CastExpression)expr;
            this.enumRefsExpr(defined, scope, cast.getExpression());
            try {
                DFType type = _finder.resolve(cast.getType());
                type.toKlass().load();
                return type;
            } catch (TypeNotFound e) {
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
            instKlass.load();
            Expression expr1 = cstr.getExpression();
            if (expr1 != null) {
                this.enumRefsExpr(defined, scope, expr1);
            }
            int nargs = cstr.arguments().size();
            DFType[] argTypes = new DFType[nargs];
            for (int i = 0; i < nargs; i++) {
                Expression arg = (Expression)cstr.arguments().get(i);
                DFType type = this.enumRefsExpr(defined, scope, arg);
                if (type == null) return null;
                argTypes[i] = type;
            }
            DFMethod method1 = instKlass.findMethod(
                CallStyle.Constructor, (String)null, argTypes);
            if (method1 != null) {
                method1.addCaller(this);
                this.setLambdaType(
                    defined, method1.getFuncType(), cstr.arguments());
            }
            return instKlass;

        } else if (expr instanceof ConditionalExpression) {
            // "c? a : b"
            ConditionalExpression cond = (ConditionalExpression)expr;
            this.enumRefsExpr(defined, scope, cond.getExpression());
            this.enumRefsExpr(defined, scope, cond.getThenExpression());
            return this.enumRefsExpr(defined, scope, cond.getElseExpression());

        } else if (expr instanceof InstanceofExpression) {
            // "a instanceof A"
            return DFBasicType.BOOLEAN;

        } else if (expr instanceof LambdaExpression) {
            // "x -> { ... }"
            LambdaExpression lambda = (LambdaExpression)expr;
            String id = Utils.encodeASTNode(lambda);
            DFLambdaKlass lambdaKlass = (DFLambdaKlass)this.getKlass(id);
            lambdaKlass.load();
            for (DFLambdaKlass.CapturedRef captured :
                     lambdaKlass.getCapturedRefs()) {
                _inputRefs.add(captured.getOriginal());
            }
            return lambdaKlass;

        } else if (expr instanceof MethodReference) {
            MethodReference methodref = (ExpressionMethodReference)expr;
            String id = Utils.encodeASTNode(methodref);
            DFMethodRefKlass methodRefKlass = (DFMethodRefKlass)this.getKlass(id);
            methodRefKlass.load();
            return methodRefKlass;

        } else {
            // ???
            throw new InvalidSyntax(expr);
        }
    }

    private DFRef enumRefsAssignment(
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
                DFType type = this.enumRefsExpr(
                    defined, scope, qname.getQualifier());
                if (type == null) {
                    // Turned out it's a class variable.
                    try {
                        type = _finder.lookupType(qname.getQualifier());
                    } catch (TypeNotFound e) {
                        return null;
                    }
                }
                //_inputRefs.add(scope.lookupThis());
                DFKlass klass = type.toKlass();
                klass.load();
                SimpleName fieldName = qname.getName();
                ref = klass.getField(fieldName);
                if (ref == null) return null;
            }
            if (!ref.isLocal()) {
                _outputRefs.add(ref);
            }
            return ref;

        } else if (expr instanceof ArrayAccess) {
            // "a[0]"
            ArrayAccess aa = (ArrayAccess)expr;
            DFType type = this.enumRefsExpr(defined, scope, aa.getArray());
            this.enumRefsExpr(defined, scope, aa.getIndex());
            if (type instanceof DFArrayType) {
                DFRef ref = scope.lookupArray(type);
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
                type = this.enumRefsExpr(defined, scope, expr1);
                if (type == null) return null;
            }
            DFKlass klass = type.toKlass();
            klass.load();
            SimpleName fieldName = fa.getName();
            DFRef ref = klass.getField(fieldName);
            if (ref == null) return null;
            _outputRefs.add(ref);
            return ref;

        } else if (expr instanceof SuperFieldAccess) {
            // "super.baa"
            SuperFieldAccess sfa = (SuperFieldAccess)expr;
            SimpleName fieldName = sfa.getName();
            DFRef ref = scope.lookupThis();
            //_inputRefs.add(ref);
            DFKlass klass = ref.getRefType().toKlass().getBaseKlass();
            klass.load();
            DFRef ref2 = klass.getField(fieldName);
            if (ref2 == null) return null;
            _outputRefs.add(ref2);
            return ref2;

        } else if (expr instanceof ParenthesizedExpression) {
            ParenthesizedExpression paren = (ParenthesizedExpression)expr;
            return this.enumRefsAssignment(
                defined, scope, paren.getExpression());

        } else {
            throw new InvalidSyntax(expr);
        }
    }

    /// Set Lambda types.

    protected void setLambdaType(
        Collection<DFSourceKlass> defined,
        DFFunctionType funcType, List<Expression> exprs)
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
            setLambdaType(defined, type, paren.getExpression());

        } else if (expr instanceof LambdaExpression) {
            LambdaExpression lambda = (LambdaExpression)expr;
            String id = Utils.encodeASTNode(lambda);
            DFLambdaKlass lambdaKlass = (DFLambdaKlass)this.getKlass(id);
            lambdaKlass.load();
            lambdaKlass.setBaseKlass(type.toKlass());
            if (lambdaKlass.isDefined()) {
                defined.add(lambdaKlass);
            }

        } else if (expr instanceof MethodReference) {
            MethodReference methodref = (MethodReference)expr;
            String id = Utils.encodeASTNode(methodref);
            DFMethodRefKlass methodRefKlass = (DFMethodRefKlass)this.getKlass(id);
            methodRefKlass.load();
            methodRefKlass.setBaseKlass(type.toKlass());
            if (methodRefKlass.isDefined()) {
                defined.add(methodRefKlass);
            }

        }
    }

    /// Expand References.

    public boolean expandRefs(DFSourceMethod callee) {
        boolean added = false;
        for (DFRef ref : callee._inputRefs) {
            if (!_inputRefs.contains(ref)) {
                _inputRefs.add(ref);
                added = true;
            }
        }
        for (DFRef ref : callee._outputRefs) {
            if (!_outputRefs.contains(ref)) {
                _outputRefs.add(ref);
                added = true;
            }
        }
        return added;
    }

    /**
     * Performs dataflow analysis for a given method.
     */

    // loadKlasses: enumerate all the referenced Klasses.
    public abstract void loadKlasses(Collection<DFSourceKlass> klasses)
        throws InvalidSyntax;

    // enumRefs: list all the internal DFRefs AND fix the lambdas.
    public abstract void enumRefs(Collection<DFSourceKlass> defined)
        throws InvalidSyntax;

    // writeGraph: generate graphs.
    public abstract void writeGraph(Exporter exporter)
        throws InvalidSyntax, EntityNotFound;

    public abstract ASTNode getAST();

    @SuppressWarnings("unchecked")
    public void processBodyDecls(
        DFGraph graph, DFContext ctx, List<BodyDeclaration> decls)
        throws InvalidSyntax, EntityNotFound {
        assert _methodScope != null;
        assert _finder != null;

        // Create input nodes.
        if (this.isTransparent()) {
            for (DFRef ref : this.getInputRefs()) {
                DFNode input = new InputNode(graph, _methodScope, ref, null);
                ctx.set(input);
            }
        }

        graph.processBodyDecls(
            ctx, _methodScope, _srcklass, decls);

        // Create output nodes.
        if (this.isTransparent()) {
            for (DFRef ref : this.getOutputRefs()) {
                DFNode output = new OutputNode(graph, _methodScope, ref, null);
                output.accept(ctx.get(ref));
            }
        }

        graph.cleanup(null);
    }

    @SuppressWarnings("unchecked")
    public void processMethodBody(
        DFGraph graph, DFContext ctx, ASTNode body)
        throws InvalidSyntax, EntityNotFound {
        assert _methodScope != null;
        assert _finder != null;

        ConsistentHashSet<DFNode> preserved = new ConsistentHashSet<DFNode>();
        {
            DFRef ref = _methodScope.lookupThis();
            DFNode input = new InputNode(graph, _methodScope, ref, null);
            ctx.set(input);
            preserved.add(input);
        }
        if (this.isTransparent()) {
            for (DFRef ref : this.getInputRefs()) {
                DFNode input = new InputNode(graph, _methodScope, ref, null);
                ctx.set(input);
                preserved.add(input);
            }
        }

        try {
            graph.processMethodBody(ctx, _methodScope, body);
        } catch (MethodNotFound e) {
            e.setMethod(this);
            Logger.error(
                "DFMethod.processMethod: MethodNotFound",
                this, e.name+"("+Utils.join(e.argTypes)+")");
            throw e;
        } catch (EntityNotFound e) {
            e.setMethod(this);
            Logger.error(
                "DFMethod.processMethod: EntityNotFound",
                this, e.name);
            throw e;
        }

        // Create output nodes.
        {
            DFRef ref = _methodScope.lookupReturn();
            if (ctx.getLast(ref) != null) {
                DFNode output = new OutputNode(graph, _methodScope, ref, null);
                output.accept(ctx.getLast(ref));
                preserved.add(output);
            }
        }
        for (DFRef ref : _methodScope.getExcRefs()) {
            if (ctx.getLast(ref) != null) {
                DFNode output = new OutputNode(graph, _methodScope, ref, null);
                output.accept(ctx.getLast(ref));
                preserved.add(output);
            }
        }
        if (this.isTransparent()) {
            for (DFRef ref : this.getOutputRefs()) {
                DFNode output = new OutputNode(graph, _methodScope, ref, null);
                output.accept(ctx.get(ref));
                preserved.add(output);
            }
        }

        // Do not remove input/output nodes.
        graph.cleanup(preserved);
    }

    protected class MethodGraph extends DFGraph {

        private String _graphId;

        public MethodGraph(String graphId) {
            super(DFSourceMethod.this);
            _graphId = graphId;
        }

        @Override
        public String getGraphId() {
            return _graphId;
        }
    }

    // MethodScope
    protected class MethodScope extends DFLocalScope {

        private InternalRef _return = null;
        private InternalRef[] _arguments = null;
        private ConsistentHashMap<DFType, DFRef> _exceptions =
            new ConsistentHashMap<DFType, DFRef>();

        protected MethodScope(DFVarScope outer, String name) {
            super(outer, name);
        }

        public boolean isDefined() {
            return _return != null && _arguments != null;
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
                ref = new InternalRef(type, type.getTypeName());
                _exceptions.put(type, ref);
            }
            return ref;
        }

        public List<DFRef> getExcRefs() {
            return _exceptions.values();
        }

        protected void buildInternalRefs(List<VariableDeclaration> parameters) {
            DFFunctionType funcType = DFSourceMethod.this.getFuncType();
            _return = new InternalRef(funcType.getReturnType(), "return");
            _arguments = new InternalRef[parameters.size()];
            DFType[] argTypes = funcType.getRealArgTypes();
            int i = 0;
            for (VariableDeclaration decl : parameters) {
                DFType argType = argTypes[i];
                int ndims = decl.getExtraDimensions();
                if (ndims != 0) {
                    argType = DFArrayType.getType(argType, ndims);
                }
                String name;
                if (funcType.isVarArg(i)) {
                    name = "varargs";
                } else {
                    name = "arg"+i;
                }
                _arguments[i] = new InternalRef(argType, name);
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
            public boolean isLocal() {
                return false;
            }

            @Override
            public String getFullName() {
                return "#"+_name;
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
