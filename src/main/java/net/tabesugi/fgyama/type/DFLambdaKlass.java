//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import javax.xml.stream.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFLambdaKlass
//
class DFLambdaKlass extends DFSourceKlass {

    private class FunctionalMethod extends DFSourceMethod {

        private DFFuncType _funcType = null;

        public FunctionalMethod(String id, DFTypeFinder finder)
            throws InvalidSyntax, EntityDuplicate {
            super(DFLambdaKlass.this, CallStyle.Lambda,
                  false, id, id, _lambdaScope, finder);

            this.build();
        }

        private void build()
            throws InvalidSyntax, EntityDuplicate {
            ASTNode body = _lambda.getBody();
            if (body instanceof Statement) {
                this.buildTypeFromStmt((Statement)body, this.getScope());
            } else if (body instanceof Expression) {
                this.buildTypeFromExpr((Expression)body, this.getScope());
            } else {
                throw new InvalidSyntax(body);
            }
        }

        @Override
        protected DFMethod parameterize(Map<String, DFKlass> paramTypes) {
            assert false;
            return null;
        }

        @SuppressWarnings("unchecked")
        protected void setFuncType(DFFuncType funcType)
            throws InvalidSyntax {
            if (funcType == null) {
                // Handle an unresolved lambda.
                DFType[] argTypes = new DFType[_lambda.parameters().size()];
                for (int i = 0; i < argTypes.length; i++) {
                    argTypes[i] = DFUnknownType.UNKNOWN;
                }
                funcType = new DFFuncType(argTypes, DFUnknownType.UNKNOWN);
            }
            _funcType = funcType;
            DFTypeFinder finder = this.getFinder();
            MethodScope methodScope = this.getScope();
            methodScope.buildInternalRefs(_lambda.parameters());
            ASTNode body = _lambda.getBody();
            if (body instanceof Statement) {
                methodScope.buildStmt(finder, (Statement)body);
            } else if (body instanceof Expression) {
                methodScope.buildExpr(finder, (Expression)body);
            } else {
                throw new InvalidSyntax(body);
            }
        }

        public ASTNode getAST() {
            return _lambda;
        }

        @Override
        public boolean addOverrider(DFMethod method) {
            Logger.error("DFLambdaKlass.FunctionalMethod: cannot override:", method);
            assert false;
            return false;
        }

        @Override
        public DFFuncType getFuncType() {
            return _funcType;
        }

        public void listUsedKlasses(Collection<DFSourceKlass> klasses)
            throws InvalidSyntax {
            ASTNode body = _lambda.getBody();
            if (body instanceof Statement) {
                this.listUsedStmt(klasses, (Statement)body);
            } else if (body instanceof Expression) {
                this.listUsedExpr(klasses, (Expression)body);
            } else {
                throw new InvalidSyntax(body);
            }
        }

        @Override
        public void listDefinedKlasses(Collection<DFSourceKlass> defined)
            throws InvalidSyntax {
            DFLocalScope scope = this.getScope();
            ASTNode body = _lambda.getBody();
            if (body instanceof Statement) {
                this.listDefinedStmt(defined, scope, (Statement)body);
            } else if (body instanceof Expression) {
                this.listDefinedExpr(defined, scope, (Expression)body);
            } else {
                throw new InvalidSyntax(body);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public DFGraph getDFGraph(int graphId)
            throws InvalidSyntax, EntityNotFound {
            MethodGraph graph = new MethodGraph("M"+graphId+"_"+this.getName());
            MethodScope methodScope = this.getScope();
            DFContext ctx = new DFContext(graph, methodScope);
            int i = 0;
            for (VariableDeclaration decl :
                     (List<VariableDeclaration>)_lambda.parameters()) {
                DFRef ref_v = methodScope.lookupArgument(i);
                DFRef ref_a = methodScope.lookupVar(decl.getName());
                DFNode assign = graph.createArgNode(ref_v, ref_a, decl);
                ctx.set(assign);
                i++;
            }

            try {
                graph.processMethodBody(ctx, _lambda.getBody());
            } catch (InvalidSyntax e) {
                Logger.error(
                    "DFLambdaKlass.getDFGraph:",
                    Utils.getASTSource(e.ast), this);
            } catch (MethodNotFound e) {
                e.setMethod(this);
                Logger.error(
                    "DFLambdaKlass.getDFGraph: MethodNotFound",
                    e.name+"("+Utils.join(e.argTypes)+")", this);
                throw e;
            } catch (EntityNotFound e) {
                e.setMethod(this);
                Logger.error(
                    "DFLambdaKlass.getDFGraph: EntityNotFound",
                    e.name, this);
                throw e;
            }

            return graph;
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
                DFLambdaKlass.this.addCapturedRef(captured);
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
        public DFVarScope getScope() {
            return _lambdaScope;
        }

        @Override
        public String getFullName() {
            return "@"+DFLambdaKlass.this.getTypeName()+"/="+_name;
        }
    }

    private LambdaExpression _lambda;
    private LambdaScope _lambdaScope;

    private DFKlass _baseKlass = null;
    private FunctionalMethod _funcMethod = null;

    private List<CapturedRef> _captured =
        new ArrayList<CapturedRef>();

    public DFLambdaKlass(
        LambdaExpression lambda,
        DFTypeSpace outerSpace, DFSourceKlass outerKlass,
        DFVarScope outerScope)
        throws InvalidSyntax {
        super(Utils.encodeASTNode(lambda),
              outerSpace, outerKlass, outerScope,
              outerKlass.getFilePath(), outerKlass.isAnalyze());
        _lambda = lambda;
        _lambdaScope = new LambdaScope(outerScope, Utils.encodeASTNode(lambda));
    }

    public ASTNode getAST() {
        return _lambda;
    }

    @Override
    public String toString() {
        return ("<DFLambdaKlass("+this.getTypeName()+")>");
    }

    public boolean isDefined() {
        return (_funcMethod != null && _funcMethod.getFuncType() != null);
    }

    @Override
    public int getReifyDepth() {
        // This is always a concrete type.
        return 0;
    }

    @Override
    public DFKlass getBaseKlass() {
        assert _baseKlass != null;
        return _baseKlass;
    }

    @Override
    public DFMethod[] getMethods() {
        this.load();
        return new DFMethod[] { _funcMethod };
    }

    @Override
    public DFMethod getFuncMethod() {
        this.load();
        return _funcMethod;
    }

    @Override
    public void overrideMethods() {
        if (!this.isDefined()) return;
        super.overrideMethods();
    }

    @Override
    protected DFKlass parameterize(Map<String, DFKlass> paramTypes) {
        assert false;
        return null;
    }

    @Override
    protected void build() {
        DFTypeFinder finder = this.getFinder();
        try {
            _funcMethod = new FunctionalMethod("#f", finder);
        } catch (InvalidSyntax e) {
            Logger.error(
                "DFLambdaKlass.build: InvalidSyntax: ",
                Utils.getASTSource(e.ast), this);
        } catch (EntityDuplicate e) {
            Logger.error(
                "DFLambdaKlass.build: EntityDuplicate: ",
                e.name, this);
        }
    }

    public int canConvertTo(DFKlass klass)
        throws TypeIncompatible {
        DFMethod method = klass.getFuncMethod();
        if (method != null) {
            DFFuncType funcType = method.getFuncType();
            if (funcType != null) {
                int nrecv = _lambda.parameters().size();
                int nsend = funcType.getRealArgTypes().length;
                if (nrecv == nsend) return 0;
            }
        }
        throw new TypeIncompatible(klass, this);
    }

    public void setBaseKlass(DFKlass baseKlass) {
        this.load();
        assert _baseKlass == null;
        assert _funcMethod != null;
        assert _funcMethod.getFuncType() == null;
        if (baseKlass instanceof DFLambdaKlass) {
            // XXX Edge case when the outer function is a generic method
            // which is reified with this lambda itself.
            // e.g. <T> foo(T a) { foo(() -> 1); }
            _baseKlass = DFBuiltinTypes.getObjectKlass();
            try {
                _funcMethod.setFuncType(null);
            } catch (InvalidSyntax e) {
            }
            return;
        }
        assert !(baseKlass instanceof DFMethodRefKlass);
        _baseKlass = baseKlass;
        DFMethod funcMethod = baseKlass.getFuncMethod();
        // BaseKlass does not have a function method.
        // This happens when baseKlass type is undefined.
        try {
            if (funcMethod == null) {
                _funcMethod.setFuncType(null);
            } else {
                _funcMethod.setFuncType(funcMethod.getFuncType());
            }
        } catch (InvalidSyntax e) {
        }
    }

    private void addCapturedRef(CapturedRef ref) {
        _captured.add(ref);
    }

    public List<CapturedRef> getCapturedRefs() {
        return _captured;
    }

    @Override
    public void listDefinedKlasses(Collection<DFSourceKlass> defined)
        throws InvalidSyntax {
        assert this.isDefined();
        super.listDefinedKlasses(defined);
    }
}
