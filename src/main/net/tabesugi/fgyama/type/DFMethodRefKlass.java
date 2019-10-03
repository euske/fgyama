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
        String name, DFTypeSpace outerSpace,
        DFKlass outerKlass, DFVarScope outerScope) {
	super(name, outerSpace, outerKlass, outerScope);
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
    protected void buildMembersFromTree(DFTypeFinder finder, ASTNode ast)
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
        try {
            DFType[] argTypes = funcType.getRealArgTypes();
            if (ast instanceof CreationReference) {
                _refMethod = _refKlass.lookupMethod(
                    DFMethod.CallStyle.Constructor, (String)null, argTypes);
            } else if (ast instanceof SuperMethodReference) {
                SimpleName name = ((SuperMethodReference)ast).getName();
                _refMethod = _refKlass.lookupMethod(
                    DFMethod.CallStyle.StaticMethod, name, argTypes);
            } else if (ast instanceof TypeMethodReference) {
                SimpleName name = ((TypeMethodReference)ast).getName();
                _refMethod = _refKlass.lookupMethod(
                    DFMethod.CallStyle.StaticMethod, name, argTypes);
            } else if (ast instanceof ExpressionMethodReference) {
                SimpleName name = ((ExpressionMethodReference)ast).getName();
                _refMethod = _refKlass.lookupMethod(
                    DFMethod.CallStyle.InstanceOrStatic, name, argTypes);
            }
            //Logger.info("DFMethodRefKlass.fixateMethod:", _refMethod);
        } catch (MethodNotFound e) {
            Logger.error(
                "DFMethodRefKlass.fixateMethod: MethodNotFound",
                this, _refKlass, e.name);
        }
    }

}
