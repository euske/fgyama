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

    private SortedMap<String, DFTypeSpace> _id2space =
        new TreeMap<String, DFTypeSpace>();
    private SortedMap<String, DFKlass> _id2klass =
        new TreeMap<String, DFKlass>();

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

    public DFTypeSpace lookupSpace(SimpleName name) {
        return this.lookupSpace(name.getIdentifier());
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
            _id2space.put(id, space);
            //Logger.info("DFTypeSpace.addChild:", this, ":", id);
        }
        return space;
    }

    protected DFKlass createKlass(
        DFKlass parentKlass, DFVarScope parentScope, SimpleName name) {
        return this.createKlass(
            parentKlass, parentScope, name.getIdentifier());
    }

    protected DFKlass createKlass(
        DFKlass parentKlass, DFVarScope parentScope, String id) {
        assert id.indexOf('.') < 0;
        DFKlass klass = _id2klass.get(id);
        if (klass != null) return klass;
        klass = new DFKlass(id, this, parentKlass, parentScope);
        //Logger.info("DFTypeSpace.createKlass:", klass);
        return this.addKlass(id, klass);
    }

    public DFKlass addKlass(String id, DFKlass klass) {
        assert id.indexOf('.') < 0;
        //assert !_id2klass.containsKey(id);
        _id2klass.put(id, klass);
        //Logger.info("DFTypeSpace.addKlass:", this, ":", id);
        return klass;
    }

    public DFKlass getKlass(Name name)
        throws TypeNotFound {
        return this.getKlass(name.getFullyQualifiedName());
    }
    public DFKlass getKlass(String id)
        throws TypeNotFound {
        //Logger.info("DFTypeSpace.getKlass:", this, ":", id);
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

    public DFKlass[] getKlasses() {
	DFKlass[] klasses = new DFKlass[_id2klass.size()];
	_id2klass.values().toArray(klasses);
	return klasses;
    }

    @SuppressWarnings("unchecked")
    public DFKlass buildAbstTypeDecl(
        String filePath, AbstractTypeDeclaration abstTypeDecl,
        DFKlass parentKlass, DFVarScope parentScope)
        throws UnsupportedSyntax {
        assert abstTypeDecl != null;
        //Logger.info("DFTypeSpace.build:", this, ":", abstTypeDecl.getName());
        DFKlass klass = this.createKlass(
            parentKlass, parentScope, abstTypeDecl.getName());
        if (abstTypeDecl instanceof TypeDeclaration) {
            klass.setMapTypes(
                ((TypeDeclaration)abstTypeDecl).typeParameters());
        }
        klass.setTree(filePath, abstTypeDecl);
	return klass;
    }

    @SuppressWarnings("unchecked")
    public void buildDecls(
        DFKlass klass, DFVarScope parentScope,
	List<BodyDeclaration> decls)
        throws UnsupportedSyntax {
        for (BodyDeclaration body : decls) {
	    if (body instanceof AbstractTypeDeclaration) {
		this.buildAbstTypeDecl(
                    klass.getFilePath(),
                    (AbstractTypeDeclaration)body,
                    klass, parentScope);
	    } else if (body instanceof FieldDeclaration) {
                FieldDeclaration fieldDecl = (FieldDeclaration)body;
                for (VariableDeclarationFragment frag :
                         (List<VariableDeclarationFragment>) fieldDecl.fragments()) {
                    Expression init = frag.getInitializer();
                    if (init != null) {
                        this.buildExpr(init, klass, parentScope);
                    }
                }
	    } else if (body instanceof MethodDeclaration) {
                MethodDeclaration methodDecl = (MethodDeclaration)body;
                Statement stmt = methodDecl.getBody();
                if (stmt != null) {
                    String id = "method"+Utils.encodeASTNode(methodDecl);
                    DFTypeSpace methodSpace = this.lookupSpace(id);
                    DFLocalVarScope scope = new DFLocalVarScope(
                        parentScope, methodDecl.getName());
                    klass.putMethodScope(methodDecl, scope);
                    methodSpace.buildStmt(stmt, klass, scope);
                }
	    } else if (body instanceof AnnotationTypeMemberDeclaration) {
		;
	    } else if (body instanceof Initializer) {
		Initializer initializer = (Initializer)body;
                Statement stmt = initializer.getBody();
                DFLocalVarScope scope = new DFLocalVarScope(
                    parentScope, "<clinit>");
                klass.putMethodScope(initializer, scope);
                this.buildStmt(stmt, klass, scope);

	    } else {
		throw new UnsupportedSyntax(body);
	    }
	}
    }

    @SuppressWarnings("unchecked")
    private void buildStmt(
        Statement ast,
        DFKlass klass, DFLocalVarScope parentScope)
        throws UnsupportedSyntax {
        assert ast != null;

        if (ast instanceof AssertStatement) {

        } else if (ast instanceof Block) {
            Block block = (Block)ast;
            DFLocalVarScope childScope = parentScope.addChild("b", ast);
            for (Statement stmt :
                     (List<Statement>) block.statements()) {
                this.buildStmt(stmt, klass, childScope);
            }

        } else if (ast instanceof EmptyStatement) {

        } else if (ast instanceof VariableDeclarationStatement) {
            VariableDeclarationStatement varStmt =
                (VariableDeclarationStatement)ast;
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>) varStmt.fragments()) {
                Expression expr = frag.getInitializer();
                if (expr != null) {
                    this.buildExpr(expr, klass, parentScope);
                }
            }

        } else if (ast instanceof ExpressionStatement) {
            ExpressionStatement exprStmt = (ExpressionStatement)ast;
            this.buildExpr(exprStmt.getExpression(), klass, parentScope);

        } else if (ast instanceof ReturnStatement) {
            ReturnStatement returnStmt = (ReturnStatement)ast;
            Expression expr = returnStmt.getExpression();
            if (expr != null) {
                this.buildExpr(expr, klass, parentScope);
            }

        } else if (ast instanceof IfStatement) {
            IfStatement ifStmt = (IfStatement)ast;
            this.buildExpr(ifStmt.getExpression(), klass, parentScope);
            Statement thenStmt = ifStmt.getThenStatement();
            this.buildStmt(thenStmt, klass, parentScope);
            Statement elseStmt = ifStmt.getElseStatement();
            if (elseStmt != null) {
                this.buildStmt(elseStmt, klass, parentScope);
            }

        } else if (ast instanceof SwitchStatement) {
            SwitchStatement switchStmt = (SwitchStatement)ast;
            DFLocalVarScope childScope = parentScope.addChild("switch", ast);
            this.buildExpr(switchStmt.getExpression(), klass, childScope);
            for (Statement stmt :
                     (List<Statement>) switchStmt.statements()) {
                this.buildStmt(stmt, klass, childScope);
            }

        } else if (ast instanceof SwitchCase) {
            SwitchCase switchCase = (SwitchCase)ast;
            Expression expr = switchCase.getExpression();
            if (expr != null) {
                this.buildExpr(expr, klass, parentScope);
            }

        } else if (ast instanceof WhileStatement) {
            WhileStatement whileStmt = (WhileStatement)ast;
            this.buildExpr(whileStmt.getExpression(), klass, parentScope);
            DFLocalVarScope childScope = parentScope.addChild("while", ast);
            Statement stmt = whileStmt.getBody();
            this.buildStmt(stmt, klass, childScope);

        } else if (ast instanceof DoStatement) {
            DoStatement doStmt = (DoStatement)ast;
            DFLocalVarScope childScope = parentScope.addChild("do", ast);
            Statement stmt = doStmt.getBody();
            this.buildStmt(stmt, klass, childScope);
            this.buildExpr(doStmt.getExpression(), klass, childScope);

        } else if (ast instanceof ForStatement) {
            ForStatement forStmt = (ForStatement)ast;
            DFLocalVarScope childScope = parentScope.addChild("for", ast);
            for (Expression init :
                     (List<Expression>) forStmt.initializers()) {
                this.buildExpr(init, klass, childScope);
            }
            Expression expr = forStmt.getExpression();
            if (expr != null) {
                this.buildExpr(expr, klass, childScope);
            }
            Statement stmt = forStmt.getBody();
            this.buildStmt(stmt, klass, childScope);
            for (Expression update :
                     (List<Expression>) forStmt.updaters()) {
                this.buildExpr(update, klass, childScope);
            }

        } else if (ast instanceof EnhancedForStatement) {
            EnhancedForStatement eForStmt = (EnhancedForStatement)ast;
            this.buildExpr(eForStmt.getExpression(), klass, parentScope);
            DFLocalVarScope childScope = parentScope.addChild("efor", ast);
            this.buildStmt(eForStmt.getBody(), klass, childScope);

        } else if (ast instanceof BreakStatement) {

        } else if (ast instanceof ContinueStatement) {

        } else if (ast instanceof LabeledStatement) {
            LabeledStatement labeledStmt = (LabeledStatement)ast;
            Statement stmt = labeledStmt.getBody();
            this.buildStmt(stmt, klass, parentScope);

        } else if (ast instanceof SynchronizedStatement) {
            SynchronizedStatement syncStmt = (SynchronizedStatement)ast;
            this.buildExpr(syncStmt.getExpression(), klass, parentScope);
            this.buildStmt(syncStmt.getBody(), klass, parentScope);

        } else if (ast instanceof TryStatement) {
            TryStatement tryStmt = (TryStatement)ast;
            DFLocalVarScope childScope = parentScope.addChild("try", ast);
            for (VariableDeclarationExpression decl :
                     (List<VariableDeclarationExpression>) tryStmt.resources()) {
                this.buildExpr(decl, klass, childScope);
            }
            this.buildStmt(tryStmt.getBody(), klass, childScope);
            for (CatchClause cc :
                     (List<CatchClause>) tryStmt.catchClauses()) {
                DFLocalVarScope catchScope = parentScope.addChild("catch", cc);
                this.buildStmt(cc.getBody(), klass, catchScope);
            }
            Block finBlock = tryStmt.getFinally();
            if (finBlock != null) {
                this.buildStmt(finBlock, klass, parentScope);
            }

        } else if (ast instanceof ThrowStatement) {
            ThrowStatement throwStmt = (ThrowStatement)ast;
            Expression expr = throwStmt.getExpression();
            if (expr != null) {
                this.buildExpr(expr, klass, parentScope);
            }

        } else if (ast instanceof ConstructorInvocation) {
            ConstructorInvocation ci = (ConstructorInvocation)ast;
            for (Expression expr :
                     (List<Expression>) ci.arguments()) {
                this.buildExpr(expr, klass, parentScope);
            }

        } else if (ast instanceof SuperConstructorInvocation) {
            SuperConstructorInvocation sci = (SuperConstructorInvocation)ast;
            for (Expression expr :
                     (List<Expression>) sci.arguments()) {
                this.buildExpr(expr, klass, parentScope);
            }

        } else if (ast instanceof TypeDeclarationStatement) {
            TypeDeclarationStatement typeDeclStmt = (TypeDeclarationStatement)ast;
            this.buildAbstTypeDecl(
                klass.getFilePath(),
                typeDeclStmt.getDeclaration(),
                klass, parentScope);

        } else {
            throw new UnsupportedSyntax(ast);

        }
    }

    @SuppressWarnings("unchecked")
    private void buildExpr(
        Expression expr,
        DFKlass klass, DFVarScope parentScope)
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
            this.buildExpr(prefix.getOperand(), klass, parentScope);

        } else if (expr instanceof PostfixExpression) {
            PostfixExpression postfix = (PostfixExpression)expr;
            this.buildExpr(postfix.getOperand(), klass, parentScope);

        } else if (expr instanceof InfixExpression) {
            InfixExpression infix = (InfixExpression)expr;
            this.buildExpr(infix.getLeftOperand(), klass, parentScope);
            this.buildExpr(infix.getRightOperand(), klass, parentScope);

        } else if (expr instanceof ParenthesizedExpression) {
            ParenthesizedExpression paren = (ParenthesizedExpression)expr;
            this.buildExpr(paren.getExpression(), klass, parentScope);

        } else if (expr instanceof Assignment) {
            Assignment assn = (Assignment)expr;
            this.buildExpr(assn.getLeftHandSide(), klass, parentScope);
            this.buildExpr(assn.getRightHandSide(), klass, parentScope);

        } else if (expr instanceof VariableDeclarationExpression) {
            VariableDeclarationExpression decl =
                (VariableDeclarationExpression)expr;
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>) decl.fragments()) {
                Expression init = frag.getInitializer();
                if (init != null) {
                    this.buildExpr(init, klass, parentScope);
                }
            }

        } else if (expr instanceof MethodInvocation) {
            MethodInvocation invoke = (MethodInvocation)expr;
            Expression expr1 = invoke.getExpression();
            if (expr1 != null) {
                this.buildExpr(expr1, klass, parentScope);
            }
            for (Expression arg : (List<Expression>) invoke.arguments()) {
                this.buildExpr(arg, klass, parentScope);
            }

        } else if (expr instanceof SuperMethodInvocation) {
            SuperMethodInvocation sinvoke = (SuperMethodInvocation)expr;
            for (Expression arg : (List<Expression>) sinvoke.arguments()) {
                this.buildExpr(arg, klass, parentScope);
            }

        } else if (expr instanceof ArrayCreation) {
            ArrayCreation ac = (ArrayCreation)expr;
            for (Expression dim : (List<Expression>) ac.dimensions()) {
                this.buildExpr(dim, klass, parentScope);
            }
            ArrayInitializer init = ac.getInitializer();
            if (init != null) {
                this.buildExpr(init, klass, parentScope);
            }

        } else if (expr instanceof ArrayInitializer) {
            ArrayInitializer init = (ArrayInitializer)expr;
            for (Expression expr1 : (List<Expression>) init.expressions()) {
                this.buildExpr(expr1, klass, parentScope);
            }

        } else if (expr instanceof ArrayAccess) {
            ArrayAccess aa = (ArrayAccess)expr;
            this.buildExpr(aa.getIndex(), klass, parentScope);
            this.buildExpr(aa.getArray(), klass, parentScope);

        } else if (expr instanceof FieldAccess) {
            FieldAccess fa = (FieldAccess)expr;
            this.buildExpr(fa.getExpression(), klass, parentScope);

        } else if (expr instanceof SuperFieldAccess) {

        } else if (expr instanceof CastExpression) {
            CastExpression cast = (CastExpression)expr;
            this.buildExpr(cast.getExpression(), klass, parentScope);

        } else if (expr instanceof ClassInstanceCreation) {
            ClassInstanceCreation cstr = (ClassInstanceCreation)expr;
            Expression expr1 = cstr.getExpression();
            if (expr1 != null) {
                this.buildExpr(expr1, klass, parentScope);
            }
            for (Expression arg : (List<Expression>) cstr.arguments()) {
                this.buildExpr(arg, klass, parentScope);
            }
            AnonymousClassDeclaration anonDecl =
                cstr.getAnonymousClassDeclaration();
            if (anonDecl != null) {
                String id = "anon"+Utils.encodeASTNode(anonDecl);
                DFKlass anonKlass = this.createKlass(klass, parentScope, id);
                anonKlass.setTree(klass.getFilePath(), anonDecl);
            }

        } else if (expr instanceof ConditionalExpression) {
            ConditionalExpression cond = (ConditionalExpression)expr;
            this.buildExpr(cond.getExpression(), klass, parentScope);
            this.buildExpr(cond.getThenExpression(), klass, parentScope);
            this.buildExpr(cond.getElseExpression(), klass, parentScope);

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
        for (Map.Entry<String,DFKlass> e : _id2klass.entrySet()) {
            out.println(i2+"defined: "+e.getKey()+" "+e.getValue());
        }
        for (DFTypeSpace space : _id2space.values()) {
            space.dump(out, i2);
        }
        out.println(indent+"}");
    }
}
