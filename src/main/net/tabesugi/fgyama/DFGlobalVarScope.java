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
        DFType elemType = DFUnknownType.UNKNOWN;
        if (type instanceof DFArrayType) {
            elemType = ((DFArrayType)type).getElemType();
        }
        String id = elemType.getTypeName();
        ref = _id2ref.get(id);
        if (ref == null) {
            ref = new DFElemRef(this, id, elemType);
            _id2ref.put(id, ref);
        }
        return ref;
    }

    private class DFElemRef extends DFRef {
        public DFElemRef(DFVarScope scope, String name, DFType type) {
            super(scope, ":"+name, type);
        }

        public String getFullName() {
            return "%"+getName();
        }
    }
}
