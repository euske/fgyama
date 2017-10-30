import java.io.*;
import java.util.*;
import javax.xml.parsers.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;
import net.tabesugi.fgyama.*;

public class CommExtractor extends ASTVisitor {

    private String src;
    private PrintStream out;
    
    private SortedMap<Integer, ASTNode> _start = new TreeMap<Integer, ASTNode>();
    private SortedMap<Integer, ASTNode> _end = new TreeMap<Integer, ASTNode>();
    
    public CommExtractor(String src, OutputStream output) {
	this.src = src;
	this.out = new PrintStream(output);
    }

    public void preVisit(ASTNode node1) {
	int start = node1.getStartPosition();
	ASTNode node0 = _start.get(start);
	if (node0 == null || node0.getLength() < node1.getLength()) {
	    _start.put(start, node1);
	}
    }
    
    public void postVisit(ASTNode node1) {
	int end = node1.getStartPosition() + node1.getLength();
	ASTNode node0 = _end.get(end);
	if (node0 == null || node0.getLength() < node1.getLength()) {
	    _end.put(end, node1);
	}
    }

    public void addComment(Comment node) {
	int start = node.getStartPosition();
	int length = node.getLength();
	int end = start + length;
	ASTNode prev = null;
	boolean prevnl = false;
	SortedMap<Integer, ASTNode> before = _end.headMap(start);
	if (!before.isEmpty()) {
	    prev = before.get(before.lastKey());
	    prevnl = hasNL(prev.getStartPosition() + prev.getLength(), start);
	}
	SortedMap<Integer, ASTNode> after = _start.tailMap(end);
	ASTNode next = null;
	boolean nextnl = false;
	if (!after.isEmpty()) {
	    next = after.get(after.firstKey());
	    nextnl = hasNL(end, next.getStartPosition());
	}
	Collection<ASTNode> nodes0 = _start.headMap(start).values();
	Collection<ASTNode> nodes1 = _end.tailMap(end).values();
	ASTNode parent = null;
	for (ASTNode n : nodes0) {
	    if (nodes1.contains(n)) {
		if (parent == null || n.getLength() < parent.getLength()) {
		    parent = n;
		}
	    }
	}
	out.println(toStr(parent)+" "+toStr(prev)+" "+prevnl+" "+toStr(node)+" "+nextnl+" "+toStr(next)+" "+getText(node));
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
