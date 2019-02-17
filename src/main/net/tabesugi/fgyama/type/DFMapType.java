//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFMapType
//
public class DFMapType extends DFKlass {

    public DFMapType(
        String name, DFTypeSpace typeSpace) {
        super(name, typeSpace, null, null);
        this.setBuilt();
    }

    @Override
    public String toString() {
        return ("<DFMapType("+this.getFullName()+":"+this.getBaseKlass()+")>");
    }

    public int canConvertFrom(DFType type, Map<DFMapType, DFType> typeMap) {
        if (typeMap == null) {
            typeMap = new HashMap<DFMapType, DFType>();
        }
        DFType type2 = typeMap.get(this);
        if (type2 != null) {
            return type2.canConvertFrom(type, typeMap);
        }
        typeMap.put(this, type);
        return this.getBaseKlass().canConvertFrom(type, typeMap);
    }

    public DFType parameterize(Map<DFMapType, DFType> typeMap) {
        if (typeMap.containsKey(this)) {
            return typeMap.get(this);
        } else {
            return this;
        }
    }

    @SuppressWarnings("unchecked")
    public void buildTypeParam(DFTypeFinder finder, TypeParameter tp)
        throws TypeNotFound {
        //Logger.info("DFMapType.build:", this, ":", tp);
        try {
            List<Type> bounds = tp.typeBounds();
            if (0 < bounds.size()) {
                DFKlass baseKlass = null;
                DFKlass[] baseIfaces = new DFKlass[bounds.size()-1];
                for (int i = 0; i < bounds.size(); i++) {
                    DFKlass klass = finder.resolveKlass(bounds.get(i));
                    //Logger.info("DFMapType.build:", this, ":", klass);
                    if (i == 0) {
                        baseKlass = klass;
                    } else {
                        baseIfaces[i-1] = klass;
                    }
                    finder = finder.extend(klass);
                }
                setBaseKlass(baseKlass);
                setBaseIfaces(baseIfaces);
            }
        } catch (TypeNotFound e) {
            e.setAst(tp);
            throw e;
        }
    }
}
