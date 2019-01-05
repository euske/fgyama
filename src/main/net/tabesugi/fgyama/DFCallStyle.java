//  Java2DF
//
package net.tabesugi.fgyama;


//  DFCallStyle
//
public enum DFCallStyle {
    Constructor,
    InstanceMethod,
    StaticMethod,
    Initializer;

    @Override
    public String toString() {
        switch (this) {
        case InstanceMethod:
            return "instance";
        case StaticMethod:
            return "static";
        case Initializer:
            return "initializer";
        case Constructor:
            return "constructor";
        default:
            return null;
        }
    }
}
