//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;
import org.w3c.dom.*;


//  DFVarSpace
//  Mapping from name -> reference.
//
public class DFVarSpace {

    private DFVarSpace _root;
    private String _name;
    private DFVarSpace _parent;

    private List<DFVarSpace> _children =
	new ArrayList<DFVarSpace>();
    private Map<ASTNode, DFVarSpace> _ast2child =
	new HashMap<ASTNode, DFVarSpace>();
    private Map<String, DFVarRef> _id2ref =
	new HashMap<String, DFVarRef>();

    protected DFVarSpace(String name) {
        _root = this;
	_name = name;
        _parent = null;
    }

    private DFVarSpace(DFVarSpace parent, String name) {
        _root = parent._root;
	_name = name;
	_parent = parent;
    }

    public DFVarSpace(DFVarSpace parent, SimpleName name) {
        _root = parent._root;
	_name = name.getIdentifier();
	_parent = parent;
    }

    @Override
    public String toString() {
	return ("<DFVarSpace("+this.getFullName()+")>");
    }

    public Element toXML(Document document, DFNode[] nodes) {
	Element elem = document.createElement("scope");
	elem.setAttribute("name", this.getFullName());
	for (DFVarSpace child : this.getChildren()) {
	    elem.appendChild(child.toXML(document, nodes));
	}
	for (DFNode node : nodes) {
	    if (node.getSpace() == this) {
		elem.appendChild(node.toXML(document));
	    }
	}
	return elem;
    }

    public String getBaseName() {
        return _name;
    }

    public String getFullName() {
        if (_parent == null) {
            return _name;
        } else {
            return _parent.getFullName()+"."+_name;
        }
    }

    public DFVarSpace addChild(String basename, ASTNode ast) {
	String id = basename + _children.size();
        Utils.logit("DFVarSpace.addChild: "+this+": "+id);
	DFVarSpace space = new DFVarSpace(this, id);
	_children.add(space);
	_ast2child.put(ast, space);
	return space;
    }

    public DFVarSpace getChildByAST(ASTNode ast) {
	return _ast2child.get(ast);
    }

    public DFVarSpace[] getChildren() {
	DFVarSpace[] spaces = new DFVarSpace[_children.size()];
	_children.toArray(spaces);
	return spaces;
    }

    protected DFVarRef addRef(String id, DFType type) {
	DFVarRef ref = _id2ref.get(id);
	if (ref == null) {
            ref = new DFVarRef(this, id, type);
            _id2ref.put(id, ref);
        }
	return ref;
    }

    protected DFVarRef lookupRef(String id) {
	DFVarRef ref = _id2ref.get(id);
	if (ref != null) {
	    return ref;
	} else if (_parent != null) {
	    return _parent.lookupRef(id);
	} else {
	    return null;
	}
    }

    protected DFVarRef lookupField(String id) {
        assert(_parent != null);
	return _parent.lookupField(id);
    }

    public DFVarRef lookupVar(SimpleName name) {
        return this.lookupRef(name.getIdentifier());
    }

    public DFVarRef lookupVarOrField(SimpleName name) {
        // try local variables first.
        DFVarRef ref = this.lookupRef(name.getIdentifier());
	if (ref != null) return ref;
        // try field names.
        ref = this.lookupField(name.getIdentifier());
        if (ref != null) return ref;
        // fallback...
        return this.addRef(name.getIdentifier(), null);
    }

    public DFVarRef lookupReturn() {
	return this.lookupRef("#return");
    }

    public DFVarRef lookupException() {
	return this.lookupRef("#exception");
    }

    public DFVarRef lookupArray(DFNode array) {
        DFType type = array.getType();
        DFVarRef ref;
        if (type instanceof DFArrayType) {
            DFType elemType = ((DFArrayType)type).getElemType();
            ref = _root.addRef("#array:"+elemType.getName(), elemType);
        } else {
            ref = _root.addRef("#array:?", null);
        }
        return ref;
    }

    public DFVarRef lookupThis() {
        assert(_parent != null);
	return _parent.lookupThis();
    }

    private DFVarRef addVar(SimpleName name, DFType type) {
        Utils.logit("DFVarSpace.addVar: "+this+": "+name+" -> "+type);
        return this.addRef(name.getIdentifier(), type);
    }

    /**
     * Lists all the variables defined inside a method.
     */
    @SuppressWarnings("unchecked")
    public void build(DFTypeFinder finder, MethodDeclaration methodDecl)
	throws UnsupportedSyntax, EntityNotFound {
        //Utils.logit("DFVarSpace.build: "+this);
        Type returnType = methodDecl.getReturnType2();
        DFType type = (returnType == null)? null : finder.resolve(returnType);
	this.addRef("#return", type);
	this.addRef("#exception", null);
        for (SingleVariableDeclaration decl :
                 (List<SingleVariableDeclaration>) methodDecl.parameters()) {
            // XXX Ignore modifiers.
            DFType paramType = finder.resolve(decl.getType());
            this.addVar(decl.getName(), paramType);
        }
        this.build(finder, methodDecl.getBody());
    }

    @SuppressWarnings("unchecked")
    public void build(DFTypeFinder finder, Statement ast)
	throws UnsupportedSyntax, EntityNotFound {

	if (ast instanceof AssertStatement) {

	} else if (ast instanceof Block) {
	    Block block = (Block)ast;
	    DFVarSpace childSpace = this.addChild("b", ast);
	    for (Statement stmt :
		     (List<Statement>) block.statements()) {
		childSpace.build(finder, stmt);
	    }

	} else if (ast instanceof EmptyStatement) {

	} else if (ast instanceof VariableDeclarationStatement) {
	    VariableDeclarationStatement varStmt =
		(VariableDeclarationStatement)ast;
	    // XXX Ignore modifiers.
	    DFType varType = finder.resolve(varStmt.getType());
	    for (VariableDeclarationFragment frag :
		     (List<VariableDeclarationFragment>) varStmt.fragments()) {
		this.addVar(frag.getName(), varType);
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
	    DFVarSpace childSpace = this.addChild("switch", ast);
	    Expression expr = switchStmt.getExpression();
	    childSpace.build(finder, expr);
	    for (Statement stmt :
		     (List<Statement>) switchStmt.statements()) {
		childSpace.build(finder, stmt);
	    }

	} else if (ast instanceof SwitchCase) {
	    SwitchCase switchCase = (SwitchCase)ast;
	    Expression expr = switchCase.getExpression();
	    if (expr != null) {
		this.build(finder, expr);
	    }

	} else if (ast instanceof WhileStatement) {
	    WhileStatement whileStmt = (WhileStatement)ast;
	    DFVarSpace childSpace = this.addChild("while", ast);
	    Expression expr = whileStmt.getExpression();
	    childSpace.build(finder, expr);
	    Statement stmt = whileStmt.getBody();
	    childSpace.build(finder, stmt);

	} else if (ast instanceof DoStatement) {
	    DoStatement doStmt = (DoStatement)ast;
	    DFVarSpace childSpace = this.addChild("do", ast);
	    Statement stmt = doStmt.getBody();
	    childSpace.build(finder, stmt);
	    Expression expr = doStmt.getExpression();
	    childSpace.build(finder, expr);

	} else if (ast instanceof ForStatement) {
	    ForStatement forStmt = (ForStatement)ast;
	    DFVarSpace childSpace = this.addChild("for", ast);
	    for (Expression init :
		     (List<Expression>) forStmt.initializers()) {
		childSpace.build(finder, init);
	    }
	    Expression expr = forStmt.getExpression();
	    if (expr != null) {
		childSpace.build(finder, expr);
	    }
	    Statement stmt = forStmt.getBody();
	    childSpace.build(finder, stmt);
	    for (Expression update :
		     (List<Expression>) forStmt.updaters()) {
		childSpace.build(finder, update);
	    }

	} else if (ast instanceof EnhancedForStatement) {
	    EnhancedForStatement eForStmt = (EnhancedForStatement)ast;
	    DFVarSpace childSpace = this.addChild("efor", ast);
	    SingleVariableDeclaration decl = eForStmt.getParameter();
	    // XXX Ignore modifiers.
	    DFType varType = finder.resolve(decl.getType());
	    childSpace.addVar(decl.getName(), varType);
	    Expression expr = eForStmt.getExpression();
	    if (expr != null) {
		childSpace.build(finder, expr);
	    }
	    Statement stmt = eForStmt.getBody();
	    childSpace.build(finder, stmt);

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
	    Block block = tryStmt.getBody();
	    this.build(finder, block);
	    for (CatchClause cc :
		     (List<CatchClause>) tryStmt.catchClauses()) {
		DFVarSpace childSpace = this.addChild("catch", cc);
		SingleVariableDeclaration decl = cc.getException();
		// XXX Ignore modifiers.
		DFType varType = finder.resolve(decl.getType());
		childSpace.addVar(decl.getName(), varType);
		childSpace.build(finder, cc.getBody());
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
	throws UnsupportedSyntax, EntityNotFound {

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
		this.addVar(frag.getName(), varType);
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
	throws UnsupportedSyntax, EntityNotFound {

	if (ast instanceof Name) {

	} else if (ast instanceof ArrayAccess) {
	    ArrayAccess aa = (ArrayAccess)ast;
	    this.build(finder, aa.getArray());
	    this.build(finder, aa.getIndex());

	} else if (ast instanceof FieldAccess) {
	    FieldAccess fa = (FieldAccess)ast;
	    SimpleName fieldName = fa.getName();
	    this.build(finder, fa.getExpression());

	} else {
	    throw new UnsupportedSyntax(ast);

	}
    }

    // dump: for debugging.
    public void dump() {
	dump(System.err, "");
    }
    public void dump(PrintStream out, String indent) {
	out.println(indent+this.getFullName()+" {");
	String i2 = indent + "  ";
	this.dumpContents(out, i2);
	for (DFVarSpace space : _children) {
	    space.dump(out, i2);
	}
	out.println(indent+"}");
    }
    public void dumpContents(PrintStream out, String indent) {
	for (DFVarRef ref : _id2ref.values()) {
	    out.println(indent+"defined: "+ref);
	}
    }
}
