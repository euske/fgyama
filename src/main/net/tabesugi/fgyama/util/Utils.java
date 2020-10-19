//  Utils.java
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import java.lang.management.*;
import javax.xml.stream.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import org.w3c.dom.*;
import org.apache.bcel.*;
import org.apache.bcel.classfile.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  Utility functions.
//
public class Utils {

    public static <T> String join(T[] a) {
        return join(", ", a);
    }
    public static <T> String join(String delim, T[] a) {
        StringBuilder b = new StringBuilder();
        b.append("[");
        for (T v : a) {
            if (1 < b.length()) {
                b.append(delim);
            }
            b.append((v == null)? "null" : v.toString());
        }
        b.append("]");
        return b.toString();
    }

    public static <T> String join(List<T> a) {
        return join(", ", a);
    }
    public static <T> String join(String delim, List<T> a) {
        StringBuilder b = new StringBuilder();
        b.append("[");
        for (T v : a) {
            if (1 < b.length()) {
                b.append(delim);
            }
            b.append((v == null)? "null" : v.toString());
        }
        b.append("]");
        return b.toString();
    }

    public static String quote(char c) {
        switch (c) {
        case '\0':
            return "\\0";
        case '\t':
            return "\\t";
        case '\b':
            return "\\b";
        case '\n':
            return "\\n";
        case '\r':
            return "\\r";
        case '\f':
            return "\\f";
        case '\'':
            return "\\'";
        case '\"':
            return "\\\"";
        case '\\':
            return "\\\\";
        default:
            if (Character.isISOControl(c)) {
                return String.format("\\u%04x", (int)c);
            } else {
                return Character.toString(c);
            }
        }
    }

    public static String quote(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            b.append(quote(s.charAt(i)));
        }
        return b.toString();
    }

    public static String hashString(String s) {
        return Integer.toHexString(s.hashCode());
    }

    public static String readFile(String path)
        throws IOException {
        return readFile(new File(path));
    }

    public static String readFile(File file)
        throws IOException {
        // Regularlize DOS/Unix newlines.
        BufferedReader reader = new BufferedReader(new FileReader(file));
        StringBuilder b = new StringBuilder();
        while (true) {
            String line = reader.readLine();
            if (line == null) break;
            b.append(line);
            b.append("\n");
        }
        reader.close();
        return b.toString();
    }

    public static CompilationUnit parseFile(File file)
        throws IOException {
        String src = Utils.readFile(file);
        Map<String, String> options = JavaCore.getOptions();
        JavaCore.setComplianceOptions(JavaCore.VERSION_1_7, options);
        ASTParser parser = ASTParser.newParser(AST.JLS8);
        parser.setSource(src.toCharArray());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setResolveBindings(false);
        parser.setEnvironment(null, null, null, false);
        parser.setCompilerOptions(options);
        // XXX throw an error for unsyntactic source code.
        return (CompilationUnit)parser.createAST(null);
    }

    public static File[] enumerateFiles(String path) {
        return enumerateFiles(new File(path));
    }
    public static File[] enumerateFiles(File file) {
        List<File> files = new ArrayList<File>();
        enumerateFiles(files, file);
        File[] a = new File[files.size()];
        files.toArray(a);
        return a;
    }
    public static void enumerateFiles(List<File> files, File file) {
        if (file.isDirectory()) {
            for (File f1 : file.listFiles()) {
                enumerateFiles(files, f1);
            }
        } else {
            files.add(file);
        }
    }

    public static Document createXml()
        throws ParserConfigurationException {
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        Document doc = docBuilder.newDocument();
        doc.setXmlStandalone(true);
        return doc;
    }

    public static Document readXml(String path)
        throws IOException, ParserConfigurationException, org.xml.sax.SAXException {
        File file = new File(path);
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        return docBuilder.parse(file);
    }

    public static Document readXml(InputStream stream)
        throws IOException, ParserConfigurationException, org.xml.sax.SAXException {
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        return docBuilder.parse(stream);
    }

    public static void printXml(OutputStream output, Document doc) {
        try {
            TransformerFactory transFactory = TransformerFactory.newInstance();
            Transformer transformer = transFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(output);
            transformer.transform(source, result);
        } catch (TransformerException e) {
            e.printStackTrace();
        }
    }

    public static String getTypeName(Type type) {
        if (type instanceof PrimitiveType) {
            PrimitiveType ptype = (PrimitiveType)type;
            return ptype.getPrimitiveTypeCode().toString();
        } else if (type instanceof SimpleType) {
            SimpleType stype = (SimpleType)type;
            return stype.getName().getFullyQualifiedName();
        } else if (type instanceof ArrayType) {
            ArrayType atype = (ArrayType)type;
            String name = getTypeName(atype.getElementType());
            int ndims = atype.getDimensions();
            for (int i = 0; i < ndims; i++) {
                name += "[]";
            }
            return name;
        } else {
            // unsupported.
            return null;
        }
    }

    public static void writeXML(XMLStreamWriter writer, ASTNode ast)
        throws XMLStreamException {
        int type = ast.getNodeType();
        int start = ast.getStartPosition();
        int end = start + ast.getLength();
        writer.writeStartElement("ast");
        writer.writeAttribute("type", Integer.toString(type));
        writer.writeAttribute("start", Integer.toString(start));
        writer.writeAttribute("end", Integer.toString(end));
        writer.writeEndElement();
    }

    public static String encodeASTNode(ASTNode node) {
        int start = node.getStartPosition();
        int end = start + node.getLength();
        String label;
        if (node instanceof AbstractTypeDeclaration) {
            label = ((AbstractTypeDeclaration)node).getName().getIdentifier();
        } else if (node instanceof Block) {
            label = "B";
        } else if (node instanceof DoStatement) {
            label = "do";
        } else if (node instanceof EnhancedForStatement) {
            label = "efor";
        } else if (node instanceof ForStatement) {
            label = "for";
        } else if (node instanceof IfStatement) {
            label = "if";
        } else if (node instanceof Initializer) {
            label = "clinit";
        } else if (node instanceof LambdaExpression) {
            label = "lambda";
        } else if (node instanceof MethodReference) {
            label = "methodref";
        } else if (node instanceof MethodDeclaration) {
            label = ((MethodDeclaration)node).getName().getIdentifier();
        } else if (node instanceof VariableDeclaration) {
            label = ((VariableDeclaration)node).getName().getIdentifier();
        } else if (node instanceof SwitchCase) {
            label = "case";
        } else if (node instanceof SwitchStatement) {
            label = "switch";
        } else if (node instanceof SynchronizedStatement) {
            label = "synchronized";
        } else if (node instanceof TryStatement) {
            label = "try";
        } else if (node instanceof CatchClause) {
            label = "catch";
        } else if (node instanceof WhileStatement) {
            label = "while";
        } else {
            label = "AST"+node.getNodeType();
        }
        return (":"+label+":"+start+":"+end);
    }

    public static String getASTNodeTypeName(int type) {
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
        case ASTNode.CREATION_REFERENCE:
            return "CreationReference";
        case ASTNode.DIMENSION:
            return "Dimension";
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
        case ASTNode.EXPRESSION_METHOD_REFERENCE:
            return "ExpressionMethodReference";
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
        case ASTNode.INTERSECTION_TYPE:
            return "IntersectionType";
        case ASTNode.JAVADOC:
            return "Javadoc";
        case ASTNode.LABELED_STATEMENT:
            return "LabeledStatement";
        case ASTNode.LAMBDA_EXPRESSION:
            return "LambdaExpression";
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
        case ASTNode.NAME_QUALIFIED_TYPE:
            return "NameQualifiedType";
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
        case ASTNode.SUPER_METHOD_REFERENCE:
            return "SuperMethhodReference";
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
        case ASTNode.TYPE_METHOD_REFERENCE:
            return "TypeMethodReference";
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
        default:
            return null;
        }
    }

    public static String getJKlassSignature(Attribute[] attrs) {
        for (Attribute attr : attrs) {
            if (attr instanceof org.apache.bcel.classfile.Signature) {
                return ((org.apache.bcel.classfile.Signature)attr).getSignature();
            }
        }
        return null;
    }

    public static void showHeapStats() {
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        MemoryUsage usage = mem.getHeapMemoryUsage();
        System.err.println("Memory: "+usage);
    }
}
