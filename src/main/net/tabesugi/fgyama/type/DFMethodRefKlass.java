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

        private DFFuncType _funcType = null;
        private DFMethod _refMethod = null;

        public FunctionalMethod(String id, DFVarScope scope, DFTypeFinder finder) {
            super(DFMethodRefKlass.this, CallStyle.Lambda,
                  false, id, id, scope, finder);
        }

        protected DFMethod parameterize(Map<String, DFType> paramTypes) {
            assert false;
            return null;
        }

        protected boolean isDefined() {
            return _funcType != null;
        }

        protected void setFuncType(DFFuncType funcType) {
            assert _funcType == null;
            _funcType = funcType;
        }

        public ASTNode getAST() {
            return _methodRef;
        }

        @Override
        public DFFuncType getFuncType() {
            return _funcType;
        }

        @SuppressWarnings("unchecked")
        public void listUsedKlasses(Collection<DFSourceKlass> klasses) {
            DFTypeFinder finder = this.getFinder();
            if (_methodRef instanceof CreationReference) {
                CreationReference creatmref = (CreationReference)_methodRef;
                DFType type = finder.resolveSafe(creatmref.getType());
                if (type instanceof DFSourceKlass) {
                    ((DFSourceKlass)type).listUsedKlasses(klasses);
                }

            } else if (_methodRef instanceof TypeMethodReference) {
                TypeMethodReference typemref = (TypeMethodReference)_methodRef;
                DFType type = finder.resolveSafe(typemref.getType());
                assert type instanceof DFSourceKlass;
                ((DFSourceKlass)type).listUsedKlasses(klasses);

            } else if (_methodRef instanceof SuperMethodReference) {
                SuperMethodReference supermref = (SuperMethodReference)_methodRef;
                try {
                    DFKlass klass = finder.lookupKlass(supermref.getQualifier());
                    klass = finder.getParameterized(klass, supermref.typeArguments());
                    if (klass instanceof DFSourceKlass) {
                        ((DFSourceKlass)klass).listUsedKlasses(klasses);
                    }
                } catch (TypeNotFound e) {
                }

            } else if (_methodRef instanceof ExpressionMethodReference) {
                // XXX ignored exprmref.typeArguments().
                ExpressionMethodReference exprmref = (ExpressionMethodReference)_methodRef;
                try {
                    this.listUsedExpr(
                        klasses, exprmref.getExpression());
                } catch (InvalidSyntax e) {
                    Logger.error("DFMethodRefKlass.listUsedKlasses:", e);
                }
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public void listDefinedKlasses(Collection<DFSourceKlass> defined)
            throws InvalidSyntax {
            DFTypeFinder finder = this.getFinder();
            assert _funcType != null;
            DFType[] argTypes = _funcType.getRealArgTypes();
            if (_methodRef instanceof CreationReference) {
                CreationReference creatmref = (CreationReference)_methodRef;
                try {
                    DFKlass klass = finder.resolve(creatmref.getType()).toKlass();
                    _refMethod = klass.findMethod(
                        CallStyle.Constructor, (String)null, argTypes);
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

            } else if (_methodRef instanceof SuperMethodReference) {
                SuperMethodReference supermref = (SuperMethodReference)_methodRef;
                try {
                    DFKlass klass = finder.lookupKlass(supermref.getQualifier());
                    klass = klass.getBaseKlass();
                    klass = finder.getParameterized(klass, supermref.typeArguments());
                    _refMethod = klass.findMethod(
                        CallStyle.StaticMethod, supermref.getName(), argTypes);
                } catch (TypeNotFound e) {
                }

            } else if (_methodRef instanceof ExpressionMethodReference) {
                ExpressionMethodReference exprmref = (ExpressionMethodReference)_methodRef;
                Expression expr1 = exprmref.getExpression();
                DFKlass klass = null;
                if (expr1 instanceof Name) {
                    try {
                        klass = finder.lookupKlass((Name)expr1);
                    } catch (TypeNotFound e) {
                    }
                }
                if (klass == null) {
                    klass = (DFKlass)this.listDefinedExpr(defined, this.getScope(), expr1);
                }
                if (klass != null) {
                    try {
                        klass = finder.getParameterized(klass, exprmref.typeArguments());
                        _refMethod = klass.findMethod(
                            CallStyle.InstanceOrStatic, exprmref.getName(), argTypes);
                    } catch (TypeNotFound e) {
                    }
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
        public void writeGraph(Exporter exporter)
            throws InvalidSyntax, EntityNotFound {
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

    protected DFKlass parameterize(Map<String, DFType> paramTypes) {
        assert false;
        return null;
    }

    public ASTNode getAST() {
        return _methodRef;
    }

    @Override
    public String toString() {
        return ("<DFMethodRefKlass("+this.getTypeName()+")>");
    }

    public boolean isDefined() {
        return _funcMethod.isDefined();
    }

    @Override
    public DFMethod getFuncMethod() {
        return _funcMethod;
    }

    @Override
    protected void build() {
        DFTypeFinder finder = this.getFinder();
        _funcMethod = new FunctionalMethod(FUNC_NAME, this.getKlassScope(), finder);
        this.addMethod(_funcMethod, FUNC_NAME);
    }

    @Override
    public void setBaseKlass(DFKlass klass) {
        this.load();
        super.setBaseKlass(klass);
        DFMethod funcMethod = klass.getFuncMethod();
        // BaseKlass does not have a function method.
        // This happens when baseKlass type is undefined.
        if (funcMethod == null) return;
        _funcMethod.setFuncType(funcMethod.getFuncType());
    }
}
