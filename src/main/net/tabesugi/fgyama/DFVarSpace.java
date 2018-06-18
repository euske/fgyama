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
	return ("<DFVarSpace("+this.getName()+")>");
    }

    public Element toXML(Document document, DFNode[] nodes) {
	Element elem = document.createElement("scope");
	elem.setAttribute("name", this.getName());
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

    public String getName() {
        if (_parent == null) {
            return _name;
        } else {
            return _parent.getName()+"."+_name;
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
    public void build(DFTypeSpace typeSpace, MethodDeclaration methodDecl)
	throws UnsupportedSyntax, EntityNotFound {
        //Utils.logit("DFVarSpace.build: "+this);
        Type returnType = methodDecl.getReturnType2();
        DFType type = (returnType == null)? null : typeSpace.resolve(returnType);
	this.addRef("#return", type);
	this.addRef("#exception", null);
        for (SingleVariableDeclaration decl :
                 (List<SingleVariableDeclaration>) methodDecl.parameters()) {
            // XXX Ignore modifiers.
            DFType paramType = typeSpace.resolve(decl.getType());
            this.addVar(decl.getName(), paramType);
        }
        this.build(typeSpace, methodDecl.getBody());
    }

    @SuppressWarnings("unchecked")
    public void build(DFTypeSpace typeSpace, Statement ast)
	throws UnsupportedSyntax, EntityNotFound {

	if (ast instanceof AssertStatement) {

	} else if (ast instanceof Block) {
	    Block block = (Block)ast;
	    DFVarSpace childSpace = this.addChild("b", ast);
	    for (Statement stmt :
		     (List<Statement>) block.statements()) {
		childSpace.build(typeSpace, stmt);
	    }

	} else if (ast instanceof EmptyStatement) {

	} else if (ast instanceof VariableDeclarationStatement) {
	    VariableDeclarationStatement varStmt =
		(VariableDeclarationStatement)ast;
	    // XXX Ignore modifiers.
	    DFType varType = typeSpace.resolve(varStmt.getType());
	    for (VariableDeclarationFragment frag :
		     (List<VariableDeclarationFragment>) varStmt.fragments()) {
		this.addVar(frag.getName(), varType);
		Expression expr = frag.getInitializer();
		if (expr != null) {
		    this.build(typeSpace, expr);
		}
	    }

	} else if (ast instanceof ExpressionStatement) {
	    ExpressionStatement exprStmt = (ExpressionStatement)ast;
	    Expression expr = exprStmt.getExpression();
	    this.build(typeSpace, expr);

	} else if (ast instanceof ReturnStatement) {
	    ReturnStatement returnStmt = (ReturnStatement)ast;
	    Expression expr = returnStmt.getExpression();
	    if (expr != null) {
		this.build(typeSpace, expr);
	    }

	} else if (ast instanceof IfStatement) {
	    IfStatement ifStmt = (IfStatement)ast;
	    Expression expr = ifStmt.getExpression();
	    this.build(typeSpace, expr);
	    Statement thenStmt = ifStmt.getThenStatement();
	    this.build(typeSpace, thenStmt);
	    Statement elseStmt = ifStmt.getElseStatement();
	    if (elseStmt != null) {
		this.build(typeSpace, elseStmt);
	    }

	} else if (ast instanceof SwitchStatement) {
	    SwitchStatement switchStmt = (SwitchStatement)ast;
	    DFVarSpace childSpace = this.addChild("switch", ast);
	    Expression expr = switchStmt.getExpression();
	    childSpace.build(typeSpace, expr);
	    for (Statement stmt :
		     (List<Statement>) switchStmt.statements()) {
		childSpace.build(typeSpace, stmt);
	    }

	} else if (ast instanceof SwitchCase) {
	    SwitchCase switchCase = (SwitchCase)ast;
	    Expression expr = switchCase.getExpression();
	    if (expr != null) {
		this.build(typeSpace, expr);
	    }

	} else if (ast instanceof WhileStatement) {
	    WhileStatement whileStmt = (WhileStatement)ast;
	    DFVarSpace childSpace = this.addChild("while", ast);
	    Expression expr = whileStmt.getExpression();
	    childSpace.build(typeSpace, expr);
	    Statement stmt = whileStmt.getBody();
	    childSpace.build(typeSpace, stmt);

	} else if (ast instanceof DoStatement) {
	    DoStatement doStmt = (DoStatement)ast;
	    DFVarSpace childSpace = this.addChild("do", ast);
	    Statement stmt = doStmt.getBody();
	    childSpace.build(typeSpace, stmt);
	    Expression expr = doStmt.getExpression();
	    childSpace.build(typeSpace, expr);

	} else if (ast instanceof ForStatement) {
	    ForStatement forStmt = (ForStatement)ast;
	    DFVarSpace childSpace = this.addChild("for", ast);
	    for (Expression init :
		     (List<Expression>) forStmt.initializers()) {
		childSpace.build(typeSpace, init);
	    }
	    Expression expr = forStmt.getExpression();
	    if (expr != null) {
		childSpace.build(typeSpace, expr);
	    }
	    Statement stmt = forStmt.getBody();
	    childSpace.build(typeSpace, stmt);
	    for (Expression update :
		     (List<Expression>) forStmt.updaters()) {
		childSpace.build(typeSpace, update);
	    }

	} else if (ast instanceof EnhancedForStatement) {
	    EnhancedForStatement eForStmt = (EnhancedForStatement)ast;
	    DFVarSpace childSpace = this.addChild("efor", ast);
	    SingleVariableDeclaration decl = eForStmt.getParameter();
	    // XXX Ignore modifiers.
	    DFType varType = typeSpace.resolve(decl.getType());
	    childSpace.addVar(decl.getName(), varType);
	    Expression expr = eForStmt.getExpression();
	    if (expr != null) {
		childSpace.build(typeSpace, expr);
	    }
	    Statement stmt = eForStmt.getBody();
	    childSpace.build(typeSpace, stmt);

	} else if (ast instanceof BreakStatement) {

	} else if (ast instanceof ContinueStatement) {

	} else if (ast instanceof LabeledStatement) {
	    LabeledStatement labeledStmt = (LabeledStatement)ast;
	    SimpleName labelName = labeledStmt.getLabel();
	    String label = labelName.getIdentifier();
	    Statement stmt = labeledStmt.getBody();
	    this.build(typeSpace, stmt);

	} else if (ast instanceof SynchronizedStatement) {
	    SynchronizedStatement syncStmt = (SynchronizedStatement)ast;
	    Block block = syncStmt.getBody();
	    this.build(typeSpace, block);

	} else if (ast instanceof TryStatement) {
	    TryStatement tryStmt = (TryStatement)ast;
	    Block block = tryStmt.getBody();
	    this.build(typeSpace, block);
	    for (CatchClause cc :
		     (List<CatchClause>) tryStmt.catchClauses()) {
		DFVarSpace childSpace = this.addChild("catch", cc);
		SingleVariableDeclaration decl = cc.getException();
		// XXX Ignore modifiers.
		DFType varType = typeSpace.resolve(decl.getType());
		childSpace.addVar(decl.getName(), varType);
		childSpace.build(typeSpace, cc.getBody());
	    }
	    Block finBlock = tryStmt.getFinally();
	    if (finBlock != null) {
		this.build(typeSpace, finBlock);
	    }

	} else if (ast instanceof ThrowStatement) {
	    ThrowStatement throwStmt = (ThrowStatement)ast;
	    Expression expr = throwStmt.getExpression();
	    if (expr != null) {
		this.build(typeSpace, expr);
	    }

	} else if (ast instanceof ConstructorInvocation) {
	    ConstructorInvocation ci = (ConstructorInvocation)ast;
	    for (Expression expr :
		     (List<Expression>) ci.arguments()) {
		this.build(typeSpace, expr);
	    }

	} else if (ast instanceof SuperConstructorInvocation) {
	    SuperConstructorInvocation sci = (SuperConstructorInvocation)ast;
	    for (Expression expr :
		     (List<Expression>) sci.arguments()) {
		this.build(typeSpace, expr);
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
    public void build(DFTypeSpace typeSpace, Expression ast)
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
	    this.build(typeSpace, operand);
	    if (op == PrefixExpression.Operator.INCREMENT ||
		op == PrefixExpression.Operator.DECREMENT) {
		this.buildLeft(typeSpace, operand);
	    }

	} else if (ast instanceof PostfixExpression) {
	    PostfixExpression postfix = (PostfixExpression)ast;
	    PostfixExpression.Operator op = postfix.getOperator();
	    Expression operand = postfix.getOperand();
	    this.build(typeSpace, operand);
	    if (op == PostfixExpression.Operator.INCREMENT ||
		op == PostfixExpression.Operator.DECREMENT) {
		this.buildLeft(typeSpace, operand);
	    }

	} else if (ast instanceof InfixExpression) {
	    InfixExpression infix = (InfixExpression)ast;
	    InfixExpression.Operator op = infix.getOperator();
	    Expression loperand = infix.getLeftOperand();
	    this.build(typeSpace, loperand);
	    Expression roperand = infix.getRightOperand();
	    this.build(typeSpace, roperand);

	} else if (ast instanceof ParenthesizedExpression) {
	    ParenthesizedExpression paren = (ParenthesizedExpression)ast;
	    this.build(typeSpace, paren.getExpression());

	} else if (ast instanceof Assignment) {
	    Assignment assn = (Assignment)ast;
	    Assignment.Operator op = assn.getOperator();
	    this.buildLeft(typeSpace, assn.getLeftHandSide());
	    if (op != Assignment.Operator.ASSIGN) {
		this.build(typeSpace, assn.getLeftHandSide());
	    }
	    this.build(typeSpace, assn.getRightHandSide());

	} else if (ast instanceof VariableDeclarationExpression) {
	    VariableDeclarationExpression decl = (VariableDeclarationExpression)ast;
	    // XXX Ignore modifiers.
	    DFType varType = typeSpace.resolve(decl.getType());
	    for (VariableDeclarationFragment frag :
		     (List<VariableDeclarationFragment>) decl.fragments()) {
		this.addVar(frag.getName(), varType);
		Expression expr = frag.getInitializer();
		if (expr != null) {
		    this.build(typeSpace, expr);
		}
	    }

	} else if (ast instanceof MethodInvocation) {
	    MethodInvocation invoke = (MethodInvocation)ast;
	    Expression expr = invoke.getExpression();
	    if (expr != null) {
		this.build(typeSpace, expr);
	    }
	    for (Expression arg :
		     (List<Expression>) invoke.arguments()) {
		this.build(typeSpace, arg);
	    }

	} else if (ast instanceof SuperMethodInvocation) {
	    SuperMethodInvocation si = (SuperMethodInvocation)ast;
	    for (Expression arg :
		     (List<Expression>) si.arguments()) {
		this.build(typeSpace, arg);
	    }

	} else if (ast instanceof ArrayCreation) {
	    ArrayCreation ac = (ArrayCreation)ast;
	    for (Expression dim :
		     (List<Expression>) ac.dimensions()) {
		this.build(typeSpace, dim);
	    }
	    ArrayInitializer init = ac.getInitializer();
	    if (init != null) {
		this.build(typeSpace, init);
	    }

	} else if (ast instanceof ArrayInitializer) {
	    ArrayInitializer init = (ArrayInitializer)ast;
	    for (Expression expr :
		     (List<Expression>) init.expressions()) {
		this.build(typeSpace, expr);
	    }

	} else if (ast instanceof ArrayAccess) {
	    ArrayAccess aa = (ArrayAccess)ast;
	    this.build(typeSpace, aa.getArray());
	    this.build(typeSpace, aa.getIndex());

	} else if (ast instanceof FieldAccess) {
	    FieldAccess fa = (FieldAccess)ast;
	    SimpleName fieldName = fa.getName();
	    this.build(typeSpace, fa.getExpression());

	} else if (ast instanceof SuperFieldAccess) {
	    SuperFieldAccess sfa = (SuperFieldAccess)ast;
	    SimpleName fieldName = sfa.getName();

	} else if (ast instanceof CastExpression) {
	    CastExpression cast = (CastExpression)ast;
	    this.build(typeSpace, cast.getExpression());

	} else if (ast instanceof ClassInstanceCreation) {
	    ClassInstanceCreation cstr = (ClassInstanceCreation)ast;
	    Expression expr = cstr.getExpression();
	    if (expr != null) {
		this.build(typeSpace, expr);
	    }
	    for (Expression arg :
		     (List<Expression>) cstr.arguments()) {
		this.build(typeSpace, arg);
	    }
	    // XXX Ignored getAnonymousClassDeclaration().

	} else if (ast instanceof ConditionalExpression) {
	    ConditionalExpression cond = (ConditionalExpression)ast;
	    this.build(typeSpace, cond.getExpression());
	    this.build(typeSpace, cond.getThenExpression());
	    this.build(typeSpace, cond.getElseExpression());

	} else if (ast instanceof InstanceofExpression) {
	    InstanceofExpression instof = (InstanceofExpression)ast;
	    this.build(typeSpace, instof.getLeftOperand());

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
    public void buildLeft(DFTypeSpace typeSpace, Expression ast)
	throws UnsupportedSyntax, EntityNotFound {

	if (ast instanceof Name) {

	} else if (ast instanceof ArrayAccess) {
	    ArrayAccess aa = (ArrayAccess)ast;
	    this.build(typeSpace, aa.getArray());
	    this.build(typeSpace, aa.getIndex());

	} else if (ast instanceof FieldAccess) {
	    FieldAccess fa = (FieldAccess)ast;
	    SimpleName fieldName = fa.getName();
	    this.build(typeSpace, fa.getExpression());

	} else {
	    throw new UnsupportedSyntax(ast);

	}
    }

    // dump: for debugging.
    public void dump() {
	dump(System.err, "");
    }
    public void dump(PrintStream out, String indent) {
	out.println(indent+getName()+" {");
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
