//  Java2DF
//
package net.tabesugi.fgyama;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  InvalidSyntax
//
public class InvalidSyntax extends Exception {

    private static final long serialVersionUID = 1L;

    public ASTNode ast;
    public String name = null;

    public InvalidSyntax(ASTNode ast) {
        assert ast != null;
        this.ast = ast;
    }

    public String getAstName() {
        return this.ast.getClass().getName();
    }
}
