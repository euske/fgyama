//  CommentExtractor.java
//  Feature extractor for comments.
//

import java.io.*;
import java.util.*;
import javax.xml.parsers.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;
import net.tabesugi.fgyama.*;

public class CommentExtractor extends ASTVisitor {

    private String src;

    private SortedMap<Integer, List<ASTNode> > _start =
	new TreeMap<Integer, List<ASTNode> >();
    private SortedMap<Integer, List<ASTNode> > _end =
	new TreeMap<Integer, List<ASTNode> >();

    private Stack<ASTNode> _stack = new Stack<ASTNode>();
    private Map<ASTNode, ASTNode> _parent =
	new HashMap<ASTNode, ASTNode>();

    public CommentExtractor(String src) {
	this.src = src;
    }

    public void preVisit(ASTNode node) {
	addNode(node);
	if (!_stack.empty()) {
	    ASTNode parent = _stack.peek();
	    _parent.put(node, parent);
	}
	_stack.push(node);
    }

    public void postVisit(ASTNode node) {
	_stack.pop();
    }

    public void addNode(ASTNode node) {
	int start = node.getStartPosition();
	int end = node.getStartPosition() + node.getLength();
	List<ASTNode> nodes = _start.get(start);
	if (nodes == null) {
	    nodes = new ArrayList<ASTNode>();
	    _start.put(start, nodes);
	}
	nodes.add(node);
	nodes = _end.get(end);
	if (nodes == null) {
	    nodes = new ArrayList<ASTNode>();
	    _end.put(end, nodes);
	}
	nodes.add(node);
    }

    public List<ASTNode> getParentNodes(ASTNode node) {
	ArrayList<ASTNode> parents = new ArrayList<ASTNode>();
	while (node != null) {
	    parents.add(node);
	    node = _parent.get(node);
	}
	return parents;
    }

    public List<ASTNode> getNodesEndBefore(int i) {
	SortedMap<Integer, List<ASTNode> > before = _end.headMap(i+1);
	if (before.isEmpty()) return null;
	i = before.lastKey();
	return before.get(i);
    }

    public List<ASTNode> getNodesStartAfter(int i) {
	SortedMap<Integer, List<ASTNode> > after = _start.tailMap(i);
	if (after.isEmpty()) return null;
	i = after.firstKey();
	return after.get(i);
    }

    public Set<ASTNode> getNodesOutside(int start, int end) {
	Set<ASTNode> nodes0 = new HashSet<ASTNode>();
	for (List<ASTNode> nodes : _start.headMap(start+1).values()) {
	    nodes0.addAll(nodes);
	}
	Set<ASTNode> nodes1 = new HashSet<ASTNode>();
	for (List<ASTNode> nodes : _end.tailMap(end).values()) {
	    nodes1.addAll(nodes);
	}
	nodes0.retainAll(nodes1);
	return nodes0;
    }

    public String getFeatures(Comment node) {
	int start = node.getStartPosition();
	int end = start + node.getLength();
	String leftkey = null;
	int leftnl = 0;
	List<ASTNode> before = getNodesEndBefore(start);
	if (before != null) {
	    ASTNode n = before.get(0);
	    leftkey = toKeySorted(before);
	    leftnl = countNL(n.getStartPosition()+n.getLength(), start);
	}
	String rightkey = null;
	int rightnl = 0;
	List<ASTNode> after = getNodesStartAfter(end);
	if (after != null) {
	    ASTNode n = after.get(0);
	    rightkey = toKeySorted(after);
	    rightnl = countNL(end, n.getStartPosition());
	}
	Collection<ASTNode> outside = getNodesOutside(start, end);
	ASTNode parent = null;
	for (ASTNode n : outside) {
	    if (n == node) continue;
	    if (parent == null || n.getLength() < parent.getLength()) {
		parent = n;
	    }
	}
	assert parent != null;
	int pstart = parent.getStartPosition();
	int pend = pstart + parent.getLength();
	List<ASTNode> parents = getParentNodes(parent);
	return ("type="+getType(node)+" "+
		"parent="+toKey(parents)+" "+
		"pstart="+(pstart == start)+" "+
                "pos="+getPos(start)+" "+
		"pend="+(pend == end)+" "+
		"leftkey="+leftkey+" "+
		"leftnl="+leftnl+" "+
		"rightnl="+rightnl+" "+
		"rightkey="+rightkey+" "+
		"words="+getWords(getText(node)));
    }

    private String toKey(List<ASTNode> nodes) {
	String[] names = new String[nodes.size()];
	for (int i = 0; i < nodes.size(); i++) {
	    names[i] = getType(nodes.get(i));
	}
	return join(names);
    }

    private String toKeySorted(Collection<ASTNode> nodes) {
	ASTNode[] sorted = new ASTNode[nodes.size()];
	nodes.toArray(sorted);
	Arrays.sort(sorted, (a, b) -> (a.getLength() - b.getLength()));
	String[] names = new String[sorted.length];
	for (int i = 0; i < sorted.length; i++) {
	    names[i] = getType(sorted[i]);
	}
	return join(names);
    }

    private int getPos(int end) {
        final int TAB = 8;
        int i = this.src.lastIndexOf('\n', end)+1;
        int n = 0;
        for (char c : this.src.substring(i, end).toCharArray()) {
            if (c == '\t') {
                n = ((n/TAB)+1) * TAB;
            } else {
                n++;
            }
        }
        return n;
    }

    private int countNL(int start, int end) {
	int n = 0;
	while (true) {
	    start = this.src.indexOf('\n', start);
	    if (start < 0 || end <= start) break;
	    start++; n++;
	}
	return n;
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
	return join(text.split("\\W+"));
    }

    private static String join(String[] words) {
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

	int context = 100;
	List<String> files = new ArrayList<String>();
	PrintStream out = System.out;
	for (int i = 0; i < args.length; i++) {
	    String arg = args[i];
	    if (arg.equals("--")) {
		for (; i < args.length; i++) {
		    files.add(args[i]);
		}
	    } else if (arg.equals("-o")) {
		out = new PrintStream(new FileOutputStream(args[i+1]));
		i++;
	    } else if (arg.startsWith("-")) {
	    } else {
		files.add(arg);
	    }
	}

	String[] srcpath = { "." };
	for (String path : files) {
	    Utils.logit("Parsing: "+path);
	    out.println("+ "+path);
	    String src = Utils.readFile(path);

	    Map<String, String> options = JavaCore.getOptions();
	    JavaCore.setComplianceOptions(JavaCore.VERSION_1_7, options);
	    ASTParser parser = ASTParser.newParser(AST.JLS8);
	    parser.setUnitName(path);
	    parser.setSource(src.toCharArray());
	    parser.setKind(ASTParser.K_COMPILATION_UNIT);
	    parser.setResolveBindings(false);
	    parser.setEnvironment(null, srcpath, null, true);
	    parser.setCompilerOptions(options);
	    CompilationUnit cu = (CompilationUnit)parser.createAST(null);

	    CommentExtractor visitor = new CommentExtractor(src);
	    cu.accept(visitor);

            for (Comment node : (List<Comment>) cu.getCommentList()) {
		visitor.addNode(node);
	    }
            for (Comment node : (List<Comment>) cu.getCommentList()) {
		int start = node.getStartPosition();
		int end = node.getStartPosition() + node.getLength();
		String feats = visitor.getFeatures(node);
		out.println("- "+start+" "+end+" "+feats);
	    }
	    out.println();
	}

	out.close();
    }
}
