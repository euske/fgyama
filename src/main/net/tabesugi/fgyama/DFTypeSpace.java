//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.apache.bcel.*;
import org.apache.bcel.classfile.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;
import org.w3c.dom.*;


//  DFTypeSpace
//
public class DFTypeSpace {

    private DFTypeSpace _root;
    private String _name;
    private DFTypeSpace _parent;

    private List<DFTypeSpace> _children =
	new ArrayList<DFTypeSpace>();
    private Map<String, DFTypeSpace> _id2space =
	new HashMap<String, DFTypeSpace>();
    private Map<String, DFClassSpace> _id2klass =
	new HashMap<String, DFClassSpace>();

    public DFTypeSpace() {
        _root = this;
	_name = ".";
        _parent = null;
    }

    public DFTypeSpace(DFTypeSpace parent, String name) {
        _root = parent._root;
	_name = name;
	_parent = parent;
    }

    public DFTypeSpace(DFTypeSpace space) {
        _root = space._root;
        _name = space._name;
        _parent = space._parent;
        _children = new ArrayList<DFTypeSpace>(space._children);
        _id2space = new HashMap<String, DFTypeSpace>(space._id2space);
        _id2klass = new HashMap<String, DFClassSpace>(space._id2klass);
    }

    @Override
    public String toString() {
	return ("<DFTypeSpace("+this.getFullName()+")>");
    }

    public String getName() {
        return _name;
    }
    public String getFullName() {
        if (_parent == null) {
            return _name;
        } else if (_parent == _root) {
            return "."+_name;
        } else {
            return _parent.getFullName()+"."+_name;
        }
    }

    public DFTypeSpace addAnonChild() {
        String id = "anon"+_children.size();
        return this.addChild(id);
    }
    private DFTypeSpace addChild(String id) {
        Utils.logit("DFTypeSpace.addChild: "+this+": "+id);
        DFTypeSpace space = new DFTypeSpace(this, id);
        _children.add(space);
        _id2space.put(id, space);
        return space;
    }

    public DFTypeSpace lookupSpace(PackageDeclaration pkgDecl) {
        if (pkgDecl == null) {
            return this;
        } else {
            return _root.lookupSpace(pkgDecl.getName());
        }
    }

    public DFTypeSpace lookupSpace(Name name) {
        if (name.isQualifiedName()) {
	    return this.lookupSpace((QualifiedName)name);
        } else {
            return this.lookupSpace((SimpleName)name);
        }
    }
    public DFTypeSpace lookupSpace(QualifiedName qname) {
        DFTypeSpace parent = _root.lookupSpace(qname.getQualifier());
        return parent.lookupSpace(qname.getName());
    }
    public DFTypeSpace lookupSpace(SimpleName sname) {
        return this.lookupSpace(sname.getIdentifier());
    }

    public DFTypeSpace lookupSpace(String id) {
        int i = id.lastIndexOf('.');
        if (0 <= i) {
            DFTypeSpace parent = _root.lookupSpace(id.substring(0, i));
            return parent.lookupSpace(id.substring(i+1));
        } else {
            DFTypeSpace space = _id2space.get(id);
            if (space == null) {
                space = this.addChild(id);
            }
            return space;
        }
    }

    private DFClassSpace addClass(Name name) {
        if (name.isQualifiedName()) {
	    return this.addClass((QualifiedName)name);
        } else {
            return this.addClass((SimpleName)name);
        }
    }
    private DFClassSpace addClass(QualifiedName qname) {
        DFTypeSpace parent = _root.lookupSpace(qname.getQualifier());
        return parent.addClass(qname.getName());
    }
    private DFClassSpace addClass(SimpleName sname) {
        return this.addClass(sname.getIdentifier());
    }

    private DFClassSpace addClass(String id) {
        assert(id.indexOf('.') < 0);
        DFTypeSpace child = this.addChild(id);
	DFClassSpace klass = new DFClassSpace(this, child, id);
        return this.addClass(id, klass);
    }
    public DFClassSpace addClass(String id, DFClassSpace klass) {
        Utils.logit("DFTypeSpace.addClass: "+this+": "+id+": "+klass);
        assert(id.indexOf('.') < 0);
        //assert(!_id2klass.containsKey(id));
	_id2klass.put(id, klass);
	return klass;
    }

    private DFClassSpace getDefaultClass(DFType type) {
        if (type == null) {
            return this.getDefaultClass("unknown");
        } else {
            return this.getDefaultClass(type.getName());
        }
    }
    private DFClassSpace getDefaultClass(Name name) {
        if (name.isQualifiedName()) {
            return this.getDefaultClass((QualifiedName)name);
        } else {
            return this.getDefaultClass((SimpleName)name);
        }
    }
    private DFClassSpace getDefaultClass(QualifiedName qname) {
        DFTypeSpace space = _root.lookupSpace(qname.getQualifier());
        return space.getDefaultClass(qname.getName());
    }
    private DFClassSpace getDefaultClass(SimpleName sname) {
        return this.getDefaultClass(sname.getIdentifier());
    }
    private DFClassSpace getDefaultClass(String id) {
        int i = id.lastIndexOf('.');
        if (0 <= i) {
            DFTypeSpace space = _root.lookupSpace(id.substring(0, i));
            return space.getDefaultClass(id.substring(i+1));
        } else {
            try {
                return this.lookupClass(id);
            } catch (EntityNotFound e) {
                return this.addClass(id);
            }
        }
    }

    public DFClassSpace lookupClass(Name name)
        throws EntityNotFound {
        if (name.isQualifiedName()) {
            return this.lookupClass((QualifiedName)name);
        } else {
            return this.lookupClass((SimpleName)name);
        }
    }
    public DFClassSpace lookupClass(QualifiedName qname)
        throws EntityNotFound {
        DFTypeSpace space = _root.lookupSpace(qname.getQualifier());
        return space.lookupClass(qname.getName());
    }
    public DFClassSpace lookupClass(SimpleName sname)
        throws EntityNotFound {
        return this.lookupClass(sname.getIdentifier());
    }

    public DFClassSpace lookupClass(String id)
        throws EntityNotFound {
        int i = id.lastIndexOf('.');
        if (0 <= i) {
            DFTypeSpace space = _root.lookupSpace(id.substring(0, i));
            return space.lookupClass(id.substring(i+1));
        } else {
            DFClassSpace klass = _id2klass.get(id);
            if (klass == null) {
                throw new EntityNotFound(id);
            }
            return klass;
        }
    }

    public DFClassSpace resolveClass(DFType type) {
        if (type instanceof DFClassType) {
            return ((DFClassType)type).getKlass();
        }
        return _root.getDefaultClass(type);
    }
    public DFClassSpace resolveClass(Type type) {
        return resolveClass(resolve(type));
    }

    @SuppressWarnings("unchecked")
    public DFType resolve(Type type) {
	if (type instanceof PrimitiveType) {
            PrimitiveType ptype = (PrimitiveType)type;
            return new DFBasicType(ptype.getPrimitiveTypeCode());
	} else if (type instanceof SimpleType) {
            SimpleType stype = (SimpleType)type;
            DFClassSpace klass = _root.getDefaultClass(stype.getName());
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

    public DFType resolve(org.apache.bcel.generic.Type type) {
        if (type.equals(org.apache.bcel.generic.BasicType.BOOLEAN)) {
            return DFType.BOOLEAN;
        } else if (type.equals(org.apache.bcel.generic.BasicType.BYTE)) {
            return DFType.BYTE;
        } else if (type.equals(org.apache.bcel.generic.BasicType.CHAR)) {
            return DFType.CHAR;
        } else if (type.equals(org.apache.bcel.generic.BasicType.DOUBLE)) {
            return DFType.DOUBLE;
        } else if (type.equals(org.apache.bcel.generic.BasicType.FLOAT)) {
            return DFType.FLOAT;
        } else if (type.equals(org.apache.bcel.generic.BasicType.INT)) {
            return DFType.INT;
        } else if (type.equals(org.apache.bcel.generic.BasicType.LONG)) {
            return DFType.LONG;
        } else if (type.equals(org.apache.bcel.generic.BasicType.SHORT)) {
            return DFType.SHORT;
        } else if (type.equals(org.apache.bcel.generic.BasicType.VOID)) {
            return DFType.VOID;
        } else if (type instanceof org.apache.bcel.generic.ArrayType) {
            org.apache.bcel.generic.ArrayType atype =
                (org.apache.bcel.generic.ArrayType)type;
            DFType elemType = this.resolve(atype.getElementType());
            int ndims = atype.getDimensions();
            return new DFArrayType(elemType, ndims);
        } else if (type instanceof org.apache.bcel.generic.ObjectType) {
            org.apache.bcel.generic.ObjectType otype =
		(org.apache.bcel.generic.ObjectType)type;
            String className = otype.getClassName();
            DFClassSpace klass = _root.getDefaultClass(className);
            return new DFClassType(klass);
        } else {
	    return null;
	}
    }

    public void loadRootClass(JavaClass jklass) {
        assert(this == _root);
	assert(jklass.getPackageName().equals("java.lang"));
        this.loadClass(jklass);
    }

    private DFClassSpace loadClass(JavaClass jklass) {
        assert(this == _root);
	String id = jklass.getClassName();
        int i = id.lastIndexOf('.');
        assert(0 <= i);
        DFTypeSpace space = _root.lookupSpace(id.substring(0, i));
	DFClassSpace klass = space.addClass(id.substring(i+1));
        klass.build(this, jklass);
	return klass;
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
        //Utils.logit("DFTypeSpace.build: "+this+": "+typeDecl.getName());
        DFClassSpace klass = this.addClass(typeDecl.getName().getIdentifier());
        DFTypeSpace child = klass.getChildSpace();
        for (BodyDeclaration body :
                 (List<BodyDeclaration>) typeDecl.bodyDeclarations()) {
            child.build(body);
        }
    }

    public void build(BodyDeclaration body)
	throws UnsupportedSyntax {
        if (body instanceof TypeDeclaration) {
            this.build((TypeDeclaration)body);
        } else if (body instanceof FieldDeclaration) {
            ;
        } else if (body instanceof MethodDeclaration) {
            ;
        } else if (body instanceof Initializer) {
	    ;
        } else {
            throw new UnsupportedSyntax(body);
        }
    }

    public void importNames(ImportDeclaration importDecl)
        throws EntityNotFound {
        // XXX support static import
        assert(!importDecl.isStatic());
        Name name = importDecl.getName();
        if (importDecl.isOnDemand()) {
            DFTypeSpace typeSpace = _root.lookupSpace(name);
            for (String id : typeSpace._id2klass.keySet()) {
                DFTypeSpace space = typeSpace._id2space.get(id);
                _id2space.put(id, space);
                DFClassSpace klass = typeSpace._id2klass.get(id);
                _id2klass.put(id, klass);
            }
        } else {
            assert(name.isQualifiedName());
            DFClassSpace klass = _root.lookupClass(name);
            QualifiedName qname = (QualifiedName)name;
            this.addClass(qname.getName().getIdentifier(), klass);
        }
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
