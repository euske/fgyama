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


// ProgNode: a DFNode that corresponds to an actual program point.
abstract class ProgNode extends DFNode {

    public ASTNode ast;

    public ProgNode(DFGraph graph, DFVarScope scope, DFTypeRef type, DFVarRef ref,
                    ASTNode ast) {
	super(graph, scope, type, ref);
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

    public SingleAssignNode(DFGraph graph, DFVarScope scope, DFVarRef ref,
                            ASTNode ast) {
	super(graph, scope, null, ref, ast);
    }

    @Override
    public String getKind() {
	return "assign";
    }
}

// ArrayAssignNode:
class ArrayAssignNode extends ProgNode {

    public ArrayAssignNode(DFGraph graph, DFVarScope scope, DFVarRef ref,
                           ASTNode ast, DFNode array, DFNode index) {
	super(graph, scope, null, ref, ast);
	this.accept(array, "array");
	this.accept(index, "index");
    }

    @Override
    public String getKind() {
	return "arrayassign";
    }
}

// FieldAssignNode:
class FieldAssignNode extends ProgNode {

    public FieldAssignNode(DFGraph graph, DFVarScope scope, DFVarRef ref,
			   ASTNode ast, DFNode obj) {
	super(graph, scope, null, ref, ast);
	this.accept(obj, "obj");
    }

    @Override
    public String getKind() {
	return "fieldassign";
    }
}

// VarRefNode: represnets a variable reference.
class VarRefNode extends ProgNode {

    public VarRefNode(DFGraph graph, DFVarScope scope, DFVarRef ref,
                      ASTNode ast) {
	super(graph, scope, ref.getType(), ref, ast);
    }

    @Override
    public String getKind() {
	return "ref";
    }
}

// ArrayAccessNode
class ArrayAccessNode extends ProgNode {

    public ArrayAccessNode(DFGraph graph, DFVarScope scope, DFVarRef ref,
			   ASTNode ast, DFNode array, DFNode index) {
	super(graph, scope, null, ref, ast);
	this.accept(array, "array");
	this.accept(index, "index");
    }

    @Override
    public String getKind() {
	return "arrayaccess";
    }
}

// FieldAccessNode
class FieldAccessNode extends ProgNode {

    public FieldAccessNode(DFGraph graph, DFVarScope scope, DFVarRef ref,
                           ASTNode ast, DFNode obj) {
	super(graph, scope, null, ref, ast);
	this.accept(obj, "obj");
    }

    @Override
    public String getKind() {
	return "fieldaccess";
    }
}

// PrefixNode
class PrefixNode extends ProgNode {

    public PrefixExpression.Operator op;

    public PrefixNode(DFGraph graph, DFVarScope scope, DFVarRef ref,
                      ASTNode ast, PrefixExpression.Operator op) {
	super(graph, scope, null, ref, ast);
	this.op = op;
    }

    @Override
    public String getKind() {
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

    public PostfixNode(DFGraph graph, DFVarScope scope, DFVarRef ref,
                       ASTNode ast, PostfixExpression.Operator op) {
	super(graph, scope, null, ref, ast);
	this.op = op;
    }

    @Override
    public String getKind() {
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

    public InfixNode(DFGraph graph, DFVarScope scope, DFTypeRef type,
                     ASTNode ast, InfixExpression.Operator op,
		     DFNode lvalue, DFNode rvalue) {
	super(graph, scope, type, null, ast);
	this.op = op;
	this.accept(lvalue, "L");
	this.accept(rvalue, "R");
    }

    @Override
    public String getKind() {
	return "infix";
    }

    @Override
    public String getData() {
	return this.op.toString();
    }
}

// TypeCastNode
class TypeCastNode extends ProgNode {

    public TypeCastNode(DFGraph graph, DFVarScope scope, DFTypeRef type,
                        ASTNode ast) {
	super(graph, scope, type, null, ast);
    }

    @Override
    public String getKind() {
	return "typecast";
    }

    @Override
    public String getData() {
	return this.getType().getName();
    }
}

// InstanceofNode
class InstanceofNode extends ProgNode {

    public DFTypeRef type;

    public InstanceofNode(DFGraph graph, DFVarScope scope,
			  ASTNode ast, DFTypeRef type) {
	super(graph, scope, DFTypeRef.BOOLEAN, null, ast);
	this.type = type;
    }

    @Override
    public String getKind() {
	return "instanceof";
    }

    @Override
    public String getData() {
	return this.getType().getName();
    }
}

// CaseNode
class CaseNode extends ProgNode {

    public List<DFNode> matches = new ArrayList<DFNode>();

    public CaseNode(DFGraph graph, DFVarScope scope,
                    ASTNode ast) {
	super(graph, scope, null, null, ast);
    }

    @Override
    public String getKind() {
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

    public AssignOpNode(DFGraph graph, DFVarScope scope, DFVarRef ref,
			ASTNode ast, Assignment.Operator op,
			DFNode lvalue, DFNode rvalue) {
	super(graph, scope, rvalue.getType(), ref, ast);
	this.op = op;
	this.accept(lvalue, "L");
	this.accept(rvalue, "R");
    }

    @Override
    public String getKind() {
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

    public ArgNode(DFGraph graph, DFVarScope scope, DFVarRef ref,
                   ASTNode ast, int index) {
	super(graph, scope, ref.getType(), ref, ast);
	this.index = index;
    }

    @Override
    public String getKind() {
	return "arg";
    }

    @Override
    public String getData() {
	return Integer.toString(this.index);
    }
}

// ConstNode: represents a constant value.
class ConstNode extends ProgNode {

    public String data;

    public ConstNode(DFGraph graph, DFVarScope scope, DFTypeRef type,
                     ASTNode ast, String data) {
	super(graph, scope, type, null, ast);
	this.data = data;
    }

    @Override
    public String getKind() {
	return "const";
    }

    @Override
    public String getData() {
	return this.data;
    }
}

// ArrayValueNode: represents an array.
class ArrayValueNode extends ProgNode {

    public List<DFNode> values = new ArrayList<DFNode>();

    public ArrayValueNode(DFGraph graph, DFVarScope scope,
                          ASTNode ast) {
	super(graph, scope, null, null, ast);
    }

    @Override
    public String getKind() {
	return "arrayvalue";
    }

    @Override
    public String getData() {
	return Integer.toString(this.values.size());
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

    public JoinNode(DFGraph graph, DFVarScope scope, DFVarRef ref,
                    ASTNode ast, DFNode cond) {
	super(graph, scope, null, ref, ast);
	this.accept(cond, "cond");
    }

    @Override
    public String getKind() {
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

    public LoopBeginNode(DFGraph graph, DFVarScope scope, DFVarRef ref,
                         ASTNode ast, DFNode enter) {
	super(graph, scope, null, ref, ast);
	this.accept(enter, "enter");
    }

    @Override
    public String getKind() {
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

    public LoopEndNode(DFGraph graph, DFVarScope scope, DFVarRef ref,
                       ASTNode ast, DFNode cond) {
	super(graph, scope, null, ref, ast);
	this.accept(cond, "cond");
    }

    @Override
    public String getKind() {
	return "end";
    }

    public void setBegin(LoopBeginNode begin) {
	this.accept(begin, "_begin");
    }
}

// LoopRepeatNode
class LoopRepeatNode extends ProgNode {

    public LoopRepeatNode(DFGraph graph, DFVarScope scope, DFVarRef ref,
                          ASTNode ast) {
	super(graph, scope, null, ref, ast);
    }

    @Override
    public String getKind() {
	return "repeat";
    }

    public void setLoop(DFNode end) {
	this.accept(end, "_loop");
    }
}

// IterNode
class IterNode extends ProgNode {

    public IterNode(DFGraph graph, DFVarScope scope, DFVarRef ref,
                    ASTNode ast) {
	super(graph, scope, null, ref, ast);
    }

    @Override
    public String getKind() {
	return "iter";
    }
}

// CallNode
abstract class CallNode extends ProgNode {

    public List<DFNode> args;
    public DFNode exception;

    public CallNode(DFGraph graph, DFVarScope scope, DFTypeRef type, DFVarRef ref,
                    ASTNode ast) {
	super(graph, scope, type, ref, ast);
	this.args = new ArrayList<DFNode>();
        this.exception = null;
    }

    @Override
    public String getKind() {
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

    public DFMethod method;

    public MethodCallNode(DFGraph graph, DFVarScope scope, DFMethod method,
			  ASTNode ast, DFNode obj) {
	super(graph, scope, method.getReturnType(), null, ast);
	if (obj != null) {
	    this.accept(obj, "obj");
	}
	this.method = method;
    }

    @Override
    public String getData() {
        return this.method.getName();
    }
}

// CreateObjectNode
class CreateObjectNode extends CallNode {

    public CreateObjectNode(DFGraph graph, DFVarScope scope, DFTypeRef type,
                            ASTNode ast, DFNode obj) {
	super(graph, scope, type, null, ast);
	if (obj != null) {
	    this.accept(obj, "obj");
	}
    }

    @Override
    public String getKind() {
	return "new";
    }

    @Override
    public String getData() {
	return this.getType().getName();
    }
}

// ReturnNode: represents a return value.
class ReturnNode extends ProgNode {

    public ReturnNode(DFGraph graph, DFVarScope scope,
                      ASTNode ast) {
	super(graph, scope, null, scope.lookupReturn(), ast);
    }

    @Override
    public String getKind() {
	return "return";
    }
}

// ExceptionNode
class ExceptionNode extends ProgNode {

    public ExceptionNode(DFGraph graph, DFVarScope scope,
                         ASTNode ast, DFNode value) {
	super(graph, scope, null, null, ast);
	this.accept(value);
    }

    @Override
    public String getKind() {
	return "exception";
    }
}


//  Java2DF
//
public class Java2DF {

    /// General graph operations.

    /**
     * Combines two components into one.
     * A JoinNode is added to each variable.
     */
    public DFComponent processConditional
	(DFGraph graph, DFVarScope varScope, DFFrame frame, DFComponent cpt, ASTNode ast,
	 DFNode condValue, DFComponent trueCpt, DFComponent falseCpt) {

	// outRefs: all the references from both component.
	List<DFVarRef> outRefs = new ArrayList<DFVarRef>();
	if (trueCpt != null) {
	    for (DFVarRef ref : trueCpt.getInputRefs()) {
		DFNode src = trueCpt.getInput(ref);
		assert src != null;
		src.accept(cpt.getValue(ref));
	    }
	    outRefs.addAll(Arrays.asList(trueCpt.getOutputRefs()));
	}
	if (falseCpt != null) {
	    for (DFVarRef ref : falseCpt.getInputRefs()) {
		DFNode src = falseCpt.getInput(ref);
		assert src != null;
		src.accept(cpt.getValue(ref));
	    }
	    outRefs.addAll(Arrays.asList(falseCpt.getOutputRefs()));
	}

	// Attach a JoinNode to each variable.
	Set<DFVarRef> used = new HashSet<DFVarRef>();
	for (DFVarRef ref : outRefs) {
	    if (used.contains(ref)) continue;
	    used.add(ref);
	    JoinNode join = new JoinNode(graph, varScope, ref, ast, condValue);
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
		JoinNode join = new JoinNode(graph, varScope, node.getRef(), null, condValue);
		join.recv(true, node);
		cpt.addExit(exit.wrap(join));
	    }
	}
	if (falseCpt != null) {
	    for (DFExit exit : falseCpt.getExits()) {
                DFNode node = exit.getNode();
		JoinNode join = new JoinNode(graph, varScope, node.getRef(), null, condValue);
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
	(DFGraph graph, DFVarScope varScope, DFFrame frame, DFComponent cpt, ASTNode ast,
	 DFNode condValue, DFFrame loopFrame, DFComponent loopCpt,
	 boolean preTest)
	throws UnsupportedSyntax {

	// Add four nodes for each loop variable.
	Map<DFVarRef, LoopBeginNode> begins = new HashMap<DFVarRef, LoopBeginNode>();
	Map<DFVarRef, LoopRepeatNode> repeats = new HashMap<DFVarRef, LoopRepeatNode>();
	Map<DFVarRef, DFNode> ends = new HashMap<DFVarRef, DFNode>();
	DFVarRef[] loopRefs = loopFrame.getInsAndOuts();
	for (DFVarRef ref : loopRefs) {
	    DFNode src = cpt.getValue(ref);
	    LoopBeginNode begin = new LoopBeginNode(graph, varScope, ref, ast, src);
	    LoopRepeatNode repeat = new LoopRepeatNode(graph, varScope, ref, ast);
	    LoopEndNode end = new LoopEndNode(graph, varScope, ref, ast, condValue);
	    begin.setEnd(end);
	    end.setBegin(begin);
	    begins.put(ref, begin);
	    ends.put(ref, end);
	    repeats.put(ref, repeat);
	}

	if (preTest) {  // Repeat -> [S] -> Begin -> End
	    // Connect the repeats to the loop inputs.
	    for (DFVarRef ref : loopCpt.getInputRefs()) {
		DFNode input = loopCpt.getInput(ref);
		DFNode src = repeats.get(ref);
		if (src == null) {
		    src = cpt.getValue(ref);
		}
		input.accept(src);
	    }
	    // Connect the loop outputs to the begins.
	    for (DFVarRef ref : loopCpt.getOutputRefs()) {
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
	    for (DFVarRef ref : loopRefs) {
		LoopBeginNode begin = begins.get(ref);
		DFNode end = ends.get(ref);
		end.accept(begin);
	    }

	} else {  // Begin -> [S] -> End -> Repeat
	    // Connect the begins to the loop inputs.
	    for (DFVarRef ref : loopCpt.getInputRefs()) {
		DFNode input = loopCpt.getInput(ref);
		DFNode src = begins.get(ref);
		if (src == null) {
		    src = cpt.getValue(ref);
		}
		input.accept(src);
	    }
	    // Connect the loop outputs to the ends.
	    for (DFVarRef ref : loopCpt.getOutputRefs()) {
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
	    for (DFVarRef ref : loopRefs) {
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
	for (DFVarRef ref : loopRefs) {
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
	(DFGraph graph, DFTypeScope typeScope, DFVarScope varScope,
	 DFFrame frame, DFComponent cpt, List<VariableDeclarationFragment> frags)
	throws UnsupportedSyntax {

	for (VariableDeclarationFragment frag : frags) {
	    DFVarRef ref = varScope.lookupVar(frag.getName());
	    Expression init = frag.getInitializer();
	    if (init != null) {
		cpt = processExpression(graph, typeScope, varScope, frame, cpt, init);
		DFNode assign = new SingleAssignNode(graph, varScope, ref, frag);
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
	(DFGraph graph, DFTypeScope typeScope, DFVarScope varScope,
	 DFFrame frame, DFComponent cpt, Expression expr)
	throws UnsupportedSyntax {

	if (expr instanceof Name) {
	    Name name = (Name)expr;
	    if (name.isSimpleName()) {
		DFVarRef ref = varScope.lookupVar((SimpleName)name);
		cpt.setLValue(new SingleAssignNode(graph, varScope, ref, expr));
	    } else {
		// QualifiedName == FieldAccess
		QualifiedName qn = (QualifiedName)name;
		SimpleName fieldName = qn.getName();
		cpt = processExpression(graph, typeScope, varScope, frame, cpt, qn.getQualifier());
		DFNode obj = cpt.getRValue();
		DFVarRef ref = varScope.lookupField(fieldName);
		cpt.setLValue(new FieldAssignNode(graph, varScope, ref, expr, obj));
	    }

	} else if (expr instanceof ArrayAccess) {
	    ArrayAccess aa = (ArrayAccess)expr;
	    cpt = processExpression(graph, typeScope, varScope, frame, cpt, aa.getArray());
	    DFNode array = cpt.getRValue();
	    cpt = processExpression(graph, typeScope, varScope, frame, cpt, aa.getIndex());
	    DFNode index = cpt.getRValue();
	    DFVarRef ref = varScope.lookupArray();
	    cpt.setLValue(new ArrayAssignNode(graph, varScope, ref, expr, array, index));

	} else if (expr instanceof FieldAccess) {
	    FieldAccess fa = (FieldAccess)expr;
	    SimpleName fieldName = fa.getName();
	    cpt = processExpression(graph, typeScope, varScope, frame, cpt, fa.getExpression());
	    DFNode obj = cpt.getRValue();
	    DFVarRef ref = varScope.lookupField(fieldName);
	    cpt.setLValue(new FieldAssignNode(graph, varScope, ref, expr, obj));

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
	(DFGraph graph, DFTypeScope typeScope, DFVarScope varScope,
         DFFrame frame, DFComponent cpt, Expression expr)
	throws UnsupportedSyntax {

	if (expr instanceof Annotation) {

	} else if (expr instanceof Name) {
	    Name name = (Name)expr;
	    if (name.isSimpleName()) {
		DFVarRef ref = varScope.lookupVar((SimpleName)name);
                DFNode node = new VarRefNode(graph, varScope, ref, expr);
                node.accept(cpt.getValue(ref));
		cpt.setRValue(node);
	    } else {
		// QualifiedName == FieldAccess
		QualifiedName qn = (QualifiedName)name;
		SimpleName fieldName = qn.getName();
		cpt = processExpression(graph, typeScope, varScope, frame, cpt, qn.getQualifier());
		DFNode obj = cpt.getRValue();
		DFVarRef ref = varScope.lookupField(fieldName);
                DFNode node = new FieldAccessNode(graph, varScope, ref, qn, obj);
                node.accept(cpt.getValue(ref));
		cpt.setRValue(node);
	    }

	} else if (expr instanceof ThisExpression) {
	    DFVarRef ref = varScope.lookupThis();
            DFNode node = new VarRefNode(graph, varScope, ref, expr);
            node.accept(cpt.getValue(ref));
	    cpt.setRValue(node);

	} else if (expr instanceof BooleanLiteral) {
	    boolean value = ((BooleanLiteral)expr).booleanValue();
	    cpt.setRValue(new ConstNode(graph, varScope, DFTypeRef.BOOLEAN,
					expr, Boolean.toString(value)));

	} else if (expr instanceof CharacterLiteral) {
	    char value = ((CharacterLiteral)expr).charValue();
	    cpt.setRValue(new ConstNode(graph, varScope, DFTypeRef.CHAR,
					expr, Character.toString(value)));

	} else if (expr instanceof NullLiteral) {
	    cpt.setRValue(new ConstNode(graph, varScope, DFTypeRef.NULL,
					expr, "null"));

	} else if (expr instanceof NumberLiteral) {
	    String value = ((NumberLiteral)expr).getToken();
	    cpt.setRValue(new ConstNode(graph, varScope, DFTypeRef.NUMBER,
					expr, value));

	} else if (expr instanceof StringLiteral) {
	    String value = ((StringLiteral)expr).getLiteralValue();
	    cpt.setRValue(new ConstNode(graph, varScope, DFTypeRef.STRING,
					expr, value));

	} else if (expr instanceof TypeLiteral) {
	    Type value = ((TypeLiteral)expr).getType();
	    cpt.setRValue(new ConstNode(graph, varScope, DFTypeRef.TYPE,
					expr, Utils.getTypeName(value)));

	} else if (expr instanceof PrefixExpression) {
	    PrefixExpression prefix = (PrefixExpression)expr;
	    PrefixExpression.Operator op = prefix.getOperator();
	    Expression operand = prefix.getOperand();
	    cpt = processExpression(graph, typeScope, varScope, frame, cpt, operand);
	    if (op == PrefixExpression.Operator.INCREMENT ||
		op == PrefixExpression.Operator.DECREMENT) {
		cpt = processAssignment(graph, typeScope, varScope, frame, cpt, operand);
		DFNode assign = cpt.getLValue();
		DFNode value = new PrefixNode(graph, varScope, assign.getRef(),
                                              expr, op);
                value.accept(cpt.getRValue());
		assign.accept(value);
		cpt.setOutput(assign);
		cpt.setRValue(value);
	    } else {
                DFNode value = new PrefixNode(graph, varScope, null,
                                              expr, op);
                value.accept(cpt.getRValue());
		cpt.setRValue(value);
	    }

	} else if (expr instanceof PostfixExpression) {
	    PostfixExpression postfix = (PostfixExpression)expr;
	    PostfixExpression.Operator op = postfix.getOperator();
	    Expression operand = postfix.getOperand();
	    cpt = processAssignment(graph, typeScope, varScope, frame, cpt, operand);
	    if (op == PostfixExpression.Operator.INCREMENT ||
		op == PostfixExpression.Operator.DECREMENT) {
		DFNode assign = cpt.getLValue();
		cpt = processExpression(graph, typeScope, varScope, frame, cpt, operand);
                DFNode node = new PostfixNode(graph, varScope, assign.getRef(),
                                              expr, op);
                node.accept(cpt.getRValue());
		assign.accept(node);
		cpt.setOutput(assign);
	    }

	} else if (expr instanceof InfixExpression) {
	    InfixExpression infix = (InfixExpression)expr;
	    InfixExpression.Operator op = infix.getOperator();
	    cpt = processExpression(graph, typeScope, varScope, frame, cpt, infix.getLeftOperand());
	    DFNode lvalue = cpt.getRValue();
	    cpt = processExpression(graph, typeScope, varScope, frame, cpt, infix.getRightOperand());
	    DFNode rvalue = cpt.getRValue();
            DFTypeRef type = lvalue.getType(); // XXX Todo: implicit type coersion.
	    cpt.setRValue(new InfixNode(graph, varScope, type,
                                        expr, op, lvalue, rvalue));

	} else if (expr instanceof ParenthesizedExpression) {
	    ParenthesizedExpression paren = (ParenthesizedExpression)expr;
	    cpt = processExpression(graph, typeScope, varScope, frame, cpt, paren.getExpression());

	} else if (expr instanceof Assignment) {
	    Assignment assn = (Assignment)expr;
	    Assignment.Operator op = assn.getOperator();
	    cpt = processAssignment(graph, typeScope, varScope, frame, cpt, assn.getLeftHandSide());
	    DFNode assign = cpt.getLValue();
	    cpt = processExpression(graph, typeScope, varScope, frame, cpt, assn.getRightHandSide());
	    DFNode rvalue = cpt.getRValue();
	    DFNode lvalue = cpt.getValue(assign.getRef());
	    assign.accept(new AssignOpNode(graph, varScope, assign.getRef(), assn,
                                           op, lvalue, rvalue));
	    cpt.setOutput(assign);
	    cpt.setRValue(assign);

	} else if (expr instanceof VariableDeclarationExpression) {
	    VariableDeclarationExpression decl = (VariableDeclarationExpression)expr;
	    cpt = processVariableDeclaration(
                graph, typeScope, varScope, frame, cpt, decl.fragments());

	} else if (expr instanceof MethodInvocation) {
	    MethodInvocation invoke = (MethodInvocation)expr;
	    Expression expr1 = invoke.getExpression();
	    DFNode obj;
	    if (expr1 == null) {
                obj = cpt.getValue(varScope.lookupThis());
            } else {
		cpt = processExpression(graph, typeScope, varScope, frame, cpt, expr1);
		obj = cpt.getRValue();
	    }
            DFClassScope klass = typeScope.lookupClass(obj.getType());
            DFMethod method = klass.lookupMethod(invoke.getName());
	    MethodCallNode call = new MethodCallNode(
                graph, varScope, method, invoke, obj);
	    for (Expression arg : (List<Expression>) invoke.arguments()) {
		cpt = processExpression(graph, typeScope, varScope, frame, cpt, arg);
		call.addArg(cpt.getRValue());
	    }
	    cpt.setRValue(call);
            if (call.exception != null) {
		DFFrame dstFrame = frame.find(DFFrame.TRY);
		cpt.addExit(new DFExit(call.exception, dstFrame));
            }

	} else if (expr instanceof SuperMethodInvocation) {
	    SuperMethodInvocation sinvoke = (SuperMethodInvocation)expr;
            DFNode obj = cpt.getValue(varScope.lookupThis());
            DFClassScope klass = typeScope.lookupClass(obj.getType());
            DFClassScope baseKlass = klass.getBase();
            DFMethod method = baseKlass.lookupMethod(sinvoke.getName());
	    MethodCallNode call = new MethodCallNode(
		graph, varScope, method, sinvoke, obj);
	    for (Expression arg : (List<Expression>) sinvoke.arguments()) {
		cpt = processExpression(graph, typeScope, varScope, frame, cpt, arg);
		call.addArg(cpt.getRValue());
	    }
	    cpt.setRValue(call);
            if (call.exception != null) {
		DFFrame dstFrame = frame.find(DFFrame.TRY);
		cpt.addExit(new DFExit(call.exception, dstFrame));
            }

	} else if (expr instanceof ArrayCreation) {
	    ArrayCreation ac = (ArrayCreation)expr;
	    for (Expression dim : (List<Expression>) ac.dimensions()) {
		// XXX cpt.getRValue() is not used (for now).
		cpt = processExpression(graph, typeScope, varScope, frame, cpt, dim);
	    }
	    ArrayInitializer init = ac.getInitializer();
	    if (init != null) {
		cpt = processExpression(graph, typeScope, varScope, frame, cpt, init);
	    } else {
		cpt.setRValue(new ArrayValueNode(graph, varScope, ac));
	    }

	} else if (expr instanceof ArrayInitializer) {
	    ArrayInitializer init = (ArrayInitializer)expr;
	    ArrayValueNode arr = new ArrayValueNode(graph, varScope, init);
	    for (Expression expr1 : (List<Expression>) init.expressions()) {
		cpt = processExpression(graph, typeScope, varScope, frame, cpt, expr1);
		arr.addValue(cpt.getRValue());
	    }
	    cpt.setRValue(arr);
	    // XXX array ref is not used.

	} else if (expr instanceof ArrayAccess) {
	    ArrayAccess aa = (ArrayAccess)expr;
	    DFVarRef ref = varScope.lookupArray();
	    cpt = processExpression(graph, typeScope, varScope, frame, cpt, aa.getArray());
	    DFNode array = cpt.getRValue();
	    cpt = processExpression(graph, typeScope, varScope, frame, cpt, aa.getIndex());
	    DFNode index = cpt.getRValue();
            DFNode node = new ArrayAccessNode(graph, varScope, ref, aa,
					      array, index);
            node.accept(cpt.getValue(ref));
	    cpt.setRValue(node);

	} else if (expr instanceof FieldAccess) {
	    FieldAccess fa = (FieldAccess)expr;
	    SimpleName fieldName = fa.getName();
	    cpt = processExpression(graph, typeScope, varScope, frame, cpt, fa.getExpression());
	    DFNode obj = cpt.getRValue();
	    DFVarRef ref = varScope.lookupField(fieldName);
            DFNode node = new FieldAccessNode(graph, varScope, ref, fa, obj);
            node.accept(cpt.getValue(ref));
	    cpt.setRValue(node);

	} else if (expr instanceof SuperFieldAccess) {
	    SuperFieldAccess sfa = (SuperFieldAccess)expr;
	    SimpleName fieldName = sfa.getName();
	    DFNode obj = cpt.getValue(varScope.lookupThis());
            DFClassScope klass = typeScope.lookupClass(obj.getType());
	    DFVarRef ref = klass.lookupField(fieldName);
            DFNode node = new FieldAccessNode(graph, varScope, ref, sfa, obj);
            node.accept(cpt.getValue(ref));
	    cpt.setRValue(node);

	} else if (expr instanceof CastExpression) {
	    CastExpression cast = (CastExpression)expr;
	    DFTypeRef type = new DFTypeRef(cast.getType());
	    cpt = processExpression(graph, typeScope, varScope, frame, cpt, cast.getExpression());
            DFNode node = new TypeCastNode(graph, varScope, type, cast);
            node.accept(cpt.getRValue());
            cpt.setRValue(node);

	} else if (expr instanceof ClassInstanceCreation) {
	    ClassInstanceCreation cstr = (ClassInstanceCreation)expr;
	    DFTypeRef instType = new DFTypeRef(cstr.getType());
	    Expression expr1 = cstr.getExpression();
	    DFNode obj = null;
	    if (expr1 != null) {
		cpt = processExpression(graph, typeScope, varScope, frame, cpt, expr1);
		obj = cpt.getRValue();
	    }
	    CreateObjectNode call = new CreateObjectNode(
		graph, varScope, instType, cstr, obj);
	    for (Expression arg : (List<Expression>) cstr.arguments()) {
		cpt = processExpression(graph, typeScope, varScope, frame, cpt, arg);
		call.addArg(cpt.getRValue());
	    }
	    cpt.setRValue(call);
	    // Ignore getAnonymousClassDeclaration() here.
	    // It will eventually be picked up as MethodDeclaration.

	} else if (expr instanceof ConditionalExpression) {
	    ConditionalExpression cond = (ConditionalExpression)expr;
	    cpt = processExpression(graph, typeScope, varScope, frame, cpt, cond.getExpression());
	    DFNode condValue = cpt.getRValue();
	    cpt = processExpression(graph, typeScope, varScope, frame, cpt, cond.getThenExpression());
	    DFNode trueValue = cpt.getRValue();
	    cpt = processExpression(graph, typeScope, varScope, frame, cpt, cond.getElseExpression());
	    DFNode falseValue = cpt.getRValue();
	    JoinNode join = new JoinNode(graph, varScope, null, expr, condValue);
	    join.recv(true, trueValue);
	    join.recv(false, falseValue);
	    cpt.setRValue(join);

	} else if (expr instanceof InstanceofExpression) {
	    InstanceofExpression instof = (InstanceofExpression)expr;
	    DFTypeRef type = new DFTypeRef(instof.getRightOperand());
	    cpt = processExpression(graph, typeScope, varScope, frame, cpt,
				    instof.getLeftOperand());
            DFNode node = new InstanceofNode(graph, varScope, instof, type);
            node.accept(cpt.getRValue());
	    cpt.setRValue(node);

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
	(DFGraph graph, DFTypeScope typeScope, DFVarScope varScope,
	 DFFrame frame, DFComponent cpt, Block block)
	throws UnsupportedSyntax {
	DFVarScope childScope = varScope.getChildByAST(block);
	for (Statement cstmt : (List<Statement>) block.statements()) {
	    cpt = processStatement(graph, typeScope, childScope, frame, cpt, cstmt);
	}
	return cpt;
    }

    @SuppressWarnings("unchecked")
    public DFComponent processVariableDeclarationStatement
	(DFGraph graph, DFTypeScope typeScope, DFVarScope varScope,
	 DFFrame frame, DFComponent cpt, VariableDeclarationStatement varStmt)
	throws UnsupportedSyntax {
	return processVariableDeclaration
	    (graph, typeScope, varScope, frame, cpt, varStmt.fragments());
    }

    public DFComponent processExpressionStatement
	(DFGraph graph, DFTypeScope typeScope, DFVarScope varScope,
	 DFFrame frame, DFComponent cpt, ExpressionStatement exprStmt)
	throws UnsupportedSyntax {
	Expression expr = exprStmt.getExpression();
	return processExpression(graph, typeScope, varScope, frame, cpt, expr);
    }

    public DFComponent processIfStatement
	(DFGraph graph, DFTypeScope typeScope, DFVarScope varScope,
	 DFFrame frame, DFComponent cpt, IfStatement ifStmt)
	throws UnsupportedSyntax {
	Expression expr = ifStmt.getExpression();
	cpt = processExpression(graph, typeScope, varScope, frame, cpt, expr);
	DFNode condValue = cpt.getRValue();

	Statement thenStmt = ifStmt.getThenStatement();
	DFComponent thenCpt = new DFComponent(graph, varScope);
	thenCpt = processStatement(graph, typeScope, varScope, frame, thenCpt, thenStmt);

	Statement elseStmt = ifStmt.getElseStatement();
	DFComponent elseCpt = null;
	if (elseStmt != null) {
	    elseCpt = new DFComponent(graph, varScope);
	    elseCpt = processStatement(graph, typeScope, varScope, frame, elseCpt, elseStmt);
	}
	return processConditional(graph, varScope, frame, cpt, ifStmt,
				  condValue, thenCpt, elseCpt);
    }

    private DFComponent processCaseStatement
	(DFGraph graph, DFTypeScope typeScope, DFVarScope varScope,
         DFFrame frame, DFComponent cpt, ASTNode apt,
         DFNode caseNode, DFComponent caseCpt) {

	for (DFVarRef ref : caseCpt.getInputRefs()) {
	    DFNode src = caseCpt.getInput(ref);
	    src.accept(cpt.getValue(ref));
	}

	for (DFVarRef ref : caseCpt.getOutputRefs()) {
	    DFNode dst = caseCpt.getOutput(ref);
	    JoinNode join = new JoinNode(graph, varScope, ref, apt, caseNode);
	    join.recv(true, dst);
	    join.close(cpt.getValue(ref));
	    cpt.setOutput(join);
	}

	return cpt;
    }

    @SuppressWarnings("unchecked")
    public DFComponent processSwitchStatement
	(DFGraph graph, DFTypeScope typeScope, DFVarScope varScope,
	 DFFrame frame, DFComponent cpt, SwitchStatement switchStmt)
	throws UnsupportedSyntax {
	DFVarScope switchScope = varScope.getChildByAST(switchStmt);
	DFFrame switchFrame = frame.getChildByAST(switchStmt);
	cpt = processExpression(graph, typeScope, varScope,
                                frame, cpt, switchStmt.getExpression());
	DFNode switchValue = cpt.getRValue();

	SwitchCase switchCase = null;
	CaseNode caseNode = null;
	DFComponent caseCpt = null;
	for (Statement stmt : (List<Statement>) switchStmt.statements()) {
	    if (stmt instanceof SwitchCase) {
		if (caseCpt != null) {
		    // switchCase, caseNode and caseCpt must be non-null.
		    cpt = processCaseStatement(graph, typeScope, switchScope, switchFrame,
                                               cpt, switchCase, caseNode, caseCpt);
		}
		switchCase = (SwitchCase)stmt;
		caseNode = new CaseNode(graph, switchScope, stmt);
                caseNode.accept(switchValue);
		caseCpt = new DFComponent(graph, switchScope);
		Expression expr = switchCase.getExpression();
		if (expr != null) {
		    cpt = processExpression(graph, typeScope, switchScope, frame, cpt, expr);
		    caseNode.addMatch(cpt.getRValue());
		} else {
		    // "default" case.
		}
	    } else {
		if (caseCpt == null) {
		    // no "case" statement.
		    throw new UnsupportedSyntax(stmt);
		}
		caseCpt = processStatement(graph, typeScope, switchScope,
                                           switchFrame, caseCpt, stmt);
	    }
	}
	if (caseCpt != null) {
	    cpt = processCaseStatement(graph, typeScope, switchScope, switchFrame,
				       cpt, switchCase, caseNode, caseCpt);
	}
	cpt.endFrame(switchFrame);
	return cpt;
    }

    public DFComponent processWhileStatement
	(DFGraph graph, DFTypeScope typeScope, DFVarScope varScope,
	 DFFrame frame, DFComponent cpt, WhileStatement whileStmt)
	throws UnsupportedSyntax {
	DFVarScope loopScope = varScope.getChildByAST(whileStmt);
	DFFrame loopFrame = frame.getChildByAST(whileStmt);
	DFComponent loopCpt = new DFComponent(graph, loopScope);
	loopCpt = processExpression(graph, typeScope, loopScope, frame, loopCpt,
				    whileStmt.getExpression());
	DFNode condValue = loopCpt.getRValue();
	loopCpt = processStatement(graph, typeScope, loopScope, loopFrame, loopCpt,
				   whileStmt.getBody());
	cpt = processLoop(graph, loopScope, frame, cpt, whileStmt,
			  condValue, loopFrame, loopCpt, true);
	cpt.endFrame(loopFrame);
	return cpt;
    }

    public DFComponent processDoStatement
	(DFGraph graph, DFTypeScope typeScope, DFVarScope varScope,
	 DFFrame frame, DFComponent cpt, DoStatement doStmt)
	throws UnsupportedSyntax {
	DFVarScope loopScope = varScope.getChildByAST(doStmt);
	DFFrame loopFrame = frame.getChildByAST(doStmt);
	DFComponent loopCpt = new DFComponent(graph, loopScope);
	loopCpt = processStatement(graph, typeScope, loopScope, loopFrame, loopCpt,
				   doStmt.getBody());
	loopCpt = processExpression(graph, typeScope, loopScope, loopFrame, loopCpt,
				    doStmt.getExpression());
	DFNode condValue = loopCpt.getRValue();
	cpt = processLoop(graph, loopScope, frame, cpt, doStmt,
			  condValue, loopFrame, loopCpt, false);
	cpt.endFrame(loopFrame);
	return cpt;
    }

    @SuppressWarnings("unchecked")
    public DFComponent processForStatement
	(DFGraph graph, DFTypeScope typeScope, DFVarScope varScope,
	 DFFrame frame, DFComponent cpt, ForStatement forStmt)
	throws UnsupportedSyntax {
	DFVarScope loopScope = varScope.getChildByAST(forStmt);
	DFFrame loopFrame = frame.getChildByAST(forStmt);
	DFComponent loopCpt = new DFComponent(graph, loopScope);
	for (Expression init : (List<Expression>) forStmt.initializers()) {
	    cpt = processExpression(graph, typeScope, loopScope, frame, cpt, init);
	}
	Expression expr = forStmt.getExpression();
	DFNode condValue;
	if (expr != null) {
	    loopCpt = processExpression(graph, typeScope, loopScope, loopFrame, loopCpt, expr);
	    condValue = loopCpt.getRValue();
	} else {
	    condValue = new ConstNode(graph, loopScope, DFTypeRef.BOOLEAN, null, "true");
	}
	loopCpt = processStatement(graph, typeScope, loopScope, loopFrame, loopCpt,
				   forStmt.getBody());
	for (Expression update : (List<Expression>) forStmt.updaters()) {
	    loopCpt = processExpression(graph, typeScope, loopScope, loopFrame, loopCpt, update);
	}
	cpt = processLoop(graph, loopScope, frame, cpt, forStmt,
			  condValue, loopFrame, loopCpt, true);
	cpt.endFrame(loopFrame);
	return cpt;
    }

    @SuppressWarnings("unchecked")
    public DFComponent processEnhancedForStatement
	(DFGraph graph, DFTypeScope typeScope, DFVarScope varScope,
	 DFFrame frame, DFComponent cpt, EnhancedForStatement eForStmt)
	throws UnsupportedSyntax {
	DFVarScope loopScope = varScope.getChildByAST(eForStmt);
	DFFrame loopFrame = frame.getChildByAST(eForStmt);
	DFComponent loopCpt = new DFComponent(graph, loopScope);
	Expression expr = eForStmt.getExpression();
	loopCpt = processExpression(graph, typeScope, loopScope, frame, loopCpt, expr);
	SingleVariableDeclaration decl = eForStmt.getParameter();
	DFVarRef ref = loopScope.lookupVar(decl.getName());
	DFNode iterValue = new IterNode(graph, loopScope, ref, expr);
        iterValue.accept(loopCpt.getRValue());
	SingleAssignNode assign = new SingleAssignNode(graph, loopScope, ref, expr);
	assign.accept(iterValue);
	cpt.setOutput(assign);
	loopCpt = processStatement(graph, typeScope, loopScope, loopFrame, loopCpt,
				   eForStmt.getBody());
	cpt = processLoop(graph, loopScope, frame, cpt, eForStmt,
			  iterValue, loopFrame, loopCpt, true);
	cpt.endFrame(loopFrame);
	return cpt;
    }

    @SuppressWarnings("unchecked")
    public DFComponent processStatement
	(DFGraph graph, DFTypeScope typeScope, DFVarScope varScope,
	 DFFrame frame, DFComponent cpt, Statement stmt)
	throws UnsupportedSyntax {

	if (stmt instanceof AssertStatement) {
	    // XXX Ignore asserts.

	} else if (stmt instanceof Block) {
	    cpt = processBlock(
		graph, typeScope, varScope, frame, cpt, (Block)stmt);

	} else if (stmt instanceof EmptyStatement) {

	} else if (stmt instanceof VariableDeclarationStatement) {
	    cpt = processVariableDeclarationStatement(
		graph, typeScope, varScope, frame, cpt, (VariableDeclarationStatement)stmt);

	} else if (stmt instanceof ExpressionStatement) {
	    cpt = processExpressionStatement(
		graph, typeScope, varScope, frame, cpt, (ExpressionStatement)stmt);

	} else if (stmt instanceof IfStatement) {
	    cpt = processIfStatement(
		graph, typeScope, varScope, frame, cpt, (IfStatement)stmt);

	} else if (stmt instanceof SwitchStatement) {
	    cpt = processSwitchStatement(
		graph, typeScope, varScope, frame, cpt, (SwitchStatement)stmt);

	} else if (stmt instanceof SwitchCase) {
	    // Invalid "case" placement.
	    throw new UnsupportedSyntax(stmt);

	} else if (stmt instanceof WhileStatement) {
	    cpt = processWhileStatement(
		graph, typeScope, varScope, frame, cpt, (WhileStatement)stmt);

	} else if (stmt instanceof DoStatement) {
	    cpt = processDoStatement(
		graph, typeScope, varScope, frame, cpt, (DoStatement)stmt);

	} else if (stmt instanceof ForStatement) {
	    cpt = processForStatement(
		graph, typeScope, varScope, frame, cpt, (ForStatement)stmt);

	} else if (stmt instanceof EnhancedForStatement) {
	    cpt = processEnhancedForStatement(
		graph, typeScope, varScope, frame, cpt, (EnhancedForStatement)stmt);

	} else if (stmt instanceof ReturnStatement) {
            ReturnStatement rtrnStmt = (ReturnStatement)stmt;
	    DFFrame dstFrame = frame.find(DFFrame.METHOD);
            Expression expr = rtrnStmt.getExpression();
            if (expr != null) {
                cpt = processExpression(
		    graph, typeScope, varScope, frame, cpt, expr);
                ReturnNode rtrn = new ReturnNode(graph, varScope, rtrnStmt);
                rtrn.accept(cpt.getRValue());
                cpt.addExit(new DFExit(rtrn, dstFrame));
            }
	    for (DFFrame frm = frame; frm != null; frm = frm.getParent()) {
		for (DFVarRef ref : frm.getOutputs()) {
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
		for (DFVarRef ref : frm.getOutputs()) {
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
		for (DFVarRef ref : frm.getOutputs()) {
		    cpt.addExit(new DFExit(cpt.getValue(ref), dstFrame, true));
		}
		if (frm == dstFrame) break;
	    }

	} else if (stmt instanceof LabeledStatement) {
	    LabeledStatement labeledStmt = (LabeledStatement)stmt;
	    DFFrame labeledFrame = frame.getChildByAST(labeledStmt);
	    cpt = processStatement(
		graph, typeScope, varScope, labeledFrame,
		cpt, labeledStmt.getBody());

	} else if (stmt instanceof SynchronizedStatement) {
	    SynchronizedStatement syncStmt = (SynchronizedStatement)stmt;
	    cpt = processStatement(
		graph, typeScope, varScope, frame,
		cpt, syncStmt.getBody());

	} else if (stmt instanceof TryStatement) {
	    // XXX Ignore catch statements (for now).
	    TryStatement tryStmt = (TryStatement)stmt;
	    DFFrame tryFrame = frame.getChildByAST(tryStmt);
	    cpt = processStatement(
		graph, typeScope, varScope, tryFrame,
		cpt, tryStmt.getBody());
	    Block finBlock = tryStmt.getFinally();
	    if (finBlock != null) {
		cpt = processStatement(
		    graph, typeScope, varScope, frame, cpt, finBlock);
	    }

	} else if (stmt instanceof ThrowStatement) {
	    ThrowStatement throwStmt = (ThrowStatement)stmt;
	    cpt = processExpression(
		graph, typeScope, varScope, frame,
		cpt, throwStmt.getExpression());
            ExceptionNode exception = new ExceptionNode(
		graph, varScope, stmt, cpt.getRValue());
	    DFFrame dstFrame = frame.find(DFFrame.TRY);
	    cpt.addExit(new DFExit(exception, dstFrame));
	    for (DFFrame frm = frame; frm != null; frm = frm.getParent()) {
		for (DFVarRef ref : frm.getOutputs()) {
		    cpt.addExit(new DFExit(cpt.getValue(ref), dstFrame, true));
		}
		if (frm == dstFrame) break;
	    }

	} else if (stmt instanceof ConstructorInvocation) {
	    // XXX Ignore all side effects.
	    ConstructorInvocation ci = (ConstructorInvocation)stmt;
	    for (Expression arg : (List<Expression>) ci.arguments()) {
		cpt = processExpression(
		    graph, typeScope, varScope, frame, cpt, arg);
	    }

	} else if (stmt instanceof SuperConstructorInvocation) {
	    // XXX Ignore all side effects.
	    SuperConstructorInvocation sci = (SuperConstructorInvocation)stmt;
	    for (Expression arg : (List<Expression>) sci.arguments()) {
		cpt = processExpression(
		    graph, typeScope, varScope, frame, cpt, arg);
	    }

	} else if (stmt instanceof TypeDeclarationStatement) {
	    // Ignore TypeDeclarationStatement because
	    // it was eventually picked up as MethodDeclaration.

	} else {
	    throw new UnsupportedSyntax(stmt);
	}

	return cpt;
    }

    /// Top-level functions.

    public Exporter exporter;
    public String[] classPath;
    public String[] srcPath;
    public DFTypeScope rootScope;

    public Java2DF(
	Exporter exporter, String[] classPath, String[] srcPath) {
	this.exporter = exporter;
	this.classPath = classPath;
	this.srcPath = srcPath;
        this.rootScope = new DFTypeScope("");
    }

    /**
     * Performs dataflow analysis for a given method.
     */
    @SuppressWarnings("unchecked")
    public DFGraph processMethodDeclaration(
        DFTypeScope typeScope, DFClassScope klass,
        MethodDeclaration method)
        throws UnsupportedSyntax {
	// Ignore method prototypes.
	if (method.getBody() == null) return null;
	String funcName = method.getName().getIdentifier();
	Block funcBlock = method.getBody();
        Type rt = method.getReturnType2();
        DFTypeRef returnType = (rt == null)? null : new DFTypeRef(rt);
        try {
            // Setup an initial scope.
            DFFrame frame = new DFFrame(DFFrame.METHOD);
            DFVarScope varScope = klass.addChild(funcName);
            varScope.build(frame, funcBlock);
            varScope.addReturn(returnType);
            varScope.dump();
            //frame.dump();

            DFGraph graph = new DFGraph(varScope, funcName);
            DFComponent cpt = new DFComponent(graph, varScope);
            // XXX Ignore isContructor().
            // XXX Ignore getReturnType2().
            // XXX Ignore isVarargs().
            int i = 0;
            for (SingleVariableDeclaration decl :
                     (List<SingleVariableDeclaration>) method.parameters()) {
                // XXX Ignore modifiers and dimensions.
                DFTypeRef paramType = new DFTypeRef(decl.getType());
                DFVarRef ref = varScope.addRef(decl.getName(), paramType);
                DFNode param = new ArgNode(graph, varScope, ref, decl, i++);
                DFNode assign = new SingleAssignNode(graph, varScope, ref, decl);
                assign.accept(param);
                cpt.setOutput(assign);
            }
            // Process the function body.
            cpt = processStatement(
                graph, typeScope, varScope, frame, cpt, funcBlock);
            cpt.endFrame(frame);
            // Remove redundant nodes.
            graph.cleanup();

            Utils.logit("Success: "+funcName);
            return graph;
        } catch (UnsupportedSyntax e) {
            //e.printStackTrace();
            e.name = funcName;
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    public void processFieldDeclaration(
        DFTypeScope typeScope, DFClassScope klass,
        FieldDeclaration method) {
	// XXX support instance field.
	// XXX support static field.
    }

    @SuppressWarnings("unchecked")
    public void processTypeDeclaration(
        DFTypeScope typeScope, TypeDeclaration typeDecl)
        throws IOException {
        DFTypeRef type = new DFTypeRef(typeDecl.getName());
        DFClassScope klass = typeScope.lookupClass(typeDecl.getName());
        for (BodyDeclaration body :
                 (List<BodyDeclaration>) typeDecl.bodyDeclarations()) {
	    try {
		if (body instanceof TypeDeclaration) {
		    processTypeDeclaration(
			typeScope, (TypeDeclaration)body);
		} else if (body instanceof FieldDeclaration) {
		    processFieldDeclaration(
			typeScope, klass, (FieldDeclaration)body);
		} else if (body instanceof MethodDeclaration) {
                    DFGraph graph = processMethodDeclaration(
                        typeScope, klass, (MethodDeclaration)body);
                    if (this.exporter != null && graph != null) {
                        this.exporter.writeGraph(graph);
                    }
		}
	    } catch (UnsupportedSyntax e) {
		String astName = e.ast.getClass().getName();
		Utils.logit("Fail: "+e.name+" (Unsupported: "+astName+") "+e.ast);
		if (this.exporter != null) {
		    this.exporter.writeError(e.name, astName);
                }
            }
        }
    }

    private CompilationUnit parseFile(String path)
	throws IOException {
	String src = Utils.readFile(path);
	Map<String, String> options = JavaCore.getOptions();
	JavaCore.setComplianceOptions(JavaCore.VERSION_1_7, options);
	ASTParser parser = ASTParser.newParser(AST.JLS8);
        parser.setUnitName(path);
	parser.setSource(src.toCharArray());
	parser.setKind(ASTParser.K_COMPILATION_UNIT);
	parser.setResolveBindings(true);
	parser.setEnvironment(this.classPath, this.srcPath, null, true);
	parser.setCompilerOptions(options);
	return (CompilationUnit)parser.createAST(null);
    }

    @SuppressWarnings("unchecked")
    public void pass1(String path)
	throws IOException {
	CompilationUnit cunit = parseFile(path);
        DFTypeScope typeScope = this.rootScope;
        PackageDeclaration pkg = cunit.getPackage();
        if (pkg != null) {
            typeScope = typeScope.addChildScope(pkg.getName());
        }
	try {
	    for (TypeDeclaration typeDecl : (List<TypeDeclaration>) cunit.types()) {
		typeScope.build(typeDecl);
	    }
	} catch (UnsupportedSyntax e) {
	    String astName = e.ast.getClass().getName();
	    Utils.logit("Fail: "+e.name+" (Unsupported: "+astName+") "+e.ast);
	}
    }

    @SuppressWarnings("unchecked")
    public void pass2(String path)
	throws IOException {
	CompilationUnit cunit = parseFile(path);
        DFTypeScope typeScope = this.rootScope;
        PackageDeclaration pkg = cunit.getPackage();
        if (pkg != null) {
            typeScope = typeScope.addChildScope(pkg.getName());
        }
	for (TypeDeclaration typeDecl : (List<TypeDeclaration>) cunit.types()) {
	    processTypeDeclaration(typeScope, typeDecl);
	}
    }

    /**
     * Provides a command line interface.
     *
     * Usage: java Java2DF [-o output] input.java ...
     */
    public static void main(String[] args)
	throws IOException {

	// Parse the options.
	String[] classpath = null;
	String[] srcpath = null;
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
	    } else if (arg.equals("-C")) {
		classpath = args[++i].split(";");
	    } else if (arg.startsWith("-")) {
		;
	    } else {
		files.add(arg);
	    }
	}

	// Process files.
	XmlExporter exporter = new XmlExporter();
        Java2DF converter = new Java2DF(exporter, classpath, srcpath);
	for (String path : files) {
	    Utils.logit("Pass1: "+path);
	    try {
		converter.pass1(path);
	    } catch (IOException e) {
		System.err.println("Cannot open input file: "+path);
	    }
	}
	for (String path : files) {
	    Utils.logit("Pass2: "+path);
	    try {
		exporter.startFile(path);
		converter.pass2(path);
		exporter.endFile();
	    } catch (IOException e) {
		System.err.println("Cannot open input file: "+path);
	    }
	}
	converter.rootScope.dump();
	exporter.close();

	Utils.printXml(output, exporter.document);
	output.close();
    }
}
