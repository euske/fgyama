//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;


//  Exporter
//
abstract class Exporter {

    public abstract void startKlass(DFKlass klass);
    public abstract void endKlass();
    public abstract void writeMethod(DFMethod method)
        throws InvalidSyntax, EntityNotFound;
}
