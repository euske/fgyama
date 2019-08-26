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

    private class DFFunctionalMethod extends DFMethod {
        public DFFunctionalMethod(String id) {
            super(DFFunctionalKlass.this, id, DFCallStyle.Lambda, id,
                  DFFunctionalKlass.this.getKlassScope(), false);
        }
    }

    private DFFunctionalMethod _funcMethod = null;

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

    public void setBaseKlass(DFKlass klass) {
        super.setBaseKlass(klass);
	_funcMethod.setFuncType(klass.getFuncMethod().getFuncType());
    }

    protected void buildTypeFromDecls(ASTNode ast)
	throws InvalidSyntax {
        LambdaExpression lambda = (LambdaExpression)ast;
        String id = "function";
        _funcMethod = new DFFunctionalMethod(id);
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
        // _baseKlass is left undefined.
        _funcMethod.setFinder(finder);
        _funcMethod.setTree(ast);
    }
}
