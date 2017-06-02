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

    abstract public String label();

    public DFLink connect(DFNode dst) {
	return this.connect(dst, null);
    }
    
    public DFLink connect(DFNode dst, String label) {
	DFLink link = new DFLink(this, dst, label);
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


//  DFLink
//
class DFLink {
    
    public DFNode src;
    public DFNode dst;
    public String name;
    
    public DFLink(DFNode src, DFNode dst, String name)
    {
	this.src = src;
	this.dst = dst;
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

    public Collection<DFVar> pop() {
	return this.vars.values();
    }
}


//  DFFlow
//
class DFFlow {
}

class DFInputFlow extends DFFlow {

    public DFRef ref;
    public DFNode node;

    public DFInputFlow(DFRef ref, DFNode node) {
	this.ref = ref;
	this.node = node;
    }

}

class DFOutputFlow extends DFFlow {

    public DFNode node;
    public DFRef ref;

    public DFOutputFlow(DFNode node, DFRef ref) {
	this.node = node;
	this.ref = ref;
    }

}


//  DFFlowSet
//
class DFFlowSet {

    public List<DFFlow> flows;
    public DFNode value;

    public DFFlowSet() {
	this.flows = new ArrayList<DFFlow>();
    }

    public void addFlow(DFFlow flow) {
	this.flows.add(flow);
    }

    public void addFlowSet(DFFlowSet fset) {
	this.flows.addAll(fset.flows);
    }
}


//  DFBindings
//
class DFBindings {

    public DFGraph graph;
    public Map<DFRef, DFNode> inputs;
    public Map<DFRef, DFNode> bindings;
    public DFNode retval;

    public DFBindings(DFGraph graph) {
	this.graph = graph;
	this.inputs = new HashMap<DFRef, DFNode>();
	this.bindings = new HashMap<DFRef, DFNode>();
	this.retval = null;
    }

    public DFNode get(DFRef ref) {
	DFNode node = this.bindings.get(ref);
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
	this.bindings.put(ref, node);
    }

    public void setReturn(DFNode node) {
	if (this.retval == null) {
	    this.retval = new ReturnNode(this.graph);
	}
	node.connect(this.retval);
    }
    
    public DFFlowSet finish(DFScope scope) {
	DFFlowSet fset = new DFFlowSet();
	for (DFRef ref : scope.pop()) {
	    this.bindings.remove(ref);
	}
	for (DFRef ref : this.inputs.keySet()) {
	    DFNode node = this.inputs.get(ref);
	    fset.addFlow(new DFInputFlow(ref, node));
	}
	for (DFRef ref : this.bindings.keySet()) {
	    DFNode node = this.bindings.get(ref);
	    fset.addFlow(new DFOutputFlow(node, ref));
	}
	return fset;
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

// ReturnNode: represents a return value.
class ReturnNode extends DFNode {

    public ReturnNode(DFGraph graph) {
	super(graph);
    }

    public String label() {
	return "Return";
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
    
    public BoxNode(DFGraph graph, ASTNode node, DFRef ref) {
	super(graph, node);
	this.ref = ref;
    }

    public String label() {
	return this.ref.name;
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
	value.connect(this, "Cond");
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
                this.writer.write(" [label="+quote(node.label())+"]");
		this.writer.write(";\n");
	    }
	    for (DFNode node : graph.nodes) {
		for (DFLink link : node.send) {
		    this.writer.write(" N"+link.src.id+" -> N"+link.dst.id);
                    this.writer.write(" [label="+quote(link.name)+"]");
		    this.writer.write(";\n");
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

    public DFFlowSet processExpression
	(DFGraph graph, Expression expr, DFScope scope)
	throws UnsupportedSyntax {
	DFFlowSet fset = new DFFlowSet();

	if (expr instanceof Name) {
	    // Variable lookup.
	    DFRef ref = getReference(expr, scope);
	    DFNode value = new DistNode(graph);
	    fset.value = value;
	    fset.addFlow(new DFInputFlow(ref, value));
	    
	} else if (expr instanceof NumberLiteral) {
	    // Cosntant.
	    String value = ((NumberLiteral)expr).getToken();
	    fset.value = new ConstNode(graph, expr, value);
	    
	} else if (expr instanceof InfixExpression) {
	    // Infix operator.
	    InfixExpression infix = (InfixExpression)expr;
	    String op = infix.getOperator().toString();
	    DFFlowSet lset = processExpression(graph, infix.getLeftOperand(), scope);
	    DFFlowSet rset = processExpression(graph, infix.getRightOperand(), scope);
	    fset.value = new InfixNode(graph, expr, op, lset.value, rset.value);
	    fset.addFlowSet(lset);
	    fset.addFlowSet(rset);
	    
	} else if (expr instanceof Assignment) {
	    // Assignment.
	    Assignment assn = (Assignment)expr;
	    String op = assn.getOperator().toString();
	    DFRef ref = getReference(assn.getLeftHandSide(), scope);
	    DFNode lvalue = new DistNode(graph);
	    fset.addFlow(new DFInputFlow(ref, lvalue));
	    DFFlowSet rset = processExpression(graph, assn.getRightHandSide(), scope);
	    fset.value = new InfixNode(graph, assn, op, lvalue, rset.value);
	    fset.addFlow(new DFOutputFlow(fset.value, ref));
	    fset.addFlowSet(rset);
	    
	} else {
	    throw new UnsupportedSyntax(expr);
	}

	return fset;
    }
    
    @SuppressWarnings("unchecked")
    public DFFlowSet processVariableDeclarationStatement
	(DFGraph graph, VariableDeclarationStatement varStmt, DFScope scope)
	throws UnsupportedSyntax {
	DFFlowSet fset = new DFFlowSet();
	for (VariableDeclarationFragment frag :
		 (List<VariableDeclarationFragment>) varStmt.fragments()) {
	    Expression expr = frag.getInitializer();
	    if (expr != null) {
		Name varName = frag.getName();
		DFVar var = scope.lookup(varName.getFullyQualifiedName());
		DFFlowSet eset = processExpression(graph, expr, scope);
		fset.addFlowSet(eset);
		fset.addFlow(new DFOutputFlow(eset.value, var));
	    }
	}
	return fset;
    }

    public DFFlowSet processExpressionStatement
	(DFGraph graph, ExpressionStatement exprStmt, DFScope scope)
	throws UnsupportedSyntax {
	Expression expr = exprStmt.getExpression();
	return processExpression(graph, expr, scope);
    }

    public DFFlowSet processReturnStatement
	(DFGraph graph, ReturnStatement rtnStmt, DFScope scope)
	throws UnsupportedSyntax {
	Expression expr = rtnStmt.getExpression();
	DFFlowSet fset = processExpression(graph, expr, scope);
	fset.addFlow(new DFOutputFlow(fset.value, DFRef.RETURN));
	return fset;
    }
    
    public DFFlowSet processIfStatement
	(DFGraph graph, IfStatement ifStmt, DFScope scope)
	throws UnsupportedSyntax {
	DFFlowSet fset = new DFFlowSet();
	Expression expr = ifStmt.getExpression();
	DFFlowSet eset = processExpression(graph, expr, scope);
	fset.addFlowSet(eset);
	
	Statement thenStmt = ifStmt.getThenStatement();
	for (DFFlow flow : processStatement(graph, thenStmt, scope).flows) {
	    if (flow instanceof DFInputFlow) {
		DFInputFlow input = (DFInputFlow)flow;
		DFNode branch = new BranchNode(graph, ifStmt, eset.value);
		branch.connect(input.node);
		fset.addFlow(new DFInputFlow(input.ref, branch));
	    } else if (flow instanceof DFOutputFlow) {
		DFOutputFlow output = (DFOutputFlow)flow;
		DFNode join = new JoinNode(graph, ifStmt, eset.value);
		output.node.connect(join, "true");
		fset.addFlow(new DFInputFlow(output.ref, join));
		fset.addFlow(new DFOutputFlow(join, output.ref));
	    }
	}
	
	Statement elseStmt = ifStmt.getElseStatement();
	if (elseStmt != null) {
	    for (DFFlow flow : processStatement(graph, elseStmt, scope).flows) {
		if (flow instanceof DFInputFlow) {
		    DFInputFlow input = (DFInputFlow)flow;
		    DFNode branch = new BranchNode(graph, ifStmt, eset.value);
		    branch.connect(input.node);
		    fset.addFlow(new DFInputFlow(input.ref, branch));
		} else if (flow instanceof DFOutputFlow) {
		    DFOutputFlow output = (DFOutputFlow)flow;
		    DFNode join = new JoinNode(graph, ifStmt, eset.value);
		    output.node.connect(join, "false");
		    fset.addFlow(new DFInputFlow(output.ref, join));
		    fset.addFlow(new DFOutputFlow(join, output.ref));
		}
	    }
	}
	return fset;
    }

    public DFFlowSet processStatement
	(DFGraph graph, Statement stmt, DFScope scope)
	throws UnsupportedSyntax {
	DFFlowSet fset;
	
	if (stmt instanceof Block) {
	    fset = processBlock(graph, (Block)stmt, scope);
	    
	} else if (stmt instanceof VariableDeclarationStatement) {
	    fset = processVariableDeclarationStatement
		(graph, (VariableDeclarationStatement)stmt, scope);

	} else if (stmt instanceof ExpressionStatement) {
	    fset = processExpressionStatement
		(graph, (ExpressionStatement)stmt, scope);
		
	} else if (stmt instanceof ReturnStatement) {
	    fset = processReturnStatement
		(graph, (ReturnStatement)stmt, scope);
	    
	} else if (stmt instanceof IfStatement) {
	    fset = processIfStatement
		(graph, (IfStatement)stmt, scope);
	    
	} else {
	    throw new UnsupportedSyntax(stmt);
	}
	
	return fset;
    }
    
    @SuppressWarnings("unchecked")
    public DFFlowSet processBlock
	(DFGraph graph, Block block, DFScope parent)
	throws UnsupportedSyntax {
	DFScope scope = new DFScope(parent);
	DFBindings bindings = new DFBindings(graph);

	// Handle all variable declarations first.
	for (Statement stmt : (List<Statement>) block.statements()) {
	    if (stmt instanceof VariableDeclarationStatement) {
		VariableDeclarationStatement varStmt = (VariableDeclarationStatement)stmt;
		Type varType = varStmt.getType();
		for (VariableDeclarationFragment frag :
			 (List<VariableDeclarationFragment>) varStmt.fragments()) {
		    Name varName = frag.getName();
		    // XXX check getExtraDimensions()
		    scope.add(varName.getFullyQualifiedName(), getTypeName(varType));
		}
	    }
	}

	// Process each statement.
	for (Statement stmt : (List<Statement>) block.statements()) {
	    DFFlowSet fset = processStatement(graph, stmt, scope);
	    for (DFFlow flow : fset.flows) {
		if (flow instanceof DFInputFlow) {
		    DFInputFlow input = (DFInputFlow)flow;
		    DFNode src = bindings.get(input.ref);
		    src.connect(input.node);
		} else if (flow instanceof DFOutputFlow) {
		    DFOutputFlow output = (DFOutputFlow)flow;
		    if (output.ref == DFRef.RETURN) {
			bindings.setReturn(output.node);
		    } else {
			DFNode dst = new BoxNode(graph, null, output.ref);
			output.node.connect(dst);
			bindings.put(output.ref, dst);
		    }
		}
	    }
	}

	return bindings.finish(scope);
    }
    
    @SuppressWarnings("unchecked")
    public DFGraph getMethodGraph(MethodDeclaration method)
	throws UnsupportedSyntax {
	// XXX check isContructor()
	Name funcName = method.getName();
	Type funcType = method.getReturnType2();
	DFGraph graph = new DFGraph(funcName.getFullyQualifiedName());
	DFScope scope = new DFScope();
	DFBindings bindings = new DFBindings(graph);
	
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
	    DFNode dst = new BoxNode(graph, paramName, var);
	    param.connect(dst);
	    bindings.put(var, dst);
	}

	Block funcBlock = method.getBody();
	DFFlowSet fset = processBlock(graph, funcBlock, scope);
	for (DFFlow flow : fset.flows) {
	    if (flow instanceof DFInputFlow) {
		DFInputFlow input = (DFInputFlow)flow;
		DFNode src = bindings.get(input.ref);
		src.connect(input.node);
	    }
	}

        // Cleanup.
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
            if (link0.name == null || link1.name == null) {
                node.remove();
                String name = link0.name;
                if (name == null) {
                    name = link1.name;
                }
                link0.src.connect(link1.dst, name);
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
