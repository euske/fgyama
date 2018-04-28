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

    public String getName() {
        if (_parent == null) {
            return _name;
        } else {
            return _parent.getName()+"."+_name;
        }
    }

    public DFVarSpace addChild(String basename, ASTNode ast) {
	int id = _children.size();
	String name = basename+id;
        Utils.logit("DFVarSpace.addChild: "+this+": "+name);
	DFVarSpace space = new DFVarSpace(this, name);
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

    public DFVarRef lookupVar(SimpleName name) {
        return this.lookupRef(name.getIdentifier());
    }

    public DFVarRef lookupVarOrField(SimpleName name) {
        // try local variables first.
        DFVarRef ref = this.lookupRef(name.getIdentifier());
	if (ref != null) return ref;
        // try field names.
        ref = this.lookupField(name);
        if (ref != null) return ref;
        // builtin names?
        String id = Utils.resolveName(name);
        if (id != null) return this.addRef(id, null);
        // fallback...
        return this.addRef(name.getIdentifier(), null);
    }

    public DFVarRef lookupReturn() {
	return this.lookupRef("#return");
    }

    public DFVarRef lookupArray() {
        // XXX
	return _root.addRef("#array", null);
    }

    public DFVarRef lookupThis() {
        assert(_parent != null);
	return _parent.lookupThis();
    }

    public DFVarRef lookupField(SimpleName name) {
        assert(_parent != null);
	return _parent.lookupField(name);
    }

    public DFMethod lookupMethod(SimpleName name) {
        assert(_parent != null);
        return _parent.lookupMethod(name);
    }

    public DFVarRef addVar(SimpleName name, DFType type) {
        Utils.logit("DFVarSpace.addVar: "+this+": "+name+" -> "+type);
        return this.addRef(name.getIdentifier(), type);
    }

    /**
     * Lists all the variables defined inside a block.
     */
    public void build(DFTypeSpace typeSpace, DFFrame frame,
                      Block block, DFType returnType)
	throws UnsupportedSyntax {
        this.build(typeSpace, frame, block);
	this.addRef("#return", returnType);
    }

    @SuppressWarnings("unchecked")
    public void build(DFTypeSpace typeSpace, DFFrame frame, Statement ast)
	throws UnsupportedSyntax {
        Utils.logit("DFVarSpace.build: "+this);

	if (ast instanceof AssertStatement) {

	} else if (ast instanceof Block) {
	    Block block = (Block)ast;
	    DFVarSpace childSpace = this.addChild("b", ast);
	    for (Statement stmt :
		     (List<Statement>) block.statements()) {
		childSpace.build(typeSpace, frame, stmt);
	    }

	} else if (ast instanceof EmptyStatement) {

	} else if (ast instanceof VariableDeclarationStatement) {
	    VariableDeclarationStatement varStmt =
		(VariableDeclarationStatement)ast;
	    // XXX Ignore modifiers and dimensions.
	    DFType varType = typeSpace.resolve(varStmt.getType());
	    for (VariableDeclarationFragment frag :
		     (List<VariableDeclarationFragment>) varStmt.fragments()) {
		this.addVar(frag.getName(), varType);
		Expression expr = frag.getInitializer();
		if (expr != null) {
		    this.build(typeSpace, frame, expr);
		}
	    }

	} else if (ast instanceof ExpressionStatement) {
	    ExpressionStatement exprStmt = (ExpressionStatement)ast;
	    Expression expr = exprStmt.getExpression();
	    this.build(typeSpace, frame, expr);

	} else if (ast instanceof ReturnStatement) {
	    ReturnStatement returnStmt = (ReturnStatement)ast;
	    Expression expr = returnStmt.getExpression();
	    if (expr != null) {
		this.build(typeSpace, frame, expr);
	    }

	} else if (ast instanceof IfStatement) {
	    IfStatement ifStmt = (IfStatement)ast;
	    Expression expr = ifStmt.getExpression();
	    this.build(typeSpace, frame, expr);
	    Statement thenStmt = ifStmt.getThenStatement();
	    this.build(typeSpace, frame, thenStmt);
	    Statement elseStmt = ifStmt.getElseStatement();
	    if (elseStmt != null) {
		this.build(typeSpace, frame, elseStmt);
	    }

	} else if (ast instanceof SwitchStatement) {
	    SwitchStatement switchStmt = (SwitchStatement)ast;
	    DFVarSpace childSpace = this.addChild("switch", ast);
	    DFFrame childFrame = frame.addChild(null, ast);
	    Expression expr = switchStmt.getExpression();
	    childSpace.build(typeSpace, frame, expr);
	    for (Statement stmt :
		     (List<Statement>) switchStmt.statements()) {
		childSpace.build(typeSpace, childFrame, stmt);
	    }

	} else if (ast instanceof SwitchCase) {
	    SwitchCase switchCase = (SwitchCase)ast;
	    Expression expr = switchCase.getExpression();
	    if (expr != null) {
		this.build(typeSpace, frame, expr);
	    }

	} else if (ast instanceof WhileStatement) {
	    WhileStatement whileStmt = (WhileStatement)ast;
	    DFVarSpace childSpace = this.addChild("while", ast);
	    DFFrame childFrame = frame.addChild(null, ast);
	    Expression expr = whileStmt.getExpression();
	    childSpace.build(typeSpace, frame, expr);
	    Statement stmt = whileStmt.getBody();
	    childSpace.build(typeSpace, childFrame, stmt);

	} else if (ast instanceof DoStatement) {
	    DoStatement doStmt = (DoStatement)ast;
	    DFVarSpace childSpace = this.addChild("do", ast);
	    DFFrame childFrame = frame.addChild(null, ast);
	    Statement stmt = doStmt.getBody();
	    childSpace.build(typeSpace, childFrame, stmt);
	    Expression expr = doStmt.getExpression();
	    childSpace.build(typeSpace, frame, expr);

	} else if (ast instanceof ForStatement) {
	    ForStatement forStmt = (ForStatement)ast;
	    DFVarSpace childSpace = this.addChild("for", ast);
	    DFFrame childFrame = frame.addChild(null, ast);
	    for (Expression init :
		     (List<Expression>) forStmt.initializers()) {
		childSpace.build(typeSpace, frame, init);
	    }
	    Expression expr = forStmt.getExpression();
	    if (expr != null) {
		childSpace.build(typeSpace, childFrame, expr);
	    }
	    Statement stmt = forStmt.getBody();
	    childSpace.build(typeSpace, childFrame, stmt);
	    for (Expression update :
		     (List<Expression>) forStmt.updaters()) {
		childSpace.build(typeSpace, childFrame, update);
	    }

	} else if (ast instanceof EnhancedForStatement) {
	    EnhancedForStatement eForStmt = (EnhancedForStatement)ast;
	    DFVarSpace childSpace = this.addChild("efor", ast);
	    DFFrame childFrame = frame.addChild(null, ast);
	    SingleVariableDeclaration decl = eForStmt.getParameter();
	    // XXX Ignore modifiers and dimensions.
	    DFType varType = typeSpace.resolve(decl.getType());
	    childSpace.addVar(decl.getName(), varType);
	    Expression expr = eForStmt.getExpression();
	    if (expr != null) {
		childSpace.build(typeSpace, frame, expr);
	    }
	    Statement stmt = eForStmt.getBody();
	    childSpace.build(typeSpace, childFrame, stmt);

	} else if (ast instanceof BreakStatement) {

	} else if (ast instanceof ContinueStatement) {

	} else if (ast instanceof LabeledStatement) {
	    LabeledStatement labeledStmt = (LabeledStatement)ast;
	    SimpleName labelName = labeledStmt.getLabel();
	    String label = labelName.getIdentifier();
	    DFFrame childFrame = frame.addChild(label, ast);
	    Statement stmt = labeledStmt.getBody();
	    this.build(typeSpace, childFrame, stmt);

	} else if (ast instanceof SynchronizedStatement) {
	    SynchronizedStatement syncStmt = (SynchronizedStatement)ast;
	    Block block = syncStmt.getBody();
	    this.build(typeSpace, frame, block);

	} else if (ast instanceof TryStatement) {
	    TryStatement tryStmt = (TryStatement)ast;
	    Block block = tryStmt.getBody();
	    DFFrame tryFrame = frame.addChild(DFFrame.TRY, ast);
	    this.build(typeSpace, tryFrame, block);
	    for (CatchClause cc :
		     (List<CatchClause>) tryStmt.catchClauses()) {
		DFVarSpace childSpace = this.addChild("catch", cc);
		SingleVariableDeclaration decl = cc.getException();
		// XXX Ignore modifiers and dimensions.
		DFType varType = typeSpace.resolve(decl.getType());
		childSpace.addVar(decl.getName(), varType);
		childSpace.build(typeSpace, frame, cc.getBody());
	    }
	    Block finBlock = tryStmt.getFinally();
	    if (finBlock != null) {
		this.build(typeSpace, frame, finBlock);
	    }

	} else if (ast instanceof ThrowStatement) {
	    ThrowStatement throwStmt = (ThrowStatement)ast;
	    Expression expr = throwStmt.getExpression();
	    if (expr != null) {
		this.build(typeSpace, frame, expr);
	    }

	} else if (ast instanceof ConstructorInvocation) {
	    ConstructorInvocation ci = (ConstructorInvocation)ast;
	    for (Expression expr :
		     (List<Expression>) ci.arguments()) {
		this.build(typeSpace, frame, expr);
	    }

	} else if (ast instanceof SuperConstructorInvocation) {
	    SuperConstructorInvocation sci = (SuperConstructorInvocation)ast;
	    for (Expression expr :
		     (List<Expression>) sci.arguments()) {
		this.build(typeSpace, frame, expr);
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
    public void build(DFTypeSpace typeSpace, DFFrame frame, Expression ast)
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
	    this.build(typeSpace, frame, operand);
	    if (op == PrefixExpression.Operator.INCREMENT ||
		op == PrefixExpression.Operator.DECREMENT) {
		this.buildLeft(typeSpace, frame, operand);
	    }

	} else if (ast instanceof PostfixExpression) {
	    PostfixExpression postfix = (PostfixExpression)ast;
	    PostfixExpression.Operator op = postfix.getOperator();
	    Expression operand = postfix.getOperand();
	    this.build(typeSpace, frame, operand);
	    if (op == PostfixExpression.Operator.INCREMENT ||
		op == PostfixExpression.Operator.DECREMENT) {
		this.buildLeft(typeSpace, frame, operand);
	    }

	} else if (ast instanceof InfixExpression) {
	    InfixExpression infix = (InfixExpression)ast;
	    InfixExpression.Operator op = infix.getOperator();
	    Expression loperand = infix.getLeftOperand();
	    this.build(typeSpace, frame, loperand);
	    Expression roperand = infix.getRightOperand();
	    this.build(typeSpace, frame, roperand);

	} else if (ast instanceof ParenthesizedExpression) {
	    ParenthesizedExpression paren = (ParenthesizedExpression)ast;
	    this.build(typeSpace, frame, paren.getExpression());

	} else if (ast instanceof Assignment) {
	    Assignment assn = (Assignment)ast;
	    Assignment.Operator op = assn.getOperator();
	    this.buildLeft(typeSpace, frame, assn.getLeftHandSide());
	    if (op != Assignment.Operator.ASSIGN) {
		this.build(typeSpace, frame, assn.getLeftHandSide());
	    }
	    this.build(typeSpace, frame, assn.getRightHandSide());

	} else if (ast instanceof VariableDeclarationExpression) {
	    VariableDeclarationExpression decl = (VariableDeclarationExpression)ast;
	    // XXX Ignore modifiers and dimensions.
	    DFType varType = typeSpace.resolve(decl.getType());
	    for (VariableDeclarationFragment frag :
		     (List<VariableDeclarationFragment>) decl.fragments()) {
		DFVarRef ref = this.addVar(frag.getName(), varType);
		frame.addOutput(ref);
		Expression expr = frag.getInitializer();
		if (expr != null) {
		    this.build(typeSpace, frame, expr);
		}
	    }

	} else if (ast instanceof MethodInvocation) {
	    MethodInvocation invoke = (MethodInvocation)ast;
	    Expression expr = invoke.getExpression();
	    if (expr != null) {
		this.build(typeSpace, frame, expr);
	    }
	    for (Expression arg :
		     (List<Expression>) invoke.arguments()) {
		this.build(typeSpace, frame, arg);
	    }

	} else if (ast instanceof SuperMethodInvocation) {
	    SuperMethodInvocation si = (SuperMethodInvocation)ast;
	    for (Expression arg :
		     (List<Expression>) si.arguments()) {
		this.build(typeSpace, frame, arg);
	    }

	} else if (ast instanceof ArrayCreation) {
	    ArrayCreation ac = (ArrayCreation)ast;
	    for (Expression dim :
		     (List<Expression>) ac.dimensions()) {
		this.build(typeSpace, frame, dim);
	    }
	    ArrayInitializer init = ac.getInitializer();
	    if (init != null) {
		this.build(typeSpace, frame, init);
	    }

	} else if (ast instanceof ArrayInitializer) {
	    ArrayInitializer init = (ArrayInitializer)ast;
	    for (Expression expr :
		     (List<Expression>) init.expressions()) {
		this.build(typeSpace, frame, expr);
	    }

	} else if (ast instanceof ArrayAccess) {
	    ArrayAccess aa = (ArrayAccess)ast;
	    this.build(typeSpace, frame, aa.getArray());
	    this.build(typeSpace, frame, aa.getIndex());
	    DFVarRef ref = this.lookupArray();
	    frame.addInput(ref);

	} else if (ast instanceof FieldAccess) {
	    FieldAccess fa = (FieldAccess)ast;
	    SimpleName fieldName = fa.getName();
	    this.build(typeSpace, frame, fa.getExpression());
	    DFVarRef ref = this.lookupField(fieldName);
	    frame.addInput(ref);

	} else if (ast instanceof SuperFieldAccess) {
	    SuperFieldAccess sfa = (SuperFieldAccess)ast;
	    SimpleName fieldName = sfa.getName();
	    DFVarRef ref = this.lookupField(fieldName);
	    frame.addInput(ref);

	} else if (ast instanceof CastExpression) {
	    CastExpression cast = (CastExpression)ast;
	    this.build(typeSpace, frame, cast.getExpression());

	} else if (ast instanceof ClassInstanceCreation) {
	    ClassInstanceCreation cstr = (ClassInstanceCreation)ast;
	    Expression expr = cstr.getExpression();
	    if (expr != null) {
		this.build(typeSpace, frame, expr);
	    }
	    for (Expression arg :
		     (List<Expression>) cstr.arguments()) {
		this.build(typeSpace, frame, arg);
	    }
	    // XXX Ignored getAnonymousClassDeclaration().

	} else if (ast instanceof ConditionalExpression) {
	    ConditionalExpression cond = (ConditionalExpression)ast;
	    this.build(typeSpace, frame, cond.getExpression());
	    this.build(typeSpace, frame, cond.getThenExpression());
	    this.build(typeSpace, frame, cond.getElseExpression());

	} else if (ast instanceof InstanceofExpression) {
	    InstanceofExpression instof = (InstanceofExpression)ast;
	    this.build(typeSpace, frame, instof.getLeftOperand());

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
    public void buildLeft(DFTypeSpace typeSpace, DFFrame frame, Expression ast)
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
		this.build(typeSpace, frame, qn.getQualifier());
		DFVarRef ref = this.lookupField(fieldName);
		frame.addOutput(ref);
	    }

	} else if (ast instanceof ArrayAccess) {
	    ArrayAccess aa = (ArrayAccess)ast;
	    this.build(typeSpace, frame, aa.getArray());
	    this.build(typeSpace, frame, aa.getIndex());
	    DFVarRef ref = this.lookupArray();
	    frame.addOutput(ref);

	} else if (ast instanceof FieldAccess) {
	    FieldAccess fa = (FieldAccess)ast;
	    SimpleName fieldName = fa.getName();
	    this.build(typeSpace, frame, fa.getExpression());
	    DFVarRef ref = this.lookupField(fieldName);
	    frame.addOutput(ref);

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
