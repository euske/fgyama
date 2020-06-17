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

    private class FunctionalMethod extends DFMethod {

        public FunctionalMethod(String id, DFVarScope scope) {
            super(DFMethodRefKlass.this, CallStyle.Lambda, false,
                  id, id, scope);
        }
    }

    private DFKlass _refKlass = null;
    private DFMethod _refMethod = null;

    private final String FUNC_NAME = "#f";

    private FunctionalMethod _funcMethod;

    public DFMethodRefKlass(
        String filePath, MethodReference methodref,
        DFTypeSpace outerSpace, DFVarScope outerScope, DFSourceKlass outerKlass)
        throws InvalidSyntax {
        super(filePath, methodref, Utils.encodeASTNode(methodref),
              outerSpace, outerScope, outerKlass);
        _funcMethod = new FunctionalMethod(FUNC_NAME, outerScope);
    }

    @Override
    public String toString() {
        return ("<DFMethodRefKlass("+this.getTypeName()+")>");
    }

    @Override
    protected void buildMembersFromAST(DFTypeFinder finder, ASTNode ast)
        throws InvalidSyntax {
    }

    @Override
    public int isSubclassOf(DFKlass klass, Map<DFMapType, DFKlass> typeMap) {
        if (klass instanceof DFSourceKlass &&
            ((DFSourceKlass)klass).isFuncInterface()) return 0;
        return -1;
    }

    @Override
    public boolean isDefined() {
        return (_funcMethod.getFuncType() != null && _refMethod != null);
    }

    @Override
    public DFMethod getFuncMethod() {
        return _funcMethod;
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
            this.fixateMethod();
        }
    }

    public void setRefKlass(DFKlass refKlass) {
        _refKlass = refKlass;
        if (_funcMethod.getFuncType() != null) {
            this.fixateMethod();
        }
    }

    private void fixateMethod() {
        assert _refKlass != null;
        DFFunctionType funcType = this.getFuncMethod().getFuncType();
        assert funcType != null;
        ASTNode ast = this.getTree();
        assert ast instanceof MethodReference;
        DFType[] argTypes = funcType.getRealArgTypes();
        DFMethod method = null;
        if (ast instanceof CreationReference) {
            method = _refKlass.findMethod(
                DFMethod.CallStyle.Constructor, (String)null, argTypes);
        } else if (ast instanceof SuperMethodReference) {
            SimpleName name = ((SuperMethodReference)ast).getName();
            method = _refKlass.findMethod(
                DFMethod.CallStyle.StaticMethod, name, argTypes);
        } else if (ast instanceof TypeMethodReference) {
            SimpleName name = ((TypeMethodReference)ast).getName();
            method = _refKlass.findMethod(
                DFMethod.CallStyle.StaticMethod, name, argTypes);
        } else if (ast instanceof ExpressionMethodReference) {
            SimpleName name = ((ExpressionMethodReference)ast).getName();
            method = _refKlass.findMethod(
                DFMethod.CallStyle.InstanceOrStatic, name, argTypes);
        }
        //Logger.info("DFMethodRefKlass.fixateMethod:", method);
        if (method != null) {
            _refMethod = method;
        } else {
            Logger.error(
                "DFMethodRefKlass.fixateMethod: MethodNotFound",
                this, _refKlass, ast);
        }
    }

}
