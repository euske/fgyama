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

    public ProgNode(
        DFGraph graph, DFVarSpace space, DFType type, DFVarRef ref,
        ASTNode ast) {
	super(graph, space, type, ref);
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

    public SingleAssignNode(
        DFGraph graph, DFVarSpace space, DFVarRef ref,
        ASTNode ast) {
	super(graph, space, ref.getType(), ref, ast);
    }

    @Override
    public String getKind() {
	return "assign";
    }
}

// ArrayAssignNode:
class ArrayAssignNode extends ProgNode {

    public ArrayAssignNode(
        DFGraph graph, DFVarSpace space, DFVarRef ref,
        ASTNode ast, DFNode array, DFNode index) {
	super(graph, space, null, ref, ast);
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

    public FieldAssignNode(
        DFGraph graph, DFVarSpace space, DFVarRef ref,
        ASTNode ast, DFNode obj) {
	super(graph, space, null, ref, ast);
	this.accept(obj, "obj");
    }

    @Override
    public String getKind() {
	return "fieldassign";
    }
}

// VarRefNode: represnets a variable reference.
class VarRefNode extends ProgNode {

    public VarRefNode(
        DFGraph graph, DFVarSpace space, DFVarRef ref,
        ASTNode ast) {
	super(graph, space, ref.getType(), ref, ast);
    }

    @Override
    public String getKind() {
	return "ref";
    }
}

// ArrayAccessNode
class ArrayAccessNode extends ProgNode {

    public ArrayAccessNode(
        DFGraph graph, DFVarSpace space, DFVarRef ref,
        ASTNode ast, DFNode array, DFNode index) {
	super(graph, space, null, ref, ast);
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

    public FieldAccessNode(
        DFGraph graph, DFVarSpace space, DFVarRef ref,
        ASTNode ast, DFNode obj) {
	super(graph, space, null, ref, ast);
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

    public PrefixNode(
        DFGraph graph, DFVarSpace space, DFVarRef ref,
        ASTNode ast, PrefixExpression.Operator op) {
	super(graph, space, null, ref, ast);
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

    public PostfixNode(
        DFGraph graph, DFVarSpace space, DFVarRef ref,
        ASTNode ast, PostfixExpression.Operator op) {
	super(graph, space, null, ref, ast);
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

    public InfixNode(
        DFGraph graph, DFVarSpace space, DFType type,
        ASTNode ast, InfixExpression.Operator op,
        DFNode lvalue, DFNode rvalue) {
	super(graph, space, type, null, ast);
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

    public TypeCastNode(
        DFGraph graph, DFVarSpace space, DFType type,
        ASTNode ast) {
	super(graph, space, type, null, ast);
	assert(type != null);
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

    public DFType type;

    public InstanceofNode(
        DFGraph graph, DFVarSpace space,
        ASTNode ast, DFType type) {
	super(graph, space, DFType.BOOLEAN, null, ast);
	assert(type != null);
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

    public CaseNode(
        DFGraph graph, DFVarSpace space,
        ASTNode ast) {
	super(graph, space, null, null, ast);
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

    public AssignOpNode(
        DFGraph graph, DFVarSpace space, DFVarRef ref,
        ASTNode ast, Assignment.Operator op,
        DFNode lvalue, DFNode rvalue) {
	super(graph, space, rvalue.getType(), ref, ast);
	this.op = op;
        if (lvalue != null) {
            this.accept(lvalue, "L");
        }
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

    public ArgNode(
        DFGraph graph, DFVarSpace space, DFVarRef ref,
        ASTNode ast, int index) {
	super(graph, space, ref.getType(), ref, ast);
	this.index = index;
    }

    @Override
    public String getKind() {
	return "arg";
    }

    @Override
    public String getData() {
	return ("arg"+this.index);
    }
}

// ConstNode: represents a constant value.
class ConstNode extends ProgNode {

    public String data;

    public ConstNode(
        DFGraph graph, DFVarSpace space, DFType type,
        ASTNode ast, String data) {
	super(graph, space, type, null, ast);
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

    public ArrayValueNode(
        DFGraph graph, DFVarSpace space,
        ASTNode ast) {
	super(graph, space, null, null, ast);
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

    public JoinNode(
        DFGraph graph, DFVarSpace space, DFVarRef ref,
        ASTNode ast, DFNode cond) {
	super(graph, space, null, ref, ast);
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

    public LoopBeginNode(
        DFGraph graph, DFVarSpace space, DFVarRef ref,
        ASTNode ast, DFNode enter) {
	super(graph, space, null, ref, ast);
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

    public LoopEndNode(
        DFGraph graph, DFVarSpace space, DFVarRef ref,
        ASTNode ast, DFNode cond) {
	super(graph, space, null, ref, ast);
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

    public LoopRepeatNode(
        DFGraph graph, DFVarSpace space, DFVarRef ref,
        ASTNode ast) {
	super(graph, space, null, ref, ast);
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

    public IterNode(
        DFGraph graph, DFVarSpace space, DFVarRef ref,
        ASTNode ast) {
	super(graph, space, null, ref, ast);
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

    public CallNode(
        DFGraph graph, DFVarSpace space, DFType type, DFVarRef ref,
        ASTNode ast) {
	super(graph, space, type, ref, ast);
	this.args = null;
        this.exception = null;
    }

    @Override
    public String getKind() {
	return "call";
    }

    public void setArgs(List<DFNode> args) {
        for (int i = 0; i < args.size(); i++) {
            String label = "arg"+i;
            this.accept(args.get(i), label);
        }
	this.args = args;
    }
}

// MethodCallNode
class MethodCallNode extends CallNode {

    public DFMethod method;

    public MethodCallNode(
        DFGraph graph, DFVarSpace space, DFMethod method,
        ASTNode ast, DFNode obj) {
	super(graph, space, method.getReturnType(), null, ast);
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

    public CreateObjectNode(
        DFGraph graph, DFVarSpace space, DFType type,
        ASTNode ast, DFNode obj) {
	super(graph, space, type, null, ast);
	assert(type != null);
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

    public ReturnNode(
        DFGraph graph, DFVarSpace space,
        ASTNode ast) {
	super(graph, space, null, space.lookupReturn(), ast);
    }

    @Override
    public String getKind() {
	return "return";
    }
}

// ExceptionNode
class ExceptionNode extends ProgNode {

    public ExceptionNode(
        DFGraph graph, DFVarSpace space,
        ASTNode ast, DFNode value) {
	super(graph, space, null, null, ast);
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
    public DFComponent processConditional(
        DFGraph graph, DFVarSpace varSpace,
        DFFrame frame, DFComponent cpt,
        ASTNode ast, DFNode condValue,
        DFComponent trueCpt, DFComponent falseCpt) {

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
	    JoinNode join = new JoinNode(graph, varSpace, ref, ast, condValue);
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
		JoinNode join = new JoinNode(
                    graph, varSpace, node.getRef(), null, condValue);
		join.recv(true, node);
		cpt.addExit(exit.wrap(join));
	    }
	}
	if (falseCpt != null) {
	    for (DFExit exit : falseCpt.getExits()) {
                DFNode node = exit.getNode();
		JoinNode join = new JoinNode(
                    graph, varSpace, node.getRef(), null, condValue);
		join.recv(false, node);
		cpt.addExit(exit.wrap(join));
	    }
	}

	return cpt;
    }

    /**
     * Expands the graph for the loop variables.
     */
    public DFComponent processLoop(
        DFGraph graph, DFVarSpace varSpace,
        DFFrame frame, DFComponent cpt,
        ASTNode ast, DFNode condValue,
        DFFrame loopFrame, DFComponent loopCpt, boolean preTest)
	throws UnsupportedSyntax {

	// Add four nodes for each loop variable.
	Map<DFVarRef, LoopBeginNode> begins =
            new HashMap<DFVarRef, LoopBeginNode>();
	Map<DFVarRef, LoopRepeatNode> repeats =
            new HashMap<DFVarRef, LoopRepeatNode>();
	Map<DFVarRef, DFNode> ends =
            new HashMap<DFVarRef, DFNode>();
	DFVarRef[] loopRefs = loopFrame.getInsAndOuts();
	for (DFVarRef ref : loopRefs) {
	    DFNode src = cpt.getValue(ref);
	    LoopBeginNode begin = new LoopBeginNode(graph, varSpace, ref, ast, src);
	    LoopRepeatNode repeat = new LoopRepeatNode(graph, varSpace, ref, ast);
	    LoopEndNode end = new LoopEndNode(graph, varSpace, ref, ast, condValue);
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
    public DFComponent processVariableDeclaration(
	DFGraph graph, DFTypeSpace typeSpace, DFVarSpace varSpace,
        DFFrame frame, DFComponent cpt, List<VariableDeclarationFragment> frags)
	throws UnsupportedSyntax {

	for (VariableDeclarationFragment frag : frags) {
            DFVarRef ref = varSpace.lookupVar(frag.getName());
            assert(ref != null);
	    Expression init = frag.getInitializer();
	    if (init != null) {
		cpt = processExpression(
                    graph, typeSpace, varSpace, frame, cpt, init);
		DFNode assign = new SingleAssignNode(graph, varSpace, ref, frag);
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
    public DFComponent processAssignment(
	DFGraph graph, DFTypeSpace typeSpace, DFVarSpace varSpace,
        DFFrame frame, DFComponent cpt, Expression expr)
	throws UnsupportedSyntax {

	if (expr instanceof Name) {
	    Name name = (Name)expr;
	    if (name.isSimpleName()) {
		DFVarRef ref = varSpace.lookupVarOrField((SimpleName)name);
		cpt.setLValue(new SingleAssignNode(graph, varSpace, ref, expr));
	    } else {
		// QualifiedName == FieldAccess
		QualifiedName qn = (QualifiedName)name;
		SimpleName fieldName = qn.getName();
		cpt = processExpression(
                    graph, typeSpace, varSpace, frame, cpt, qn.getQualifier());
		DFNode obj = cpt.getRValue();
                DFClassSpace klass = typeSpace.resolveClass(obj.getType());
                DFVarRef ref = klass.lookupField(fieldName);
		cpt.setLValue(new FieldAssignNode(graph, varSpace, ref, expr, obj));
	    }

	} else if (expr instanceof ArrayAccess) {
	    ArrayAccess aa = (ArrayAccess)expr;
	    cpt = processExpression(
                graph, typeSpace, varSpace, frame, cpt, aa.getArray());
	    DFNode array = cpt.getRValue();
	    cpt = processExpression(
                graph, typeSpace, varSpace, frame, cpt, aa.getIndex());
	    DFNode index = cpt.getRValue();
	    DFVarRef ref = varSpace.lookupArray();
	    cpt.setLValue(new ArrayAssignNode(graph, varSpace, ref, expr, array, index));

	} else if (expr instanceof FieldAccess) {
	    FieldAccess fa = (FieldAccess)expr;
	    SimpleName fieldName = fa.getName();
	    cpt = processExpression(
                graph, typeSpace, varSpace, frame, cpt, fa.getExpression());
	    DFNode obj = cpt.getRValue();
            DFClassSpace klass = typeSpace.resolveClass(obj.getType());
	    DFVarRef ref = klass.lookupField(fieldName);
	    cpt.setLValue(new FieldAssignNode(graph, varSpace, ref, expr, obj));

	} else {
	    throw new UnsupportedSyntax(expr);
	}

	return cpt;
    }

    /**
     * Creates a value node.
     */
    @SuppressWarnings("unchecked")
    public DFComponent processExpression(
	DFGraph graph, DFTypeSpace typeSpace, DFVarSpace varSpace,
        DFFrame frame, DFComponent cpt, Expression expr)
	throws UnsupportedSyntax {

	if (expr instanceof Annotation) {

	} else if (expr instanceof Name) {
	    Name name = (Name)expr;
	    if (name.isSimpleName()) {
		DFVarRef ref = varSpace.lookupVarOrField((SimpleName)name);
                DFNode node = new VarRefNode(graph, varSpace, ref, expr);
                node.accept(cpt.getValue(ref));
		cpt.setRValue(node);
	    } else {
		// QualifiedName == FieldAccess
		QualifiedName qn = (QualifiedName)name;
		SimpleName fieldName = qn.getName();
		cpt = processExpression(
                    graph, typeSpace, varSpace, frame, cpt, qn.getQualifier());
		DFNode obj = cpt.getRValue();
                DFClassSpace klass = typeSpace.resolveClass(obj.getType());
		DFVarRef ref = klass.lookupField(fieldName);
                DFNode node = new FieldAccessNode(graph, varSpace, ref, qn, obj);
                node.accept(cpt.getValue(ref));
		cpt.setRValue(node);
	    }

	} else if (expr instanceof ThisExpression) {
	    DFVarRef ref = varSpace.lookupThis();
            DFNode node = new VarRefNode(graph, varSpace, ref, expr);
            node.accept(cpt.getValue(ref));
	    cpt.setRValue(node);

	} else if (expr instanceof BooleanLiteral) {
	    boolean value = ((BooleanLiteral)expr).booleanValue();
	    cpt.setRValue(new ConstNode(
                              graph, varSpace, DFType.BOOLEAN,
                              expr, Boolean.toString(value)));

	} else if (expr instanceof CharacterLiteral) {
	    char value = ((CharacterLiteral)expr).charValue();
	    cpt.setRValue(new ConstNode(
                              graph, varSpace, DFType.CHAR,
                              expr, "'"+Character.toString(value)+"'"));

	} else if (expr instanceof NullLiteral) {
	    cpt.setRValue(new ConstNode(
                              graph, varSpace, DFType.NULL,
                              expr, "null"));

	} else if (expr instanceof NumberLiteral) {
	    String value = ((NumberLiteral)expr).getToken();
	    cpt.setRValue(new ConstNode(
                              graph, varSpace, DFType.NUMBER,
                              expr, value));

	} else if (expr instanceof StringLiteral) {
	    String value = ((StringLiteral)expr).getLiteralValue();
	    cpt.setRValue(new ConstNode(
                              graph, varSpace, DFType.STRING,
                              expr, "\""+value+"\""));

	} else if (expr instanceof TypeLiteral) {
	    Type value = ((TypeLiteral)expr).getType();
	    cpt.setRValue(new ConstNode(
                              graph, varSpace, DFType.TYPE,
                              expr, Utils.getTypeName(value)));

	} else if (expr instanceof PrefixExpression) {
	    PrefixExpression prefix = (PrefixExpression)expr;
	    PrefixExpression.Operator op = prefix.getOperator();
	    Expression operand = prefix.getOperand();
	    cpt = processExpression(
                graph, typeSpace, varSpace, frame, cpt, operand);
	    if (op == PrefixExpression.Operator.INCREMENT ||
		op == PrefixExpression.Operator.DECREMENT) {
		cpt = processAssignment(
                    graph, typeSpace, varSpace, frame, cpt, operand);
		DFNode assign = cpt.getLValue();
		DFNode value = new PrefixNode(
                    graph, varSpace, assign.getRef(),
                    expr, op);
                value.accept(cpt.getRValue());
		assign.accept(value);
		cpt.setOutput(assign);
		cpt.setRValue(value);
	    } else {
                DFNode value = new PrefixNode(
                    graph, varSpace, null,
                    expr, op);
                value.accept(cpt.getRValue());
		cpt.setRValue(value);
	    }

	} else if (expr instanceof PostfixExpression) {
	    PostfixExpression postfix = (PostfixExpression)expr;
	    PostfixExpression.Operator op = postfix.getOperator();
	    Expression operand = postfix.getOperand();
	    cpt = processAssignment(
                graph, typeSpace, varSpace, frame, cpt, operand);
	    if (op == PostfixExpression.Operator.INCREMENT ||
		op == PostfixExpression.Operator.DECREMENT) {
		DFNode assign = cpt.getLValue();
		cpt = processExpression(
                    graph, typeSpace, varSpace, frame, cpt, operand);
                DFNode node = new PostfixNode(graph, varSpace, assign.getRef(),
                                              expr, op);
                node.accept(cpt.getRValue());
		assign.accept(node);
		cpt.setOutput(assign);
	    }

	} else if (expr instanceof InfixExpression) {
	    InfixExpression infix = (InfixExpression)expr;
	    InfixExpression.Operator op = infix.getOperator();
	    cpt = processExpression(graph, typeSpace, varSpace, frame, cpt, infix.getLeftOperand());
	    DFNode lvalue = cpt.getRValue();
	    cpt = processExpression(graph, typeSpace, varSpace, frame, cpt, infix.getRightOperand());
	    DFNode rvalue = cpt.getRValue();
            DFType type = lvalue.getType(); // XXX Todo: implicit type coersion.
	    cpt.setRValue(new InfixNode(graph, varSpace, type,
                                        expr, op, lvalue, rvalue));

	} else if (expr instanceof ParenthesizedExpression) {
	    ParenthesizedExpression paren = (ParenthesizedExpression)expr;
	    cpt = processExpression(graph, typeSpace, varSpace, frame, cpt, paren.getExpression());

	} else if (expr instanceof Assignment) {
	    Assignment assn = (Assignment)expr;
	    Assignment.Operator op = assn.getOperator();
	    cpt = processAssignment(graph, typeSpace, varSpace, frame, cpt, assn.getLeftHandSide());
	    DFNode assign = cpt.getLValue();
	    cpt = processExpression(graph, typeSpace, varSpace, frame, cpt, assn.getRightHandSide());
	    DFNode rvalue = cpt.getRValue();
	    DFNode lvalue = ((op == Assignment.Operator.ASSIGN)?
                             null : cpt.getValue(assign.getRef()));
	    assign.accept(new AssignOpNode(
                              graph, varSpace, assign.getRef(), assn,
                              op, lvalue, rvalue));
	    cpt.setOutput(assign);
	    cpt.setRValue(assign);

	} else if (expr instanceof VariableDeclarationExpression) {
	    VariableDeclarationExpression decl = (VariableDeclarationExpression)expr;
	    cpt = processVariableDeclaration(
                graph, typeSpace, varSpace, frame, cpt, decl.fragments());

	} else if (expr instanceof MethodInvocation) {
	    MethodInvocation invoke = (MethodInvocation)expr;
	    Expression expr1 = invoke.getExpression();
	    DFNode obj;
	    if (expr1 == null) {
                obj = cpt.getValue(varSpace.lookupThis());
            } else {
		cpt = processExpression(
                    graph, typeSpace, varSpace, frame, cpt, expr1);
		obj = cpt.getRValue();
	    }
            List<DFNode> args = new ArrayList<DFNode>();
            for (Expression arg : (List<Expression>) invoke.arguments()) {
                cpt = processExpression(
                    graph, typeSpace, varSpace, frame, cpt, arg);
                args.add(cpt.getRValue());
            }
            DFClassSpace klass = typeSpace.resolveClass(obj.getType());
            DFMethod[] methods = klass.lookupMethods(invoke.getName());
            MethodCallNode call = null;
            for (DFMethod method : methods) {
                call = new MethodCallNode(graph, varSpace, method, invoke, obj);
                call.setArgs(args);
            }
            assert(call != null);
            cpt.setRValue(call);
            if (call.exception != null) {
		DFFrame dstFrame = frame.find(DFFrame.TRY);
		cpt.addExit(new DFExit(call.exception, dstFrame));
            }

	} else if (expr instanceof SuperMethodInvocation) {
	    SuperMethodInvocation sinvoke = (SuperMethodInvocation)expr;
            DFNode obj = cpt.getValue(varSpace.lookupThis());
            List<DFNode> args = new ArrayList<DFNode>();
            for (Expression arg : (List<Expression>) sinvoke.arguments()) {
                cpt = processExpression(
                    graph, typeSpace, varSpace, frame, cpt, arg);
                args.add(cpt.getRValue());
            }
            DFClassSpace klass = typeSpace.resolveClass(obj.getType());
            DFClassSpace baseKlass = klass.getBase();
            DFMethod[] methods = baseKlass.lookupMethods(sinvoke.getName());
            MethodCallNode call = null;
            for (DFMethod method : methods) {
                call = new MethodCallNode(graph, varSpace, method, sinvoke, obj);
		call.setArgs(args);
	    }
            assert(call != null);
	    cpt.setRValue(call);
            if (call.exception != null) {
		DFFrame dstFrame = frame.find(DFFrame.TRY);
		cpt.addExit(new DFExit(call.exception, dstFrame));
            }

	} else if (expr instanceof ArrayCreation) {
	    ArrayCreation ac = (ArrayCreation)expr;
	    for (Expression dim : (List<Expression>) ac.dimensions()) {
		// XXX cpt.getRValue() is not used (for now).
		cpt = processExpression(
                    graph, typeSpace, varSpace, frame, cpt, dim);
	    }
	    ArrayInitializer init = ac.getInitializer();
	    if (init != null) {
		cpt = processExpression(
                    graph, typeSpace, varSpace, frame, cpt, init);
	    } else {
		cpt.setRValue(new ArrayValueNode(graph, varSpace, ac));
	    }

	} else if (expr instanceof ArrayInitializer) {
	    ArrayInitializer init = (ArrayInitializer)expr;
	    ArrayValueNode arr = new ArrayValueNode(graph, varSpace, init);
	    for (Expression expr1 : (List<Expression>) init.expressions()) {
		cpt = processExpression(
                    graph, typeSpace, varSpace, frame, cpt, expr1);
		arr.addValue(cpt.getRValue());
	    }
	    cpt.setRValue(arr);
	    // XXX array ref is not used.

	} else if (expr instanceof ArrayAccess) {
	    ArrayAccess aa = (ArrayAccess)expr;
	    DFVarRef ref = varSpace.lookupArray();
	    cpt = processExpression(
                graph, typeSpace, varSpace, frame, cpt, aa.getArray());
	    DFNode array = cpt.getRValue();
	    cpt = processExpression(
                graph, typeSpace, varSpace, frame, cpt, aa.getIndex());
	    DFNode index = cpt.getRValue();
            DFNode node = new ArrayAccessNode(
                graph, varSpace, ref, aa,
                array, index);
            node.accept(cpt.getValue(ref));
	    cpt.setRValue(node);

	} else if (expr instanceof FieldAccess) {
	    FieldAccess fa = (FieldAccess)expr;
	    SimpleName fieldName = fa.getName();
	    cpt = processExpression(
                graph, typeSpace, varSpace, frame, cpt, fa.getExpression());
	    DFNode obj = cpt.getRValue();
            DFClassSpace klass = typeSpace.resolveClass(obj.getType());
	    DFVarRef ref = klass.lookupField(fieldName);
            DFNode node = new FieldAccessNode(graph, varSpace, ref, fa, obj);
            node.accept(cpt.getValue(ref));
	    cpt.setRValue(node);

	} else if (expr instanceof SuperFieldAccess) {
	    SuperFieldAccess sfa = (SuperFieldAccess)expr;
	    SimpleName fieldName = sfa.getName();
	    DFNode obj = cpt.getValue(varSpace.lookupThis());
            DFClassSpace klass = typeSpace.resolveClass(obj.getType());
	    DFVarRef ref = klass.lookupField(fieldName);
            DFNode node = new FieldAccessNode(graph, varSpace, ref, sfa, obj);
            node.accept(cpt.getValue(ref));
	    cpt.setRValue(node);

	} else if (expr instanceof CastExpression) {
	    CastExpression cast = (CastExpression)expr;
	    DFType type = typeSpace.resolve(cast.getType());
	    cpt = processExpression(
                graph, typeSpace, varSpace, frame, cpt, cast.getExpression());
            DFNode node = new TypeCastNode(graph, varSpace, type, cast);
            node.accept(cpt.getRValue());
            cpt.setRValue(node);

	} else if (expr instanceof ClassInstanceCreation) {
	    ClassInstanceCreation cstr = (ClassInstanceCreation)expr;
	    DFType instType = typeSpace.resolve(cstr.getType());
	    Expression expr1 = cstr.getExpression();
	    DFNode obj = null;
	    if (expr1 != null) {
		cpt = processExpression(
                    graph, typeSpace, varSpace, frame, cpt, expr1);
		obj = cpt.getRValue();
	    }
            List<DFNode> args = new ArrayList<DFNode>();
	    for (Expression arg : (List<Expression>) cstr.arguments()) {
		cpt = processExpression(
                    graph, typeSpace, varSpace, frame, cpt, arg);
                args.add(cpt.getRValue());
            }
	    CreateObjectNode call = new CreateObjectNode(
		graph, varSpace, instType, cstr, obj);
            call.setArgs(args);
	    cpt.setRValue(call);
	    // Ignore getAnonymousClassDeclaration() here.
	    // It will eventually be picked up as MethodDeclaration.

	} else if (expr instanceof ConditionalExpression) {
	    ConditionalExpression cond = (ConditionalExpression)expr;
	    cpt = processExpression(
                graph, typeSpace, varSpace, frame, cpt, cond.getExpression());
	    DFNode condValue = cpt.getRValue();
	    cpt = processExpression(
                graph, typeSpace, varSpace, frame, cpt, cond.getThenExpression());
	    DFNode trueValue = cpt.getRValue();
	    cpt = processExpression(
                graph, typeSpace, varSpace, frame, cpt, cond.getElseExpression());
	    DFNode falseValue = cpt.getRValue();
	    JoinNode join = new JoinNode(graph, varSpace, null, expr, condValue);
	    join.recv(true, trueValue);
	    join.recv(false, falseValue);
	    cpt.setRValue(join);

	} else if (expr instanceof InstanceofExpression) {
	    InstanceofExpression instof = (InstanceofExpression)expr;
	    DFType type = typeSpace.resolve(instof.getRightOperand());
	    cpt = processExpression(
                graph, typeSpace, varSpace, frame, cpt,
                instof.getLeftOperand());
            DFNode node = new InstanceofNode(graph, varSpace, instof, type);
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
    public DFComponent processBlock(
        DFGraph graph, DFTypeSpace typeSpace, DFVarSpace varSpace,
        DFFrame frame, DFComponent cpt, Block block)
	throws UnsupportedSyntax {
	DFVarSpace childSpace = varSpace.getChildByAST(block);
	for (Statement cstmt : (List<Statement>) block.statements()) {
	    cpt = processStatement(
                graph, typeSpace, childSpace, frame, cpt, cstmt);
	}
	return cpt;
    }

    @SuppressWarnings("unchecked")
    public DFComponent processVariableDeclarationStatement(
	DFGraph graph, DFTypeSpace typeSpace, DFVarSpace varSpace,
        DFFrame frame, DFComponent cpt, VariableDeclarationStatement varStmt)
	throws UnsupportedSyntax {
	return processVariableDeclaration(
            graph, typeSpace, varSpace, frame, cpt, varStmt.fragments());
    }

    public DFComponent processExpressionStatement(
	DFGraph graph, DFTypeSpace typeSpace, DFVarSpace varSpace,
	DFFrame frame, DFComponent cpt, ExpressionStatement exprStmt)
	throws UnsupportedSyntax {
	Expression expr = exprStmt.getExpression();
	return processExpression(
            graph, typeSpace, varSpace, frame, cpt, expr);
    }

    public DFComponent processIfStatement(
	DFGraph graph, DFTypeSpace typeSpace, DFVarSpace varSpace,
	DFFrame frame, DFComponent cpt, IfStatement ifStmt)
	throws UnsupportedSyntax {
	Expression expr = ifStmt.getExpression();
	cpt = processExpression(graph, typeSpace, varSpace, frame, cpt, expr);
	DFNode condValue = cpt.getRValue();

	Statement thenStmt = ifStmt.getThenStatement();
	DFComponent thenCpt = new DFComponent(graph, varSpace);
	thenCpt = processStatement(
            graph, typeSpace, varSpace, frame, thenCpt, thenStmt);

	Statement elseStmt = ifStmt.getElseStatement();
	DFComponent elseCpt = null;
	if (elseStmt != null) {
	    elseCpt = new DFComponent(graph, varSpace);
	    elseCpt = processStatement(
                graph, typeSpace, varSpace, frame, elseCpt, elseStmt);
	}
	return processConditional(
            graph, varSpace, frame, cpt, ifStmt,
            condValue, thenCpt, elseCpt);
    }

    private DFComponent processCaseStatement(
	DFGraph graph, DFTypeSpace typeSpace, DFVarSpace varSpace,
        DFFrame frame, DFComponent cpt, ASTNode apt,
        DFNode caseNode, DFComponent caseCpt) {

	for (DFVarRef ref : caseCpt.getInputRefs()) {
	    DFNode src = caseCpt.getInput(ref);
	    src.accept(cpt.getValue(ref));
	}

	for (DFVarRef ref : caseCpt.getOutputRefs()) {
	    DFNode dst = caseCpt.getOutput(ref);
	    JoinNode join = new JoinNode(graph, varSpace, ref, apt, caseNode);
	    join.recv(true, dst);
	    join.close(cpt.getValue(ref));
	    cpt.setOutput(join);
	}

	return cpt;
    }

    @SuppressWarnings("unchecked")
    public DFComponent processSwitchStatement(
	DFGraph graph, DFTypeSpace typeSpace, DFVarSpace varSpace,
	DFFrame frame, DFComponent cpt, SwitchStatement switchStmt)
	throws UnsupportedSyntax {
	DFVarSpace switchSpace = varSpace.getChildByAST(switchStmt);
	DFFrame switchFrame = frame.getChildByAST(switchStmt);
	cpt = processExpression(
            graph, typeSpace, varSpace,
            frame, cpt, switchStmt.getExpression());
	DFNode switchValue = cpt.getRValue();

	SwitchCase switchCase = null;
	CaseNode caseNode = null;
	DFComponent caseCpt = null;
	for (Statement stmt : (List<Statement>) switchStmt.statements()) {
	    if (stmt instanceof SwitchCase) {
		if (caseCpt != null) {
		    // switchCase, caseNode and caseCpt must be non-null.
		    cpt = processCaseStatement(
                        graph, typeSpace, switchSpace, switchFrame,
                        cpt, switchCase, caseNode, caseCpt);
		}
		switchCase = (SwitchCase)stmt;
		caseNode = new CaseNode(graph, switchSpace, stmt);
                caseNode.accept(switchValue);
		caseCpt = new DFComponent(graph, switchSpace);
		Expression expr = switchCase.getExpression();
		if (expr != null) {
		    cpt = processExpression(
                        graph, typeSpace, switchSpace, frame, cpt, expr);
		    caseNode.addMatch(cpt.getRValue());
		} else {
		    // "default" case.
		}
	    } else {
		if (caseCpt == null) {
		    // no "case" statement.
		    throw new UnsupportedSyntax(stmt);
		}
		caseCpt = processStatement(
                    graph, typeSpace, switchSpace,
                    switchFrame, caseCpt, stmt);
	    }
	}
	if (caseCpt != null) {
	    cpt = processCaseStatement(
                graph, typeSpace, switchSpace, switchFrame,
                cpt, switchCase, caseNode, caseCpt);
	}
	cpt.endFrame(switchFrame);
	return cpt;
    }

    public DFComponent processWhileStatement(
	DFGraph graph, DFTypeSpace typeSpace, DFVarSpace varSpace,
	DFFrame frame, DFComponent cpt, WhileStatement whileStmt)
	throws UnsupportedSyntax {
	DFVarSpace loopSpace = varSpace.getChildByAST(whileStmt);
	DFFrame loopFrame = frame.getChildByAST(whileStmt);
	DFComponent loopCpt = new DFComponent(graph, loopSpace);
	loopCpt = processExpression(
            graph, typeSpace, loopSpace, frame, loopCpt,
            whileStmt.getExpression());
	DFNode condValue = loopCpt.getRValue();
	loopCpt = processStatement(
            graph, typeSpace, loopSpace, loopFrame, loopCpt,
            whileStmt.getBody());
	cpt = processLoop(
            graph, loopSpace, frame, cpt, whileStmt,
            condValue, loopFrame, loopCpt, true);
	cpt.endFrame(loopFrame);
	return cpt;
    }

    public DFComponent processDoStatement(
	DFGraph graph, DFTypeSpace typeSpace, DFVarSpace varSpace,
        DFFrame frame, DFComponent cpt, DoStatement doStmt)
	throws UnsupportedSyntax {
	DFVarSpace loopSpace = varSpace.getChildByAST(doStmt);
	DFFrame loopFrame = frame.getChildByAST(doStmt);
	DFComponent loopCpt = new DFComponent(graph, loopSpace);
	loopCpt = processStatement(
            graph, typeSpace, loopSpace, loopFrame, loopCpt,
            doStmt.getBody());
	loopCpt = processExpression(
            graph, typeSpace, loopSpace, loopFrame, loopCpt,
            doStmt.getExpression());
	DFNode condValue = loopCpt.getRValue();
	cpt = processLoop(
            graph, loopSpace, frame, cpt, doStmt,
            condValue, loopFrame, loopCpt, false);
	cpt.endFrame(loopFrame);
	return cpt;
    }

    @SuppressWarnings("unchecked")
    public DFComponent processForStatement(
	DFGraph graph, DFTypeSpace typeSpace, DFVarSpace varSpace,
	DFFrame frame, DFComponent cpt, ForStatement forStmt)
	throws UnsupportedSyntax {
	DFVarSpace loopSpace = varSpace.getChildByAST(forStmt);
	DFFrame loopFrame = frame.getChildByAST(forStmt);
	DFComponent loopCpt = new DFComponent(graph, loopSpace);
	for (Expression init : (List<Expression>) forStmt.initializers()) {
	    cpt = processExpression(
                graph, typeSpace, loopSpace, frame, cpt, init);
	}
	Expression expr = forStmt.getExpression();
	DFNode condValue;
	if (expr != null) {
	    loopCpt = processExpression(
                graph, typeSpace, loopSpace, loopFrame, loopCpt, expr);
	    condValue = loopCpt.getRValue();
	} else {
	    condValue = new ConstNode(graph, loopSpace, DFType.BOOLEAN, null, "true");
	}
	loopCpt = processStatement(
            graph, typeSpace, loopSpace, loopFrame, loopCpt,
            forStmt.getBody());
	for (Expression update : (List<Expression>) forStmt.updaters()) {
	    loopCpt = processExpression(
                graph, typeSpace, loopSpace, loopFrame, loopCpt, update);
	}
	cpt = processLoop(
            graph, loopSpace, frame, cpt, forStmt,
            condValue, loopFrame, loopCpt, true);
	cpt.endFrame(loopFrame);
	return cpt;
    }

    @SuppressWarnings("unchecked")
    public DFComponent processEnhancedForStatement(
	DFGraph graph, DFTypeSpace typeSpace, DFVarSpace varSpace,
        DFFrame frame, DFComponent cpt, EnhancedForStatement eForStmt)
	throws UnsupportedSyntax {
	DFVarSpace loopSpace = varSpace.getChildByAST(eForStmt);
	DFFrame loopFrame = frame.getChildByAST(eForStmt);
	DFComponent loopCpt = new DFComponent(graph, loopSpace);
	Expression expr = eForStmt.getExpression();
	loopCpt = processExpression(
            graph, typeSpace, loopSpace, frame, loopCpt, expr);
	SingleVariableDeclaration decl = eForStmt.getParameter();
	DFVarRef ref = loopSpace.lookupVar(decl.getName());
        assert(ref != null);
	DFNode iterValue = new IterNode(graph, loopSpace, ref, expr);
        iterValue.accept(loopCpt.getRValue());
	SingleAssignNode assign = new SingleAssignNode(graph, loopSpace, ref, expr);
	assign.accept(iterValue);
	cpt.setOutput(assign);
	loopCpt = processStatement(
            graph, typeSpace, loopSpace, loopFrame, loopCpt,
            eForStmt.getBody());
	cpt = processLoop(
            graph, loopSpace, frame, cpt, eForStmt,
            iterValue, loopFrame, loopCpt, true);
	cpt.endFrame(loopFrame);
	return cpt;
    }

    @SuppressWarnings("unchecked")
    public DFComponent processStatement(
	DFGraph graph, DFTypeSpace typeSpace, DFVarSpace varSpace,
	DFFrame frame, DFComponent cpt, Statement stmt)
	throws UnsupportedSyntax {

	if (stmt instanceof AssertStatement) {
	    // XXX Ignore asserts.

	} else if (stmt instanceof Block) {
	    cpt = processBlock(
		graph, typeSpace, varSpace, frame, cpt, (Block)stmt);

	} else if (stmt instanceof EmptyStatement) {

	} else if (stmt instanceof VariableDeclarationStatement) {
	    cpt = processVariableDeclarationStatement(
		graph, typeSpace, varSpace, frame, cpt, (VariableDeclarationStatement)stmt);

	} else if (stmt instanceof ExpressionStatement) {
	    cpt = processExpressionStatement(
		graph, typeSpace, varSpace, frame, cpt, (ExpressionStatement)stmt);

	} else if (stmt instanceof IfStatement) {
	    cpt = processIfStatement(
		graph, typeSpace, varSpace, frame, cpt, (IfStatement)stmt);

	} else if (stmt instanceof SwitchStatement) {
	    cpt = processSwitchStatement(
		graph, typeSpace, varSpace, frame, cpt, (SwitchStatement)stmt);

	} else if (stmt instanceof SwitchCase) {
	    // Invalid "case" placement.
	    throw new UnsupportedSyntax(stmt);

	} else if (stmt instanceof WhileStatement) {
	    cpt = processWhileStatement(
		graph, typeSpace, varSpace, frame, cpt, (WhileStatement)stmt);

	} else if (stmt instanceof DoStatement) {
	    cpt = processDoStatement(
		graph, typeSpace, varSpace, frame, cpt, (DoStatement)stmt);

	} else if (stmt instanceof ForStatement) {
	    cpt = processForStatement(
		graph, typeSpace, varSpace, frame, cpt, (ForStatement)stmt);

	} else if (stmt instanceof EnhancedForStatement) {
	    cpt = processEnhancedForStatement(
		graph, typeSpace, varSpace, frame, cpt, (EnhancedForStatement)stmt);

	} else if (stmt instanceof ReturnStatement) {
            ReturnStatement rtrnStmt = (ReturnStatement)stmt;
	    DFFrame dstFrame = frame.find(DFFrame.METHOD);
            Expression expr = rtrnStmt.getExpression();
            if (expr != null) {
                cpt = processExpression(
		    graph, typeSpace, varSpace, frame, cpt, expr);
                ReturnNode rtrn = new ReturnNode(graph, varSpace, rtrnStmt);
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
		graph, typeSpace, varSpace, labeledFrame,
		cpt, labeledStmt.getBody());

	} else if (stmt instanceof SynchronizedStatement) {
	    SynchronizedStatement syncStmt = (SynchronizedStatement)stmt;
	    cpt = processStatement(
		graph, typeSpace, varSpace, frame,
		cpt, syncStmt.getBody());

	} else if (stmt instanceof TryStatement) {
	    // XXX Ignore catch statements (for now).
	    TryStatement tryStmt = (TryStatement)stmt;
	    DFFrame tryFrame = frame.getChildByAST(tryStmt);
	    cpt = processStatement(
		graph, typeSpace, varSpace, tryFrame,
		cpt, tryStmt.getBody());
	    Block finBlock = tryStmt.getFinally();
	    if (finBlock != null) {
		cpt = processStatement(
		    graph, typeSpace, varSpace, frame, cpt, finBlock);
	    }

	} else if (stmt instanceof ThrowStatement) {
	    ThrowStatement throwStmt = (ThrowStatement)stmt;
	    cpt = processExpression(
		graph, typeSpace, varSpace, frame,
		cpt, throwStmt.getExpression());
            ExceptionNode exception = new ExceptionNode(
		graph, varSpace, stmt, cpt.getRValue());
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
		    graph, typeSpace, varSpace, frame, cpt, arg);
	    }

	} else if (stmt instanceof SuperConstructorInvocation) {
	    // XXX Ignore all side effects.
	    SuperConstructorInvocation sci = (SuperConstructorInvocation)stmt;
	    for (Expression arg : (List<Expression>) sci.arguments()) {
		cpt = processExpression(
		    graph, typeSpace, varSpace, frame, cpt, arg);
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
    public DFTypeSpace rootSpace;

    public Java2DF(
	Exporter exporter, String[] classPath, String[] srcPath) {
	this.exporter = exporter;
	this.classPath = classPath;
	this.srcPath = srcPath;
        this.rootSpace = new DFTypeSpace();
    }

    /**
     * Performs dataflow analysis for a given method.
     */
    @SuppressWarnings("unchecked")
    public DFGraph processMethodDeclaration(
        DFTypeSpace typeSpace, DFClassSpace klass,
        MethodDeclaration methodDecl)
        throws UnsupportedSyntax {
	// Ignore method prototypes.
	if (methodDecl.getBody() == null) return null;
        DFMethod method = klass.lookupMethod(methodDecl);
        assert(method != null);
        try {
            // Setup an initial space.
            DFFrame frame = new DFFrame(DFFrame.METHOD);
            DFVarSpace varSpace = new DFVarSpace(klass, methodDecl.getName());
            varSpace.build(typeSpace, frame, methodDecl);
            //varSpace.dump();
            //frame.dump();

            DFGraph graph = new DFGraph(varSpace, method);
            DFComponent cpt = new DFComponent(graph, varSpace);
            // XXX Ignore isContructor().
            // XXX Ignore isVarargs().
            int i = 0;
            for (SingleVariableDeclaration decl :
                     (List<SingleVariableDeclaration>) methodDecl.parameters()) {
                // XXX Ignore modifiers and dimensions.
                DFVarRef ref = varSpace.lookupVar(decl.getName());
                assert(ref != null);
                DFNode param = new ArgNode(graph, varSpace, ref, decl, i++);
                DFNode assign = new SingleAssignNode(graph, varSpace, ref, decl);
                assign.accept(param);
                cpt.setOutput(assign);
            }

            // Process the function body.
            cpt = processStatement(
                graph, typeSpace, varSpace, frame, cpt, methodDecl.getBody());
            cpt.endFrame(frame);
            // Remove redundant nodes.
            graph.cleanup();

            Utils.logit("Success: "+method.getName());
            return graph;
        } catch (UnsupportedSyntax e) {
            //e.printStackTrace();
            e.name = method.getName();
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    public void processFieldDeclaration(
        DFGraph graph, DFTypeSpace typeSpace, DFClassSpace klass,
        DFFrame frame, FieldDeclaration fieldDecl)
        throws UnsupportedSyntax {
	for (VariableDeclarationFragment frag :
		 (List<VariableDeclarationFragment>) fieldDecl.fragments()) {
	    DFVarRef ref = klass.lookupField(frag.getName());
	    Expression init = frag.getInitializer();
	    if (init != null) {
		DFComponent cpt = new DFComponent(graph, klass);
		cpt = processExpression(
                    graph, typeSpace, klass, frame, cpt, init);
		DFNode assign = new SingleAssignNode(graph, klass, ref, frag);
		assign.accept(cpt.getRValue());
	    }
	}
    }

    @SuppressWarnings("unchecked")
    public void processTypeDeclaration(
        DFTypeSpace typeSpace, TypeDeclaration typeDecl)
        throws IOException {
        DFClassSpace klass = typeSpace.lookupClass(typeDecl.getName());
        assert(klass != null);
        DFTypeSpace child = typeSpace.lookupSpace(typeDecl.getName());
	DFGraph classGraph = new DFGraph(klass);
	DFFrame frame = new DFFrame(DFFrame.CLASS);
        for (BodyDeclaration body :
                 (List<BodyDeclaration>) typeDecl.bodyDeclarations()) {
	    try {
		if (body instanceof TypeDeclaration) {
		    processTypeDeclaration(
			child, (TypeDeclaration)body);
		} else if (body instanceof FieldDeclaration) {
		    processFieldDeclaration(
			classGraph, typeSpace, klass,
			frame, (FieldDeclaration)body);
		} else if (body instanceof MethodDeclaration) {
                    DFGraph graph = processMethodDeclaration(
                        typeSpace, klass, (MethodDeclaration)body);
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
	if (this.exporter != null) {
	    this.exporter.writeGraph(classGraph);
	}
	//klass.dump();
    }

    public CompilationUnit parseFile(String path)
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

    // pass1
    public void buildTypeSpace(CompilationUnit cunit) {
        DFTypeSpace typeSpace = this.rootSpace.lookupSpace(cunit.getPackage());
	try {
            typeSpace.build(cunit);
	} catch (UnsupportedSyntax e) {
	    String astName = e.ast.getClass().getName();
	    Utils.logit("Fail: "+e.name+" (Unsupported: "+astName+") "+e.ast);
	}
    }

    // pass2
    @SuppressWarnings("unchecked")
    public void buildClassSpace(CompilationUnit cunit) {
        DFTypeSpace typeSpace = this.rootSpace.lookupSpace(cunit.getPackage());
        typeSpace = typeSpace.extend(cunit.imports());
	try {
            for (TypeDeclaration typeDecl :
                     (List<TypeDeclaration>) cunit.types()) {
                DFClassSpace klass = typeSpace.lookupClass(typeDecl.getName());
                assert(klass != null);
                klass.build(typeDecl);
            }
	} catch (UnsupportedSyntax e) {
	    String astName = e.ast.getClass().getName();
	    Utils.logit("Fail: "+e.name+" (Unsupported: "+astName+") "+e.ast);
	}
    }

    // pass3
    @SuppressWarnings("unchecked")
    public void buildGraphs(CompilationUnit cunit)
        throws IOException {
        DFTypeSpace typeSpace = this.rootSpace.lookupSpace(cunit.getPackage());
        typeSpace = typeSpace.extend(cunit.imports());
	for (TypeDeclaration typeDecl :
                 (List<TypeDeclaration>) cunit.types()) {
	    processTypeDeclaration(typeSpace, typeDecl);
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
                CompilationUnit cunit = converter.parseFile(path);
		converter.buildTypeSpace(cunit);
	    } catch (IOException e) {
		System.err.println("Cannot open input file: "+path);
	    }
	}
	for (String path : files) {
	    Utils.logit("Pass2: "+path);
	    try {
                CompilationUnit cunit = converter.parseFile(path);
		converter.buildClassSpace(cunit);
	    } catch (IOException e) {
		System.err.println("Cannot open input file: "+path);
	    }
	}
	for (String path : files) {
	    Utils.logit("Pass3: "+path);
	    try {
                CompilationUnit cunit = converter.parseFile(path);
		exporter.startFile(path);
		converter.buildGraphs(cunit);
		exporter.endFile();
	    } catch (IOException e) {
		System.err.println("Cannot open input file: "+path);
	    }
	}
	//converter.rootSpace.dump();
	exporter.close();

	Utils.printXml(output, exporter.document);
	output.close();
    }
}
