// http://stackoverflow.com/questions/37028368/astparser-setting-environment-manually
// http://stackoverflow.com/questions/18939857/how-to-get-a-class-name-of-a-method-by-using-eclipse-jdt-astparser
// http://www.vogella.com/tutorials/EclipseJDT/article.html

// javac -cp org.eclipse.jdt.core.jar:org.eclipse.core.runtime.jar:org.eclipse.equinox.common.jar Test.java
// java -cp .:org.eclipse.jdt.core.jar:org.eclipse.core.runtime.jar:org.eclipse.equinox.common.jar:org.eclipse.core.resources.jar:org.eclipse.core.jobs.jar:org.eclipse.osgi.jar:org.eclipse.core.contenttype.jar:org.eclipse.equinox.preferences.jar Test

import java.io.*;
import java.util.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import org.eclipse.jdt.core.dom.*;
import org.w3c.dom.*;

public class Java2Xml extends ASTVisitor {

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

		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
		Document doc = docBuilder.newDocument();
		
		Java2Xml visitor = new Java2Xml(doc);
		cu.accept(visitor);

		TransformerFactory transFactory = TransformerFactory.newInstance();
		Transformer transformer = transFactory.newTransformer();
		DOMSource source = new DOMSource(doc);
		StreamResult result = new StreamResult(System.out);
		transformer.transform(source, result);
		
	    } catch (IOException e) {
		e.printStackTrace();
	    } catch (ParserConfigurationException e) {
		e.printStackTrace();
	    } catch (TransformerException e) {
		e.printStackTrace();
	    }
	}
    }

    private Document _document;
    private Stack<Element> _stack;

    public Java2Xml(Document document) {
	_document = document;
	_stack = new Stack<Element>();
    }
    
    public void preVisit(ASTNode node) {
	int type = node.getNodeType();
	String name = getNodeTypeName(type);
	Element elem = _document.createElement(name);
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

    public static String getNodeTypeName(int type) {
	switch (type) {
	case ASTNode.ANNOTATION_TYPE_DECLARATION:
	    return "AnnotationTypeDeclaration";
	case ASTNode.ANNOTATION_TYPE_MEMBER_DECLARATION:
	    return "AnnotationTypeMemberDeclaration";
	case ASTNode.ANONYMOUS_CLASS_DECLARATION:
	    return "AnonymousClassDeclaration";
	case ASTNode.ARRAY_ACCESS:
	    return "ArrayAccess";
	case ASTNode.ARRAY_CREATION:
	    return "ArrayCreation";
	case ASTNode.ARRAY_INITIALIZER:
	    return "ArrayInitializer";
	case ASTNode.ARRAY_TYPE:
	    return "ArrayType";
	case ASTNode.ASSERT_STATEMENT:
	    return "AssertStatement";
	case ASTNode.ASSIGNMENT:
	    return "Assignment";
	case ASTNode.BLOCK:
	    return "Block";
	case ASTNode.BLOCK_COMMENT:
	    return "BlockComment";
	case ASTNode.BOOLEAN_LITERAL:
	    return "BooleanLiteral";
	case ASTNode.BREAK_STATEMENT:
	    return "BreakStatement";
	case ASTNode.CAST_EXPRESSION:
	    return "CastExpression";
	case ASTNode.CATCH_CLAUSE:
	    return "CatchClause";
	case ASTNode.CHARACTER_LITERAL:
	    return "CharacterLiteral";
	case ASTNode.CLASS_INSTANCE_CREATION:
	    return "ClassInstanceCreation";
	case ASTNode.COMPILATION_UNIT:
	    return "CompilationUnit";
	case ASTNode.CONDITIONAL_EXPRESSION:
	    return "ConditionalExpression";
	case ASTNode.CONSTRUCTOR_INVOCATION:
	    return "ConstructorInvocation";
	case ASTNode.CONTINUE_STATEMENT:
	    return "ContinueStatement";
	case ASTNode.DO_STATEMENT:
	    return "DoStatement";
	case ASTNode.EMPTY_STATEMENT:
	    return "EmptyStatement";
	case ASTNode.ENHANCED_FOR_STATEMENT:
	    return "EnhancedForStatement";
	case ASTNode.ENUM_CONSTANT_DECLARATION:
	    return "EnumConstantDeclaration";
	case ASTNode.ENUM_DECLARATION:
	    return "EnumDeclaration";
	case ASTNode.EXPRESSION_STATEMENT:
	    return "ExpressionStatement";
	case ASTNode.FIELD_ACCESS:
	    return "FieldAccess";
	case ASTNode.FIELD_DECLARATION:
	    return "FieldDeclaration";
	case ASTNode.FOR_STATEMENT:
	    return "ForStatement";
	case ASTNode.IF_STATEMENT:
	    return "IfStatement";
	case ASTNode.IMPORT_DECLARATION:
	    return "ImportDeclaration";
	case ASTNode.INFIX_EXPRESSION:
	    return "InfixExpression";
	case ASTNode.INITIALIZER:
	    return "Initializer";
	case ASTNode.INSTANCEOF_EXPRESSION:
	    return "InstanceofExpression";
	case ASTNode.JAVADOC:
	    return "Javadoc";
	case ASTNode.LABELED_STATEMENT:
	    return "LabeledStatement";
	case ASTNode.LINE_COMMENT:
	    return "LineComment";
	case ASTNode.MARKER_ANNOTATION:
	    return "MarkerAnnotation";
	case ASTNode.MEMBER_REF:
	    return "MemberRef";
	case ASTNode.MEMBER_VALUE_PAIR:
	    return "MemberValuePair";
	case ASTNode.METHOD_DECLARATION:
	    return "MethodDeclaration";
	case ASTNode.METHOD_INVOCATION:
	    return "MethodInvocation";
	case ASTNode.METHOD_REF:
	    return "MethodRef";
	case ASTNode.METHOD_REF_PARAMETER:
	    return "MethodRefParameter";
	case ASTNode.MODIFIER:
	    return "Modifier";
	case ASTNode.NORMAL_ANNOTATION:
	    return "NormalAnnotation";
	case ASTNode.NULL_LITERAL:
	    return "NullLiteral";
	case ASTNode.NUMBER_LITERAL:
	    return "NumberLiteral";
	case ASTNode.PACKAGE_DECLARATION:
	    return "PackageDeclaration";
	case ASTNode.PARAMETERIZED_TYPE:
	    return "ParameterizedType";
	case ASTNode.PARENTHESIZED_EXPRESSION:
	    return "ParenthesizedExpression";
	case ASTNode.POSTFIX_EXPRESSION:
	    return "PostfixExpression";
	case ASTNode.PREFIX_EXPRESSION:
	    return "PrefixExpression";
	case ASTNode.PRIMITIVE_TYPE:
	    return "PrimitiveType";
	case ASTNode.QUALIFIED_NAME:
	    return "QualifiedName";
	case ASTNode.QUALIFIED_TYPE:
	    return "QualifiedType";
	case ASTNode.RETURN_STATEMENT:
	    return "ReturnStatement";
	case ASTNode.SIMPLE_NAME:
	    return "SimpleName";
	case ASTNode.SIMPLE_TYPE:
	    return "SimpleType";
	case ASTNode.SINGLE_MEMBER_ANNOTATION:
	    return "SingleMemberAnnotation";
	case ASTNode.SINGLE_VARIABLE_DECLARATION:
	    return "SingleVariableDeclaration";
	case ASTNode.STRING_LITERAL:
	    return "StringLiteral";
	case ASTNode.SUPER_CONSTRUCTOR_INVOCATION:
	    return "SuperConstructorInvocation";
	case ASTNode.SUPER_FIELD_ACCESS:
	    return "SuperFieldAccess";
	case ASTNode.SUPER_METHOD_INVOCATION:
	    return "SuperMethodInvocation";
	case ASTNode.SWITCH_CASE:
	    return "SwitchCase";
	case ASTNode.SWITCH_STATEMENT:
	    return "SwitchStatement";
	case ASTNode.SYNCHRONIZED_STATEMENT:
	    return "SynchronizedStatement";
	case ASTNode.TAG_ELEMENT:
	    return "TagElement";
	case ASTNode.TEXT_ELEMENT:
	    return "TextElement";
	case ASTNode.THIS_EXPRESSION:
	    return "ThisExpression";
	case ASTNode.THROW_STATEMENT:
	    return "ThrowStatement";
	case ASTNode.TRY_STATEMENT:
	    return "TryStatement";
	case ASTNode.TYPE_DECLARATION:
	    return "TypeDeclaration";
	case ASTNode.TYPE_DECLARATION_STATEMENT:
	    return "TypeDeclarationStatement";
	case ASTNode.TYPE_LITERAL:
	    return "TypeLiteral";
	case ASTNode.TYPE_PARAMETER:
	    return "TypeParameter";
	case ASTNode.UNION_TYPE:
	    return "UnionType";
	case ASTNode.VARIABLE_DECLARATION_EXPRESSION:
	    return "VariableDeclarationExpression";
	case ASTNode.VARIABLE_DECLARATION_FRAGMENT:
	    return "VariableDeclarationFragment";
	case ASTNode.VARIABLE_DECLARATION_STATEMENT:
	    return "VariableDeclarationStatement";
	case ASTNode.WHILE_STATEMENT:
	    return "WhileStatement";
	case ASTNode.WILDCARD_TYPE:
	    return "WildcardType";
	case 85:		// ???
	    return "UNKNOWN";
	default:
	    return null;
	}
    }
}
