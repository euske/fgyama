//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;
import org.w3c.dom.*;


//  DFTypeSpace
//
public class DFTypeSpace {

    private String _name;
    private DFTypeSpace _outerSpace;

    private SortedMap<String, DFTypeSpace> _id2space =
        new TreeMap<String, DFTypeSpace>();
    private SortedMap<String, DFKlass> _id2klass =
        new TreeMap<String, DFKlass>();

    public DFTypeSpace(String name, DFTypeSpace outerSpace) {
        _name = name;
        _outerSpace = outerSpace;
    }

    public DFTypeSpace(String name) {
	this(name, null);
    }

    @Override
    public String toString() {
        return ("<DFTypeSpace("+this.getSpaceName()+")>");
    }

    public int compareTo(DFTypeSpace space) {
        if (this == space) return 0;
	if (!(space instanceof DFTypeSpace)) return -1;
        if (_outerSpace != null) {
            if (space._outerSpace != null) {
                int x = _outerSpace.compareTo(space._outerSpace);
                if (x != 0) return x;
            } else {
                return +1;
            }
        } else if (space._outerSpace != null) {
            return -1;
        }
        return _name.compareTo(space._name);
    }

    public String getSpaceName() {
        if (_outerSpace == null) {
            return _name+"/";
        } else {
            return _outerSpace.getSpaceName()+_name+"/";
        }
    }

    public DFTypeSpace lookupSpace(SimpleName name) {
        return this.lookupSpace(name.getIdentifier());
    }

    public DFTypeSpace lookupSpace(String id) {
        int i = id.lastIndexOf('.');
        if (0 <= i) {
            DFTypeSpace space = this.lookupSpace(id.substring(0, i));
            return space.lookupSpace(id.substring(i+1));
        }
        DFKlass klass = _id2klass.get(id);
        if (klass != null) return klass;
        DFTypeSpace space = _id2space.get(id);
        if (space == null) {
            space = new DFTypeSpace(id, this);
            _id2space.put(id, space);
            //Logger.info("DFTypeSpace.addChild:", this, ":", id);
        }
        return space;
    }

    protected DFKlass createKlass(
        DFKlass outerKlass, DFVarScope outerScope, SimpleName name) {
        return this.createKlass(
            outerKlass, outerScope, name.getIdentifier());
    }

    protected DFKlass createKlass(
        DFKlass outerKlass, DFVarScope outerScope, String id) {
        assert id.indexOf('.') < 0;
        DFKlass klass = _id2klass.get(id);
        if (klass != null) return klass;
        klass = new DFKlass(id, this, outerKlass, outerScope);
        //Logger.info("DFTypeSpace.createKlass:", klass);
        return this.addKlass(id, klass);
    }

    public DFKlass addKlass(String id, DFKlass klass) {
        assert id.indexOf('.') < 0;
        //assert !_id2klass.containsKey(id);
        _id2klass.put(id, klass);
        //Logger.info("DFTypeSpace.addKlass:", this, ":", id);
        return klass;
    }

    public DFKlass getKlass(Name name)
        throws TypeNotFound {
        return this.getKlass(name.getFullyQualifiedName());
    }
    public DFKlass getKlass(String id)
        throws TypeNotFound {
        //Logger.info("DFTypeSpace.getKlass:", this, ":", id);
        int i = id.lastIndexOf('.');
        if (0 <= i) {
            DFTypeSpace space = this.lookupSpace(id.substring(0, i));
            return space.getKlass(id.substring(i+1));
        }
        DFKlass klass = _id2klass.get(id);
        if (klass == null) {
            throw new TypeNotFound(this.getSpaceName()+id);
        }
        return klass;
    }

    public DFKlass[] getKlasses() {
	DFKlass[] klasses = new DFKlass[_id2klass.size()];
	_id2klass.values().toArray(klasses);
	return klasses;
    }

    public void buildMapTypes(DFTypeFinder finder, DFMapType[] mapTypes) {
        for (DFMapType mapType : mapTypes) {
            try {
                // This might cause TypeNotFound
                // for a recursive type.
                mapType.build(finder);
            } catch (TypeNotFound e) {
            }
            this.addKlass(mapType.getTypeName(), mapType.getKlass());
        }
    }

    @SuppressWarnings("unchecked")
    public DFKlass buildAbstTypeDecl(
        String filePath, AbstractTypeDeclaration abstTypeDecl,
        DFKlass outerKlass, DFVarScope outerScope)
        throws UnsupportedSyntax {
        assert abstTypeDecl != null;
        //Logger.info("DFTypeSpace.build:", this, ":", abstTypeDecl.getName());
        DFKlass klass = this.createKlass(
            outerKlass, outerScope, abstTypeDecl.getName());
        if (abstTypeDecl instanceof TypeDeclaration) {
            klass.setMapTypes(
                ((TypeDeclaration)abstTypeDecl).typeParameters());
        }
        klass.setTree(filePath, abstTypeDecl, abstTypeDecl.bodyDeclarations());
	return klass;
    }

    @SuppressWarnings("unchecked")
    public static DFMapType[] getMapTypes(List<TypeParameter> tps) {
        if (tps.size() == 0) return null;
        DFMapType[] mapTypes = new DFMapType[tps.size()];
        for (int i = 0; i < tps.size(); i++) {
            TypeParameter tp = tps.get(i);
            String id = tp.getName().getIdentifier();
            mapTypes[i] = new DFMapType(id);
            mapTypes[i].setTypeBounds(tp.typeBounds());
        }
        return mapTypes;
    }

    public static DFTypeSpace createMapTypeSpace(DFMapType[] mapTypes) {
        StringBuilder b = new StringBuilder();
        for (DFMapType mapType : mapTypes) {
            if (0 < b.length()) {
                b.append(",");
            }
            b.append(mapType.getTypeName());
        }
        DFTypeSpace mapTypeSpace = new DFTypeSpace("{"+b.toString()+"}");
        for (DFMapType mapType : mapTypes) {
            mapTypeSpace.addKlass(
                mapType.getTypeName(),
                DFBuiltinTypes.getObjectKlass());
        }
        return mapTypeSpace;
    }

    // dump: for debugging.
    public void dump() {
        dump(System.err, "");
    }
    public void dump(PrintStream out, String indent) {
        out.println(indent+this.getSpaceName()+" {");
        String i2 = indent + "  ";
        for (Map.Entry<String,DFKlass> e : _id2klass.entrySet()) {
            out.println(i2+"defined: "+e.getKey()+" "+e.getValue());
        }
        for (DFTypeSpace space : _id2space.values()) {
            space.dump(out, i2);
        }
        out.println(indent+"}");
    }
}
