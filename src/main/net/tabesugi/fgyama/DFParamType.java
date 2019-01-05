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

    public DFParamType(
        String name, DFTypeSpace typeSpace) {
        super(name, typeSpace, null, null, DFBuiltinTypes.getObjectKlass());
    }

    @Override
    public String toString() {
        return ("<DFParamType("+this.getFullName()+":"+_baseKlass+")>");
    }

    @Override
    public String getTypeName() {
        return _name+":"+_baseKlass.getTypeName();
    }

    public int canConvertFrom(DFType type, Map<DFParamType, DFType> typeMap) {
        if (typeMap == null) {
            typeMap = new HashMap<DFParamType, DFType>();
        }
        DFType type2 = typeMap.get(this);
        if (type2 != null) {
            return type2.canConvertFrom(type, typeMap);
        }
        typeMap.put(this, type);
        return _baseKlass.canConvertFrom(type, typeMap);
    }

    public DFType parameterize(Map<DFParamType, DFType> typeMap) {
        if (typeMap.containsKey(this)) {
            return typeMap.get(this);
        } else {
            return this;
        }
    }

    public void load(DFTypeFinder finder, JNITypeParser parser)
        throws TypeNotFound {
        this.setLoaded();
        _baseKlass = (DFKlass)parser.getType(finder);
    }

    protected void buildFromTree(DFTypeFinder finder, ASTNode ast)
        throws UnsupportedSyntax, TypeNotFound {
        if (ast instanceof TypeParameter) {
            this.build(finder, (TypeParameter)ast);
        }
    }

    @SuppressWarnings("unchecked")
    private void build(DFTypeFinder finder, TypeParameter typeParam)
        throws TypeNotFound {
        //Logger.info("DFParamType.build: "+this+": "+typeParam);
        try {
            List<Type> bounds = typeParam.typeBounds();
            if (0 < bounds.size()) {
                _baseIfaces = new DFKlass[bounds.size()-1];
                for (int i = 0; i < bounds.size(); i++) {
                    DFKlass klass = finder.resolveKlass(bounds.get(i));
                    //Logger.info("DFParamType.build: "+this+": "+klass);
                    if (i == 0) {
                        _baseKlass = klass;
                    } else {
                        _baseIfaces[i-1] = klass;
                    }
                    finder = finder.extend(klass);
                }
            }
        } catch (TypeNotFound e) {
            e.setAst(typeParam);
            throw e;
        }
    }
}
