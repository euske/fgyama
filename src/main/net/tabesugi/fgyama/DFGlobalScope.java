//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


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

    @Override
    public DFRef lookupArray(DFType type) {
        DFRef ref;
        DFType elemType = DFUnknownType.UNKNOWN;
        if (type instanceof DFArrayType) {
            elemType = ((DFArrayType)type).getElemType();
        }
        ref = _arrays.get(elemType);
        if (ref == null) {
            ref = new ElemRef(elemType);
            _arrays.put(elemType, ref);
        }
        return ref;
    }

    private class ElemRef extends DFRef {

        public ElemRef(DFType type) {
            super(type);
        }

        @Override
        public DFVarScope getScope() {
            return DFGlobalScope.this;
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
