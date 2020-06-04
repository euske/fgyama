//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFMethodRefKlass
//
class DFMethodRefKlass extends DFLambdaKlass {

    private DFKlass _refKlass = null;
    private DFMethod _refMethod = null;

    public DFMethodRefKlass(
        String name, DFTypeSpace outerSpace, DFVarScope outerScope,
        DFSourceKlass outerKlass) {
        super(name, outerSpace, outerScope, outerKlass);
    }

    @Override
    public String toString() {
        return ("<DFMethodRefKlass("+this.getTypeName()+")>");
    }

    @Override
    protected void buildTypeFromDecls(ASTNode ast)
        throws InvalidSyntax {
        MethodReference methodref = (MethodReference)ast;
    }

    @Override
    protected void buildMembersFromAST(DFTypeFinder finder, ASTNode ast)
        throws InvalidSyntax {
    }

    @Override
    public boolean isDefined() {
        return (super.isDefined() && _refMethod != null);
    }

    @Override
    public void setBaseKlass(DFKlass klass) {
        super.setBaseKlass(klass);
        if (_refKlass != null) {
            this.fixateMethod();
        }
    }

    public void setRefKlass(DFKlass refKlass) {
        _refKlass = refKlass;
        if (super.isDefined()) {
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
