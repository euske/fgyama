//  Java2DF.java
//
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.dom.*;


//  UnsupportedSyntax
//
class UnsupportedSyntax extends Exception {

    static final long serialVersionUID = 1L;

    public ASTNode node;
    
    public UnsupportedSyntax(ASTNode node) {
	this.node = node;
    }
}


//  DFGraph
//
class DFGraph {

    public String name;
    public List<DFNode> nodes;

    public DFGraph(String name) {
	this.name = name;
	this.nodes = new ArrayList<DFNode>();
    }

    public int addNode(DFNode node) {
	this.nodes.add(node);
	return this.nodes.size();
    }

    public void removeNode(DFNode node) {
        this.nodes.remove(node);
    }
}


//  DFNodeType
//
enum DFNodeType {
    Normal,
    Box,
    Cond,
    Loop,
}


//  DFNode
//
abstract class DFNode {

    public DFGraph graph;
    public int id;
    public List<DFLink> send;
    public List<DFLink> recv;
    
    public DFNode(DFGraph graph) {
	this.graph = graph;
	this.id = this.graph.addNode(this);
	this.send = new ArrayList<DFLink>();
	this.recv = new ArrayList<DFLink>();
    }

    public String toString() {
	return ("<DFNode("+this.id+") "+this.label()+">");
    }

    public DFNodeType type() {
	return DFNodeType.Normal;
    }

    abstract public String label();

    public DFLink connect(DFNode dst) {
	return this.connect(dst, DFLinkType.DataFlow);
    }
    
    public DFLink connect(DFNode dst, DFLinkType type) {
	return this.connect(dst, type, null);
    }
    
    public DFLink connect(DFNode dst, String label) {
	return this.connect(dst, DFLinkType.DataFlow, label);
    }
    
    public DFLink connect(DFNode dst, DFLinkType type, String label) {
	DFLink link = new DFLink(this, dst, type, label);
	this.send.add(link);
	dst.recv.add(link);
	return link;
    }

    public void remove() {
        List<DFLink> removed = new ArrayList<DFLink>();
        for (DFLink link : this.send) {
            if (link.src == this) {
                removed.add(link);
            }
        }
        for (DFLink link : this.recv) {
            if (link.dst == this) {
                removed.add(link);
            }
        }
        for (DFLink link : removed) {
            link.disconnect();
        }
        this.graph.removeNode(this);
    }
}


//  DFLinkType
//
enum DFLinkType {
    DataFlow,
    ControlFlow,
}


//  DFLink
//
class DFLink {
    
    public DFNode src;
    public DFNode dst;
    public DFLinkType type;
    public String name;
    
    public DFLink(DFNode src, DFNode dst, DFLinkType type, String name)
    {
	this.src = src;
	this.dst = dst;
	this.type = type;
	this.name = name;
    }

    public String toString() {
	return ("<DFLink: "+this.src+"-("+this.name+")-"+this.dst+">");
    }

    public void disconnect()
    {
	this.src.send.remove(this);
	this.dst.recv.remove(this);
    }
}


//  DFRef
//
class DFRef {

    public DFScope scope;
    public String name;
    
    public DFRef(DFScope scope, String name) {
	this.scope = scope;
	this.name = name;
    }

    public String toString() {
	return ("<DFRef: "+this.name+">");
    }

    public static DFRef THIS = new DFRef(null, "THIS");
    public static DFRef RETURN = new DFRef(null, "RETURN");
}


//  DFVar
//
class DFVar extends DFRef {

    public Type type;

    public DFVar(DFScope scope, String name, Type type) {
	super(scope, name);
	this.type = type;
    }

    public String toString() {
	return ("<DFVar: "+this.name+"("+this.type+")>");
    }
}


//  DFScope
//
class DFScope {

    public DFScope root;
    public DFScope parent;
    public Map<String, DFVar> vars;

    public DFScope() {
	this(null);
    }
    
    public DFScope(DFScope parent) {
	this.root = (parent != null)? parent.root : this;
	this.parent = parent;
	this.vars = new HashMap<String, DFVar>();
    }

    public String toString() {
	StringBuilder vars = new StringBuilder();
	for (DFVar var : this.vars.values()) {
	    vars.append(" "+var);
	}
	return ("<DFScope:"+vars+">");
    }

    public DFVar add(String name, Type type) {
	DFVar var = new DFVar(this, name, type);
	this.vars.put(name, var);
	return var;
    }

    public DFVar lookup(String name) {
	DFVar var = this.vars.get(name);
	if (var != null) {
	    return var;
	} else if (this.parent != null) {
	    return this.parent.lookup(name);
	} else {
	    return this.add(name, null);
	}
    }

    public DFVar lookupArray() {
	return this.root.lookup("[]");
    }
    
    public DFVar lookupField(String name) {
	return this.lookup("."+name);
    }
    
    public Collection<DFVar> vars() {
	return this.vars.values();
    }
}


//  DFComponent
//
class DFComponent {

    public DFGraph graph;
    public Map<DFRef, DFNode> inputs;
    public Map<DFRef, DFNode> outputs;
    public DFNode value;
    public BoxNode assign;
    
    public DFComponent(DFGraph graph) {
	this.graph = graph;
	this.inputs = new HashMap<DFRef, DFNode>();
	this.outputs = new HashMap<DFRef, DFNode>();
	this.value = null;
	this.assign = null;
    }

    public String toString() {
	StringBuilder inputs = new StringBuilder();
	for (Map.Entry<DFRef, DFNode> entry : this.inputs.entrySet()) {
	    inputs.append(" "+entry.getKey()+":"+entry.getValue());
	}
	StringBuilder outputs = new StringBuilder();
	for (Map.Entry<DFRef, DFNode> entry : this.outputs.entrySet()) {
	    outputs.append(" "+entry.getKey()+":"+entry.getValue());
	}
	return ("<DFComponent: inputs="+inputs+", outputs="+
		outputs+", value="+this.value+">");
    }

    public DFNode get(DFRef ref) {
	DFNode node = this.outputs.get(ref);
	if (node == null) {
	    node = this.inputs.get(ref);
	    if (node == null) {
		node = new SingleAssignNode(this.graph, null, ref);
		this.inputs.put(ref, node);
	    }
	}
	return node;
    }

    public void put(DFRef ref, DFNode node) {
	this.outputs.put(ref, node);
    }

    public void finish(DFScope scope) {
	for (DFRef ref : scope.vars()) {
	    this.inputs.remove(ref);
	    this.outputs.remove(ref);
	}
    }
    
    public void connect(DFComponent next) {
	for (DFRef ref : this.outputs.keySet()) {
	    DFNode node = this.inputs.get(ref);
	    node.connect(next.inputs.get(ref));
	}
	for (DFRef ref : this.inputs.keySet()) {
	    if (!this.outputs.containsKey(ref)) {
		DFNode node = this.inputs.get(ref);
		node.connect(next.inputs.get(ref));
	    }
	}
    }
}


// DistNode: a DFNode that distributes a value to multiple nodes.
class DistNode extends DFNode {

    public DistNode(DFGraph graph) {
	super(graph);
    }

    public String label() {
	return null;
    }
}

// ProgNode: a DFNode that corresponds to an actual program point.
abstract class ProgNode extends DFNode {

    public ASTNode node;
    
    public ProgNode(DFGraph graph, ASTNode node) {
	super(graph);
	this.node = node;
    }
}

// ArgNode: represnets a function argument.
class ArgNode extends ProgNode {

    public int index;

    public ArgNode(DFGraph graph, ASTNode node, int index) {
	super(graph, node);
	this.index = index;
    }

    public String label() {
	return "Arg "+this.index;
    }
}

// BoxNode: corresponds to a certain location in a memory.
abstract class BoxNode extends ProgNode {

    public DFRef ref;
    
    public BoxNode(DFGraph graph, ASTNode node, DFRef ref) {
	super(graph, node);
	this.ref = ref;
    }

    public DFNodeType type() {
	return DFNodeType.Box;
    }

    public String label() {
	return this.ref.name;
    }

    abstract public void take(DFNode value);
}

// SingleAssignNode:
class SingleAssignNode extends BoxNode {

    public DFNode value;
    
    public SingleAssignNode(DFGraph graph, ASTNode node, DFRef ref) {
	super(graph, node, ref);
    }

    public void take(DFNode value) {
	this.value = value;
	value.connect(this, "assign");
    }
}

// ArrayAssignNode:
class ArrayAssignNode extends SingleAssignNode {

    public DFNode index;

    public ArrayAssignNode(DFGraph graph, ASTNode node, DFRef ref,
			   DFNode array, DFNode index) {
	super(graph, node, ref);
	this.index = index;
	array.connect(this, "access");
	index.connect(this, "index");
    }
}

// FieldAssignNode:
class FieldAssignNode extends SingleAssignNode {

    public DFNode obj;

    public FieldAssignNode(DFGraph graph, ASTNode node, DFRef ref,
			   DFNode obj) {
	super(graph, node, ref);
	this.obj = obj;
	obj.connect(this, "index");
    }
}


// ReturnNode: represents a return value.
class ReturnNode extends ProgNode {

    public DFNode value;
    
    public ReturnNode(DFGraph graph, ASTNode node, DFNode value) {
	super(graph, node);
	this.value = value;
	value.connect(this, "return");
    }

    public DFNodeType type() {
	return DFNodeType.Box;
    }

    public String label() {
	return "Return";
    }
}

// ConstNode: represents a constant value.
class ConstNode extends ProgNode {

    public String value;

    public ConstNode(DFGraph graph, ASTNode node, String value) {
	super(graph, node);
	this.value = value;
    }

    public String label() {
	return this.value;
    }
}

// ArrayValueNode: represents an array.
class ArrayValueNode extends ProgNode {

    public List<DFNode> values;

    public ArrayValueNode(DFGraph graph, ASTNode node) {
	super(graph, node);
	this.values = new ArrayList<DFNode>();
    }

    public String label() {
	return "["+this.values.size()+"]";
    }
    
    public void take(DFNode value) {
	int i = this.values.size();
	value.connect(this, "value"+i);
	this.values.add(value);
    }
}

// PrefixNode
class PrefixNode extends ProgNode {

    public PrefixExpression.Operator op;
    public DFNode value;

    public PrefixNode(DFGraph graph, ASTNode node,
		      PrefixExpression.Operator op, DFNode value) {
	super(graph, node);
	this.op = op;
	this.value = value;
	value.connect(this, "pre");
    }

    public String label() {
	return this.op.toString();
    }
}

// PostfixNode
class PostfixNode extends ProgNode {

    public PostfixExpression.Operator op;
    public DFNode value;

    public PostfixNode(DFGraph graph, ASTNode node,
		       PostfixExpression.Operator op, DFNode value) {
	super(graph, node);
	this.op = op;
	this.value = value;
	value.connect(this, "post");
    }

    public String label() {
	return this.op.toString();
    }
}

// InfixNode
class InfixNode extends ProgNode {

    public InfixExpression.Operator op;
    public DFNode lvalue;
    public DFNode rvalue;

    public InfixNode(DFGraph graph, ASTNode node,
		     InfixExpression.Operator op,
		     DFNode lvalue, DFNode rvalue) {
	super(graph, node);
	this.op = op;
	this.lvalue = lvalue;
	this.rvalue = rvalue;
	lvalue.connect(this, "L");
	rvalue.connect(this, "R");
    }

    public String label() {
	return this.op.toString();
    }
}

// ArrayAccessNode
class ArrayAccessNode extends ProgNode {

    public DFNode value;
    public DFNode index;

    public ArrayAccessNode(DFGraph graph, ASTNode node,
			   DFNode array, DFNode value, DFNode index) {
	super(graph, node);
	this.value = value;
	this.index = index;
	array.connect(this, "array");
	value.connect(this, "access");
	index.connect(this, "index");
    }

    public String label() {
	return "[]";
    }
}

// FieldAccessNode
class FieldAccessNode extends ProgNode {

    public DFNode value;
    public DFNode obj;

    public FieldAccessNode(DFGraph graph, ASTNode node,
			   DFNode value, DFNode obj) {
	super(graph, node);
	this.value = value;
	this.obj = obj;
	value.connect(this, "access");
	obj.connect(this, "index");
    }

    public String label() {
	return ".";
    }
}


// AssignmentNode
class AssignmentNode extends ProgNode {

    public Assignment.Operator op;
    public DFNode lvalue;
    public DFNode rvalue;

    public AssignmentNode(DFGraph graph, ASTNode node,
			  Assignment.Operator op,
			  DFNode lvalue, DFNode rvalue) {
	super(graph, node);
	this.op = op;
	this.lvalue = lvalue;
	this.rvalue = rvalue;
	lvalue.connect(this, "L");
	rvalue.connect(this, "R");
    }

    public String label() {
	return this.op.toString();
    }
}

// CondNode
abstract class CondNode extends ProgNode {
    
    public DFNode value;
    
    public CondNode(DFGraph graph, ASTNode node, DFNode value) {
	super(graph, node);
	this.value = value;
	value.connect(this, DFLinkType.ControlFlow);
    }

    public DFNodeType type() {
	return DFNodeType.Cond;
    }
}

// BranchNode
class BranchNode extends CondNode {

    public BranchNode(DFGraph graph, ASTNode node, DFNode value) {
	super(graph, node, value);
    }

    public String label() {
	return "Branch";
    }

    public void send(boolean cond, DFNode node) {
	if (cond) {
	    this.connect(node, "true");
	} else {
	    this.connect(node, "false");
	}
    }
}

// JoinNode
class JoinNode extends CondNode {

    public DFRef ref;
    public boolean recvTrue = false;
    public boolean recvFalse = false;
    
    public JoinNode(DFGraph graph, ASTNode node, DFNode value, DFRef ref) {
	super(graph, node, value);
	this.ref = ref;
    }
    
    public String label() {
	return "Join:"+this.ref.name;
    }
    
    public void recv(boolean cond, DFNode node) {
	if (cond) {
	    this.recvTrue = true;
	    node.connect(this, "true");
	} else {
	    this.recvFalse = true;
	    node.connect(this, "false");
	}
    }

    public boolean isClosed() {
	return (this.recvTrue && this.recvFalse);
    };

    public void close(DFNode node) {
	if (!this.recvTrue) {
	    node.connect(this, "true");
	}
	if (!this.recvFalse) {
	    node.connect(this, "false");
	}
    }
}

// LoopNode
class LoopNode extends ProgNode {

    public DFRef ref;
    public DFNode init;
    
    public LoopNode(DFGraph graph, ASTNode node, DFRef ref, DFNode init) {
	super(graph, node);
	this.ref = ref;
	this.init = init;
	init.connect(this, "init");
    }
    
    public DFNodeType type() {
	return DFNodeType.Loop;
    }
    
    public String label() {
	return "Loop:"+this.ref.name;
    }

    public void enter(DFNode cont) {
	cont.connect(this, "cont");
    }
}

// CallNode
class CallNode extends ProgNode {

    public String name;
    public List<DFNode> args;

    public CallNode(DFGraph graph, ASTNode node, String name) {
	super(graph, node);
	this.name = name;
	this.args = new ArrayList<DFNode>();
    }

    public String label() {
	return this.name+"()";
    }

    public void take(DFNode arg) {
	int i = this.args.size();
	arg.connect(this, "arg"+i);
	this.args.add(arg);
    }
}



//  GraphvizExporter
//
class GraphvizExporter {

    public BufferedWriter writer;
    
    public GraphvizExporter(OutputStream stream) {
	this.writer = new BufferedWriter(new OutputStreamWriter(stream));
    }

    public void writeGraph(DFGraph graph) {
	try {
	    this.writer.write("digraph "+graph.name+" {\n");
	    for (DFNode node : graph.nodes) {
		this.writer.write(" N"+node.id);
                this.writer.write(" [label="+quote(node.label()));
		switch (node.type()) {
		case Box:
		    this.writer.write(", shape=box");
		    break;
		case Cond:
		    this.writer.write(", shape=diamond");
		    break;
		}
		this.writer.write("];\n");
	    }
	    for (DFNode node : graph.nodes) {
		for (DFLink link : node.send) {
		    this.writer.write(" N"+link.src.id+" -> N"+link.dst.id);
                    this.writer.write(" [label="+quote(link.name));
		    switch (link.type) {
		    case ControlFlow:
			this.writer.write(", style=dotted");
			break;
		    }
		    this.writer.write("];\n");
		}
	    }
	    this.writer.write("}\n");
	    this.writer.flush();
	} catch (IOException e) {
	}
    }

    public static String quote(String s) {
        if (s == null) {
            return "\"\"";
        } else {
            return "\"" + s.replace("\"", "\\\"") + "\"";
        }
    }
}


//  Utility functions.
// 
class Utils {

    public static void logit(String s) {
	System.err.println(s);
    }

    public static String getTypeName(Type type) {
	if (type instanceof PrimitiveType) {
	    return ((PrimitiveType)type).getPrimitiveTypeCode().toString();
	} else if (type instanceof SimpleType) {
	    return ((SimpleType)type).getName().getFullyQualifiedName();
	} else if (type instanceof ArrayType) {
	    String name = getTypeName(((ArrayType)type).getElementType());
	    int ndims = ((ArrayType)type).getDimensions();
	    for (int i = 0; i < ndims; i++) {
		name += "[]";
	    }
	    return name;
	} else {
	    return null;
	}
    }
}


//  Java2DF
// 
public class Java2DF extends ASTVisitor {

    // Instance methods.
    
    public GraphvizExporter exporter;

    public Java2DF(GraphvizExporter exporter) {
	this.exporter = exporter;
    }

    public boolean visit(MethodDeclaration method) {
	try {
	    DFGraph graph = getMethodGraph(method);
	    exporter.writeGraph(graph);
	} catch (UnsupportedSyntax e) {
	    Utils.logit("Unsupported: "+e.node);
	}
	return true;
    }

    public DFComponent processVariableDeclaration
	(DFGraph graph, DFScope scope, DFComponent compo, Type varType,
	 List<VariableDeclarationFragment> frags)
	throws UnsupportedSyntax {
	for (VariableDeclarationFragment frag : frags) {
	    Expression init = frag.getInitializer();
	    if (init != null) {
		SimpleName varName = frag.getName();
		DFRef var = scope.add(varName.getIdentifier(), varType);
		compo = processExpression(graph, scope, compo, init);
		BoxNode box = new SingleAssignNode(graph, frag, var);
		box.take(compo.value);
		compo.put(var, box);
	    }
	}
	return compo;
    }

    @SuppressWarnings("unchecked")
    public DFComponent processExpressionLeft
	(DFGraph graph, DFScope scope, DFComponent compo, Expression expr)
	throws UnsupportedSyntax {
	if (expr instanceof SimpleName) {
	    // Single value assignment.
	    SimpleName varName = (SimpleName)expr;
	    DFRef ref = scope.lookup(varName.getIdentifier());
	    compo.assign = new SingleAssignNode(graph, expr, ref);
	    
	} else if (expr instanceof ArrayAccess) {
	    // Array assignment.
	    ArrayAccess aa = (ArrayAccess)expr;
	    compo = processExpression(graph, scope, compo, aa.getArray());
	    DFNode array = compo.value;
	    compo = processExpression(graph, scope, compo, aa.getIndex());
	    DFNode index = compo.value;
	    DFRef ref = scope.lookupArray();
	    compo.assign = new ArrayAssignNode(graph, expr, ref, array, index);
	    
	} else if (expr instanceof FieldAccess) {
	    // Field assignment.
	    FieldAccess fa = (FieldAccess)expr;
	    SimpleName fieldName = fa.getName();
	    compo = processExpression(graph, scope, compo, fa.getExpression());
	    DFNode obj = compo.value;
	    DFRef ref = scope.lookupField(fieldName.getIdentifier());
	    compo.assign = new FieldAssignNode(graph, expr, ref, obj);
	    
	} else if (expr instanceof QualifiedName) {
	    // Qualified name = Field assignment.
	    QualifiedName qn = (QualifiedName)expr;
	    SimpleName fieldName = qn.getName();
	    DFRef ref = scope.lookupField(fieldName.getIdentifier());
	    compo = processExpression(graph, scope, compo, qn.getQualifier());
	    DFNode obj = compo.value;
	    compo.assign = new FieldAssignNode(graph, expr, ref, obj);
	    
	} else {
	    throw new UnsupportedSyntax(expr);
	}
	return compo;
    }
    
    @SuppressWarnings("unchecked")
    public DFComponent processExpression
	(DFGraph graph, DFScope scope, DFComponent compo, Expression expr)
	throws UnsupportedSyntax {

	if (expr instanceof Annotation) {
	    // Ignore annotations.

	} else if (expr instanceof SimpleName) {
	    // Variable lookup.
	    SimpleName varName = (SimpleName)expr;
	    DFRef ref = scope.lookup(varName.getIdentifier());
	    compo.value = compo.get(ref);
	    
	} else if (expr instanceof ThisExpression) {
	    // "this".
	    DFRef ref = DFRef.THIS;
	    compo.value = compo.get(ref);
	    
	} else if (expr instanceof BooleanLiteral) {
	    // Boolean cosntant.
	    boolean value = ((BooleanLiteral)expr).booleanValue();
	    compo.value = new ConstNode(graph, expr, Boolean.toString(value));
	    
	} else if (expr instanceof CharacterLiteral) {
	    // Char cosntant.
	    char value = ((CharacterLiteral)expr).charValue();
	    compo.value = new ConstNode(graph, expr, Character.toString(value));
	    
	} else if (expr instanceof NullLiteral) {
	    // Null cosntant.
	    compo.value = new ConstNode(graph, expr, "null");
	    
	} else if (expr instanceof NumberLiteral) {
	    // Number cosntant.
	    String value = ((NumberLiteral)expr).getToken();
	    compo.value = new ConstNode(graph, expr, value);
	    
	} else if (expr instanceof StringLiteral) {
	    // String cosntant.
	    String value = ((StringLiteral)expr).getLiteralValue();
	    compo.value = new ConstNode(graph, expr, value);
	    
	} else if (expr instanceof TypeLiteral) {
	    // Type name cosntant.
	    Type value = ((TypeLiteral)expr).getType();
	    compo.value = new ConstNode(graph, expr, Utils.getTypeName(value));
	    
	} else if (expr instanceof PrefixExpression) {
	    // Prefix operator.
	    PrefixExpression prefix = (PrefixExpression)expr;
	    PrefixExpression.Operator op = prefix.getOperator();
	    Expression operand = prefix.getOperand();
	    compo = processExpressionLeft(graph, scope, compo, operand);
	    BoxNode assign = compo.assign;
	    compo = processExpression(graph, scope, compo, operand);
	    DFNode value = new PrefixNode(graph, expr, op, compo.value);
	    if (op == PrefixExpression.Operator.INCREMENT ||
		op == PrefixExpression.Operator.DECREMENT) {
		assign.take(value);
		compo.put(assign.ref, assign);
	    }
	    compo.value = value;
	    
	} else if (expr instanceof PostfixExpression) {
	    // Postfix operator.
	    PostfixExpression postfix = (PostfixExpression)expr;
	    PostfixExpression.Operator op = postfix.getOperator();
	    Expression operand = postfix.getOperand();
	    compo = processExpressionLeft(graph, scope, compo, operand);
	    BoxNode assign = compo.assign;
	    compo = processExpression(graph, scope, compo, operand);
	    if (op == PostfixExpression.Operator.INCREMENT ||
		op == PostfixExpression.Operator.DECREMENT) {
		assign.take(new PostfixNode(graph, expr, op, compo.value));
		compo.put(assign.ref, assign);
	    }
	    
	} else if (expr instanceof InfixExpression) {
	    // Infix operator.
	    InfixExpression infix = (InfixExpression)expr;
	    InfixExpression.Operator op = infix.getOperator();
	    compo = processExpression(graph, scope, compo, infix.getLeftOperand());
	    DFNode lvalue = compo.value;
	    compo = processExpression(graph, scope, compo, infix.getRightOperand());
	    DFNode rvalue = compo.value;
	    compo.value = new InfixNode(graph, expr, op, lvalue, rvalue);
	    
	} else if (expr instanceof ParenthesizedExpression) {
	    // Parentheses.
	    ParenthesizedExpression paren = (ParenthesizedExpression)expr;
	    compo = processExpression(graph, scope, compo, paren.getExpression());
	    
	} else if (expr instanceof Assignment) {
	    // Assignment.
	    Assignment assn = (Assignment)expr;
	    Assignment.Operator op = assn.getOperator();
	    compo = processExpressionLeft(graph, scope, compo, assn.getLeftHandSide());
	    BoxNode assign = compo.assign;
	    compo = processExpression(graph, scope, compo, assn.getRightHandSide());
	    DFNode rvalue = compo.value;
	    if (op != Assignment.Operator.ASSIGN) {
		DFNode lvalue = compo.get(assign.ref);
		rvalue = new AssignmentNode(graph, assn, op, lvalue, rvalue);
	    }
	    assign.take(rvalue);
	    compo.put(assign.ref, assign);
	    compo.value = assign;

	} else if (expr instanceof VariableDeclarationExpression) {
	    // Variable declaration.
	    VariableDeclarationExpression decl = (VariableDeclarationExpression)expr;
	    Type varType = decl.getType();
	    compo = processVariableDeclaration
		(graph, scope, compo, varType, decl.fragments());

	} else if (expr instanceof MethodInvocation) {
	    // Function call.
	    MethodInvocation invoke = (MethodInvocation)expr;
	    SimpleName methodName = invoke.getName();
	    CallNode call = new CallNode(graph, invoke, methodName.getIdentifier());
	    for (Expression arg : (List<Expression>) invoke.arguments()) {
		compo = processExpression(graph, scope, compo, arg);
		call.take(compo.value);
	    }
	    compo.value = call;
	    
	} else if (expr instanceof ArrayCreation) {
	    // new array[];
	    ArrayCreation ac = (ArrayCreation)expr;
	    for (Expression dim : (List<Expression>) ac.dimensions()) {
		compo = processExpression(graph, scope, compo, dim);
		// XXX compo.value is not used (for now).
		if (compo.value != null) {
		    graph.removeNode(compo.value);
		}
	    }
	    ArrayInitializer init = ac.getInitializer();
	    if (init != null) {
		compo = processExpression(graph, scope, compo, init);
	    } else {
		compo.value = new ArrayValueNode(graph, ac);
	    }
	    
	} else if (expr instanceof ArrayInitializer) {
	    // array constants.
	    ArrayInitializer init = (ArrayInitializer)expr;
	    ArrayValueNode arr = new ArrayValueNode(graph, init);
	    for (Expression expr1 : (List<Expression>) init.expressions()) {
		compo = processExpression(graph, scope, compo, expr1);
		arr.take(compo.value);
	    }
	    compo.value = arr;
	    
	} else if (expr instanceof ArrayAccess) {
	    // array access.
	    ArrayAccess aa = (ArrayAccess)expr;
	    DFRef ref = scope.lookupArray();
	    compo = processExpression(graph, scope, compo, aa.getArray());
	    DFNode array = compo.value;
	    compo = processExpression(graph, scope, compo, aa.getIndex());
	    DFNode index = compo.value;
	    compo.value = new ArrayAccessNode(graph, aa, compo.get(ref), array, index);
	    
	} else if (expr instanceof FieldAccess) {
	    // field access.
	    FieldAccess fa = (FieldAccess)expr;
	    SimpleName fieldName = fa.getName();
	    DFRef ref = scope.lookupField(fieldName.getIdentifier());
	    compo = processExpression(graph, scope, compo, fa.getExpression());
	    DFNode obj = compo.value;
	    compo.value = new FieldAccessNode(graph, fa, compo.get(ref), obj);
	    
	} else if (expr instanceof QualifiedName) {
	    // Qualified name = Field assignment.
	    QualifiedName qn = (QualifiedName)expr;
	    SimpleName fieldName = qn.getName();
	    DFRef ref = scope.lookupField(fieldName.getIdentifier());
	    compo = processExpression(graph, scope, compo, qn.getQualifier());
	    DFNode obj = compo.value;
	    compo.value = new FieldAccessNode(graph, qn, compo.get(ref), obj);
	    
	} else {
	    // CastExpression
	    // ClassInstanceCreation
	    // ConditionalExpression
	    // InstanceofExpression
	    
	    // LambdaExpression
	    // SuperFieldAccess
	    // SuperMethodInvocation
	    // MethodReference
	    //  CreationReference
	    //  ExpressionMethodReference
	    //  SuperMethodReference
	    //  TypeMethodReference
	    
	    throw new UnsupportedSyntax(expr);
	}
	
	return compo;
    }
    
    public DFComponent processConditional
	(DFGraph graph, DFScope scope, DFComponent compo, Statement stmt,
	 DFNode condValue, DFComponent trueCompo, DFComponent falseCompo) {
	
	Map<DFRef, BranchNode> branches = new HashMap<DFRef, BranchNode>();
	Map<DFRef, JoinNode> joins = new HashMap<DFRef, JoinNode>();
	
	for (Map.Entry<DFRef, DFNode> entry : trueCompo.inputs.entrySet()) {
	    DFRef ref = entry.getKey();
	    DFNode src = entry.getValue();
	    BranchNode branch = branches.get(ref);
	    if (branch == null) {
		branch = new BranchNode(graph, stmt, condValue);
		branches.put(ref, branch);
		compo.get(ref).connect(branch);
	    }
	    branch.send(true, src);
	}
	if (falseCompo != null) {
	    for (Map.Entry<DFRef, DFNode> entry : falseCompo.inputs.entrySet()) {
		DFRef ref = entry.getKey();
		DFNode src = entry.getValue();
		BranchNode branch = branches.get(ref);
		if (branch == null) {
		    branch = new BranchNode(graph, stmt, condValue);
		    branches.put(ref, branch);
		    compo.get(ref).connect(branch);
		}
		branch.send(false, src);
	    }
	}
	
	for (Map.Entry<DFRef, DFNode> entry : trueCompo.outputs.entrySet()) {
	    DFRef ref = entry.getKey();
	    DFNode dst = entry.getValue();
	    JoinNode join = joins.get(ref);
	    if (join == null) {
		join = new JoinNode(graph, stmt, condValue, ref);
		joins.put(ref, join);
	    }
	    join.recv(true, dst);
	}
	if (falseCompo != null) {
	    for (Map.Entry<DFRef, DFNode> entry : falseCompo.outputs.entrySet()) {
		DFRef ref = entry.getKey();
		DFNode dst = entry.getValue();
		JoinNode join = joins.get(ref);
		if (join == null) {
		    join = new JoinNode(graph, stmt, condValue, ref);
		    joins.put(ref, join);
		}
		join.recv(false, dst);
	    }
	}
	for (Map.Entry<DFRef, JoinNode> entry : joins.entrySet()) {
	    DFRef ref = entry.getKey();
	    JoinNode join = entry.getValue();
	    if (!join.isClosed()) {
		join.close(compo.get(ref));
	    }
	    compo.put(ref, join);
	}
	return compo;
    }

    public DFComponent processLoop
	(DFGraph graph, DFScope scope, DFComponent compo, Statement stmt, 
	 DFComponent exprCompo, DFComponent bodyCompo)
	throws UnsupportedSyntax {
	
	DFNode condValue = exprCompo.value;
	Map<DFRef, LoopNode> loops = new HashMap<DFRef, LoopNode>();
	Map<DFRef, DFNode> temps = new HashMap<DFRef, DFNode>();
	
	for (Map.Entry<DFRef, DFNode> entry : exprCompo.inputs.entrySet()) {
	    DFRef ref = entry.getKey();
	    DFNode dst = entry.getValue();
	    LoopNode loop = loops.get(ref);
	    if (loop == null) {
		DFNode src = compo.get(ref);
		loop = new LoopNode(graph, stmt, ref, src);
		loops.put(ref, loop);
	    }
	    loop.connect(dst);
	}
	for (Map.Entry<DFRef, DFNode> entry : exprCompo.outputs.entrySet()) {
	    DFRef ref = entry.getKey();
	    DFNode tmp = entry.getValue();
	    temps.put(ref, tmp);
	}
	for (Map.Entry<DFRef, DFNode> entry : bodyCompo.inputs.entrySet()) {
	    DFRef ref = entry.getKey();
	    DFNode dst = entry.getValue();
	    DFNode tmp = temps.get(ref);
	    if (tmp != null) {
		temps.remove(ref);
		tmp.connect(dst);
	    } else {
		LoopNode loop = loops.get(ref);
		if (loop == null) {
		    DFNode src = compo.get(ref);
		    loop = new LoopNode(graph, stmt, ref, src);
		    loops.put(ref, loop);
		}
		loop.connect(dst);
	    }
	}
	for (Map.Entry<DFRef, DFNode> entry : temps.entrySet()) {
	    DFRef ref = entry.getKey();
	    DFNode tmp = entry.getValue();
	    LoopNode loop = loops.get(ref);
	    if (loop != null) {
		BranchNode branch = new BranchNode(graph, stmt, condValue);
		tmp.connect(branch);
		DFNode cont = new DistNode(graph);
		branch.send(true, cont);
		loop.enter(cont);
		DFNode exit = new DistNode(graph);
		branch.send(false, exit);
		compo.put(ref, exit);
	    } else {		
		compo.put(ref, tmp);
	    }
	}
	for (Map.Entry<DFRef, DFNode> entry : bodyCompo.outputs.entrySet()) {
	    DFRef ref = entry.getKey();
	    DFNode dst = entry.getValue();
	    LoopNode loop = loops.get(ref);
	    if (loop != null) {
		BranchNode branch = new BranchNode(graph, stmt, condValue);
		dst.connect(branch);
		DFNode cont = new DistNode(graph);
		branch.send(true, cont);
		loop.enter(cont);
		DFNode exit = new DistNode(graph);
		branch.send(false, exit);
		compo.put(ref, exit);
	    } else {		
		compo.put(ref, dst);
	    }
	}
	
	return compo;
    }

    @SuppressWarnings("unchecked")
    public DFComponent processVariableDeclarationStatement
	(DFGraph graph, DFScope scope, DFComponent compo,
	 VariableDeclarationStatement varStmt)
	throws UnsupportedSyntax {
	Type varType = varStmt.getType();
	return processVariableDeclaration
	    (graph, scope, compo, varType, varStmt.fragments());
    }

    public DFComponent processExpressionStatement
	(DFGraph graph, DFScope scope, DFComponent compo,
	 ExpressionStatement exprStmt)
	throws UnsupportedSyntax {
	Expression expr = exprStmt.getExpression();
	return processExpression(graph, scope, compo, expr);
    }

    public DFComponent processReturnStatement
	(DFGraph graph, DFScope scope, DFComponent compo,
	 ReturnStatement rtnStmt)
	throws UnsupportedSyntax {
	Expression expr = rtnStmt.getExpression();
	compo = processExpression(graph, scope, compo, expr);
	DFNode rtrn = new ReturnNode(graph, rtnStmt, compo.value);
	compo.put(DFRef.RETURN, rtrn);
	return compo;
    }
    
    public DFComponent processIfStatement
	(DFGraph graph, DFScope scope, DFComponent compo,
	 IfStatement ifStmt)
	throws UnsupportedSyntax {
	
	Expression expr = ifStmt.getExpression();
	compo = processExpression(graph, scope, compo, expr);
	DFNode evalue = compo.value;
	
	Statement thenStmt = ifStmt.getThenStatement();
	DFComponent thenCompo = new DFComponent(graph);
	thenCompo = processStatement(graph, scope, thenCompo, thenStmt);
	
	Statement elseStmt = ifStmt.getElseStatement();
	DFComponent elseCompo = null;
	if (elseStmt != null) {
	    elseCompo = new DFComponent(graph);
	    elseCompo = processStatement(graph, scope, elseCompo, elseStmt);
	}

	return processConditional(graph, scope, compo, ifStmt,
				  evalue, thenCompo, elseCompo);
    }
	
    public DFComponent processWhileStatement
	(DFGraph graph, DFScope scope, DFComponent compo,
	 WhileStatement whileStmt)
	throws UnsupportedSyntax {
	
	Expression expr = whileStmt.getExpression();
	DFComponent exprCompo = new DFComponent(graph);
	exprCompo = processExpression(graph, scope, exprCompo, expr);
	
	Statement body = whileStmt.getBody();
	DFComponent bodyCompo = new DFComponent(graph);
	bodyCompo = processStatement(graph, scope, bodyCompo, body);

	return processLoop(graph, scope, compo, whileStmt,
			   exprCompo, bodyCompo);
    }
    
    @SuppressWarnings("unchecked")
    public DFComponent processForStatement
	(DFGraph graph, DFScope scope, DFComponent compo,
	 ForStatement forStmt)
	throws UnsupportedSyntax {

	for (Expression init : (List<Expression>) forStmt.initializers()) {
	    compo = processExpression(graph, scope, compo, init);
	}
	
	Expression expr = forStmt.getExpression();
	DFComponent exprCompo = new DFComponent(graph);
	exprCompo = processExpression(graph, scope, exprCompo, expr);
	
	Statement body = forStmt.getBody();
	DFComponent bodyCompo = new DFComponent(graph);
	bodyCompo = processStatement(graph, scope, bodyCompo, body);
	for (Expression update : (List<Expression>) forStmt.updaters()) {
	    bodyCompo = processExpression(graph, scope, bodyCompo, update);
	}
	
	return processLoop(graph, scope, compo, forStmt,
			   exprCompo, bodyCompo);
    }
    
    public DFComponent processStatement
	(DFGraph graph, DFScope scope, DFComponent compo, Statement stmt)
	throws UnsupportedSyntax {
	
	if (stmt instanceof AssertStatement) {
	    // Ignore assert.
	    return compo;
	    
	} else if (stmt instanceof Block) {
	    return processBlock(graph, scope, compo, (Block)stmt);

	} else if (stmt instanceof EmptyStatement) {
	    return compo;
	    
	} else if (stmt instanceof VariableDeclarationStatement) {
	    return processVariableDeclarationStatement
		(graph, scope, compo, (VariableDeclarationStatement)stmt);

	} else if (stmt instanceof ExpressionStatement) {
	    return processExpressionStatement
		(graph, scope, compo, (ExpressionStatement)stmt);
		
	} else if (stmt instanceof ReturnStatement) {
	    return processReturnStatement
		(graph, scope, compo, (ReturnStatement)stmt);
	    
	} else if (stmt instanceof IfStatement) {
	    return processIfStatement
		(graph, scope, compo, (IfStatement)stmt);
	    
	} else if (stmt instanceof WhileStatement) {
	    return processWhileStatement
		(graph, scope, compo, (WhileStatement)stmt);
	    
	} else if (stmt instanceof ForStatement) {
	    return processForStatement
		(graph, scope, compo, (ForStatement)stmt);
	    
	} else if (stmt instanceof SynchronizedStatement) {
	    // Ignore synchronized.
	    SynchronizedStatement syncStmt = (SynchronizedStatement)stmt;
	    return processBlock(graph, scope, compo, syncStmt.getBody());
	    
	} else {
	    // BreakStatement
	    // ContinueStatement
	    // DoStatement
	    // EnhancedForStatement
	    // SwitchCase
	    // SwitchStatement
	    // ThrowStatement
	    // TryStatement
	    
	    // ConstructorInvocation
	    // LabeledStatement
	    // SuperConstructorInvocation
	    // TypeDeclarationStatement
	    
	    throw new UnsupportedSyntax(stmt);
	}
    }

    @SuppressWarnings("unchecked")
    public DFComponent processBlock
	(DFGraph graph, DFScope parent, DFComponent compo, Block block)
	throws UnsupportedSyntax {
	
	DFScope scope = new DFScope(parent);
	for (Statement stmt : (List<Statement>) block.statements()) {
	    compo = processStatement(graph, scope, compo, stmt);
	}
	compo.finish(scope);
	return compo;
    }
    
    @SuppressWarnings("unchecked")
    public DFComponent processMethodDeclaration
	(DFGraph graph, DFScope scope, MethodDeclaration method)
	throws UnsupportedSyntax {
	DFComponent compo = new DFComponent(graph);
	// XXX check isContructor()
	Type funcType = method.getReturnType2();
	int i = 0;
	// XXX check isVarargs()
	for (SingleVariableDeclaration decl :
		 (List<SingleVariableDeclaration>) method.parameters()) {
	    DFNode param = new ArgNode(graph, decl, i++);
	    SimpleName paramName = decl.getName();
	    Type paramType = decl.getType();
	    // XXX check getExtraDimensions()
	    DFRef var = scope.add(paramName.getFullyQualifiedName(), paramType);
	    BoxNode assign = new SingleAssignNode(graph, decl, var);
	    assign.take(param);
	    compo.put(assign.ref, assign);
	}
	return compo;
    }
    
    public DFGraph getMethodGraph(MethodDeclaration method)
	throws UnsupportedSyntax {
	SimpleName funcName = method.getName();
	DFGraph graph = new DFGraph(funcName.getFullyQualifiedName());
	DFScope scope = new DFScope();
	
	DFComponent compo = processMethodDeclaration(graph, scope, method);
	
	Block funcBlock = method.getBody();
	compo = processBlock(graph, scope, compo, funcBlock);

        // Collapse redundant nodes.
        List<DFNode> removed = new ArrayList<DFNode>();
        for (DFNode node : graph.nodes) {
            if (node.label() == null &&
                node.send.size() == 1 &&
                node.recv.size() == 1) {
                removed.add(node);
            }
        }
        for (DFNode node : removed) {
            DFLink link0 = node.recv.get(0);
            DFLink link1 = node.send.get(0);
            if (link0.type == link1.type &&
		(link0.name == null || link1.name == null)) {
                node.remove();
                String name = link0.name;
                if (name == null) {
                    name = link1.name;
                }
                link0.src.connect(link1.dst, link0.type, name);
            }
        }
	
	return graph;
    }

    // main
    public static void main(String[] args) throws IOException {
	String[] classpath = new String[] { "/" };
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
		} catch (IOException e) {
		    System.err.println("Cannot open output file: "+path);
		}
	    } else if (arg.startsWith("-")) {
	    } else {
		files.add(arg);
	    }
	}
	
	GraphvizExporter exporter = new GraphvizExporter(output);
	for (String path : files) {
	    try {
		BufferedReader reader = new BufferedReader(new FileReader(path));
		String src = "";
		while (true) {
		    String line = reader.readLine();
		    if (line == null) break;
		    src += line+"\n";
		}
		reader.close();

		Utils.logit("Parsing: "+path);
		ASTParser parser = ASTParser.newParser(AST.JLS8);
		parser.setSource(src.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(true);
		parser.setEnvironment(classpath, null, null, true);
		CompilationUnit cu = (CompilationUnit)parser.createAST(null);
		
		Java2DF visitor = new Java2DF(exporter);
		cu.accept(visitor);
	    } catch (IOException e) {
		System.err.println("Cannot open input file: "+path);
	    }
	}

	output.close();
    }
}
