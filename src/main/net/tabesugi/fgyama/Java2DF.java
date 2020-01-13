/**
 * Java2DF
 * Dataflow analyzer for Java
 */
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.w3c.dom.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFFileScope
//  File-wide scope for methods and variables.
class DFFileScope extends DFVarScope {

    private Map<String, DFRef> _refs =
	new HashMap<String, DFRef>();
    private List<DFMethod> _methods =
	new ArrayList<DFMethod>();

    public DFFileScope(DFVarScope outer, String path) {
	super(outer, "["+path+"]");
    }

    @Override
    public DFRef lookupVar(String id)
	throws VariableNotFound {
	DFRef ref = _refs.get(id);
	if (ref != null) {
	    return ref;
	} else {
	    return super.lookupVar(id);
	}
    }

    public DFMethod lookupStaticMethod(SimpleName name, DFType[] argTypes)
        throws MethodNotFound {
	String id = name.getIdentifier();
	int bestDist = -1;
	DFMethod bestMethod = null;
	for (DFMethod method1 : _methods) {
            if (!id.equals(method1.getName())) continue;
	    Map<DFMapType, DFType> typeMap = new HashMap<DFMapType, DFType>();
	    int dist = method1.canAccept(argTypes, typeMap);
	    if (dist < 0) continue;
	    if (bestDist < 0 || dist < bestDist) {
		DFMethod method = method1.parameterize(typeMap);
		if (method != null) {
		    bestDist = dist;
		    bestMethod = method;
		}
	    }
	}
        if (bestMethod == null) throw new MethodNotFound(id, argTypes);
	return bestMethod;
    }

    public void importStatic(DFKlass klass) {
	Logger.debug("ImportStatic:", klass+".*");
	for (DFKlass.FieldRef ref : klass.getFields()) {
	    _refs.put(ref.getName(), ref);
	}
	for (DFMethod method : klass.getMethods()) {
	    _methods.add(method);
	}
    }

    public void importStatic(DFKlass klass, SimpleName name) {
	Logger.debug("ImportStatic:", klass+"."+name);
	String id = name.getIdentifier();
	try {
            DFRef ref = klass.lookupField(id);
	    _refs.put(id, ref);
	} catch (VariableNotFound e) {
            try {
                DFMethod method = klass.lookupMethod(
                    DFMethod.CallStyle.StaticMethod, name, null);
                _methods.add(method);
            } catch (MethodNotFound ee) {
            }
	}
    }
}


//  Java2DF
//
public class Java2DF {

    private DFRootTypeSpace _rootSpace;
    private List<Exporter> _exporters =
        new ArrayList<Exporter>();
    private DFGlobalScope _globalScope =
        new DFGlobalScope();
    private Map<String, DFFileScope> _fileScope =
        new HashMap<String, DFFileScope>();
    private Map<String, List<DFKlass>> _fileKlasses =
        new HashMap<String, List<DFKlass>>();

    private void enumKlasses(DFKlass klass, Set<DFKlass> klasses)
        throws InvalidSyntax {
        klass.load();
        ASTNode ast = klass.getTree();
        if (ast == null) return;
        if (klasses.contains(klass)) return;
        if (klass.isGeneric()) return;
        //Logger.info("enumKlasses:", klass);
        klasses.add(klass);
        DFTypeFinder finder = klass.getFinder();
        List<DFKlass> toLoad = new ArrayList<DFKlass>();
	this.enumKlassesDecl(finder, klass, ast, klasses);
    }

    @SuppressWarnings("unchecked")
    private void enumKlassesDecl(
        DFTypeFinder finder, DFKlass klass,
        ASTNode ast, Set<DFKlass> klasses)
        throws InvalidSyntax {
        if (ast instanceof AbstractTypeDeclaration) {
            AbstractTypeDeclaration abstDecl = (AbstractTypeDeclaration)ast;
            this.enumKlassesDecls(
                finder, klass, abstDecl.bodyDeclarations(), klasses);

        } else if (ast instanceof ClassInstanceCreation) {
	    ClassInstanceCreation cstr = (ClassInstanceCreation)ast;
            this.enumKlassesDecls(
                finder, klass,
		cstr.getAnonymousClassDeclaration().bodyDeclarations(),	klasses);

        } else if (ast instanceof LambdaExpression) {
            LambdaExpression lambda = (LambdaExpression)ast;
            DFMethod method = ((DFLambdaKlass)klass).getFuncMethod();
            ASTNode body = lambda.getBody();
            if (body instanceof Statement) {
                this.enumKlassesStmt(
                    finder, method, (Statement)body, klasses);
            } else if (body instanceof Expression) {
                this.enumKlassesExpr(
                    finder, method, (Expression)body, klasses);
            } else {
                throw new InvalidSyntax(body);
            }

        } else if (ast instanceof CreationReference) {
            DFType type = finder.resolveSafe(
                ((CreationReference)ast).getType());
            if (type instanceof DFKlass) {
                enumKlasses(type.toKlass(), klasses);
            }

        } else if (ast instanceof SuperMethodReference) {
            try {
                DFType type = finder.lookupType(
                    ((SuperMethodReference)ast).getQualifier());
                if (type instanceof DFKlass) {
                    enumKlasses(type.toKlass(), klasses);
                }
            } catch (TypeNotFound e) {
            }

        } else if (ast instanceof TypeMethodReference) {
            DFType type = finder.resolveSafe(
                ((TypeMethodReference)ast).getType());
            if (type instanceof DFKlass) {
                enumKlasses(type.toKlass(), klasses);
            }

        } else if (ast instanceof ExpressionMethodReference) {
            // XXX ignored mref.typeArguments() for method refs.
            this.enumKlassesExpr(
                finder, klass,
                ((ExpressionMethodReference)ast).getExpression(),
                klasses);

        } else {
            throw new InvalidSyntax(ast);
        }
    }

    @SuppressWarnings("unchecked")
    private void enumKlassesDecls(
        DFTypeFinder finder, DFKlass klass,
        List<BodyDeclaration> decls, Set<DFKlass> klasses)
        throws InvalidSyntax {

        DFMethod initMethod = klass.getInitMethod();
        assert initMethod != null;
        for (BodyDeclaration body : decls) {
            if (body instanceof AbstractTypeDeclaration) {
                AbstractTypeDeclaration decl = (AbstractTypeDeclaration)body;
                DFType innerType = klass.getType(decl.getName());
                if (innerType != null) {
		    enumKlasses(innerType.toKlass(), klasses);
		}

            } else if (body instanceof FieldDeclaration) {
                FieldDeclaration decl = (FieldDeclaration)body;
		DFType fldType = finder.resolveSafe(decl.getType());
		if (fldType instanceof DFKlass) {
		    enumKlasses((DFKlass)fldType, klasses);
		}
                for (VariableDeclarationFragment frag :
                         (List<VariableDeclarationFragment>) decl.fragments()) {
                    Expression expr = frag.getInitializer();
                    if (expr != null) {
                        this.enumKlassesExpr(finder, initMethod, expr, klasses);
                    }
                }

            } else if (body instanceof MethodDeclaration) {
                MethodDeclaration decl = (MethodDeclaration)body;
                String id = Utils.encodeASTNode(decl);
                DFMethod method = klass.getMethod(id);
                DFTypeFinder finder2 = method.getFinder();
		List<SingleVariableDeclaration> varDecls = decl.parameters();
		for (SingleVariableDeclaration varDecl : varDecls) {
		    DFType argType = finder2.resolveSafe(varDecl.getType());
		    if (argType instanceof DFKlass) {
			enumKlasses((DFKlass)argType, klasses);
		    }
		}
		if (!decl.isConstructor()) {
		    DFType returnType = finder2.resolveSafe(decl.getReturnType2());
		    if (returnType instanceof DFKlass) {
			enumKlasses((DFKlass)returnType, klasses);
		    }
		}
                if (decl.getBody() != null) {
                    this.enumKlassesStmt(
                        finder2, method, decl.getBody(), klasses);
                }

            } else if (body instanceof EnumConstantDeclaration) {

            } else if (body instanceof AnnotationTypeMemberDeclaration) {
                AnnotationTypeMemberDeclaration decl =
                    (AnnotationTypeMemberDeclaration)body;
		DFType type = finder.resolveSafe(decl.getType());
		if (type instanceof DFKlass) {
		    enumKlasses((DFKlass)type, klasses);
		}

            } else if (body instanceof Initializer) {
                Initializer initializer = (Initializer)body;
                this.enumKlassesStmt(
                    finder, initMethod, initializer.getBody(), klasses);

            } else {
                throw new InvalidSyntax(body);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void enumKlassesStmt(
        DFTypeFinder finder, DFTypeSpace space,
        Statement ast, Set<DFKlass> klasses)
        throws InvalidSyntax {
        assert ast != null;

        if (ast instanceof AssertStatement) {

        } else if (ast instanceof Block) {
            Block block = (Block)ast;
            for (Statement stmt :
                     (List<Statement>) block.statements()) {
                this.enumKlassesStmt(finder, space, stmt, klasses);
            }

        } else if (ast instanceof EmptyStatement) {

        } else if (ast instanceof VariableDeclarationStatement) {
            VariableDeclarationStatement varStmt =
                (VariableDeclarationStatement)ast;
	    DFType varType = finder.resolveSafe(varStmt.getType());
	    if (varType instanceof DFKlass) {
		enumKlasses((DFKlass)varType, klasses);
	    }
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>) varStmt.fragments()) {
                Expression expr = frag.getInitializer();
                if (expr != null) {
                    this.enumKlassesExpr(finder, space, expr, klasses);
                }
            }

        } else if (ast instanceof ExpressionStatement) {
            ExpressionStatement exprStmt = (ExpressionStatement)ast;
            Expression expr = exprStmt.getExpression();
            this.enumKlassesExpr(finder, space, expr, klasses);

        } else if (ast instanceof ReturnStatement) {
            ReturnStatement returnStmt = (ReturnStatement)ast;
            Expression expr = returnStmt.getExpression();
            if (expr != null) {
                this.enumKlassesExpr(finder, space, expr, klasses);
            }

        } else if (ast instanceof IfStatement) {
            IfStatement ifStmt = (IfStatement)ast;
            Expression expr = ifStmt.getExpression();
            this.enumKlassesExpr(finder, space, expr, klasses);
            Statement thenStmt = ifStmt.getThenStatement();
            this.enumKlassesStmt(finder, space, thenStmt, klasses);
            Statement elseStmt = ifStmt.getElseStatement();
            if (elseStmt != null) {
                this.enumKlassesStmt(finder, space, elseStmt, klasses);
            }

        } else if (ast instanceof SwitchStatement) {
            SwitchStatement switchStmt = (SwitchStatement)ast;
            Expression expr = switchStmt.getExpression();
            this.enumKlassesExpr(finder, space, expr, klasses);
            for (Statement stmt :
                     (List<Statement>) switchStmt.statements()) {
                this.enumKlassesStmt(finder, space, stmt, klasses);
            }

        } else if (ast instanceof SwitchCase) {
            SwitchCase switchCase = (SwitchCase)ast;
            Expression expr = switchCase.getExpression();
            if (expr != null) {
                this.enumKlassesExpr(finder, space, expr, klasses);
            }

        } else if (ast instanceof WhileStatement) {
            WhileStatement whileStmt = (WhileStatement)ast;
            Expression expr = whileStmt.getExpression();
            this.enumKlassesExpr(finder, space, expr, klasses);
            Statement stmt = whileStmt.getBody();
            this.enumKlassesStmt(finder, space, stmt, klasses);

        } else if (ast instanceof DoStatement) {
            DoStatement doStmt = (DoStatement)ast;
            Statement stmt = doStmt.getBody();
            this.enumKlassesStmt(finder, space, stmt, klasses);
            Expression expr = doStmt.getExpression();
            this.enumKlassesExpr(finder, space, expr, klasses);

        } else if (ast instanceof ForStatement) {
            ForStatement forStmt = (ForStatement)ast;
            for (Expression init :
                     (List<Expression>) forStmt.initializers()) {
                this.enumKlassesExpr(finder, space, init, klasses);
            }
            Expression expr = forStmt.getExpression();
            if (expr != null) {
                this.enumKlassesExpr(finder, space, expr, klasses);
            }
            Statement stmt = forStmt.getBody();
            this.enumKlassesStmt(finder, space, stmt, klasses);
            for (Expression update :
                     (List<Expression>) forStmt.updaters()) {
                this.enumKlassesExpr(finder, space, update, klasses);
            }

        } else if (ast instanceof EnhancedForStatement) {
            EnhancedForStatement eForStmt = (EnhancedForStatement)ast;
            this.enumKlassesExpr(finder, space, eForStmt.getExpression(), klasses);
            SingleVariableDeclaration decl = eForStmt.getParameter();
	    DFType varType = finder.resolveSafe(decl.getType());
	    if (varType instanceof DFKlass) {
		enumKlasses((DFKlass)varType, klasses);
	    }
            Statement stmt = eForStmt.getBody();
            this.enumKlassesStmt(finder, space, stmt, klasses);

        } else if (ast instanceof BreakStatement) {

        } else if (ast instanceof ContinueStatement) {

        } else if (ast instanceof LabeledStatement) {
            LabeledStatement labeledStmt = (LabeledStatement)ast;
            Statement stmt = labeledStmt.getBody();
            this.enumKlassesStmt(finder, space, stmt, klasses);

        } else if (ast instanceof SynchronizedStatement) {
            SynchronizedStatement syncStmt = (SynchronizedStatement)ast;
            this.enumKlassesExpr(finder, space, syncStmt.getExpression(), klasses);
            this.enumKlassesStmt(finder, space, syncStmt.getBody(), klasses);

        } else if (ast instanceof TryStatement) {
            TryStatement tryStmt = (TryStatement)ast;
            for (VariableDeclarationExpression decl :
                     (List<VariableDeclarationExpression>) tryStmt.resources()) {
                this.enumKlassesExpr(finder, space, decl, klasses);
            }
            this.enumKlassesStmt(finder, space, tryStmt.getBody(), klasses);
            for (CatchClause cc :
                     (List<CatchClause>) tryStmt.catchClauses()) {
                SingleVariableDeclaration decl = cc.getException();
		DFType varType = finder.resolveSafe(decl.getType());
		if (varType instanceof DFKlass) {
		    enumKlasses((DFKlass)varType, klasses);
		}
                this.enumKlassesStmt(finder, space, cc.getBody(), klasses);
            }
            Block finBlock = tryStmt.getFinally();
            if (finBlock != null) {
                this.enumKlassesStmt(finder, space, finBlock, klasses);
            }

        } else if (ast instanceof ThrowStatement) {
            ThrowStatement throwStmt = (ThrowStatement)ast;
            Expression expr = throwStmt.getExpression();
            if (expr != null) {
                this.enumKlassesExpr(finder, space, expr, klasses);
            }

        } else if (ast instanceof ConstructorInvocation) {
            ConstructorInvocation ci = (ConstructorInvocation)ast;
            for (Expression expr :
                     (List<Expression>) ci.arguments()) {
                this.enumKlassesExpr(finder, space, expr, klasses);
            }

        } else if (ast instanceof SuperConstructorInvocation) {
            SuperConstructorInvocation sci = (SuperConstructorInvocation)ast;
            for (Expression expr :
                     (List<Expression>) sci.arguments()) {
                this.enumKlassesExpr(finder, space, expr, klasses);
            }

        } else if (ast instanceof TypeDeclarationStatement) {
            TypeDeclarationStatement decl = (TypeDeclarationStatement)ast;
            AbstractTypeDeclaration abstDecl = decl.getDeclaration();
            DFType innerType = space.getType(abstDecl.getName());
            if (innerType != null) {
                this.enumKlasses(innerType.toKlass(), klasses);
	    }

        } else {
            throw new InvalidSyntax(ast);

        }
    }

    @SuppressWarnings("unchecked")
    private void enumKlassesExpr(
        DFTypeFinder finder, DFTypeSpace space,
        Expression ast, Set<DFKlass> klasses)
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
                    DFKlass innerKlass = finder.lookupType(name).toKlass();
                    enumKlasses(innerKlass, klasses);
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
		DFKlass klass = finder.resolve(value).toKlass();
		enumKlasses(klass, klasses);
            } catch (TypeNotFound e) {
            }

        } else if (ast instanceof PrefixExpression) {
            PrefixExpression prefix = (PrefixExpression)ast;
            PrefixExpression.Operator op = prefix.getOperator();
            Expression operand = prefix.getOperand();
            this.enumKlassesExpr(finder, space, operand, klasses);
            if (op == PrefixExpression.Operator.INCREMENT ||
                op == PrefixExpression.Operator.DECREMENT) {
                this.enumKlassesExpr(finder, space, operand, klasses);
            }

        } else if (ast instanceof PostfixExpression) {
            PostfixExpression postfix = (PostfixExpression)ast;
            PostfixExpression.Operator op = postfix.getOperator();
            Expression operand = postfix.getOperand();
            this.enumKlassesExpr(finder, space, operand, klasses);
            if (op == PostfixExpression.Operator.INCREMENT ||
                op == PostfixExpression.Operator.DECREMENT) {
                this.enumKlassesExpr(finder, space, operand, klasses);
            }

        } else if (ast instanceof InfixExpression) {
            InfixExpression infix = (InfixExpression)ast;
            InfixExpression.Operator op = infix.getOperator();
            Expression loperand = infix.getLeftOperand();
            this.enumKlassesExpr(finder, space, loperand, klasses);
            Expression roperand = infix.getRightOperand();
            this.enumKlassesExpr(finder, space, roperand, klasses);

        } else if (ast instanceof ParenthesizedExpression) {
            ParenthesizedExpression paren = (ParenthesizedExpression)ast;
            this.enumKlassesExpr(finder, space, paren.getExpression(), klasses);

        } else if (ast instanceof Assignment) {
            Assignment assn = (Assignment)ast;
            Assignment.Operator op = assn.getOperator();
            this.enumKlassesExpr(finder, space, assn.getLeftHandSide(), klasses);
            if (op != Assignment.Operator.ASSIGN) {
                this.enumKlassesExpr(finder, space, assn.getLeftHandSide(), klasses);
            }
            this.enumKlassesExpr(finder, space, assn.getRightHandSide(), klasses);

        } else if (ast instanceof VariableDeclarationExpression) {
            VariableDeclarationExpression decl = (VariableDeclarationExpression)ast;
	    DFType varType = finder.resolveSafe(decl.getType());
	    if (varType instanceof DFKlass) {
		enumKlasses((DFKlass)varType, klasses);
	    }
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>) decl.fragments()) {
                Expression expr = frag.getInitializer();
                if (expr != null) {
                    this.enumKlassesExpr(finder, space, expr, klasses);
                }
            }

        } else if (ast instanceof MethodInvocation) {
            MethodInvocation invoke = (MethodInvocation)ast;
            Expression expr = invoke.getExpression();
            if (expr instanceof Name) {
                try {
                    DFKlass innerKlass = finder.lookupType((Name)expr).toKlass();
                    enumKlasses(innerKlass, klasses);
                } catch (TypeNotFound e) {
                }
            } else if (expr != null) {
                this.enumKlassesExpr(finder, space, expr, klasses);
            }
            for (Expression arg :
                     (List<Expression>) invoke.arguments()) {
                this.enumKlassesExpr(finder, space, arg, klasses);
            }

        } else if (ast instanceof SuperMethodInvocation) {
            SuperMethodInvocation si = (SuperMethodInvocation)ast;
            for (Expression arg :
                     (List<Expression>) si.arguments()) {
                this.enumKlassesExpr(finder, space, arg, klasses);
            }

        } else if (ast instanceof ArrayCreation) {
            ArrayCreation ac = (ArrayCreation)ast;
            for (Expression dim :
                     (List<Expression>) ac.dimensions()) {
                this.enumKlassesExpr(finder, space, dim, klasses);
            }
            ArrayInitializer init = ac.getInitializer();
            if (init != null) {
                this.enumKlassesExpr(finder, space, init, klasses);
            }
	    DFType type = finder.resolveSafe(ac.getType().getElementType());
	    if (type instanceof DFKlass) {
		enumKlasses((DFKlass)type, klasses);
	    }

        } else if (ast instanceof ArrayInitializer) {
            ArrayInitializer init = (ArrayInitializer)ast;
            for (Expression expr :
                     (List<Expression>) init.expressions()) {
                this.enumKlassesExpr(finder, space, expr, klasses);
            }

        } else if (ast instanceof ArrayAccess) {
            ArrayAccess aa = (ArrayAccess)ast;
            this.enumKlassesExpr(finder, space, aa.getArray(), klasses);
            this.enumKlassesExpr(finder, space, aa.getIndex(), klasses);

        } else if (ast instanceof FieldAccess) {
            FieldAccess fa = (FieldAccess)ast;
            SimpleName fieldName = fa.getName();
            this.enumKlassesExpr(finder, space, fa.getExpression(), klasses);

        } else if (ast instanceof SuperFieldAccess) {
            SuperFieldAccess sfa = (SuperFieldAccess)ast;
            SimpleName fieldName = sfa.getName();

        } else if (ast instanceof CastExpression) {
            CastExpression cast = (CastExpression)ast;
            this.enumKlassesExpr(finder, space, cast.getExpression(), klasses);
	    DFType type = finder.resolveSafe(cast.getType());
	    if (type instanceof DFKlass) {
		enumKlasses((DFKlass)type, klasses);
	    }

        } else if (ast instanceof ClassInstanceCreation) {
            ClassInstanceCreation cstr = (ClassInstanceCreation)ast;
            try {
                DFType instType;
                if (cstr.getAnonymousClassDeclaration() != null) {
                    String id = Utils.encodeASTNode(cstr);
                    instType = space.getType(id);
                } else {
                    instType = finder.resolve(cstr.getType());
                }
                if (instType instanceof DFKlass) {
                    enumKlasses((DFKlass)instType, klasses);
                }
            } catch (TypeNotFound e) {
            }
            Expression expr = cstr.getExpression();
            if (expr != null) {
                this.enumKlassesExpr(finder, space, expr, klasses);
            }
            for (Expression arg :
                     (List<Expression>) cstr.arguments()) {
                this.enumKlassesExpr(finder, space, arg, klasses);
            }

        } else if (ast instanceof ConditionalExpression) {
            ConditionalExpression cond = (ConditionalExpression)ast;
            this.enumKlassesExpr(finder, space, cond.getExpression(), klasses);
            this.enumKlassesExpr(finder, space, cond.getThenExpression(), klasses);
            this.enumKlassesExpr(finder, space, cond.getElseExpression(), klasses);

        } else if (ast instanceof InstanceofExpression) {
            InstanceofExpression instof = (InstanceofExpression)ast;
            this.enumKlassesExpr(finder, space, instof.getLeftOperand(), klasses);

        } else if (ast instanceof LambdaExpression) {
            LambdaExpression lambda = (LambdaExpression)ast;
            String id = Utils.encodeASTNode(lambda);
            DFKlass lambdaKlass = (DFKlass)space.getType(id);
	    // Do not use lambda klasses until defined.

        } else if (ast instanceof MethodReference) {
            //  CreationReference
            //  ExpressionMethodReference
            //  SuperMethodReference
            //  TypeMethodReference
            MethodReference methodref = (MethodReference)ast;
            String id = Utils.encodeASTNode(methodref);
            DFKlass methodRefKlass = (DFKlass)space.getType(id);
	    // Do not use methodref klasses until defined.

        } else {
	    throw new InvalidSyntax(ast);
        }
    }

    /// Top-level functions.

    public Java2DF(
        DFRootTypeSpace rootSpace) {
        _rootSpace = rootSpace;
    }

    public void addExporter(Exporter exporter) {
        _exporters.add(exporter);
    }

    public void removeExporter(Exporter exporter) {
        _exporters.remove(exporter);
    }

    private void exportGraph(DFGraph graph) {
        for (Exporter exporter : _exporters) {
            exporter.writeGraph(graph);
        }
    }
    private void startKlass(DFKlass klass) {
        for (Exporter exporter : _exporters) {
            exporter.startKlass(klass);
        }
    }
    private void endKlass() {
        for (Exporter exporter : _exporters) {
            exporter.endKlass();
        }
    }

    // Stage1: populate TypeSpaces.
    @SuppressWarnings("unchecked")
    public void buildTypeSpace(String key, CompilationUnit cunit)
        throws InvalidSyntax {
        DFTypeSpace packageSpace = _rootSpace.lookupSpace(cunit.getPackage());
        DFFileScope fileScope = new DFFileScope(_globalScope, key);
        _fileScope.put(key, fileScope);
        List<DFKlass> klasses = new ArrayList<DFKlass>();
	for (AbstractTypeDeclaration abstTypeDecl :
		 (List<AbstractTypeDeclaration>) cunit.types()) {
	    DFKlass klass = packageSpace.buildTypeFromAST(
		key, abstTypeDecl, null, fileScope);
	    klass.setKlassTree(key, abstTypeDecl);
	    Logger.debug("Stage1: Created:", klass);
	    klasses.add(klass);
	}
        _fileKlasses.put(key, klasses);
    }

    // Stage2: set references to external Klasses.
    @SuppressWarnings("unchecked")
    public void setTypeFinder(String key, CompilationUnit cunit) {
	// Search path for types: ROOT -> java.lang -> package -> imports.
        DFTypeFinder finder = new DFTypeFinder(_rootSpace);
        finder = new DFTypeFinder(_rootSpace.lookupSpace("java.lang"), finder);
        DFTypeSpace packageSpace = _rootSpace.lookupSpace(cunit.getPackage());
        finder = new DFTypeFinder(packageSpace, finder);
	// Populate the import space.
        DFTypeSpace importSpace = new DFTypeSpace("import:"+key);
        for (ImportDeclaration importDecl :
                 (List<ImportDeclaration>) cunit.imports()) {
            Name name = importDecl.getName();
	    if (importDecl.isOnDemand()) {
		Logger.debug("Import:", name+".*");
		finder = new DFTypeFinder(_rootSpace.lookupSpace(name), finder);
	    } else {
		assert name.isQualifiedName();
                DFType type = _rootSpace.getType(name);
                if (type != null) {
		    Logger.debug("Import:", name);
                    String id = ((QualifiedName)name).getName().getIdentifier();
		    importSpace.addKlass(id, type.toKlass());
		} else {
		    if (!importDecl.isStatic()) {
			Logger.error("Import: Class not found:", name);
		    }
		}
	    }
        }
	finder = new DFTypeFinder(importSpace, finder);
        for (DFKlass klass : _fileKlasses.get(key)) {
            klass.setFinder(finder);
	}
    }

    // Stage3: load class definitions and define parameterized Klasses.
    @SuppressWarnings("unchecked")
    public void loadKlasses(
        String key, CompilationUnit cunit, Set<DFKlass> klasses)
        throws InvalidSyntax {
        // Process static imports.
        DFFileScope fileScope = _fileScope.get(key);
        for (ImportDeclaration importDecl :
                 (List<ImportDeclaration>) cunit.imports()) {
            if (!importDecl.isStatic()) continue;
            Name name = importDecl.getName();
            if (importDecl.isOnDemand()) {
                DFType type = _rootSpace.getType(name);
                if (type != null) {
                    DFKlass klass = type.toKlass();
                    klass.load();
                    fileScope.importStatic(klass);
                }
            } else {
                QualifiedName qname = (QualifiedName)name;
                DFType type = _rootSpace.getType(qname.getQualifier());
                if (type != null) {
                    DFKlass klass = type.toKlass();
                    klass.load();
                    fileScope.importStatic(klass, qname.getName());
                }
            }
        }
        for (DFKlass klass : _fileKlasses.get(key)) {
	    enumKlasses(klass, klasses);
        }
    }

    // Stage4: list all methods.
    public void listMethods(Set<DFKlass> klasses)
        throws InvalidSyntax {
        // At this point, all the methods in all the used classes
        // (public, inner, in-statement and anonymous) are known.
        Queue<DFMethod> queue = new ArrayDeque<DFMethod>();

	// List method overrides.
        for (DFKlass klass : klasses) {
            klass.overrideMethods();
	}

        // Build method scopes (normal classes).
        for (DFKlass klass : klasses) {
	    assert !(klass instanceof DFLambdaKlass);
            DFMethod init = klass.getInitMethod();
            if (init != null) {
                init.buildScope();
            }
            for (DFMethod method : klass.getMethods()) {
		method.buildScope();
            }
        }

        // Build call graphs (normal classes).
	List<DFKlass> defined = new ArrayList<DFKlass>();
        for (DFKlass klass : klasses) {
	    assert !(klass instanceof DFLambdaKlass);
            DFMethod init = klass.getInitMethod();
            if (init != null) {
                init.enumRefs(defined);
            }
            for (DFMethod method : klass.getMethods()) {
		method.enumRefs(defined);
		queue.add(method);
            }
        }

	while (!defined.isEmpty()) {
	    klasses.addAll(defined);
	    List<DFKlass> defined2 = new ArrayList<DFKlass>();
            for (DFKlass klass : defined) {
                klass.overrideMethods();
            }
	    // Build method scopes (lambdas).
	    for (DFKlass klass : defined) {
		assert klass.isDefined();
		for (DFMethod method : klass.getMethods()) {
		    method.buildScope();
		}
	    }
	    // Build call graphs (lambdas).
	    for (DFKlass klass : defined) {
		assert klass.isDefined();
		for (DFMethod method : klass.getMethods()) {
		    method.enumRefs(defined2);
		    queue.add(method);
		}
	    }
	    defined = defined2;
	}

        // Expand callee refs recursively.
        while (!queue.isEmpty()) {
            DFMethod method = queue.remove();
            for (DFMethod caller : method.getCallers()) {
                if (caller.expandRefs(method)) {
                    queue.add(caller);
                }
            }
        }
    }

    // Stage5: generate graphs for each method.
    @SuppressWarnings("unchecked")
    public void buildGraphs(Counter counter, DFKlass klass, boolean strict)
        throws InvalidSyntax, EntityNotFound {
        try {
            this.startKlass(klass);
            if (!(klass instanceof DFLambdaKlass)) {
                try {
                    DFMethod init = klass.getInitMethod();
                    if (init != null) {
                        Logger.info("Stage5:", init.getSignature());
                        DFGraph graph = init.processKlassBody(counter);
                        if (graph != null) {
                            this.exportGraph(graph);
                        }
                    }
                } catch (EntityNotFound e) {
                    if (strict) throw e;
                }
            }
            for (DFMethod method : klass.getMethods()) {
                if (method.isGeneric()) continue;
                try {
                    Logger.info("Stage5:", method.getSignature());
                    DFGraph graph = method.processMethod(counter);
                    if (graph != null) {
                        this.exportGraph(graph);
                    }
                } catch (EntityNotFound e) {
                    if (strict) throw e;
                }
            }
        } finally {
            this.endKlass();
        }
    }

    /**
     * Provides a command line interface.
     *
     * Usage: java Java2DF [-o output] input.java ...
     */
    public static void main(String[] args)
        throws IOException, InvalidSyntax, EntityNotFound {

        // Parse the options.
        List<String> files = new ArrayList<String>();
	List<String> jarfiles = new ArrayList<String>();
        Set<String> processed = new HashSet<String>();
        OutputStream output = System.out;
        String sep = System.getProperty("path.separator");
        boolean strict = false;
        boolean reformat = true;
        Logger.LogLevel = 0;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--")) {
                while (i < args.length) {
                    files.add(args[++i]);
                }
	    } else if (arg.equals("-i")) {
		String path = args[++i];
		InputStream input = System.in;
		try {
		    if (!path.equals("-")) {
			input = new FileInputStream(path);
		    }
		    Logger.info("Input file:", path);
		    BufferedReader reader = new BufferedReader(
			new InputStreamReader(input));
		    while (true) {
			String line = reader.readLine();
			if (line == null) break;
			files.add(line);
		    }
		} catch (IOException e) {
		    System.err.println("Cannot open input file: "+path);
		}
            } else if (arg.equals("-v")) {
		Logger.LogLevel++;
            } else if (arg.equals("-o")) {
                String path = args[++i];
                try {
                    output = new BufferedOutputStream(new FileOutputStream(path));
                    Logger.info("Exporting:", path);
                } catch (IOException e) {
                    System.err.println("Cannot open output file: "+path);
                }
            } else if (arg.equals("-C")) {
                for (String path : args[++i].split(sep)) {
		    jarfiles.add(path);
                }
            } else if (arg.equals("-p")) {
                processed.add(args[++i]);
            } else if (arg.equals("-S")) {
                strict = true;
            } else if (arg.equals("-s")) {
                reformat = false;
            } else if (arg.equals("-a")) {
                DFMethod.setDefaultTransparent(true);
            } else if (arg.startsWith("-")) {
                System.err.println("Unknown option: "+arg);
                System.err.println(
		    "usage: Java2DF [-v] [-a] [-S] [-i input] [-o output]" +
		    " [-C jar] [-p path] [-s] [path ...]");
                System.exit(1);
                return;
            } else {
                files.add(arg);
            }
        }

	// Initialize base classes.
        DFRootTypeSpace rootSpace = new DFRootTypeSpace();
	DFBuiltinTypes.initialize(rootSpace);
	for (String path : jarfiles) {
	    rootSpace.loadJarFile(path);
	}

        // Process files.
        Java2DF converter = new Java2DF(rootSpace);
        Map<String, CompilationUnit> srcs =
            new HashMap<String, CompilationUnit>();
        for (String path : files) {
            Logger.info("Stage1:", path);
            try {
                CompilationUnit cunit = Utils.parseFile(path);
                srcs.put(path, cunit);
                converter.buildTypeSpace(path, cunit);
            } catch (IOException e) {
                Logger.error("Stage1: IOException at "+path);
                throw e;
	    } catch (InvalidSyntax e) {
                throw e;
            }
        }
        for (String path : files) {
            Logger.info("Stage2:", path);
            CompilationUnit cunit = srcs.get(path);
            converter.setTypeFinder(path, cunit);
        }
        ConsistentHashSet<DFKlass> klasses = new ConsistentHashSet<DFKlass>();
        for (String path : files) {
            Logger.info("Stage3:", path);
            CompilationUnit cunit = srcs.get(path);
	    converter.loadKlasses(path, cunit, klasses);
        }
        Logger.info("Stage4.");
	try {
	    converter.listMethods(klasses);
	} catch (InvalidSyntax e) {
	    throw e;
	}

        ByteArrayOutputStream temp = null;
        if (reformat) {
            temp = new ByteArrayOutputStream();
        }

        XmlExporter exporter = new XmlExporter((temp != null)? temp : output);
        converter.addExporter(exporter);
        Counter counter = new Counter(1);
        for (DFKlass klass : klasses) {
            if (!processed.isEmpty() && !processed.contains(klass.getFilePath())) continue;
            try {
                converter.buildGraphs(counter, klass, strict);
            } catch (EntityNotFound e) {
                Logger.error("Stage5: EntityNotFound at", klass,
                             "("+e.name+", method="+e.method+
                             ", ast="+e.ast+")");
                throw e;
            }
        }
        converter.removeExporter(exporter);
        exporter.close();

        if (temp != null) {
            temp.close();
            try {
                InputStream in = new ByteArrayInputStream(temp.toByteArray());
                Document document = Utils.readXml(in);
                in.close();
                document.setXmlStandalone(true);
                Utils.printXml(output, document);
            } catch (Exception e) {
            }
        }

        output.close();
    }
}
