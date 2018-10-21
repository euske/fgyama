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
        super(name, typeSpace, null, null, DFRootTypeSpace.getObjectKlass());
        assert typeSpace != null;
        _index = index;
    }

    public int getIndex() {
        return _index;
    }

    @Override
    public String getTypeName() {
        return _name+":"+_baseKlass.getTypeName();
    }

    @SuppressWarnings("unchecked")
    public void build(DFTypeFinder finder, TypeParameter typeParam)
        throws TypeNotFound {
        try {
            List<Type> bounds = typeParam.typeBounds();
            if (0 < bounds.size()) {
                _baseIfaces = new DFKlass[bounds.size()-1];
                for (int i = 0; i < bounds.size(); i++) {
                    DFKlass klass = finder.resolveKlass(bounds.get(i));
                    //Logger.info("DFKlass.build: "+this+": "+klass);
                    if (i == 0) {
                        _baseKlass = klass;
                    } else {
                        _baseIfaces[i-1] = klass;
                    }
                    finder = klass.addFinders(finder);
                }
            }
        } catch (TypeNotFound e) {
            e.setAst(typeParam);
            throw e;
        }
    }
}
