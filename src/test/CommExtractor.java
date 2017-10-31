import java.io.*;
import java.util.*;
import javax.xml.parsers.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;
import net.tabesugi.fgyama.*;

public class CommExtractor extends ASTVisitor {

    private String src;
    private PrintStream out;
    
    private SortedMap<Integer, List<ASTNode> > _start =
	new TreeMap<Integer, List<ASTNode> >();
    private SortedMap<Integer, List<ASTNode> > _end =
	new TreeMap<Integer, List<ASTNode> >();
    
    public CommExtractor(String src, OutputStream output) {
	this.src = src;
	this.out = new PrintStream(output);
    }

    public void preVisit(ASTNode node) {
	int start = node.getStartPosition();
	List<ASTNode> nodes = _start.get(start);
	if (nodes == null) {
	    nodes = new ArrayList<ASTNode>();
	    _start.put(start, nodes);
	}
	nodes.add(node);
    }
    
    public void postVisit(ASTNode node) {
	int end = node.getStartPosition() + node.getLength();
	List<ASTNode> nodes = _end.get(end);
	if (nodes == null) {
	    nodes = new ArrayList<ASTNode>();
	    _end.put(end, nodes);
	}
	nodes.add(node);
    }

    public void addComment(Comment node) {
	int start = node.getStartPosition();
	int length = node.getLength();
	int end = start + length;
	ASTNode prev = null;
	boolean prevnl = false;
	SortedMap<Integer, List<ASTNode> > before = _end.headMap(start+1);
	if (!before.isEmpty()) {
	    int i = before.lastKey();
	    prev = pickOne(before.get(i));
	    prevnl = hasNL(i, start);
	}
	SortedMap<Integer, List<ASTNode> > after = _start.tailMap(end);
	ASTNode next = null;
	boolean nextnl = false;
	if (!after.isEmpty()) {
	    int i = after.firstKey();
	    next = pickOne(after.get(i));
	    nextnl = hasNL(end, i);
	}
	Set<ASTNode> nodes0 = new HashSet<ASTNode>();
	for (List<ASTNode> nodes : _start.headMap(start+1).values()) {
	    nodes0.addAll(nodes);
	}
	Set<ASTNode> nodes1 = new HashSet<ASTNode>();
	for (List<ASTNode> nodes : _end.tailMap(end).values()) {
	    nodes1.addAll(nodes);
	}
	nodes0.retainAll(nodes1);
	ASTNode parent = null;
	for (ASTNode n : nodes0) {
	    if (parent == null || n.getLength() < parent.getLength()) {
		parent = n;
	    }
	}
	out.println(toStr(parent)+" "+toStr(prev)+" "+prevnl+" "+toStr(node)+" "+nextnl+" "+toStr(next)+" "+getText(node));
    }

    private ASTNode pickOne(Collection<ASTNode> nodes) {
	ASTNode node0 = null;
	for (ASTNode node1 : nodes) {
	    if (node0 == null || node0.getLength() < node1.getLength()) {
		node0 = node1;
	    }
	}
	return node0;
    }

    private boolean hasNL(int start, int end) {
	String s = this.src.substring(start, end);
	return 0 <= s.indexOf("\n");
    }

    private String toStr(ASTNode node) {
	if (node == null) {
	    return "null";
	} else {
	    int type = node.getNodeType();
	    return Utils.getASTNodeTypeName(type);
	}
    }

    private String getText(ASTNode node) {
	int start = node.getStartPosition();
	int length = node.getLength();
	int end = start + length;
	return this.src.substring(start, end);
    }
    
    @SuppressWarnings("unchecked")
    public static void main(String[] args)
	throws IOException {
	
	List<String> files = new ArrayList<String>();
	OutputStream output = System.out;
	for (int i = 0; i < args.length; i++) {
	    String arg = args[i];
	    if (arg.equals("--")) {
		for (; i < args.length; i++) {
		    files.add(args[i]);
		}
	    } else if (arg.equals("-o")) {
		output = new FileOutputStream(args[i+1]);
		i++;
	    } else if (arg.startsWith("-")) {
	    } else {
		files.add(arg);
	    }
	}

	String[] srcpath = { "." };
	for (String path : files) {
	    Utils.logit("Parsing: "+path);
	    String src = Utils.readFile(path);

	    Map<String, String> options = JavaCore.getOptions();
	    JavaCore.setComplianceOptions(JavaCore.VERSION_1_7, options);
	    ASTParser parser = ASTParser.newParser(AST.JLS8);
	    parser.setUnitName(path);
	    parser.setSource(src.toCharArray());
	    parser.setKind(ASTParser.K_COMPILATION_UNIT);
	    parser.setResolveBindings(true);
	    parser.setEnvironment(null, srcpath, null, true);
	    parser.setCompilerOptions(options);
	    CompilationUnit cu = (CompilationUnit)parser.createAST(null);

	    CommExtractor visitor = new CommExtractor(src, output);
	    cu.accept(visitor);
	    
            for (Comment node : (List<Comment>) cu.getCommentList()) {
		visitor.addComment(node);
	    }		
	}
	
	output.close();
    }
}
