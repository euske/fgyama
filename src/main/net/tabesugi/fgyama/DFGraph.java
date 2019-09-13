//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import javax.xml.stream.*;


//  DFGraph
//
interface DFGraph {

    String getGraphId();
    int addNode(DFNode node);

    void writeXML(XMLStreamWriter writer) throws XMLStreamException;
}
