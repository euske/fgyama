//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import javax.xml.stream.*;
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

        protected DFMethod parameterize(DFKlass[] paramTypes)
            throws InvalidSyntax {
            assert false;
            return null;
        }

        protected boolean isDefined() {
            return _funcType != null && _refMethod != null;
        }

        protected void setFuncType(DFFunctionType funcType) {
            assert _funcType == null;
            _funcType = funcType;
        }

        public ASTNode getAST() {
            return _methodRef;
        }

        @Override
        public DFFunctionType getFuncType() {
            return _funcType;
        }

        @Override
        public void loadKlasses(Set<DFSourceKlass> klasses)
            throws InvalidSyntax {
            DFTypeFinder finder = this.getFinder();
            if (_methodRef instanceof CreationReference) {
                DFType type = finder.resolveSafe(
                    ((CreationReference)_methodRef).getType());
                if (type instanceof DFSourceKlass) {
                    ((DFSourceKlass)type).loadKlasses(klasses);
                }

            } else if (_methodRef instanceof SuperMethodReference) {
                try {
                    DFType type = finder.lookupType(
                        ((SuperMethodReference)_methodRef).getQualifier());
                    if (type instanceof DFSourceKlass) {
                        ((DFSourceKlass)type).loadKlasses(klasses);
                    }
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
            DFTypeFinder finder = this.getFinder();
            assert _funcType != null;
            DFType[] argTypes = _funcType.getRealArgTypes();
            if (_methodRef instanceof ExpressionMethodReference) {
                ExpressionMethodReference exprmref = (ExpressionMethodReference)_methodRef;
                Expression expr1 = exprmref.getExpression();
                DFType type = null;
                if (expr1 instanceof Name) {
                    try {
                        type = finder.lookupType((Name)expr1);
                    } catch (TypeNotFound e) {
                    }
                }
                if (type == null) {
                    type = this.enumRefsExpr(defined, this.getScope(), expr1);
                }
                if (type != null) {
                    DFKlass klass = type.toKlass();
                    _refMethod = klass.findMethod(
                        CallStyle.InstanceOrStatic, exprmref.getName(), argTypes);
                }

            } else if (_methodRef instanceof CreationReference) {
                CreationReference creatmref = (CreationReference)_methodRef;
                try {
                    DFKlass klass = finder.resolve(creatmref.getType()).toKlass();
                    _refMethod = klass.findMethod(
                        CallStyle.Constructor, (String)null, argTypes);
                } catch (TypeNotFound e) {
                }

            } else if (_methodRef instanceof SuperMethodReference) {
                SuperMethodReference supermref = (SuperMethodReference)_methodRef;
                try {
                    DFKlass klass = finder.lookupType(supermref.getQualifier()).toKlass();
                    klass = klass.getBaseKlass();
                    _refMethod = klass.findMethod(
                        CallStyle.StaticMethod, supermref.getName(), argTypes);
                } catch (TypeNotFound e) {
                }

            } else if (_methodRef instanceof TypeMethodReference) {
                TypeMethodReference typemref = (TypeMethodReference)_methodRef;
                try {
                    DFKlass klass = finder.resolve(typemref.getType()).toKlass();
                    _refMethod = klass.findMethod(
                        CallStyle.StaticMethod, typemref.getName(), argTypes);
                } catch (TypeNotFound e) {
                }

            } else {
                throw new InvalidSyntax(_methodRef);
            }

            if (_refMethod == null) {
                Logger.error(
                    "DFMethodRefKlass.setRefMethod: MethodNotFound",
                    this, _methodRef);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public DFGraph generateGraph(Counter counter)
            throws InvalidSyntax, EntityNotFound {
            return null;
        }
    }

    private MethodReference _methodRef;

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

    protected DFKlass parameterize(DFKlass[] paramTypes)
        throws InvalidSyntax {
        assert false;
        return null;
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
        return _funcMethod.isDefined();
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
        DFMethod funcMethod = klass.getFuncMethod();
        // BaseKlass does not have a function method.
        // This happens when baseKlass type is undefined.
        if (funcMethod == null) return;
        _funcMethod.setFuncType(funcMethod.getFuncType());
    }
}
