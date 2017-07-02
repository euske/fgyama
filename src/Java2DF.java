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


//  DFRef
//  Place to store a value.
//
class DFRef {

    public DFScope scope;
    public String name;
    
    public DFRef(DFScope scope, String name) {
	this.scope = scope;
	this.name = name;
    }

    public String toString() {
	String scope = (this.scope == null)? "" : this.scope.name;
	return ("<DFRef("+scope+"."+this.name+")>");
    }
}


//  DFVar
//  Variable.
//
class DFVar extends DFRef {

    public Type type;

    public DFVar(DFScope scope, String name, Type type) {
	super(scope, name);
	this.type = type;
    }

    public String toString() {
	return ("<DFVar("+this.scope.name+"."+this.name+"): "+this.type+">");
    }
}


//  DFScope
//  Mapping from name -> variable.
//
class DFScope {

    public String name;
    public DFScope parent;
    public List<DFScope> children;

    public Map<String, DFVar> vars;
    public Set<DFRef> inputs;
    public Set<DFRef> outputs;

    public static int baseId = 0;
    public static int genId() {
	return baseId++;
    }

    public DFScope() {
	this(null);
    }

    public static DFRef THIS = new DFRef(null, "THIS");
    public static DFRef SUPER = new DFRef(null, "SUPER");
    public static DFRef RETURN = new DFRef(null, "RETURN");
    public static DFRef ARRAY = new DFRef(null, "[]");
    
    public DFScope(DFScope parent) {
	this.name = "S"+genId();
	this.parent = parent;
	if (parent != null) {
	    parent.children.add(this);
	}
	this.children = new ArrayList<DFScope>();
	this.vars = new HashMap<String, DFVar>();
	this.inputs = new HashSet<DFRef>();
	this.outputs = new HashSet<DFRef>();
    }

    public String toString() {
	return ("<DFScope("+this.name+")>");
    }

    public void dump() {
	dump(System.out, "");
    }
    
    public void dump(PrintStream out, String indent) {
	out.println(indent+this.name+" {");
	String i2 = indent + "  ";
	StringBuilder inputs = new StringBuilder();
	for (DFRef ref : this.inputs) {
	    inputs.append(" "+ref);
	}
	out.println(i2+"inputs:"+inputs);
	StringBuilder outputs = new StringBuilder();
	for (DFRef ref : this.outputs) {
	    outputs.append(" "+ref);
	}
	out.println(i2+"outputs:"+outputs);
	StringBuilder loops = new StringBuilder();
	for (DFRef ref : this.getLoopRefs()) {
	    loops.append(" "+ref);
	}
	out.println(i2+"loops:"+loops);
	for (DFVar var : this.vars.values()) {
	    out.println(i2+"defined: "+var);
	}
	for (DFScope scope : this.children) {
	    scope.dump(out, i2);
	}
	out.println(indent+"}");
    }

    public DFVar add(String name, Type type) {
	DFVar var = new DFVar(this, name, type);
	this.vars.put(name, var);
	return var;
    }

    public Collection<DFVar> vars() {
	return this.vars.values();
    }

    public DFRef lookup(String name) {
	DFVar var = this.vars.get(name);
	if (var != null) {
	    return var;
	} else if (this.parent != null) {
	    return this.parent.lookup(name);
	} else {
	    return this.add(name, null);
	}
    }

    public DFRef lookupThis() {
	return THIS;
    }
    
    public DFRef lookupSuper() {
	return SUPER;
    }
    
    public DFRef lookupReturn() {
	return RETURN;
    }
    
    public DFRef lookupArray() {
	return ARRAY;
    }
    
    public DFRef lookupField(String name) {
	return this.lookup("."+name);
    }
    
    public void finish(DFComponent cpt) {
	for (DFRef ref : this.vars.values()) {
	    cpt.removeRef(ref);
	}
    }

    public void addInput(DFRef ref) {
	if (this.parent != null) {
	    this.parent.addInput(ref);
	}
	this.inputs.add(ref);
    }

    public void addOutput(DFRef ref) {
	if (this.parent != null) {
	    this.parent.addOutput(ref);
	}
	this.outputs.add(ref);
    }

    public Set<DFRef> getLoopRefs() {
	Set<DFRef> refs = new HashSet<DFRef>(this.inputs);
	refs.retainAll(this.outputs);
	return refs;
    }
}


//  DFScopeMap
//
class DFScopeMap {

    public Map<ASTNode, DFScope> scopes;

    public DFScopeMap() {
	this.scopes = new HashMap<ASTNode, DFScope>();
    }

    public void put(ASTNode ast, DFScope scope) {
	this.scopes.put(ast, scope);
    }

    public DFScope get(ASTNode ast) {
	return this.scopes.get(ast);
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


//  DFFrame
//
class DFFrame {

    public DFFrame parent;
    public Collection<DFRef> loopRefs;
    
    public DFFrame(DFFrame parent, Collection<DFRef> loopRefs) {
	this.parent = parent;
	this.loopRefs = loopRefs;
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


//  DFExit
//
class DFExit {

    public DFRef ref;
    public DFNode node;
    public DFFrame frame;
    public DFLabel label;

    public DFExit(DFRef ref, DFNode node, DFFrame frame, DFLabel label) {
	this.ref = ref;
	this.node = node;
	this.frame = frame;
	this.label = label;
    }

    public String toString() {
	return (this.ref+":"+this.node+" -> "+this.frame+":"+this.label);
    }
}


//  DFComponent
//
class DFComponent {

    public DFGraph graph;
    public Map<DFRef, DFNode> inputs;
    public Map<DFRef, DFNode> outputs;
    public List<DFExit> exits;
    public DFNode value;
    public AssignNode assign;
    
    public DFComponent(DFGraph graph) {
	this.graph = graph;
	this.inputs = new HashMap<DFRef, DFNode>();
	this.outputs = new HashMap<DFRef, DFNode>();
	this.exits = new ArrayList<DFExit>();
	this.value = null;
	this.assign = null;
    }

    public void dump() {
	dump(System.out);
    }
    
    public void dump(PrintStream out) {
	out.println("DFComponent");
	StringBuilder inputs = new StringBuilder();
	for (DFRef ref : this.inputs.keySet()) {
	    inputs.append(" "+ref);
	}
	out.println("  inputs:"+inputs);
	StringBuilder outputs = new StringBuilder();
	for (DFRef ref : this.outputs.keySet()) {
	    outputs.append(" "+ref);
	}
	out.println("  outputs:"+outputs);
	for (DFExit exit : this.exits) {
	    out.println("  exit: "+exit);
	}
	if (this.value != null) {
	    out.println("  value: "+this.value);
	}
	if (this.assign != null) {
	    out.println("  assign: "+this.assign);
	}
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

    public void jump(DFRef ref, DFFrame frame, DFLabel label) {
	DFNode node = this.get(ref);
	this.addExit(new DFExit(ref, node, frame, label));
	this.outputs.remove(ref);
    }

    public void addExit(DFExit exit) {
	this.exits.add(exit);
    }

    public void removeRef(DFRef ref) {
	this.inputs.remove(ref);
	this.outputs.remove(ref);
	List<DFExit> removed = new ArrayList<DFExit>();
	for (DFExit exit : this.exits) {
	    if (exit.ref == ref) {
		removed.add(exit);
	    }
	}
	this.exits.removeAll(removed);
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


// AssignOpNode
class AssignOpNode extends ProgNode {

    public Assignment.Operator op;
    public DFNode lvalue;
    public DFNode rvalue;

    public AssignOpNode(DFGraph graph, ASTNode ast,
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
    public DFNode enter;
    
    public LoopNode(DFGraph graph, ASTNode ast, DFRef ref, DFNode enter) {
	super(graph, ast);
	this.ref = ref;
	this.enter = enter;
	enter.connect(this, "enter");
    }
    
    public DFNodeType type() {
	return DFNodeType.Loop;
    }
    
    public String label() {
	return "Loop:"+this.ref.name;
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
	(DFGraph graph, DFScope scope, DFComponent cpt, 
	 List<VariableDeclarationFragment> frags)
	throws UnsupportedSyntax {

	for (VariableDeclarationFragment frag : frags) {
	    SimpleName varName = frag.getName();
	    DFRef var = scope.lookup(varName.getIdentifier());
	    Expression init = frag.getInitializer();
	    if (init != null) {
		cpt = processExpression(graph, scope, cpt, init);
		AssignNode assign = new SingleAssignNode(graph, frag, var);
		assign.take(cpt.value);
		cpt.put(assign.ref, assign);
	    }
	}
	return cpt;
    }

    @SuppressWarnings("unchecked")
    public DFComponent processAssignment
	(DFGraph graph, DFScope scope, DFComponent cpt, 
	 Expression expr)
	throws UnsupportedSyntax {

	if (expr instanceof SimpleName) {
	    SimpleName varName = (SimpleName)expr;
	    DFRef ref = scope.lookup(varName.getIdentifier());
	    cpt.assign = new SingleAssignNode(graph, expr, ref);
	    
	} else if (expr instanceof ArrayAccess) {
	    ArrayAccess aa = (ArrayAccess)expr;
	    cpt = processExpression(graph, scope, cpt, aa.getArray());
	    DFNode array = cpt.value;
	    cpt = processExpression(graph, scope, cpt, aa.getIndex());
	    DFNode index = cpt.value;
	    DFRef ref = scope.lookupArray();
	    cpt.assign = new ArrayAssignNode(graph, expr, ref, array, index);
	    
	} else if (expr instanceof FieldAccess) {
	    FieldAccess fa = (FieldAccess)expr;
	    SimpleName fieldName = fa.getName();
	    cpt = processExpression(graph, scope, cpt, fa.getExpression());
	    DFNode obj = cpt.value;
	    DFRef ref = scope.lookupField(fieldName.getIdentifier());
	    cpt.assign = new FieldAssignNode(graph, expr, ref, obj);
	    
	} else if (expr instanceof QualifiedName) {
	    QualifiedName qn = (QualifiedName)expr;
	    SimpleName fieldName = qn.getName();
	    DFRef ref = scope.lookupField(fieldName.getIdentifier());
	    cpt = processExpression(graph, scope, cpt, qn.getQualifier());
	    DFNode obj = cpt.value;
	    cpt.assign = new FieldAssignNode(graph, expr, ref, obj);
	    
	} else {
	    throw new UnsupportedSyntax(expr);
	}
	return cpt;
    }
    
    @SuppressWarnings("unchecked")
    public DFComponent processExpression
	(DFGraph graph, DFScope scope, DFComponent cpt, 
	 Expression expr)
	throws UnsupportedSyntax {

	if (expr instanceof Annotation) {

	} else if (expr instanceof SimpleName) {
	    SimpleName varName = (SimpleName)expr;
	    DFRef ref = scope.lookup(varName.getIdentifier());
	    cpt.value = cpt.get(ref);
	    
	} else if (expr instanceof ThisExpression) {
	    cpt.value = cpt.get(scope.lookupThis());
	    
	} else if (expr instanceof BooleanLiteral) {
	    boolean value = ((BooleanLiteral)expr).booleanValue();
	    cpt.value = new ConstNode(graph, expr, Boolean.toString(value));
	    
	} else if (expr instanceof CharacterLiteral) {
	    char value = ((CharacterLiteral)expr).charValue();
	    cpt.value = new ConstNode(graph, expr, Character.toString(value));
	    
	} else if (expr instanceof NullLiteral) {
	    cpt.value = new ConstNode(graph, expr, "null");
	    
	} else if (expr instanceof NumberLiteral) {
	    String value = ((NumberLiteral)expr).getToken();
	    cpt.value = new ConstNode(graph, expr, value);
	    
	} else if (expr instanceof StringLiteral) {
	    String value = ((StringLiteral)expr).getLiteralValue();
	    cpt.value = new ConstNode(graph, expr, value);
	    
	} else if (expr instanceof TypeLiteral) {
	    Type value = ((TypeLiteral)expr).getType();
	    cpt.value = new ConstNode(graph, expr, Utils.getTypeName(value));
	    
	} else if (expr instanceof PrefixExpression) {
	    PrefixExpression prefix = (PrefixExpression)expr;
	    PrefixExpression.Operator op = prefix.getOperator();
	    Expression operand = prefix.getOperand();
	    cpt = processAssignment(graph, scope, cpt, operand);
	    AssignNode assign = cpt.assign;
	    cpt = processExpression(graph, scope, cpt, operand);
	    DFNode value = new PrefixNode(graph, expr, op, cpt.value);
	    if (op == PrefixExpression.Operator.INCREMENT ||
		op == PrefixExpression.Operator.DECREMENT) {
		assign.take(value);
		cpt.put(assign.ref, assign);
	    }
	    cpt.value = value;
	    
	} else if (expr instanceof PostfixExpression) {
	    PostfixExpression postfix = (PostfixExpression)expr;
	    PostfixExpression.Operator op = postfix.getOperator();
	    Expression operand = postfix.getOperand();
	    cpt = processAssignment(graph, scope, cpt, operand);
	    AssignNode assign = cpt.assign;
	    cpt = processExpression(graph, scope, cpt, operand);
	    if (op == PostfixExpression.Operator.INCREMENT ||
		op == PostfixExpression.Operator.DECREMENT) {
		assign.take(new PostfixNode(graph, expr, op, cpt.value));
		cpt.put(assign.ref, assign);
	    }
	    
	} else if (expr instanceof InfixExpression) {
	    InfixExpression infix = (InfixExpression)expr;
	    InfixExpression.Operator op = infix.getOperator();
	    cpt = processExpression(graph, scope, cpt, infix.getLeftOperand());
	    DFNode lvalue = cpt.value;
	    cpt = processExpression(graph, scope, cpt, infix.getRightOperand());
	    DFNode rvalue = cpt.value;
	    cpt.value = new InfixNode(graph, expr, op, lvalue, rvalue);
	    
	} else if (expr instanceof ParenthesizedExpression) {
	    ParenthesizedExpression paren = (ParenthesizedExpression)expr;
	    cpt = processExpression(graph, scope, cpt, paren.getExpression());
	    
	} else if (expr instanceof Assignment) {
	    Assignment assn = (Assignment)expr;
	    Assignment.Operator op = assn.getOperator();
	    cpt = processAssignment(graph, scope, cpt, assn.getLeftHandSide());
	    AssignNode assign = cpt.assign;
	    cpt = processExpression(graph, scope, cpt, assn.getRightHandSide());
	    DFNode rvalue = cpt.value;
	    if (op != Assignment.Operator.ASSIGN) {
		DFNode lvalue = cpt.get(assign.ref);
		rvalue = new AssignOpNode(graph, assn, op, lvalue, rvalue);
	    }
	    assign.take(rvalue);
	    cpt.put(assign.ref, assign);
	    cpt.value = assign;

	} else if (expr instanceof VariableDeclarationExpression) {
	    VariableDeclarationExpression decl = (VariableDeclarationExpression)expr;
	    cpt = processVariableDeclaration
		(graph, scope, cpt, decl.fragments());

	} else if (expr instanceof MethodInvocation) {
	    MethodInvocation invoke = (MethodInvocation)expr;
	    Expression expr1 = invoke.getExpression();
	    DFNode obj = null;
	    if (expr1 != null) {
		cpt = processExpression(graph, scope, cpt, expr1);
		obj = cpt.value;
	    }
	    SimpleName methodName = invoke.getName();
	    MethodCallNode call = new MethodCallNode
		(graph, invoke, obj, methodName.getIdentifier());
	    for (Expression arg : (List<Expression>) invoke.arguments()) {
		cpt = processExpression(graph, scope, cpt, arg);
		call.take(cpt.value);
	    }
	    cpt.value = call;
	    
	} else if (expr instanceof SuperMethodInvocation) {
	    SuperMethodInvocation si = (SuperMethodInvocation)expr;
	    SimpleName methodName = si.getName();
	    DFNode obj = cpt.get(scope.lookupSuper());
	    MethodCallNode call = new MethodCallNode
		(graph, si, obj, methodName.getIdentifier());
	    for (Expression arg : (List<Expression>) si.arguments()) {
		cpt = processExpression(graph, scope, cpt, arg);
		call.take(cpt.value);
	    }
	    cpt.value = call;
	    
	} else if (expr instanceof ArrayCreation) {
	    ArrayCreation ac = (ArrayCreation)expr;
	    for (Expression dim : (List<Expression>) ac.dimensions()) {
		cpt = processExpression(graph, scope, cpt, dim);
		// XXX cpt.value is not used (for now).
		if (cpt.value != null) {
		    graph.removeNode(cpt.value);
		}
	    }
	    ArrayInitializer init = ac.getInitializer();
	    if (init != null) {
		cpt = processExpression(graph, scope, cpt, init);
	    } else {
		cpt.value = new ArrayValueNode(graph, ac);
	    }
	    
	} else if (expr instanceof ArrayInitializer) {
	    ArrayInitializer init = (ArrayInitializer)expr;
	    ArrayValueNode arr = new ArrayValueNode(graph, init);
	    for (Expression expr1 : (List<Expression>) init.expressions()) {
		cpt = processExpression(graph, scope, cpt, expr1);
		arr.take(cpt.value);
	    }
	    cpt.value = arr;
	    // XXX array ref is not used.
	    
	} else if (expr instanceof ArrayAccess) {
	    ArrayAccess aa = (ArrayAccess)expr;
	    DFRef ref = scope.lookupArray();
	    cpt = processExpression(graph, scope, cpt, aa.getArray());
	    DFNode array = cpt.value;
	    cpt = processExpression(graph, scope, cpt, aa.getIndex());
	    DFNode index = cpt.value;
	    cpt.value = new ArrayAccessNode(graph, aa, cpt.get(ref), array, index);
	    
	} else if (expr instanceof FieldAccess) {
	    FieldAccess fa = (FieldAccess)expr;
	    SimpleName fieldName = fa.getName();
	    DFRef ref = scope.lookupField(fieldName.getIdentifier());
	    cpt = processExpression(graph, scope, cpt, fa.getExpression());
	    DFNode obj = cpt.value;
	    cpt.value = new FieldAccessNode(graph, fa, cpt.get(ref), obj);
	    
	} else if (expr instanceof SuperFieldAccess) {
	    SuperFieldAccess sfa = (SuperFieldAccess)expr;
	    SimpleName fieldName = sfa.getName();
	    DFRef ref = scope.lookupField(fieldName.getIdentifier());
	    DFNode obj = cpt.get(scope.lookupSuper());
	    cpt.value = new FieldAccessNode(graph, sfa, cpt.get(ref), obj);
	    
	} else if (expr instanceof QualifiedName) {
	    QualifiedName qn = (QualifiedName)expr;
	    SimpleName fieldName = qn.getName();
	    DFRef ref = scope.lookupField(fieldName.getIdentifier());
	    cpt = processExpression(graph, scope, cpt, qn.getQualifier());
	    DFNode obj = cpt.value;
	    cpt.value = new FieldAccessNode(graph, qn, cpt.get(ref), obj);
	    
	} else if (expr instanceof CastExpression) {
	    CastExpression cast = (CastExpression)expr;
	    Type type = cast.getType();
	    cpt = processExpression(graph, scope, cpt, cast.getExpression());
	    cpt.value = new TypeCastNode(graph, cast, type, cpt.value);
	    
	} else if (expr instanceof ClassInstanceCreation) {
	    ClassInstanceCreation cstr = (ClassInstanceCreation)expr;
	    Type instType = cstr.getType();
	    Expression expr1 = cstr.getExpression();
	    DFNode obj = null;
	    if (expr1 != null) {
		cpt = processExpression(graph, scope, cpt, expr1);
		obj = cpt.value;
	    }
	    CreateObjectNode call = new CreateObjectNode(graph, cstr, obj, instType);
	    for (Expression arg : (List<Expression>) cstr.arguments()) {
		cpt = processExpression(graph, scope, cpt, arg);
		call.take(cpt.value);
	    }
	    cpt.value = call;
	    // XXX ignore getAnonymousClassDeclaration();
	    
	} else if (expr instanceof ConditionalExpression) {
	    ConditionalExpression cond = (ConditionalExpression)expr;
	    cpt = processExpression(graph, scope, cpt, cond.getExpression());
	    cpt = processExpression(graph, scope, cpt, cond.getThenExpression());
	    cpt = processExpression(graph, scope, cpt, cond.getElseExpression());
	    // XXX conditional node
	    
	} else if (expr instanceof InstanceofExpression) {
	    InstanceofExpression instof = (InstanceofExpression)expr;
	    Type type = instof.getRightOperand();
	    cpt = processExpression(graph, scope, cpt, instof.getLeftOperand());
	    cpt.value = new InstanceofNode(graph, instof, type, cpt.value);
	    
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
    
    public DFComponent processConditional
	(DFGraph graph, DFComponent cpt, DFFrame frame,
	 Statement stmt, DFNode condValue,
	 DFComponent trueCpt, DFComponent falseCpt) {

	Set<DFRef> refs = new HashSet<DFRef>();
	if (trueCpt != null) {
	    for (Map.Entry<DFRef, DFNode> entry : trueCpt.inputs.entrySet()) {
		DFRef ref = entry.getKey();
		DFNode src = entry.getValue();
		cpt.get(ref).connect(src);
	    }
	    refs.addAll(trueCpt.outputs.keySet());
	}
	if (falseCpt != null) {
	    for (Map.Entry<DFRef, DFNode> entry : falseCpt.inputs.entrySet()) {
		DFRef ref = entry.getKey();
		DFNode src = entry.getValue();
		cpt.get(ref).connect(src);
	    }
	    refs.addAll(falseCpt.outputs.keySet());
	}
	
	for (DFRef ref : refs) {
	    JoinNode join = new JoinNode(graph, stmt, condValue, ref);
	    if (trueCpt != null) {
		join.recv(true, trueCpt.get(ref));
	    }
	    if (falseCpt != null) {
		join.recv(false, falseCpt.get(ref));
	    }
	    join.close(cpt.get(ref));
	    cpt.put(ref, join);
	}

	if (trueCpt != null) {
	    for (DFExit exit : trueCpt.exits) {
		DFRef ref = exit.ref;
		DFNode node = trueCpt.get(ref);
		JoinNode join = new JoinNode(graph, stmt, condValue, ref);
		join.recv(true, node);
		cpt.addExit(new DFExit(ref, join, exit.frame, exit.label));
	    }
	}
	if (falseCpt != null) {
	    for (DFExit exit : falseCpt.exits) {
		DFRef ref = exit.ref;
		DFNode node = falseCpt.get(ref);
		JoinNode join = new JoinNode(graph, stmt, condValue, ref);
		join.recv(false, node);
		cpt.addExit(new DFExit(ref, join, exit.frame, exit.label));
	    }
	}
	
	return cpt;
    }

    public DFComponent processLoop
	(DFGraph graph, DFComponent cpt, DFFrame frame, 
	 Statement stmt, DFNode condValue, DFComponent loopCpt)
	throws UnsupportedSyntax {

	Map<DFRef, LoopNode> loops = new HashMap<DFRef, LoopNode>();
	Map<DFRef, BranchNode> branches = new HashMap<DFRef, BranchNode>();
	Map<DFRef, DFNode> exits = new HashMap<DFRef, DFNode>();
	for (DFRef ref : frame.loopRefs) {
	    DFNode src = cpt.get(ref);
	    LoopNode loop = new LoopNode(graph, stmt, ref, src);
	    loops.put(ref, loop);
	    BranchNode branch = new BranchNode(graph, stmt, condValue);
	    branches.put(ref, branch);
	    branch.send(true, loop);
	    DFNode exit = new DistNode(graph);
	    branch.send(false, exit);
	    exits.put(ref, exit);
	}
	
	for (Map.Entry<DFRef, DFNode> entry : loopCpt.inputs.entrySet()) {
	    DFRef ref = entry.getKey();
	    DFNode input = entry.getValue();
	    LoopNode loop = loops.get(ref);
	    if (loop != null) {
		loop.connect(input);
	    } else {
		DFNode src = cpt.get(ref);
		src.connect(input);
	    }
	}
	for (Map.Entry<DFRef, DFNode> entry : loopCpt.outputs.entrySet()) {
	    DFRef ref = entry.getKey();
	    DFNode output = entry.getValue();
	    BranchNode branch = branches.get(ref);
	    if (branch != null) {
		output.connect(branch);
		DFNode exit = exits.get(ref);
		cpt.put(ref, exit);
	    } else {
		cpt.put(ref, output);
	    }
	}
	
	return cpt;
    }

    @SuppressWarnings("unchecked")
    public DFComponent processVariableDeclarationStatement
	(DFGraph graph, DFScopeMap map, DFFrame frame, DFComponent cpt,
	 VariableDeclarationStatement varStmt)
	throws UnsupportedSyntax {
	DFScope scope = map.get(varStmt);
	return processVariableDeclaration
	    (graph, scope, cpt, varStmt.fragments());
    }

    public DFComponent processExpressionStatement
	(DFGraph graph, DFScopeMap map, DFFrame frame, DFComponent cpt,
	 ExpressionStatement exprStmt)
	throws UnsupportedSyntax {
	DFScope scope = map.get(exprStmt);
	Expression expr = exprStmt.getExpression();
	return processExpression(graph, scope, cpt, expr);
    }

    public DFComponent processReturnStatement
	(DFGraph graph, DFScopeMap map, DFFrame frame, DFComponent cpt,
	 ReturnStatement rtrnStmt)
	throws UnsupportedSyntax {
	DFScope scope = map.get(rtrnStmt);
	Expression expr = rtrnStmt.getExpression();
	DFNode value = null;
	if (expr != null) {
	    cpt = processExpression(graph, scope, cpt, expr);
	    value = cpt.value;
	}
	DFNode rtrn = new ReturnNode(graph, rtrnStmt, value);
	DFRef ref = scope.lookupReturn();
	cpt.put(ref, rtrn);
	return cpt;
    }
    
    public DFComponent processIfStatement
	(DFGraph graph, DFScopeMap map, DFFrame frame, DFComponent cpt,
	 IfStatement ifStmt)
	throws UnsupportedSyntax {
	DFScope scope = map.get(ifStmt);
	Expression expr = ifStmt.getExpression();
	cpt = processExpression(graph, scope, cpt, expr);
	DFNode condValue = cpt.value;
	
	Statement thenStmt = ifStmt.getThenStatement();
	DFComponent thenCpt = new DFComponent(graph);
	thenCpt = processStatement(graph, map, frame, thenCpt, thenStmt);
	
	Statement elseStmt = ifStmt.getElseStatement();
	DFFrame elseFrame = null;
	DFComponent elseCpt = null;
	if (elseStmt != null) {
	    elseCpt = new DFComponent(graph);
	    elseCpt = processStatement(graph, map, frame, elseCpt, elseStmt);
	}

	return processConditional(graph, cpt, frame,
				  ifStmt, condValue,
				  thenCpt, elseCpt);
    }
	
    public DFComponent processWhileStatement
	(DFGraph graph, DFScopeMap map, DFFrame frame, DFComponent cpt,
	 WhileStatement whileStmt)
	throws UnsupportedSyntax {
	DFScope scope = map.get(whileStmt);
	// Create a new frame.
	frame = new DFFrame(frame, scope.getLoopRefs());
	DFComponent loopCpt = new DFComponent(graph);
	loopCpt = processExpression(graph, scope, loopCpt,
				    whileStmt.getExpression());
	DFNode condValue = loopCpt.value;
	loopCpt = processStatement(graph, map, frame, loopCpt,
				   whileStmt.getBody());
	return processLoop(graph, cpt, frame, whileStmt,
			   condValue, loopCpt);
    }
    
    @SuppressWarnings("unchecked")
    public DFComponent processForStatement
	(DFGraph graph, DFScopeMap map, DFFrame frame, DFComponent cpt,
	 ForStatement forStmt)
	throws UnsupportedSyntax {
	DFScope scope = map.get(forStmt);
	// Create a new frame.
	frame = new DFFrame(frame, scope.getLoopRefs());
	for (Expression init : (List<Expression>) forStmt.initializers()) {
	    cpt = processExpression(graph, scope, cpt, init);
	}
	
	DFComponent loopCpt = new DFComponent(graph);
	Expression expr = forStmt.getExpression();
	DFNode condValue = loopCpt.value;
	if (expr != null) {
	    loopCpt = processExpression(graph, scope, loopCpt, expr);
	    condValue = loopCpt.value;
	} else {
	    condValue = new ConstNode(graph, null, "true");
	}
	loopCpt = processStatement(graph, map, frame, loopCpt,
				   forStmt.getBody());
	for (Expression update : (List<Expression>) forStmt.updaters()) {
	    loopCpt = processExpression(graph, scope, loopCpt, update);
	}
	
	return processLoop(graph, cpt, frame, forStmt,
			   condValue, loopCpt);
    }
    
    @SuppressWarnings("unchecked")
    public DFComponent processStatement
	(DFGraph graph, DFScopeMap map, DFFrame frame, DFComponent cpt,
	 Statement stmt)
	throws UnsupportedSyntax {
	
	if (stmt instanceof AssertStatement) {
	    // Ignore assert.
	} else if (stmt instanceof Block) {
	    DFScope scope = map.get(stmt);
	    Block block = (Block)stmt;
	    for (Statement cstmt : (List<Statement>) block.statements()) {
		cpt = processStatement(graph, map, frame, cpt, cstmt);
	    }
	    scope.finish(cpt);

	} else if (stmt instanceof EmptyStatement) {
	    
	} else if (stmt instanceof VariableDeclarationStatement) {
	    cpt = processVariableDeclarationStatement
		(graph, map, frame, cpt, (VariableDeclarationStatement)stmt);

	} else if (stmt instanceof ExpressionStatement) {
	    cpt = processExpressionStatement
		(graph, map, frame, cpt, (ExpressionStatement)stmt);
		
	} else if (stmt instanceof ReturnStatement) {
	    cpt = processReturnStatement
		(graph, map, frame, cpt, (ReturnStatement)stmt);
	    
	} else if (stmt instanceof IfStatement) {
	    cpt = processIfStatement
		(graph, map, frame, cpt, (IfStatement)stmt);
	    
	} else if (stmt instanceof SwitchStatement) {
	    // XXX switch
	    DFScope scope = map.get(stmt);
	    SwitchStatement switchStmt = (SwitchStatement)stmt;
	    cpt = processExpression(graph, scope, cpt, switchStmt.getExpression());
	    for (Statement cstmt : (List<Statement>) switchStmt.statements()) {
		cpt = processStatement(graph, map, frame, cpt, cstmt);
	    }
	    scope.finish(cpt);
	    
	} else if (stmt instanceof SwitchCase) {
	    // XXX case
	    DFScope scope = map.get(stmt);
	    SwitchCase switchCase = (SwitchCase)stmt;
	    Expression expr = switchCase.getExpression();
	    if (expr != null) {
		cpt = processExpression(graph, scope, cpt, expr);
	    }
	    
	} else if (stmt instanceof WhileStatement) {
	    cpt = processWhileStatement
		(graph, map, frame, cpt, (WhileStatement)stmt);
	    
	} else if (stmt instanceof DoStatement) {
	    DoStatement doStmt = (DoStatement)stmt;
	    // XXX do
	    // doStmt.getBody();
	    // doStmt.getExpression();
	    
	} else if (stmt instanceof ForStatement) {
	    DFScope scope = map.get(stmt);
	    cpt = processForStatement
		(graph, map, frame, cpt, (ForStatement)stmt);
	    scope.finish(cpt);
	    
	} else if (stmt instanceof EnhancedForStatement) {
	    DFScope scope = map.get(stmt);
	    // XXX cpt = processEForStatement(graph, map, cpt, (EnhancedForStatement)stmt);
	    scope.finish(cpt);
	    
	} else if (stmt instanceof BreakStatement) {
	    // XXX ignore label (for now).
	    BreakStatement breakStmt = (BreakStatement)stmt;
	    // SimpleName labelName = breakStmt.getLabel();
	    if (frame != null) {
		for (DFRef ref : frame.loopRefs) {
		    cpt.jump(ref, frame, DFLabel.BREAK);
		}
	    }
	    
	} else if (stmt instanceof ContinueStatement) {
	    // XXX ignore label (for now).
	    ContinueStatement contStmt = (ContinueStatement)stmt;
	    // SimpleName labelName = contStmt.getLabel();
	    if (frame != null) {
		for (DFRef ref : frame.loopRefs) {
		    cpt.jump(ref, frame, DFLabel.CONTINUE);
		}
	    }
	    
	} else if (stmt instanceof LabeledStatement) {
	    // XXX ignore label (for now).
	    LabeledStatement labeledStmt = (LabeledStatement)stmt;
	    // SimpleName labelName = labeledStmt.getLabel();
	    cpt = processStatement(graph, map, frame, cpt,
				   labeledStmt.getBody());
	    
	} else if (stmt instanceof SynchronizedStatement) {
	    // Ignore synchronized.
	    SynchronizedStatement syncStmt = (SynchronizedStatement)stmt;
	    cpt = processStatement(graph, map, frame, cpt,
				   syncStmt.getBody());

	} else if (stmt instanceof TryStatement) {
	    // XXX Ignore try...catch (for now).
	    TryStatement tryStmt = (TryStatement)stmt;
	    cpt = processStatement(graph, map, frame, cpt,
				   tryStmt.getBody());
	    Block finBlock = tryStmt.getFinally();
	    if (finBlock != null) {
		cpt = processStatement(graph, map, frame, cpt, finBlock);
	    }
	    
	} else if (stmt instanceof ThrowStatement) {
	    // XXX Ignore throw (for now).
	    DFScope scope = map.get(stmt);
	    ThrowStatement throwStmt = (ThrowStatement)stmt;
	    cpt = processExpression(graph, scope, cpt, throwStmt.getExpression());
	    
	} else if (stmt instanceof ConstructorInvocation) {
	    // XXX ignore all side effects.
	    DFScope scope = map.get(stmt);
	    ConstructorInvocation ci = (ConstructorInvocation)stmt;
	    for (Expression arg : (List<Expression>) ci.arguments()) {
		cpt = processExpression(graph, scope, cpt, arg);
	    }
	    
	} else if (stmt instanceof SuperConstructorInvocation) {
	    // XXX ignore all side effects.
	    DFScope scope = map.get(stmt);
	    SuperConstructorInvocation sci = (SuperConstructorInvocation)stmt;
	    for (Expression arg : (List<Expression>) sci.arguments()) {
		cpt = processExpression(graph, scope, cpt, arg);
	    }
		
	} else {
	    // TypeDeclarationStatement
	    
	    throw new UnsupportedSyntax(stmt);
	}

	cpt.dump();
	return cpt;
    }

    @SuppressWarnings("unchecked")
    public void buildScope(DFScopeMap map, DFScope scope, Statement ast)
	throws UnsupportedSyntax {
	
	if (ast instanceof AssertStatement) {

	} else if (ast instanceof Block) {
	    Block block = (Block)ast;
	    // Create a new scope.
	    scope = new DFScope(scope);
	    for (Statement stmt :
		     (List<Statement>) block.statements()) {
		buildScope(map, scope, stmt);
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
		    buildScope(map, scope, expr);
		}
	    }

	} else if (ast instanceof ExpressionStatement) {
	    ExpressionStatement exprStmt = (ExpressionStatement)ast;
	    Expression expr = exprStmt.getExpression();
	    buildScope(map, scope, expr);
	    
	} else if (ast instanceof ReturnStatement) {
	    ReturnStatement returnStmt = (ReturnStatement)ast;
	    Expression expr = returnStmt.getExpression();
	    if (expr != null) {
		buildScope(map, scope, expr);
	    }
	    
	} else if (ast instanceof IfStatement) {
	    IfStatement ifStmt = (IfStatement)ast;
	    Expression expr = ifStmt.getExpression();
	    buildScope(map, scope, expr);
	    Statement thenStmt = ifStmt.getThenStatement();
	    buildScope(map, scope, thenStmt);
	    Statement elseStmt = ifStmt.getElseStatement();
	    if (elseStmt != null) {
		buildScope(map, scope, elseStmt);
	    }
	    
	} else if (ast instanceof SwitchStatement) {
	    SwitchStatement switchStmt = (SwitchStatement)ast;
	    // Create a new scope.
	    scope = new DFScope(scope);
	    Expression expr = switchStmt.getExpression();
	    buildScope(map, scope, expr);
	    for (Statement stmt :
		     (List<Statement>) switchStmt.statements()) {
		buildScope(map, scope, stmt);
	    }
	    
	} else if (ast instanceof SwitchCase) {
	    SwitchCase switchCase = (SwitchCase)ast;
	    Expression expr = switchCase.getExpression();
	    if (expr != null) {
		buildScope(map, scope, expr);
	    }
	    
	} else if (ast instanceof WhileStatement) {
	    WhileStatement whileStmt = (WhileStatement)ast;
	    // Create a new scope.
	    scope = new DFScope(scope);
	    Expression expr = whileStmt.getExpression();
	    buildScope(map, scope, expr);
	    Statement stmt = whileStmt.getBody();
	    buildScope(map, scope, stmt);
	    
	} else if (ast instanceof DoStatement) {
	    DoStatement doStmt = (DoStatement)ast;
	    // Create a new scope.
	    scope = new DFScope(scope);
	    Statement stmt = doStmt.getBody();
	    buildScope(map, scope, stmt);
	    Expression expr = doStmt.getExpression();
	    buildScope(map, scope, expr);
	    
	} else if (ast instanceof ForStatement) {
	    ForStatement forStmt = (ForStatement)ast;
	    // Create a new scope.
	    scope = new DFScope(scope);
	    for (Expression init :
		     (List<Expression>) forStmt.initializers()) {
		buildScope(map, scope, init);
	    }
	    Expression expr = forStmt.getExpression();
	    if (expr != null) {
		buildScope(map, scope, expr);
	    }
	    Statement stmt = forStmt.getBody();
	    buildScope(map, scope, stmt);
	    for (Expression update :
		     (List<Expression>) forStmt.updaters()) {
		buildScope(map, scope, update);
	    }
	    
	} else if (ast instanceof EnhancedForStatement) {
	    EnhancedForStatement eForStmt = (EnhancedForStatement)ast;
	    // Create a new scope.
	    scope = new DFScope(scope);
	    SingleVariableDeclaration decl = eForStmt.getParameter();
	    // XXX ignore modifiers and dimensions.
	    Type varType = decl.getType();
	    SimpleName varName = decl.getName();
	    scope.add(varName.getIdentifier(), varType);
	    Expression expr = eForStmt.getExpression();
	    if (expr != null) {
		buildScope(map, scope, expr);
	    }
	    Statement stmt = eForStmt.getBody();
	    buildScope(map, scope, stmt);
	    
	} else if (ast instanceof BreakStatement) {
	    
	} else if (ast instanceof ContinueStatement) {
	    
	} else if (ast instanceof LabeledStatement) {
	    LabeledStatement labeledStmt = (LabeledStatement)ast;
	    Statement stmt = labeledStmt.getBody();
	    buildScope(map, scope, stmt);
	    
	} else if (ast instanceof SynchronizedStatement) {
	    SynchronizedStatement syncStmt = (SynchronizedStatement)ast;
	    Block block = syncStmt.getBody();
	    buildScope(map, scope, block);

	} else if (ast instanceof TryStatement) {
	    TryStatement tryStmt = (TryStatement)ast;
	    Block block = tryStmt.getBody();
	    buildScope(map, scope, block);
	    for (CatchClause cc :
		     (List<CatchClause>) tryStmt.catchClauses()) {
		// Create a new scope.
		DFScope child = new DFScope(scope);
		SingleVariableDeclaration decl = cc.getException();
		// XXX ignore modifiers and dimensions.
		Type varType = decl.getType();
		SimpleName varName = decl.getName();
		child.add(varName.getIdentifier(), varType);
		buildScope(map, child, cc.getBody());
		map.put(cc, child);
	    }
	    Block finBlock = tryStmt.getFinally();
	    if (finBlock != null) {
		buildScope(map, scope, finBlock);
	    }
	    
	} else if (ast instanceof ThrowStatement) {
	    ThrowStatement throwStmt = (ThrowStatement)ast;
	    Expression expr = throwStmt.getExpression();
	    if (expr != null) {
		buildScope(map, scope, expr);
	    }
	    
	} else if (ast instanceof ConstructorInvocation) {
	    ConstructorInvocation ci = (ConstructorInvocation)ast;
	    for (Expression expr :
		     (List<Expression>) ci.arguments()) {
		buildScope(map, scope, expr);
	    }
	    
	} else if (ast instanceof SuperConstructorInvocation) {
	    SuperConstructorInvocation sci = (SuperConstructorInvocation)ast;
	    for (Expression expr :
		     (List<Expression>) sci.arguments()) {
		buildScope(map, scope, expr);
	    }
	} else {
	    // TypeDeclarationStatement
	    throw new UnsupportedSyntax(ast);
	    
	}
	
	map.put(ast, scope);
    }
	
    @SuppressWarnings("unchecked")
    public void buildScope(DFScopeMap map, DFScope scope, Expression ast)
	throws UnsupportedSyntax {
	
	if (ast instanceof Annotation) {

	} else if (ast instanceof SimpleName) {
	    SimpleName varName = (SimpleName)ast;
	    DFRef ref = scope.lookup(varName.getIdentifier());
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
	    buildScope(map, scope, operand);
	    if (op == PrefixExpression.Operator.INCREMENT ||
		op == PrefixExpression.Operator.DECREMENT) {
		buildScopeLeft(map, scope, operand);
	    }
	    
	} else if (ast instanceof PostfixExpression) {
	    PostfixExpression postfix = (PostfixExpression)ast;
	    PostfixExpression.Operator op = postfix.getOperator();
	    Expression operand = postfix.getOperand();
	    buildScope(map, scope, operand);
	    if (op == PostfixExpression.Operator.INCREMENT ||
		op == PostfixExpression.Operator.DECREMENT) {
		buildScopeLeft(map, scope, operand);
	    }
	    
	} else if (ast instanceof InfixExpression) {
	    InfixExpression infix = (InfixExpression)ast;
	    InfixExpression.Operator op = infix.getOperator();
	    Expression loperand = infix.getLeftOperand();
	    buildScope(map, scope, loperand);
	    Expression roperand = infix.getRightOperand();
	    buildScope(map, scope, roperand);
    
	} else if (ast instanceof ParenthesizedExpression) {
	    ParenthesizedExpression paren = (ParenthesizedExpression)ast;
	    buildScope(map, scope, paren.getExpression());
	    
	} else if (ast instanceof Assignment) {
	    Assignment assn = (Assignment)ast;
	    Assignment.Operator op = assn.getOperator();
	    buildScopeLeft(map, scope, assn.getLeftHandSide());
	    if (op != Assignment.Operator.ASSIGN) {
		buildScope(map, scope, assn.getLeftHandSide());
	    }
	    buildScope(map, scope, assn.getRightHandSide());

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
		    buildScope(map, scope, expr);
		    scope.addOutput(ref);
		}
	    }

	} else if (ast instanceof MethodInvocation) {
	    MethodInvocation invoke = (MethodInvocation)ast;
	    Expression expr = invoke.getExpression();
	    if (expr != null) {
		buildScope(map, scope, expr);
	    }
	    for (Expression arg :
		     (List<Expression>) invoke.arguments()) {
		buildScope(map, scope, arg);
	    }
	    
	} else if (ast instanceof SuperMethodInvocation) {
	    SuperMethodInvocation si = (SuperMethodInvocation)ast;
	    for (Expression arg :
		     (List<Expression>) si.arguments()) {
		buildScope(map, scope, arg);
	    }
	    
	} else if (ast instanceof ArrayCreation) {
	    ArrayCreation ac = (ArrayCreation)ast;
	    for (Expression dim :
		     (List<Expression>) ac.dimensions()) {
		buildScope(map, scope, dim);
	    }
	    ArrayInitializer init = ac.getInitializer();
	    if (init != null) {
		buildScope(map, scope, init);
	    }
	    
	} else if (ast instanceof ArrayInitializer) {
	    ArrayInitializer init = (ArrayInitializer)ast;
	    for (Expression expr :
		     (List<Expression>) init.expressions()) {
		buildScope(map, scope, expr);
	    }
	    
	} else if (ast instanceof ArrayAccess) {
	    ArrayAccess aa = (ArrayAccess)ast;
	    buildScope(map, scope, aa.getArray());
	    buildScope(map, scope, aa.getIndex());
	    DFRef ref = scope.lookupArray();
	    scope.addInput(ref);
	    
	} else if (ast instanceof FieldAccess) {
	    FieldAccess fa = (FieldAccess)ast;
	    SimpleName fieldName = fa.getName();
	    buildScope(map, scope, fa.getExpression());
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
	    buildScope(map, scope, qn.getQualifier());
	    scope.addInput(ref);
	    
	} else if (ast instanceof CastExpression) {
	    CastExpression cast = (CastExpression)ast;
	    buildScope(map, scope, cast.getExpression());
	    
	} else if (ast instanceof ClassInstanceCreation) {
	    ClassInstanceCreation cstr = (ClassInstanceCreation)ast;
	    Expression expr = cstr.getExpression();
	    if (expr != null) {
		buildScope(map, scope, expr);
	    }
	    for (Expression arg :
		     (List<Expression>) cstr.arguments()) {
		buildScope(map, scope, arg);
	    }
	    ASTNode anon = cstr.getAnonymousClassDeclaration();
	    if (anon != null) {
		throw new UnsupportedSyntax(anon);
	    }
	    
	} else if (ast instanceof ConditionalExpression) {
	    ConditionalExpression cond = (ConditionalExpression)ast;
	    buildScope(map, scope, cond.getExpression());
	    buildScope(map, scope, cond.getThenExpression());
	    buildScope(map, scope, cond.getElseExpression());
	    
	} else if (ast instanceof InstanceofExpression) {
	    InstanceofExpression instof = (InstanceofExpression)ast;
	    buildScope(map, scope, instof.getLeftOperand());
	    
	} else {
	    // AnonymousClassDeclaration
	    // LambdaExpression
	    // MethodReference
	    //  CreationReference
	    //  ExpressionMethodReference
	    //  SuperMethodReference
	    //  TypeMethodReference
	    throw new UnsupportedSyntax(ast);
	    
	}

	map.put(ast, scope);
    }

    @SuppressWarnings("unchecked")
    public void buildScopeLeft(DFScopeMap map, DFScope scope, Expression ast)
	throws UnsupportedSyntax {
	
	if (ast instanceof SimpleName) {
	    SimpleName varName = (SimpleName)ast;
	    DFRef ref = scope.lookup(varName.getIdentifier());
	    scope.addOutput(ref);
	    
	} else if (ast instanceof ArrayAccess) {
	    ArrayAccess aa = (ArrayAccess)ast;
	    buildScope(map, scope, aa.getArray());
	    buildScope(map, scope, aa.getIndex());
	    DFRef ref = scope.lookupArray();
	    scope.addOutput(ref);
	    
	} else if (ast instanceof FieldAccess) {
	    FieldAccess fa = (FieldAccess)ast;
	    SimpleName fieldName = fa.getName();
	    buildScope(map, scope, fa.getExpression());
	    DFRef ref = scope.lookupField(fieldName.getIdentifier());
	    scope.addOutput(ref);
	    
	} else if (ast instanceof QualifiedName) {
	    QualifiedName qn = (QualifiedName)ast;
	    SimpleName fieldName = qn.getName();
	    DFRef ref = scope.lookupField(fieldName.getIdentifier());
	    buildScope(map, scope, qn.getQualifier());
	    scope.addOutput(ref);
	    
	} else {
	    throw new UnsupportedSyntax(ast);
	    
	}

	map.put(ast, scope);
    }
    
    @SuppressWarnings("unchecked")
    public DFComponent buildMethodDeclaration
	(DFGraph graph, DFScope scope, MethodDeclaration method)
	throws UnsupportedSyntax {
	
	DFComponent cpt = new DFComponent(graph);
	// XXX ignore isContructor()
	// XXX ignore getReturnType2()
	int i = 0;
	// XXX ignore isVarargs()
	for (SingleVariableDeclaration decl :
		 (List<SingleVariableDeclaration>) method.parameters()) {
	    DFNode param = new ArgNode(graph, decl, i++);
	    SimpleName paramName = decl.getName();
	    // XXX ignore modifiers and dimensions.
	    Type paramType = decl.getType();
	    DFRef var = scope.add(paramName.getIdentifier(), paramType);
	    AssignNode assign = new SingleAssignNode(graph, decl, var);
	    assign.take(param);
	    cpt.put(assign.ref, assign);
	}
	return cpt;
    }
    
    public DFGraph getMethodGraph(MethodDeclaration method)
	throws UnsupportedSyntax {
	String funcName = method.getName().getFullyQualifiedName();
	Block funcBlock = method.getBody();
	// Ignore method prototypes.
	if (funcBlock == null) return null;
				   
	DFGraph graph = new DFGraph(funcName);
	DFScope scope = new DFScope();
	
	// Setup an initial scope.
	DFComponent cpt = buildMethodDeclaration(graph, scope, method);
	DFScopeMap map = new DFScopeMap();
	buildScope(map, scope, funcBlock);
	scope.dump();

	// Process the function body.
	cpt = processStatement(graph, map, null, cpt, funcBlock);

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
