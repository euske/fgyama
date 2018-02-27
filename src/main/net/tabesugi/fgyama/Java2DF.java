/**
 * Java2DF
 * Dataflow analyzer for Java
 */
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;
import org.w3c.dom.*;


//  UnsupportedSyntax
//
class UnsupportedSyntax extends Exception {

    static final long serialVersionUID = 1L;

    public ASTNode ast;

    public UnsupportedSyntax(ASTNode ast) {
	this.ast = ast;
    }
}


// ProgNode: a DFNode that corresponds to an actual program point.
abstract class ProgNode extends DFNode {

    public ASTNode ast;

    public ProgNode(DFScope scope, DFRef ref, ASTNode ast) {
	super(scope, ref);
	this.ast = ast;
    }

    @Override
    public Element toXML(Document document) {
	Element elem = super.toXML(document);
	if (this.ast != null) {
	    Element east = document.createElement("ast");
	    east.setAttribute("type", Integer.toString(this.ast.getNodeType()));
	    east.setAttribute("start", Integer.toString(this.ast.getStartPosition()));
	    east.setAttribute("length", Integer.toString(this.ast.getLength()));
	    elem.appendChild(east);
	}
	return elem;
    }
}

// SingleAssignNode:
class SingleAssignNode extends ProgNode {

    public SingleAssignNode(DFScope scope, DFRef ref, ASTNode ast) {
	super(scope, ref, ast);
    }

    @Override
    public String getType() {
	return "assign";
    }
}

// ArrayAssignNode:
class ArrayAssignNode extends ProgNode {

    public ArrayAssignNode(DFScope scope, DFRef ref, ASTNode ast,
			   DFNode array, DFNode index) {
	super(scope, ref, ast);
	this.accept(array, "array");
	this.accept(index, "index");
    }

    @Override
    public String getType() {
	return "arrayassign";
    }
}

// FieldAssignNode:
class FieldAssignNode extends ProgNode {

    public FieldAssignNode(DFScope scope, DFRef ref, ASTNode ast,
			   DFNode obj) {
	super(scope, ref, ast);
	this.accept(obj, "obj");
    }

    @Override
    public String getType() {
	return "fieldassign";
    }
}

// VarRefNode: represnets a variable reference.
class VarRefNode extends ProgNode {

    public VarRefNode(DFScope scope, DFRef ref, ASTNode ast,
		      DFNode value) {
	super(scope, ref, ast);
	this.accept(value);
    }

    @Override
    public String getType() {
	return "ref";
    }
}

// ArrayAccessNode
class ArrayAccessNode extends ProgNode {

    public ArrayAccessNode(DFScope scope, DFRef ref, ASTNode ast,
			   DFNode array, DFNode index, DFNode value) {
	super(scope, ref, ast);
	this.accept(array, "array");
	this.accept(index, "index");
	this.accept(value);
    }

    @Override
    public String getType() {
	return "arrayaccess";
    }
}

// FieldAccessNode
class FieldAccessNode extends ProgNode {

    public FieldAccessNode(DFScope scope, DFRef ref, ASTNode ast,
			   DFNode obj, DFNode value) {
	super(scope, ref, ast);
	this.accept(obj, "obj");
	this.accept(value);
    }

    @Override
    public String getType() {
	return "fieldaccess";
    }
}

// PrefixNode
class PrefixNode extends ProgNode {

    public PrefixExpression.Operator op;

    public PrefixNode(DFScope scope, DFRef ref, ASTNode ast,
		      PrefixExpression.Operator op, DFNode value) {
	super(scope, ref, ast);
	this.op = op;
	this.accept(value);
    }

    @Override
    public String getType() {
	return "prefix";
    }

    @Override
    public String getData() {
	return this.op.toString();
    }
}

// PostfixNode
class PostfixNode extends ProgNode {

    public PostfixExpression.Operator op;

    public PostfixNode(DFScope scope, DFRef ref, ASTNode ast,
		       PostfixExpression.Operator op, DFNode value) {
	super(scope, ref, ast);
	this.op = op;
	this.accept(value);
    }

    @Override
    public String getType() {
	return "postfix";
    }

    @Override
    public String getData() {
	return this.op.toString();
    }
}

// InfixNode
class InfixNode extends ProgNode {

    public InfixExpression.Operator op;

    public InfixNode(DFScope scope, ASTNode ast,
		     InfixExpression.Operator op,
		     DFNode lvalue, DFNode rvalue) {
	super(scope, null, ast);
	this.op = op;
	this.accept(lvalue, "L");
	this.accept(rvalue, "R");
    }

    @Override
    public String getType() {
	return "infix";
    }

    @Override
    public String getData() {
	return this.op.toString();
    }
}

// TypeCastNode
class TypeCastNode extends ProgNode {

    public Type type;

    public TypeCastNode(DFScope scope, ASTNode ast,
			Type type, DFNode value) {
	super(scope, null, ast);
	this.type = type;
	this.accept(value);
    }

    @Override
    public String getType() {
	return "typecast";
    }

    @Override
    public String getData() {
	return "("+Utils.getTypeName(this.type)+")";
    }
}

// InstanceofNode
class InstanceofNode extends ProgNode {

    public Type type;

    public InstanceofNode(DFScope scope, ASTNode ast,
			  Type type, DFNode value) {
	super(scope, null, ast);
	this.type = type;
	this.accept(value);
    }

    @Override
    public String getType() {
	return "instanceof";
    }

    @Override
    public String getData() {
	return Utils.getTypeName(this.type)+"?";
    }
}

// CaseNode
class CaseNode extends ProgNode {

    public List<DFNode> matches = new ArrayList<DFNode>();

    public CaseNode(DFScope scope, ASTNode ast,
		    DFNode value) {
	super(scope, null, ast);
	this.accept(value);
    }

    @Override
    public String getType() {
	return "case";
    }

    @Override
    public String getData() {
	if (this.matches.isEmpty()) {
	    return "default";
	} else {
	    return "case("+this.matches.size()+")";
	}
    }

    public void addMatch(DFNode node) {
	String label = "match"+this.matches.size();
	this.accept(node, label);
	this.matches.add(node);
    }
}

// AssignOpNode
class AssignOpNode extends ProgNode {

    public Assignment.Operator op;

    public AssignOpNode(DFScope scope, DFRef ref, ASTNode ast,
			Assignment.Operator op,
			DFNode lvalue, DFNode rvalue) {
	super(scope, ref, ast);
	this.op = op;
	this.accept(lvalue, "L");
	this.accept(rvalue, "R");
    }

    @Override
    public String getType() {
	return "assignop";
    }

    @Override
    public String getData() {
	return this.op.toString();
    }
}

// ArgNode: represnets a function argument.
class ArgNode extends ProgNode {

    public int index;

    public ArgNode(DFScope scope, DFRef ref, ASTNode ast,
		   int index) {
	super(scope, ref, ast);
	this.index = index;
    }

    @Override
    public String getType() {
	return "arg";
    }

    @Override
    public String getData() {
	return "arg"+this.index;
    }
}

// ConstNode: represents a constant value.
class ConstNode extends ProgNode {

    public String value;

    public ConstNode(DFScope scope, ASTNode ast, String value) {
	super(scope, null, ast);
	this.value = value;
    }

    @Override
    public String getType() {
	return "const";
    }

    @Override
    public String getData() {
	return this.value;
    }
}

// ArrayValueNode: represents an array.
class ArrayValueNode extends ProgNode {

    public List<DFNode> values = new ArrayList<DFNode>();

    public ArrayValueNode(DFScope scope, ASTNode ast) {
	super(scope, null, ast);
    }

    @Override
    public String getType() {
	return "arrayvalue";
    }

    @Override
    public String getData() {
	return "["+this.values.size()+"]";
    }

    public void addValue(DFNode value) {
	String label = "value"+this.values.size();
	this.accept(value, label);
	this.values.add(value);
    }
}

// JoinNode
class JoinNode extends ProgNode {

    public boolean recvTrue = false;
    public boolean recvFalse = false;

    public JoinNode(DFScope scope, DFRef ref, ASTNode ast,
		      DFNode value) {
	super(scope, ref, ast);
	this.accept(value, "cond");
    }

    @Override
    public String getType() {
	return "join";
    }

    @Override
    public void finish(DFComponent cpt) {
	if (!this.isClosed()) {
	    this.close(cpt.getValue(this.getRef()));
	}
    }

    public void recv(boolean cond, DFNode node) {
	if (cond) {
	    assert(!this.recvTrue);
	    this.recvTrue = true;
	    this.accept(node, "true");
	} else {
	    assert(!this.recvFalse);
	    this.recvFalse = true;
	    this.accept(node, "false");
	}
    }

    public boolean isClosed() {
	return (this.recvTrue && this.recvFalse);
    };

    public void close(DFNode node) {
	if (!this.recvTrue) {
	    assert(this.recvFalse);
	    this.recvTrue = true;
	    this.accept(node, "true");
	}
	if (!this.recvFalse) {
	    assert(this.recvTrue);
	    this.recvFalse = true;
	    this.accept(node, "false");
	}
    }
}

// LoopBeginNode
class LoopBeginNode extends ProgNode {

    public LoopBeginNode(DFScope scope, DFRef ref, ASTNode ast,
			 DFNode enter) {
	super(scope, ref, ast);
	this.accept(enter, "enter");
    }

    @Override
    public String getType() {
	return "begin";
    }

    public void setRepeat(DFNode repeat) {
	this.accept(repeat, "repeat");
    }

    public void setEnd(LoopEndNode end) {
	this.accept(end, "_end");
    }
}

// LoopEndNode
class LoopEndNode extends ProgNode {

    public LoopEndNode(DFScope scope, DFRef ref, ASTNode ast,
		       DFNode value) {
	super(scope, ref, ast);
	this.accept(value, "cond");
    }

    @Override
    public String getType() {
	return "end";
    }

    public void setBegin(LoopBeginNode begin) {
	this.accept(begin, "_begin");
    }
}

// LoopRepeatNode
class LoopRepeatNode extends ProgNode {

    public LoopRepeatNode(DFScope scope, DFRef ref, ASTNode ast) {
	super(scope, ref, ast);
    }

    @Override
    public String getType() {
	return "repeat";
    }

    public void setLoop(DFNode end) {
	this.accept(end, "_loop");
    }
}

// IterNode
class IterNode extends ProgNode {

    public IterNode(DFScope scope, DFRef ref, ASTNode ast,
		    DFNode value) {
	super(scope, ref, ast);
	this.accept(value);
    }

    @Override
    public String getType() {
	return "iter";
    }
}

// CallNode
abstract class CallNode extends ProgNode {

    public List<DFNode> args;
    public DFNode exception;

    public CallNode(DFScope scope, DFRef ref, ASTNode ast,
		    DFNode obj) {
	super(scope, ref, ast);
	this.args = new ArrayList<DFNode>();
        this.exception = null;
	if (obj != null) {
	    this.accept(obj, "obj");
	}
    }

    @Override
    public String getType() {
	return "call";
    }

    public void addArg(DFNode arg) {
	String label = "arg"+this.args.size();
	this.accept(arg, label);
	this.args.add(arg);
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

    @Override
    public String getData() {
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

    @Override
    public String getData() {
	return "new "+Utils.getTypeName(this.type);
    }
}

// ReturnNode: represents a return value.
class ReturnNode extends ProgNode {

    public ReturnNode(DFScope scope, ASTNode ast, DFNode value) {
	super(scope, scope.lookupReturn(), ast);
	this.accept(value);
    }

    @Override
    public String getType() {
	return "return";
    }
}

// ExceptionNode
class ExceptionNode extends ProgNode {

    public ExceptionNode(DFScope scope, ASTNode ast, DFNode value) {
	super(scope, null, ast);
	this.accept(value);
    }

    @Override
    public String getType() {
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
	 DFNode condValue, DFComponent trueCpt, DFComponent falseCpt) {

	// outRefs: all the references from both component.
	List<DFRef> outRefs = new ArrayList<DFRef>();
	if (trueCpt != null) {
	    for (DFRef ref : trueCpt.getInputRefs()) {
		DFNode src = trueCpt.getInput(ref);
		assert src != null;
		src.accept(cpt.getValue(ref));
	    }
	    outRefs.addAll(Arrays.asList(trueCpt.getOutputRefs()));
	}
	if (falseCpt != null) {
	    for (DFRef ref : falseCpt.getInputRefs()) {
		DFNode src = falseCpt.getInput(ref);
		assert src != null;
		src.accept(cpt.getValue(ref));
	    }
	    outRefs.addAll(Arrays.asList(falseCpt.getOutputRefs()));
	}

	// Attach a JoinNode to each variable.
	Set<DFRef> used = new HashSet<DFRef>();
	for (DFRef ref : outRefs) {
	    if (used.contains(ref)) continue;
	    used.add(ref);
	    JoinNode join = new JoinNode(scope, ref, ast, condValue);
	    if (trueCpt != null) {
		DFNode dst = trueCpt.getOutput(ref);
		if (dst != null) {
		    join.recv(true, dst);
		}
	    }
	    if (falseCpt != null) {
		DFNode dst = falseCpt.getOutput(ref);
		if (dst != null) {
		    join.recv(false, dst);
		}
	    }
	    if (!join.isClosed()) {
		join.close(cpt.getValue(ref));
	    }
	    cpt.setOutput(join);
	}

	// Take care of exits.
	if (trueCpt != null) {
	    for (DFExit exit : trueCpt.getExits()) {
                DFNode node = exit.getNode();
		JoinNode join = new JoinNode(scope, node.getRef(), null, condValue);
		join.recv(true, node);
		cpt.addExit(exit.wrap(join));
	    }
	}
	if (falseCpt != null) {
	    for (DFExit exit : falseCpt.getExits()) {
                DFNode node = exit.getNode();
		JoinNode join = new JoinNode(scope, node.getRef(), null, condValue);
		join.recv(false, node);
		cpt.addExit(exit.wrap(join));
	    }
	}

	return cpt;
    }

    /**
     * Expands the graph for the loop variables.
     */
    public DFComponent processLoop
	(DFScope scope, DFFrame frame, DFComponent cpt, ASTNode ast,
	 DFNode condValue, DFFrame loopFrame, DFComponent loopCpt,
	 boolean preTest)
	throws UnsupportedSyntax {

	// Add four nodes for each loop variable.
	Map<DFRef, LoopBeginNode> begins = new HashMap<DFRef, LoopBeginNode>();
	Map<DFRef, LoopRepeatNode> repeats = new HashMap<DFRef, LoopRepeatNode>();
	Map<DFRef, DFNode> ends = new HashMap<DFRef, DFNode>();
	DFRef[] loopRefs = loopFrame.getInsAndOuts();
	for (DFRef ref : loopRefs) {
	    DFNode src = cpt.getValue(ref);
	    LoopBeginNode begin = new LoopBeginNode(scope, ref, ast, src);
	    LoopRepeatNode repeat = new LoopRepeatNode(scope, ref, ast);
	    LoopEndNode end = new LoopEndNode(scope, ref, ast, condValue);
	    begin.setEnd(end);
	    end.setBegin(begin);
	    begins.put(ref, begin);
	    ends.put(ref, end);
	    repeats.put(ref, repeat);
	}

	if (preTest) {  // Repeat -> [S] -> Begin -> End
	    // Connect the repeats to the loop inputs.
	    for (DFRef ref : loopCpt.getInputRefs()) {
		DFNode input = loopCpt.getInput(ref);
		DFNode src = repeats.get(ref);
		if (src == null) {
		    src = cpt.getValue(ref);
		}
		input.accept(src);
	    }
	    // Connect the loop outputs to the begins.
	    for (DFRef ref : loopCpt.getOutputRefs()) {
		DFNode output = loopCpt.getOutput(ref);
		LoopBeginNode begin = begins.get(ref);
		if (begin != null) {
		    begin.setRepeat(output);
		} else {
		    //assert !loopRefs.contains(ref);
		    cpt.setOutput(output);
		}
	    }
	    // Connect the beings and ends.
	    for (DFRef ref : loopRefs) {
		LoopBeginNode begin = begins.get(ref);
		DFNode end = ends.get(ref);
		end.accept(begin);
	    }

	} else {  // Begin -> [S] -> End -> Repeat
	    // Connect the begins to the loop inputs.
	    for (DFRef ref : loopCpt.getInputRefs()) {
		DFNode input = loopCpt.getInput(ref);
		DFNode src = begins.get(ref);
		if (src == null) {
		    src = cpt.getValue(ref);
		}
		input.accept(src);
	    }
	    // Connect the loop outputs to the ends.
	    for (DFRef ref : loopCpt.getOutputRefs()) {
		DFNode output = loopCpt.getOutput(ref);
		DFNode dst = ends.get(ref);
		if (dst != null) {
		    dst.accept(output);
		} else {
		    //assert !loopRefs.contains(ref);
		    cpt.setOutput(output);
		}
	    }
	    // Connect the repeats and begins.
	    for (DFRef ref : loopRefs) {
		LoopRepeatNode repeat = repeats.get(ref);
		LoopBeginNode begin = begins.get(ref);
		begin.setRepeat(repeat);
	    }
	}

	// Redirect the continue statements.
	for (DFExit exit : loopCpt.getExits()) {
	    if (exit.isCont() && exit.getFrame() == loopFrame) {
		DFNode node = exit.getNode();
		DFNode end = ends.get(node.getRef());
		if (end == null) {
		    end = cpt.getValue(node.getRef());
		}
		if (node instanceof JoinNode) {
		    ((JoinNode)node).close(end);
		}
		ends.put(node.getRef(), node);
	    } else {
		cpt.addExit(exit);
	    }
	}

	// Closing the loop.
	for (DFRef ref : loopRefs) {
	    DFNode end = ends.get(ref);
	    LoopRepeatNode repeat = repeats.get(ref);
	    cpt.setOutput(end);
	    repeat.setLoop(end);
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
	    DFRef ref = scope.lookupVar(frag.getName());
	    Expression init = frag.getInitializer();
	    if (init != null) {
		cpt = processExpression(scope, frame, cpt, init);
		DFNode assign = new SingleAssignNode(scope, ref, frag);
		assign.accept(cpt.getRValue());
		cpt.setOutput(assign);
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

	if (expr instanceof Name) {
	    Name name = (Name)expr;
	    if (name.isSimpleName()) {
		DFRef ref = scope.lookupVar((SimpleName)name);
		cpt.setLValue(new SingleAssignNode(scope, ref, expr));
	    } else {
		// QualifiedName == FieldAccess
		QualifiedName qn = (QualifiedName)name;
		SimpleName fieldName = qn.getName();
		cpt = processExpression(scope, frame, cpt, qn.getQualifier());
		DFNode obj = cpt.getRValue();
		DFRef ref = scope.lookupField(fieldName);
		cpt.setLValue(new FieldAssignNode(scope, ref, expr, obj));
	    }

	} else if (expr instanceof ArrayAccess) {
	    ArrayAccess aa = (ArrayAccess)expr;
	    cpt = processExpression(scope, frame, cpt, aa.getArray());
	    DFNode array = cpt.getRValue();
	    cpt = processExpression(scope, frame, cpt, aa.getIndex());
	    DFNode index = cpt.getRValue();
	    DFRef ref = scope.lookupArray();
	    cpt.setLValue(new ArrayAssignNode(scope, ref, expr, array, index));

	} else if (expr instanceof FieldAccess) {
	    FieldAccess fa = (FieldAccess)expr;
	    SimpleName fieldName = fa.getName();
	    cpt = processExpression(scope, frame, cpt, fa.getExpression());
	    DFNode obj = cpt.getRValue();
	    DFRef ref = scope.lookupField(fieldName);
	    cpt.setLValue(new FieldAssignNode(scope, ref, expr, obj));

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

	} else if (expr instanceof Name) {
	    Name name = (Name)expr;
	    if (name.isSimpleName()) {
		DFRef ref = scope.lookupVar((SimpleName)name);
		cpt.setRValue(new VarRefNode(scope, ref, expr, cpt.getValue(ref)));
	    } else {
		// QualifiedName == FieldAccess
		QualifiedName qn = (QualifiedName)name;
		SimpleName fieldName = qn.getName();
		cpt = processExpression(scope, frame, cpt, qn.getQualifier());
		DFNode obj = cpt.getRValue();
		DFRef ref = scope.lookupField(fieldName);
		cpt.setRValue(new FieldAccessNode(scope, ref, qn,
						  cpt.getValue(ref), obj));
	    }

	} else if (expr instanceof ThisExpression) {
	    DFRef ref = scope.lookupThis();
	    cpt.setRValue(new VarRefNode(scope, ref, expr, cpt.getValue(ref)));

	} else if (expr instanceof BooleanLiteral) {
	    boolean value = ((BooleanLiteral)expr).booleanValue();
	    cpt.setRValue(new ConstNode(scope, expr, Boolean.toString(value)));

	} else if (expr instanceof CharacterLiteral) {
	    char value = ((CharacterLiteral)expr).charValue();
	    cpt.setRValue(new ConstNode(scope, expr, Character.toString(value)));

	} else if (expr instanceof NullLiteral) {
	    cpt.setRValue(new ConstNode(scope, expr, "null"));

	} else if (expr instanceof NumberLiteral) {
	    String value = ((NumberLiteral)expr).getToken();
	    cpt.setRValue(new ConstNode(scope, expr, value));

	} else if (expr instanceof StringLiteral) {
	    String value = ((StringLiteral)expr).getLiteralValue();
	    cpt.setRValue(new ConstNode(scope, expr, value));

	} else if (expr instanceof TypeLiteral) {
	    Type value = ((TypeLiteral)expr).getType();
	    cpt.setRValue(new ConstNode(scope, expr, Utils.getTypeName(value)));

	} else if (expr instanceof PrefixExpression) {
	    PrefixExpression prefix = (PrefixExpression)expr;
	    PrefixExpression.Operator op = prefix.getOperator();
	    Expression operand = prefix.getOperand();
	    cpt = processExpression(scope, frame, cpt, operand);
	    if (op == PrefixExpression.Operator.INCREMENT ||
		op == PrefixExpression.Operator.DECREMENT) {
		cpt = processAssignment(scope, frame, cpt, operand);
		DFNode assign = cpt.getLValue();
		DFNode value = new PrefixNode(scope, assign.getRef(), expr, op, cpt.getRValue());
		assign.accept(value);
		cpt.setOutput(assign);
		cpt.setRValue(value);
	    } else {
		cpt.setRValue(new PrefixNode(scope, null, expr, op, cpt.getRValue()));
	    }

	} else if (expr instanceof PostfixExpression) {
	    PostfixExpression postfix = (PostfixExpression)expr;
	    PostfixExpression.Operator op = postfix.getOperator();
	    Expression operand = postfix.getOperand();
	    cpt = processAssignment(scope, frame, cpt, operand);
	    if (op == PostfixExpression.Operator.INCREMENT ||
		op == PostfixExpression.Operator.DECREMENT) {
		DFNode assign = cpt.getLValue();
		cpt = processExpression(scope, frame, cpt, operand);
		assign.accept(new PostfixNode(scope, assign.getRef(), expr, op, cpt.getRValue()));
		cpt.setOutput(assign);
	    }

	} else if (expr instanceof InfixExpression) {
	    InfixExpression infix = (InfixExpression)expr;
	    InfixExpression.Operator op = infix.getOperator();
	    cpt = processExpression(scope, frame, cpt, infix.getLeftOperand());
	    DFNode lvalue = cpt.getRValue();
	    cpt = processExpression(scope, frame, cpt, infix.getRightOperand());
	    DFNode rvalue = cpt.getRValue();
	    cpt.setRValue(new InfixNode(scope, expr, op, lvalue, rvalue));

	} else if (expr instanceof ParenthesizedExpression) {
	    ParenthesizedExpression paren = (ParenthesizedExpression)expr;
	    cpt = processExpression(scope, frame, cpt, paren.getExpression());

	} else if (expr instanceof Assignment) {
	    Assignment assn = (Assignment)expr;
	    Assignment.Operator op = assn.getOperator();
	    cpt = processAssignment(scope, frame, cpt, assn.getLeftHandSide());
	    DFNode assign = cpt.getLValue();
	    cpt = processExpression(scope, frame, cpt, assn.getRightHandSide());
	    DFNode rvalue = cpt.getRValue();
	    DFNode lvalue = cpt.getValue(assign.getRef());
	    assign.accept(new AssignOpNode(scope, assign.getRef(), assn, op, lvalue, rvalue));
	    cpt.setOutput(assign);
	    cpt.setRValue(assign);

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
		obj = cpt.getRValue();
	    }
	    SimpleName methodName = invoke.getName();
	    MethodCallNode call = new MethodCallNode
		(scope, invoke, obj, methodName.getIdentifier());
	    for (Expression arg : (List<Expression>) invoke.arguments()) {
		cpt = processExpression(scope, frame, cpt, arg);
		call.addArg(cpt.getRValue());
	    }
	    cpt.setRValue(call);
            if (call.exception != null) {
		DFFrame dstFrame = frame.find(DFFrame.TRY);
		cpt.addExit(new DFExit(call.exception, dstFrame));
            }

	} else if (expr instanceof SuperMethodInvocation) {
	    SuperMethodInvocation si = (SuperMethodInvocation)expr;
	    SimpleName methodName = si.getName();
	    DFNode obj = cpt.getValue(scope.lookupSuper());
	    MethodCallNode call = new MethodCallNode
		(scope, si, obj, methodName.getIdentifier());
	    for (Expression arg : (List<Expression>) si.arguments()) {
		cpt = processExpression(scope, frame, cpt, arg);
		call.addArg(cpt.getRValue());
	    }
	    cpt.setRValue(call);

	} else if (expr instanceof ArrayCreation) {
	    ArrayCreation ac = (ArrayCreation)expr;
	    for (Expression dim : (List<Expression>) ac.dimensions()) {
		// XXX cpt.getRValue() is not used (for now).
		cpt = processExpression(scope, frame, cpt, dim);
	    }
	    ArrayInitializer init = ac.getInitializer();
	    if (init != null) {
		cpt = processExpression(scope, frame, cpt, init);
	    } else {
		cpt.setRValue(new ArrayValueNode(scope, ac));
	    }

	} else if (expr instanceof ArrayInitializer) {
	    ArrayInitializer init = (ArrayInitializer)expr;
	    ArrayValueNode arr = new ArrayValueNode(scope, init);
	    for (Expression expr1 : (List<Expression>) init.expressions()) {
		cpt = processExpression(scope, frame, cpt, expr1);
		arr.addValue(cpt.getRValue());
	    }
	    cpt.setRValue(arr);
	    // XXX array ref is not used.

	} else if (expr instanceof ArrayAccess) {
	    ArrayAccess aa = (ArrayAccess)expr;
	    DFRef ref = scope.lookupArray();
	    cpt = processExpression(scope, frame, cpt, aa.getArray());
	    DFNode array = cpt.getRValue();
	    cpt = processExpression(scope, frame, cpt, aa.getIndex());
	    DFNode index = cpt.getRValue();
	    cpt.setRValue(new ArrayAccessNode(scope, ref, aa,
					      array, index, cpt.getValue(ref)));

	} else if (expr instanceof FieldAccess) {
	    FieldAccess fa = (FieldAccess)expr;
	    SimpleName fieldName = fa.getName();
	    cpt = processExpression(scope, frame, cpt, fa.getExpression());
	    DFNode obj = cpt.getRValue();
	    DFRef ref = scope.lookupField(fieldName);
	    cpt.setRValue(new FieldAccessNode(scope, ref, fa,
					      cpt.getValue(ref), obj));

	} else if (expr instanceof SuperFieldAccess) {
	    SuperFieldAccess sfa = (SuperFieldAccess)expr;
	    SimpleName fieldName = sfa.getName();
	    DFNode obj = cpt.getValue(scope.lookupSuper());
	    DFRef ref = scope.lookupField(fieldName);
	    cpt.setRValue(new FieldAccessNode(scope, ref, sfa,
					      cpt.getValue(ref), obj));

	} else if (expr instanceof CastExpression) {
	    CastExpression cast = (CastExpression)expr;
	    Type type = cast.getType();
	    cpt = processExpression(scope, frame, cpt, cast.getExpression());
	    cpt.setRValue(new TypeCastNode(scope, cast, type, cpt.getRValue()));

	} else if (expr instanceof ClassInstanceCreation) {
	    ClassInstanceCreation cstr = (ClassInstanceCreation)expr;
	    Type instType = cstr.getType();
	    Expression expr1 = cstr.getExpression();
	    DFNode obj = null;
	    if (expr1 != null) {
		cpt = processExpression(scope, frame, cpt, expr1);
		obj = cpt.getRValue();
	    }
	    CreateObjectNode call =
		new CreateObjectNode(scope, cstr, obj, instType);
	    for (Expression arg : (List<Expression>) cstr.arguments()) {
		cpt = processExpression(scope, frame, cpt, arg);
		call.addArg(cpt.getRValue());
	    }
	    cpt.setRValue(call);
	    // Ignore getAnonymousClassDeclaration() here.
	    // It will eventually be picked up as MethodDeclaration.

	} else if (expr instanceof ConditionalExpression) {
	    ConditionalExpression cond = (ConditionalExpression)expr;
	    cpt = processExpression(scope, frame, cpt, cond.getExpression());
	    DFNode condValue = cpt.getRValue();
	    cpt = processExpression(scope, frame, cpt, cond.getThenExpression());
	    DFNode trueValue = cpt.getRValue();
	    cpt = processExpression(scope, frame, cpt, cond.getElseExpression());
	    DFNode falseValue = cpt.getRValue();
	    JoinNode join = new JoinNode(scope, null, expr, condValue);
	    join.recv(true, trueValue);
	    join.recv(false, falseValue);
	    cpt.setRValue(join);

	} else if (expr instanceof InstanceofExpression) {
	    InstanceofExpression instof = (InstanceofExpression)expr;
	    Type type = instof.getRightOperand();
	    cpt = processExpression(scope, frame, cpt, instof.getLeftOperand());
	    cpt.setRValue(new InstanceofNode(scope, instof, type, cpt.getRValue()));

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
    public DFComponent processBlock
	(DFScope scope, DFFrame frame, DFComponent cpt,
	 Block block)
	throws UnsupportedSyntax {
	DFScope childScope = scope.getChild(block);
	for (Statement cstmt : (List<Statement>) block.statements()) {
	    cpt = processStatement(childScope, frame, cpt, cstmt);
	}
	return cpt;
    }

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
	DFNode condValue = cpt.getRValue();

	Statement thenStmt = ifStmt.getThenStatement();
	DFComponent thenCpt = new DFComponent(scope);
	thenCpt = processStatement(scope, frame, thenCpt, thenStmt);

	Statement elseStmt = ifStmt.getElseStatement();
	DFComponent elseCpt = null;
	if (elseStmt != null) {
	    elseCpt = new DFComponent(scope);
	    elseCpt = processStatement(scope, frame, elseCpt, elseStmt);
	}
	return processConditional(scope, frame, cpt, ifStmt,
				  condValue, thenCpt, elseCpt);
    }

    private DFComponent processCaseStatement
	(DFScope scope, DFFrame frame, DFComponent cpt,
	 ASTNode apt, DFNode caseNode, DFComponent caseCpt) {

	for (DFRef ref : caseCpt.getInputRefs()) {
	    DFNode src = caseCpt.getInput(ref);
	    src.accept(cpt.getValue(ref));
	}

	for (DFRef ref : caseCpt.getOutputRefs()) {
	    DFNode dst = caseCpt.getOutput(ref);
	    JoinNode join = new JoinNode(scope, ref, apt, caseNode);
	    join.recv(true, dst);
	    join.close(cpt.getValue(ref));
	    cpt.setOutput(join);
	}

	return cpt;
    }

    @SuppressWarnings("unchecked")
    public DFComponent processSwitchStatement
	(DFScope scope, DFFrame frame, DFComponent cpt,
	 SwitchStatement switchStmt)
	throws UnsupportedSyntax {
	DFScope switchScope = scope.getChild(switchStmt);
	DFFrame switchFrame = frame.getChild(switchStmt);
	cpt = processExpression(scope, frame, cpt, switchStmt.getExpression());
	DFNode switchValue = cpt.getRValue();

	SwitchCase switchCase = null;
	CaseNode caseNode = null;
	DFComponent caseCpt = null;
	for (Statement stmt : (List<Statement>) switchStmt.statements()) {
	    if (stmt instanceof SwitchCase) {
		if (caseCpt != null) {
		    // switchCase, caseNode and caseCpt must be non-null.
		    cpt = processCaseStatement(switchScope, switchFrame, cpt,
					       switchCase, caseNode, caseCpt);
		}
		switchCase = (SwitchCase)stmt;
		caseNode = new CaseNode(switchScope, stmt, switchValue);
		caseCpt = new DFComponent(switchScope);
		Expression expr = switchCase.getExpression();
		if (expr != null) {
		    cpt = processExpression(switchScope, frame, cpt, expr);
		    caseNode.addMatch(cpt.getRValue());
		} else {
		    // "default" case.
		}
	    } else {
		if (caseCpt == null) {
		    // no "case" statement.
		    throw new UnsupportedSyntax(stmt);
		}
		caseCpt = processStatement(switchScope, switchFrame, caseCpt, stmt);
	    }
	}
	if (caseCpt != null) {
	    cpt = processCaseStatement(switchScope, switchFrame, cpt,
				       switchCase, caseNode, caseCpt);
	}
	cpt.endFrame(switchFrame);
	return cpt;
    }

    public DFComponent processWhileStatement
	(DFScope scope, DFFrame frame, DFComponent cpt,
	 WhileStatement whileStmt)
	throws UnsupportedSyntax {
	DFScope loopScope = scope.getChild(whileStmt);
	DFFrame loopFrame = frame.getChild(whileStmt);
	DFComponent loopCpt = new DFComponent(loopScope);
	loopCpt = processExpression(loopScope, frame, loopCpt,
				    whileStmt.getExpression());
	DFNode condValue = loopCpt.getRValue();
	loopCpt = processStatement(loopScope, loopFrame, loopCpt,
				   whileStmt.getBody());
	cpt = processLoop(loopScope, frame, cpt, whileStmt,
			  condValue, loopFrame, loopCpt, true);
	cpt.endFrame(loopFrame);
	return cpt;
    }

    public DFComponent processDoStatement
	(DFScope scope, DFFrame frame, DFComponent cpt,
	 DoStatement doStmt)
	throws UnsupportedSyntax {
	DFScope loopScope = scope.getChild(doStmt);
	DFFrame loopFrame = frame.getChild(doStmt);
	DFComponent loopCpt = new DFComponent(loopScope);
	loopCpt = processStatement(loopScope, loopFrame, loopCpt,
				   doStmt.getBody());
	loopCpt = processExpression(loopScope, loopFrame, loopCpt,
				    doStmt.getExpression());
	DFNode condValue = loopCpt.getRValue();
	cpt = processLoop(loopScope, frame, cpt, doStmt,
			  condValue, loopFrame, loopCpt, false);
	cpt.endFrame(loopFrame);
	return cpt;
    }

    @SuppressWarnings("unchecked")
    public DFComponent processForStatement
	(DFScope scope, DFFrame frame, DFComponent cpt,
	 ForStatement forStmt)
	throws UnsupportedSyntax {
	DFScope loopScope = scope.getChild(forStmt);
	DFFrame loopFrame = frame.getChild(forStmt);
	DFComponent loopCpt = new DFComponent(loopScope);
	for (Expression init : (List<Expression>) forStmt.initializers()) {
	    cpt = processExpression(loopScope, frame, cpt, init);
	}
	Expression expr = forStmt.getExpression();
	DFNode condValue;
	if (expr != null) {
	    loopCpt = processExpression(loopScope, loopFrame, loopCpt, expr);
	    condValue = loopCpt.getRValue();
	} else {
	    condValue = new ConstNode(loopScope, null, "true");
	}
	loopCpt = processStatement(loopScope, loopFrame, loopCpt,
				   forStmt.getBody());
	for (Expression update : (List<Expression>) forStmt.updaters()) {
	    loopCpt = processExpression(loopScope, loopFrame, loopCpt, update);
	}
	cpt = processLoop(loopScope, frame, cpt, forStmt,
			  condValue, loopFrame, loopCpt, true);
	cpt.endFrame(loopFrame);
	return cpt;
    }

    @SuppressWarnings("unchecked")
    public DFComponent processEnhancedForStatement
	(DFScope scope, DFFrame frame, DFComponent cpt,
	 EnhancedForStatement eForStmt)
	throws UnsupportedSyntax {
	DFScope loopScope = scope.getChild(eForStmt);
	DFFrame loopFrame = frame.getChild(eForStmt);
	DFComponent loopCpt = new DFComponent(loopScope);
	Expression expr = eForStmt.getExpression();
	loopCpt = processExpression(loopScope, frame, loopCpt, expr);
	SingleVariableDeclaration decl = eForStmt.getParameter();
	DFRef ref = loopScope.lookupVar(decl.getName());
	DFNode iterValue = new IterNode(loopScope, ref, expr, loopCpt.getRValue());
	SingleAssignNode assign = new SingleAssignNode(loopScope, ref, expr);
	assign.accept(iterValue);
	cpt.setOutput(assign);
	loopCpt = processStatement(loopScope, loopFrame, loopCpt,
				   eForStmt.getBody());
	cpt = processLoop(loopScope, frame, cpt, eForStmt,
			  iterValue, loopFrame, loopCpt, true);
	cpt.endFrame(loopFrame);
	return cpt;
    }

    @SuppressWarnings("unchecked")
    public DFComponent processStatement
	(DFScope scope, DFFrame frame, DFComponent cpt,
	 Statement stmt)
	throws UnsupportedSyntax {

	if (stmt instanceof AssertStatement) {
	    // XXX Ignore asserts.

	} else if (stmt instanceof Block) {
	    cpt = processBlock
		(scope, frame, cpt, (Block)stmt);

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
	    cpt = processSwitchStatement
		(scope, frame, cpt, (SwitchStatement)stmt);

	} else if (stmt instanceof SwitchCase) {
	    // Invalid "case" placement.
	    throw new UnsupportedSyntax(stmt);

	} else if (stmt instanceof WhileStatement) {
	    cpt = processWhileStatement
		(scope, frame, cpt, (WhileStatement)stmt);

	} else if (stmt instanceof DoStatement) {
	    cpt = processDoStatement
		(scope, frame, cpt, (DoStatement)stmt);

	} else if (stmt instanceof ForStatement) {
	    cpt = processForStatement
		(scope, frame, cpt, (ForStatement)stmt);

	} else if (stmt instanceof EnhancedForStatement) {
	    cpt = processEnhancedForStatement
		(scope, frame, cpt, (EnhancedForStatement)stmt);

	} else if (stmt instanceof ReturnStatement) {
            ReturnStatement rtrnStmt = (ReturnStatement)stmt;
	    DFFrame dstFrame = frame.find(DFFrame.METHOD);
            Expression expr = rtrnStmt.getExpression();
            if (expr != null) {
                cpt = processExpression(scope, frame, cpt, expr);
                ReturnNode rtrn = new ReturnNode(scope, rtrnStmt, cpt.getRValue());
                cpt.addExit(new DFExit(rtrn, dstFrame));
            }
	    for (DFFrame frm = frame; frm != null; frm = frm.getParent()) {
		for (DFRef ref : frm.getOutputs()) {
		    cpt.addExit(new DFExit(cpt.getValue(ref), dstFrame));
		}
		if (frm == dstFrame) break;
	    }

	} else if (stmt instanceof BreakStatement) {
	    BreakStatement breakStmt = (BreakStatement)stmt;
	    SimpleName labelName = breakStmt.getLabel();
	    String dstLabel = (labelName == null)? null : labelName.getIdentifier();
	    DFFrame dstFrame = frame.find(dstLabel);
	    for (DFFrame frm = frame; frm != null; frm = frm.getParent()) {
		for (DFRef ref : frm.getOutputs()) {
		    cpt.addExit(new DFExit(cpt.getValue(ref), dstFrame));
		}
		if (frm == dstFrame) break;
	    }

	} else if (stmt instanceof ContinueStatement) {
	    ContinueStatement contStmt = (ContinueStatement)stmt;
	    SimpleName labelName = contStmt.getLabel();
	    String dstLabel = (labelName == null)? null : labelName.getIdentifier();
	    DFFrame dstFrame = frame.find(dstLabel);
	    for (DFFrame frm = frame; frm != null; frm = frm.getParent()) {
		for (DFRef ref : frm.getOutputs()) {
		    cpt.addExit(new DFExit(cpt.getValue(ref), dstFrame, true));
		}
		if (frm == dstFrame) break;
	    }

	} else if (stmt instanceof LabeledStatement) {
	    LabeledStatement labeledStmt = (LabeledStatement)stmt;
	    DFFrame labeledFrame = frame.getChild(labeledStmt);
	    cpt = processStatement(scope, labeledFrame, cpt,
				   labeledStmt.getBody());

	} else if (stmt instanceof SynchronizedStatement) {
	    SynchronizedStatement syncStmt = (SynchronizedStatement)stmt;
	    cpt = processStatement(scope, frame, cpt,
				   syncStmt.getBody());

	} else if (stmt instanceof TryStatement) {
	    // XXX Ignore catch statements (for now).
	    TryStatement tryStmt = (TryStatement)stmt;
	    DFFrame tryFrame = frame.getChild(tryStmt);
	    cpt = processStatement(scope, tryFrame, cpt,
				   tryStmt.getBody());
	    Block finBlock = tryStmt.getFinally();
	    if (finBlock != null) {
		cpt = processStatement(scope, frame, cpt, finBlock);
	    }

	} else if (stmt instanceof ThrowStatement) {
	    ThrowStatement throwStmt = (ThrowStatement)stmt;
	    cpt = processExpression(scope, frame, cpt, throwStmt.getExpression());
            ExceptionNode exception = new ExceptionNode(scope, stmt, cpt.getRValue());
	    DFFrame dstFrame = frame.find(DFFrame.TRY);
	    cpt.addExit(new DFExit(exception, dstFrame));
	    for (DFFrame frm = frame; frm != null; frm = frm.getParent()) {
		for (DFRef ref : frm.getOutputs()) {
		    cpt.addExit(new DFExit(cpt.getValue(ref), dstFrame, true));
		}
		if (frm == dstFrame) break;
	    }

	} else if (stmt instanceof ConstructorInvocation) {
	    // XXX Ignore all side effects.
	    ConstructorInvocation ci = (ConstructorInvocation)stmt;
	    for (Expression arg : (List<Expression>) ci.arguments()) {
		cpt = processExpression(scope, frame, cpt, arg);
	    }

	} else if (stmt instanceof SuperConstructorInvocation) {
	    // XXX Ignore all side effects.
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
    public void buildScope(DFScope scope, DFFrame frame, Statement ast)
	throws UnsupportedSyntax {

	if (ast instanceof AssertStatement) {

	} else if (ast instanceof Block) {
	    Block block = (Block)ast;
	    DFScope childScope = scope.addChild("b", ast);
	    for (Statement stmt :
		     (List<Statement>) block.statements()) {
		buildScope(childScope, frame, stmt);
	    }

	} else if (ast instanceof EmptyStatement) {

	} else if (ast instanceof VariableDeclarationStatement) {
	    VariableDeclarationStatement varStmt =
		(VariableDeclarationStatement)ast;
	    // XXX Ignore modifiers and dimensions.
	    Type varType = varStmt.getType();
	    for (VariableDeclarationFragment frag :
		     (List<VariableDeclarationFragment>) varStmt.fragments()) {
		scope.addVar(frag.getName(), varType);
		Expression expr = frag.getInitializer();
		if (expr != null) {
		    buildScope(scope, frame, expr);
		}
	    }

	} else if (ast instanceof ExpressionStatement) {
	    ExpressionStatement exprStmt = (ExpressionStatement)ast;
	    Expression expr = exprStmt.getExpression();
	    buildScope(scope, frame, expr);

	} else if (ast instanceof ReturnStatement) {
	    ReturnStatement returnStmt = (ReturnStatement)ast;
	    Expression expr = returnStmt.getExpression();
	    if (expr != null) {
		buildScope(scope, frame, expr);
	    }

	} else if (ast instanceof IfStatement) {
	    IfStatement ifStmt = (IfStatement)ast;
	    Expression expr = ifStmt.getExpression();
	    buildScope(scope, frame, expr);
	    Statement thenStmt = ifStmt.getThenStatement();
	    buildScope(scope, frame, thenStmt);
	    Statement elseStmt = ifStmt.getElseStatement();
	    if (elseStmt != null) {
		buildScope(scope, frame, elseStmt);
	    }

	} else if (ast instanceof SwitchStatement) {
	    SwitchStatement switchStmt = (SwitchStatement)ast;
	    DFScope childScope = scope.addChild("switch", ast);
	    DFFrame childFrame = frame.addChild(null, ast);
	    Expression expr = switchStmt.getExpression();
	    buildScope(childScope, frame, expr);
	    for (Statement stmt :
		     (List<Statement>) switchStmt.statements()) {
		buildScope(childScope, childFrame, stmt);
	    }

	} else if (ast instanceof SwitchCase) {
	    SwitchCase switchCase = (SwitchCase)ast;
	    Expression expr = switchCase.getExpression();
	    if (expr != null) {
		buildScope(scope, frame, expr);
	    }

	} else if (ast instanceof WhileStatement) {
	    WhileStatement whileStmt = (WhileStatement)ast;
	    DFScope childScope = scope.addChild("while", ast);
	    DFFrame childFrame = frame.addChild(null, ast);
	    Expression expr = whileStmt.getExpression();
	    buildScope(childScope, frame, expr);
	    Statement stmt = whileStmt.getBody();
	    buildScope(childScope, childFrame, stmt);

	} else if (ast instanceof DoStatement) {
	    DoStatement doStmt = (DoStatement)ast;
	    DFScope childScope = scope.addChild("do", ast);
	    DFFrame childFrame = frame.addChild(null, ast);
	    Statement stmt = doStmt.getBody();
	    buildScope(childScope, childFrame, stmt);
	    Expression expr = doStmt.getExpression();
	    buildScope(childScope, frame, expr);

	} else if (ast instanceof ForStatement) {
	    ForStatement forStmt = (ForStatement)ast;
	    DFScope childScope = scope.addChild("for", ast);
	    DFFrame childFrame = frame.addChild(null, ast);
	    for (Expression init :
		     (List<Expression>) forStmt.initializers()) {
		buildScope(childScope, frame, init);
	    }
	    Expression expr = forStmt.getExpression();
	    if (expr != null) {
		buildScope(childScope, childFrame, expr);
	    }
	    Statement stmt = forStmt.getBody();
	    buildScope(childScope, childFrame, stmt);
	    for (Expression update :
		     (List<Expression>) forStmt.updaters()) {
		buildScope(childScope, childFrame, update);
	    }

	} else if (ast instanceof EnhancedForStatement) {
	    EnhancedForStatement eForStmt = (EnhancedForStatement)ast;
	    DFScope childScope = scope.addChild("efor", ast);
	    DFFrame childFrame = frame.addChild(null, ast);
	    SingleVariableDeclaration decl = eForStmt.getParameter();
	    // XXX Ignore modifiers and dimensions.
	    Type varType = decl.getType();
	    childScope.addVar(decl.getName(), varType);
	    Expression expr = eForStmt.getExpression();
	    if (expr != null) {
		buildScope(childScope, frame, expr);
	    }
	    Statement stmt = eForStmt.getBody();
	    buildScope(childScope, childFrame, stmt);

	} else if (ast instanceof BreakStatement) {

	} else if (ast instanceof ContinueStatement) {

	} else if (ast instanceof LabeledStatement) {
	    LabeledStatement labeledStmt = (LabeledStatement)ast;
	    SimpleName labelName = labeledStmt.getLabel();
	    String label = labelName.getIdentifier();
	    DFFrame childFrame = frame.addChild(label, ast);
	    Statement stmt = labeledStmt.getBody();
	    buildScope(scope, childFrame, stmt);

	} else if (ast instanceof SynchronizedStatement) {
	    SynchronizedStatement syncStmt = (SynchronizedStatement)ast;
	    Block block = syncStmt.getBody();
	    buildScope(scope, frame, block);

	} else if (ast instanceof TryStatement) {
	    TryStatement tryStmt = (TryStatement)ast;
	    Block block = tryStmt.getBody();
	    DFFrame tryFrame = frame.addChild(DFFrame.TRY, ast);
	    buildScope(scope, tryFrame, block);
	    for (CatchClause cc :
		     (List<CatchClause>) tryStmt.catchClauses()) {
		DFScope childScope = scope.addChild("catch", cc);
		SingleVariableDeclaration decl = cc.getException();
		// XXX Ignore modifiers and dimensions.
		Type varType = decl.getType();
		childScope.addVar(decl.getName(), varType);
		buildScope(childScope, frame, cc.getBody());
	    }
	    Block finBlock = tryStmt.getFinally();
	    if (finBlock != null) {
		buildScope(scope, frame, finBlock);
	    }

	} else if (ast instanceof ThrowStatement) {
	    ThrowStatement throwStmt = (ThrowStatement)ast;
	    Expression expr = throwStmt.getExpression();
	    if (expr != null) {
		buildScope(scope, frame, expr);
	    }

	} else if (ast instanceof ConstructorInvocation) {
	    ConstructorInvocation ci = (ConstructorInvocation)ast;
	    for (Expression expr :
		     (List<Expression>) ci.arguments()) {
		buildScope(scope, frame, expr);
	    }

	} else if (ast instanceof SuperConstructorInvocation) {
	    SuperConstructorInvocation sci = (SuperConstructorInvocation)ast;
	    for (Expression expr :
		     (List<Expression>) sci.arguments()) {
		buildScope(scope, frame, expr);
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
    public void buildScope(DFScope scope, DFFrame frame, Expression ast)
	throws UnsupportedSyntax {

	if (ast instanceof Annotation) {

	} else if (ast instanceof Name) {
	    Name name = (Name)ast;
	    if (name.isSimpleName()) {
		DFRef ref = scope.lookupVar((SimpleName)name);
		frame.addInput(ref);
	    } else {
		// QualifiedName == FieldAccess
		QualifiedName qn = (QualifiedName)name;
		SimpleName fieldName = qn.getName();
		DFRef ref = scope.lookupField(fieldName);
		frame.addInput(ref);
	    }

	} else if (ast instanceof ThisExpression) {
	    DFRef ref = scope.lookupThis();
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
	    buildScope(scope, frame, operand);
	    if (op == PrefixExpression.Operator.INCREMENT ||
		op == PrefixExpression.Operator.DECREMENT) {
		buildScopeLeft(scope, frame, operand);
	    }

	} else if (ast instanceof PostfixExpression) {
	    PostfixExpression postfix = (PostfixExpression)ast;
	    PostfixExpression.Operator op = postfix.getOperator();
	    Expression operand = postfix.getOperand();
	    buildScope(scope, frame, operand);
	    if (op == PostfixExpression.Operator.INCREMENT ||
		op == PostfixExpression.Operator.DECREMENT) {
		buildScopeLeft(scope, frame, operand);
	    }

	} else if (ast instanceof InfixExpression) {
	    InfixExpression infix = (InfixExpression)ast;
	    InfixExpression.Operator op = infix.getOperator();
	    Expression loperand = infix.getLeftOperand();
	    buildScope(scope, frame, loperand);
	    Expression roperand = infix.getRightOperand();
	    buildScope(scope, frame, roperand);

	} else if (ast instanceof ParenthesizedExpression) {
	    ParenthesizedExpression paren = (ParenthesizedExpression)ast;
	    buildScope(scope, frame, paren.getExpression());

	} else if (ast instanceof Assignment) {
	    Assignment assn = (Assignment)ast;
	    Assignment.Operator op = assn.getOperator();
	    buildScopeLeft(scope, frame, assn.getLeftHandSide());
	    if (op != Assignment.Operator.ASSIGN) {
		buildScope(scope, frame, assn.getLeftHandSide());
	    }
	    buildScope(scope, frame, assn.getRightHandSide());

	} else if (ast instanceof VariableDeclarationExpression) {
	    VariableDeclarationExpression decl = (VariableDeclarationExpression)ast;
	    // XXX Ignore modifiers and dimensions.
	    Type varType = decl.getType();
	    for (VariableDeclarationFragment frag :
		     (List<VariableDeclarationFragment>) decl.fragments()) {
		DFRef ref = scope.addVar(frag.getName(), varType);
		frame.addOutput(ref);
		Expression expr = frag.getInitializer();
		if (expr != null) {
		    buildScope(scope, frame, expr);
		}
	    }

	} else if (ast instanceof MethodInvocation) {
	    MethodInvocation invoke = (MethodInvocation)ast;
	    Expression expr = invoke.getExpression();
	    if (expr != null) {
		buildScope(scope, frame, expr);
	    }
	    for (Expression arg :
		     (List<Expression>) invoke.arguments()) {
		buildScope(scope, frame, arg);
	    }

	} else if (ast instanceof SuperMethodInvocation) {
	    SuperMethodInvocation si = (SuperMethodInvocation)ast;
	    for (Expression arg :
		     (List<Expression>) si.arguments()) {
		buildScope(scope, frame, arg);
	    }

	} else if (ast instanceof ArrayCreation) {
	    ArrayCreation ac = (ArrayCreation)ast;
	    for (Expression dim :
		     (List<Expression>) ac.dimensions()) {
		buildScope(scope, frame, dim);
	    }
	    ArrayInitializer init = ac.getInitializer();
	    if (init != null) {
		buildScope(scope, frame, init);
	    }

	} else if (ast instanceof ArrayInitializer) {
	    ArrayInitializer init = (ArrayInitializer)ast;
	    for (Expression expr :
		     (List<Expression>) init.expressions()) {
		buildScope(scope, frame, expr);
	    }

	} else if (ast instanceof ArrayAccess) {
	    ArrayAccess aa = (ArrayAccess)ast;
	    buildScope(scope, frame, aa.getArray());
	    buildScope(scope, frame, aa.getIndex());
	    DFRef ref = scope.lookupArray();
	    frame.addInput(ref);

	} else if (ast instanceof FieldAccess) {
	    FieldAccess fa = (FieldAccess)ast;
	    SimpleName fieldName = fa.getName();
	    buildScope(scope, frame, fa.getExpression());
	    DFRef ref = scope.lookupField(fieldName);
	    frame.addInput(ref);

	} else if (ast instanceof SuperFieldAccess) {
	    SuperFieldAccess sfa = (SuperFieldAccess)ast;
	    SimpleName fieldName = sfa.getName();
	    DFRef ref = scope.lookupField(fieldName);
	    frame.addInput(ref);

	} else if (ast instanceof CastExpression) {
	    CastExpression cast = (CastExpression)ast;
	    buildScope(scope, frame, cast.getExpression());

	} else if (ast instanceof ClassInstanceCreation) {
	    ClassInstanceCreation cstr = (ClassInstanceCreation)ast;
	    Expression expr = cstr.getExpression();
	    if (expr != null) {
		buildScope(scope, frame, expr);
	    }
	    for (Expression arg :
		     (List<Expression>) cstr.arguments()) {
		buildScope(scope, frame, arg);
	    }
	    // Ignore getAnonymousClassDeclaration() here.
	    // It will eventually be picked up as MethodDeclaration.

	} else if (ast instanceof ConditionalExpression) {
	    ConditionalExpression cond = (ConditionalExpression)ast;
	    buildScope(scope, frame, cond.getExpression());
	    buildScope(scope, frame, cond.getThenExpression());
	    buildScope(scope, frame, cond.getElseExpression());

	} else if (ast instanceof InstanceofExpression) {
	    InstanceofExpression instof = (InstanceofExpression)ast;
	    buildScope(scope, frame, instof.getLeftOperand());

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
    public void buildScopeLeft(DFScope scope, DFFrame frame, Expression ast)
	throws UnsupportedSyntax {

	if (ast instanceof Name) {
	    Name name = (Name)ast;
	    if (name.isSimpleName()) {
		DFRef ref = scope.lookupVar((SimpleName)name);
		frame.addOutput(ref);
	    } else {
		// QualifiedName == FieldAccess
		QualifiedName qn = (QualifiedName)name;
		SimpleName fieldName = qn.getName();
		buildScope(scope, frame, qn.getQualifier());
		DFRef ref = scope.lookupField(fieldName);
		frame.addOutput(ref);
	    }

	} else if (ast instanceof ArrayAccess) {
	    ArrayAccess aa = (ArrayAccess)ast;
	    buildScope(scope, frame, aa.getArray());
	    buildScope(scope, frame, aa.getIndex());
	    DFRef ref = scope.lookupArray();
	    frame.addOutput(ref);

	} else if (ast instanceof FieldAccess) {
	    FieldAccess fa = (FieldAccess)ast;
	    SimpleName fieldName = fa.getName();
	    buildScope(scope, frame, fa.getExpression());
	    DFRef ref = scope.lookupField(fieldName);
	    frame.addOutput(ref);

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
	// XXX Ignore isContructor().
	// XXX Ignore getReturnType2().
	// XXX Ignore isVarargs().
	int i = 0;
	for (SingleVariableDeclaration decl :
		 (List<SingleVariableDeclaration>) method.parameters()) {
	    // XXX Ignore modifiers and dimensions.
	    Type paramType = decl.getType();
	    DFRef ref = scope.addVar(decl.getName(), paramType);
	    DFNode param = new ArgNode(scope, ref, decl, i++);
	    DFNode assign = new SingleAssignNode(scope, ref, decl);
	    assign.accept(param);
	    cpt.setOutput(assign);
	}
	return cpt;
    }

    /// Top-level functions.

    /**
     * Performs dataflow analysis for a given method.
     */
    public DFGraph getMethodGraph(MethodDeclaration method)
	throws UnsupportedSyntax {
	String funcName = method.getName().getFullyQualifiedName();
	Block funcBlock = method.getBody();

	DFGraph graph = new DFGraph(funcName);

	// Setup an initial scope.
	DFScope scope = new DFScope(graph, funcName);
	DFFrame frame = new DFFrame(DFFrame.METHOD);

	DFComponent cpt = buildMethodDeclaration(scope, method);
	buildScope(scope, frame, funcBlock);
	//scope.dump();
	//frame.dump();

	// Process the function body.
	cpt = processStatement(scope, frame, cpt, funcBlock);

	cpt.endFrame(frame);
	return graph;
    }

    /// ASTVisitor methods.

    public Exporter exporter;

    public Java2DF(Exporter exporter) {
	this.exporter = exporter;
    }

    public boolean visit(MethodDeclaration method) {
	String funcName = method.getName().getFullyQualifiedName();
	// Ignore method prototypes.
	if (method.getBody() == null) return true;
	try {
	    try {
		DFGraph graph = getMethodGraph(method);
		if (graph != null) {
		    Utils.logit("Success: "+funcName);
		    // Remove redundant nodes.
		    graph.cleanup();
		    if (this.exporter != null) {
			this.exporter.writeGraph(graph);
		    }
		}
	    } catch (UnsupportedSyntax e) {
		String astName = e.ast.getClass().getName();
		Utils.logit("Fail: "+funcName+" (Unsupported: "+astName+") "+e.ast);
		//e.printStackTrace();
		if (this.exporter != null) {
		    this.exporter.writeError(funcName, astName);
		}
	    }
	} catch (IOException e) {
	    e.printStackTrace();
	}
	return true;
    }

    public void processFile(String path)
	throws IOException {
	Utils.logit("Parsing: "+path);
	String src = Utils.readFile(path);
	Map<String, String> options = JavaCore.getOptions();
	JavaCore.setComplianceOptions(JavaCore.VERSION_1_7, options);
	ASTParser parser = ASTParser.newParser(AST.JLS8);
	parser.setSource(src.toCharArray());
	parser.setKind(ASTParser.K_COMPILATION_UNIT);
	//parser.setResolveBindings(true);
	parser.setEnvironment(null, null, null, true);
	parser.setCompilerOptions(options);
	CompilationUnit cu = (CompilationUnit)parser.createAST(null);
	cu.accept(this);
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
	XmlExporter exporter = new XmlExporter();
	for (String path : files) {
	    try {
		exporter.startFile(path);
		Java2DF converter = new Java2DF(exporter);
		converter.processFile(path);
		exporter.endFile();
	    } catch (IOException e) {
		System.err.println("Cannot open input file: "+path);
	    }
	}
	exporter.close();
	Utils.printXml(output, exporter.document);
	output.close();
    }
}
