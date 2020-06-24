//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFMethodRefKlass
//
class DFMethodRefKlass extends DFSourceKlass {

    private class FunctionalMethod extends DFSourceMethod {

        private DFFunctionType _funcType = null;
        private DFMethod _refMethod = null;

        public FunctionalMethod(String id, DFVarScope scope, DFTypeFinder finder) {
            super(DFMethodRefKlass.this, CallStyle.Lambda,
                  false, id, id, scope, finder);
        }

        public void setFuncType(DFFunctionType funcType) {
            _funcType = funcType;
        }

        @Override
        public DFFunctionType getFuncType() {
            return _funcType;
        }

        public DFMethod getRefMethod() {
            return _refMethod;
        }

        @Override
        public void loadKlasses(Set<DFSourceKlass> klasses)
            throws InvalidSyntax {
            DFTypeFinder finder = this.getFinder();
            if (_methodRef instanceof CreationReference) {
                DFType type = finder.resolveSafe(
                    ((CreationReference)_methodRef).getType());
                assert type instanceof DFSourceKlass;
                ((DFSourceKlass)type).loadKlasses(klasses);

            } else if (_methodRef instanceof SuperMethodReference) {
                try {
                    DFType type = finder.lookupType(
                        ((SuperMethodReference)_methodRef).getQualifier());
                    assert type instanceof DFSourceKlass;
                    ((DFSourceKlass)type).loadKlasses(klasses);
                } catch (TypeNotFound e) {
                }

            } else if (_methodRef instanceof TypeMethodReference) {
                DFType type = finder.resolveSafe(
                    ((TypeMethodReference)_methodRef).getType());
                assert type instanceof DFSourceKlass;
                ((DFSourceKlass)type).loadKlasses(klasses);

            } else if (_methodRef instanceof ExpressionMethodReference) {
                // XXX ignored mref.typeArguments() for method refs.
                this.loadKlassesExpr(
                    klasses,
                    ((ExpressionMethodReference)_methodRef).getExpression());

            } else {
                throw new InvalidSyntax(_methodRef);
            }
        }
        @Override
        public void enumRefs(List<DFSourceKlass> defined)
            throws InvalidSyntax {
        }
        @SuppressWarnings("unchecked")
        public DFGraph generateGraph(Counter counter)
            throws InvalidSyntax, EntityNotFound {
            return null;
        }

        protected void fixateMethod(DFKlass refKlass) {
            assert _funcType != null;
            ASTNode ast = _methodRef;
            DFType[] argTypes = _funcType.getRealArgTypes();
            if (ast instanceof CreationReference) {
                _refMethod = refKlass.findMethod(
                    DFMethod.CallStyle.Constructor, (String)null, argTypes);
            } else if (ast instanceof SuperMethodReference) {
                SimpleName name = ((SuperMethodReference)ast).getName();
                _refMethod = refKlass.findMethod(
                    DFMethod.CallStyle.StaticMethod, name, argTypes);
            } else if (ast instanceof TypeMethodReference) {
                SimpleName name = ((TypeMethodReference)ast).getName();
                _refMethod = refKlass.findMethod(
                    DFMethod.CallStyle.StaticMethod, name, argTypes);
            } else if (ast instanceof ExpressionMethodReference) {
                SimpleName name = ((ExpressionMethodReference)ast).getName();
                _refMethod = refKlass.findMethod(
                    DFMethod.CallStyle.InstanceOrStatic, name, argTypes);
            }
            //Logger.info("DFMethodRefKlass.fixateMethod:", method);
            if (_refMethod == null) {
                Logger.error(
                    "DFMethodRefKlass.fixateMethod: MethodNotFound",
                    this, refKlass, ast);
            }
        }
    }

    private MethodReference _methodRef;
    private DFKlass _refKlass = null;

    private final String FUNC_NAME = "#f";

    private FunctionalMethod _funcMethod;

    public DFMethodRefKlass(
        MethodReference methodRef,
        DFTypeSpace outerSpace, DFSourceKlass outerKlass,
        DFVarScope outerScope)
        throws InvalidSyntax {
        super(Utils.encodeASTNode(methodRef),
              outerSpace, outerKlass, outerKlass.getFilePath(),
              outerScope);
        _methodRef = methodRef;
    }

    @Override
    public String toString() {
        return ("<DFMethodRefKlass("+this.getTypeName()+")>");
    }

    @Override
    public int isSubclassOf(DFKlass klass, Map<DFMapType, DFKlass> typeMap) {
        if (klass instanceof DFSourceKlass &&
            ((DFSourceKlass)klass).isFuncInterface()) return 0;
        return -1;
    }

    @Override
    public boolean isDefined() {
        return (_funcMethod.getFuncType() != null &&
                _funcMethod.getRefMethod() != null);
    }

    @Override
    public DFMethod getFuncMethod() {
        return _funcMethod;
    }

    @Override
    protected void build() throws InvalidSyntax {
        DFTypeFinder finder = this.getFinder();
        _funcMethod = new FunctionalMethod(FUNC_NAME, this.getKlassScope(), finder);
        this.addMethod(_funcMethod, FUNC_NAME);
    }

    @Override
    public void setBaseKlass(DFKlass klass) {
        super.setBaseKlass(klass);
        assert _funcMethod.getFuncType() == null;
        DFMethod funcMethod = klass.getFuncMethod();
        // BaseKlass does not have a function method.
        // This happens when baseKlass type is undefined.
        if (funcMethod == null) return;
        _funcMethod.setFuncType(funcMethod.getFuncType());
        if (_refKlass != null) {
            _funcMethod.fixateMethod(_refKlass);
        }
    }

    public void setRefKlass(DFKlass refKlass) {
        _refKlass = refKlass;
        if (_funcMethod.getFuncType() != null) {
            _funcMethod.fixateMethod(_refKlass);
        }
    }

    public void loadKlasses(Set<DFSourceKlass> klasses)
        throws InvalidSyntax {
        super.loadKlasses(klasses);

    }
}
