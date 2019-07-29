//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;
import org.w3c.dom.*;


//  DFGlobalScope
//
public class DFGlobalScope extends DFVarScope {

    private Map<String, DFRef> _id2elem =
        new HashMap<String, DFRef>();

    public DFGlobalScope() {
        super("GLOBAL");
    }

    @Override
    public String toString() {
        return ("<DFGlobalScope>");
    }

    public DFRef lookupArray(DFType type) {
        DFRef ref;
        DFType elemType = DFUnknownType.UNKNOWN;
        if (type instanceof DFArrayType) {
            elemType = ((DFArrayType)type).getElemType();
        }
        String id = elemType.getTypeName();
        ref = _id2elem.get(id);
        if (ref == null) {
            ref = new DFElemRef(elemType);
            _id2elem.put(id, ref);
        }
        return ref;
    }

    private class DFElemRef extends DFRef {

        public DFElemRef(DFType type) {
            super(type);
        }

        @Override
        public boolean isLocal() {
            return false;
        }
        @Override
        public boolean isInternal() {
            return false;
        }

        @Override
        public String getFullName() {
            return "%"+this.getRefType().getTypeName();
        }
    }
}
