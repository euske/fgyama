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

    private class FunctionalMethod extends DFMethod {
        public FunctionalMethod(String id) {
            super(DFFunctionalKlass.this, id, CallStyle.Lambda, id,
                  DFFunctionalKlass.this._lambdaScope, false);
        }
    }

    private class LambdaScope extends DFVarScope {

        private Map<String, ExtRef> _id2ext =
            new HashMap<String, ExtRef>();

        public LambdaScope(DFVarScope outer, String id) {
            super(outer, id);
        }

        @Override
        public DFRef lookupVar(String id)
            throws VariableNotFound {
            DFRef ref = _id2ext.get(id);
            if (ref != null) return ref;
            ref = super.lookupVar(id);
            if (ref != null) {
                // ref is an external variable.
                ExtRef extref = new ExtRef(ref, id);
                DFFunctionalKlass.this.addExtRef(extref);
                _id2ext.put(id, extref);
            }
            return ref;
        }
    }

    public class ExtRef extends DFRef {

        private DFRef _captured;
        private String _name;

        public ExtRef(DFRef captured, String name) {
            super(captured.getRefType());
            _captured = captured;
            _name = name;
        }

        public DFRef getCaptured() {
            return _captured;
        }

        @Override
        public boolean isLocal() {
            return false;
        }

        @Override
        public String getFullName() {
            return "@"+DFFunctionalKlass.this.getTypeName()+"/="+_name;
        }
    }

    private LambdaScope _lambdaScope = null;
    private List<ExtRef> _extrefs =
        new ArrayList<ExtRef>();
    private FunctionalMethod _funcMethod = null;

    public DFFunctionalKlass(
        String name, DFTypeSpace outerSpace,
        DFKlass outerKlass, DFVarScope outerScope) {
	super(name, outerSpace, outerKlass, outerScope);
        _lambdaScope = new LambdaScope(outerScope, name);
    }

    public int isSubclassOf(DFKlass klass, Map<DFMapType, DFType> typeMap) {
        if (klass.isFuncInterface()) return 0;
        return -1;
    }

    public DFMethod getFuncMethod() {
        assert _funcMethod != null;
        return _funcMethod;
    }

    private void addExtRef(ExtRef ref) {
        _extrefs.add(ref);
    }

    public ExtRef[] getExtRefs() {
        ExtRef[] refs = new ExtRef[_extrefs.size()];
        _extrefs.toArray(refs);
        return refs;
    }

    public void setBaseKlass(DFKlass klass) {
        super.setBaseKlass(klass);
	_funcMethod.setFuncType(klass.getFuncMethod().getFuncType());
    }

    protected void buildTypeFromDecls(ASTNode ast)
	throws InvalidSyntax {
        LambdaExpression lambda = (LambdaExpression)ast;
        String id = "function";
        _funcMethod = new FunctionalMethod(id);
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
