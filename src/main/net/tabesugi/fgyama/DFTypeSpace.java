//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.apache.bcel.*;
import org.apache.bcel.classfile.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;
import org.w3c.dom.*;


//  DFTypeSpace
//
public class DFTypeSpace {

    private DFTypeSpace _root;
    private String _name = null;

    private List<DFTypeSpace> _children =
	new ArrayList<DFTypeSpace>();
    private Map<String, DFTypeSpace> _id2space =
	new HashMap<String, DFTypeSpace>();
    private Map<String, DFClassSpace> _id2klass =
	new HashMap<String, DFClassSpace>();

    public static DFClassSpace OBJECT_CLASS = null;
    public static DFClassSpace ARRAY_CLASS = null;
    public static DFClassSpace STRING_CLASS = null;

    public DFTypeSpace() {
        _root = this;
    }

    public DFTypeSpace(DFTypeSpace parent, String name) {
        _root = parent._root;
	if (parent == _root) {
	    _name = name;
	} else {
	    _name = parent._name + "." + name;
	}
    }

    @Override
    public String toString() {
	return ("<DFTypeSpace("+_name+")>");
    }

    public String getFullName() {
	return _name;
    }

    public String getAnonName() {
        return "anon"+_children.size(); // assign unique id.
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

    public DFTypeSpace lookupSpace(String id) {
        int i = id.indexOf('.');
        if (0 <= i) {
            DFTypeSpace space = this.lookupSpace(id.substring(0, i));
            return space.lookupSpace(id.substring(i+1));
        } else {
            DFTypeSpace space = _id2space.get(id);
            if (space == null) {
                space = new DFTypeSpace(this, id);
                _children.add(space);
                _id2space.put(id, space);
                //Utils.logit("DFTypeSpace.addChild: "+this+": "+id);
            }
            return space;
        }
    }

    public DFClassSpace createClass(String id) {
        int i = id.indexOf('.');
        if (0 <= i) {
            DFTypeSpace space = this.lookupSpace(id.substring(0, i));
            return space.createClass(id.substring(i+1));
        } else {
            DFTypeSpace child = this.lookupSpace(id);
            DFClassSpace klass = new DFClassSpace(this, child, id);
            //Utils.logit("DFTypeSpace.createClass: "+this+": "+klass);
            return this.addClass(klass);
        }
    }

    public DFClassSpace addClass(DFClassSpace klass) {
        String id = klass.getBaseName();
        assert(id.indexOf('.') < 0);
        //assert(!_id2klass.containsKey(id));
	_id2klass.put(id, klass);
        //Utils.logit("DFTypeSpace.addClass: "+this+": "+id);
	return klass;
    }

    public void addClasses(DFTypeSpace typeSpace) {
        for (DFClassSpace klass : typeSpace._id2klass.values()) {
            this.addClass(klass);
        }
    }

    public DFClassSpace lookupClass(Name name)
        throws EntityNotFound {
        return this.lookupClass(name.getFullyQualifiedName());
    }
    public DFClassSpace lookupClass(String id)
        throws EntityNotFound {
        //Utils.logit("DFTypeSpace.lookupClass: "+this+": "+id);
        DFTypeSpace space = this;
        while (space != null) {
            int i = id.indexOf('.');
            if (i < 0) {
                DFClassSpace klass = space._id2klass.get(id);
                if (klass == null) {
                    throw new EntityNotFound(id);
                }
                klass.load(_root);
                return klass;
            }
            space = space._id2space.get(id.substring(0, i));
            id = id.substring(i+1);
        }
        throw new EntityNotFound(id);
    }

    public DFClassSpace resolveClass(Type type)
        throws EntityNotFound {
        return this.resolveClass(resolve(type));
    }

    public DFClassSpace resolveClass(DFType type)
        throws EntityNotFound {
	if (type == null) {
	    // treat unknown class as Object.
	    return DFTypeSpace.OBJECT_CLASS;
        } else if (type instanceof DFArrayType) {
            return DFTypeSpace.ARRAY_CLASS;
	} else if (type instanceof DFClassType) {
            return ((DFClassType)type).getKlass();
        } else {
            throw new EntityNotFound(type.getName());
        }
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
            this.build(((MethodDeclaration)body).getBody());
        } else if (body instanceof Initializer) {
	    ;
        } else {
            throw new UnsupportedSyntax(body);
        }
    }

    public void build(Block block)
	throws UnsupportedSyntax {

    }

    public void loadJarFile(String jarPath)
	throws IOException {
        assert(this == _root);
        Utils.logit("Loading: "+jarPath);
	JarFile jarfile = new JarFile(jarPath);
	try {
	    for (Enumeration<JarEntry> es = jarfile.entries(); es.hasMoreElements(); ) {
		JarEntry je = es.nextElement();
		String path = je.getName();
                if (path.endsWith(".class")) {
                    String name = path.substring(0, path.length()-6).replace('/', '.');
                    DFClassSpace klass = this.createClass(name);
                    klass.setJarPath(jarPath);
                }
	    }
	} finally {
	    jarfile.close();
	}
    }

    public void loadDefaultClasses()
	throws IOException, EntityNotFound {
        assert(this == _root);
        File homeDir = new File(System.getProperty("java.home"));
        File libDir = new File(homeDir, "lib");
        File rtFile = new File(libDir, "rt.jar");
        this.loadJarFile(rtFile.getAbsolutePath());
        OBJECT_CLASS = this.lookupClass("java.lang.Object");
        ARRAY_CLASS = this.lookupClass("java.lang.Object");
        STRING_CLASS = this.lookupClass("java.lang.String");
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
