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
            throws InvalidSyntax {
            super(DFLambdaKlass.this, CallStyle.Lambda,
                  false, id, id, _lambdaScope, finder);

            this.build();
        }

        private void build()
            throws InvalidSyntax {
            ASTNode body = _lambda.getBody();
            if (body instanceof Statement) {
                this.buildTypeFromStmt((Statement)body, this.getScope());
            } else if (body instanceof Expression) {
                this.buildTypeFromExpr((Expression)body, this.getScope());
            } else {
                throw new InvalidSyntax(body);
            }
        }

        protected DFMethod parameterize(Map<String, DFKlass> paramTypes)
            throws InvalidSyntax {
            assert false;
            return null;
        }

        @SuppressWarnings("unchecked")
        protected void setFuncType(DFFuncType funcType)
            throws InvalidSyntax {
            _funcType = funcType;
            DFTypeFinder finder = this.getFinder();
            MethodScope methodScope = (MethodScope)this.getScope();
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
        public boolean isTransparent() {
            return true;
        }

        @Override
        public DFFuncType getFuncType() {
            return _funcType;
        }

        public void loadKlasses(Collection<DFSourceKlass> klasses)
            throws InvalidSyntax {
            ASTNode body = _lambda.getBody();
            if (body instanceof Statement) {
                this.loadKlassesStmt(klasses, (Statement)body);
            } else if (body instanceof Expression) {
                this.loadKlassesExpr(klasses, (Expression)body);
            } else {
                throw new InvalidSyntax(body);
            }
        }

        @Override
        public void enumRefs(Collection<DFSourceKlass> defined)
            throws InvalidSyntax {
            DFLocalScope scope = this.getScope();
            ASTNode body = _lambda.getBody();
            if (body instanceof Statement) {
                this.enumRefsStmt(defined, scope, (Statement)body);
            } else if (body instanceof Expression) {
                this.enumRefsExpr(defined, scope, (Expression)body);
            } else {
                throw new InvalidSyntax(body);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public void writeGraph(Exporter exporter)
            throws InvalidSyntax, EntityNotFound {
            MethodScope methodScope = (MethodScope)this.getScope();
            MethodGraph graph = new MethodGraph(
                "M"+exporter.getNewId()+"_"+this.getName());
            DFContext ctx = new DFContext(graph, methodScope);
            int i = 0;
            for (VariableDeclaration decl :
                     (List<VariableDeclaration>)_lambda.parameters()) {
                DFRef ref = methodScope.lookupArgument(i);
                DFNode input = new InputNode(graph, methodScope, ref, decl);
                ctx.set(input);
                DFNode assign = new AssignNode(
                    graph, methodScope, methodScope.lookupVar(decl.getName()), decl);
                assign.accept(input);
                ctx.set(assign);
                i++;
            }
            ASTNode body = _lambda.getBody();
            this.processMethodBody(graph, ctx, body);
            exporter.writeGraph(graph);
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
        public boolean isLocal() {
            return false;
        }

        @Override
        public String getFullName() {
            return "@"+DFLambdaKlass.this.getTypeName()+"/="+_name;
        }
    }

    private final String FUNC_NAME = "#f";

    private LambdaExpression _lambda;
    private LambdaScope _lambdaScope;
    private FunctionalMethod _funcMethod;

    private List<CapturedRef> _captured =
        new ArrayList<CapturedRef>();

    public DFLambdaKlass(
        LambdaExpression lambda,
        DFTypeSpace outerSpace, DFSourceKlass outerKlass,
        DFVarScope outerScope)
        throws InvalidSyntax {
        super(Utils.encodeASTNode(lambda),
              outerSpace, outerKlass, outerKlass.getFilePath(),
              outerScope);
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

    @Override
    public int isSubclassOf(DFKlass klass, Map<DFMapType, DFKlass> typeMap) {
        if (klass instanceof DFSourceKlass &&
            ((DFSourceKlass)klass).isFuncInterface()) return 0;
        return -1;
    }

    @Override
    public boolean isLoaded() {
        return (_funcMethod != null && _funcMethod.getFuncType() != null);
    }

    @Override
    public DFMethod getFuncMethod() {
        return _funcMethod;
    }

    protected DFKlass parameterize(Map<String, DFKlass> paramTypes)
        throws InvalidSyntax {
        assert false;
        return null;
    }

    @Override
    protected void build() throws InvalidSyntax {
        DFTypeFinder finder = this.getFinder();
        _funcMethod = new FunctionalMethod(FUNC_NAME, finder);
        this.addMethod(_funcMethod, FUNC_NAME);
    }

    @Override
    public void setBaseKlass(DFKlass klass) {
        super.setBaseKlass(klass);
        assert _funcMethod != null;
        assert _funcMethod.getFuncType() == null;
        DFMethod funcMethod = klass.getFuncMethod();
        // BaseKlass does not have a function method.
        // This happens when baseKlass type is undefined.
        if (funcMethod == null) return;
        try {
            _funcMethod.setFuncType(funcMethod.getFuncType());
        } catch (InvalidSyntax e) {
        }
    }

    private void addCapturedRef(CapturedRef ref) {
        _captured.add(ref);
    }

    public List<CapturedRef> getCapturedRefs() {
        return _captured;
    }
}
