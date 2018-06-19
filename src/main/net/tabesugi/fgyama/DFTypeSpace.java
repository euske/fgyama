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
    private DFTypeSpace _parent = null;
    private String _name = null;

    private List<DFTypeSpace> _children =
	new ArrayList<DFTypeSpace>();
    private Map<String, DFTypeSpace> _id2space =
	new HashMap<String, DFTypeSpace>();
    private Map<String, DFClassSpace> _id2klass =
	new HashMap<String, DFClassSpace>();

    public DFTypeSpace() {
        _root = this;
    }

    public DFTypeSpace(DFTypeSpace root) {
        _root = root;
        this.importClasses(root.lookupSpace("java.lang"));
    }

    public DFTypeSpace(DFTypeSpace parent, String name) {
        _root = parent._root;
	_parent = parent;
	_name = name;
    }

    @Override
    public String toString() {
	return ("<DFTypeSpace("+this.getFullName()+")>");
    }

    public String getBaseName() {
        return _name;
    }
    public String getFullName() {
        if (_root == this) {
            return "root";
        } else if (_parent == null) {
            return "copy";
        } else if (_parent == _root) {
            return _name;
        } else {
            return _parent.getFullName()+"."+_name;
        }
    }

    public DFTypeSpace addAnonSpace() {
        String id = "anon"+_children.size(); // assign unique id.
        return this.addChild(id);
    }

    public DFTypeSpace lookupSpace(PackageDeclaration pkgDecl) {
        if (pkgDecl == null) {
            return this;
        } else {
            return this.lookupSpace(pkgDecl.getName());
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

    private DFTypeSpace addChild(String id) {
        //Utils.logit("DFTypeSpace.addChild: "+this+": "+id);
        assert(id.indexOf('.') < 0);
        assert(!_id2space.containsKey(id));
        DFTypeSpace space = new DFTypeSpace(this, id);
        _children.add(space);
        _id2space.put(id, space);
        return space;
    }

    private DFClassSpace createClass(String id) {
        assert(id.indexOf('.') < 0);
        assert(!_id2space.containsKey(id));
        DFTypeSpace child = this.addChild(id);
	DFClassSpace klass = new DFClassSpace(this, child, id);
        Utils.logit("DFTypeSpace.createClass: "+klass);
        return this.addClass(klass);
    }

    public DFClassSpace addClass(DFClassSpace klass) {
        String id = klass.getBaseName();
        assert(id.indexOf('.') < 0);
        assert(!_id2klass.containsKey(id));
	_id2klass.put(id, klass);
        //Utils.logit("DFTypeSpace.addClass: "+this+": "+id);
	return klass;
    }

    public DFClassSpace lookupClass(Name name)
        throws EntityNotFound {
        return this.lookupClass(name.getFullyQualifiedName());
    }
    public DFClassSpace lookupClass(String id)
        throws EntityNotFound {
        //Utils.logit("DFTypeSpace.lookupClass: "+this+": "+id);
        int i = id.lastIndexOf('.');
        if (0 <= i) {
            DFTypeSpace space = _root.lookupSpace(id.substring(0, i));
            return space.lookupClass(id.substring(i+1));
        } else {
            DFClassSpace klass = _id2klass.get(id);
            if (klass == null) {
                String fullName = this.getFullName()+"."+id;
                JavaClass jklass = DFRepository.loadJavaClass(fullName);
                if (jklass == null) {
                    throw new EntityNotFound(fullName);
                }
                klass = _root.loadClass(jklass);
            }
            return klass;
        }
    }

    public DFClassSpace resolveClass(Type type)
        throws EntityNotFound {
        return this.resolveClass(resolve(type));
    }

    public DFClassSpace resolveClass(DFType type)
        throws EntityNotFound {
        assert(type != null);
        if (type instanceof DFClassType) {
            return ((DFClassType)type).getKlass();
        }
        return this.lookupClass(type.getName());
    }

    @SuppressWarnings("unchecked")
    public DFType resolve(Type type)
        throws EntityNotFound {
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
            return DFBasicType.BOOLEAN;
        } else if (type.equals(org.apache.bcel.generic.BasicType.BYTE)) {
            return DFBasicType.BYTE;
        } else if (type.equals(org.apache.bcel.generic.BasicType.CHAR)) {
            return DFBasicType.CHAR;
        } else if (type.equals(org.apache.bcel.generic.BasicType.DOUBLE)) {
            return DFBasicType.DOUBLE;
        } else if (type.equals(org.apache.bcel.generic.BasicType.FLOAT)) {
            return DFBasicType.FLOAT;
        } else if (type.equals(org.apache.bcel.generic.BasicType.INT)) {
            return DFBasicType.INT;
        } else if (type.equals(org.apache.bcel.generic.BasicType.LONG)) {
            return DFBasicType.LONG;
        } else if (type.equals(org.apache.bcel.generic.BasicType.SHORT)) {
            return DFBasicType.SHORT;
        } else if (type.equals(org.apache.bcel.generic.BasicType.VOID)) {
            return DFBasicType.VOID;
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
        return space.loadClass(id.substring(i+1), jklass);
    }
    private DFClassSpace loadClass(String id, JavaClass jklass)
        throws EntityNotFound {
        if (_id2klass.containsKey(id)) {
            return _id2klass.get(id);
        } else {
            DFClassSpace klass = this.createClass(id);
            klass.load(this, jklass);
            return klass;
        }
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
        DFClassSpace klass = this.createClass(typeDecl.getName().getIdentifier());
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
            Utils.logit("DFTypeSpace.importClass: "+klass);
            this.addClass(klass);
        }
    }

    public void importClasses(DFTypeSpace typeSpace) {
        for (String id : typeSpace._id2klass.keySet()) {
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
