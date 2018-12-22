//  Java2DF
//
package net.tabesugi.fgyama;


//  MethodNotFound
//
public class MethodNotFound extends EntityNotFound {

    private static final long serialVersionUID = 1L;

    public DFType[] argTypes;

    public MethodNotFound(String name, DFType[] argTypes) {
        super(name);
        this.argTypes = argTypes;
    }
}
