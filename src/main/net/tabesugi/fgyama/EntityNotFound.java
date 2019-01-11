//  Java2DF
//
package net.tabesugi.fgyama;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  EntityNotFound
//
public class EntityNotFound extends Exception {

    private static final long serialVersionUID = 1L;

    public String name;
    public ASTNode ast = null;
    public DFMethod method = null;

    public EntityNotFound(String name) {
        this.name = name;
    }

    public void setAst(ASTNode ast) {
        if (this.ast == null) {
            this.ast = ast;
        }
    }

    public void setMethod(DFMethod method) {
        if (this.method == null) {
            this.method = method;
        }
    }
}
