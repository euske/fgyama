//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;
import org.w3c.dom.*;


//  DFGlobalVarSpace
//
public class DFGlobalVarSpace extends DFVarSpace {

    private Map<String, DFVarRef> _id2ref =
        new HashMap<String, DFVarRef>();

    public DFGlobalVarSpace() {
        super("GLOBAL");
    }

    @Override
    public String toString() {
        return ("<DFGlobalVarSpace>");
    }

    public DFVarRef lookupArray(DFType type) {
        DFVarRef ref;
        if (type instanceof DFArrayType) {
            DFType elemType = ((DFArrayType)type).getElemType();
	    String id = "%:"+elemType.getName();
	    ref = _id2ref.get(id);
	    if (ref == null) {
		ref = new DFVarRef(null, id, elemType);
		_id2ref.put(id, ref);
	    }
        } else {
	    String id = "%:?";
	    ref = _id2ref.get(id);
	    if (ref == null) {
		ref = new DFVarRef(null, id, null);
		_id2ref.put(id, ref);
	    }
        }
        return ref;
    }
}
