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

    public MethodNotFound(String name, DFType[] argTypes) {
        super(name);
        this.argTypes = argTypes;
    }

    public MethodNotFound(SimpleName name, DFType[] argTypes) {
        this(name.getIdentifier(), argTypes);
    }
}
