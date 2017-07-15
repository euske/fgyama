//  Java2DF.java
//
package net.tabesugi.dfaaa;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


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
	return ("<DFRef("+this.label()+")>");
    }

    public String label() {
	return ((this.scope == null)?
		this.name :
		this.scope.name+"."+this.name);
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
    public List<DFNode> nodes;
    public List<DFScope> children;

    public Map<String, DFVar> vars;
    public Set<DFRef> inputs;
    public Set<DFRef> outputs;

    public static int baseId = 0;
    public static int genId() {
	return baseId++;
    }

    public DFScope(String name) {
	this(name, null);
    }

    public DFScope(DFScope parent) {
	this("S"+genId(), parent);
    }

    public static DFRef THIS = new DFRef(null, "THIS");
    public static DFRef SUPER = new DFRef(null, "SUPER");
    public static DFRef RETURN = new DFRef(null, "RETURN");
    public static DFRef ARRAY = new DFRef(null, "[]");
    
    public DFScope(String name, DFScope parent) {
	this.name = name;
	this.parent = parent;
	if (parent != null) {
	    parent.children.add(this);
	}
	this.nodes = new ArrayList<DFNode>();
	this.children = new ArrayList<DFScope>();
	this.vars = new HashMap<String, DFVar>();
	this.inputs = new HashSet<DFRef>();
	this.outputs = new HashSet<DFRef>();
    }

    public String toString() {
	return ("<DFScope("+this.name+")>");
    }

    public int addNode(DFNode node) {
	this.nodes.add(node);
	return this.nodes.size();
    }

    public void removeNode(DFNode node) {
        this.nodes.remove(node);
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

    public DFRef lookupVar(String name) {
	DFVar var = this.vars.get(name);
	if (var != null) {
	    return var;
	} else if (this.parent != null) {
	    return this.parent.lookupVar(name);
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
	return this.lookupVar("."+name);
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

    public void cleanup() {
        List<DFNode> removed = new ArrayList<DFNode>();
        for (DFNode node : this.nodes) {
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
	for (DFScope child : this.children) {
	    child.cleanup();
	}
    }			   
}


//  DFNodeType
//
enum DFNodeType {
    None,
    Constant,
    Operator,
    Assign,
    Branch,
    Join,
    Loop,
    Terminal,
}


//  DFLinkType
//
enum DFLinkType {
    None,
    DataFlow,
    BackFlow,
    ControlFlow,
    Informational,
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


//  DFNode
//
abstract class DFNode {

    public DFScope scope;
    public DFRef ref;
    public int id;
    public String name;
    public List<DFLink> send;
    public List<DFLink> recv;
    
    public static int baseId = 0;
    public static int genId() {
	return baseId++;
    }

    public DFNode(DFScope scope, DFRef ref) {
	this.scope = scope;
	this.ref = ref;
	this.id = genId();
	this.send = new ArrayList<DFLink>();
	this.recv = new ArrayList<DFLink>();
	this.scope.addNode(this);
    }

    public String toString() {
	return ("<DFNode("+this.name()+") "+this.label()+">");
    }

    public String name() {
	return ("N"+this.scope.name+"_"+id);
    }

    abstract public DFNodeType type();

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
        this.scope.removeNode(this);
    }
}

// DistNode: a DFNode that distributes a value to multiple nodes.
class DistNode extends DFNode {

    public DistNode(DFScope scope, DFRef ref) {
	super(scope, ref);
    }

    public DFNodeType type() {
	return DFNodeType.None;
    }

    public String label() {
	return null;
    }
}

// ProgNode: a DFNode that corresponds to an actual program point.
abstract class ProgNode extends DFNode {

    public ASTNode ast;
    
    public ProgNode(DFScope scope, DFRef ref, ASTNode ast) {
	super(scope, ref);
	this.ast = ast;
    }
}

// AssignNode: corresponds to a certain location in a memory.
abstract class AssignNode extends ProgNode {

    public AssignNode(DFScope scope, DFRef ref, ASTNode ast) {
	super(scope, ref, ast);
    }

    public DFNodeType type() {
	return DFNodeType.Assign;
    }

    public String label() {
	return "assign:"+this.ref.name;
    }

    abstract public void take(DFNode value);
}

// SingleAssignNode:
class SingleAssignNode extends AssignNode {

    public DFNode value;
    
    public SingleAssignNode(DFScope scope, DFRef ref, ASTNode ast) {
	super(scope, ref, ast);
    }

    public void take(DFNode value) {
	this.value = value;
	value.connect(this);
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
	array.connect(this, "array");
	index.connect(this, "index");
    }
}

// FieldAssignNode:
class FieldAssignNode extends SingleAssignNode {

    public DFNode obj;

    public FieldAssignNode(DFScope scope, DFRef ref, ASTNode ast,
			   DFNode obj) {
	super(scope, ref, ast);
	this.obj = obj;
	obj.connect(this, "index");
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
	array.connect(this, "array");
	value.connect(this, "value");
	index.connect(this, "index");
    }

    public DFNodeType type() {
	return DFNodeType.Operator;
    }

    public String label() {
	return "array:"+this.ref.name;
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
	value.connect(this, "value");
	obj.connect(this, "index");
    }

    public DFNodeType type() {
	return DFNodeType.Operator;
    }

    public String label() {
	return ".";
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
	value.connect(this);
    }

    public DFNodeType type() {
	return DFNodeType.Operator;
    }

    public String label() {
	if (this.ref != null) {
	    return this.op.toString()+":"+this.ref.name;
	} else {
	    return this.op.toString();
	}
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
	value.connect(this);
    }

    public DFNodeType type() {
	return DFNodeType.Operator;
    }

    public String label() {
	if (this.ref != null) {
	    return this.op.toString()+":"+this.ref.name;
	} else {
	    return this.op.toString();
	}
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

    public DFNodeType type() {
	return DFNodeType.Constant;
    }

    public String label() {
	return "="+this.value;
    }
}

// ArrayValueNode: represents an array.
class ArrayValueNode extends ProgNode {

    public List<DFNode> values;

    public ArrayValueNode(DFScope scope, ASTNode ast) {
	super(scope, null, ast);
	this.values = new ArrayList<DFNode>();
    }

    public DFNodeType type() {
	return DFNodeType.Constant;
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
	lvalue.connect(this, "L");
	rvalue.connect(this, "R");
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
	value.connect(this);
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
	value.connect(this);
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
	value.connect(this);
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
	node.connect(this);
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
	lvalue.connect(this, "L");
	rvalue.connect(this, "R");
    }

    public DFNodeType type() {
	return DFNodeType.Operator;
    }

    public String label() {
	if (this.ref != null) {
	    return this.op.toString()+":"+this.ref.name;
	} else {
	    return this.op.toString();
	}
    }
}

// CondNode
abstract class CondNode extends ProgNode {
    
    public DFNode value;
    
    public CondNode(DFScope scope, DFRef ref, ASTNode ast,
		    DFNode value) {
	super(scope, ref, ast);
	this.value = value;
	value.connect(this, DFLinkType.ControlFlow, "cond");
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
	if (this.ref != null) {
	    return "branch:"+this.ref.name;
	} else {
	    return "branch";
	}
    }

    public void open(DFNode loop, DFNode exit) {
	this.connect(loop, DFLinkType.BackFlow, "true");
	this.connect(exit, "false");
    }
}

// JoinNode
class JoinNode extends CondNode {

    public boolean recvTrue = false;
    public boolean recvFalse = false;
    
    public JoinNode(DFScope scope, DFRef ref, ASTNode ast,
		    DFNode value) {
	super(scope, ref, ast, value);
    }
    
    public DFNodeType type() {
	return DFNodeType.Branch;
    }

    public String label() {
	if (this.ref != null) {
	    return "join:"+this.ref.name;
	} else {
	    return "join";
	}
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

    public DFNode enter;
    
    public LoopNode(DFScope scope, DFRef ref, ASTNode ast,
		    DFNode enter) {
	super(scope, ref, ast);
	this.enter = enter;
	enter.connect(this, "enter");
    }
    
    public DFNodeType type() {
	return DFNodeType.Loop;
    }
    
    public String label() {
	if (this.ref != null) {
	    return "loop:"+this.ref.name;
	} else {
	    return "loop";
	}
    }
}

// IterNode
class IterNode extends ProgNode {

    public DFNode list;
    
    public IterNode(DFScope scope, DFRef ref, ASTNode ast,
		    DFNode list) {
	super(scope, ref, ast);
	this.list = list;
	list.connect(this);
    }
    
    public DFNodeType type() {
	return DFNodeType.Operator;
    }

    public String label() {
	return "iter:"+this.ref.name;
    }
}

// CallNode
abstract class CallNode extends ProgNode {

    public DFNode obj;
    public List<DFNode> args;

    public CallNode(DFScope scope, DFRef ref, ASTNode ast,
		    DFNode obj) {
	super(scope, ref, ast);
	this.obj = obj;
	this.args = new ArrayList<DFNode>();
	if (obj != null) {
	    obj.connect(this, "index");
	}
    }

    public DFNodeType type() {
	return DFNodeType.Operator;
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
	super(scope, null, ast, obj); // 
	this.type = type;
    }
    
    public String label() {
	return "new "+Utils.getTypeName(this.type);
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


//  DFMeet
//
class DFMeet {

    public DFNode node;
    public DFFrame frame;
    public DFLabel label;

    public DFMeet(DFNode node, DFFrame frame, DFLabel label) {
	this.node = node;
	this.frame = frame;
	this.label = label;
    }

    public String toString() {
	return (this.node+" -> "+this.frame+":"+this.label);
    }
}


//  DFComponent
//
class DFComponent {

    public DFScope scope;
    public Map<DFRef, DFNode> inputs;
    public Map<DFRef, DFNode> outputs;
    public List<DFMeet> meets;
    public DFNode value;
    public AssignNode assign;
    
    public DFComponent(DFScope scope) {
	this.scope = scope;
	this.inputs = new HashMap<DFRef, DFNode>();
	this.outputs = new HashMap<DFRef, DFNode>();
	this.meets = new ArrayList<DFMeet>();
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
	for (DFMeet meet : this.meets) {
	    out.println("  meet: "+meet);
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
		node = new DistNode(this.scope, ref);
		this.inputs.put(ref, node);
	    }
	}
	return node;
    }

    public void put(DFNode node) {
	this.outputs.put(node.ref, node);
    }

    public void jump(DFRef ref, DFFrame frame, DFLabel label) {
	DFNode node = this.get(ref);
	this.addMeet(new DFMeet(node, frame, label));
	this.outputs.remove(ref);
    }

    public void addMeet(DFMeet meet) {
	this.meets.add(meet);
    }

    public void removeRef(DFRef ref) {
	this.inputs.remove(ref);
	this.outputs.remove(ref);
	List<DFMeet> removed = new ArrayList<DFMeet>();
	for (DFMeet meet : this.meets) {
	    if (meet.node.ref == ref) {
		removed.add(meet);
	    }
	}
	this.meets.removeAll(removed);
    }
}


//  TextExporter
//
class TextExporter {

    public BufferedWriter writer;
    
    public TextExporter(OutputStream stream) {
	this.writer = new BufferedWriter(new OutputStreamWriter(stream));
    }

    public void startFile(String path)
	throws IOException {
	this.writer.write("#"+path+"\n");
	this.writer.flush();
    }

    public void writeFailure(String funcName, String astName)
	throws IOException {
	this.writer.write("!"+funcName+","+astName+"\n");
	this.writer.flush();
    }
    
    public void writeGraph(DFScope scope)
	throws IOException {
	if (scope.parent == null) {
	    this.writer.write("@"+scope.name+"\n");
	} else {
	    this.writer.write(":"+scope.name+","+scope.parent.name+"\n");
	}
	for (DFNode node : scope.nodes) {
	    this.writer.write("+"+scope.name);
	    this.writer.write(","+node.name());
	    this.writer.write(","+node.type().ordinal());
	    String label = node.label();
	    if (label != null) {
		this.writer.write(","+Utils.sanitize(label));
	    } else {
		this.writer.write(",");
	    }
	    if (node.ref != null) {
		this.writer.write(","+node.ref.label());
	    } else {
		this.writer.write(",");
	    }
	    if (node instanceof ProgNode) {
		ProgNode prognode = (ProgNode)node;
		ASTNode ast = prognode.ast;
		if (ast != null) {
		    int type = ast.getNodeType();
		    int start = ast.getStartPosition();
		    int length = ast.getLength();
		    this.writer.write(","+type+","+start+","+length);
		}
	    }
	    this.writer.newLine();
	}
	for (DFNode node : scope.nodes) {
	    for (DFLink link : node.send) {
		this.writer.write("-"+link.src.name()+","+link.dst.name());
		this.writer.write(","+link.type.ordinal());
		if (link.name != null) {
		    this.writer.write(","+link.name);;
		}
		this.writer.newLine();
	    }
	}
	for (DFScope child : scope.children) {
	    this.writeGraph(child);
	}
	if (scope.parent == null) {
	    this.writer.newLine();
	}
	this.writer.flush();
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

    
//  Java2DF
// 
public class Java2DF extends ASTVisitor {

    class UnsupportedSyntax extends Exception {

	static final long serialVersionUID = 1L;

	public ASTNode ast;
    
	public UnsupportedSyntax(ASTNode ast) {
	    this.ast = ast;
	}
    }

    // Instance methods.
    
    public TextExporter exporter;

    public Java2DF(TextExporter exporter) {
	this.exporter = exporter;
    }

    public DFComponent processConditional
	(DFScope scope, DFComponent cpt, ASTNode ast, 
	 DFNode condValue, DFComponent trueCpt, DFComponent falseCpt) {

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

	if (trueCpt != null) {
	    for (DFMeet meet : trueCpt.meets) {
		DFRef ref = meet.node.ref;
		DFNode node = trueCpt.get(ref);
		JoinNode join = new JoinNode(scope, ref, ast, condValue);
		join.recv(true, node);
		cpt.addMeet(new DFMeet(join, meet.frame, meet.label));
	    }
	}
	if (falseCpt != null) {
	    for (DFMeet meet : falseCpt.meets) {
		DFRef ref = meet.node.ref;
		DFNode node = falseCpt.get(ref);
		JoinNode join = new JoinNode(scope, ref, ast, condValue);
		join.recv(false, node);
		cpt.addMeet(new DFMeet(join, meet.frame, meet.label));
	    }
	}
	
	return cpt;
    }

    public DFComponent processCase
	(DFScope scope, DFComponent cpt, DFFrame frame,
	 ASTNode apt, DFNode caseNode, DFComponent caseCpt) {

	for (Map.Entry<DFRef, DFNode> entry : caseCpt.inputs.entrySet()) {
	    DFRef ref = entry.getKey();
	    DFNode src = entry.getValue();
	    cpt.get(ref).connect(src);
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

    public DFComponent processLoop
	(DFScope scope, DFComponent cpt, DFFrame frame, 
	 ASTNode ast, DFNode condValue, DFComponent loopCpt)
	throws UnsupportedSyntax {

	Map<DFRef, DFNode> inputs = new HashMap<DFRef, DFNode>();
	Map<DFRef, DFNode> outputs = new HashMap<DFRef, DFNode>();
	Map<DFRef, DFNode> exits = new HashMap<DFRef, DFNode>();
	for (DFRef ref : frame.loopRefs) {
	    DFNode src = cpt.get(ref);
	    DFNode loop = new LoopNode(scope, ref, ast, src);
	    DFNode exit = new DistNode(scope, ref);
	    BranchNode branch = new BranchNode(scope, ref, ast, condValue);
	    branch.open(loop, exit);
	    loop.connect(branch, DFLinkType.Informational, "end");
	    inputs.put(ref, loop);
	    outputs.put(ref, branch);
	    exits.put(ref, exit);
	}
	
	for (DFMeet meet : loopCpt.meets) {
	    if (meet.frame == frame && meet.label == DFLabel.CONTINUE) {
		DFNode node = meet.node;
		DFNode loop = inputs.get(node.ref);
		if (node instanceof JoinNode) {
		    ((JoinNode)node).close(loop);
		}
		inputs.put(node.ref, node);
	    }
	}
	
	for (Map.Entry<DFRef, DFNode> entry : loopCpt.inputs.entrySet()) {
	    DFRef ref = entry.getKey();
	    DFNode input = entry.getValue();
	    DFNode loop = inputs.get(ref);
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
	    DFNode branch = outputs.get(ref);
	    if (branch != null) {
		output.connect(branch);
		DFNode exit = exits.get(ref);
		cpt.put(exit);
	    } else {
		cpt.put(output);
	    }
	}
	
	for (DFMeet meet : loopCpt.meets) {
	    if (meet.frame == frame && meet.label == DFLabel.BREAK) {
		DFNode node = meet.node;
		DFNode output = cpt.get(node.ref);
		if (node instanceof JoinNode) {
		    ((JoinNode)node).close(output);
		}
		cpt.put(node);
	    }
	}

    	for (DFMeet meet : loopCpt.meets) {
	    if (meet.frame != frame) {
		cpt.addMeet(meet);
	    }
	}
	return cpt;
    }

    public DFComponent processVariableDeclaration
	(DFScope scope, DFComponent cpt, 
	 List<VariableDeclarationFragment> frags)
	throws UnsupportedSyntax {

	for (VariableDeclarationFragment frag : frags) {
	    SimpleName varName = frag.getName();
	    DFRef ref = scope.lookupVar(varName.getIdentifier());
	    Expression init = frag.getInitializer();
	    if (init != null) {
		cpt = processExpression(scope, cpt, init);
		AssignNode assign = new SingleAssignNode(scope, ref, frag);
		assign.take(cpt.value);
		cpt.put(assign);
	    }
	}
	return cpt;
    }

    @SuppressWarnings("unchecked")
    public DFComponent processAssignment
	(DFScope scope, DFComponent cpt, 
	 Expression expr)
	throws UnsupportedSyntax {

	if (expr instanceof SimpleName) {
	    SimpleName varName = (SimpleName)expr;
	    DFRef ref = scope.lookupVar(varName.getIdentifier());
	    cpt.assign = new SingleAssignNode(scope, ref, expr);
	    
	} else if (expr instanceof ArrayAccess) {
	    ArrayAccess aa = (ArrayAccess)expr;
	    cpt = processExpression(scope, cpt, aa.getArray());
	    DFNode array = cpt.value;
	    cpt = processExpression(scope, cpt, aa.getIndex());
	    DFNode index = cpt.value;
	    DFRef ref = scope.lookupArray();
	    cpt.assign = new ArrayAssignNode(scope, ref, expr, array, index);
	    
	} else if (expr instanceof FieldAccess) {
	    FieldAccess fa = (FieldAccess)expr;
	    SimpleName fieldName = fa.getName();
	    cpt = processExpression(scope, cpt, fa.getExpression());
	    DFNode obj = cpt.value;
	    DFRef ref = scope.lookupField(fieldName.getIdentifier());
	    cpt.assign = new FieldAssignNode(scope, ref, expr, obj);
	    
	} else if (expr instanceof QualifiedName) {
	    QualifiedName qn = (QualifiedName)expr;
	    SimpleName fieldName = qn.getName();
	    DFRef ref = scope.lookupField(fieldName.getIdentifier());
	    cpt = processExpression(scope, cpt, qn.getQualifier());
	    DFNode obj = cpt.value;
	    cpt.assign = new FieldAssignNode(scope, ref, expr, obj);
	    
	} else {
	    throw new UnsupportedSyntax(expr);
	}
	return cpt;
    }
    
    @SuppressWarnings("unchecked")
    public DFComponent processExpression
	(DFScope scope, DFComponent cpt, 
	 Expression expr)
	throws UnsupportedSyntax {

	if (expr instanceof Annotation) {

	} else if (expr instanceof SimpleName) {
	    SimpleName varName = (SimpleName)expr;
	    DFRef ref = scope.lookupVar(varName.getIdentifier());
	    cpt.value = cpt.get(ref);
	    
	} else if (expr instanceof ThisExpression) {
	    cpt.value = cpt.get(scope.lookupThis());
	    
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
	    cpt = processExpression(scope, cpt, operand);
	    if (op == PrefixExpression.Operator.INCREMENT ||
		op == PrefixExpression.Operator.DECREMENT) {
		cpt = processAssignment(scope, cpt, operand);
		AssignNode assign = cpt.assign;
		DFNode value = new PrefixNode(scope, assign.ref, expr, op, cpt.value);
		assign.take(value);
		cpt.put(assign);
		cpt.value = value;
	    }
	    
	} else if (expr instanceof PostfixExpression) {
	    PostfixExpression postfix = (PostfixExpression)expr;
	    PostfixExpression.Operator op = postfix.getOperator();
	    Expression operand = postfix.getOperand();
	    cpt = processAssignment(scope, cpt, operand);
	    if (op == PostfixExpression.Operator.INCREMENT ||
		op == PostfixExpression.Operator.DECREMENT) {
		AssignNode assign = cpt.assign;
		cpt = processExpression(scope, cpt, operand);
		assign.take(new PostfixNode(scope, assign.ref, expr, op, cpt.value));
		cpt.put(assign);
	    }
	    
	} else if (expr instanceof InfixExpression) {
	    InfixExpression infix = (InfixExpression)expr;
	    InfixExpression.Operator op = infix.getOperator();
	    cpt = processExpression(scope, cpt, infix.getLeftOperand());
	    DFNode lvalue = cpt.value;
	    cpt = processExpression(scope, cpt, infix.getRightOperand());
	    DFNode rvalue = cpt.value;
	    cpt.value = new InfixNode(scope, expr, op, lvalue, rvalue);
	    
	} else if (expr instanceof ParenthesizedExpression) {
	    ParenthesizedExpression paren = (ParenthesizedExpression)expr;
	    cpt = processExpression(scope, cpt, paren.getExpression());
	    
	} else if (expr instanceof Assignment) {
	    Assignment assn = (Assignment)expr;
	    Assignment.Operator op = assn.getOperator();
	    cpt = processAssignment(scope, cpt, assn.getLeftHandSide());
	    AssignNode assign = cpt.assign;
	    cpt = processExpression(scope, cpt, assn.getRightHandSide());
	    DFNode rvalue = cpt.value;
	    if (op != Assignment.Operator.ASSIGN) {
		DFNode lvalue = cpt.get(assign.ref);
		rvalue = new AssignOpNode(scope, assign.ref, assn, op, lvalue, rvalue);
	    }
	    assign.take(rvalue);
	    cpt.put(assign);
	    cpt.value = assign;

	} else if (expr instanceof VariableDeclarationExpression) {
	    VariableDeclarationExpression decl = (VariableDeclarationExpression)expr;
	    cpt = processVariableDeclaration
		(scope, cpt, decl.fragments());

	} else if (expr instanceof MethodInvocation) {
	    MethodInvocation invoke = (MethodInvocation)expr;
	    Expression expr1 = invoke.getExpression();
	    DFNode obj = null;
	    if (expr1 != null) {
		cpt = processExpression(scope, cpt, expr1);
		obj = cpt.value;
	    }
	    SimpleName methodName = invoke.getName();
	    MethodCallNode call = new MethodCallNode
		(scope, invoke, obj, methodName.getIdentifier());
	    for (Expression arg : (List<Expression>) invoke.arguments()) {
		cpt = processExpression(scope, cpt, arg);
		call.take(cpt.value);
	    }
	    cpt.value = call;
	    
	} else if (expr instanceof SuperMethodInvocation) {
	    SuperMethodInvocation si = (SuperMethodInvocation)expr;
	    SimpleName methodName = si.getName();
	    DFNode obj = cpt.get(scope.lookupSuper());
	    MethodCallNode call = new MethodCallNode
		(scope, si, obj, methodName.getIdentifier());
	    for (Expression arg : (List<Expression>) si.arguments()) {
		cpt = processExpression(scope, cpt, arg);
		call.take(cpt.value);
	    }
	    cpt.value = call;
	    
	} else if (expr instanceof ArrayCreation) {
	    ArrayCreation ac = (ArrayCreation)expr;
	    for (Expression dim : (List<Expression>) ac.dimensions()) {
		// XXX cpt.value is not used (for now).
		cpt = processExpression(scope, cpt, dim);
	    }
	    ArrayInitializer init = ac.getInitializer();
	    if (init != null) {
		cpt = processExpression(scope, cpt, init);
	    } else {
		cpt.value = new ArrayValueNode(scope, ac);
	    }
	    
	} else if (expr instanceof ArrayInitializer) {
	    ArrayInitializer init = (ArrayInitializer)expr;
	    ArrayValueNode arr = new ArrayValueNode(scope, init);
	    for (Expression expr1 : (List<Expression>) init.expressions()) {
		cpt = processExpression(scope, cpt, expr1);
		arr.take(cpt.value);
	    }
	    cpt.value = arr;
	    // XXX array ref is not used.
	    
	} else if (expr instanceof ArrayAccess) {
	    ArrayAccess aa = (ArrayAccess)expr;
	    DFRef ref = scope.lookupArray();
	    cpt = processExpression(scope, cpt, aa.getArray());
	    DFNode array = cpt.value;
	    cpt = processExpression(scope, cpt, aa.getIndex());
	    DFNode index = cpt.value;
	    cpt.value = new ArrayAccessNode(scope, ref, aa,
					    cpt.get(ref), array, index);
	    
	} else if (expr instanceof FieldAccess) {
	    FieldAccess fa = (FieldAccess)expr;
	    SimpleName fieldName = fa.getName();
	    DFRef ref = scope.lookupField(fieldName.getIdentifier());
	    cpt = processExpression(scope, cpt, fa.getExpression());
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
	    cpt = processExpression(scope, cpt, qn.getQualifier());
	    DFNode obj = cpt.value;
	    cpt.value = new FieldAccessNode(scope, ref, qn,
					    cpt.get(ref), obj);
	    
	} else if (expr instanceof CastExpression) {
	    CastExpression cast = (CastExpression)expr;
	    Type type = cast.getType();
	    cpt = processExpression(scope, cpt, cast.getExpression());
	    cpt.value = new TypeCastNode(scope, cast, type, cpt.value);
	    
	} else if (expr instanceof ClassInstanceCreation) {
	    ClassInstanceCreation cstr = (ClassInstanceCreation)expr;
	    Type instType = cstr.getType();
	    Expression expr1 = cstr.getExpression();
	    DFNode obj = null;
	    if (expr1 != null) {
		cpt = processExpression(scope, cpt, expr1);
		obj = cpt.value;
	    }
	    CreateObjectNode call =
		new CreateObjectNode(scope, cstr, obj, instType);
	    for (Expression arg : (List<Expression>) cstr.arguments()) {
		cpt = processExpression(scope, cpt, arg);
		call.take(cpt.value);
	    }
	    cpt.value = call;
	    // XXX ignore getAnonymousClassDeclaration();
	    
	} else if (expr instanceof ConditionalExpression) {
	    ConditionalExpression cond = (ConditionalExpression)expr;
	    cpt = processExpression(scope, cpt, cond.getExpression());
	    DFNode condValue = cpt.value;
	    cpt = processExpression(scope, cpt, cond.getThenExpression());
	    DFNode trueValue = cpt.value;
	    cpt = processExpression(scope, cpt, cond.getElseExpression());
	    DFNode falseValue = cpt.value;
	    JoinNode join = new JoinNode(scope, null, expr, condValue);
	    join.recv(true, trueValue);
	    join.recv(false, falseValue);
	    cpt.value = join;
	    
	} else if (expr instanceof InstanceofExpression) {
	    InstanceofExpression instof = (InstanceofExpression)expr;
	    Type type = instof.getRightOperand();
	    cpt = processExpression(scope, cpt, instof.getLeftOperand());
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
    
    @SuppressWarnings("unchecked")
    public DFComponent processVariableDeclarationStatement
	(DFScopeMap map, DFFrame frame, DFComponent cpt,
	 VariableDeclarationStatement varStmt)
	throws UnsupportedSyntax {
	DFScope scope = map.get(varStmt);
	return processVariableDeclaration
	    (scope, cpt, varStmt.fragments());
    }

    public DFComponent processExpressionStatement
	(DFScopeMap map, DFFrame frame, DFComponent cpt,
	 ExpressionStatement exprStmt)
	throws UnsupportedSyntax {
	DFScope scope = map.get(exprStmt);
	Expression expr = exprStmt.getExpression();
	return processExpression(scope, cpt, expr);
    }

    public DFComponent processReturnStatement
	(DFScopeMap map, DFFrame frame, DFComponent cpt,
	 ReturnStatement rtrnStmt)
	throws UnsupportedSyntax {
	DFScope scope = map.get(rtrnStmt);
	Expression expr = rtrnStmt.getExpression();
	DFNode value = null;
	if (expr != null) {
	    cpt = processExpression(scope, cpt, expr);
	    value = cpt.value;
	}
	if (value != null) {
	    DFRef ref = scope.lookupReturn();
	    ReturnNode rtrn = new ReturnNode(scope, ref, rtrnStmt);
	    rtrn.take(value);
	    cpt.put(rtrn);
	}
	return cpt;
    }
    
    public DFComponent processIfStatement
	(DFScopeMap map, DFFrame frame, DFComponent cpt,
	 IfStatement ifStmt)
	throws UnsupportedSyntax {
	DFScope scope = map.get(ifStmt);
	Expression expr = ifStmt.getExpression();
	cpt = processExpression(scope, cpt, expr);
	DFNode condValue = cpt.value;
	
	Statement thenStmt = ifStmt.getThenStatement();
	DFComponent thenCpt = new DFComponent(scope);
	thenCpt = processStatement(map, frame, thenCpt, thenStmt);
	
	Statement elseStmt = ifStmt.getElseStatement();
	DFComponent elseCpt = null;
	if (elseStmt != null) {
	    elseCpt = new DFComponent(scope);
	    elseCpt = processStatement(map, frame, elseCpt, elseStmt);
	}

	return processConditional(scope, cpt, 
				  ifStmt, condValue,
				  thenCpt, elseCpt);
    }
	
    @SuppressWarnings("unchecked")
    public DFComponent processSwitchStatement
	(DFScopeMap map, DFFrame frame, DFComponent cpt,
	 SwitchStatement switchStmt)
	throws UnsupportedSyntax {
	DFScope scope = map.get(switchStmt);
	cpt = processExpression(scope, cpt, switchStmt.getExpression());
	DFNode switchValue = cpt.value;

	SwitchCase switchCase = null;
	CaseNode caseNode = null;
	DFComponent caseCpt = null;
	for (Statement stmt : (List<Statement>) switchStmt.statements()) {
	    if (stmt instanceof SwitchCase) {
		if (caseNode != null && caseCpt != null) {
		    cpt = processCase(scope, cpt, frame,
				      switchCase, caseNode, caseCpt);
		    caseNode = null;
		    caseCpt = null;
		}
		switchCase = (SwitchCase)stmt;
		if (caseNode == null) {
		    caseNode = new CaseNode(scope, stmt, switchValue);
		}
		Expression expr = switchCase.getExpression();
		if (expr != null) {
		    cpt = processExpression(scope, cpt, expr);
		    caseNode.add(cpt.value);
		}
	    } else {
		if (caseCpt == null) {
		    caseCpt = new DFComponent(scope);
		}
		caseCpt = processStatement(map, frame, caseCpt, stmt);
	    }
	}
	if (caseNode != null && caseCpt != null) {
	    cpt = processCase(scope, cpt, frame,
			      switchCase, caseNode, caseCpt);
	}
	
	return cpt;
    }
    
    public DFComponent processWhileStatement
	(DFScopeMap map, DFFrame frame, DFComponent cpt,
	 WhileStatement whileStmt)
	throws UnsupportedSyntax {
	DFScope scope = map.get(whileStmt);
	// Create a new frame.
	frame = new DFFrame(frame, scope.getLoopRefs());
	DFComponent loopCpt = new DFComponent(scope);
	loopCpt = processExpression(scope, loopCpt,
				    whileStmt.getExpression());
	DFNode condValue = loopCpt.value;
	loopCpt = processStatement(map, frame, loopCpt,
				   whileStmt.getBody());
	return processLoop(scope, cpt, frame, 
			   whileStmt, condValue, loopCpt);
    }
    
    public DFComponent processDoStatement
	(DFScopeMap map, DFFrame frame, DFComponent cpt,
	 DoStatement doStmt)
	throws UnsupportedSyntax {
	DFScope scope = map.get(doStmt);
	// Create a new frame.
	frame = new DFFrame(frame, scope.getLoopRefs());
	DFComponent loopCpt = new DFComponent(scope);
	loopCpt = processStatement(map, frame, loopCpt,
				   doStmt.getBody());
	loopCpt = processExpression(scope, loopCpt,
				    doStmt.getExpression());
	DFNode condValue = loopCpt.value;
	return processLoop(scope, cpt, frame, 
			   doStmt, condValue, loopCpt);
    }
    
    @SuppressWarnings("unchecked")
    public DFComponent processForStatement
	(DFScopeMap map, DFFrame frame, DFComponent cpt,
	 ForStatement forStmt)
	throws UnsupportedSyntax {
	DFScope scope = map.get(forStmt);
	// Create a new frame.
	frame = new DFFrame(frame, scope.getLoopRefs());
	for (Expression init : (List<Expression>) forStmt.initializers()) {
	    cpt = processExpression(scope, cpt, init);
	}
	
	DFComponent loopCpt = new DFComponent(scope);
	Expression expr = forStmt.getExpression();
	DFNode condValue;
	if (expr != null) {
	    loopCpt = processExpression(scope, loopCpt, expr);
	    condValue = loopCpt.value;
	} else {
	    condValue = new ConstNode(scope, null, "true");
	}
	loopCpt = processStatement(map, frame, loopCpt,
				   forStmt.getBody());
	for (Expression update : (List<Expression>) forStmt.updaters()) {
	    loopCpt = processExpression(scope, loopCpt, update);
	}
	
	return processLoop(scope, cpt, frame, forStmt,
			   condValue, loopCpt);
    }
    
    @SuppressWarnings("unchecked")
    public DFComponent processEnhancedForStatement
	(DFScopeMap map, DFFrame frame, DFComponent cpt,
	 EnhancedForStatement eForStmt)
	throws UnsupportedSyntax {
	DFScope scope = map.get(eForStmt);
	// Create a new frame.
	frame = new DFFrame(frame, scope.getLoopRefs());
	
	DFComponent loopCpt = new DFComponent(scope);
	Expression expr = eForStmt.getExpression();
	loopCpt = processExpression(scope, loopCpt, expr);
	SingleVariableDeclaration decl = eForStmt.getParameter();
	SimpleName varName = decl.getName();
	DFRef ref = scope.lookupVar(varName.getIdentifier());
	DFNode iterValue = new IterNode(scope, ref, expr, loopCpt.value);
	SingleAssignNode assign = new SingleAssignNode(scope, ref, expr);
	assign.take(iterValue);
	cpt.put(assign);
	loopCpt = processStatement(map, frame, loopCpt,
				   eForStmt.getBody());
	
	return processLoop(scope, cpt, frame, eForStmt,
			   iterValue, loopCpt);
    }
    
    @SuppressWarnings("unchecked")
    public DFComponent processStatement
	(DFScopeMap map, DFFrame frame, DFComponent cpt,
	 Statement stmt)
	throws UnsupportedSyntax {
	
	if (stmt instanceof AssertStatement) {
	    // Ignore assert.
	} else if (stmt instanceof Block) {
	    DFScope scope = map.get(stmt);
	    Block block = (Block)stmt;
	    for (Statement cstmt : (List<Statement>) block.statements()) {
		cpt = processStatement(map, frame, cpt, cstmt);
	    }
	    scope.finish(cpt);

	} else if (stmt instanceof EmptyStatement) {
	    
	} else if (stmt instanceof VariableDeclarationStatement) {
	    cpt = processVariableDeclarationStatement
		(map, frame, cpt, (VariableDeclarationStatement)stmt);

	} else if (stmt instanceof ExpressionStatement) {
	    cpt = processExpressionStatement
		(map, frame, cpt, (ExpressionStatement)stmt);
		
	} else if (stmt instanceof ReturnStatement) {
	    cpt = processReturnStatement
		(map, frame, cpt, (ReturnStatement)stmt);
	    
	} else if (stmt instanceof IfStatement) {
	    cpt = processIfStatement
		(map, frame, cpt, (IfStatement)stmt);
	    
	} else if (stmt instanceof SwitchStatement) {
	    DFScope scope = map.get(stmt);
	    cpt = processSwitchStatement
		(map, frame, cpt, (SwitchStatement)stmt);
	    scope.finish(cpt);
	    
	} else if (stmt instanceof SwitchCase) {
	    // Invalid "case" placement.
	    throw new UnsupportedSyntax(stmt);
	    
	} else if (stmt instanceof WhileStatement) {
	    cpt = processWhileStatement
		(map, frame, cpt, (WhileStatement)stmt);
	    
	} else if (stmt instanceof DoStatement) {
	    cpt = processDoStatement
		(map, frame, cpt, (DoStatement)stmt);
	    
	} else if (stmt instanceof ForStatement) {
	    DFScope scope = map.get(stmt);
	    cpt = processForStatement
		(map, frame, cpt, (ForStatement)stmt);
	    scope.finish(cpt);
	    
	} else if (stmt instanceof EnhancedForStatement) {
	    DFScope scope = map.get(stmt);
	    cpt = processEnhancedForStatement
		(map, frame, cpt, (EnhancedForStatement)stmt);
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
	    cpt = processStatement(map, frame, cpt,
				   labeledStmt.getBody());
	    
	} else if (stmt instanceof SynchronizedStatement) {
	    SynchronizedStatement syncStmt = (SynchronizedStatement)stmt;
	    cpt = processStatement(map, frame, cpt,
				   syncStmt.getBody());

	} else if (stmt instanceof TryStatement) {
	    // XXX Ignore catch statements (for now).
	    TryStatement tryStmt = (TryStatement)stmt;
	    cpt = processStatement(map, frame, cpt,
				   tryStmt.getBody());
	    Block finBlock = tryStmt.getFinally();
	    if (finBlock != null) {
		cpt = processStatement(map, frame, cpt, finBlock);
	    }
	    
	} else if (stmt instanceof ThrowStatement) {
	    // XXX Ignore throw (for now).
	    DFScope scope = map.get(stmt);
	    ThrowStatement throwStmt = (ThrowStatement)stmt;
	    cpt = processExpression(scope, cpt, throwStmt.getExpression());
	    
	} else if (stmt instanceof ConstructorInvocation) {
	    // XXX ignore all side effects.
	    DFScope scope = map.get(stmt);
	    ConstructorInvocation ci = (ConstructorInvocation)stmt;
	    for (Expression arg : (List<Expression>) ci.arguments()) {
		cpt = processExpression(scope, cpt, arg);
	    }
	    
	} else if (stmt instanceof SuperConstructorInvocation) {
	    // XXX ignore all side effects.
	    DFScope scope = map.get(stmt);
	    SuperConstructorInvocation sci = (SuperConstructorInvocation)stmt;
	    for (Expression arg : (List<Expression>) sci.arguments()) {
		cpt = processExpression(scope, cpt, arg);
	    }
		
	} else {
	    // TypeDeclarationStatement
	    
	    throw new UnsupportedSyntax(stmt);
	}

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
	    DFRef ref = scope.lookupVar(varName.getIdentifier());
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
    
    public DFScope getMethodGraph(MethodDeclaration method)
	throws UnsupportedSyntax {
	String funcName = method.getName().getFullyQualifiedName();
	Block funcBlock = method.getBody();
	// Ignore method prototypes.
	if (funcBlock == null) return null;
				   
	DFScope scope = new DFScope(funcName);
	
	// Setup an initial scope.
	DFComponent cpt = buildMethodDeclaration(scope, method);
	DFScopeMap map = new DFScopeMap();
	buildScope(map, scope, funcBlock);
	//scope.dump();

	// Process the function body.
	cpt = processStatement(map, null, cpt, funcBlock);

        // Collapse redundant nodes.
	scope.cleanup();
	return scope;
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

    // main
    public static void main(String[] args)
	throws IOException {
	
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
	    parser.setEnvironment(classpath, null, null, true);
	    parser.setCompilerOptions(options);
	    CompilationUnit cu = (CompilationUnit)parser.createAST(null);
	    
	    Java2DF visitor = new Java2DF(exporter);
	    cu.accept(visitor);
	}
	output.close();
    }
}
