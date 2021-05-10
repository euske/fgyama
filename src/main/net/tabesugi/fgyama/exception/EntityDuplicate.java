//  Java2DF
//
package net.tabesugi.fgyama;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  EntityDuplicate
//
public class EntityDuplicate extends Exception {

    private static final long serialVersionUID = 1L;

    public String name;
    public ASTNode ast = null;

    public EntityDuplicate(String name) {
        this.name = name;
    }

    public void setAst(ASTNode ast) {
        if (this.ast == null) {
            this.ast = ast;
        }
    }
}
