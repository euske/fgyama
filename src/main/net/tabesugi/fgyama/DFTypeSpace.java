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
    private DFTypeSpace _parent = null;
    private DFTypeSpace _next = null;

    private List<DFTypeSpace> _children =
	new ArrayList<DFTypeSpace>();
    private Map<String, DFTypeSpace> _id2space =
	new HashMap<String, DFTypeSpace>();
    private Map<String, DFClassSpace> _id2klass =
	new HashMap<String, DFClassSpace>();

    public DFTypeSpace(String name) {
        _root = this;
	_name = name;
    }

    public DFTypeSpace(String name, DFTypeSpace next) {
        _root = this;
	_name = name;
        _next = next;
    }

    public DFTypeSpace(DFTypeSpace parent, String name) {
        _root = parent._root;
	_name = name;
	_parent = parent;
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
        return this.lookupSpace(name.getFullyQualifiedName());
    }
    private DFTypeSpace lookupSpace(String id) {
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

    public DFClassSpace lookupClass(Name name)
        throws EntityNotFound {
        return this.lookupClass(name.getFullyQualifiedName());
    }
    private DFClassSpace lookupClass(String id)
        throws EntityNotFound {
        try {
            int i = id.lastIndexOf('.');
            if (0 <= i) {
                DFTypeSpace space = _root.lookupSpace(id.substring(0, i));
                return space.lookupClass(id.substring(i+1));
            } else {
                Utils.logit("lookupClass: "+this+": "+id);
                DFClassSpace klass = _id2klass.get(id);
                if (klass == null) {
                    String fullName = this.getFullName().substring(1)+"."+id;
                    JavaClass jklass = DFRepository.loadJavaClass(fullName);
                    if (jklass == null) {
                        throw new EntityNotFound(fullName);
                    }
                    klass = _root.loadClass(jklass);
                }
                return klass;
            }
        } catch (EntityNotFound e) {
            if (_next != null) {
                return _next.lookupClass(id);
            } else {
                throw e;
            }
        }
    }

    public DFClassSpace resolveClass(Type type)
        throws EntityNotFound {
        return this.resolveClass(resolve(type));
    }

    public DFClassSpace resolveClass(DFType type)
        throws EntityNotFound {
        if (type instanceof DFClassType) {
            return ((DFClassType)type).getKlass();
        }
        return this.lookupClass(type.getName());
    }

    @SuppressWarnings("unchecked")
    public DFType resolve(Type type)
        throws EntityNotFound {
        Utils.logit("resolve:"+this+":"+type);
	if (type instanceof PrimitiveType) {
            PrimitiveType ptype = (PrimitiveType)type;
            return new DFBasicType(ptype.getPrimitiveTypeCode());
	} else if (type instanceof ArrayType) {
            ArrayType atype = (ArrayType)type;
	    DFType elemType = this.resolve(atype.getElementType());
	    int ndims = atype.getDimensions();
	    return new DFArrayType(elemType, ndims);
	} else if (type instanceof SimpleType) {
            SimpleType stype = (SimpleType)type;
            DFClassSpace klass = this.lookupClass(stype.getName());
            return new DFClassType(klass);
	} else if (type instanceof ParameterizedType) {
            ParameterizedType ptype = (ParameterizedType)type;
            List<Type> args = (List<Type>) ptype.typeArguments();
            DFType baseType = this.resolve(ptype.getType());
            DFType[] argTypes = new DFType[args.size()];
            for (int i = 0; i < args.size(); i++) {
                argTypes[i] = this.resolve(args.get(i));
            }
            assert(baseType instanceof DFClassType);
            // XXX make DFCompoundType
            DFClassSpace baseKlass = ((DFClassType)baseType).getKlass();
            return new DFClassType(baseKlass, argTypes);
        } else {
            // ???
            throw new EntityNotFound(type.toString());
        }
    }

    public DFType resolve(org.apache.bcel.generic.Type type)
        throws EntityNotFound {
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
            DFClassSpace klass = this.lookupClass(className);
            return new DFClassType(klass);
        } else {
            // ???
            throw new EntityNotFound(type.toString());
	}
    }

    @SuppressWarnings("unchecked")
    public DFType[] resolveList(MethodDeclaration decl)
        throws EntityNotFound {
        List<DFType> types = new ArrayList<DFType>();
        for (SingleVariableDeclaration varDecl :
                 (List<SingleVariableDeclaration>) decl.parameters()) {
            types.add(this.resolve(varDecl.getType()));
        }
        DFType[] argTypes = new DFType[types.size()];
        types.toArray(argTypes);
        return argTypes;
    }

    public DFClassSpace loadClass(JavaClass jklass)
        throws EntityNotFound {
        assert(this == _root);
	String id = jklass.getClassName();
        int i = id.lastIndexOf('.');
        assert(0 <= i);
        DFTypeSpace space = this.lookupSpace(id.substring(0, i));
	DFClassSpace klass = space.addClass(id.substring(i+1));
        klass.load(this, jklass);
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

    public void importClass(ImportDeclaration importDecl)
        throws EntityNotFound {
        // XXX support static import
        assert(!importDecl.isStatic());
        Name name = importDecl.getName();
        if (importDecl.isOnDemand()) {
            DFTypeSpace typeSpace = _root.lookupSpace(name);
            this.importClasses(typeSpace);
        } else {
            assert(name.isQualifiedName());
            DFClassSpace klass = _root.lookupClass(name);
            QualifiedName qname = (QualifiedName)name;
            this.addClass(qname.getName().getIdentifier(), klass);
        }
    }

    private void importClasses(DFTypeSpace typeSpace) {
        for (String id : typeSpace._id2klass.keySet()) {
            DFTypeSpace space = typeSpace._id2space.get(id);
            _id2space.put(id, space);
            DFClassSpace klass = typeSpace._id2klass.get(id);
            _id2klass.put(id, klass);
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
