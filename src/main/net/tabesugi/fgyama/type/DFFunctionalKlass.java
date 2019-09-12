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

        private Map<String, CapturedRef> _id2captured =
            new HashMap<String, CapturedRef>();

        public LambdaScope(DFVarScope outer, String id) {
            super(outer, id);
        }

        @Override
        public DFRef lookupVar(String id)
            throws VariableNotFound {
            DFRef ref = _id2captured.get(id);
            if (ref != null) return ref;
            ref = super.lookupVar(id);
            if (ref != null) {
                // replace ref with a captured variable.
                CapturedRef captured = new CapturedRef(ref, id);
                DFFunctionalKlass.this.addCapturedRef(captured);
                _id2captured.put(id, captured);
                ref = captured;
            }
            return ref;
        }
    }

    public class CapturedRef extends DFRef {

        private DFRef _original;
        private String _name;

        public CapturedRef(DFRef original, String name) {
            super(original.getRefType());
            _original = original;
            _name = name;
        }

        public DFRef getOriginal() {
            return _original;
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
    private List<CapturedRef> _captured =
        new ArrayList<CapturedRef>();
    private FunctionalMethod _funcMethod = null;

    public DFFunctionalKlass(
        String name, DFTypeSpace outerSpace,
        DFKlass outerKlass, DFVarScope outerScope) {
	super(name, outerSpace, outerKlass, outerScope,
              DFBuiltinTypes.getObjectKlass());
        _lambdaScope = new LambdaScope(outerScope, name);
    }

    @Override
    public String toString() {
        return ("<DFFunctionalKlass("+this.getTypeName()+")>");
    }

    public int isSubclassOf(DFKlass klass, Map<DFMapType, DFType> typeMap) {
        if (klass.isFuncInterface()) return 0;
        return -1;
    }

    public boolean isDefined() {
        return (_funcMethod != null &&
                _funcMethod.getFuncType() != null);
    }

    public DFMethod getFuncMethod() {
        assert _funcMethod != null;
        return _funcMethod;
    }

    private void addCapturedRef(CapturedRef ref) {
        _captured.add(ref);
    }

    public CapturedRef[] getCapturedRefs() {
        CapturedRef[] refs = new CapturedRef[_captured.size()];
        _captured.toArray(refs);
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
