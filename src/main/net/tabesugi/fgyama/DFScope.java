//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;
import org.w3c.dom.*;


//  DFScope
//  Mapping from name -> reference.
//
public class DFScope {

    private DFScope _root;
    private String _name;
    private DFScope _parent;

    private List<DFScope> _children = new ArrayList<DFScope>();
    private Map<String, DFScope> _name2child = new HashMap<String, DFScope>();
    private Map<ASTNode, DFScope> _ast2child = new HashMap<ASTNode, DFScope>();
    private Map<String, DFRef> _refs = new HashMap<String, DFRef>();

    public DFScope(String name) {
        _root = this;
	_name = name;
        _parent = null;
    }

    public DFScope(String name, DFScope parent) {
        _root = parent._root;
	_name = name;
	_parent = parent;
    }

    @Override
    public String toString() {
	return ("<DFScope("+_name+")>");
    }

    public Element toXML(Document document, DFNode[] nodes) {
	Element elem = document.createElement("scope");
	elem.setAttribute("name", _name);
	for (DFScope child : this.getChildren()) {
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

    public DFScope addChild(String basename, ASTNode ast) {
	int id = _children.size();
	String name = basename+id;
	DFScope scope = new DFScope(name, this);
	_children.add(scope);
        _name2child.put(name, scope);
	_ast2child.put(ast, scope);
	return scope;
    }

    public DFScope getChildByAST(ASTNode ast) {
	return _ast2child.get(ast);
    }

    public DFScope getChildByName(Name name) {
        if (name.isQualifiedName()) {
	    QualifiedName qname = (QualifiedName)name;
	    DFScope scope = (_parent != null)? _parent : this;
	    scope = scope.getChildByName(qname.getQualifier());
            return scope.getChildByName(qname.getName().getIdentifier());
        } else {
            SimpleName sname = (SimpleName)name;
            return this.getChildByName(sname.getIdentifier());
        }
    }

    public DFScope getChildByName(String name) {
        DFScope scope = _name2child.get(name);
        if (scope == null) {
            scope = new DFScope(name, this);
            _children.add(scope);
            _name2child.put(name, scope);
        }
        return scope;
    }

    public DFScope[] getChildren() {
	DFScope[] scopes = new DFScope[_children.size()];
	_children.toArray(scopes);
	return scopes;
    }

    public DFRef addVar(String id, DFType type) {
	DFRef ref = _refs.get(id);
	if (ref == null) {
            ref = new DFVar(this, id, type);
            _refs.put(id, ref);
        }
	return ref;
    }

    public DFRef addVar(SimpleName name, DFType type) {
        return this.addVar(name.getIdentifier(), type);
    }

    private DFRef lookupRef(String id) {
	DFRef ref = _refs.get(id);
	if (ref != null) {
	    return ref;
	} else if (_parent != null) {
	    return _parent.lookupRef(id);
	} else {
	    return this.addVar(id, null);
	}
    }

    private DFRef lookupRef(IBinding binding) {
        return this.lookupRef(binding.getKey());
    }

    public DFRef lookupVar(SimpleName name) {
        return this.lookupRef(name.getIdentifier());
    }

    public DFRef lookupField(SimpleName name) {
        return this.lookupRef("."+name.getIdentifier());
    }

    public DFRef lookupThis() {
	return this.lookupRef("#this");
    }

    public DFRef lookupSuper() {
	return this.lookupRef("#super");
    }

    public DFRef lookupReturn() {
	return this.lookupRef("#return");
    }

    public DFRef lookupArray() {
	return _root.addVar("#array", null);
    }

    // dump: for debugging.
    public void dump() {
	dump(System.out, "");
    }
    public void dump(PrintStream out, String indent) {
	out.println(indent+getName()+" {");
	String i2 = indent + "  ";
	for (DFRef ref : _refs.values()) {
	    out.println(i2+"defined: "+ref);
	}
	for (DFScope scope : _children) {
	    scope.dump(out, i2);
	}
	out.println(indent+"}");
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
	    DFScope childScope = this.addChild("b", ast);
	    for (Statement stmt :
		     (List<Statement>) block.statements()) {
		childScope.build(frame, stmt);
	    }

	} else if (ast instanceof EmptyStatement) {

	} else if (ast instanceof VariableDeclarationStatement) {
	    VariableDeclarationStatement varStmt =
		(VariableDeclarationStatement)ast;
	    // XXX Ignore modifiers and dimensions.
	    DFType varType = new DFType(varStmt.getType());
	    for (VariableDeclarationFragment frag :
		     (List<VariableDeclarationFragment>) varStmt.fragments()) {
		this.addVar(frag.getName(), varType);
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
	    DFScope childScope = this.addChild("switch", ast);
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
	    DFScope childScope = this.addChild("while", ast);
	    DFFrame childFrame = frame.addChild(null, ast);
	    Expression expr = whileStmt.getExpression();
	    childScope.build(frame, expr);
	    Statement stmt = whileStmt.getBody();
	    childScope.build(childFrame, stmt);

	} else if (ast instanceof DoStatement) {
	    DoStatement doStmt = (DoStatement)ast;
	    DFScope childScope = this.addChild("do", ast);
	    DFFrame childFrame = frame.addChild(null, ast);
	    Statement stmt = doStmt.getBody();
	    childScope.build(childFrame, stmt);
	    Expression expr = doStmt.getExpression();
	    childScope.build(frame, expr);

	} else if (ast instanceof ForStatement) {
	    ForStatement forStmt = (ForStatement)ast;
	    DFScope childScope = this.addChild("for", ast);
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
	    DFScope childScope = this.addChild("efor", ast);
	    DFFrame childFrame = frame.addChild(null, ast);
	    SingleVariableDeclaration decl = eForStmt.getParameter();
	    // XXX Ignore modifiers and dimensions.
	    DFType varType = new DFType(decl.getType());
	    childScope.addVar(decl.getName(), varType);
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
		DFScope childScope = this.addChild("catch", cc);
		SingleVariableDeclaration decl = cc.getException();
		// XXX Ignore modifiers and dimensions.
		DFType varType = new DFType(decl.getType());
		childScope.addVar(decl.getName(), varType);
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
	    Name name = (Name)ast;
	    if (name.isSimpleName()) {
		DFRef ref = this.lookupVar((SimpleName)name);
		frame.addInput(ref);
	    } else {
		// QualifiedName == FieldAccess
		QualifiedName qn = (QualifiedName)name;
		SimpleName fieldName = qn.getName();
		DFRef ref = this.lookupField(fieldName);
		frame.addInput(ref);
	    }

	} else if (ast instanceof ThisExpression) {
	    DFRef ref = this.lookupThis();
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
	    DFType varType = new DFType(decl.getType());
	    for (VariableDeclarationFragment frag :
		     (List<VariableDeclarationFragment>) decl.fragments()) {
		DFRef ref = this.addVar(frag.getName(), varType);
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
	    DFRef ref = this.lookupArray();
	    frame.addInput(ref);

	} else if (ast instanceof FieldAccess) {
	    FieldAccess fa = (FieldAccess)ast;
	    SimpleName fieldName = fa.getName();
	    this.build(frame, fa.getExpression());
	    DFRef ref = this.lookupField(fieldName);
	    frame.addInput(ref);

	} else if (ast instanceof SuperFieldAccess) {
	    SuperFieldAccess sfa = (SuperFieldAccess)ast;
	    SimpleName fieldName = sfa.getName();
	    DFRef ref = this.lookupField(fieldName);
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
	    // Ignore getAnonymousClassDeclaration() here.
	    // It will eventually be picked up as MethodDeclaration.

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
	    Name name = (Name)ast;
	    if (name.isSimpleName()) {
		DFRef ref = this.lookupVar((SimpleName)name);
		frame.addOutput(ref);
	    } else {
		// QualifiedName == FieldAccess
		QualifiedName qn = (QualifiedName)name;
		SimpleName fieldName = qn.getName();
		this.build(frame, qn.getQualifier());
		DFRef ref = this.lookupField(fieldName);
		frame.addOutput(ref);
	    }

	} else if (ast instanceof ArrayAccess) {
	    ArrayAccess aa = (ArrayAccess)ast;
	    this.build(frame, aa.getArray());
	    this.build(frame, aa.getIndex());
	    DFRef ref = this.lookupArray();
	    frame.addOutput(ref);

	} else if (ast instanceof FieldAccess) {
	    FieldAccess fa = (FieldAccess)ast;
	    SimpleName fieldName = fa.getName();
	    this.build(frame, fa.getExpression());
	    DFRef ref = this.lookupField(fieldName);
	    frame.addOutput(ref);

	} else {
	    throw new UnsupportedSyntax(ast);

	}
    }
}
