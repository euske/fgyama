//  Java2DF
//
package net.tabesugi.fgyama;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  MethodNotFound
//
public class MethodNotFound extends EntityNotFound {

    private static final long serialVersionUID = 1L;

    public DFType[] argTypes;
    public DFType returnType;

    public MethodNotFound(String name, DFType[] argTypes, DFType returnType) {
        super(name);
        this.argTypes = argTypes;
        this.returnType = returnType;
    }

    public MethodNotFound(SimpleName name, DFType[] argTypes, DFType returnType) {
        this(name.getIdentifier(), argTypes, returnType);
    }
}
