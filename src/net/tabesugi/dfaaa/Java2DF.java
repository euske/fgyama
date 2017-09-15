/**
 * Java2DF
 * Dataflow analyzer for Java
 */
package net.tabesugi.dfaaa;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  UnsupportedSyntax
//
class UnsupportedSyntax extends Exception {

    static final long serialVersionUID = 1L;

    public ASTNode ast;
    
    public UnsupportedSyntax(ASTNode ast) {
	this.ast = ast;
    }
}


// ReferNode: reference node.
abstract class ReferNode extends ProgNode {

    public ReferNode(DFScope scope, DFRef ref, ASTNode ast) {
	super(scope, ref, ast);
    }

    public DFNodeType type() {
	return DFNodeType.Refer;
    }
}

// SingleAssignNode:
class SingleAssignNode extends AssignNode {

    public DFNode value;
    
    public SingleAssignNode(DFScope scope, DFRef ref, ASTNode ast) {
	super(scope, ref, ast);
    }

    public void take(DFNode value) {
	this.value = value;
	value.connect(this, 1);
    }
}

// ReturnNode: represents a return value.
class ReturnNode extends SingleAssignNode {

    public ReturnNode(DFScope scope, DFRef ref, ASTNode ast) {
	super(scope, ref, ast);
    }

    public DFNodeType type() {
	return DFNodeType.Terminal;
    }

    public String label() {
	return "return";
    }
}

// ArrayAssignNode:
class ArrayAssignNode extends SingleAssignNode {

    public DFNode index;

    public ArrayAssignNode(DFScope scope, DFRef ref, ASTNode ast,
			   DFNode array, DFNode index) {
	super(scope, ref, ast);
	this.index = index;
	array.connect(this, 1, "array");
	index.connect(this, 2, "index");
    }
}

// FieldAssignNode:
class FieldAssignNode extends SingleAssignNode {

    public DFNode obj;

    public FieldAssignNode(DFScope scope, DFRef ref, ASTNode ast,
			   DFNode obj) {
	super(scope, ref, ast);
	this.obj = obj;
	obj.connect(this, 1, "index");
    }
}

// ArrayAccessNode
class ArrayAccessNode extends ProgNode {

    public DFNode value;
    public DFNode index;

    public ArrayAccessNode(DFScope scope, DFRef ref, ASTNode ast,
			   DFNode array, DFNode value, DFNode index) {
	super(scope, ref, ast);
	this.value = value;
	this.index = index;
	array.connect(this, 1, "array");
	value.connect(this, 2, "value");
	index.connect(this, 3, "index");
    }

    public DFNodeType type() {
	return DFNodeType.Operator;
    }

    public String label() {
	return "arrayaccess";
    }
}

// FieldAccessNode
class FieldAccessNode extends ProgNode {

    public DFNode value;
    public DFNode obj;

    public FieldAccessNode(DFScope scope, DFRef ref, ASTNode ast,
			   DFNode value, DFNode obj) {
	super(scope, ref, ast);
	this.value = value;
	this.obj = obj;
	value.connect(this, 1, "value");
	obj.connect(this, 2, "index");
    }

    public DFNodeType type() {
	return DFNodeType.Operator;
    }

    public String label() {
	return "fieldaccess";
    }
}

// PrefixNode
class PrefixNode extends ProgNode {

    public PrefixExpression.Operator op;
    public DFNode value;

    public PrefixNode(DFScope scope, DFRef ref, ASTNode ast,
		      PrefixExpression.Operator op, DFNode value) {
	super(scope, ref, ast);
	this.op = op;
	this.value = value;
	value.connect(this, 1);
    }

    public DFNodeType type() {
	return DFNodeType.Operator;
    }

    public String label() {
	return this.op.toString();
    }
}

// PostfixNode
class PostfixNode extends ProgNode {

    public PostfixExpression.Operator op;
    public DFNode value;

    public PostfixNode(DFScope scope, DFRef ref, ASTNode ast,
		       PostfixExpression.Operator op, DFNode value) {
	super(scope, ref, ast);
	this.op = op;
	this.value = value;
	value.connect(this, 1);
    }

    public DFNodeType type() {
	return DFNodeType.Operator;
    }

    public String label() {
	return this.op.toString();
    }
}

// ArgNode: represnets a function argument.
class ArgNode extends ProgNode {

    public int index;

    public ArgNode(DFScope scope, ASTNode ast, int index) {
	super(scope, null, ast);
	this.index = index;
    }

    public DFNodeType type() {
	return DFNodeType.Terminal;
    }

    public String label() {
	return "arg";
    }
}

// VarRefNode: represnets a variable reference.
class VarRefNode extends ReferNode {

    public DFNode value;

    public VarRefNode(DFScope scope, DFRef ref, ASTNode ast, DFNode value) {
	super(scope, ref, ast);
	this.value = value;
	value.connect(this, 1);
    }

    public String label() {
	return "ref";
    }
}

// ConstNode: represents a constant value.
class ConstNode extends ProgNode {

    public String value;

    public ConstNode(DFScope scope, ASTNode ast, String value) {
	super(scope, null, ast);
	this.value = value;
    }

    public DFNodeType type() {
	return DFNodeType.Const;
    }
    
    public String label() {
	return this.value;
    }
}

// ArrayValueNode: represents an array.
class ArrayValueNode extends ReferNode {

    public List<DFNode> values;

    public ArrayValueNode(DFScope scope, ASTNode ast) {
	super(scope, null, ast);
	this.values = new ArrayList<DFNode>();
    }

    public String label() {
	return "["+this.values.size()+"]";
    }
    
    public void take(DFNode value) {
	this.values.add(value);
	value.connect(this, this.values.size(), "value");
    }
}

// InfixNode
class InfixNode extends ProgNode {

    public InfixExpression.Operator op;
    public DFNode lvalue;
    public DFNode rvalue;

    public InfixNode(DFScope scope, ASTNode ast,
		     InfixExpression.Operator op,
		     DFNode lvalue, DFNode rvalue) {
	super(scope, null, ast);
	this.op = op;
	this.lvalue = lvalue;
	this.rvalue = rvalue;
	lvalue.connect(this, 1, "L");
	rvalue.connect(this, 2, "R");
    }

    public DFNodeType type() {
	return DFNodeType.Operator;
    }

    public String label() {
	return this.op.toString();
    }
}

// TypeCastNode
class TypeCastNode extends ProgNode {

    public Type type;
    public DFNode value;
    
    public TypeCastNode(DFScope scope, ASTNode ast,
			Type type, DFNode value) {
	super(scope, null, ast);
	this.type = type;
	this.value = value;
	value.connect(this, 1);
    }

    public DFNodeType type() {
	return DFNodeType.Operator;
    }

    public String label() {
	return "("+Utils.getTypeName(this.type)+")";
    }
}

// InstanceofNode
class InstanceofNode extends ProgNode {

    public Type type;
    public DFNode value;
    
    public InstanceofNode(DFScope scope, ASTNode ast,
			  Type type, DFNode value) {
	super(scope, null, ast);
	this.type = type;
	this.value = value;
	value.connect(this, 1);
    }

    public DFNodeType type() {
	return DFNodeType.Operator;
    }

    public String label() {
	return Utils.getTypeName(this.type)+"?";
    }
}

// CaseNode
class CaseNode extends ProgNode {

    public DFNode value;
    public List<DFNode> matches;
    
    public CaseNode(DFScope scope, ASTNode ast,
		    DFNode value) {
	super(scope, null, ast);
	this.value = value;
	value.connect(this, 1);
	this.matches = new ArrayList<DFNode>();
    }

    public DFNodeType type() {
	return DFNodeType.Operator;
    }

    public String label() {
	if (!this.matches.isEmpty()) {
	    return "case";
	} else {
	    return "default";
	}
    }

    public void add(DFNode node) {
	this.matches.add(node);
	node.connect(this, this.matches.size());
    }
}

// AssignOpNode
class AssignOpNode extends ProgNode {

    public Assignment.Operator op;
    public DFNode lvalue;
    public DFNode rvalue;

    public AssignOpNode(DFScope scope, DFRef ref, ASTNode ast,
			Assignment.Operator op,
			DFNode lvalue, DFNode rvalue) {
	super(scope, ref, ast);
	this.op = op;
	this.lvalue = lvalue;
	this.rvalue = rvalue;
	lvalue.connect(this, 1, "L");
	rvalue.connect(this, 2, "R");
    }

    public DFNodeType type() {
	return DFNodeType.Operator;
    }

    public String label() {
	return this.op.toString();
    }
}

// BranchNode
class BranchNode extends CondNode {

    public BranchNode(DFScope scope, DFRef ref, ASTNode ast,
		      DFNode value) {
	super(scope, ref, ast, value);
    }

    public DFNodeType type() {
	return DFNodeType.Branch;
    }

    public String label() {
	return "branch";
    }

    public void send(boolean cond, DFNode node) {
	if (cond) {
	    this.connect(node, 1, "true");
	} else {
	    this.connect(node, 2, "false");
	}
    }
}

// LoopNode
class LoopNode extends ProgNode {

    public DFNode enter;
    
    public LoopNode(DFScope scope, DFRef ref, ASTNode ast,
		    DFNode enter) {
	super(scope, ref, ast);
	this.enter = enter;
	enter.connect(this, 1, "enter");
    }
    
    public DFNodeType type() {
	return DFNodeType.Loop;
    }
    
    public String label() {
	return "loop";
    }
}

// IterNode
class IterNode extends ProgNode {

    public DFNode list;
    
    public IterNode(DFScope scope, DFRef ref, ASTNode ast,
		    DFNode list) {
	super(scope, ref, ast);
	this.list = list;
	list.connect(this, 1);
    }
    
    public DFNodeType type() {
	return DFNodeType.Operator;
    }

    public String label() {
	return "iter";
    }
}

// CallNode
abstract class CallNode extends ProgNode {

    public DFNode obj;
    public List<DFNode> args;
    public DFNode exception;

    public CallNode(DFScope scope, DFRef ref, ASTNode ast,
		    DFNode obj) {
	super(scope, ref, ast);
	this.obj = obj;
	this.args = new ArrayList<DFNode>();
        this.exception = null;
	if (obj != null) {
	    obj.connect(this, 1, "index");
	}
    }

    public DFNodeType type() {
	return DFNodeType.Operator;
    }

    public void take(DFNode arg) {
	this.args.add(arg);
	arg.connect(this, this.args.size()+1, "arg");
    }
}

// MethodCallNode
class MethodCallNode extends CallNode {

    public String name;

    public MethodCallNode(DFScope scope, ASTNode ast,
			  DFNode obj, String name) {
	super(scope, null, ast, obj);
	this.name = name;
    }
    
    public String label() {
	return this.name+"()";
    }
}

// CreateObjectNode
class CreateObjectNode extends CallNode {

    public Type type;

    public CreateObjectNode(DFScope scope, ASTNode ast,
			    DFNode obj, Type type) {
	super(scope, null, ast, obj);
	this.type = type;
    }
    
    public String label() {
	return "new "+Utils.getTypeName(this.type);
    }
}

// ExceptionNode
class ExceptionNode extends ProgNode {

    public DFNode value;

    public ExceptionNode(DFScope scope, ASTNode ast, DFNode value) {
	super(scope, scope.lookupException(), ast);
        this.value = value;
	value.connect(this, 1);
    }

    public DFNodeType type() {
	return DFNodeType.Exception;
    }

    public String label() {
	return "exception";
    }
}

    
//  Java2DF
// 
public class Java2DF extends ASTVisitor {

    /// General graph operations.
    
    /** 
     * Combines two components into one.
     * A JoinNode is added to each variable.
     */
    public DFComponent processConditional
	(DFScope scope, DFFrame frame, DFComponent cpt, ASTNode ast, 
	 DFNode condValue,
	 DFFrame trueFrame, DFComponent trueCpt,
	 DFFrame falseFrame, DFComponent falseCpt) {

	// refs: all the references from both component.
	Set<DFRef> refs = new HashSet<DFRef>();
	if (trueCpt != null) {
	    for (Map.Entry<DFRef, DFNode> entry : trueCpt.inputs.entrySet()) {
		DFRef ref = entry.getKey();
		DFNode src = entry.getValue();
		src.accept(cpt.get(ref));
	    }
	    refs.addAll(trueCpt.outputs.keySet());
	}
	if (falseCpt != null) {
	    for (Map.Entry<DFRef, DFNode> entry : falseCpt.inputs.entrySet()) {
		DFRef ref = entry.getKey();
		DFNode src = entry.getValue();
		src.accept(cpt.get(ref));
	    }
	    refs.addAll(falseCpt.outputs.keySet());
	}

	// Attach a JoinNode to each variable.
	for (DFRef ref : refs) {
	    JoinNode join = new JoinNode(scope, ref, ast, condValue);
	    if (trueCpt != null && trueCpt.outputs.containsKey(ref)) {
		join.recv(true, trueCpt.get(ref));
	    }
	    if (falseCpt != null && falseCpt.outputs.containsKey(ref)) {
		join.recv(false, falseCpt.get(ref));
	    }
	    if (!join.isClosed()) {
		join.close(cpt.get(ref));
	    }
	    cpt.put(join);
	}

	// Take care of exits.
	if (trueFrame != null) {
	    for (DFExit exit : trueFrame.exits) {
		frame.add(exit.addJoin(scope, condValue, true));
	    }
	}
	if (falseFrame != null) {
	    for (DFExit exit : falseFrame.exits) {
		frame.add(exit.addJoin(scope, condValue, false));
	    }
	}
	
	return cpt;
    }

    /** 
     * Expands the graph for the loop variables.
     */
    public DFComponent processLoop
	(DFScope scope, DFFrame frame, DFComponent cpt, ASTNode ast, 
	 DFNode condValue,
	 DFFrame loopFrame, DFComponent loopCpt,
	 Set<DFRef> loopRefs)
	throws UnsupportedSyntax {

	// Add four nodes for each loop variable.
	Map<DFRef, LoopNode> loops = new HashMap<DFRef, LoopNode>();
	Map<DFRef, BranchNode> branches = new HashMap<DFRef, BranchNode>();
	Map<DFRef, DFNode> repeats = new HashMap<DFRef, DFNode>();
	Map<DFRef, DFNode> leaves = new HashMap<DFRef, DFNode>();
	for (DFRef ref : loopRefs) {
	    DFNode src = cpt.get(ref);
	    LoopNode loop = new LoopNode(scope, ref, ast, src);
	    BranchNode branch = new BranchNode(scope, ref, ast, condValue);
	    DFNode repeat = new DistNode(scope, ref);
	    DFNode leave = new DistNode(scope, ref);
	    // LoopNode -> P -> Branch -> [Repeat | Leave]
	    loop.connect(branch, 0, DFLinkType.Informational, "end");
	    branch.send(true, repeat);
	    branch.send(false, leave);
	    repeat.connect(loop, 0, DFLinkType.BackFlow, "repeat");
	    loops.put(ref, loop);
	    branches.put(ref, branch);
	    repeats.put(ref, repeat);
	    leaves.put(ref, leave);
	}

	// Connect the inputs to the loop.
	for (Map.Entry<DFRef, DFNode> entry : loopCpt.inputs.entrySet()) {
	    DFRef ref = entry.getKey();
	    DFNode input = entry.getValue();
	    LoopNode loop = loops.get(ref);
	    if (loop != null) {
		input.accept(loop);
	    } else {
		DFNode src = cpt.get(ref);
		input.accept(src);
	    }
	}
	
	// Connect the outputs to the loop.
	for (Map.Entry<DFRef, DFNode> entry : loopCpt.outputs.entrySet()) {
	    DFRef ref = entry.getKey();
	    DFNode output = entry.getValue();
	    BranchNode branch = branches.get(ref);
	    if (branch != null) {
		branch.accept(output);
	    } else {
		cpt.put(output);
	    }
	}
	
	// Reconnect the continue statements.
	for (DFExit exit : loopFrame.exits) {
	    if (exit.cont &&
		(exit.label == null || exit.label.equals(loopFrame.label))) {
		DFNode node = exit.node;
		DFNode repeat = repeats.get(node.ref);
		if (node instanceof JoinNode) {
		    ((JoinNode)node).close(repeat);
		}
		repeats.put(node.ref, node);
	    } else {
		frame.add(exit);
	    }
	}

	// Handle the leave nodes.
	for (DFRef ref : loopRefs) {
	    DFNode leave = leaves.get(ref);
	    cpt.put(leave);
	}
	
	return cpt;
    }

    /** 
     * Creates a new variable node.
     */
    public DFComponent processVariableDeclaration
	(DFScope scope, DFFrame frame, DFComponent cpt, 
	 List<VariableDeclarationFragment> frags)
	throws UnsupportedSyntax {

	for (VariableDeclarationFragment frag : frags) {
	    SimpleName varName = frag.getName();
	    DFRef ref = scope.lookupVar(varName.getIdentifier());
	    Expression init = frag.getInitializer();
	    if (init != null) {
		cpt = processExpression(scope, frame, cpt, init);
		AssignNode assign = new SingleAssignNode(scope, ref, frag);
		assign.take(cpt.value);
		cpt.put(assign);
	    }
	}
	return cpt;
    }

    /** 
     * Creates an assignment node.
     */
    @SuppressWarnings("unchecked")
    public DFComponent processAssignment
	(DFScope scope, DFFrame frame, DFComponent cpt, 
	 Expression expr)
	throws UnsupportedSyntax {

	if (expr instanceof SimpleName) {
	    SimpleName varName = (SimpleName)expr;
	    DFRef ref = scope.lookupVar(varName.getIdentifier());
	    cpt.assign = new SingleAssignNode(scope, ref, expr);
	    
	} else if (expr instanceof ArrayAccess) {
	    ArrayAccess aa = (ArrayAccess)expr;
	    cpt = processExpression(scope, frame, cpt, aa.getArray());
	    DFNode array = cpt.value;
	    cpt = processExpression(scope, frame, cpt, aa.getIndex());
	    DFNode index = cpt.value;
	    DFRef ref = scope.lookupArray();
	    cpt.assign = new ArrayAssignNode(scope, ref, expr, array, index);
	    
	} else if (expr instanceof FieldAccess) {
	    FieldAccess fa = (FieldAccess)expr;
	    SimpleName fieldName = fa.getName();
	    cpt = processExpression(scope, frame, cpt, fa.getExpression());
	    DFNode obj = cpt.value;
	    DFRef ref = scope.lookupField(fieldName.getIdentifier());
	    cpt.assign = new FieldAssignNode(scope, ref, expr, obj);
	    
	} else if (expr instanceof QualifiedName) {
	    QualifiedName qn = (QualifiedName)expr;
	    SimpleName fieldName = qn.getName();
	    DFRef ref = scope.lookupField(fieldName.getIdentifier());
	    cpt = processExpression(scope, frame, cpt, qn.getQualifier());
	    DFNode obj = cpt.value;
	    cpt.assign = new FieldAssignNode(scope, ref, expr, obj);
	    
	} else {
	    throw new UnsupportedSyntax(expr);
	}
	
	return cpt;
    }

    /** 
     * Creates a value node.
     */
    @SuppressWarnings("unchecked")
    public DFComponent processExpression
	(DFScope scope, DFFrame frame, DFComponent cpt, 
	 Expression expr)
	throws UnsupportedSyntax {

	if (expr instanceof Annotation) {

	} else if (expr instanceof SimpleName) {
	    SimpleName varName = (SimpleName)expr;
	    DFRef ref = scope.lookupVar(varName.getIdentifier());
	    cpt.value = new VarRefNode(scope, ref, expr, cpt.get(ref));
	    
	} else if (expr instanceof ThisExpression) {
	    DFRef ref = scope.lookupThis();
	    cpt.value = new VarRefNode(scope, ref, expr, cpt.get(ref));
	    
	} else if (expr instanceof BooleanLiteral) {
	    boolean value = ((BooleanLiteral)expr).booleanValue();
	    cpt.value = new ConstNode(scope, expr, Boolean.toString(value));
	    
	} else if (expr instanceof CharacterLiteral) {
	    char value = ((CharacterLiteral)expr).charValue();
	    cpt.value = new ConstNode(scope, expr, Character.toString(value));
	    
	} else if (expr instanceof NullLiteral) {
	    cpt.value = new ConstNode(scope, expr, "null");
	    
	} else if (expr instanceof NumberLiteral) {
	    String value = ((NumberLiteral)expr).getToken();
	    cpt.value = new ConstNode(scope, expr, value);
	    
	} else if (expr instanceof StringLiteral) {
	    String value = ((StringLiteral)expr).getLiteralValue();
	    cpt.value = new ConstNode(scope, expr, value);
	    
	} else if (expr instanceof TypeLiteral) {
	    Type value = ((TypeLiteral)expr).getType();
	    cpt.value = new ConstNode(scope, expr, Utils.getTypeName(value));
	    
	} else if (expr instanceof PrefixExpression) {
	    PrefixExpression prefix = (PrefixExpression)expr;
	    PrefixExpression.Operator op = prefix.getOperator();
	    Expression operand = prefix.getOperand();
	    cpt = processExpression(scope, frame, cpt, operand);
	    if (op == PrefixExpression.Operator.INCREMENT ||
		op == PrefixExpression.Operator.DECREMENT) {
		cpt = processAssignment(scope, frame, cpt, operand);
		AssignNode assign = cpt.assign;
		DFNode value = new PrefixNode(scope, assign.ref, expr, op, cpt.value);
		assign.take(value);
		cpt.put(assign);
		cpt.value = value;
	    } else {
		cpt.value = new PrefixNode(scope, null, expr, op, cpt.value);
	    }
	    
	} else if (expr instanceof PostfixExpression) {
	    PostfixExpression postfix = (PostfixExpression)expr;
	    PostfixExpression.Operator op = postfix.getOperator();
	    Expression operand = postfix.getOperand();
	    cpt = processAssignment(scope, frame, cpt, operand);
	    if (op == PostfixExpression.Operator.INCREMENT ||
		op == PostfixExpression.Operator.DECREMENT) {
		AssignNode assign = cpt.assign;
		cpt = processExpression(scope, frame, cpt, operand);
		assign.take(new PostfixNode(scope, assign.ref, expr, op, cpt.value));
		cpt.put(assign);
	    }
	    
	} else if (expr instanceof InfixExpression) {
	    InfixExpression infix = (InfixExpression)expr;
	    InfixExpression.Operator op = infix.getOperator();
	    cpt = processExpression(scope, frame, cpt, infix.getLeftOperand());
	    DFNode lvalue = cpt.value;
	    cpt = processExpression(scope, frame, cpt, infix.getRightOperand());
	    DFNode rvalue = cpt.value;
	    cpt.value = new InfixNode(scope, expr, op, lvalue, rvalue);
	    
	} else if (expr instanceof ParenthesizedExpression) {
	    ParenthesizedExpression paren = (ParenthesizedExpression)expr;
	    cpt = processExpression(scope, frame, cpt, paren.getExpression());
	    
	} else if (expr instanceof Assignment) {
	    Assignment assn = (Assignment)expr;
	    Assignment.Operator op = assn.getOperator();
	    cpt = processAssignment(scope, frame, cpt, assn.getLeftHandSide());
	    AssignNode assign = cpt.assign;
	    cpt = processExpression(scope, frame, cpt, assn.getRightHandSide());
	    DFNode rvalue = cpt.value;
	    DFNode lvalue = cpt.get(assign.ref);
	    assign.take(new AssignOpNode(scope, assign.ref, assn, op, lvalue, rvalue));
	    cpt.put(assign);
	    cpt.value = assign;

	} else if (expr instanceof VariableDeclarationExpression) {
	    VariableDeclarationExpression decl = (VariableDeclarationExpression)expr;
	    cpt = processVariableDeclaration
		(scope, frame, cpt, decl.fragments());

	} else if (expr instanceof MethodInvocation) {
	    MethodInvocation invoke = (MethodInvocation)expr;
	    Expression expr1 = invoke.getExpression();
	    DFNode obj = null;
	    if (expr1 != null) {
		cpt = processExpression(scope, frame, cpt, expr1);
		obj = cpt.value;
	    }
	    SimpleName methodName = invoke.getName();
	    MethodCallNode call = new MethodCallNode
		(scope, invoke, obj, methodName.getIdentifier());
	    for (Expression arg : (List<Expression>) invoke.arguments()) {
		cpt = processExpression(scope, frame, cpt, arg);
		call.take(cpt.value);
	    }
	    cpt.value = call;
            if (call.exception != null) {
                frame.add(new DFExit(call.exception));
            }
	    
	} else if (expr instanceof SuperMethodInvocation) {
	    SuperMethodInvocation si = (SuperMethodInvocation)expr;
	    SimpleName methodName = si.getName();
	    DFNode obj = cpt.get(scope.lookupSuper());
	    MethodCallNode call = new MethodCallNode
		(scope, si, obj, methodName.getIdentifier());
	    for (Expression arg : (List<Expression>) si.arguments()) {
		cpt = processExpression(scope, frame, cpt, arg);
		call.take(cpt.value);
	    }
	    cpt.value = call;
	    
	} else if (expr instanceof ArrayCreation) {
	    ArrayCreation ac = (ArrayCreation)expr;
	    for (Expression dim : (List<Expression>) ac.dimensions()) {
		// XXX cpt.value is not used (for now).
		cpt = processExpression(scope, frame, cpt, dim);
	    }
	    ArrayInitializer init = ac.getInitializer();
	    if (init != null) {
		cpt = processExpression(scope, frame, cpt, init);
	    } else {
		cpt.value = new ArrayValueNode(scope, ac);
	    }
	    
	} else if (expr instanceof ArrayInitializer) {
	    ArrayInitializer init = (ArrayInitializer)expr;
	    ArrayValueNode arr = new ArrayValueNode(scope, init);
	    for (Expression expr1 : (List<Expression>) init.expressions()) {
		cpt = processExpression(scope, frame, cpt, expr1);
		arr.take(cpt.value);
	    }
	    cpt.value = arr;
	    // XXX array ref is not used.
	    
	} else if (expr instanceof ArrayAccess) {
	    ArrayAccess aa = (ArrayAccess)expr;
	    DFRef ref = scope.lookupArray();
	    cpt = processExpression(scope, frame, cpt, aa.getArray());
	    DFNode array = cpt.value;
	    cpt = processExpression(scope, frame, cpt, aa.getIndex());
	    DFNode index = cpt.value;
	    cpt.value = new ArrayAccessNode(scope, ref, aa,
					    cpt.get(ref), array, index);
	    
	} else if (expr instanceof FieldAccess) {
	    FieldAccess fa = (FieldAccess)expr;
	    SimpleName fieldName = fa.getName();
	    DFRef ref = scope.lookupField(fieldName.getIdentifier());
	    cpt = processExpression(scope, frame, cpt, fa.getExpression());
	    DFNode obj = cpt.value;
	    cpt.value = new FieldAccessNode(scope, ref, fa,
					    cpt.get(ref), obj);
	    
	} else if (expr instanceof SuperFieldAccess) {
	    SuperFieldAccess sfa = (SuperFieldAccess)expr;
	    SimpleName fieldName = sfa.getName();
	    DFRef ref = scope.lookupField(fieldName.getIdentifier());
	    DFNode obj = cpt.get(scope.lookupSuper());
	    cpt.value = new FieldAccessNode(scope, ref, sfa,
					    cpt.get(ref), obj);
	    
	} else if (expr instanceof QualifiedName) {
	    QualifiedName qn = (QualifiedName)expr;
	    SimpleName fieldName = qn.getName();
	    DFRef ref = scope.lookupField(fieldName.getIdentifier());
	    cpt = processExpression(scope, frame, cpt, qn.getQualifier());
	    DFNode obj = cpt.value;
	    cpt.value = new FieldAccessNode(scope, ref, qn,
					    cpt.get(ref), obj);
	    
	} else if (expr instanceof CastExpression) {
	    CastExpression cast = (CastExpression)expr;
	    Type type = cast.getType();
	    cpt = processExpression(scope, frame, cpt, cast.getExpression());
	    cpt.value = new TypeCastNode(scope, cast, type, cpt.value);
	    
	} else if (expr instanceof ClassInstanceCreation) {
	    ClassInstanceCreation cstr = (ClassInstanceCreation)expr;
	    Type instType = cstr.getType();
	    Expression expr1 = cstr.getExpression();
	    DFNode obj = null;
	    if (expr1 != null) {
		cpt = processExpression(scope, frame, cpt, expr1);
		obj = cpt.value;
	    }
	    CreateObjectNode call =
		new CreateObjectNode(scope, cstr, obj, instType);
	    for (Expression arg : (List<Expression>) cstr.arguments()) {
		cpt = processExpression(scope, frame, cpt, arg);
		call.take(cpt.value);
	    }
	    cpt.value = call;
	    // XXX ignore getAnonymousClassDeclaration()
	    // It will eventually be picked up as MethodDeclaration.
	    
	} else if (expr instanceof ConditionalExpression) {
	    ConditionalExpression cond = (ConditionalExpression)expr;
	    cpt = processExpression(scope, frame, cpt, cond.getExpression());
	    DFNode condValue = cpt.value;
	    cpt = processExpression(scope, frame, cpt, cond.getThenExpression());
	    DFNode trueValue = cpt.value;
	    cpt = processExpression(scope, frame, cpt, cond.getElseExpression());
	    DFNode falseValue = cpt.value;
	    JoinNode join = new JoinNode(scope, null, expr, condValue);
	    join.recv(true, trueValue);
	    join.recv(false, falseValue);
	    cpt.value = join;
	    
	} else if (expr instanceof InstanceofExpression) {
	    InstanceofExpression instof = (InstanceofExpression)expr;
	    Type type = instof.getRightOperand();
	    cpt = processExpression(scope, frame, cpt, instof.getLeftOperand());
	    cpt.value = new InstanceofNode(scope, instof, type, cpt.value);
	    
	} else {
	    // LambdaExpression
	    // MethodReference
	    //  CreationReference
	    //  ExpressionMethodReference
	    //  SuperMethodReference
	    //  TypeMethodReference
	    
	    throw new UnsupportedSyntax(expr);
	}
	
	return cpt;
    }

    /// Statement processors.
    
    @SuppressWarnings("unchecked")
    public DFComponent processVariableDeclarationStatement
	(DFScope scope, DFFrame frame, DFComponent cpt,
	 VariableDeclarationStatement varStmt)
	throws UnsupportedSyntax {
	return processVariableDeclaration
	    (scope, frame, cpt, varStmt.fragments());
    }

    public DFComponent processExpressionStatement
	(DFScope scope, DFFrame frame, DFComponent cpt,
	 ExpressionStatement exprStmt)
	throws UnsupportedSyntax {
	Expression expr = exprStmt.getExpression();
	return processExpression(scope, frame, cpt, expr);
    }

    public DFComponent processIfStatement
	(DFScope scope, DFFrame frame, DFComponent cpt,
	 IfStatement ifStmt)
	throws UnsupportedSyntax {
	Expression expr = ifStmt.getExpression();
	cpt = processExpression(scope, frame, cpt, expr);
	DFNode condValue = cpt.value;
	
	Statement thenStmt = ifStmt.getThenStatement();
	DFFrame thenFrame = new DFFrame(frame, scope.name);
	DFComponent thenCpt = new DFComponent(scope);
	thenCpt = processStatement(scope, thenFrame, thenCpt, thenStmt);
	
	Statement elseStmt = ifStmt.getElseStatement();
	DFFrame elseFrame = null;
	DFComponent elseCpt = null;
	if (elseStmt != null) {
	    elseFrame = new DFFrame(frame, scope.name);
	    elseCpt = new DFComponent(scope);
	    elseCpt = processStatement(scope, elseFrame, elseCpt, elseStmt);
	}

	return processConditional(scope, frame, cpt, ifStmt,
				  condValue,
				  thenFrame, thenCpt,
				  elseFrame, elseCpt);
    }
	
    private DFComponent processCaseStatement
	(DFScope scope, DFFrame frame, DFComponent cpt,
	 ASTNode apt, DFNode caseNode, DFComponent caseCpt) {

	for (Map.Entry<DFRef, DFNode> entry : caseCpt.inputs.entrySet()) {
	    DFRef ref = entry.getKey();
	    DFNode src = entry.getValue();
	    src.accept(cpt.get(ref));
	}
	
	for (Map.Entry<DFRef, DFNode> entry : caseCpt.outputs.entrySet()) {
	    DFRef ref = entry.getKey();
	    DFNode dst = entry.getValue();
	    JoinNode join = new JoinNode(scope, ref, apt, caseNode);
	    join.recv(true, dst);
	    join.close(cpt.get(ref));
	    cpt.put(join);
	}
	
	return cpt;
    }

    @SuppressWarnings("unchecked")
    public DFComponent processSwitchStatement
	(DFScope scope, DFFrame frame, DFComponent cpt,
	 SwitchStatement switchStmt)
	throws UnsupportedSyntax {
	DFFrame switchFrame = new DFFrame(frame, scope.name);
	cpt = processExpression(scope, frame, cpt, switchStmt.getExpression());
	DFNode switchValue = cpt.value;

	SwitchCase switchCase = null;
	CaseNode caseNode = null;
	DFComponent caseCpt = null;
	for (Statement stmt : (List<Statement>) switchStmt.statements()) {
	    if (stmt instanceof SwitchCase) {
		if (caseCpt != null) {
		    // switchCase, caseNode and caseCpt must be non-null.
		    cpt = processCaseStatement(scope, switchFrame, cpt,
					       switchCase, caseNode, caseCpt);
		}
		switchCase = (SwitchCase)stmt;
		caseNode = new CaseNode(scope, stmt, switchValue);
		caseCpt = new DFComponent(scope);
		Expression expr = switchCase.getExpression();
		if (expr != null) {
		    cpt = processExpression(scope, frame, cpt, expr);
		    caseNode.add(cpt.value);
		} else {
		    // "default" case.
		}
	    } else {
		if (caseCpt == null) {
		    // no "case" statement.
		    throw new UnsupportedSyntax(stmt);
		}
		caseCpt = processStatement(scope, switchFrame, caseCpt, stmt);
	    }
	}
	if (caseCpt != null) {
	    cpt = processCaseStatement(scope, switchFrame, cpt,
				       switchCase, caseNode, caseCpt);
	}
	switchFrame.finish(cpt);
	return cpt;
    }
    
    public DFComponent processWhileStatement
	(DFScope scope, DFFrame frame, DFComponent cpt,
	 WhileStatement whileStmt, String label)
	throws UnsupportedSyntax {
	DFFrame loopFrame = new DFFrame(frame, scope.name, label);
	DFComponent loopCpt = new DFComponent(scope);
	loopCpt = processExpression(scope, frame, loopCpt,
				    whileStmt.getExpression());
	DFNode condValue = loopCpt.value;
	loopCpt = processStatement(scope, loopFrame, loopCpt,
				   whileStmt.getBody());
	cpt = processLoop(scope, frame, cpt, whileStmt, 
			  condValue, loopFrame, loopCpt,
			  scope.getAllRefs());
	loopFrame.finish(cpt);
	return cpt;
    }
    
    public DFComponent processDoStatement
	(DFScope scope, DFFrame frame, DFComponent cpt,
	 DoStatement doStmt, String label)
	throws UnsupportedSyntax {
	DFFrame loopFrame = new DFFrame(frame, scope.name, label);
	DFComponent loopCpt = new DFComponent(scope);
	loopCpt = processStatement(scope, loopFrame, loopCpt,
				   doStmt.getBody());
	loopCpt = processExpression(scope, frame, loopCpt,
				    doStmt.getExpression());
	DFNode condValue = loopCpt.value;
	cpt = processLoop(scope, frame, cpt, doStmt, 
			  condValue, loopFrame, loopCpt,
			  scope.getAllRefs());
	loopFrame.finish(cpt);
	return cpt;
    }
    
    @SuppressWarnings("unchecked")
    public DFComponent processForStatement
	(DFScope scope, DFFrame frame, DFComponent cpt,
	 ForStatement forStmt, String label)
	throws UnsupportedSyntax {
	for (Expression init : (List<Expression>) forStmt.initializers()) {
	    cpt = processExpression(scope, frame, cpt, init);
	}
	DFFrame loopFrame = new DFFrame(frame, scope.name, label);
	DFComponent loopCpt = new DFComponent(scope);
	Expression expr = forStmt.getExpression();
	DFNode condValue;
	if (expr != null) {
	    loopCpt = processExpression(scope, frame, loopCpt, expr);
	    condValue = loopCpt.value;
	} else {
	    condValue = new ConstNode(scope, null, "true");
	}
	loopCpt = processStatement(scope, loopFrame, loopCpt,
				   forStmt.getBody());
	for (Expression update : (List<Expression>) forStmt.updaters()) {
	    loopCpt = processExpression(scope, frame, loopCpt, update);
	}
	cpt = processLoop(scope, frame, cpt, forStmt, 
			  condValue, loopFrame, loopCpt,
			  scope.getAllRefs());
	loopFrame.finish(cpt);
	return cpt;
    }
    
    @SuppressWarnings("unchecked")
    public DFComponent processEnhancedForStatement
	(DFScope scope, DFFrame frame, DFComponent cpt,
	 EnhancedForStatement eForStmt, String label)
	throws UnsupportedSyntax {
	DFFrame loopFrame = new DFFrame(frame, scope.name, label);
	DFComponent loopCpt = new DFComponent(scope);
	Expression expr = eForStmt.getExpression();
	loopCpt = processExpression(scope, frame, loopCpt, expr);
	SingleVariableDeclaration decl = eForStmt.getParameter();
	SimpleName varName = decl.getName();
	DFRef ref = scope.lookupVar(varName.getIdentifier());
	DFNode iterValue = new IterNode(scope, ref, expr, loopCpt.value);
	SingleAssignNode assign = new SingleAssignNode(scope, ref, expr);
	assign.take(iterValue);
	cpt.put(assign);
	loopCpt = processStatement(scope, loopFrame, loopCpt,
				   eForStmt.getBody());
	cpt = processLoop(scope, frame, cpt, eForStmt, 
			  iterValue, loopFrame, loopCpt,
			  scope.getAllRefs());
	loopFrame.finish(cpt);
	return cpt;
    }
    
    public DFComponent processStatement
	(DFScope scope, DFFrame frame, DFComponent cpt,
	 Statement stmt)
	throws UnsupportedSyntax {
	return processStatement(scope, frame, cpt, stmt, null);
    }
    
    @SuppressWarnings("unchecked")
    public DFComponent processStatement
	(DFScope scope, DFFrame frame, DFComponent cpt,
	 Statement stmt, String label)
	throws UnsupportedSyntax {
	
	if (stmt instanceof AssertStatement) {
	    // XXX Ignore asserts.
	    
	} else if (stmt instanceof Block) {
	    DFScope child = scope.getChild(stmt);
	    Block block = (Block)stmt;
	    for (Statement cstmt : (List<Statement>) block.statements()) {
		cpt = processStatement(child, frame, cpt, cstmt);
	    }
	    child.finish(cpt);

	} else if (stmt instanceof EmptyStatement) {
	    
	} else if (stmt instanceof VariableDeclarationStatement) {
	    cpt = processVariableDeclarationStatement
		(scope, frame, cpt, (VariableDeclarationStatement)stmt);

	} else if (stmt instanceof ExpressionStatement) {
	    cpt = processExpressionStatement
		(scope, frame, cpt, (ExpressionStatement)stmt);
		
	} else if (stmt instanceof IfStatement) {
	    cpt = processIfStatement
		(scope, frame, cpt, (IfStatement)stmt);
	    
	} else if (stmt instanceof SwitchStatement) {
	    DFScope child = scope.getChild(stmt);
	    cpt = processSwitchStatement
		(child, frame, cpt, (SwitchStatement)stmt);
	    child.finish(cpt);
	    
	} else if (stmt instanceof SwitchCase) {
	    // Invalid "case" placement.
	    throw new UnsupportedSyntax(stmt);
	    
	} else if (stmt instanceof WhileStatement) {
	    DFScope child = scope.getChild(stmt);
	    cpt = processWhileStatement
		(child, frame, cpt, (WhileStatement)stmt, label);
	    child.finish(cpt);
	    
	} else if (stmt instanceof DoStatement) {
	    DFScope child = scope.getChild(stmt);
	    cpt = processDoStatement
		(child, frame, cpt, (DoStatement)stmt, label);
	    child.finish(cpt);
	    
	} else if (stmt instanceof ForStatement) {
	    DFScope child = scope.getChild(stmt);
	    cpt = processForStatement
		(child, frame, cpt, (ForStatement)stmt, label);
	    child.finish(cpt);
	    
	} else if (stmt instanceof EnhancedForStatement) {
	    DFScope child = scope.getChild(stmt);
	    cpt = processEnhancedForStatement
		(child, frame, cpt, (EnhancedForStatement)stmt, label);
	    child.finish(cpt);
	    
	} else if (stmt instanceof ReturnStatement) {
            ReturnStatement rtrnStmt = (ReturnStatement)stmt;
            Expression expr = rtrnStmt.getExpression();
            if (expr != null) {
                cpt = processExpression(scope, frame, cpt, expr);
                DFRef ref = scope.lookupReturn();
                ReturnNode rtrn = new ReturnNode(scope, ref, rtrnStmt);
                rtrn.take(cpt.value);
                frame.add(new DFExit(rtrn, DFFrame.RETURN));
            }
	    for (DFRef ref : scope.getAllRefs()) {
		DFNode node = cpt.get(ref);
		frame.add(new DFExit(node, DFFrame.RETURN));
	    }
	    
	} else if (stmt instanceof BreakStatement) {
	    BreakStatement breakStmt = (BreakStatement)stmt;
	    SimpleName labelName = breakStmt.getLabel();
	    String name = (labelName == null)? null : labelName.getIdentifier();
	    for (DFRef ref : scope.getAllRefs()) {
		DFNode node = cpt.get(ref);
		frame.add(new DFExit(node, name));
	    }
	    
	} else if (stmt instanceof ContinueStatement) {
	    ContinueStatement contStmt = (ContinueStatement)stmt;
	    SimpleName labelName = contStmt.getLabel();
	    String name = (labelName == null)? null : labelName.getIdentifier();
	    for (DFRef ref : scope.getAllRefs()) {
		DFNode node = cpt.get(ref);
		frame.add(new DFExit(node, name, true));
	    }
	    
	} else if (stmt instanceof LabeledStatement) {
	    LabeledStatement labeledStmt = (LabeledStatement)stmt;
	    SimpleName labelName = labeledStmt.getLabel();
	    cpt = processStatement(scope, frame, cpt,
				   labeledStmt.getBody(),
				   labelName.getIdentifier());	   
	    
	} else if (stmt instanceof SynchronizedStatement) {
	    SynchronizedStatement syncStmt = (SynchronizedStatement)stmt;
	    cpt = processStatement(scope, frame, cpt,
				   syncStmt.getBody());

	} else if (stmt instanceof TryStatement) {
	    // XXX Ignore catch statements (for now).
	    TryStatement tryStmt = (TryStatement)stmt;
	    cpt = processStatement(scope, frame, cpt,
				   tryStmt.getBody());
	    Block finBlock = tryStmt.getFinally();
	    if (finBlock != null) {
		cpt = processStatement(scope, frame, cpt, finBlock);
	    }
	    
	} else if (stmt instanceof ThrowStatement) {
	    ThrowStatement throwStmt = (ThrowStatement)stmt;
	    cpt = processExpression(scope, frame, cpt, throwStmt.getExpression());
            ExceptionNode exception = new ExceptionNode(scope, stmt, cpt.value);
            frame.add(new DFExit(exception));
	    for (DFRef ref : scope.getAllRefs()) {
		DFNode node = cpt.get(ref);
		frame.add(new DFExit(node));
	    }
	    
	} else if (stmt instanceof ConstructorInvocation) {
	    // XXX ignore all side effects.
	    ConstructorInvocation ci = (ConstructorInvocation)stmt;
	    for (Expression arg : (List<Expression>) ci.arguments()) {
		cpt = processExpression(scope, frame, cpt, arg);
	    }
	    
	} else if (stmt instanceof SuperConstructorInvocation) {
	    // XXX ignore all side effects.
	    SuperConstructorInvocation sci = (SuperConstructorInvocation)stmt;
	    for (Expression arg : (List<Expression>) sci.arguments()) {
		cpt = processExpression(scope, frame, cpt, arg);
	    }

	} else if (stmt instanceof TypeDeclarationStatement) {
	    // Ignore TypeDeclarationStatement because
	    // it was eventually picked up as MethodDeclaration.
	    
	} else {
	    throw new UnsupportedSyntax(stmt);
	}

	return cpt;
    }

    /**
     * Lists all the variables defined inside a block.
     */
    @SuppressWarnings("unchecked")
    public void buildScope(DFScope scope, Statement ast)
	throws UnsupportedSyntax {
	
	if (ast instanceof AssertStatement) {

	} else if (ast instanceof Block) {
	    Block block = (Block)ast;
	    DFScope child = new DFScope(scope, ast);
	    for (Statement stmt :
		     (List<Statement>) block.statements()) {
		buildScope(child, stmt);
	    }
	    
	} else if (ast instanceof EmptyStatement) {
	    
	} else if (ast instanceof VariableDeclarationStatement) {
	    VariableDeclarationStatement varStmt =
		(VariableDeclarationStatement)ast;
	    // XXX ignore modifiers and dimensions.
	    Type varType = varStmt.getType();
	    for (VariableDeclarationFragment frag :
		     (List<VariableDeclarationFragment>) varStmt.fragments()) {
		SimpleName varName = frag.getName();
		scope.add(varName.getIdentifier(), varType);
		Expression expr = frag.getInitializer();
		if (expr != null) {
		    buildScope(scope, expr);
		}
	    }

	} else if (ast instanceof ExpressionStatement) {
	    ExpressionStatement exprStmt = (ExpressionStatement)ast;
	    Expression expr = exprStmt.getExpression();
	    buildScope(scope, expr);
	    
	} else if (ast instanceof ReturnStatement) {
	    ReturnStatement returnStmt = (ReturnStatement)ast;
	    Expression expr = returnStmt.getExpression();
	    if (expr != null) {
		buildScope(scope, expr);
	    }
	    
	} else if (ast instanceof IfStatement) {
	    IfStatement ifStmt = (IfStatement)ast;
	    Expression expr = ifStmt.getExpression();
	    buildScope(scope, expr);
	    Statement thenStmt = ifStmt.getThenStatement();
	    buildScope(scope, thenStmt);
	    Statement elseStmt = ifStmt.getElseStatement();
	    if (elseStmt != null) {
		buildScope(scope, elseStmt);
	    }
	    
	} else if (ast instanceof SwitchStatement) {
	    SwitchStatement switchStmt = (SwitchStatement)ast;
	    DFScope child = new DFScope(scope, ast);
	    Expression expr = switchStmt.getExpression();
	    buildScope(child, expr);
	    for (Statement stmt :
		     (List<Statement>) switchStmt.statements()) {
		buildScope(child, stmt);
	    }
	    
	} else if (ast instanceof SwitchCase) {
	    SwitchCase switchCase = (SwitchCase)ast;
	    Expression expr = switchCase.getExpression();
	    if (expr != null) {
		buildScope(scope, expr);
	    }
	    
	} else if (ast instanceof WhileStatement) {
	    WhileStatement whileStmt = (WhileStatement)ast;
	    DFScope child = new DFScope(scope, ast);
	    Expression expr = whileStmt.getExpression();
	    buildScope(child, expr);
	    Statement stmt = whileStmt.getBody();
	    buildScope(child, stmt);
	    
	} else if (ast instanceof DoStatement) {
	    DoStatement doStmt = (DoStatement)ast;
	    DFScope child = new DFScope(scope, ast);
	    Statement stmt = doStmt.getBody();
	    buildScope(child, stmt);
	    Expression expr = doStmt.getExpression();
	    buildScope(child, expr);
	    
	} else if (ast instanceof ForStatement) {
	    ForStatement forStmt = (ForStatement)ast;
	    DFScope child = new DFScope(scope, ast);
	    for (Expression init :
		     (List<Expression>) forStmt.initializers()) {
		buildScope(child, init);
	    }
	    Expression expr = forStmt.getExpression();
	    if (expr != null) {
		buildScope(child, expr);
	    }
	    Statement stmt = forStmt.getBody();
	    buildScope(child, stmt);
	    for (Expression update :
		     (List<Expression>) forStmt.updaters()) {
		buildScope(child, update);
	    }
	    
	} else if (ast instanceof EnhancedForStatement) {
	    EnhancedForStatement eForStmt = (EnhancedForStatement)ast;
	    DFScope child = new DFScope(scope, ast);
	    SingleVariableDeclaration decl = eForStmt.getParameter();
	    // XXX ignore modifiers and dimensions.
	    Type varType = decl.getType();
	    SimpleName varName = decl.getName();
	    child.add(varName.getIdentifier(), varType);
	    Expression expr = eForStmt.getExpression();
	    if (expr != null) {
		buildScope(child, expr);
	    }
	    Statement stmt = eForStmt.getBody();
	    buildScope(child, stmt);
	    
	} else if (ast instanceof BreakStatement) {
	    
	} else if (ast instanceof ContinueStatement) {
	    
	} else if (ast instanceof LabeledStatement) {
	    LabeledStatement labeledStmt = (LabeledStatement)ast;
	    Statement stmt = labeledStmt.getBody();
	    buildScope(scope, stmt);
	    
	} else if (ast instanceof SynchronizedStatement) {
	    SynchronizedStatement syncStmt = (SynchronizedStatement)ast;
	    Block block = syncStmt.getBody();
	    buildScope(scope, block);

	} else if (ast instanceof TryStatement) {
	    TryStatement tryStmt = (TryStatement)ast;
	    Block block = tryStmt.getBody();
	    buildScope(scope, block);
	    for (CatchClause cc :
		     (List<CatchClause>) tryStmt.catchClauses()) {
		DFScope child = new DFScope(scope, cc);
		SingleVariableDeclaration decl = cc.getException();
		// XXX ignore modifiers and dimensions.
		Type varType = decl.getType();
		SimpleName varName = decl.getName();
		child.add(varName.getIdentifier(), varType);
		buildScope(child, cc.getBody());
	    }
	    Block finBlock = tryStmt.getFinally();
	    if (finBlock != null) {
		buildScope(scope, finBlock);
	    }
	    
	} else if (ast instanceof ThrowStatement) {
	    ThrowStatement throwStmt = (ThrowStatement)ast;
	    Expression expr = throwStmt.getExpression();
	    if (expr != null) {
		buildScope(scope, expr);
	    }
	    
	} else if (ast instanceof ConstructorInvocation) {
	    ConstructorInvocation ci = (ConstructorInvocation)ast;
	    for (Expression expr :
		     (List<Expression>) ci.arguments()) {
		buildScope(scope, expr);
	    }
	    
	} else if (ast instanceof SuperConstructorInvocation) {
	    SuperConstructorInvocation sci = (SuperConstructorInvocation)ast;
	    for (Expression expr :
		     (List<Expression>) sci.arguments()) {
		buildScope(scope, expr);
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
    public void buildScope(DFScope scope, Expression ast)
	throws UnsupportedSyntax {
	
	if (ast instanceof Annotation) {

	} else if (ast instanceof SimpleName) {
	    SimpleName varName = (SimpleName)ast;
	    DFRef ref = scope.lookupVar(varName.getIdentifier());
	    scope.addInput(ref);
	    
	} else if (ast instanceof ThisExpression) {
	    scope.addInput(scope.lookupThis());
	    
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
	    buildScope(scope, operand);
	    if (op == PrefixExpression.Operator.INCREMENT ||
		op == PrefixExpression.Operator.DECREMENT) {
		buildScopeLeft(scope, operand);
	    }
	    
	} else if (ast instanceof PostfixExpression) {
	    PostfixExpression postfix = (PostfixExpression)ast;
	    PostfixExpression.Operator op = postfix.getOperator();
	    Expression operand = postfix.getOperand();
	    buildScope(scope, operand);
	    if (op == PostfixExpression.Operator.INCREMENT ||
		op == PostfixExpression.Operator.DECREMENT) {
		buildScopeLeft(scope, operand);
	    }
	    
	} else if (ast instanceof InfixExpression) {
	    InfixExpression infix = (InfixExpression)ast;
	    InfixExpression.Operator op = infix.getOperator();
	    Expression loperand = infix.getLeftOperand();
	    buildScope(scope, loperand);
	    Expression roperand = infix.getRightOperand();
	    buildScope(scope, roperand);
    
	} else if (ast instanceof ParenthesizedExpression) {
	    ParenthesizedExpression paren = (ParenthesizedExpression)ast;
	    buildScope(scope, paren.getExpression());
	    
	} else if (ast instanceof Assignment) {
	    Assignment assn = (Assignment)ast;
	    Assignment.Operator op = assn.getOperator();
	    buildScopeLeft(scope, assn.getLeftHandSide());
	    if (op != Assignment.Operator.ASSIGN) {
		buildScope(scope, assn.getLeftHandSide());
	    }
	    buildScope(scope, assn.getRightHandSide());

	} else if (ast instanceof VariableDeclarationExpression) {
	    VariableDeclarationExpression decl = (VariableDeclarationExpression)ast;
	    // XXX ignore modifiers and dimensions.
	    Type varType = decl.getType();
	    for (VariableDeclarationFragment frag :
		     (List<VariableDeclarationFragment>) decl.fragments()) {
		SimpleName varName = frag.getName();
		DFRef ref = scope.add(varName.getIdentifier(), varType);
		Expression expr = frag.getInitializer();
		if (expr != null) {
		    buildScope(scope, expr);
		    scope.addOutput(ref);
		}
	    }

	} else if (ast instanceof MethodInvocation) {
	    MethodInvocation invoke = (MethodInvocation)ast;
	    Expression expr = invoke.getExpression();
	    if (expr != null) {
		buildScope(scope, expr);
	    }
	    for (Expression arg :
		     (List<Expression>) invoke.arguments()) {
		buildScope(scope, arg);
	    }
	    
	} else if (ast instanceof SuperMethodInvocation) {
	    SuperMethodInvocation si = (SuperMethodInvocation)ast;
	    for (Expression arg :
		     (List<Expression>) si.arguments()) {
		buildScope(scope, arg);
	    }
	    
	} else if (ast instanceof ArrayCreation) {
	    ArrayCreation ac = (ArrayCreation)ast;
	    for (Expression dim :
		     (List<Expression>) ac.dimensions()) {
		buildScope(scope, dim);
	    }
	    ArrayInitializer init = ac.getInitializer();
	    if (init != null) {
		buildScope(scope, init);
	    }
	    
	} else if (ast instanceof ArrayInitializer) {
	    ArrayInitializer init = (ArrayInitializer)ast;
	    for (Expression expr :
		     (List<Expression>) init.expressions()) {
		buildScope(scope, expr);
	    }
	    
	} else if (ast instanceof ArrayAccess) {
	    ArrayAccess aa = (ArrayAccess)ast;
	    buildScope(scope, aa.getArray());
	    buildScope(scope, aa.getIndex());
	    DFRef ref = scope.lookupArray();
	    scope.addInput(ref);
	    
	} else if (ast instanceof FieldAccess) {
	    FieldAccess fa = (FieldAccess)ast;
	    SimpleName fieldName = fa.getName();
	    buildScope(scope, fa.getExpression());
	    DFRef ref = scope.lookupField(fieldName.getIdentifier());
	    scope.addInput(ref);
	    
	} else if (ast instanceof SuperFieldAccess) {
	    SuperFieldAccess sfa = (SuperFieldAccess)ast;
	    SimpleName fieldName = sfa.getName();
	    DFRef ref = scope.lookupField(fieldName.getIdentifier());
	    scope.addInput(ref);
	    
	} else if (ast instanceof QualifiedName) {
	    QualifiedName qn = (QualifiedName)ast;
	    SimpleName fieldName = qn.getName();
	    DFRef ref = scope.lookupField(fieldName.getIdentifier());
	    buildScope(scope, qn.getQualifier());
	    scope.addInput(ref);
	    
	} else if (ast instanceof CastExpression) {
	    CastExpression cast = (CastExpression)ast;
	    buildScope(scope, cast.getExpression());
	    
	} else if (ast instanceof ClassInstanceCreation) {
	    ClassInstanceCreation cstr = (ClassInstanceCreation)ast;
	    Expression expr = cstr.getExpression();
	    if (expr != null) {
		buildScope(scope, expr);
	    }
	    for (Expression arg :
		     (List<Expression>) cstr.arguments()) {
		buildScope(scope, arg);
	    }
	    // XXX ignore getAnonymousClassDeclaration()
	    // It will eventually be picked up as MethodDeclaration.
	    
	} else if (ast instanceof ConditionalExpression) {
	    ConditionalExpression cond = (ConditionalExpression)ast;
	    buildScope(scope, cond.getExpression());
	    buildScope(scope, cond.getThenExpression());
	    buildScope(scope, cond.getElseExpression());
	    
	} else if (ast instanceof InstanceofExpression) {
	    InstanceofExpression instof = (InstanceofExpression)ast;
	    buildScope(scope, instof.getLeftOperand());
	    
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
    public void buildScopeLeft(DFScope scope, Expression ast)
	throws UnsupportedSyntax {
	
	if (ast instanceof SimpleName) {
	    SimpleName varName = (SimpleName)ast;
	    DFRef ref = scope.lookupVar(varName.getIdentifier());
	    scope.addOutput(ref);
	    
	} else if (ast instanceof ArrayAccess) {
	    ArrayAccess aa = (ArrayAccess)ast;
	    buildScope(scope, aa.getArray());
	    buildScope(scope, aa.getIndex());
	    DFRef ref = scope.lookupArray();
	    scope.addOutput(ref);
	    
	} else if (ast instanceof FieldAccess) {
	    FieldAccess fa = (FieldAccess)ast;
	    SimpleName fieldName = fa.getName();
	    buildScope(scope, fa.getExpression());
	    DFRef ref = scope.lookupField(fieldName.getIdentifier());
	    scope.addOutput(ref);
	    
	} else if (ast instanceof QualifiedName) {
	    QualifiedName qn = (QualifiedName)ast;
	    SimpleName fieldName = qn.getName();
	    DFRef ref = scope.lookupField(fieldName.getIdentifier());
	    buildScope(scope, qn.getQualifier());
	    scope.addOutput(ref);
	    
	} else {
	    throw new UnsupportedSyntax(ast);
	    
	}
    }
    
    /** 
     * Creates a graph for an entire method.
     */
    @SuppressWarnings("unchecked")
    public DFComponent buildMethodDeclaration
	(DFScope scope, MethodDeclaration method)
	throws UnsupportedSyntax {
	
	DFComponent cpt = new DFComponent(scope);
	// XXX ignore isContructor()
	// XXX ignore getReturnType2()
	int i = 0;
	// XXX ignore isVarargs()
	for (SingleVariableDeclaration decl :
		 (List<SingleVariableDeclaration>) method.parameters()) {
	    DFNode param = new ArgNode(scope, decl, i++);
	    SimpleName paramName = decl.getName();
	    // XXX ignore modifiers and dimensions.
	    Type paramType = decl.getType();
	    DFRef ref = scope.add(paramName.getIdentifier(), paramType);
	    AssignNode assign = new SingleAssignNode(scope, ref, decl);
	    assign.take(param);
	    cpt.put(assign);
	}
	return cpt;
    }

    /// Top-level functions.

    /** 
     * Performs dataflow analysis for a given method.
     */
    public DFScope getMethodGraph(MethodDeclaration method)
	throws UnsupportedSyntax {
	String funcName = method.getName().getFullyQualifiedName();
	Block funcBlock = method.getBody();
	// Ignore method prototypes.
	if (funcBlock == null) return null;
				   
	DFScope scope = new DFScope(funcName);
	
	// Setup an initial scope.
	DFFrame frame = new DFFrame(null, scope.name, DFFrame.RETURN);
	DFComponent cpt = buildMethodDeclaration(scope, method);
	buildScope(scope, funcBlock);
	//scope.dump();

	// Process the function body.
	cpt = processStatement(scope, frame, cpt, funcBlock);
	frame.finish(cpt);

        // Collapse redundant nodes.
	scope.cleanup();
	return scope;
    }

    /// ASTVisitor methods.
    
    public TextExporter exporter;

    public Java2DF(TextExporter exporter) {
	this.exporter = exporter;
    }

    public boolean visit(MethodDeclaration method) {
	String funcName = method.getName().getFullyQualifiedName();
	try {
	    try {
		DFScope scope = getMethodGraph(method);
		if (scope != null) {
		    Utils.logit("success: "+funcName);
		    if (this.exporter != null) {
			this.exporter.writeGraph(scope);
		    }
		}
	    } catch (UnsupportedSyntax e) {
		String astName = e.ast.getClass().getName();
		Utils.logit("fail: "+funcName+" (Unsupported: "+astName+") "+e.ast);
		//e.printStackTrace();
		if (this.exporter != null) {
		    this.exporter.writeFailure(funcName, astName);
		}
	    }
	} catch (IOException e) {
	    e.printStackTrace();
	}
	return true;
    }

    /**
     * Provides a command line interface.
     *
     * Usage: java Java2DF [-o output] input.java ...
     */
    public static void main(String[] args)
	throws IOException {

	// Parse the options.
	List<String> files = new ArrayList<String>();
	OutputStream output = System.out;
	for (int i = 0; i < args.length; i++) {
	    String arg = args[i];
	    if (arg.equals("--")) {
		for (; i < args.length; i++) {
		    files.add(args[i]);
		}
	    } else if (arg.equals("-o")) {
		String path = args[++i];
		try {
		    output = new FileOutputStream(path);
		    Utils.logit("Exporting: "+path);
		} catch (IOException e) {
		    System.err.println("Cannot open output file: "+path);
		}
	    } else if (arg.startsWith("-")) {
	    } else {
		files.add(arg);
	    }
	}

	// Process files.
	TextExporter exporter = new TextExporter(output);
	for (String path : files) {
	    Utils.logit("Parsing: "+path);
	    String src = Utils.readFile(path);
	    exporter.startFile(path);

	    Map<String, String> options = JavaCore.getOptions();
	    JavaCore.setComplianceOptions(JavaCore.VERSION_1_7, options);
	    ASTParser parser = ASTParser.newParser(AST.JLS8);
	    parser.setSource(src.toCharArray());
	    parser.setKind(ASTParser.K_COMPILATION_UNIT);
	    //parser.setResolveBindings(true);
	    parser.setEnvironment(null, null, null, true);
	    parser.setCompilerOptions(options);
	    CompilationUnit cu = (CompilationUnit)parser.createAST(null);
	    
	    Java2DF visitor = new Java2DF(exporter);
	    cu.accept(visitor);
	}
	output.close();
    }
}
