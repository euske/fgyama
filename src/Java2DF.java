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

    public static DFRef RETURN = new DFRef(null, "RETURN");
}


//  DFVar
//
class DFVar extends DFRef {

    public String type;

    public DFVar(DFScope scope, String name, String type) {
	super(scope, name);
	this.type = type;
    }
}


//  DFScope
//
class DFScope {

    public DFScope parent;
    public Map<String, DFVar> vars;

    public DFScope() {
	this(null);
    }
    
    public DFScope(DFScope parent) {
	this.parent = parent;
	this.vars = new HashMap<String, DFVar>();
    }

    public DFVar add(String name, String type) {
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
	    return null;
	}
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
    
    public DFComponent(DFGraph graph) {
	this.graph = graph;
	this.inputs = new HashMap<DFRef, DFNode>();
	this.outputs = new HashMap<DFRef, DFNode>();
	this.value = null;
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
class BoxNode extends ProgNode {

    public DFRef ref;
    public DFNode value;
    
    public BoxNode(DFGraph graph, ASTNode node,
		   DFRef ref, DFNode value) {
	super(graph, node);
	this.ref = ref;
	this.value = value;
	value.connect(this, "assign");
    }

    public DFNodeType type() {
	return DFNodeType.Box;
    }

    public String label() {
	return this.ref.name;
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

// PrefixNode
class PrefixNode extends ProgNode {

    public String op;
    public DFNode value;

    public PrefixNode(DFGraph graph, ASTNode node,
		      String op, DFNode value) {
	super(graph, node);
	this.op = op;
	this.value = value;
	value.connect(this, "pre");
    }

    public String label() {
	return this.op;
    }
}

// PostfixNode
class PostfixNode extends ProgNode {

    public String op;
    public DFNode value;

    public PostfixNode(DFGraph graph, ASTNode node,
		       String op, DFNode value) {
	super(graph, node);
	this.op = op;
	this.value = value;
	value.connect(this, "post");
    }

    public String label() {
	return this.op;
    }
}

// InfixNode
class InfixNode extends ProgNode {

    public String op;
    public DFNode lvalue;
    public DFNode rvalue;

    public InfixNode(DFGraph graph, ASTNode node,
		     String op, DFNode lvalue, DFNode rvalue) {
	super(graph, node);
	this.op = op;
	this.lvalue = lvalue;
	this.rvalue = rvalue;
	lvalue.connect(this, "L");
	rvalue.connect(this, "R");
    }

    public String label() {
	return this.op;
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
}

// JoinNode
class JoinNode extends CondNode {

    public JoinNode(DFGraph graph, ASTNode node, DFNode value) {
	super(graph, node, value);
    }
    
    public String label() {
	return "Join";
    }
}

// LoopNode
class LoopNode extends ProgNode {

    public LoopNode(DFGraph graph, ASTNode node) {
	super(graph, node);
    }
    
    public DFNodeType type() {
	return DFNodeType.Loop;
    }
    
    public String label() {
	return "Loop";
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



//  Java2DF
// 
public class Java2DF extends ASTVisitor {

    // Utility functions.

    public static void logit(String s) {
	System.err.println(s);
    }

    public static String getTypeName(Type type) {
	if (type instanceof PrimitiveType) {
	    return ((PrimitiveType)type).getPrimitiveTypeCode().toString();
	} else if (type instanceof SimpleType) {
	    return ((SimpleType)type).getName().getFullyQualifiedName();
	} else {
	    return null;
	}
    }

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
	    logit("Unsupported: "+e.node);
	}
	return true;
    }

    public DFRef getReference(Expression expr, DFScope scope) 
	throws UnsupportedSyntax {
	if (expr instanceof Name) {
	    Name varName = (Name)expr;
	    return scope.lookup(varName.getFullyQualifiedName());
	} else {
	    throw new UnsupportedSyntax(expr);
	}
    }

    public DFComponent processExpression
	(DFGraph graph, DFScope scope, DFComponent compo, Expression expr)
	throws UnsupportedSyntax {

	if (expr instanceof Name) {
	    // Variable lookup.
	    DFRef ref = getReference(expr, scope);
	    compo.value = compo.get(ref);
	    
	} else if (expr instanceof BooleanLiteral) {
	    // Cosntant.
	    boolean value = ((BooleanLiteral)expr).booleanValue();
	    compo.value = new ConstNode(graph, expr, Boolean.toString(value));
	    
	} else if (expr instanceof CharacterLiteral) {
	    // Cosntant.
	    char value = ((CharacterLiteral)expr).charValue();
	    compo.value = new ConstNode(graph, expr, Character.toString(value));
	    
	} else if (expr instanceof NullLiteral) {
	    // Cosntant.
	    compo.value = new ConstNode(graph, expr, "null");
	    
	} else if (expr instanceof NumberLiteral) {
	    // Cosntant.
	    String value = ((NumberLiteral)expr).getToken();
	    compo.value = new ConstNode(graph, expr, value);
	    
	} else if (expr instanceof StringLiteral) {
	    // Cosntant.
	    String value = ((StringLiteral)expr).getLiteralValue();
	    compo.value = new ConstNode(graph, expr, value);
	    
	} else if (expr instanceof TypeLiteral) {
	    // Cosntant.
	    Type value = ((TypeLiteral)expr).getType();
	    compo.value = new ConstNode(graph, expr, getTypeName(value));
	    
	} else if (expr instanceof PrefixExpression) {
	    // Prefix operator.
	    PrefixExpression prefix = (PrefixExpression)expr;
	    String op = prefix.getOperator().toString();
	    compo = processExpression(graph, scope, compo, prefix.getOperand());
	    compo.value = new PrefixNode(graph, expr, op, compo.value);
	    
	} else if (expr instanceof PostfixExpression) {
	    // Postfix operator.
	    PostfixExpression postfix = (PostfixExpression)expr;
	    String op = postfix.getOperator().toString();
	    compo = processExpression(graph, scope, compo, postfix.getOperand());
	    compo.value = new PostfixNode(graph, expr, op, compo.value);
	    
	} else if (expr instanceof InfixExpression) {
	    // Infix operator.
	    InfixExpression infix = (InfixExpression)expr;
	    String op = infix.getOperator().toString();
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
	    String op = assn.getOperator().toString();
	    DFRef ref = getReference(assn.getLeftHandSide(), scope);
	    DFNode lvalue = compo.get(ref);
	    compo = processExpression(graph, scope, compo, assn.getRightHandSide());
	    DFNode rvalue = new InfixNode(graph, assn, op, lvalue, compo.value);
	    DFNode box = new BoxNode(graph, assn, ref, rvalue);
	    compo.put(ref, box);
	    compo.value = box;
	    
	} else {
	    throw new UnsupportedSyntax(expr);
	}
	
	return compo;
    }
    
    @SuppressWarnings("unchecked")
    public DFComponent processVariableDeclarationStatement
	(DFGraph graph, DFScope scope, DFComponent compo,
	 VariableDeclarationStatement varStmt)
	throws UnsupportedSyntax {
	Type varType = varStmt.getType();
	for (VariableDeclarationFragment frag :
		 (List<VariableDeclarationFragment>) varStmt.fragments()) {
	    Expression expr = frag.getInitializer();
	    if (expr != null) {
		Name varName = frag.getName();
		DFVar var = scope.add(varName.getFullyQualifiedName(),
				      getTypeName(varType));
		compo = processExpression(graph, scope, compo, expr);
		DFNode box = new BoxNode(graph, frag, var, compo.value);
		compo.put(var, box);
	    }
	}
	return compo;
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
	
	Map<DFRef, DFNode> branches = new HashMap<DFRef, DFNode>();
	Map<DFRef, DFNode> joins = new HashMap<DFRef, DFNode>();
	for (Map.Entry<DFRef, DFNode> entry : thenCompo.inputs.entrySet()) {
	    DFRef ref = entry.getKey();
	    DFNode src = entry.getValue();
	    DFNode branch = branches.get(ref);
	    if (branch == null) {
		branch = new BranchNode(graph, ifStmt, evalue);
		branches.put(ref, branch);
		compo.get(ref).connect(branch);
	    }
	    branch.connect(src, "then");
	}
	for (Map.Entry<DFRef, DFNode> entry : thenCompo.outputs.entrySet()) {
	    DFRef ref = entry.getKey();
	    DFNode dst = entry.getValue();
	    DFNode join = joins.get(ref);
	    if (join == null) {
		join = new JoinNode(graph, ifStmt, evalue);
		joins.put(ref, join);
		compo.get(ref).connect(join);
		compo.put(ref, join);
	    }
	    dst.connect(join, "then");
	}
	
	Statement elseStmt = ifStmt.getElseStatement();
	if (elseStmt != null) {
	    DFComponent elseCompo = new DFComponent(graph);
	    elseCompo = processStatement(graph, scope, elseCompo, elseStmt);
	    for (Map.Entry<DFRef, DFNode> entry : elseCompo.inputs.entrySet()) {
		DFRef ref = entry.getKey();
		DFNode src = entry.getValue();
		DFNode branch = branches.get(ref);
		if (branch == null) {
		    branch = new BranchNode(graph, ifStmt, evalue);
		    branches.put(ref, branch);
		    compo.get(ref).connect(branch);
		}
		branch.connect(src, "else");
	    }
	    for (Map.Entry<DFRef, DFNode> entry : elseCompo.outputs.entrySet()) {
		DFRef ref = entry.getKey();
		DFNode dst = entry.getValue();
		DFNode join = joins.get(ref);
		if (join == null) {
		    join = new JoinNode(graph, ifStmt, evalue);
		    joins.put(ref, join);
		    compo.get(ref).connect(join);
		    compo.put(ref, join);
		}
		dst.connect(join, "else");
	    }
	}
	
	return compo;
    }

    public DFComponent processWhileStatement
	(DFGraph graph, DFScope scope, DFComponent compo,
	 WhileStatement whileStmt)
	throws UnsupportedSyntax {
	
	Expression expr = whileStmt.getExpression();
	DFComponent exprCompo = new DFComponent(graph);
	exprCompo = processExpression(graph, scope, exprCompo, expr);
	DFNode evalue = exprCompo.value;
	
	Statement body = whileStmt.getBody();
	DFComponent bodyCompo = new DFComponent(graph);
	bodyCompo = processStatement(graph, scope, bodyCompo, body);

	Map<DFRef, DFNode> bindings = new HashMap<DFRef, DFNode>();
	Map<DFRef, DFNode> loops = new HashMap<DFRef, DFNode>();
	for (Map.Entry<DFRef, DFNode> entry : exprCompo.inputs.entrySet()) {
	    DFRef ref = entry.getKey();
	    DFNode src = entry.getValue();
	    DFNode loop = loops.get(ref);
	    if (loop == null) {
		loop = new LoopNode(graph, whileStmt);
		loops.put(ref, loop);
		compo.get(ref).connect(loop, "init");
	    }
	    loop.connect(src);
	}
	for (Map.Entry<DFRef, DFNode> entry : exprCompo.outputs.entrySet()) {
	    DFRef ref = entry.getKey();
	    DFNode dst = entry.getValue();
	    bindings.put(ref, dst);
	}
	
	for (Map.Entry<DFRef, DFNode> entry : bodyCompo.inputs.entrySet()) {
	    DFRef ref = entry.getKey();
	    DFNode src = entry.getValue();
	    DFNode loop = loops.get(ref);
	    if (loop == null) {
		DFNode node = bindings.get(ref);
		if (node != null) {
		    bindings.remove(ref);
		} else {
		    node = compo.get(ref);
		}
		loop = new LoopNode(graph, whileStmt);
		loops.put(ref, loop);
		node.connect(loop, "init");
	    }
	    DFNode branch = new BranchNode(graph, whileStmt, evalue);
	    loop.connect(branch);
	    branch.connect(src, "true");
	    DFNode dst = new DistNode(graph);
	    branch.connect(dst, "false");
	    compo.put(ref, dst);
	}
	for (Map.Entry<DFRef, DFNode> entry : bodyCompo.outputs.entrySet()) {
	    DFRef ref = entry.getKey();
	    DFNode dst = entry.getValue();
	    DFNode loop = loops.get(ref);
	    if (loop != null) {
		dst.connect(loop, "loop");
	    } else {
		compo.put(ref, dst);
	    }
	}
	
	for (Map.Entry<DFRef, DFNode> entry : bindings.entrySet()) {
	    DFRef ref = entry.getKey();
	    DFNode dst = entry.getValue();
	    compo.put(ref, dst);
	}
	return compo;
    }

    @SuppressWarnings("unchecked")
    public DFComponent processForStatement
	(DFGraph graph, DFScope scope, DFComponent compo,
	 ForStatement forStmt)
	throws UnsupportedSyntax {
	
	return compo;
    }
    
    public DFComponent processStatement
	(DFGraph graph, DFScope scope, DFComponent compo, Statement stmt)
	throws UnsupportedSyntax {
	
	if (stmt instanceof Block) {
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
	    
	} else {
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
	    Name paramName = decl.getName();
	    Type paramType = decl.getType();
	    // XXX check getExtraDimensions()
	    DFVar var = scope.add(paramName.getFullyQualifiedName(),
				  getTypeName(paramType));
	    DFNode box = new BoxNode(graph, decl, var, param);
	    compo.put(var, box);
	}
	return compo;
    }
    
    public DFGraph getMethodGraph(MethodDeclaration method)
	throws UnsupportedSyntax {
	Name funcName = method.getName();
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

		logit("Parsing: "+path);
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
