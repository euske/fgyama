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

        @Override
        protected DFMethod parameterize(Map<String, DFKlass> paramTypes) {
            assert false;
            return null;
        }

        protected void setFuncType(DFFuncType funcType) {
            assert _funcType == null;
            if (funcType == null) {
                funcType = new DFFuncType(new DFType[] {}, null);
            }
            _funcType = funcType;
        }

        public ASTNode getAST() {
            return _methodRef;
        }

        @Override
        public boolean addOverrider(DFMethod method) {
            Logger.error("DFMethodRefKlass.FunctionalMethod: cannot override:", method);
            assert false;
            return false;
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
                if (type instanceof DFSourceKlass) {
                    ((DFSourceKlass)type).listUsedKlasses(klasses);
                }

            } else if (_methodRef instanceof SuperMethodReference) {
                SuperMethodReference supermref = (SuperMethodReference)_methodRef;
                DFKlass klass = DFMethodRefKlass.this.getBaseKlass();
                // XXX ignored: supermref.typeArguments()
                if (klass instanceof DFSourceKlass) {
                    ((DFSourceKlass)klass).listUsedKlasses(klasses);
                }

            } else if (_methodRef instanceof ExpressionMethodReference) {
                // XXX ignored exprmref.typeArguments().
                ExpressionMethodReference exprmref = (ExpressionMethodReference)_methodRef;
                try {
                    this.listUsedExpr(
                        klasses, exprmref.getExpression());
                } catch (InvalidSyntax e) {
                    Logger.error(
                        "DFMethodRefKlass.listUsedKlasses:",
                        Utils.getASTSource(e.ast), this);
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
            DFType returnType = _funcType.getReturnType();
            if (_methodRef instanceof CreationReference) {
                CreationReference creatmref = (CreationReference)_methodRef;
                try {
                    DFKlass klass = finder.resolve(creatmref.getType()).toKlass();
                    _refMethod = klass.lookupMethod(
                        CallStyle.Constructor, (String)null,
                        argTypes, returnType);
                } catch (TypeNotFound e) {
                } catch (MethodNotFound e) {
                }

            } else if (_methodRef instanceof TypeMethodReference) {
                TypeMethodReference typemref = (TypeMethodReference)_methodRef;
                try {
                    DFKlass klass = finder.resolve(typemref.getType()).toKlass();
                    _refMethod = klass.lookupMethod(
                        CallStyle.StaticMethod, typemref.getName(),
                        argTypes, returnType);
                } catch (TypeNotFound e) {
                } catch (MethodNotFound e) {
                }

            } else if (_methodRef instanceof SuperMethodReference) {
                SuperMethodReference supermref = (SuperMethodReference)_methodRef;
                try {
                    DFKlass klass = DFMethodRefKlass.this.getBaseKlass();
                    // XXX ignored: supermref.typeArguments()
                    _refMethod = klass.lookupMethod(
                        CallStyle.StaticMethod, supermref.getName(),
                        argTypes, returnType);
                } catch (MethodNotFound e) {
                }

            } else if (_methodRef instanceof ExpressionMethodReference) {
                ExpressionMethodReference exprmref = (ExpressionMethodReference)_methodRef;
                Expression expr1 = exprmref.getExpression();
                DFKlass klass = null;
                if (expr1 instanceof Name) {
                    try {
                        klass = finder.resolveKlass((Name)expr1);
                    } catch (TypeNotFound e) {
                    }
                }
                if (klass == null) {
                    DFType type = this.listDefinedExpr(defined, this.getScope(), expr1);
                    if (type != null) {
                        klass = type.toKlass();
                    }
                }
                if (klass != null) {
                    // XXX ignored: exprmref.typeArguments()
                    try {
                        _refMethod = klass.lookupMethod(
                            CallStyle.InstanceOrStatic, exprmref.getName(),
                            argTypes, returnType);
                    } catch (MethodNotFound e) {
                    }
                }

            } else {
                throw new InvalidSyntax(_methodRef);
            }

            if (_refMethod == null) {
                Logger.error(
                    "DFMethodRefKlass.listDefinedKlasses: MethodNotFound",
                    Utils.getASTSource(_methodRef), this);
            }
        }

        @Override
        public DFGraph getDFGraph(int graphId)
            throws InvalidSyntax, EntityNotFound {
            return null;
        }
    }

    private MethodReference _methodRef;

    private DFKlass _baseKlass = null;
    private FunctionalMethod _funcMethod = null;

    public DFMethodRefKlass(
        MethodReference methodRef,
        DFTypeSpace outerSpace, DFSourceKlass outerKlass,
        DFVarScope outerScope)
        throws InvalidSyntax {
        super(Utils.encodeASTNode(methodRef),
              outerSpace, outerKlass, outerScope,
              outerKlass.getFilePath(), outerKlass.isAnalyze());

        _methodRef = methodRef;
    }

    public ASTNode getAST() {
        return _methodRef;
    }

    @Override
    public String toString() {
        return ("<DFMethodRefKlass("+this.getTypeName()+")>");
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
        _funcMethod = new FunctionalMethod("#f", this.getKlassScope(), finder);
    }

    public int canConvertTo(DFKlass klass)
        throws TypeIncompatible {
        DFMethod method = klass.getFuncMethod();
        if (method == null) {
            throw new TypeIncompatible(klass, this);
        }
        return 0;
    }

    public void setBaseKlass(DFKlass baseKlass) {
        this.load();
        if (baseKlass instanceof DFMethodRefKlass) {
            // XXX Edge case when the outer function is a generic method
            // which is reified with this methodref itself.
            // e.g. <T> foo(T a) { foo(A::foo); }
            _baseKlass = DFBuiltinTypes.getObjectKlass();
            _funcMethod.setFuncType(null);
            return;
        }
        assert !(baseKlass instanceof DFLambdaKlass);
        assert _baseKlass == null;
        _baseKlass = baseKlass;
        DFMethod funcMethod = baseKlass.getFuncMethod();
        // BaseKlass does not have a function method.
        // This happens when baseKlass type is undefined.
        if (funcMethod == null) {
            _funcMethod.setFuncType(null);
        } else {
            _funcMethod.setFuncType(funcMethod.getFuncType());
        }
    }

    @Override
    public void listDefinedKlasses(Collection<DFSourceKlass> defined)
        throws InvalidSyntax {
        assert this.isDefined();
        super.listDefinedKlasses(defined);
    }
}
