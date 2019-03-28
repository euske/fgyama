//  Java2DF
//
package net.tabesugi.fgyama;


//  TypeNotFound
//
public class TypeNotFound extends EntityNotFound {

    private static final long serialVersionUID = 1L;

    public DFTypeFinder finder;

    public TypeNotFound(String name) {
        this(name, null);
    }

    public TypeNotFound(String name, DFTypeFinder finder) {
        super(name);
        this.finder = finder;
    }
}
