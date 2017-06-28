//  Java2DF.java
//
import java.io.*;
import java.util.*;
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
    public static DFRef SUPER = new DFRef(null, "SUPER");
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

    public void finish(DFComponent compo) {
	for (DFRef ref : this.vars.values()) {
	    compo.removeRef(ref);
	}
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


//  DFLabel
//
class DFLabel {

    public String name;

    public DFLabel(String name) {
	this.name = name;
    }

    public String toString() {
	return this.name+":";
    }
    
    public static DFLabel BREAK = new DFLabel("BREAK");
    public static DFLabel CONTINUE = new DFLabel("CONTINUE");
}


//  DFSnapshot
//
class DFSnapshot {

    public DFLabel label;
    public Map<DFRef, DFNode> nodes;

    public DFSnapshot(DFLabel label) {
	this.label = label;
	this.nodes = new HashMap<DFRef, DFNode>();
    }

    public String toString() {
	StringBuilder nodes = new StringBuilder();
	for (Map.Entry<DFRef, DFNode> entry : this.nodes.entrySet()) {
	    nodes.append(" "+entry.getKey()+":"+entry.getValue());
	}
	return ("<DFSnapshot("+this.label+") nodes="+nodes+">");
    }

    public void add(DFRef ref, DFNode node) {
	this.nodes.put(ref, node);
    }
}


//  DFComponent
//
class DFComponent {

    public DFGraph graph;
    public Map<DFRef, DFNode> inputs;
    public Map<DFRef, DFNode> outputs;
    public List<DFSnapshot> snapshots;
    public DFNode value;
    public AssignNode assign;
    
    public DFComponent(DFGraph graph) {
	this.graph = graph;
	this.inputs = new HashMap<DFRef, DFNode>();
	this.outputs = new HashMap<DFRef, DFNode>();
	this.snapshots = new ArrayList<DFSnapshot>();
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
		node = new DistNode(this.graph);
		this.inputs.put(ref, node);
	    }
	}
	return node;
    }

    public void put(DFRef ref, DFNode node) {
	this.outputs.put(ref, node);
    }

    public void removeRef(DFRef ref) {
	this.inputs.remove(ref);
	this.outputs.remove(ref);
    }

    public DFSnapshot snapshot(DFLabel label) {
	DFSnapshot snapshot = new DFSnapshot(label);
	for (Map.Entry<DFRef, DFNode> entry : this.inputs.entrySet()) {
	    DFRef ref = entry.getKey();
	    DFNode node = entry.getValue();
	    snapshot.add(ref, node);
	}
	for (Map.Entry<DFRef, DFNode> entry : this.outputs.entrySet()) {
	    DFRef ref = entry.getKey();
	    DFNode node = entry.getValue();
	    snapshot.add(ref, node);
	}
	this.snapshots.add(snapshot);
	return snapshot;
    }
    
    public void mergeSnapshots(DFComponent compo) {
	this.snapshots.addAll(compo.snapshots);
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

    public ASTNode ast;
    
    public ProgNode(DFGraph graph, ASTNode ast) {
	super(graph);
	this.ast = ast;
    }
}

// ArgNode: represnets a function argument.
class ArgNode extends ProgNode {

    public int index;

    public ArgNode(DFGraph graph, ASTNode ast, int index) {
	super(graph, ast);
	this.index = index;
    }

    public String label() {
	return "Arg "+this.index;
    }
}

// AssignNode: corresponds to a certain location in a memory.
abstract class AssignNode extends ProgNode {

    public DFRef ref;
    
    public AssignNode(DFGraph graph, ASTNode ast, DFRef ref) {
	super(graph, ast);
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
class SingleAssignNode extends AssignNode {

    public DFNode value;
    
    public SingleAssignNode(DFGraph graph, ASTNode ast, DFRef ref) {
	super(graph, ast, ref);
    }

    public void take(DFNode value) {
	this.value = value;
	value.connect(this, "assign");
    }
}

// ArrayAssignNode:
class ArrayAssignNode extends SingleAssignNode {

    public DFNode index;

    public ArrayAssignNode(DFGraph graph, ASTNode ast, DFRef ref,
			   DFNode array, DFNode index) {
	super(graph, ast, ref);
	this.index = index;
	array.connect(this, "access");
	index.connect(this, "index");
    }
}

// FieldAssignNode:
class FieldAssignNode extends SingleAssignNode {

    public DFNode obj;

    public FieldAssignNode(DFGraph graph, ASTNode ast, DFRef ref,
			   DFNode obj) {
	super(graph, ast, ref);
	this.obj = obj;
	obj.connect(this, "index");
    }
}


// ReturnNode: represents a return value.
class ReturnNode extends ProgNode {

    public DFNode value;
    
    public ReturnNode(DFGraph graph, ASTNode ast, DFNode value) {
	super(graph, ast);
	this.value = value;
	if (value != null) {
	    value.connect(this, "return");
	}
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

    public ConstNode(DFGraph graph, ASTNode ast, String value) {
	super(graph, ast);
	this.value = value;
    }

    public String label() {
	return this.value;
    }
}

// ArrayValueNode: represents an array.
class ArrayValueNode extends ProgNode {

    public List<DFNode> values;

    public ArrayValueNode(DFGraph graph, ASTNode ast) {
	super(graph, ast);
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

    public PrefixNode(DFGraph graph, ASTNode ast,
		      PrefixExpression.Operator op, DFNode value) {
	super(graph, ast);
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

    public PostfixNode(DFGraph graph, ASTNode ast,
		       PostfixExpression.Operator op, DFNode value) {
	super(graph, ast);
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

    public InfixNode(DFGraph graph, ASTNode ast,
		     InfixExpression.Operator op,
		     DFNode lvalue, DFNode rvalue) {
	super(graph, ast);
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

    public ArrayAccessNode(DFGraph graph, ASTNode ast,
			   DFNode array, DFNode value, DFNode index) {
	super(graph, ast);
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

    public FieldAccessNode(DFGraph graph, ASTNode ast,
			   DFNode value, DFNode obj) {
	super(graph, ast);
	this.value = value;
	this.obj = obj;
	value.connect(this, "access");
	obj.connect(this, "index");
    }

    public String label() {
	return ".";
    }
}

// TypeCastNode
class TypeCastNode extends ProgNode {

    public Type type;
    public DFNode value;
    
    public TypeCastNode(DFGraph graph, ASTNode ast,
			Type type, DFNode value) {
	super(graph, ast);
	this.type = type;
	this.value = value;
	value.connect(this, "cast");
    }

    public String label() {
	return "("+Utils.getTypeName(this.type)+")";
    }
}

// InstanceofNode
class InstanceofNode extends ProgNode {

    public Type type;
    public DFNode value;
    
    public InstanceofNode(DFGraph graph, ASTNode ast,
			  Type type, DFNode value) {
	super(graph, ast);
	this.type = type;
	this.value = value;
	value.connect(this, "instanceof");
    }

    public String label() {
	return Utils.getTypeName(this.type);
    }
}


// AssnOpNode
class AssnOpNode extends ProgNode {

    public Assignment.Operator op;
    public DFNode lvalue;
    public DFNode rvalue;

    public AssnOpNode(DFGraph graph, ASTNode ast,
		      Assignment.Operator op,
		      DFNode lvalue, DFNode rvalue) {
	super(graph, ast);
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
    
    public CondNode(DFGraph graph, ASTNode ast, DFNode value) {
	super(graph, ast);
	this.value = value;
	value.connect(this, DFLinkType.ControlFlow);
    }

    public DFNodeType type() {
	return DFNodeType.Cond;
    }
}

// BranchNode
class BranchNode extends CondNode {

    public BranchNode(DFGraph graph, ASTNode ast, DFNode value) {
	super(graph, ast, value);
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
    
    public JoinNode(DFGraph graph, ASTNode ast, DFNode value, DFRef ref) {
	super(graph, ast, value);
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
    
    public LoopNode(DFGraph graph, ASTNode ast, DFRef ref, DFNode init) {
	super(graph, ast);
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

// LoopJoinNode
class LoopJoinNode extends ProgNode {

    public DFRef ref;
    public DFNode exit;
    public List<DFNode> nodes;
    
    public LoopJoinNode(DFGraph graph, ASTNode ast, DFRef ref, DFNode exit) {
	super(graph, ast);
	this.ref = ref;
	this.nodes = new ArrayList<DFNode>();
	this.exit = exit;
	exit.connect(this, "exit");
    }

    public DFNodeType type() {
	return DFNodeType.Cond;
    }
    
    public String label() {
	return "LoopJoin:"+this.ref.name;
    }
    
    public void take(DFNode node, DFLabel label) {
	int i = this.nodes.size();
	node.connect(this, label.name+":"+i);
	this.nodes.add(node);
    }
}

// CallNode
abstract class CallNode extends ProgNode {

    public DFNode obj;
    public List<DFNode> args;

    public CallNode(DFGraph graph, ASTNode ast, DFNode obj) {
	super(graph, ast);
	this.obj = obj;
	this.args = new ArrayList<DFNode>();
	if (obj != null) {
	    obj.connect(this, "index");
	}
    }

    public void take(DFNode arg) {
	int i = this.args.size();
	arg.connect(this, "arg"+i);
	this.args.add(arg);
    }
}

// MethodCallNode
class MethodCallNode extends CallNode {

    public String name;

    public MethodCallNode(DFGraph graph, ASTNode ast, DFNode obj, String name) {
	super(graph, ast, obj);
	this.name = name;
    }
    
    public String label() {
	return this.name+"()";
    }
}

// CreateObjectNode
class CreateObjectNode extends CallNode {

    public Type type;

    public CreateObjectNode(DFGraph graph, ASTNode ast, DFNode obj, Type type) {
	super(graph, ast, obj);
	this.type = type;
    }
    
    public String label() {
	return "new "+Utils.getTypeName(this.type);
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

    public DFComponent processVariableDeclaration
	(DFGraph graph, DFScope scope, DFComponent compo, Type varType,
	 List<VariableDeclarationFragment> frags)
	throws UnsupportedSyntax {
	for (VariableDeclarationFragment frag : frags) {
	    SimpleName varName = frag.getName();
	    DFRef var = scope.add(varName.getIdentifier(), varType);
	    Expression init = frag.getInitializer();
	    if (init != null) {
		compo = processExpression(graph, scope, compo, init);
		AssignNode assign = new SingleAssignNode(graph, frag, var);
		assign.take(compo.value);
		compo.put(assign.ref, assign);
	    }
	}
	return compo;
    }

    @SuppressWarnings("unchecked")
    public DFComponent processAssignment
	(DFGraph graph, DFScope scope, DFComponent compo, Expression expr)
	throws UnsupportedSyntax {
	if (expr instanceof SimpleName) {
	    SimpleName varName = (SimpleName)expr;
	    DFRef ref = scope.lookup(varName.getIdentifier());
	    compo.assign = new SingleAssignNode(graph, expr, ref);
	    
	} else if (expr instanceof ArrayAccess) {
	    ArrayAccess aa = (ArrayAccess)expr;
	    compo = processExpression(graph, scope, compo, aa.getArray());
	    DFNode array = compo.value;
	    compo = processExpression(graph, scope, compo, aa.getIndex());
	    DFNode index = compo.value;
	    DFRef ref = scope.lookupArray();
	    compo.assign = new ArrayAssignNode(graph, expr, ref, array, index);
	    
	} else if (expr instanceof FieldAccess) {
	    FieldAccess fa = (FieldAccess)expr;
	    SimpleName fieldName = fa.getName();
	    compo = processExpression(graph, scope, compo, fa.getExpression());
	    DFNode obj = compo.value;
	    DFRef ref = scope.lookupField(fieldName.getIdentifier());
	    compo.assign = new FieldAssignNode(graph, expr, ref, obj);
	    
	} else if (expr instanceof QualifiedName) {
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

	} else if (expr instanceof SimpleName) {
	    SimpleName varName = (SimpleName)expr;
	    DFRef ref = scope.lookup(varName.getIdentifier());
	    compo.value = compo.get(ref);
	    
	} else if (expr instanceof ThisExpression) {
	    DFRef ref = DFRef.THIS;
	    compo.value = compo.get(ref);
	    
	} else if (expr instanceof BooleanLiteral) {
	    boolean value = ((BooleanLiteral)expr).booleanValue();
	    compo.value = new ConstNode(graph, expr, Boolean.toString(value));
	    
	} else if (expr instanceof CharacterLiteral) {
	    char value = ((CharacterLiteral)expr).charValue();
	    compo.value = new ConstNode(graph, expr, Character.toString(value));
	    
	} else if (expr instanceof NullLiteral) {
	    compo.value = new ConstNode(graph, expr, "null");
	    
	} else if (expr instanceof NumberLiteral) {
	    String value = ((NumberLiteral)expr).getToken();
	    compo.value = new ConstNode(graph, expr, value);
	    
	} else if (expr instanceof StringLiteral) {
	    String value = ((StringLiteral)expr).getLiteralValue();
	    compo.value = new ConstNode(graph, expr, value);
	    
	} else if (expr instanceof TypeLiteral) {
	    Type value = ((TypeLiteral)expr).getType();
	    compo.value = new ConstNode(graph, expr, Utils.getTypeName(value));
	    
	} else if (expr instanceof PrefixExpression) {
	    PrefixExpression prefix = (PrefixExpression)expr;
	    PrefixExpression.Operator op = prefix.getOperator();
	    Expression operand = prefix.getOperand();
	    compo = processAssignment(graph, scope, compo, operand);
	    AssignNode assign = compo.assign;
	    compo = processExpression(graph, scope, compo, operand);
	    DFNode value = new PrefixNode(graph, expr, op, compo.value);
	    if (op == PrefixExpression.Operator.INCREMENT ||
		op == PrefixExpression.Operator.DECREMENT) {
		assign.take(value);
		compo.put(assign.ref, assign);
	    }
	    compo.value = value;
	    
	} else if (expr instanceof PostfixExpression) {
	    PostfixExpression postfix = (PostfixExpression)expr;
	    PostfixExpression.Operator op = postfix.getOperator();
	    Expression operand = postfix.getOperand();
	    compo = processAssignment(graph, scope, compo, operand);
	    AssignNode assign = compo.assign;
	    compo = processExpression(graph, scope, compo, operand);
	    if (op == PostfixExpression.Operator.INCREMENT ||
		op == PostfixExpression.Operator.DECREMENT) {
		assign.take(new PostfixNode(graph, expr, op, compo.value));
		compo.put(assign.ref, assign);
	    }
	    
	} else if (expr instanceof InfixExpression) {
	    InfixExpression infix = (InfixExpression)expr;
	    InfixExpression.Operator op = infix.getOperator();
	    compo = processExpression(graph, scope, compo, infix.getLeftOperand());
	    DFNode lvalue = compo.value;
	    compo = processExpression(graph, scope, compo, infix.getRightOperand());
	    DFNode rvalue = compo.value;
	    compo.value = new InfixNode(graph, expr, op, lvalue, rvalue);
	    
	} else if (expr instanceof ParenthesizedExpression) {
	    ParenthesizedExpression paren = (ParenthesizedExpression)expr;
	    compo = processExpression(graph, scope, compo, paren.getExpression());
	    
	} else if (expr instanceof Assignment) {
	    Assignment assn = (Assignment)expr;
	    Assignment.Operator op = assn.getOperator();
	    compo = processAssignment(graph, scope, compo, assn.getLeftHandSide());
	    AssignNode assign = compo.assign;
	    compo = processExpression(graph, scope, compo, assn.getRightHandSide());
	    DFNode rvalue = compo.value;
	    if (op != Assignment.Operator.ASSIGN) {
		DFNode lvalue = compo.get(assign.ref);
		rvalue = new AssnOpNode(graph, assn, op, lvalue, rvalue);
	    }
	    assign.take(rvalue);
	    compo.put(assign.ref, assign);
	    compo.value = assign;

	} else if (expr instanceof VariableDeclarationExpression) {
	    VariableDeclarationExpression decl = (VariableDeclarationExpression)expr;
	    Type varType = decl.getType();
	    compo = processVariableDeclaration
		(graph, scope, compo, varType, decl.fragments());

	} else if (expr instanceof MethodInvocation) {
	    MethodInvocation invoke = (MethodInvocation)expr;
	    Expression expr1 = invoke.getExpression();
	    DFNode obj = null;
	    if (expr1 != null) {
		compo = processExpression(graph, scope, compo, expr1);
		obj = compo.value;
	    }
	    SimpleName methodName = invoke.getName();
	    MethodCallNode call = new MethodCallNode
		(graph, invoke, obj, methodName.getIdentifier());
	    for (Expression arg : (List<Expression>) invoke.arguments()) {
		compo = processExpression(graph, scope, compo, arg);
		call.take(compo.value);
	    }
	    compo.value = call;
	    
	} else if (expr instanceof SuperMethodInvocation) {
	    SuperMethodInvocation si = (SuperMethodInvocation)expr;
	    SimpleName methodName = si.getName();
	    DFNode obj = compo.get(DFRef.SUPER);
	    MethodCallNode call = new MethodCallNode
		(graph, si, obj, methodName.getIdentifier());
	    for (Expression arg : (List<Expression>) si.arguments()) {
		compo = processExpression(graph, scope, compo, arg);
		call.take(compo.value);
	    }
	    compo.value = call;
	    
	} else if (expr instanceof ArrayCreation) {
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
	    ArrayInitializer init = (ArrayInitializer)expr;
	    ArrayValueNode arr = new ArrayValueNode(graph, init);
	    for (Expression expr1 : (List<Expression>) init.expressions()) {
		compo = processExpression(graph, scope, compo, expr1);
		arr.take(compo.value);
	    }
	    compo.value = arr;
	    // XXX array ref is not used.
	    
	} else if (expr instanceof ArrayAccess) {
	    ArrayAccess aa = (ArrayAccess)expr;
	    DFRef ref = scope.lookupArray();
	    compo = processExpression(graph, scope, compo, aa.getArray());
	    DFNode array = compo.value;
	    compo = processExpression(graph, scope, compo, aa.getIndex());
	    DFNode index = compo.value;
	    compo.value = new ArrayAccessNode(graph, aa, compo.get(ref), array, index);
	    
	} else if (expr instanceof FieldAccess) {
	    FieldAccess fa = (FieldAccess)expr;
	    SimpleName fieldName = fa.getName();
	    DFRef ref = scope.lookupField(fieldName.getIdentifier());
	    compo = processExpression(graph, scope, compo, fa.getExpression());
	    DFNode obj = compo.value;
	    compo.value = new FieldAccessNode(graph, fa, compo.get(ref), obj);
	    
	} else if (expr instanceof SuperFieldAccess) {
	    SuperFieldAccess sfa = (SuperFieldAccess)expr;
	    SimpleName fieldName = sfa.getName();
	    DFRef ref = scope.lookupField(fieldName.getIdentifier());
	    DFNode obj = compo.get(DFRef.SUPER);
	    compo.value = new FieldAccessNode(graph, sfa, compo.get(ref), obj);
	    
	} else if (expr instanceof QualifiedName) {
	    QualifiedName qn = (QualifiedName)expr;
	    SimpleName fieldName = qn.getName();
	    DFRef ref = scope.lookupField(fieldName.getIdentifier());
	    compo = processExpression(graph, scope, compo, qn.getQualifier());
	    DFNode obj = compo.value;
	    compo.value = new FieldAccessNode(graph, qn, compo.get(ref), obj);
	    
	} else if (expr instanceof CastExpression) {
	    CastExpression cast = (CastExpression)expr;
	    Type type = cast.getType();
	    compo = processExpression(graph, scope, compo, cast.getExpression());
	    compo.value = new TypeCastNode(graph, cast, type, compo.value);
	    
	} else if (expr instanceof ClassInstanceCreation) {
	    ClassInstanceCreation cstr = (ClassInstanceCreation)expr;
	    Type instType = cstr.getType();
	    Expression expr1 = cstr.getExpression();
	    DFNode obj = null;
	    if (expr1 != null) {
		compo = processExpression(graph, scope, compo, expr1);
		obj = compo.value;
	    }
	    CreateObjectNode call = new CreateObjectNode(graph, cstr, obj, instType);
	    for (Expression arg : (List<Expression>) cstr.arguments()) {
		compo = processExpression(graph, scope, compo, arg);
		call.take(compo.value);
	    }
	    compo.value = call;
	    // XXX ignore getAnonymousClassDeclaration();
	    
	} else if (expr instanceof ConditionalExpression) {
	    ConditionalExpression cond = (ConditionalExpression)expr;
	    compo = processExpression(graph, scope, compo, cond.getExpression());
	    compo = processExpression(graph, scope, compo, cond.getThenExpression());
	    compo = processExpression(graph, scope, compo, cond.getElseExpression());
	    // XXX conditional node
	    
	} else if (expr instanceof InstanceofExpression) {
	    InstanceofExpression instof = (InstanceofExpression)expr;
	    Type type = instof.getRightOperand();
	    compo = processExpression(graph, scope, compo, instof.getLeftOperand());
	    compo.value = new InstanceofNode(graph, instof, type, compo.value);
	    
	} else {
	    // LambdaExpression
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
	compo.mergeSnapshots(trueCompo);
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
	    compo.mergeSnapshots(falseCompo);
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
	 DFNode condValue, DFComponent loopCompo)
	throws UnsupportedSyntax {
	
	Map<DFRef, LoopNode> loops = new HashMap<DFRef, LoopNode>();
	Map<DFRef, LoopJoinNode> joins = new HashMap<DFRef, LoopJoinNode>();
	
	for (Map.Entry<DFRef, DFNode> entry : loopCompo.inputs.entrySet()) {
	    DFRef ref = entry.getKey();
	    DFNode input = entry.getValue();
	    DFNode output = loopCompo.outputs.get(ref);
	    DFNode src = compo.get(ref);
	    if (output == null) {
		// ref is not a loop variable.
		src.connect(input);
	    } else {
		// ref is a loop variable.
		LoopNode loop = new LoopNode(graph, stmt, ref, src);
		loops.put(ref, loop);
		loop.connect(input);
		
		BranchNode branch = new BranchNode(graph, stmt, condValue);
		output.connect(branch);
		DFNode cont = new DistNode(graph);
		branch.send(true, cont);
		loop.enter(cont);
		DFNode exit = new DistNode(graph);
		branch.send(false, exit);
		compo.put(ref, exit);
	    }
	}
	
	for (Map.Entry<DFRef, DFNode> entry : loopCompo.outputs.entrySet()) {
	    DFRef ref = entry.getKey();
	    DFNode output = entry.getValue();
	    DFNode input = loopCompo.inputs.get(ref);
	    if (input == null) {
		// ref is not a loop variable.
		compo.put(ref, output);
	    }
	}

	for (DFSnapshot snapshot : loopCompo.snapshots) {
	    for (Map.Entry<DFRef, LoopNode> entry : loops.entrySet()) {
		DFRef ref = entry.getKey();
		DFNode src = entry.getValue();
		DFNode dst = snapshot.nodes.get(ref);
		if (dst == null) {
		    dst = entry.getValue();
		}
		LoopJoinNode join = joins.get(ref);
		if (join == null) {
		    join = new LoopJoinNode(graph, stmt, ref, src);
		    joins.put(ref, join);
		}
		join.take(dst, snapshot.label);
		if (snapshot.label == DFLabel.BREAK) {
		    compo.put(ref, join);
		}
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
	DFNode value = null;
	if (expr != null) {
	    compo = processExpression(graph, scope, compo, expr);
	    value = compo.value;
	}
	DFNode rtrn = new ReturnNode(graph, rtnStmt, value);
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
	
	DFComponent loopCompo = new DFComponent(graph);
	loopCompo = processExpression(graph, scope, loopCompo,
				      whileStmt.getExpression());
	DFNode condValue = loopCompo.value;
	loopCompo = processStatement(graph, scope, loopCompo, 
				     whileStmt.getBody());
	return processLoop(graph, scope, compo, whileStmt,
			   condValue, loopCompo);
    }
    
    @SuppressWarnings("unchecked")
    public DFComponent processForStatement
	(DFGraph graph, DFScope scope, DFComponent compo,
	 ForStatement forStmt)
	throws UnsupportedSyntax {

	for (Expression init : (List<Expression>) forStmt.initializers()) {
	    compo = processExpression(graph, scope, compo, init);
	}
	
	DFComponent loopCompo = new DFComponent(graph);
	Expression expr = forStmt.getExpression();
	DFNode condValue = loopCompo.value;
	if (expr != null) {
	    loopCompo = processExpression(graph, scope, loopCompo, expr);
	    condValue = loopCompo.value;
	} else {
	    condValue = new ConstNode(graph, null, "true");
	}
	loopCompo = processStatement(graph, scope, loopCompo,
				     forStmt.getBody());
	for (Expression update : (List<Expression>) forStmt.updaters()) {
	    loopCompo = processExpression(graph, scope, loopCompo, update);
	}
	
	return processLoop(graph, scope, compo, forStmt,
			   condValue, loopCompo);
    }
    
    @SuppressWarnings("unchecked")
    public DFComponent processStatement
	(DFGraph graph, DFScope scope, DFComponent compo, Statement stmt)
	throws UnsupportedSyntax {
	
	if (stmt instanceof AssertStatement) {
	    // Ignore assert.
	} else if (stmt instanceof Block) {
	    compo = processBlock(graph, scope, compo, (Block)stmt);

	} else if (stmt instanceof EmptyStatement) {
	    
	} else if (stmt instanceof VariableDeclarationStatement) {
	    compo = processVariableDeclarationStatement
		(graph, scope, compo, (VariableDeclarationStatement)stmt);

	} else if (stmt instanceof ExpressionStatement) {
	    compo = processExpressionStatement
		(graph, scope, compo, (ExpressionStatement)stmt);
		
	} else if (stmt instanceof ReturnStatement) {
	    compo = processReturnStatement
		(graph, scope, compo, (ReturnStatement)stmt);
	    
	} else if (stmt instanceof IfStatement) {
	    compo = processIfStatement
		(graph, scope, compo, (IfStatement)stmt);
	    
	} else if (stmt instanceof SwitchStatement) {
	    // XXX switch
	    SwitchStatement switchStmt = (SwitchStatement)stmt;
	    compo = processExpression(graph, scope, compo, switchStmt.getExpression());
	    for (Statement cstmt : (List<Statement>) switchStmt.statements()) {
		compo = processStatement(graph, scope, compo, cstmt);
	    }
	    
	} else if (stmt instanceof SwitchCase) {
	    // XXX case
	    SwitchCase switchCase = (SwitchCase)stmt;
	    Expression expr = switchCase.getExpression();
	    if (expr != null) {
		compo = processExpression(graph, scope, compo, expr);
	    }
	    
	} else if (stmt instanceof WhileStatement) {
	    compo = processWhileStatement
		(graph, scope, compo, (WhileStatement)stmt);
	    
	} else if (stmt instanceof DoStatement) {
	    DoStatement doStmt = (DoStatement)stmt;
	    // XXX do
	    // doStmt.getBody();
	    // doStmt.getExpression();
	    
	} else if (stmt instanceof ForStatement) {
	    // Create a new scope.
	    DFScope forScope = new DFScope(scope);
	    compo = processForStatement
		(graph, scope, compo, (ForStatement)stmt);
	    forScope.finish(compo);
	    
	} else if (stmt instanceof EnhancedForStatement) {
	    // Create a new scope.
	    DFScope eforScope = new DFScope(scope);
	    // XXX compo = processEForStatement(graph, scope, compo, (EnhancedForStatement)stmt);
	    eforScope.finish(compo);
	    
	} else if (stmt instanceof BreakStatement) {
	    // XXX ignore label (for now).
	    BreakStatement breakStmt = (BreakStatement)stmt;
	    // SimpleName labelName = breakStmt.getLabel();
	    compo.snapshot(DFLabel.BREAK);
	    
	} else if (stmt instanceof ContinueStatement) {
	    // XXX ignore label (for now).
	    ContinueStatement contStmt = (ContinueStatement)stmt;
	    // SimpleName labelName = contStmt.getLabel();
	    compo.snapshot(DFLabel.CONTINUE);
	    
	} else if (stmt instanceof LabeledStatement) {
	    // XXX ignore label (for now).
	    LabeledStatement labeledStmt = (LabeledStatement)stmt;
	    // SimpleName labelName = labeledStmt.getLabel();
	    compo = processStatement(graph, scope, compo, labeledStmt.getBody());
	    
	} else if (stmt instanceof SynchronizedStatement) {
	    // Ignore synchronized.
	    SynchronizedStatement syncStmt = (SynchronizedStatement)stmt;
	    compo = processBlock(graph, scope, compo, syncStmt.getBody());

	} else if (stmt instanceof TryStatement) {
	    // XXX Ignore try...catch (for now).
	    TryStatement tryStmt = (TryStatement)stmt;
	    compo = processBlock(graph, scope, compo, tryStmt.getBody());
	    if (tryStmt.getFinally() != null) {
		compo = processBlock(graph, scope, compo, tryStmt.getFinally());
	    }
	    
	} else if (stmt instanceof ThrowStatement) {
	    // XXX Ignore throw (for now).
	    ThrowStatement throwStmt = (ThrowStatement)stmt;
	    compo = processExpression(graph, scope, compo, throwStmt.getExpression());
	    
	} else if (stmt instanceof ConstructorInvocation) {
	    // XXX ignore all side effects.
	    ConstructorInvocation ci = (ConstructorInvocation)stmt;
	    for (Expression arg : (List<Expression>) ci.arguments()) {
		compo = processExpression(graph, scope, compo, arg);
	    }
	    
	} else if (stmt instanceof SuperConstructorInvocation) {
	    // XXX ignore all side effects.
	    SuperConstructorInvocation sci = (SuperConstructorInvocation)stmt;
	    for (Expression arg : (List<Expression>) sci.arguments()) {
		compo = processExpression(graph, scope, compo, arg);
	    }
		
	} else {
	    // TypeDeclarationStatement
	    
	    throw new UnsupportedSyntax(stmt);
	}

	return compo;
    }

    @SuppressWarnings("unchecked")
    public DFComponent processBlock
	(DFGraph graph, DFScope parent, DFComponent compo, Block block)
	throws UnsupportedSyntax {
	
	DFScope scope = new DFScope(parent);
	List<Statement> statements = (List<Statement>) block.statements();
	if (statements != null) {
	    for (Statement stmt : statements) {
		compo = processStatement(graph, scope, compo, stmt);
	    }
	}
	scope.finish(compo);
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
	    AssignNode assign = new SingleAssignNode(graph, decl, var);
	    assign.take(param);
	    compo.put(assign.ref, assign);
	}
	return compo;
    }
    
    public DFGraph getMethodGraph(MethodDeclaration method)
	throws UnsupportedSyntax {
	String funcName = method.getName().getFullyQualifiedName();
	DFGraph graph = new DFGraph(funcName);
	DFScope scope = new DFScope();
	
	DFComponent compo = processMethodDeclaration(graph, scope, method);
	
	Block funcBlock = method.getBody();
	// Ignore method prototypes.
	if (funcBlock == null) return null;
				   
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

    public boolean visit(MethodDeclaration method) {
	String funcName = method.getName().getFullyQualifiedName();
	try {
	    DFGraph graph = getMethodGraph(method);
	    if (graph != null) {
		Utils.logit("success: "+funcName);
		if (this.exporter != null) {
		    this.exporter.writeGraph(graph);
		}
	    }
	} catch (UnsupportedSyntax e) {
	    String name = e.ast.getClass().getName();
	    Utils.logit("Unsupported("+name+"): "+e.ast);
	    Utils.logit("fail: "+funcName);
	}
	return true;
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
		    Utils.logit("Exporting: "+path);
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
