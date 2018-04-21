//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;
import org.w3c.dom.*;


//  DFVarScope
//  Mapping from name -> reference.
//
public class DFVarScope {

    private DFVarScope _root;
    private String _name;
    private DFVarScope _parent;

    private List<DFVarScope> _children =
	new ArrayList<DFVarScope>();
    private Map<ASTNode, DFVarScope> _ast2child =
	new HashMap<ASTNode, DFVarScope>();
    private Map<String, DFVarRef> _id2ref =
	new HashMap<String, DFVarRef>();

    protected DFVarScope(String name) {
        _root = this;
	_name = name;
        _parent = null;
    }

    public DFVarScope(DFVarScope parent, String name) {
        _root = parent._root;
	_name = name;
	_parent = parent;
    }

    @Override
    public String toString() {
	return ("<DFVarScope("+this.getName()+")>");
    }

    public Element toXML(Document document, DFNode[] nodes) {
	Element elem = document.createElement("scope");
	elem.setAttribute("name", this.getName());
	for (DFVarScope child : this.getChildren()) {
	    elem.appendChild(child.toXML(document, nodes));
	}
	for (DFNode node : nodes) {
	    if (node.getScope() == this) {
		elem.appendChild(node.toXML(document));
	    }
	}
	return elem;
    }

    public String getName() {
        if (_parent == null) {
            return _name;
        } else {
            return _parent.getName()+"."+_name;
        }
    }

    public DFVarScope addChild(String basename, ASTNode ast) {
	int id = _children.size();
	String name = basename+id;
	DFVarScope scope = new DFVarScope(this, name);
	_children.add(scope);
	_ast2child.put(ast, scope);
	return scope;
    }

    public DFVarScope getChildByAST(ASTNode ast) {
	return _ast2child.get(ast);
    }

    public DFVarScope[] getChildren() {
	DFVarScope[] scopes = new DFVarScope[_children.size()];
	_children.toArray(scopes);
	return scopes;
    }

    protected DFVarRef addRef(String id, DFTypeRef type) {
	DFVarRef ref = _id2ref.get(id);
	if (ref == null) {
            ref = new DFVarRef(this, id, type);
            _id2ref.put(id, ref);
        }
	return ref;
    }

    public DFVarRef addRef(SimpleName name, DFTypeRef type) {
        return this.addRef(name.getIdentifier(), type);
    }

    protected DFVarRef lookupRef(String id, boolean add) {
	DFVarRef ref = _id2ref.get(id);
	if (ref != null) {
	    return ref;
	} else if (_parent != null) {
	    return _parent.lookupRef(id, add);
	} else if (add) {
	    return this.addRef(id, null);
	} else {
	    return null;
	}
    }

    public DFVarRef lookupVar(SimpleName name, boolean add) {
        return this.lookupRef(name.getIdentifier(), add);
    }

    public DFVarRef lookupVarOrField(SimpleName name) {
	DFVarRef ref = this.lookupVar(name, false);
	if (ref != null) return ref;
        ref = this.lookupField(name, false);
        if (ref != null) return ref;
	return this.lookupVar(name, true);
    }

    public DFVarRef addReturn(DFTypeRef returnType) {
	return this.addRef("#return", returnType);
    }

    public DFVarRef lookupReturn() {
	return this.lookupRef("#return", false);
    }

    public DFVarRef lookupArray() {
	return _root.addRef("#array", null);
    }

    public DFVarRef lookupThis() {
        assert(_parent != null);
	return _parent.lookupThis();
    }

    public DFVarRef lookupField(SimpleName name) {
	return this.lookupField(name, true);
    }
    public DFVarRef lookupField(SimpleName name, boolean add) {
        assert(_parent != null);
        return _parent.lookupField(name, add);
    }

    public DFMethod lookupMethod(SimpleName name) {
        assert(_parent != null);
        return _parent.lookupMethod(name);
    }

    // dump: for debugging.
    public void dump() {
	dump(System.err, "");
    }
    public void dump(PrintStream out, String indent) {
	out.println(indent+getName()+" {");
	String i2 = indent + "  ";
	this.dumpContents(out, i2);
	for (DFVarScope scope : _children) {
	    scope.dump(out, i2);
	}
	out.println(indent+"}");
    }
    public void dumpContents(PrintStream out, String indent) {
	for (DFVarRef ref : _id2ref.values()) {
	    out.println(indent+"defined: "+ref);
	}
    }

    /**
     * Lists all the variables defined inside a block.
     */
    @SuppressWarnings("unchecked")
    public void build(DFFrame frame, Statement ast)
	throws UnsupportedSyntax {

	if (ast instanceof AssertStatement) {

	} else if (ast instanceof Block) {
	    Block block = (Block)ast;
	    DFVarScope childScope = this.addChild("b", ast);
	    for (Statement stmt :
		     (List<Statement>) block.statements()) {
		childScope.build(frame, stmt);
	    }

	} else if (ast instanceof EmptyStatement) {

	} else if (ast instanceof VariableDeclarationStatement) {
	    VariableDeclarationStatement varStmt =
		(VariableDeclarationStatement)ast;
	    // XXX Ignore modifiers and dimensions.
	    DFTypeRef varType = new DFTypeRef(varStmt.getType());
	    for (VariableDeclarationFragment frag :
		     (List<VariableDeclarationFragment>) varStmt.fragments()) {
		this.addRef(frag.getName(), varType);
		Expression expr = frag.getInitializer();
		if (expr != null) {
		    this.build(frame, expr);
		}
	    }

	} else if (ast instanceof ExpressionStatement) {
	    ExpressionStatement exprStmt = (ExpressionStatement)ast;
	    Expression expr = exprStmt.getExpression();
	    this.build(frame, expr);

	} else if (ast instanceof ReturnStatement) {
	    ReturnStatement returnStmt = (ReturnStatement)ast;
	    Expression expr = returnStmt.getExpression();
	    if (expr != null) {
		this.build(frame, expr);
	    }

	} else if (ast instanceof IfStatement) {
	    IfStatement ifStmt = (IfStatement)ast;
	    Expression expr = ifStmt.getExpression();
	    this.build(frame, expr);
	    Statement thenStmt = ifStmt.getThenStatement();
	    this.build(frame, thenStmt);
	    Statement elseStmt = ifStmt.getElseStatement();
	    if (elseStmt != null) {
		this.build(frame, elseStmt);
	    }

	} else if (ast instanceof SwitchStatement) {
	    SwitchStatement switchStmt = (SwitchStatement)ast;
	    DFVarScope childScope = this.addChild("switch", ast);
	    DFFrame childFrame = frame.addChild(null, ast);
	    Expression expr = switchStmt.getExpression();
	    childScope.build(frame, expr);
	    for (Statement stmt :
		     (List<Statement>) switchStmt.statements()) {
		childScope.build(childFrame, stmt);
	    }

	} else if (ast instanceof SwitchCase) {
	    SwitchCase switchCase = (SwitchCase)ast;
	    Expression expr = switchCase.getExpression();
	    if (expr != null) {
		this.build(frame, expr);
	    }

	} else if (ast instanceof WhileStatement) {
	    WhileStatement whileStmt = (WhileStatement)ast;
	    DFVarScope childScope = this.addChild("while", ast);
	    DFFrame childFrame = frame.addChild(null, ast);
	    Expression expr = whileStmt.getExpression();
	    childScope.build(frame, expr);
	    Statement stmt = whileStmt.getBody();
	    childScope.build(childFrame, stmt);

	} else if (ast instanceof DoStatement) {
	    DoStatement doStmt = (DoStatement)ast;
	    DFVarScope childScope = this.addChild("do", ast);
	    DFFrame childFrame = frame.addChild(null, ast);
	    Statement stmt = doStmt.getBody();
	    childScope.build(childFrame, stmt);
	    Expression expr = doStmt.getExpression();
	    childScope.build(frame, expr);

	} else if (ast instanceof ForStatement) {
	    ForStatement forStmt = (ForStatement)ast;
	    DFVarScope childScope = this.addChild("for", ast);
	    DFFrame childFrame = frame.addChild(null, ast);
	    for (Expression init :
		     (List<Expression>) forStmt.initializers()) {
		childScope.build(frame, init);
	    }
	    Expression expr = forStmt.getExpression();
	    if (expr != null) {
		childScope.build(childFrame, expr);
	    }
	    Statement stmt = forStmt.getBody();
	    childScope.build(childFrame, stmt);
	    for (Expression update :
		     (List<Expression>) forStmt.updaters()) {
		childScope.build(childFrame, update);
	    }

	} else if (ast instanceof EnhancedForStatement) {
	    EnhancedForStatement eForStmt = (EnhancedForStatement)ast;
	    DFVarScope childScope = this.addChild("efor", ast);
	    DFFrame childFrame = frame.addChild(null, ast);
	    SingleVariableDeclaration decl = eForStmt.getParameter();
	    // XXX Ignore modifiers and dimensions.
	    DFTypeRef varType = new DFTypeRef(decl.getType());
	    childScope.addRef(decl.getName(), varType);
	    Expression expr = eForStmt.getExpression();
	    if (expr != null) {
		childScope.build(frame, expr);
	    }
	    Statement stmt = eForStmt.getBody();
	    childScope.build(childFrame, stmt);

	} else if (ast instanceof BreakStatement) {

	} else if (ast instanceof ContinueStatement) {

	} else if (ast instanceof LabeledStatement) {
	    LabeledStatement labeledStmt = (LabeledStatement)ast;
	    SimpleName labelName = labeledStmt.getLabel();
	    String label = labelName.getIdentifier();
	    DFFrame childFrame = frame.addChild(label, ast);
	    Statement stmt = labeledStmt.getBody();
	    this.build(childFrame, stmt);

	} else if (ast instanceof SynchronizedStatement) {
	    SynchronizedStatement syncStmt = (SynchronizedStatement)ast;
	    Block block = syncStmt.getBody();
	    this.build(frame, block);

	} else if (ast instanceof TryStatement) {
	    TryStatement tryStmt = (TryStatement)ast;
	    Block block = tryStmt.getBody();
	    DFFrame tryFrame = frame.addChild(DFFrame.TRY, ast);
	    this.build(tryFrame, block);
	    for (CatchClause cc :
		     (List<CatchClause>) tryStmt.catchClauses()) {
		DFVarScope childScope = this.addChild("catch", cc);
		SingleVariableDeclaration decl = cc.getException();
		// XXX Ignore modifiers and dimensions.
		DFTypeRef varType = new DFTypeRef(decl.getType());
		childScope.addRef(decl.getName(), varType);
		childScope.build(frame, cc.getBody());
	    }
	    Block finBlock = tryStmt.getFinally();
	    if (finBlock != null) {
		this.build(frame, finBlock);
	    }

	} else if (ast instanceof ThrowStatement) {
	    ThrowStatement throwStmt = (ThrowStatement)ast;
	    Expression expr = throwStmt.getExpression();
	    if (expr != null) {
		this.build(frame, expr);
	    }

	} else if (ast instanceof ConstructorInvocation) {
	    ConstructorInvocation ci = (ConstructorInvocation)ast;
	    for (Expression expr :
		     (List<Expression>) ci.arguments()) {
		this.build(frame, expr);
	    }

	} else if (ast instanceof SuperConstructorInvocation) {
	    SuperConstructorInvocation sci = (SuperConstructorInvocation)ast;
	    for (Expression expr :
		     (List<Expression>) sci.arguments()) {
		this.build(frame, expr);
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
    public void build(DFFrame frame, Expression ast)
	throws UnsupportedSyntax {

	if (ast instanceof Annotation) {

	} else if (ast instanceof Name) {
            // XXX incomplete ref!
	    Name name = (Name)ast;
	    if (name.isSimpleName()) {
		DFVarRef ref = this.lookupVarOrField((SimpleName)name);
		frame.addInput(ref);
	    } else {
		// QualifiedName == FieldAccess
		QualifiedName qn = (QualifiedName)name;
		SimpleName fieldName = qn.getName();
		DFVarRef ref = this.lookupField(fieldName);
		frame.addInput(ref);
	    }

	} else if (ast instanceof ThisExpression) {
	    DFVarRef ref = this.lookupThis();
	    frame.addInput(ref);

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
	    this.build(frame, operand);
	    if (op == PrefixExpression.Operator.INCREMENT ||
		op == PrefixExpression.Operator.DECREMENT) {
		this.buildLeft(frame, operand);
	    }

	} else if (ast instanceof PostfixExpression) {
	    PostfixExpression postfix = (PostfixExpression)ast;
	    PostfixExpression.Operator op = postfix.getOperator();
	    Expression operand = postfix.getOperand();
	    this.build(frame, operand);
	    if (op == PostfixExpression.Operator.INCREMENT ||
		op == PostfixExpression.Operator.DECREMENT) {
		this.buildLeft(frame, operand);
	    }

	} else if (ast instanceof InfixExpression) {
	    InfixExpression infix = (InfixExpression)ast;
	    InfixExpression.Operator op = infix.getOperator();
	    Expression loperand = infix.getLeftOperand();
	    this.build(frame, loperand);
	    Expression roperand = infix.getRightOperand();
	    this.build(frame, roperand);

	} else if (ast instanceof ParenthesizedExpression) {
	    ParenthesizedExpression paren = (ParenthesizedExpression)ast;
	    this.build(frame, paren.getExpression());

	} else if (ast instanceof Assignment) {
	    Assignment assn = (Assignment)ast;
	    Assignment.Operator op = assn.getOperator();
	    this.buildLeft(frame, assn.getLeftHandSide());
	    if (op != Assignment.Operator.ASSIGN) {
		this.build(frame, assn.getLeftHandSide());
	    }
	    this.build(frame, assn.getRightHandSide());

	} else if (ast instanceof VariableDeclarationExpression) {
	    VariableDeclarationExpression decl = (VariableDeclarationExpression)ast;
	    // XXX Ignore modifiers and dimensions.
	    DFTypeRef varType = new DFTypeRef(decl.getType());
	    for (VariableDeclarationFragment frag :
		     (List<VariableDeclarationFragment>) decl.fragments()) {
		DFVarRef ref = this.addRef(frag.getName(), varType);
		frame.addOutput(ref);
		Expression expr = frag.getInitializer();
		if (expr != null) {
		    this.build(frame, expr);
		}
	    }

	} else if (ast instanceof MethodInvocation) {
	    MethodInvocation invoke = (MethodInvocation)ast;
	    Expression expr = invoke.getExpression();
	    if (expr != null) {
		this.build(frame, expr);
	    }
	    for (Expression arg :
		     (List<Expression>) invoke.arguments()) {
		this.build(frame, arg);
	    }

	} else if (ast instanceof SuperMethodInvocation) {
	    SuperMethodInvocation si = (SuperMethodInvocation)ast;
	    for (Expression arg :
		     (List<Expression>) si.arguments()) {
		this.build(frame, arg);
	    }

	} else if (ast instanceof ArrayCreation) {
	    ArrayCreation ac = (ArrayCreation)ast;
	    for (Expression dim :
		     (List<Expression>) ac.dimensions()) {
		this.build(frame, dim);
	    }
	    ArrayInitializer init = ac.getInitializer();
	    if (init != null) {
		this.build(frame, init);
	    }

	} else if (ast instanceof ArrayInitializer) {
	    ArrayInitializer init = (ArrayInitializer)ast;
	    for (Expression expr :
		     (List<Expression>) init.expressions()) {
		this.build(frame, expr);
	    }

	} else if (ast instanceof ArrayAccess) {
	    ArrayAccess aa = (ArrayAccess)ast;
	    this.build(frame, aa.getArray());
	    this.build(frame, aa.getIndex());
	    DFVarRef ref = this.lookupArray();
	    frame.addInput(ref);

	} else if (ast instanceof FieldAccess) {
	    FieldAccess fa = (FieldAccess)ast;
	    SimpleName fieldName = fa.getName();
	    this.build(frame, fa.getExpression());
	    DFVarRef ref = this.lookupField(fieldName);
	    frame.addInput(ref);

	} else if (ast instanceof SuperFieldAccess) {
	    SuperFieldAccess sfa = (SuperFieldAccess)ast;
	    SimpleName fieldName = sfa.getName();
	    DFVarRef ref = this.lookupField(fieldName);
	    frame.addInput(ref);

	} else if (ast instanceof CastExpression) {
	    CastExpression cast = (CastExpression)ast;
	    this.build(frame, cast.getExpression());

	} else if (ast instanceof ClassInstanceCreation) {
	    ClassInstanceCreation cstr = (ClassInstanceCreation)ast;
	    Expression expr = cstr.getExpression();
	    if (expr != null) {
		this.build(frame, expr);
	    }
	    for (Expression arg :
		     (List<Expression>) cstr.arguments()) {
		this.build(frame, arg);
	    }
	    // XXX Ignored getAnonymousClassDeclaration().

	} else if (ast instanceof ConditionalExpression) {
	    ConditionalExpression cond = (ConditionalExpression)ast;
	    this.build(frame, cond.getExpression());
	    this.build(frame, cond.getThenExpression());
	    this.build(frame, cond.getElseExpression());

	} else if (ast instanceof InstanceofExpression) {
	    InstanceofExpression instof = (InstanceofExpression)ast;
	    this.build(frame, instof.getLeftOperand());

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
    public void buildLeft(DFFrame frame, Expression ast)
	throws UnsupportedSyntax {

	if (ast instanceof Name) {
            // XXX incomplete ref!
	    Name name = (Name)ast;
	    if (name.isSimpleName()) {
		DFVarRef ref = this.lookupVarOrField((SimpleName)name);
		frame.addOutput(ref);
	    } else {
		// QualifiedName == FieldAccess
		QualifiedName qn = (QualifiedName)name;
		SimpleName fieldName = qn.getName();
		this.build(frame, qn.getQualifier());
		DFVarRef ref = this.lookupField(fieldName);
		frame.addOutput(ref);
	    }

	} else if (ast instanceof ArrayAccess) {
	    ArrayAccess aa = (ArrayAccess)ast;
	    this.build(frame, aa.getArray());
	    this.build(frame, aa.getIndex());
	    DFVarRef ref = this.lookupArray();
	    frame.addOutput(ref);

	} else if (ast instanceof FieldAccess) {
	    FieldAccess fa = (FieldAccess)ast;
	    SimpleName fieldName = fa.getName();
	    this.build(frame, fa.getExpression());
	    DFVarRef ref = this.lookupField(fieldName);
	    frame.addOutput(ref);

	} else {
	    throw new UnsupportedSyntax(ast);

	}
    }
}
