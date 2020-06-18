//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import java.util.jar.*;
import javax.xml.stream.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


class DFAbstTypeDeclKlass extends DFSourceKlass {

    @SuppressWarnings("unchecked")
    public DFAbstTypeDeclKlass(
        String filePath, AbstractTypeDeclaration abstTypeDecl,
        DFTypeSpace outerSpace, DFVarScope outerScope, DFSourceKlass outerKlass)
        throws InvalidSyntax {
        super(filePath, abstTypeDecl, abstTypeDecl.getName().getIdentifier(),
              outerSpace, outerScope, outerKlass);
        if (abstTypeDecl instanceof TypeDeclaration) {
            TypeDeclaration typeDecl = (TypeDeclaration)abstTypeDecl;
            DFMapType[] mapTypes = this.getMapTypes(this, typeDecl.typeParameters());
            if (mapTypes != null) {
                this.setMapTypes(mapTypes);
            }
        }
        this.buildTypeFromDecls(
            abstTypeDecl, abstTypeDecl.bodyDeclarations());
    }

    @SuppressWarnings("unchecked")
    private DFAbstTypeDeclKlass(
        DFSourceKlass genericKlass, DFKlass[] paramTypes)
        throws InvalidSyntax {
        super(genericKlass, paramTypes);
        ASTNode ast = this.getTree();
        assert ast instanceof AbstractTypeDeclaration;
        AbstractTypeDeclaration abstTypeDecl = (AbstractTypeDeclaration)ast;
        this.buildTypeFromDecls(
            abstTypeDecl, abstTypeDecl.bodyDeclarations());
    }

    // Constructor for a parameterized klass.
    @Override
    protected DFKlass parameterize(DFKlass[] paramTypes)
        throws InvalidSyntax {
        assert paramTypes != null;
        return new DFAbstTypeDeclKlass(this, paramTypes);
    }

}

class DFAnonymousKlass extends DFSourceKlass {

    @SuppressWarnings("unchecked")
    protected DFAnonymousKlass(
        String filePath, ClassInstanceCreation cstr,
        DFTypeSpace outerSpace, DFVarScope outerScope, DFSourceKlass outerKlass)
        throws InvalidSyntax {
        super(filePath, cstr, Utils.encodeASTNode(cstr),
              outerSpace, outerScope, outerKlass);
        this.buildTypeFromDecls(
            cstr, cstr.getAnonymousClassDeclaration().bodyDeclarations());
    }

}

//  DFSourceKlass
//
public class DFSourceKlass extends DFKlass {

    // These fields are set immediately after construction.
    private String _filePath = null;
    private ASTNode _ast = null;

    // The following fields are available after the klass is loaded. (Stage3)
    private DFMethod _initMethod = null;
    private boolean _interface = false;
    private DFKlass _baseKlass = null;
    private DFKlass[] _baseIfaces = null;


    protected DFSourceKlass(
        String filePath, ASTNode ast, String id,
        DFTypeSpace outerSpace, DFVarScope outerScope, DFSourceKlass outerKlass)
        throws InvalidSyntax {
        super(id, outerSpace, outerScope, outerKlass);
        _filePath = filePath;
        _ast = ast;
    }

    protected DFSourceKlass(
        DFSourceKlass genericKlass, DFKlass[] paramTypes)
        throws InvalidSyntax {
        super(genericKlass, paramTypes);
        _filePath = genericKlass._filePath;
        _ast = genericKlass._ast;
    }

    @Override
    public void writeXML(XMLStreamWriter writer)
        throws XMLStreamException {
        writer.writeAttribute("path", this.getFilePath());
        super.writeXML(writer);
    }

    @Override
    protected void dumpContents(PrintStream out, String indent) {
        super.dumpContents(out, indent);
        if (_baseKlass != null) {
            _baseKlass.dump(out, indent);
        }
        if (_baseIfaces != null) {
            for (DFKlass iface : _baseIfaces) {
                if (iface != null) {
                    iface.dump(out, indent);
                }
            }
        }
    }

    public String getFilePath() {
        return _filePath;
    }
    public ASTNode getTree() {
        return _ast;
    }

    @Override
    public boolean isInterface() {
        assert this.isDefined();
        return _interface;
    }

    @Override
    public boolean isEnum() {
        assert this.isDefined();
        return (_baseKlass != null &&
                _baseKlass.getGenericKlass() == DFBuiltinTypes.getEnumKlass());
    }

    @Override
    public DFKlass getBaseKlass() {
        assert this.isDefined();
        if (_baseKlass != null) return _baseKlass;
        return super.getBaseKlass();
    }

    @Override
    public DFKlass[] getBaseIfaces() {
        assert this.isDefined();
        return _baseIfaces;
    }

    public DFMethod getInitMethod() {
        assert this.isDefined();
        return _initMethod;
    }

    @Override
    public DFMethod findMethod(
        DFMethod.CallStyle callStyle, String id, DFType[] argTypes) {
        assert this.isDefined();
        DFMethod method = super.findMethod(callStyle, id, argTypes);
        if (method != null) return method;
        if (_baseKlass != null) {
            method = _baseKlass.findMethod(callStyle, id, argTypes);
            if (method != null) return method;
        }
        if (_baseIfaces != null) {
            for (DFKlass iface : _baseIfaces) {
                method = iface.findMethod(callStyle, id, argTypes);
                if (method != null) return method;
            }
        }
        return null;
    }

    public void overrideMethods() {
        // override the methods.
        for (DFMethod method : this.getMethods()) {
            if (_baseKlass != null) {
                this.overrideMethod(_baseKlass, method);
            }
            if (_baseIfaces != null) {
                for (DFKlass iface : _baseIfaces) {
                    this.overrideMethod(iface, method);
                }
            }
        }
    }

    private void overrideMethod(DFKlass klass, DFMethod method1) {
        for (DFMethod method0 : klass.getMethods()) {
            if (method0.addOverrider(method1)) break;
        }
    }

    @SuppressWarnings("unchecked")
    protected void buildTypeFromDecls(ASTNode ast, List<BodyDeclaration> decls)
        throws InvalidSyntax {

        _initMethod = new DFMethod(
            this, DFMethod.CallStyle.Initializer, false,
            "<clinit>", "<clinit>", this.getKlassScope());
        _initMethod.setTree(ast);

        for (BodyDeclaration body : decls) {
            if (body instanceof AbstractTypeDeclaration) {
                AbstractTypeDeclaration abstTypeDecl = (AbstractTypeDeclaration)body;
                String id = abstTypeDecl.getName().getIdentifier();
                String path = this.getFilePath();
                DFSourceKlass klass = new DFAbstTypeDeclKlass(
                    path, abstTypeDecl, this, this.getKlassScope(), this);
                this.addKlass(id, klass);

            } else if (body instanceof FieldDeclaration) {
                FieldDeclaration fieldDecl = (FieldDeclaration)body;
                for (VariableDeclarationFragment frag :
                         (List<VariableDeclarationFragment>) fieldDecl.fragments()) {
                    Expression init = frag.getInitializer();
                    if (init != null) {
                        this.buildTypeFromExpr(init, _initMethod, _initMethod.getScope());
                    }
                }

            } else if (body instanceof MethodDeclaration) {
                MethodDeclaration methodDecl = (MethodDeclaration)body;
                String id = Utils.encodeASTNode(methodDecl);
                String name;
                DFMethod.CallStyle callStyle;
                if (methodDecl.isConstructor()) {
                    name = "<init>";
                    callStyle = DFMethod.CallStyle.Constructor;
                } else {
                    name = methodDecl.getName().getIdentifier();
                    callStyle = (isStatic(methodDecl))?
                        DFMethod.CallStyle.StaticMethod :
                        DFMethod.CallStyle.InstanceMethod;
                }
                Statement stmt = methodDecl.getBody();
                DFMethod method = new DFMethod(
                    this, callStyle, (stmt == null),
                    id, name, this.getKlassScope());
                DFMapType[] mapTypes = method.getMapTypes(this, methodDecl.typeParameters());
                method.setMapTypes(mapTypes);
                method.setTree(methodDecl);
                this.addMethod(method, id);
                if (stmt != null) {
                    this.buildTypeFromStmt(stmt, method, method.getScope());
                }

            } else if (body instanceof AnnotationTypeMemberDeclaration) {
                ;

            } else if (body instanceof Initializer) {
                Initializer initializer = (Initializer)body;
                Statement stmt = initializer.getBody();
                if (stmt != null) {
                    this.buildTypeFromStmt(stmt, _initMethod, _initMethod.getScope());
                }

            } else {
                throw new InvalidSyntax(body);
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected void buildTypeFromStmt(
        Statement ast,
        DFTypeSpace space, DFLocalScope outerScope)
        throws InvalidSyntax {
        assert ast != null;

        if (ast instanceof AssertStatement) {

        } else if (ast instanceof Block) {
            Block block = (Block)ast;
            DFLocalScope innerScope = outerScope.addChild(ast);
            for (Statement stmt :
                     (List<Statement>) block.statements()) {
                this.buildTypeFromStmt(stmt, space, innerScope);
            }

        } else if (ast instanceof EmptyStatement) {

        } else if (ast instanceof VariableDeclarationStatement) {
            VariableDeclarationStatement varStmt =
                (VariableDeclarationStatement)ast;
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>) varStmt.fragments()) {
                Expression expr = frag.getInitializer();
                if (expr != null) {
                    this.buildTypeFromExpr(expr, space, outerScope);
                }
            }

        } else if (ast instanceof ExpressionStatement) {
            ExpressionStatement exprStmt = (ExpressionStatement)ast;
            this.buildTypeFromExpr(exprStmt.getExpression(), space, outerScope);

        } else if (ast instanceof ReturnStatement) {
            ReturnStatement returnStmt = (ReturnStatement)ast;
            Expression expr = returnStmt.getExpression();
            if (expr != null) {
                this.buildTypeFromExpr(expr, space, outerScope);
            }

        } else if (ast instanceof IfStatement) {
            IfStatement ifStmt = (IfStatement)ast;
            this.buildTypeFromExpr(ifStmt.getExpression(), space, outerScope);
            Statement thenStmt = ifStmt.getThenStatement();
            this.buildTypeFromStmt(thenStmt, space, outerScope);
            Statement elseStmt = ifStmt.getElseStatement();
            if (elseStmt != null) {
                this.buildTypeFromStmt(elseStmt, space, outerScope);
            }

        } else if (ast instanceof SwitchStatement) {
            SwitchStatement switchStmt = (SwitchStatement)ast;
            DFLocalScope innerScope = outerScope.addChild(ast);
            this.buildTypeFromExpr(switchStmt.getExpression(), space, innerScope);
            for (Statement stmt :
                     (List<Statement>) switchStmt.statements()) {
                this.buildTypeFromStmt(stmt, space, innerScope);
            }

        } else if (ast instanceof SwitchCase) {
            SwitchCase switchCase = (SwitchCase)ast;
            Expression expr = switchCase.getExpression();
            if (expr != null) {
                this.buildTypeFromExpr(expr, space, outerScope);
            }

        } else if (ast instanceof WhileStatement) {
            WhileStatement whileStmt = (WhileStatement)ast;
            this.buildTypeFromExpr(whileStmt.getExpression(), space, outerScope);
            DFLocalScope innerScope = outerScope.addChild(ast);
            Statement stmt = whileStmt.getBody();
            this.buildTypeFromStmt(stmt, space, innerScope);

        } else if (ast instanceof DoStatement) {
            DoStatement doStmt = (DoStatement)ast;
            DFLocalScope innerScope = outerScope.addChild(ast);
            Statement stmt = doStmt.getBody();
            this.buildTypeFromStmt(stmt, space, innerScope);
            this.buildTypeFromExpr(doStmt.getExpression(), space, innerScope);

        } else if (ast instanceof ForStatement) {
            ForStatement forStmt = (ForStatement)ast;
            DFLocalScope innerScope = outerScope.addChild(ast);
            for (Expression init :
                     (List<Expression>) forStmt.initializers()) {
                this.buildTypeFromExpr(init, space, innerScope);
            }
            Expression expr = forStmt.getExpression();
            if (expr != null) {
                this.buildTypeFromExpr(expr, space, innerScope);
            }
            Statement stmt = forStmt.getBody();
            this.buildTypeFromStmt(stmt, space, innerScope);
            for (Expression update :
                     (List<Expression>) forStmt.updaters()) {
                this.buildTypeFromExpr(update, space, innerScope);
            }

        } else if (ast instanceof EnhancedForStatement) {
            EnhancedForStatement eForStmt = (EnhancedForStatement)ast;
            this.buildTypeFromExpr(eForStmt.getExpression(), space, outerScope);
            DFLocalScope innerScope = outerScope.addChild(ast);
            this.buildTypeFromStmt(eForStmt.getBody(), space, innerScope);

        } else if (ast instanceof BreakStatement) {

        } else if (ast instanceof ContinueStatement) {

        } else if (ast instanceof LabeledStatement) {
            LabeledStatement labeledStmt = (LabeledStatement)ast;
            Statement stmt = labeledStmt.getBody();
            this.buildTypeFromStmt(stmt, space, outerScope);

        } else if (ast instanceof SynchronizedStatement) {
            SynchronizedStatement syncStmt = (SynchronizedStatement)ast;
            this.buildTypeFromExpr(syncStmt.getExpression(), space, outerScope);
            this.buildTypeFromStmt(syncStmt.getBody(), space, outerScope);

        } else if (ast instanceof TryStatement) {
            TryStatement tryStmt = (TryStatement)ast;
            DFLocalScope innerScope = outerScope.addChild(ast);
            for (VariableDeclarationExpression decl :
                     (List<VariableDeclarationExpression>) tryStmt.resources()) {
                this.buildTypeFromExpr(decl, space, innerScope);
            }
            this.buildTypeFromStmt(tryStmt.getBody(), space, innerScope);
            for (CatchClause cc :
                     (List<CatchClause>) tryStmt.catchClauses()) {
                DFLocalScope catchScope = outerScope.addChild(cc);
                this.buildTypeFromStmt(cc.getBody(), space, catchScope);
            }
            Block finBlock = tryStmt.getFinally();
            if (finBlock != null) {
                this.buildTypeFromStmt(finBlock, space, outerScope);
            }

        } else if (ast instanceof ThrowStatement) {
            ThrowStatement throwStmt = (ThrowStatement)ast;
            Expression expr = throwStmt.getExpression();
            if (expr != null) {
                this.buildTypeFromExpr(expr, space, outerScope);
            }

        } else if (ast instanceof ConstructorInvocation) {
            ConstructorInvocation ci = (ConstructorInvocation)ast;
            for (Expression expr :
                     (List<Expression>) ci.arguments()) {
                this.buildTypeFromExpr(expr, space, outerScope);
            }

        } else if (ast instanceof SuperConstructorInvocation) {
            SuperConstructorInvocation sci = (SuperConstructorInvocation)ast;
            for (Expression expr :
                     (List<Expression>) sci.arguments()) {
                this.buildTypeFromExpr(expr, space, outerScope);
            }

        } else if (ast instanceof TypeDeclarationStatement) {
            TypeDeclarationStatement typeDeclStmt = (TypeDeclarationStatement)ast;
            AbstractTypeDeclaration abstTypeDecl = typeDeclStmt.getDeclaration();
            String id = abstTypeDecl.getName().getIdentifier();
            String path = this.getFilePath();
            DFSourceKlass klass = new DFAbstTypeDeclKlass(
                path, abstTypeDecl, space, outerScope, this);
            this.addKlass(id, klass);

        } else {
            throw new InvalidSyntax(ast);

        }
    }

    @SuppressWarnings("unchecked")
    protected void buildTypeFromExpr(
        Expression expr,
        DFTypeSpace space, DFVarScope outerScope)
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
            this.buildTypeFromExpr(prefix.getOperand(), space, outerScope);

        } else if (expr instanceof PostfixExpression) {
            PostfixExpression postfix = (PostfixExpression)expr;
            this.buildTypeFromExpr(postfix.getOperand(), space, outerScope);

        } else if (expr instanceof InfixExpression) {
            InfixExpression infix = (InfixExpression)expr;
            this.buildTypeFromExpr(infix.getLeftOperand(), space, outerScope);
            this.buildTypeFromExpr(infix.getRightOperand(), space, outerScope);

        } else if (expr instanceof ParenthesizedExpression) {
            ParenthesizedExpression paren = (ParenthesizedExpression)expr;
            this.buildTypeFromExpr(paren.getExpression(), space, outerScope);

        } else if (expr instanceof Assignment) {
            Assignment assn = (Assignment)expr;
            this.buildTypeFromExpr(assn.getLeftHandSide(), space, outerScope);
            this.buildTypeFromExpr(assn.getRightHandSide(), space, outerScope);

        } else if (expr instanceof VariableDeclarationExpression) {
            VariableDeclarationExpression decl =
                (VariableDeclarationExpression)expr;
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>) decl.fragments()) {
                Expression init = frag.getInitializer();
                if (init != null) {
                    this.buildTypeFromExpr(init, space, outerScope);
                }
            }

        } else if (expr instanceof MethodInvocation) {
            MethodInvocation invoke = (MethodInvocation)expr;
            Expression expr1 = invoke.getExpression();
            if (expr1 != null) {
                this.buildTypeFromExpr(expr1, space, outerScope);
            }
            for (Expression arg : (List<Expression>) invoke.arguments()) {
                this.buildTypeFromExpr(arg, space, outerScope);
            }

        } else if (expr instanceof SuperMethodInvocation) {
            SuperMethodInvocation sinvoke = (SuperMethodInvocation)expr;
            for (Expression arg : (List<Expression>) sinvoke.arguments()) {
                this.buildTypeFromExpr(arg, space, outerScope);
            }

        } else if (expr instanceof ArrayCreation) {
            ArrayCreation ac = (ArrayCreation)expr;
            for (Expression dim : (List<Expression>) ac.dimensions()) {
                this.buildTypeFromExpr(dim, space, outerScope);
            }
            ArrayInitializer init = ac.getInitializer();
            if (init != null) {
                this.buildTypeFromExpr(init, space, outerScope);
            }

        } else if (expr instanceof ArrayInitializer) {
            ArrayInitializer init = (ArrayInitializer)expr;
            for (Expression expr1 : (List<Expression>) init.expressions()) {
                this.buildTypeFromExpr(expr1, space, outerScope);
            }

        } else if (expr instanceof ArrayAccess) {
            ArrayAccess aa = (ArrayAccess)expr;
            this.buildTypeFromExpr(aa.getIndex(), space, outerScope);
            this.buildTypeFromExpr(aa.getArray(), space, outerScope);

        } else if (expr instanceof FieldAccess) {
            FieldAccess fa = (FieldAccess)expr;
            this.buildTypeFromExpr(fa.getExpression(), space, outerScope);

        } else if (expr instanceof SuperFieldAccess) {

        } else if (expr instanceof CastExpression) {
            CastExpression cast = (CastExpression)expr;
            this.buildTypeFromExpr(cast.getExpression(), space, outerScope);

        } else if (expr instanceof ClassInstanceCreation) {
            ClassInstanceCreation cstr = (ClassInstanceCreation)expr;
            Expression expr1 = cstr.getExpression();
            if (expr1 != null) {
                this.buildTypeFromExpr(expr1, space, outerScope);
            }
            for (Expression arg : (List<Expression>) cstr.arguments()) {
                this.buildTypeFromExpr(arg, space, outerScope);
            }
            if (cstr.getAnonymousClassDeclaration() != null) {
                String id = Utils.encodeASTNode(cstr);
                DFSourceKlass anonKlass = new DFAnonymousKlass(
                    this.getFilePath(), cstr, space, outerScope, this);
                space.addKlass(id, anonKlass);
            }

        } else if (expr instanceof ConditionalExpression) {
            ConditionalExpression cond = (ConditionalExpression)expr;
            this.buildTypeFromExpr(cond.getExpression(), space, outerScope);
            this.buildTypeFromExpr(cond.getThenExpression(), space, outerScope);
            this.buildTypeFromExpr(cond.getElseExpression(), space, outerScope);

        } else if (expr instanceof InstanceofExpression) {

        } else if (expr instanceof LambdaExpression) {
            LambdaExpression lambda = (LambdaExpression)expr;
            String id = Utils.encodeASTNode(lambda);
            DFSourceKlass lambdaKlass = new DFLambdaKlass(
                this.getFilePath(), lambda, space, outerScope, this);
            space.addKlass(id, lambdaKlass);

        } else if (expr instanceof MethodReference) {
            //  CreationReference
            //  ExpressionMethodReference
            //  SuperMethodReference
            //  TypeMethodReference
            MethodReference methodref = (MethodReference)expr;
            String id = Utils.encodeASTNode(methodref);
            DFSourceKlass methodRefKlass = new DFMethodRefKlass(
                this.getFilePath(), methodref, space, outerScope, this);
            space.addKlass(id, methodRefKlass);

        } else {
            // ???
            throw new InvalidSyntax(expr);
        }
    }

    protected void build(DFTypeFinder finder)
        throws InvalidSyntax {
        super.build(finder);
        assert _ast != null;
        if (this.isGeneric()) {
            // a generic class is only referred to, but not built.
        } else {
            this.buildMembersFromAST(finder, _ast);
        }
    }

    // Only used by DFLambdaKlass.
    protected void setBaseKlass(DFKlass klass) {
        _baseKlass = klass;
    }

    @SuppressWarnings("unchecked")
    private boolean isStatic(BodyDeclaration body) {
        for (IExtendedModifier imod :
                 (List<IExtendedModifier>) body.modifiers()) {
            if (imod.isModifier()) {
                if (((Modifier)imod).isStatic()) return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    protected void buildMembersFromAST(DFTypeFinder finder, ASTNode ast)
        throws InvalidSyntax {
        //Logger.info("DFKlass.buildMembersFromAST:", this, finder);

        if (ast instanceof AbstractTypeDeclaration) {
            this.buildMembersFromAbstTypeDecl(finder, (AbstractTypeDeclaration)ast);

        } else if (ast instanceof ClassInstanceCreation) {
            this.buildMembersFromAnonDecl(finder, (ClassInstanceCreation)ast);

        } else {
            throw new InvalidSyntax(ast);
        }
    }

    private void buildMembersFromAbstTypeDecl(
        DFTypeFinder finder, AbstractTypeDeclaration abstTypeDecl)
        throws InvalidSyntax {
        if (abstTypeDecl instanceof TypeDeclaration) {
            this.buildMembersFromTypeDecl(finder, (TypeDeclaration)abstTypeDecl);

        } else if (abstTypeDecl instanceof EnumDeclaration) {
            this.buildMembersFromEnumDecl(finder, (EnumDeclaration)abstTypeDecl);

        } else if (abstTypeDecl instanceof AnnotationTypeDeclaration) {
            this.buildMembersFromAnnotTypeDecl(finder, (AnnotationTypeDeclaration)abstTypeDecl);
        }
    }

    @SuppressWarnings("unchecked")
    private void buildMembersFromTypeDecl(
        DFTypeFinder finder, TypeDeclaration typeDecl)
        throws InvalidSyntax {
        _interface = typeDecl.isInterface();
        // Load base klasses/interfaces.
        // Get superclass.
        Type superClass = typeDecl.getSuperclassType();
        if (superClass == null) {
            _baseKlass = DFBuiltinTypes.getObjectKlass();
        } else {
            try {
                _baseKlass = finder.resolve(superClass).toKlass();
                _baseKlass.load();
            } catch (TypeNotFound e) {
                Logger.error(
                    "DFKlass.buildMembersFromTypeDecl: TypeNotFound (baseKlass)",
                    this, e.name);
            }
        }
        // Get interfaces.
        List<Type> ifaces = typeDecl.superInterfaceTypes();
        _baseIfaces = new DFKlass[ifaces.size()];
        for (int i = 0; i < ifaces.size(); i++) {
            DFKlass iface = DFBuiltinTypes.getObjectKlass();
            try {
                iface = finder.resolve(ifaces.get(i)).toKlass();
            } catch (TypeNotFound e) {
                Logger.error(
                    "DFKlass.buildMembersFromTypeDecl: TypeNotFound (iface)",
                    this, e.name);
            }
            _baseIfaces[i] = iface;
        }
        for (DFKlass iface : _baseIfaces) {
            iface.load();
        }
        this.buildMembers(finder, typeDecl.bodyDeclarations());
    }

    @SuppressWarnings("unchecked")
    private void buildMembersFromAnonDecl(
        DFTypeFinder finder, ClassInstanceCreation cstr)
        throws InvalidSyntax {
        // Get superclass.
        Type superClass = cstr.getType();
        if (superClass == null) {
            _baseKlass = DFBuiltinTypes.getObjectKlass();
        } else {
            try {
                _baseKlass = finder.resolve(superClass).toKlass();
                _baseKlass.load();
            } catch (TypeNotFound e) {
                Logger.error(
                    "DFKlass.buildMembersFromAnonDecl: TypeNotFound (baseKlass)",
                    this, e.name);
            }
        }
        this.buildMembers(
            finder, cstr.getAnonymousClassDeclaration().bodyDeclarations());
    }

    @SuppressWarnings("unchecked")
    private void buildMembersFromEnumDecl(
        DFTypeFinder finder, EnumDeclaration enumDecl)
        throws InvalidSyntax {
        // Load base klasses/interfaces.
        // Get superclass.
        DFKlass enumKlass = DFBuiltinTypes.getEnumKlass();
        _baseKlass = enumKlass.getConcreteKlass(new DFKlass[] { this });
        _baseKlass.load();
        // Get interfaces.
        List<Type> ifaces = enumDecl.superInterfaceTypes();
        _baseIfaces = new DFKlass[ifaces.size()];
        for (int i = 0; i < ifaces.size(); i++) {
            DFKlass iface = DFBuiltinTypes.getObjectKlass();
            try {
                iface = finder.resolve(ifaces.get(i)).toKlass();
            } catch (TypeNotFound e) {
                Logger.error(
                    "DFKlass.buildMembersFromEnumDecl: TypeNotFound (iface)",
                    this, e.name);
            }
            _baseIfaces[i] = iface;
        }
        for (DFKlass iface : _baseIfaces) {
            iface.load();
        }
        // Get constants.
        for (EnumConstantDeclaration econst :
                 (List<EnumConstantDeclaration>) enumDecl.enumConstants()) {
            this.addField(econst.getName(), true, this);
        }
        // Enum has a special method "values()".
        DFMethod method = new DFMethod(
            this, DFMethod.CallStyle.InstanceMethod, false,
            "values", "values", null);
        method.setFuncType(
            new DFFunctionType(new DFType[] {}, DFArrayType.getType(this, 1)));
        this.addMethod(method, null);
        this.buildMembers(finder, enumDecl.bodyDeclarations());
    }

    @SuppressWarnings("unchecked")
    private void buildMembersFromAnnotTypeDecl(
        DFTypeFinder finder, AnnotationTypeDeclaration annotTypeDecl)
        throws InvalidSyntax {
        this.buildMembers(finder, annotTypeDecl.bodyDeclarations());
    }

    @SuppressWarnings("unchecked")
    private void buildMembers(DFTypeFinder finder, List<BodyDeclaration> decls)
        throws InvalidSyntax {
        if (_initMethod != null) {
            _initMethod.setBaseFinder(finder);
            _initMethod.buildFuncType(this);
        }

        for (BodyDeclaration body : decls) {
            if (body instanceof AbstractTypeDeclaration) {
                // Child klasses are loaded independently.

            } else if (body instanceof FieldDeclaration) {
                FieldDeclaration decl = (FieldDeclaration)body;
                DFType fldType = finder.resolveSafe(decl.getType());
                for (VariableDeclarationFragment frag :
                         (List<VariableDeclarationFragment>) decl.fragments()) {
                    DFType ft = fldType;
                    int ndims = frag.getExtraDimensions();
                    if (ndims != 0) {
                        ft = DFArrayType.getType(ft, ndims);
                    }
                    this.addField(frag.getName(), isStatic(decl), ft);
                }

            } else if (body instanceof MethodDeclaration) {
                MethodDeclaration decl = (MethodDeclaration)body;
                String id = Utils.encodeASTNode(decl);
                DFMethod method = this.getMethod(id);
                method.setBaseFinder(finder);
                method.buildFuncType(this);

            } else if (body instanceof EnumConstantDeclaration) {

            } else if (body instanceof AnnotationTypeMemberDeclaration) {
                AnnotationTypeMemberDeclaration decl =
                    (AnnotationTypeMemberDeclaration)body;
                DFType type = finder.resolveSafe(decl.getType());
                this.addField(decl.getName(), isStatic(decl), type);

            } else if (body instanceof Initializer) {

            } else {
                throw new InvalidSyntax(body);
            }
        }
    }

    public void loadKlasses(Set<DFSourceKlass> klasses)
        throws InvalidSyntax {
        if (_ast == null) return;
        if (klasses.contains(this)) return;
        if (this.isGeneric()) return;
        this.load();
        //Logger.info("loadKlasses:", this);
        klasses.add(this);
        DFTypeFinder finder = this.getFinder();
        List<DFSourceKlass> toLoad = new ArrayList<DFSourceKlass>();
        this.loadKlassesDecl(finder, _ast, klasses);
    }

    @SuppressWarnings("unchecked")
    private void loadKlassesDecl(
        DFTypeFinder finder, ASTNode ast, Set<DFSourceKlass> klasses)
        throws InvalidSyntax {
        if (ast instanceof AbstractTypeDeclaration) {
            AbstractTypeDeclaration abstDecl = (AbstractTypeDeclaration)ast;
            this.loadKlassesDecls(
                finder, abstDecl.bodyDeclarations(), klasses);

        } else if (ast instanceof ClassInstanceCreation) {
            ClassInstanceCreation cstr = (ClassInstanceCreation)ast;
            this.loadKlassesDecls(
                finder,
                cstr.getAnonymousClassDeclaration().bodyDeclarations(), klasses);

        } else if (ast instanceof LambdaExpression) {
            LambdaExpression lambda = (LambdaExpression)ast;
            DFMethod method = ((DFLambdaKlass)this).getFuncMethod();
            ASTNode body = lambda.getBody();
            if (body instanceof Statement) {
                this.loadKlassesStmt(
                    finder, method, (Statement)body, klasses);
            } else if (body instanceof Expression) {
                this.loadKlassesExpr(
                    finder, method, (Expression)body, klasses);
            } else {
                throw new InvalidSyntax(body);
            }

        } else if (ast instanceof CreationReference) {
            DFType type = finder.resolveSafe(
                ((CreationReference)ast).getType());
            if (type instanceof DFSourceKlass) {
                ((DFSourceKlass)type).loadKlasses(klasses);
            }

        } else if (ast instanceof SuperMethodReference) {
            try {
                DFType type = finder.lookupType(
                    ((SuperMethodReference)ast).getQualifier());
                if (type instanceof DFSourceKlass) {
                    ((DFSourceKlass)type).loadKlasses(klasses);
                }
            } catch (TypeNotFound e) {
            }

        } else if (ast instanceof TypeMethodReference) {
            DFType type = finder.resolveSafe(
                ((TypeMethodReference)ast).getType());
            if (type instanceof DFSourceKlass) {
                ((DFSourceKlass)type).loadKlasses(klasses);
            }

        } else if (ast instanceof ExpressionMethodReference) {
            // XXX ignored mref.typeArguments() for method refs.
            this.loadKlassesExpr(
                finder, this,
                ((ExpressionMethodReference)ast).getExpression(),
                klasses);

        } else {
            throw new InvalidSyntax(ast);
        }
    }

    @SuppressWarnings("unchecked")
    private void loadKlassesDecls(
        DFTypeFinder finder,
        List<BodyDeclaration> decls, Set<DFSourceKlass> klasses)
        throws InvalidSyntax {

        DFMethod initMethod = this.getInitMethod();
        assert initMethod != null;
        for (BodyDeclaration body : decls) {
            if (body instanceof AbstractTypeDeclaration) {
                AbstractTypeDeclaration decl = (AbstractTypeDeclaration)body;
                DFKlass innerType = this.getKlass(decl.getName());
                if (innerType instanceof DFSourceKlass) {
                    ((DFSourceKlass)innerType).loadKlasses(klasses);
                }

            } else if (body instanceof FieldDeclaration) {
                FieldDeclaration decl = (FieldDeclaration)body;
                DFType fldType = finder.resolveSafe(decl.getType());
                if (fldType instanceof DFSourceKlass) {
                    ((DFSourceKlass)fldType).loadKlasses(klasses);
                }
                for (VariableDeclarationFragment frag :
                         (List<VariableDeclarationFragment>) decl.fragments()) {
                    Expression expr = frag.getInitializer();
                    if (expr != null) {
                        this.loadKlassesExpr(finder, initMethod, expr, klasses);
                    }
                }

            } else if (body instanceof MethodDeclaration) {
                MethodDeclaration decl = (MethodDeclaration)body;
                String id = Utils.encodeASTNode(decl);
                DFMethod method = this.getMethod(id);
                DFTypeFinder finder2 = method.getFinder();
                List<SingleVariableDeclaration> varDecls = decl.parameters();
                for (SingleVariableDeclaration varDecl : varDecls) {
                    DFType argType = finder2.resolveSafe(varDecl.getType());
                    if (argType instanceof DFSourceKlass) {
                        ((DFSourceKlass)argType).loadKlasses(klasses);
                    }
                }
                if (!decl.isConstructor()) {
                    DFType returnType = finder2.resolveSafe(decl.getReturnType2());
                    if (returnType instanceof DFSourceKlass) {
                        ((DFSourceKlass)returnType).loadKlasses(klasses);
                    }
                }
                if (decl.getBody() != null) {
                    this.loadKlassesStmt(
                        finder2, method, decl.getBody(), klasses);
                }

            } else if (body instanceof EnumConstantDeclaration) {

            } else if (body instanceof AnnotationTypeMemberDeclaration) {
                AnnotationTypeMemberDeclaration decl =
                    (AnnotationTypeMemberDeclaration)body;
                DFType type = finder.resolveSafe(decl.getType());
                if (type instanceof DFSourceKlass) {
                    ((DFSourceKlass)type).loadKlasses(klasses);
                }

            } else if (body instanceof Initializer) {
                Initializer initializer = (Initializer)body;
                this.loadKlassesStmt(
                    finder, initMethod, initializer.getBody(), klasses);

            } else {
                throw new InvalidSyntax(body);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void loadKlassesStmt(
        DFTypeFinder finder, DFTypeSpace space,
        Statement ast, Set<DFSourceKlass> klasses)
        throws InvalidSyntax {
        assert ast != null;

        if (ast instanceof AssertStatement) {

        } else if (ast instanceof Block) {
            Block block = (Block)ast;
            for (Statement stmt :
                     (List<Statement>) block.statements()) {
                this.loadKlassesStmt(finder, space, stmt, klasses);
            }

        } else if (ast instanceof EmptyStatement) {

        } else if (ast instanceof VariableDeclarationStatement) {
            VariableDeclarationStatement varStmt =
                (VariableDeclarationStatement)ast;
            DFType varType = finder.resolveSafe(varStmt.getType());
            if (varType instanceof DFSourceKlass) {
                ((DFSourceKlass)varType).loadKlasses(klasses);
            }
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>) varStmt.fragments()) {
                Expression expr = frag.getInitializer();
                if (expr != null) {
                    this.loadKlassesExpr(finder, space, expr, klasses);
                }
            }

        } else if (ast instanceof ExpressionStatement) {
            ExpressionStatement exprStmt = (ExpressionStatement)ast;
            Expression expr = exprStmt.getExpression();
            this.loadKlassesExpr(finder, space, expr, klasses);

        } else if (ast instanceof ReturnStatement) {
            ReturnStatement returnStmt = (ReturnStatement)ast;
            Expression expr = returnStmt.getExpression();
            if (expr != null) {
                this.loadKlassesExpr(finder, space, expr, klasses);
            }

        } else if (ast instanceof IfStatement) {
            IfStatement ifStmt = (IfStatement)ast;
            Expression expr = ifStmt.getExpression();
            this.loadKlassesExpr(finder, space, expr, klasses);
            Statement thenStmt = ifStmt.getThenStatement();
            this.loadKlassesStmt(finder, space, thenStmt, klasses);
            Statement elseStmt = ifStmt.getElseStatement();
            if (elseStmt != null) {
                this.loadKlassesStmt(finder, space, elseStmt, klasses);
            }

        } else if (ast instanceof SwitchStatement) {
            SwitchStatement switchStmt = (SwitchStatement)ast;
            Expression expr = switchStmt.getExpression();
            this.loadKlassesExpr(finder, space, expr, klasses);
            for (Statement stmt :
                     (List<Statement>) switchStmt.statements()) {
                this.loadKlassesStmt(finder, space, stmt, klasses);
            }

        } else if (ast instanceof SwitchCase) {
            SwitchCase switchCase = (SwitchCase)ast;
            Expression expr = switchCase.getExpression();
            if (expr != null) {
                this.loadKlassesExpr(finder, space, expr, klasses);
            }

        } else if (ast instanceof WhileStatement) {
            WhileStatement whileStmt = (WhileStatement)ast;
            Expression expr = whileStmt.getExpression();
            this.loadKlassesExpr(finder, space, expr, klasses);
            Statement stmt = whileStmt.getBody();
            this.loadKlassesStmt(finder, space, stmt, klasses);

        } else if (ast instanceof DoStatement) {
            DoStatement doStmt = (DoStatement)ast;
            Statement stmt = doStmt.getBody();
            this.loadKlassesStmt(finder, space, stmt, klasses);
            Expression expr = doStmt.getExpression();
            this.loadKlassesExpr(finder, space, expr, klasses);

        } else if (ast instanceof ForStatement) {
            ForStatement forStmt = (ForStatement)ast;
            for (Expression init :
                     (List<Expression>) forStmt.initializers()) {
                this.loadKlassesExpr(finder, space, init, klasses);
            }
            Expression expr = forStmt.getExpression();
            if (expr != null) {
                this.loadKlassesExpr(finder, space, expr, klasses);
            }
            Statement stmt = forStmt.getBody();
            this.loadKlassesStmt(finder, space, stmt, klasses);
            for (Expression update :
                     (List<Expression>) forStmt.updaters()) {
                this.loadKlassesExpr(finder, space, update, klasses);
            }

        } else if (ast instanceof EnhancedForStatement) {
            EnhancedForStatement eForStmt = (EnhancedForStatement)ast;
            this.loadKlassesExpr(finder, space, eForStmt.getExpression(), klasses);
            SingleVariableDeclaration decl = eForStmt.getParameter();
            DFType varType = finder.resolveSafe(decl.getType());
            if (varType instanceof DFSourceKlass) {
                ((DFSourceKlass)varType).loadKlasses(klasses);
            }
            Statement stmt = eForStmt.getBody();
            this.loadKlassesStmt(finder, space, stmt, klasses);

        } else if (ast instanceof BreakStatement) {

        } else if (ast instanceof ContinueStatement) {

        } else if (ast instanceof LabeledStatement) {
            LabeledStatement labeledStmt = (LabeledStatement)ast;
            Statement stmt = labeledStmt.getBody();
            this.loadKlassesStmt(finder, space, stmt, klasses);

        } else if (ast instanceof SynchronizedStatement) {
            SynchronizedStatement syncStmt = (SynchronizedStatement)ast;
            this.loadKlassesExpr(finder, space, syncStmt.getExpression(), klasses);
            this.loadKlassesStmt(finder, space, syncStmt.getBody(), klasses);

        } else if (ast instanceof TryStatement) {
            TryStatement tryStmt = (TryStatement)ast;
            for (VariableDeclarationExpression decl :
                     (List<VariableDeclarationExpression>) tryStmt.resources()) {
                this.loadKlassesExpr(finder, space, decl, klasses);
            }
            this.loadKlassesStmt(finder, space, tryStmt.getBody(), klasses);
            for (CatchClause cc :
                     (List<CatchClause>) tryStmt.catchClauses()) {
                SingleVariableDeclaration decl = cc.getException();
                DFType varType = finder.resolveSafe(decl.getType());
                if (varType instanceof DFSourceKlass) {
                    ((DFSourceKlass)varType).loadKlasses(klasses);
                }
                this.loadKlassesStmt(finder, space, cc.getBody(), klasses);
            }
            Block finBlock = tryStmt.getFinally();
            if (finBlock != null) {
                this.loadKlassesStmt(finder, space, finBlock, klasses);
            }

        } else if (ast instanceof ThrowStatement) {
            ThrowStatement throwStmt = (ThrowStatement)ast;
            Expression expr = throwStmt.getExpression();
            if (expr != null) {
                this.loadKlassesExpr(finder, space, expr, klasses);
            }

        } else if (ast instanceof ConstructorInvocation) {
            ConstructorInvocation ci = (ConstructorInvocation)ast;
            for (Expression expr :
                     (List<Expression>) ci.arguments()) {
                this.loadKlassesExpr(finder, space, expr, klasses);
            }

        } else if (ast instanceof SuperConstructorInvocation) {
            SuperConstructorInvocation sci = (SuperConstructorInvocation)ast;
            for (Expression expr :
                     (List<Expression>) sci.arguments()) {
                this.loadKlassesExpr(finder, space, expr, klasses);
            }

        } else if (ast instanceof TypeDeclarationStatement) {
            TypeDeclarationStatement decl = (TypeDeclarationStatement)ast;
            AbstractTypeDeclaration abstDecl = decl.getDeclaration();
            DFKlass innerType = space.getKlass(abstDecl.getName());
            if (innerType instanceof DFSourceKlass) {
                ((DFSourceKlass)innerType).loadKlasses(klasses);
            }

        } else {
            throw new InvalidSyntax(ast);

        }
    }

    @SuppressWarnings("unchecked")
    private void loadKlassesExpr(
        DFTypeFinder finder, DFTypeSpace space,
        Expression ast, Set<DFSourceKlass> klasses)
        throws InvalidSyntax {
        assert ast != null;

        if (ast instanceof Annotation) {

        } else if (ast instanceof Name) {

        } else if (ast instanceof ThisExpression) {
            // "this"
            ThisExpression thisExpr = (ThisExpression)ast;
            Name name = thisExpr.getQualifier();
            if (name != null) {
                try {
                    DFType type = finder.lookupType(name);
                    if (type instanceof DFSourceKlass) {
                        ((DFSourceKlass)type).loadKlasses(klasses);
                    }
                } catch (TypeNotFound e) {
                }
            }

        } else if (ast instanceof BooleanLiteral) {

        } else if (ast instanceof CharacterLiteral) {

        } else if (ast instanceof NullLiteral) {

        } else if (ast instanceof NumberLiteral) {

        } else if (ast instanceof StringLiteral) {

        } else if (ast instanceof TypeLiteral) {
            Type value = ((TypeLiteral)ast).getType();
            try {
                DFType type = finder.resolve(value);
                if (type instanceof DFSourceKlass) {
                    ((DFSourceKlass)type).loadKlasses(klasses);
                }
            } catch (TypeNotFound e) {
            }

        } else if (ast instanceof PrefixExpression) {
            PrefixExpression prefix = (PrefixExpression)ast;
            PrefixExpression.Operator op = prefix.getOperator();
            Expression operand = prefix.getOperand();
            this.loadKlassesExpr(finder, space, operand, klasses);
            if (op == PrefixExpression.Operator.INCREMENT ||
                op == PrefixExpression.Operator.DECREMENT) {
                this.loadKlassesExpr(finder, space, operand, klasses);
            }

        } else if (ast instanceof PostfixExpression) {
            PostfixExpression postfix = (PostfixExpression)ast;
            PostfixExpression.Operator op = postfix.getOperator();
            Expression operand = postfix.getOperand();
            this.loadKlassesExpr(finder, space, operand, klasses);
            if (op == PostfixExpression.Operator.INCREMENT ||
                op == PostfixExpression.Operator.DECREMENT) {
                this.loadKlassesExpr(finder, space, operand, klasses);
            }

        } else if (ast instanceof InfixExpression) {
            InfixExpression infix = (InfixExpression)ast;
            InfixExpression.Operator op = infix.getOperator();
            Expression loperand = infix.getLeftOperand();
            this.loadKlassesExpr(finder, space, loperand, klasses);
            Expression roperand = infix.getRightOperand();
            this.loadKlassesExpr(finder, space, roperand, klasses);

        } else if (ast instanceof ParenthesizedExpression) {
            ParenthesizedExpression paren = (ParenthesizedExpression)ast;
            this.loadKlassesExpr(finder, space, paren.getExpression(), klasses);

        } else if (ast instanceof Assignment) {
            Assignment assn = (Assignment)ast;
            Assignment.Operator op = assn.getOperator();
            this.loadKlassesExpr(finder, space, assn.getLeftHandSide(), klasses);
            if (op != Assignment.Operator.ASSIGN) {
                this.loadKlassesExpr(finder, space, assn.getLeftHandSide(), klasses);
            }
            this.loadKlassesExpr(finder, space, assn.getRightHandSide(), klasses);

        } else if (ast instanceof VariableDeclarationExpression) {
            VariableDeclarationExpression decl = (VariableDeclarationExpression)ast;
            DFType varType = finder.resolveSafe(decl.getType());
            if (varType instanceof DFSourceKlass) {
                ((DFSourceKlass)varType).loadKlasses(klasses);
            }
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>) decl.fragments()) {
                Expression expr = frag.getInitializer();
                if (expr != null) {
                    this.loadKlassesExpr(finder, space, expr, klasses);
                }
            }

        } else if (ast instanceof MethodInvocation) {
            MethodInvocation invoke = (MethodInvocation)ast;
            Expression expr = invoke.getExpression();
            if (expr instanceof Name) {
                try {
                    DFType type = finder.lookupType((Name)expr);
                    if (type instanceof DFSourceKlass) {
                        ((DFSourceKlass)type).loadKlasses(klasses);
                    }
                } catch (TypeNotFound e) {
                }
            } else if (expr != null) {
                this.loadKlassesExpr(finder, space, expr, klasses);
            }
            for (Expression arg :
                     (List<Expression>) invoke.arguments()) {
                this.loadKlassesExpr(finder, space, arg, klasses);
            }

        } else if (ast instanceof SuperMethodInvocation) {
            SuperMethodInvocation si = (SuperMethodInvocation)ast;
            for (Expression arg :
                     (List<Expression>) si.arguments()) {
                this.loadKlassesExpr(finder, space, arg, klasses);
            }

        } else if (ast instanceof ArrayCreation) {
            ArrayCreation ac = (ArrayCreation)ast;
            for (Expression dim :
                     (List<Expression>) ac.dimensions()) {
                this.loadKlassesExpr(finder, space, dim, klasses);
            }
            ArrayInitializer init = ac.getInitializer();
            if (init != null) {
                this.loadKlassesExpr(finder, space, init, klasses);
            }
            DFType type = finder.resolveSafe(ac.getType().getElementType());
            if (type instanceof DFSourceKlass) {
                ((DFSourceKlass)type).loadKlasses(klasses);
            }

        } else if (ast instanceof ArrayInitializer) {
            ArrayInitializer init = (ArrayInitializer)ast;
            for (Expression expr :
                     (List<Expression>) init.expressions()) {
                this.loadKlassesExpr(finder, space, expr, klasses);
            }

        } else if (ast instanceof ArrayAccess) {
            ArrayAccess aa = (ArrayAccess)ast;
            this.loadKlassesExpr(finder, space, aa.getArray(), klasses);
            this.loadKlassesExpr(finder, space, aa.getIndex(), klasses);

        } else if (ast instanceof FieldAccess) {
            FieldAccess fa = (FieldAccess)ast;
            SimpleName fieldName = fa.getName();
            this.loadKlassesExpr(finder, space, fa.getExpression(), klasses);

        } else if (ast instanceof SuperFieldAccess) {
            SuperFieldAccess sfa = (SuperFieldAccess)ast;
            SimpleName fieldName = sfa.getName();

        } else if (ast instanceof CastExpression) {
            CastExpression cast = (CastExpression)ast;
            this.loadKlassesExpr(finder, space, cast.getExpression(), klasses);
            DFType type = finder.resolveSafe(cast.getType());
            if (type instanceof DFSourceKlass) {
                ((DFSourceKlass)type).loadKlasses(klasses);
            }

        } else if (ast instanceof ClassInstanceCreation) {
            ClassInstanceCreation cstr = (ClassInstanceCreation)ast;
            try {
                DFKlass instKlass;
                if (cstr.getAnonymousClassDeclaration() != null) {
                    String id = Utils.encodeASTNode(cstr);
                    instKlass = space.getKlass(id);
                } else {
                    instKlass = finder.resolve(cstr.getType()).toKlass();
                }
                if (instKlass instanceof DFSourceKlass) {
                    ((DFSourceKlass)instKlass).loadKlasses(klasses);
                }
            } catch (TypeNotFound e) {
            }
            Expression expr = cstr.getExpression();
            if (expr != null) {
                this.loadKlassesExpr(finder, space, expr, klasses);
            }
            for (Expression arg :
                     (List<Expression>) cstr.arguments()) {
                this.loadKlassesExpr(finder, space, arg, klasses);
            }

        } else if (ast instanceof ConditionalExpression) {
            ConditionalExpression cond = (ConditionalExpression)ast;
            this.loadKlassesExpr(finder, space, cond.getExpression(), klasses);
            this.loadKlassesExpr(finder, space, cond.getThenExpression(), klasses);
            this.loadKlassesExpr(finder, space, cond.getElseExpression(), klasses);

        } else if (ast instanceof InstanceofExpression) {
            InstanceofExpression instof = (InstanceofExpression)ast;
            this.loadKlassesExpr(finder, space, instof.getLeftOperand(), klasses);

        } else if (ast instanceof LambdaExpression) {
            LambdaExpression lambda = (LambdaExpression)ast;
            String id = Utils.encodeASTNode(lambda);
            DFSourceKlass lambdaKlass = (DFSourceKlass)space.getKlass(id);
            // Do not use lambda klasses until defined.

        } else if (ast instanceof MethodReference) {
            //  CreationReference
            //  ExpressionMethodReference
            //  SuperMethodReference
            //  TypeMethodReference
            MethodReference methodref = (MethodReference)ast;
            String id = Utils.encodeASTNode(methodref);
            DFSourceKlass methodRefKlass = (DFSourceKlass)space.getKlass(id);
            // Do not use methodref klasses until defined.

        } else {
            throw new InvalidSyntax(ast);
        }
    }
}
