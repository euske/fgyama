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

    private Map<DFType, DFRef> _arrays =
        new HashMap<DFType, DFRef>();

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
        ref = _arrays.get(type);
        if (ref == null) {
            ref = new DFElemRef(elemType);
            _arrays.put(type, ref);
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
        public String getFullName() {
            return "%"+this.getRefType().getTypeName();
        }
    }
}
