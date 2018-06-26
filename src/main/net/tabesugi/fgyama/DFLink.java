//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;
import org.w3c.dom.*;


//  DFLink
//
class DFLink {

    private DFNode _dst;
    private DFNode _src;
    private String _label;

    public DFLink(DFNode dst, DFNode src, String label)
    {
        _dst = dst;
        _src = src;
        _label = label;
    }

    @Override
    public String toString() {
        return ("<DFLink "+_dst+"<-"+_src+">");
    }

    public Element toXML(Document document) {
        Element elem = document.createElement("link");
        elem.setAttribute("src", _src.getName());
        if (_label != null) {
            elem.setAttribute("label", _label);
        }
        return elem;
    }
}
