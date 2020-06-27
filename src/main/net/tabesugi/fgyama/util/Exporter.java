//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;


//  Exporter
//
abstract class Exporter {

    int _baseId;

    public Exporter(int baseId) {
        _baseId = baseId;
    }

    public int getNewId() {
        return _baseId++;
    }

    public void close() {
    }

    public abstract void startKlass(DFKlass klass);
    public abstract void endKlass();
    public abstract void writeGraph(DFGraph graph);
}
