//  Java2DF
//
package net.tabesugi.fgyama;


//  EntityNotFound
//
public class EntityNotFound extends Exception {

    private static final long serialVersionUID = 1L;

    public String name = null;

    public EntityNotFound(String name) {
	this.name = name;
    }
}
