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

    private DFTypeSpace _root;
    private String _name;
    private DFTypeSpace _parent;

    private DFClassSpace _default;

    private List<DFTypeSpace> _children =
	new ArrayList<DFTypeSpace>();
    private Map<String, DFTypeSpace> _id2space =
	new HashMap<String, DFTypeSpace>();
    private Map<String, DFClassSpace> _id2klass =
	new HashMap<String, DFClassSpace>();

    public DFTypeSpace() {
        _root = this;
	_default = new DFClassSpace(this);
	_name = ".";
        _parent = null;
    }

    public DFTypeSpace(DFTypeSpace parent, String name) {
        _root = parent._root;
	_default = parent._default;
	_name = name;
	_parent = parent;
    }

    public DFTypeSpace(DFTypeSpace space) {
        _root = space._root;
        _default = space._default;
        _name = space._name;
        _parent = space._parent;
        _children = new ArrayList<DFTypeSpace>(space._children);
        _id2space = new HashMap<String, DFTypeSpace>(space._id2space);
        _id2klass = new HashMap<String, DFClassSpace>(space._id2klass);
    }

    @Override
    public String toString() {
	return ("<DFTypeSpace("+this.getName()+")>");
    }

    public String getName() {
        if (_parent == null) {
            return _name;
        } else if (_parent == _root) {
            return "."+_name;
        } else {
            return _parent.getName()+"."+_name;
        }
    }

    private DFTypeSpace addChild(String id) {
        Utils.logit("DFTypeSpace.addChild: "+this+": "+id);
        DFTypeSpace space = new DFTypeSpace(this, id);
        _children.add(space);
        _id2space.put(id, space);
        return space;
    }

    public DFTypeSpace lookupSpace(SimpleName name) {
        String id = name.getIdentifier();
        DFTypeSpace space = _id2space.get(id);
        if (space == null) {
            space = this.addChild(id);
        }
        return space;
    }

    public DFTypeSpace lookupSpace(Name name) {
        if (name.isQualifiedName()) {
	    QualifiedName qname = (QualifiedName)name;
	    DFTypeSpace parent = (_parent != null)? _parent : this;
	    parent = parent.lookupSpace(qname.getQualifier());
            return parent.lookupSpace(qname.getName());
        } else {
            return this.lookupSpace((SimpleName)name);
        }
    }

    public DFTypeSpace lookupSpace(PackageDeclaration pkgDecl) {
        if (pkgDecl == null) {
            return this;
        } else {
            return this.lookupSpace(pkgDecl.getName());
        }
    }

    public DFClassSpace getDefaultClass() {
	return _default;
    }

    private void addClass(SimpleName name) {
        this.addClass(name, new DFClassSpace(this, name));
    }
    private void addClass(SimpleName name, DFClassSpace klass) {
        Utils.logit("DFTypeSpace.addClass: "+this+": "+klass);
        String id = name.getIdentifier();
        assert(!_id2klass.containsKey(id));
	_id2klass.put(id, klass);
    }

    public DFClassSpace lookupClass(SimpleName name) {
        String id = name.getIdentifier();
        DFClassSpace klass = _id2klass.get(id);
        if (klass != null) {
            return klass;
        } else if (_parent != null) {
            return _parent.lookupClass(name);
        } else {
            return null;
        }
    }

    public DFClassSpace lookupClass(Name name) {
        if (name.isQualifiedName()) {
	    QualifiedName qname = (QualifiedName)name;
            DFTypeSpace parent = this.lookupSpace(qname.getQualifier());
            return parent.lookupClass(qname.getName());
        } else {
            return this.lookupClass((SimpleName)name);
        }
    }

    public DFClassSpace resolveClass(DFType type) {
        if (type instanceof DFClassType) {
            return ((DFClassType)type).getKlass();
        }
        return this.getDefaultClass();
    }

    @SuppressWarnings("unchecked")
    public DFType resolve(Type type) {
	if (type instanceof PrimitiveType) {
            PrimitiveType ptype = (PrimitiveType)type;
            return new DFBasicType(ptype.getPrimitiveTypeCode());
	} else if (type instanceof SimpleType) {
            SimpleType stype = (SimpleType)type;
            DFClassSpace klass = this.lookupClass(stype.getName());
            if (klass == null) {
                klass = this.getDefaultClass();
            }
            return new DFClassType(klass);
	} else if (type instanceof ArrayType) {
            ArrayType atype = (ArrayType)type;
	    DFType elemType = this.resolve(atype.getElementType());
	    int ndims = atype.getDimensions();
	    return new DFArrayType(elemType, ndims);
	} else if (type instanceof ParameterizedType) {
            ParameterizedType ptype = (ParameterizedType)type;
            List<Type> args = (List<Type>) ptype.typeArguments();
            DFType baseType = this.resolve(ptype.getType());
            DFType[] argTypes = new DFType[args.size()];
            for (int i = 0; i < args.size(); i++) {
                argTypes[i] = this.resolve(args.get(i));
            }
            if (baseType instanceof DFClassType) {
                // XXX make DFCompoundType
                return new DFClassType(
                    ((DFClassType)baseType).getKlass(), argTypes);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public void build(CompilationUnit cunit)
	throws UnsupportedSyntax {
        for (TypeDeclaration typeDecl :
                 (List<TypeDeclaration>) cunit.types()) {
            this.build(typeDecl);
        }
    }

    @SuppressWarnings("unchecked")
    public void build(TypeDeclaration typeDecl)
	throws UnsupportedSyntax {
        Utils.logit("DFTypeSpace.build: "+this+": "+typeDecl.getName());
        this.addClass(typeDecl.getName());
        DFTypeSpace child = this.lookupSpace(typeDecl.getName());
        for (BodyDeclaration body :
                 (List<BodyDeclaration>) typeDecl.bodyDeclarations()) {
            if (body instanceof TypeDeclaration) {
                child.build((TypeDeclaration)body);
            } else if (body instanceof FieldDeclaration) {
                ;
            } else if (body instanceof MethodDeclaration) {
                ;
            } else {
                throw new UnsupportedSyntax(body);
            }
        }
    }

    public DFTypeSpace extend(List<ImportDeclaration> imports) {
        // Make a copy as we're polluting the oririnal TypeSpace.
        DFTypeSpace typeSpace = new DFTypeSpace(this);
	for (ImportDeclaration importDecl : imports) {
            // XXX support static import
            assert(!importDecl.isStatic());
            if (importDecl.isOnDemand()) {
                // XXX TODO
            } else {
                Name name = importDecl.getName();
                assert(name.isQualifiedName());
                DFClassSpace klass = _root.lookupClass(name);
                if (klass == null) {
                    Utils.logit("Fail: could not import: "+name);
                } else {
                    QualifiedName qname = (QualifiedName)name;
                    typeSpace.addClass(qname.getName(), klass);
                }
            }
        }
        return typeSpace;
    }

    // dump: for debugging.
    public void dump() {
	dump(System.err, "");
    }
    public void dump(PrintStream out, String indent) {
	out.println(indent+_name+" {");
	String i2 = indent + "  ";
	for (DFClassSpace klass : _id2klass.values()) {
	    out.println(i2+"defined: "+klass);
	}
	for (DFTypeSpace space : _children) {
	    space.dump(out, i2);
	}
	out.println(indent+"}");
    }
}
