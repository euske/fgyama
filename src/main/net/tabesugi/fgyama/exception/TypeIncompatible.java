//  Java2DF
//
package net.tabesugi.fgyama;


//  TypeIncompatible
//
public class TypeIncompatible extends Exception {

    private static final long serialVersionUID = 1L;

    public DFType type1;
    public DFType type2;

    public TypeIncompatible(DFType type1, DFType type2) {
        this.type1 = type1;
        this.type2 = type2;
    }
}
