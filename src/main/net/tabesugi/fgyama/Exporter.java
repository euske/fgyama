//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;


//  Exporter
//
abstract class Exporter {

    abstract public void close() throws IOException;
    abstract public void startFile(String path) throws IOException;
    abstract public void endFile() throws IOException;
    
    abstract public void writeError(String funcName, String astName) throws IOException;
    abstract public void writeGraph(DFGraph graph) throws IOException;
}
