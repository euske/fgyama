//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.apache.bcel.*;
import org.apache.bcel.classfile.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;
import org.w3c.dom.*;


//  DFTypeSpace
//
public class DFTypeSpace {

    private DFTypeSpace _parent;
    private String _name;

    private List<DFTypeSpace> _children =
        new ArrayList<DFTypeSpace>();
    private Map<String, DFTypeSpace> _id2space =
        new HashMap<String, DFTypeSpace>();
    private Map<String, DFKlass> _id2klass =
        new HashMap<String, DFKlass>();

    public DFTypeSpace(DFTypeSpace parent, String name) {
        _parent = parent;
        _name = name;
    }

    @Override
    public String toString() {
        return ("<DFTypeSpace("+this.getFullName()+")>");
    }

    public String getFullName() {
        if (_parent == null) {
            return "";
        } else {
            return _parent.getFullName()+_name+"/";
        }
    }

    public String getAnonName() {
        return "anon"+_children.size(); // assign unique id.
    }

    public DFTypeSpace lookupSpace(PackageDeclaration pkgDecl) {
        if (pkgDecl == null) {
            return this;
        } else {
            return this.lookupSpace(pkgDecl.getName());
        }
    }

    public DFTypeSpace lookupSpace(Name name) {
        return this.lookupSpace(name.getFullyQualifiedName());
    }

    public DFTypeSpace lookupSpace(String id) {
        int i = id.lastIndexOf('.');
        if (0 <= i) {
            DFTypeSpace space = this.lookupSpace(id.substring(0, i));
            return space.lookupSpace(id.substring(i+1));
        }
        DFKlass klass = _id2klass.get(id);
        if (klass != null) {
            return klass.getKlassSpace();
        }
        DFTypeSpace space = _id2space.get(id);
        if (space == null) {
            space = new DFTypeSpace(this, id);
            _children.add(space);
            _id2space.put(id, space);
            //Logger.info("DFTypeSpace.addChild: "+this+": "+id);
        }
        return space;
    }

    protected DFKlass createKlass(
        DFKlass parentKlass, DFVarScope parentScope,
        AbstractTypeDeclaration abstDecl) {
        SimpleName name = abstDecl.getName();
        DFKlass klass = this.createKlass(
            parentKlass, parentScope, name.getIdentifier());
        klass.setTree(abstDecl);
        return klass;
    }

    protected DFKlass createKlass(
        DFKlass parentKlass, DFVarScope parentScope, String id) {
        assert id.indexOf('.') < 0;
        DFKlass klass = _id2klass.get(id);
        if (klass != null) return klass;
        klass = new DFKlass(
            id, this, parentKlass, parentScope,
            DFBuiltinTypes.getObjectKlass());
        //Logger.info("DFTypeSpace.createKlass: "+klass);
        return this.addKlass(klass);
    }

    public DFParamType createParamType(String id) {
        DFParamType pt = new DFParamType(id, this);
        this.addKlass(pt);
        return pt;
    }

    public DFKlass addKlass(DFKlass klass) {
        String id = klass.getKlassName();
        assert id.indexOf('.') < 0;
        //assert !_id2klass.containsKey(id);
        _id2klass.put(id, klass);
        //Logger.info("DFTypeSpace.addKlass: "+this+": "+id);
        return klass;
    }

    public DFKlass getKlass(Name name)
        throws TypeNotFound {
        return this.getKlass(name.getFullyQualifiedName());
    }
    public DFKlass getKlass(String id)
        throws TypeNotFound {
        //Logger.info("DFTypeSpace.getKlass: "+this+": "+id);
        int i = id.lastIndexOf('.');
        if (0 <= i) {
            DFTypeSpace space = this.lookupSpace(id.substring(0, i));
            return space.getKlass(id.substring(i+1));
        }
        DFKlass klass = _id2klass.get(id);
        if (klass == null) {
            throw new TypeNotFound(this.getFullName()+id);
        }
        return klass;
    }

    @SuppressWarnings("unchecked")
    public DFKlass[] buildModuleSpace(
        CompilationUnit cunit, DFVarScope scope)
        throws UnsupportedSyntax {
        List<DFKlass> list = new ArrayList<DFKlass>();
        for (AbstractTypeDeclaration abstTypeDecl :
                 (List<AbstractTypeDeclaration>) cunit.types()) {
            this.build(list, abstTypeDecl, null, scope);
        }
        DFKlass[] klasses = new DFKlass[list.size()];
        list.toArray(klasses);
        return klasses;
    }

    private void build(
        List<DFKlass> list, AbstractTypeDeclaration abstTypeDecl,
        DFKlass parentKlass, DFVarScope parentScope)
        throws UnsupportedSyntax {
        assert abstTypeDecl != null;
        if (abstTypeDecl instanceof TypeDeclaration) {
            this.build(list, (TypeDeclaration)abstTypeDecl,
                       parentKlass, parentScope);
        } else if (abstTypeDecl instanceof EnumDeclaration) {
            this.build(list, (EnumDeclaration)abstTypeDecl,
                       parentKlass, parentScope);
        } else if (abstTypeDecl instanceof AnnotationTypeDeclaration) {
            this.build(list, (AnnotationTypeDeclaration)abstTypeDecl,
                       parentKlass, parentScope);
        } else {
            throw new UnsupportedSyntax(abstTypeDecl);
        }
    }

    @SuppressWarnings("unchecked")
    private void build(
        List<DFKlass> list, TypeDeclaration typeDecl,
        DFKlass parentKlass, DFVarScope parentScope)
        throws UnsupportedSyntax {
        //Logger.info("DFTypeSpace.build: "+this+": "+typeDecl.getName());
        DFKlass klass = this.createKlass(
            parentKlass, parentScope, typeDecl);
        list.add(klass);
        klass.addParamTypes(typeDecl.typeParameters());
        DFTypeSpace child = klass.getKlassSpace();
	child.build(list, klass, klass.getKlassScope(),
		    typeDecl.bodyDeclarations());
    }

    @SuppressWarnings("unchecked")
    private void build(
        List<DFKlass> list, EnumDeclaration enumDecl,
        DFKlass parentKlass, DFVarScope parentScope)
        throws UnsupportedSyntax {
        //Logger.info("DFTypeSpace.build: "+this+": "+enumDecl.getName());
        DFKlass klass = this.createKlass(
            parentKlass, parentScope, enumDecl);
        list.add(klass);
        DFTypeSpace child = klass.getKlassSpace();
	child.build(list, klass, klass.getKlassScope(),
		    enumDecl.bodyDeclarations());
    }

    @SuppressWarnings("unchecked")
    private void build(
        List<DFKlass> list, AnnotationTypeDeclaration annotTypeDecl,
        DFKlass parentKlass, DFVarScope parentScope)
        throws UnsupportedSyntax {
        //Logger.info("DFTypeSpace.build: "+this+": "+annotTypeDecl.getName());
        DFKlass klass = this.createKlass(
            parentKlass, parentScope, annotTypeDecl);
        list.add(klass);
        DFTypeSpace child = klass.getKlassSpace();
	child.build(list, klass, klass.getKlassScope(),
		    annotTypeDecl.bodyDeclarations());
    }

    @SuppressWarnings("unchecked")
    private void build(
        List<DFKlass> list, DFKlass parentKlass, DFVarScope parentScope,
	List<BodyDeclaration> decls)
        throws UnsupportedSyntax {
        for (BodyDeclaration body : decls) {
	    if (body instanceof AbstractTypeDeclaration) {
		this.build(list, (AbstractTypeDeclaration)body,
			   parentKlass, parentScope);
	    } else if (body instanceof FieldDeclaration) {
                FieldDeclaration fieldDecl = (FieldDeclaration)body;
                for (VariableDeclarationFragment frag :
                         (List<VariableDeclarationFragment>) fieldDecl.fragments()) {
                    Expression init = frag.getInitializer();
                    if (init != null) {
                        this.build(list, init, parentKlass, parentScope);
                    }
                }
	    } else if (body instanceof MethodDeclaration) {
                MethodDeclaration methodDecl = (MethodDeclaration)body;
                Statement stmt = methodDecl.getBody();
                if (stmt != null) {
                    String id = Utils.encodeASTNode(methodDecl);
                    DFTypeSpace methodSpace = this.lookupSpace(id);
                    DFLocalVarScope scope = new DFLocalVarScope(
                        parentScope, methodDecl.getName());
                    parentKlass.addMethodScope(methodDecl, scope);
                    methodSpace.build(list, stmt, parentKlass, scope);
                }
	    } else if (body instanceof AnnotationTypeMemberDeclaration) {
		;
	    } else if (body instanceof Initializer) {
		Initializer initializer = (Initializer)body;
                DFLocalVarScope scope = new DFLocalVarScope(
                    parentScope, "<clinit>");
                parentKlass.addMethodScope(initializer, scope);
                this.build(list, initializer.getBody(), parentKlass, scope);

	    } else {
		throw new UnsupportedSyntax(body);
	    }
	}
    }

    @SuppressWarnings("unchecked")
    private void build(
        List<DFKlass> list, Statement ast,
        DFKlass parentKlass, DFLocalVarScope parentScope)
        throws UnsupportedSyntax {
        assert ast != null;

        if (ast instanceof AssertStatement) {
            // XXX Ignore asserts.

        } else if (ast instanceof Block) {
            Block block = (Block)ast;
            DFLocalVarScope childScope = parentScope.addChild("b", ast);
            for (Statement stmt :
                     (List<Statement>) block.statements()) {
                this.build(list, stmt, parentKlass, childScope);
            }

        } else if (ast instanceof EmptyStatement) {

        } else if (ast instanceof VariableDeclarationStatement) {
            VariableDeclarationStatement varStmt =
                (VariableDeclarationStatement)ast;
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>) varStmt.fragments()) {
                Expression expr = frag.getInitializer();
                if (expr != null) {
                    this.build(list, expr, parentKlass, parentScope);
                }
            }

        } else if (ast instanceof ExpressionStatement) {
            ExpressionStatement exprStmt = (ExpressionStatement)ast;
            this.build(list, exprStmt.getExpression(), parentKlass, parentScope);

        } else if (ast instanceof ReturnStatement) {
            ReturnStatement returnStmt = (ReturnStatement)ast;
            Expression expr = returnStmt.getExpression();
            if (expr != null) {
                this.build(list, expr, parentKlass, parentScope);
            }

        } else if (ast instanceof IfStatement) {
            IfStatement ifStmt = (IfStatement)ast;
            this.build(list, ifStmt.getExpression(), parentKlass, parentScope);
            Statement thenStmt = ifStmt.getThenStatement();
            this.build(list, thenStmt, parentKlass, parentScope);
            Statement elseStmt = ifStmt.getElseStatement();
            if (elseStmt != null) {
                this.build(list, elseStmt, parentKlass, parentScope);
            }

        } else if (ast instanceof SwitchStatement) {
            SwitchStatement switchStmt = (SwitchStatement)ast;
            DFLocalVarScope childScope = parentScope.addChild("switch", ast);
            this.build(list, switchStmt.getExpression(), parentKlass, childScope);
            for (Statement stmt :
                     (List<Statement>) switchStmt.statements()) {
                this.build(list, stmt, parentKlass, childScope);
            }

        } else if (ast instanceof SwitchCase) {
            SwitchCase switchCase = (SwitchCase)ast;
            Expression expr = switchCase.getExpression();
            if (expr != null) {
                this.build(list, expr, parentKlass, parentScope);
            }

        } else if (ast instanceof WhileStatement) {
            WhileStatement whileStmt = (WhileStatement)ast;
            this.build(list, whileStmt.getExpression(), parentKlass, parentScope);
            DFLocalVarScope childScope = parentScope.addChild("while", ast);
            Statement stmt = whileStmt.getBody();
            this.build(list, stmt, parentKlass, childScope);

        } else if (ast instanceof DoStatement) {
            DoStatement doStmt = (DoStatement)ast;
            DFLocalVarScope childScope = parentScope.addChild("do", ast);
            Statement stmt = doStmt.getBody();
            this.build(list, stmt, parentKlass, childScope);
            this.build(list, doStmt.getExpression(), parentKlass, childScope);

        } else if (ast instanceof ForStatement) {
            ForStatement forStmt = (ForStatement)ast;
            DFLocalVarScope childScope = parentScope.addChild("for", ast);
            for (Expression init :
                     (List<Expression>) forStmt.initializers()) {
                this.build(list, init, parentKlass, childScope);
            }
            Expression expr = forStmt.getExpression();
            if (expr != null) {
                this.build(list, expr, parentKlass, childScope);
            }
            Statement stmt = forStmt.getBody();
            this.build(list, stmt, parentKlass, childScope);
            for (Expression update :
                     (List<Expression>) forStmt.updaters()) {
                this.build(list, update, parentKlass, childScope);
            }

        } else if (ast instanceof EnhancedForStatement) {
            EnhancedForStatement eForStmt = (EnhancedForStatement)ast;
            this.build(list, eForStmt.getExpression(), parentKlass, parentScope);
            DFLocalVarScope childScope = parentScope.addChild("efor", ast);
            this.build(list, eForStmt.getBody(), parentKlass, childScope);

        } else if (ast instanceof BreakStatement) {

        } else if (ast instanceof ContinueStatement) {

        } else if (ast instanceof LabeledStatement) {
            LabeledStatement labeledStmt = (LabeledStatement)ast;
            Statement stmt = labeledStmt.getBody();
            this.build(list, stmt, parentKlass, parentScope);

        } else if (ast instanceof SynchronizedStatement) {
            SynchronizedStatement syncStmt = (SynchronizedStatement)ast;
            Block block = syncStmt.getBody();
            this.build(list, block, parentKlass, parentScope);

        } else if (ast instanceof TryStatement) {
            TryStatement tryStmt = (TryStatement)ast;
            DFLocalVarScope childScope = parentScope.addChild("try", ast);
            for (VariableDeclarationExpression decl :
                     (List<VariableDeclarationExpression>) tryStmt.resources()) {
                this.build(list, decl, parentKlass, childScope);
            }
            this.build(list, tryStmt.getBody(), parentKlass, childScope);
            for (CatchClause cc :
                     (List<CatchClause>) tryStmt.catchClauses()) {
                DFLocalVarScope catchScope = parentScope.addChild("catch", cc);
                this.build(list, cc.getBody(), parentKlass, catchScope);
            }
            Block finBlock = tryStmt.getFinally();
            if (finBlock != null) {
                this.build(list, finBlock, parentKlass, parentScope);
            }

        } else if (ast instanceof ThrowStatement) {
            ThrowStatement throwStmt = (ThrowStatement)ast;
            Expression expr = throwStmt.getExpression();
            if (expr != null) {
                this.build(list, expr, parentKlass, parentScope);
            }

        } else if (ast instanceof ConstructorInvocation) {
            ConstructorInvocation ci = (ConstructorInvocation)ast;
            for (Expression expr :
                     (List<Expression>) ci.arguments()) {
                this.build(list, expr, parentKlass, parentScope);
            }

        } else if (ast instanceof SuperConstructorInvocation) {
            SuperConstructorInvocation sci = (SuperConstructorInvocation)ast;
            for (Expression expr :
                     (List<Expression>) sci.arguments()) {
                this.build(list, expr, parentKlass, parentScope);
            }

        } else if (ast instanceof TypeDeclarationStatement) {
            TypeDeclarationStatement typeDeclStmt = (TypeDeclarationStatement)ast;
            this.build(list, typeDeclStmt.getDeclaration(),
                       parentKlass, parentScope);

        } else {
            throw new UnsupportedSyntax(ast);

        }
    }

    @SuppressWarnings("unchecked")
    private void build(
        List<DFKlass> list, Expression expr,
        DFKlass parentKlass, DFVarScope parentScope)
        throws UnsupportedSyntax {
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
            this.build(list, prefix.getOperand(), parentKlass, parentScope);

        } else if (expr instanceof PostfixExpression) {
            PostfixExpression postfix = (PostfixExpression)expr;
            this.build(list, postfix.getOperand(), parentKlass, parentScope);

        } else if (expr instanceof InfixExpression) {
            InfixExpression infix = (InfixExpression)expr;
            this.build(list, infix.getLeftOperand(), parentKlass, parentScope);
            this.build(list, infix.getRightOperand(), parentKlass, parentScope);

        } else if (expr instanceof ParenthesizedExpression) {
            ParenthesizedExpression paren = (ParenthesizedExpression)expr;
            this.build(list, paren.getExpression(), parentKlass, parentScope);

        } else if (expr instanceof Assignment) {
            Assignment assn = (Assignment)expr;
            this.build(list, assn.getLeftHandSide(), parentKlass, parentScope);
            this.build(list, assn.getRightHandSide(), parentKlass, parentScope);

        } else if (expr instanceof VariableDeclarationExpression) {
            VariableDeclarationExpression decl =
                (VariableDeclarationExpression)expr;
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>) decl.fragments()) {
                Expression init = frag.getInitializer();
                if (init != null) {
                    this.build(list, init, parentKlass, parentScope);
                }
            }

        } else if (expr instanceof MethodInvocation) {
            MethodInvocation invoke = (MethodInvocation)expr;
            Expression expr1 = invoke.getExpression();
            if (expr1 != null) {
                this.build(list, expr1, parentKlass, parentScope);
            }
            for (Expression arg : (List<Expression>) invoke.arguments()) {
                this.build(list, arg, parentKlass, parentScope);
            }

        } else if (expr instanceof SuperMethodInvocation) {
            SuperMethodInvocation sinvoke = (SuperMethodInvocation)expr;
            for (Expression arg : (List<Expression>) sinvoke.arguments()) {
                this.build(list, arg, parentKlass, parentScope);
            }

        } else if (expr instanceof ArrayCreation) {
            ArrayCreation ac = (ArrayCreation)expr;
            for (Expression dim : (List<Expression>) ac.dimensions()) {
                this.build(list, dim, parentKlass, parentScope);
            }
            ArrayInitializer init = ac.getInitializer();
            if (init != null) {
                this.build(list, init, parentKlass, parentScope);
            }

        } else if (expr instanceof ArrayInitializer) {
            ArrayInitializer init = (ArrayInitializer)expr;
            for (Expression expr1 : (List<Expression>) init.expressions()) {
                this.build(list, expr1, parentKlass, parentScope);
            }

        } else if (expr instanceof ArrayAccess) {
            ArrayAccess aa = (ArrayAccess)expr;
            this.build(list, aa.getIndex(), parentKlass, parentScope);
            this.build(list, aa.getArray(), parentKlass, parentScope);

        } else if (expr instanceof FieldAccess) {
            FieldAccess fa = (FieldAccess)expr;
            this.build(list, fa.getExpression(), parentKlass, parentScope);

        } else if (expr instanceof SuperFieldAccess) {

        } else if (expr instanceof CastExpression) {
            CastExpression cast = (CastExpression)expr;
            this.build(list, cast.getExpression(), parentKlass, parentScope);

        } else if (expr instanceof ClassInstanceCreation) {
            ClassInstanceCreation cstr = (ClassInstanceCreation)expr;
            Expression expr1 = cstr.getExpression();
            if (expr1 != null) {
                this.build(list, expr1, parentKlass, parentScope);
            }
            for (Expression arg : (List<Expression>) cstr.arguments()) {
                this.build(list, arg, parentKlass, parentScope);
            }
            AnonymousClassDeclaration anonDecl =
                cstr.getAnonymousClassDeclaration();
            if (anonDecl != null) {
                String id = Utils.encodeASTNode(anonDecl);
                DFTypeSpace anonSpace = this.lookupSpace(id);
                DFKlass anonKlass = anonSpace.createKlass(
                    parentKlass, parentScope, id);
                anonKlass.setTree(anonDecl);
                list.add(anonKlass);
                anonSpace.build(list, anonKlass, anonKlass.getKlassScope(),
                                anonDecl.bodyDeclarations());
            }

        } else if (expr instanceof ConditionalExpression) {
            ConditionalExpression cond = (ConditionalExpression)expr;
            this.build(list, cond.getExpression(), parentKlass, parentScope);
            this.build(list, cond.getThenExpression(), parentKlass, parentScope);
            this.build(list, cond.getElseExpression(), parentKlass, parentScope);

        } else if (expr instanceof InstanceofExpression) {

        } else if (expr instanceof LambdaExpression) {
            LambdaExpression lambda = (LambdaExpression)expr;
            ASTNode body = lambda.getBody();
            if (body instanceof Statement) {
                // XXX TODO Statement lambda
            } else if (body instanceof Expression) {
                // XXX TODO Expresssion lambda
            } else {
                throw new UnsupportedSyntax(body);
            }

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

    // dump: for debugging.
    public void dump() {
        dump(System.err, "");
    }
    public void dump(PrintStream out, String indent) {
        out.println(indent+this.getFullName()+" {");
        String i2 = indent + "  ";
        for (DFKlass klass : _id2klass.values()) {
            out.println(i2+"defined: "+klass);
        }
        for (DFTypeSpace space : _children) {
            space.dump(out, i2);
        }
        out.println(indent+"}");
    }
}
