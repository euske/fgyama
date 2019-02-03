//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;


//  Exporter
//
abstract class Exporter {

    abstract public void close();
    abstract public void startFile(String path);
    abstract public void endFile();

    abstract public void writeError(String funcName, String astName);
    abstract public void writeGraph(DFGraph graph);
}
