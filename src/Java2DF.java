//  Java2DF.java
//
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.dom.*;


//  UnsupportedSyntax
//
class UnsupportedSyntax extends Exception {

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

}


//  DFNode
//
class DFNode {

    public DFGraph graph;
    public int id;
    public String name;
    public List<DFLink> send;
    public List<DFLink> recv;
    
    public DFNode(DFGraph graph) {
	this.graph = graph;
	this.send = new ArrayList<DFLink>();
	this.recv = new ArrayList<DFLink>();
	this.id = this.graph.addNode(this);
    }

    public DFLink connect(DFNode dst) {
	return this.connect(dst, null);
    }
    
    public DFLink connect(DFNode dst, String label) {
	DFLink link = new DFLink(this, dst, label);
	this.send.add(link);
	dst.recv.add(link);
	return link;
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
    
    public DFRef(DFScope scope) {
	this.scope = scope;
    }
}


//  DFVar
//
class DFVar extends DFRef {

    public String type;

    public DFVar(DFScope scope, String name, String type) {
	super(scope);
	this.name = name;
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

    public DFRef ref;
    public DFNode node;

    public DFFlow(DFRef ref, DFNode node) {
	this.ref = ref;
	this.node = node;
    }

}


//  DFFlowSet
//
class DFFlowSet {

    public List<DFFlow> inputs;
    public List<DFFlow> outputs;
    public DFNode value;

    public DFFlowSet() {
	this.inputs = new ArrayList<DFFlow>();
	this.outputs = new ArrayList<DFFlow>();
    }

    public void addInput(DFFlow flow) {
	this.inputs.add(flow);
    }

    public void addOutput(DFFlow flow) {
	this.outputs.add(flow);
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
	this.retval = new InnerNode(this.graph);
    }

    public DFNode get(DFRef ref) {
	DFNode node = this.bindings.get(ref);
	if (node == null) {
	    node = this.inputs.get(ref);
	    if (node == null) {
		node = new InnerNode(this.graph);
		this.inputs.put(ref, node);
		this.bindings.put(ref, node);
	    }
	}
	return node;
    }

    public void put(DFRef ref, DFNode node) {
	this.bindings.put(ref, node);
    }

    public void setReturn(DFNode node) {
	node.connect(this.retval);
    }

    public DFFlowSet finish(DFScope scope) {
	DFFlowSet fset = new DFFlowSet();
	for (DFRef ref : scope.pop()) {
	    this.bindings.remove(ref);
	}
	for (DFRef ref : this.inputs.keySet()) {
	    fset.addInput(new DFFlow(ref, this.inputs.get(ref)));
	}
	for (DFRef ref : this.bindings.keySet()) {
	    fset.addOutput(new DFFlow(ref, this.bindings.get(ref)));
	}
	return fset;
    }

}


// InnerNode
class InnerNode extends DFNode {

    public InnerNode(DFGraph graph) {
	super(graph);
    }
}

// ProgNode
class ProgNode extends DFNode {

    public ASTNode node;
    
    public ProgNode(DFGraph graph, ASTNode node) {
	super(graph);
	this.node = node;
    }
}

// ArgNode
class ArgNode extends ProgNode {

    public int index;

    public ArgNode(DFGraph graph, ASTNode node, int index) {
	super(graph, node);
	this.name = "Arg"+index;
	this.index = index;
    }
}

// RefNode
class RefNode extends ProgNode {

    public DFRef ref;
    
    public RefNode(DFGraph graph, ASTNode node, DFRef ref) {
	super(graph, node);
	this.name = ref.name;
	this.ref = ref;
    }
}

// ConstNode
class ConstNode extends ProgNode {

    String value;

    public ConstNode(DFGraph graph, ASTNode node, String value) {
	super(graph, node);
	this.name = value;
	this.value = value;
    }
}

// InfixNode
class InfixNode extends ProgNode {

    String op;
    DFNode lvalue;
    DFNode rvalue;

    public InfixNode(DFGraph graph, ASTNode node,
		     String op, DFNode lvalue, DFNode rvalue) {
	super(graph, node);
	this.name = op;
	this.lvalue = lvalue;
	this.rvalue = rvalue;
	lvalue.connect(this, "L");
	rvalue.connect(this, "R");
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
		if (node.name != null) {
		    this.writer.write(" [label="+quote(node.name)+"]");
		}
		this.writer.write(";\n");
	    }
	    for (DFNode node : graph.nodes) {
		for (DFLink link : node.send) {
		    this.writer.write(" N"+link.src.id+" -> N"+link.dst.id);
		    if (link.name != null) {
			this.writer.write(" [label="+quote(link.name)+"]");
		    }
		    this.writer.write(";\n");
		}
	    }
	    this.writer.write("}\n");
	    this.writer.flush();
	} catch (IOException e) {
	}
    }

    public static String quote(String s) {
	return "\"" + s.replace("\"", "\\\"") + "\"";
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
	    DFGraph graph = getGraph(method);
	    exporter.writeGraph(graph);
	} catch (UnsupportedSyntax e) {
	    logit("Unsupported: "+e.node);
	}
	return true;
    }

    public DFRef getReference(Expression node, DFScope scope) 
	throws UnsupportedSyntax {
	if (node instanceof Name) {
	    Name varName = (Name)node;
	    return scope.lookup(varName.getFullyQualifiedName());
	} else {
	    throw new UnsupportedSyntax(node);
	}
    }

    public DFFlowSet processExpression(DFGraph graph, Expression node, DFScope scope)
	throws UnsupportedSyntax {
	DFFlowSet fset = new DFFlowSet();

	if (node instanceof Name) {
	    // Variable lookup.
	    DFRef ref = getReference(node, scope);
	    fset.value = new InnerNode(graph);
	    fset.addInput(new DFFlow(ref, fset.value));
	    
	} else if (node instanceof NumberLiteral) {
	    // Cosntant.
	    String value = ((NumberLiteral)node).getToken();
	    fset.value = new ConstNode(graph, node, value);
	    
	} else if (node instanceof InfixExpression) {
	    // Infix operator.
	    InfixExpression expr = (InfixExpression)node;
	    String op = expr.getOperator().toString();
	    DFFlowSet lset = processExpression(graph, expr.getLeftOperand(), scope);
	    DFFlowSet rset = processExpression(graph, expr.getRightOperand(), scope);
	    fset.value = new InfixNode(graph, node, op, lset.value, rset.value);
	    for (DFFlow flow : lset.inputs) {
		fset.addInput(flow);
	    }
	    for (DFFlow flow : lset.outputs) {
		fset.addOutput(flow);
	    }
	    for (DFFlow flow : rset.inputs) {
		fset.addInput(flow);
	    }
	    for (DFFlow flow : rset.outputs) {
		fset.addInput(flow);
	    }
	    
	} else if (node instanceof Assignment) {
	    // Assignment.
	    Assignment assn = (Assignment)node;
	    String op = assn.getOperator().toString();
	    DFRef ref = getReference(assn.getLeftHandSide(), scope);
	    DFNode lvalue = new InnerNode(graph);
	    fset.addInput(new DFFlow(ref, lvalue));
	    DFFlowSet rset = processExpression(graph, assn.getRightHandSide(), scope);
	    fset.value = new InfixNode(graph, node, op, lvalue, rset.value);
	    DFNode lref = new RefNode(graph, assn.getLeftHandSide(), ref);
	    fset.addOutput(new DFFlow(ref, lref));
	    fset.value.connect(lref);
	    for (DFFlow flow : rset.inputs) {
		fset.addInput(flow);
	    }
	    for (DFFlow flow : rset.outputs) {
		fset.addInput(flow);
	    }
	    
	} else {
	    throw new UnsupportedSyntax(node);
	}

	return fset;
    }
    
    public DFFlowSet processBlock(DFGraph graph, Block block, DFScope parent)
	throws UnsupportedSyntax {
	DFScope scope = new DFScope(parent);
	DFBindings bindings = new DFBindings(graph);

	for (Statement stmt : (List<Statement>) block.statements()) {
	    
	    if (stmt instanceof VariableDeclarationStatement) {
		// Variable declaration.
		VariableDeclarationStatement varStmt = (VariableDeclarationStatement)stmt;
		Type varType = varStmt.getType();
		for (VariableDeclarationFragment frag :
			 (List<VariableDeclarationFragment>) varStmt.fragments()) {
		    Name varName = frag.getName();
		    // XXX check getExtraDimensions()
		    DFVar var = scope.add(varName.getFullyQualifiedName(),
					  getTypeName(varType));
		    Expression expr = frag.getInitializer();
		    if (expr != null) {
			DFFlowSet fset = processExpression(graph, expr, scope);
			DFNode dst = new RefNode(graph, varName, var);
			for (DFFlow flow : fset.inputs) {
			    DFNode src = bindings.get(flow.ref);
			    src.connect(flow.node);
			}
			fset.value.connect(dst);
			bindings.put(var, dst);
		    }
		}

	    } else if (stmt instanceof ExpressionStatement) {
		// Normal expression.
		ExpressionStatement exprStmt = (ExpressionStatement)stmt;
		Expression expr = exprStmt.getExpression();
		DFFlowSet fset = processExpression(graph, expr, scope);
		for (DFFlow flow : fset.inputs) {
		    DFNode src = bindings.get(flow.ref);
		    src.connect(flow.node);
		}
		for (DFFlow flow : fset.outputs) {
		    DFNode dst = new RefNode(graph, null, flow.ref);
		    flow.node.connect(dst);
		    bindings.put(flow.ref, dst);
		}
		
	    } else if (stmt instanceof ReturnStatement) {
		// Return.
		ReturnStatement rtnStmt = (ReturnStatement)stmt;
		Expression expr = rtnStmt.getExpression();
		DFFlowSet fset = processExpression(graph, expr, scope);
		for (DFFlow flow : fset.inputs) {
		    DFNode src = bindings.get(flow.ref);
		    src.connect(flow.node);
		}
		bindings.setReturn(fset.value);
		
	    } else {
		throw new UnsupportedSyntax(stmt);
	    }
	    
	}

	return bindings.finish(scope);
    }
    
    public DFGraph getGraph(MethodDeclaration node)
	throws UnsupportedSyntax {
	// XXX check isContructor()
	Name funcName = node.getName();
	Type funcType = node.getReturnType2();
	DFGraph graph = new DFGraph(funcName.getFullyQualifiedName());
	DFScope scope = new DFScope();
	DFBindings bindings = new DFBindings(graph);
	
	int i = 0;
	// XXX check isVarargs()
	for (SingleVariableDeclaration decl :
		 (List<SingleVariableDeclaration>) node.parameters()) {
	    DFNode param = new ArgNode(graph, decl, i++);
	    Name paramName = decl.getName();
	    Type paramType = decl.getType();
	    // XXX check getExtraDimensions()
	    DFVar var = scope.add(paramName.getFullyQualifiedName(),
				  getTypeName(paramType));
	    DFNode dst = new RefNode(graph, paramName, var);
	    param.connect(dst);
	    bindings.put(var, dst);
	}

	Block funcBlock = node.getBody();
	DFFlowSet fset = processBlock(graph, funcBlock, scope);
	for (DFFlow flow : fset.inputs) {
	    DFNode src = bindings.get(flow.ref);
	    src.connect(flow.node);
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
