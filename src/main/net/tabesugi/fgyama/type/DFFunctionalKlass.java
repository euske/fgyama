//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFFunctionalKlass
//
class DFFunctionalKlass extends DFKlass {

    private DFMethod _funcMethod = null;

    public DFFunctionalKlass(
        String name, DFTypeSpace outerSpace,
        DFKlass outerKlass, DFVarScope outerScope) {
	super(name, outerSpace, outerKlass, outerScope);
    }

    public int isSubclassOf(DFKlass klass, Map<DFMapType, DFType> typeMap) {
        if (klass.isFuncInterface()) return 0;
        return -1;
    }

    public DFMethod getFuncMethod() {
        assert _funcMethod != null;
        return _funcMethod;
    }

    protected void buildTypeFromDecls(ASTNode ast)
	throws InvalidSyntax {
        LambdaExpression lambda = (LambdaExpression)ast;
        String id = Utils.encodeASTNode(lambda);
        _funcMethod = new DFMethod(
            this, id, DFCallStyle.Lambda, "lambda",
            this.getKlassScope(), false);
        this.addMethod(_funcMethod, id);
        ASTNode body = lambda.getBody();
        if (body instanceof Statement) {
            this.buildTypeFromStmt((Statement)body, _funcMethod, _funcMethod.getScope());
        } else if (body instanceof Expression) {
            this.buildTypeFromExpr((Expression)body, _funcMethod, _funcMethod.getScope());
        } else {
            throw new InvalidSyntax(body);
        }
    }

    protected void buildMembersFromTree(DFTypeFinder finder, ASTNode ast)
        throws InvalidSyntax {
        _funcMethod.setFinder(finder);
        _funcMethod.setTree(ast);
    }
}
