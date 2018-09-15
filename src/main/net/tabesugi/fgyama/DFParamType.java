//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFParamType
//
public class DFParamType extends DFKlass {

    private int _index;

    public DFParamType(
        String name, DFTypeSpace typeSpace, int index) {
        super(name, typeSpace);
        assert typeSpace != null;
        _index = index;
    }

    public int getIndex() {
        return _index;
    }
}
