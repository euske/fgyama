//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;


//  Exporter
//
abstract class Exporter {

    public void close() {
    }

    public abstract void startKlass(DFSourceKlass klass);
    public abstract void endKlass();
    public abstract void writeMethod(DFSourceMethod method)
        throws InvalidSyntax, EntityNotFound;
}
