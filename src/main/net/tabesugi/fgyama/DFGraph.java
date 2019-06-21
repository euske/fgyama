//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.w3c.dom.*;


//  DFGraph
//
interface DFGraph {

    String getHash();
    Element toXML(Document document);
    int addNode(DFNode node);

}
