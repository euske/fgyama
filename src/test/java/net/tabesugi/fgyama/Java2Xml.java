/**
 * Java2Xml
 * Utility for probing JDT behaviors
 *
 * Relevant resources used:
 *   http://www.vogella.com/tutorials/EclipseJDT/article.html
 *   http://stackoverflow.com/questions/37028368/astparser-setting-environment-manually
 *   http://stackoverflow.com/questions/18939857/how-to-get-a-class-name-of-a-method-by-using-eclipse-jdt-astparser
 */
package net.tabesugi.fgyama;

import java.io.*;
import java.util.*;
import javax.xml.parsers.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;
import org.w3c.dom.*;
import net.tabesugi.fgyama.*;


//  Java2Xml
//
public class Java2Xml extends ASTVisitor {

    public static void usage() {
        System.out.println("usage: Java2Xml [-cp classpath] [-o output] [-c] src.java ...");
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args)
        throws IOException, ParserConfigurationException {

        String[] srcpath = { "." };
        List<String> files = new ArrayList<String>();
        OutputStream output = System.out;
        boolean comments = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--")) {
                for (; i < args.length; i++) {
                    files.add(args[i]);
                }
            } else if (arg.equals("-o")) {
                output = new FileOutputStream(args[i+1]);
                i++;
            } else if (arg.startsWith("-cp")) {
                srcpath = args[i+1].split(":");
                i++;
            } else if (arg.startsWith("-c")) {
                comments = true;
            } else if (arg.startsWith("-")) {
                usage();
                return;
            } else {
                files.add(arg);
            }
        }

        for (String path : files) {
            Logger.info("Parsing: "+path);
            String src = Utils.readFile(path);

            Map<String, String> options = JavaCore.getOptions();
            JavaCore.setComplianceOptions(JavaCore.VERSION_1_7, options);
            ASTParser parser = ASTParser.newParser(AST.JLS_Latest);
            parser.setUnitName(path);
            parser.setSource(src.toCharArray());
            parser.setKind(ASTParser.K_COMPILATION_UNIT);
            parser.setResolveBindings(true);
            parser.setEnvironment(null, srcpath, null, true);
            parser.setCompilerOptions(options);
            CompilationUnit cu = (CompilationUnit)parser.createAST(null);

            Document doc = Utils.createXml();
            Java2Xml visitor = new Java2Xml(doc);
            cu.accept(visitor);

            if (comments) {
                // Add comments.
                Element root = doc.getDocumentElement();
                for (org.eclipse.jdt.core.dom.Comment node :
                         (List<org.eclipse.jdt.core.dom.Comment>) cu.getCommentList()) {
                    int type = node.getNodeType();
                    String name = Utils.getASTNodeTypeName(type);
                    Element elem = doc.createElement(name);
                    int start = node.getStartPosition();
                    int length = node.getLength();
                    String text = src.substring(start, start+length);
                    elem.appendChild(doc.createTextNode(text));
                    root.appendChild(elem);
                }
            }

            Utils.printXml(output, doc);
        }
        output.close();
    }

    private Document _document;
    private Stack<Element> _stack;

    public Java2Xml(Document document) {
        _document = document;
        _stack = new Stack<Element>();
    }

    // Traverse the tree.

    public void preVisit(ASTNode node) {
        int type = node.getNodeType();
        String name = Utils.getASTNodeTypeName(type);
        Element elem = _document.createElement(name);
        if (node instanceof Type) {
            IBinding binding = ((Type)node).resolveBinding();
            if (binding != null) {
                elem.setAttribute("binding", binding.getKey());
            }
        } else if (node instanceof Name) {
            IBinding binding = ((Name)node).resolveBinding();
            if (binding != null) {
                elem.setAttribute("binding", binding.getKey());
            }
        }
        if (_stack.empty()) {
            _document.appendChild(elem);
        } else {
            appendTop(elem);
        }
        _stack.push(elem);
    }

    public void postVisit(ASTNode node) {
        _stack.pop();
    }

    private void appendTop(Node node) {
        _stack.peek().appendChild(node);
    }
    private void appendAttr(String key, String value) {
        _stack.peek().setAttribute(key, value);
    }
    private void appendText(String text) {
        appendTop(_document.createTextNode(text));
    }

    public boolean visit(SimpleName node) {
        appendText(node.getFullyQualifiedName());
        return true;
    }
    public boolean visit(Modifier node) {
        appendText(node.getKeyword().toString());
        return true;
    }
    public boolean visit(PrimitiveType node) {
        appendText(node.getPrimitiveTypeCode().toString());
        return true;
    }
    public boolean visit(BooleanLiteral node) {
        appendText(Boolean.toString(node.booleanValue()));
        return true;
    }
    public boolean visit(CharacterLiteral node) {
        appendText(Character.toString(node.charValue()));
        return true;
    }
    public boolean visit(NumberLiteral node) {
        appendText(node.getToken());
        return true;
    }
    public boolean visit(StringLiteral node) {
        appendText(node.getLiteralValue());
        return true;
    }
    public boolean visit(InfixExpression node) {
        appendAttr("operator", node.getOperator().toString());
        return true;
    }
    public boolean visit(PostfixExpression node) {
        appendAttr("operator", node.getOperator().toString());
        return true;
    }
    public boolean visit(PrefixExpression node) {
        appendAttr("operator", node.getOperator().toString());
        return true;
    }
    public boolean visit(Assignment node) {
        appendAttr("operator", node.getOperator().toString());
        return true;
    }
}
