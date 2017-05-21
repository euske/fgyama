// http://stackoverflow.com/questions/37028368/astparser-setting-environment-manually
// http://stackoverflow.com/questions/18939857/how-to-get-a-class-name-of-a-method-by-using-eclipse-jdt-astparser

// javac -cp org.eclipse.jdt.core.jar:org.eclipse.core.runtime.jar:org.eclipse.equinox.common.jar Test.java
// java -cp .:org.eclipse.jdt.core.jar:org.eclipse.core.runtime.jar:org.eclipse.equinox.common.jar:org.eclipse.core.resources.jar:org.eclipse.core.jobs.jar:org.eclipse.osgi.jar:org.eclipse.core.contenttype.jar:org.eclipse.equinox.preferences.jar Test

import java.io.*;
import org.eclipse.jdt.core.dom.*;

public class JDTParser extends ASTVisitor {

    public static void main(String[] args) {
	String[] classpath = new String[] { "/" };
	for (String path : args) {
	    System.err.println("Parsing: "+path);
	    try {
		BufferedReader reader = new BufferedReader(new FileReader(path));
		String src = "";
		while (true) {
		    String line = reader.readLine();
		    if (line == null) break;
		    src += line+"\n";
		}
		reader.close();

		ASTParser parser = ASTParser.newParser(AST.JLS8);
		parser.setSource(src.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(true);
		parser.setEnvironment(classpath, null, null, true);
		CompilationUnit cu = (CompilationUnit)parser.createAST(null);
		JDTParser visitor = new JDTParser();
		cu.accept(visitor);
	    } catch (IOException e) {
		System.err.println("Error: "+e);
	    }
	}
    }

    public void preVisit(ASTNode node) {
	int type = node.getNodeType();
	System.out.print("<"+type+">");
    }
    public void postVisit(ASTNode node) {
	int type = node.getNodeType();
	System.out.print("</"+type+">");
    }

    public boolean visit(SimpleName node) {
	System.out.print(node.getFullyQualifiedName());
	return true;
    }
    public boolean visit(SimpleType node) {
	System.out.print(node.getName());
	return true;
    }
}
