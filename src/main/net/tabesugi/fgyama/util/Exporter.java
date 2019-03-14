//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;


//  Exporter
//
abstract class Exporter {

    abstract public void close();
    abstract public void startKlass(DFKlass klass);
    abstract public void endKlass();
    abstract public void writeGraph(DFGraph graph);
}
