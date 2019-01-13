//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;
import org.w3c.dom.*;


//  DFGlobalVarScope
//
public class DFGlobalVarScope extends DFVarScope {

    private Map<String, DFRef> _id2ref =
        new HashMap<String, DFRef>();

    public DFGlobalVarScope() {
        super("GLOBAL");
    }

    @Override
    public String toString() {
        return ("<DFGlobalVarScope>");
    }

    public DFRef lookupArray(DFType type) {
        DFRef ref;
        if (type instanceof DFArrayType) {
            DFType elemType = ((DFArrayType)type).getElemType();
	    String id = "%:"+elemType.getTypeName();
	    ref = _id2ref.get(id);
	    if (ref == null) {
		ref = new DFRef(null, id, elemType);
		_id2ref.put(id, ref);
	    }
        } else {
	    String id = "%:?";
	    ref = _id2ref.get(id);
	    if (ref == null) {
		ref = new DFRef(null, id, null);
		_id2ref.put(id, ref);
	    }
        }
        return ref;
    }
}
