//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  PackageNameExtractor
//
public class PackageNameExtractor extends ASTVisitor {

    private String _package = null;
    private String _toplevel = null;

    public String getPackageName() {
	return _package;
    }

    public String getTopLevelName() {
	return _toplevel;
    }

    public boolean visit(PackageDeclaration node) {
	_package = node.getName().getFullyQualifiedName();
	return false;
    }

    public boolean visit(TypeDeclaration node) {
	if (node.isPackageMemberTypeDeclaration()) {
	    _toplevel = node.getName().getIdentifier();
	}
	return false;
    }

    public boolean visit(EnumDeclaration node) {
	if (node.isPackageMemberTypeDeclaration()) {
	    _toplevel = node.getName().getIdentifier();
	}
	return false;
    }

    public static String getCanonicalName(String path)
	throws IOException {
	String src = Utils.readFile(path);
	Map<String, String> options = JavaCore.getOptions();
	JavaCore.setComplianceOptions(JavaCore.VERSION_1_7, options);
	ASTParser parser = ASTParser.newParser(AST.JLS8);
        parser.setUnitName(path);
	parser.setSource(src.toCharArray());
	parser.setKind(ASTParser.K_COMPILATION_UNIT);
	parser.setResolveBindings(false);
	parser.setEnvironment(null, null, null, false);
	parser.setCompilerOptions(options);
	PackageNameExtractor extractor = new PackageNameExtractor();
	CompilationUnit cu = (CompilationUnit)parser.createAST(null);
	cu.accept(extractor);
	String pkg = extractor.getPackageName();
	String name = extractor.getTopLevelName();
        if (name == null) {
            return null;
        } else if (pkg == null) {
            return name;
        } else {
            return pkg+"."+name;
        }
    }
}
