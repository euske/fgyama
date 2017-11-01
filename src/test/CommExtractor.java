//  CommExtractor.java
//  Feature extractor for comments.
//

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

    private Stack<ASTNode> _stack = new Stack<ASTNode>();
    private Map<ASTNode, List<ASTNode> > _children =
	new HashMap<ASTNode, List<ASTNode> >();
    
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
	if (!_stack.empty()) {
	    ASTNode parent = _stack.peek();
	    List<ASTNode> children = _children.get(parent);
	    if (children == null) {
		children = new ArrayList<ASTNode>();
		_children.put(parent, children);
	    }
	    children.add(node);
	}
	_stack.push(node);
    }
    
    public void postVisit(ASTNode node) {
	int end = node.getStartPosition() + node.getLength();
	List<ASTNode> nodes = _end.get(end);
	if (nodes == null) {
	    nodes = new ArrayList<ASTNode>();
	    _end.put(end, nodes);
	}
	nodes.add(node);
	_stack.pop();
    }

    public void addComment(Comment node) {
	int start = node.getStartPosition();
	int end = start + node.getLength();
	String prevkey = null;
	boolean prevnl = false;
	SortedMap<Integer, List<ASTNode> > before = _end.headMap(start+1);
	if (!before.isEmpty()) {
	    int i = before.lastKey();
	    prevkey = toKey(before.get(i));
	    prevnl = hasNL(i, start);
	}
	SortedMap<Integer, List<ASTNode> > after = _start.tailMap(end);
	String nextkey = null;
	boolean nextnl = false;
	if (!after.isEmpty()) {
	    int i = after.firstKey();
	    nextkey = toKey(after.get(i));
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
	assert parent != null;
	int pstart = parent.getStartPosition();
	int pend = pstart + parent.getLength();
	out.println("parent="+getType(parent)+" "+
		    "pstart="+(pstart == start)+" "+
		    "pend="+(pend == end)+" "+
		    "prevkey="+prevkey+" "+
		    "prevnl="+prevnl+" "+
		    "type="+getType(node)+" "+
		    "nextnl="+nextnl+" "+
		    "nextkey="+nextkey+" "+
		    "words="+getWords(getText(node)));
    }

    private String toKey(Collection<ASTNode> nodes) {
	ASTNode[] sorted = new ASTNode[nodes.size()];
	nodes.toArray(sorted);
	Arrays.sort(sorted, (a, b) -> (b.getLength() - a.getLength()));
	String s = null;
	for (ASTNode node : sorted) {
	    if (s == null) {
		s = "";
	    } else {
		s += ",";
	    }
	    s += getType(node);
	}
	return s;
    }

    private boolean hasNL(int start, int end) {
	String s = this.src.substring(start, end);
	return 0 <= s.indexOf("\n");
    }

    private String getType(ASTNode node) {
	int type = node.getNodeType();
	return Utils.getASTNodeTypeName(type);
    }

    private String getText(ASTNode node) {
	int start = node.getStartPosition();
	int length = node.getLength();
	int end = start + length;
	return this.src.substring(start, end);
    }

    private String getWords(String text) {
	String[] words = text.split("\\W+");
	String s = null;
	for (String w : words) {
	    if (w.length() == 0) {
		;
	    } else if (s == null) {
		s = w;
	    } else {
		s += ","+w;
	    }
	}
	return s;
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
