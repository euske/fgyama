//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;
import org.w3c.dom.*;


//  DFTypeFinder
//
public class DFTypeFinder {

    private DFTypeSpace _space;
    private DFTypeFinder _next = null;

    public DFTypeFinder(DFTypeSpace space) {
        assert space != null;
        _space = space;
    }

    public DFTypeFinder(DFTypeSpace space, DFTypeFinder next) {
        assert space != null;
        assert next != null;
        _space = space;
        _next = next;
    }

    public DFTypeFinder extend(DFTypeSpace space) {
        return new DFTypeFinder(space, this);
    }

    @Override
    public String toString() {
        List<DFTypeSpace> path = new ArrayList<DFTypeSpace>();
        DFTypeFinder finder = this;
        while (finder != null) {
            path.add(finder._space);
            finder = finder._next;
        }
        return ("<DFTypeFinder: "+Utils.join(path)+">");
    }

    public DFKlass lookupKlass(Name name)
        throws TypeNotFound {
        DFTypeFinder finder = this;
        while (finder != null) {
            try {
                return finder._space.getKlass(name);
            } catch (TypeNotFound e) {
                finder = finder._next;
            }
        }
        throw new TypeNotFound(name.getFullyQualifiedName(), this);
    }

    public DFKlass lookupKlass(String name)
        throws TypeNotFound {
        name = name.replace('$', '.');
        DFTypeFinder finder = this;
        while (finder != null) {
            try {
                return finder._space.getKlass(name);
            } catch (TypeNotFound e) {
                finder = finder._next;
            }
        }
        throw new TypeNotFound(name, this);
    }

    @SuppressWarnings("unchecked")
    public DFType resolve(Type type)
        throws TypeNotFound {
        if (type instanceof PrimitiveType) {
            PrimitiveType ptype = (PrimitiveType)type;
            return DFBasicType.getType(ptype.getPrimitiveTypeCode());
        } else if (type instanceof ArrayType) {
            ArrayType atype = (ArrayType)type;
            DFType elemType = this.resolve(atype.getElementType());
            int ndims = atype.getDimensions();
            return new DFArrayType(elemType, ndims);
        } else if (type instanceof SimpleType) {
            SimpleType stype = (SimpleType)type;
            DFKlass klass = this.lookupKlass(stype.getName());
            klass.load();
            return klass;
        } else if (type instanceof ParameterizedType) {
            ParameterizedType ptype = (ParameterizedType)type;
            List<Type> args = (List<Type>) ptype.typeArguments();
            DFType genericType = this.resolve(ptype.getType());
            assert genericType instanceof DFKlass;
            DFKlass genericKlass = (DFKlass)genericType;
            DFType[] paramTypes = new DFType[args.size()];
            for (int i = 0; i < args.size(); i++) {
                paramTypes[i] = this.resolve(args.get(i));
            }
            DFKlass paramKlass = genericKlass.parameterize(paramTypes);
            paramKlass.load();
            return paramKlass;
        } else if (type instanceof QualifiedType) {
            QualifiedType qtype = (QualifiedType)type;
            DFKlass klass = (DFKlass)this.resolve(qtype.getQualifier());
            DFTypeSpace space = klass.getKlassSpace();
            DFKlass childKlass = space.getKlass(qtype.getName());
            childKlass.load();
            return childKlass;
        } else if (type instanceof UnionType) {
            // XXX only consider the first type.
            UnionType utype = (UnionType)type;
            for (Type type1 : (List<Type>)utype.types()) {
                return this.resolve(type1);
            }
            throw new TypeNotFound(type.toString());
        } else if (type instanceof IntersectionType) {
            // XXX only consider the first type.
            IntersectionType itype = (IntersectionType)type;
            for (Type type1 : (List<Type>)itype.types()) {
                return this.resolve(type1);
            }
            throw new TypeNotFound(type.toString());
        } else if (type instanceof WildcardType) {
            WildcardType wtype = (WildcardType)type;
            Type bound = wtype.getBound();
            if (wtype.isUpperBound() && bound != null) {
                return this.resolve(bound);
            } else {
                return DFBuiltinTypes.getObjectKlass();
            }
        } else {
            // not supported:
            //  NameQualifiedType
            throw new TypeNotFound(type.toString());
        }
    }

    public DFType resolve(org.apache.bcel.generic.Type type)
        throws TypeNotFound {
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
            return this.lookupKlass(className);
        } else {
            // ???
            throw new TypeNotFound(type.toString());
        }
    }

    @SuppressWarnings("unchecked")
    public DFType[] resolveArgs(MethodDeclaration decl)
        throws TypeNotFound {
        List<DFType> types = new ArrayList<DFType>();
        for (SingleVariableDeclaration varDecl :
                 (List<SingleVariableDeclaration>) decl.parameters()) {
            types.add(this.resolve(varDecl.getType()));
        }
        DFType[] argTypes = new DFType[types.size()];
        types.toArray(argTypes);
        return argTypes;
    }

    public DFTypeFinder resolveMapTypeSpace(
        DFTypeSpace mapTypeSpace, DFMapType[] mapTypes) {
        DFTypeFinder finder = new DFTypeFinder(mapTypeSpace, this);
        for (DFMapType mapType : mapTypes) {
            try {
                // This might cause TypeNotFound
                // for a recursive type.
                mapType.build(finder);
            } catch (TypeNotFound e) {
            }
            mapTypeSpace.addKlass(
                mapType.getTypeName(),
                mapType.getKlass());
        }
        return finder;
    }

    // dump: for debugging.
    public void dump() {
        dump(System.err, 0);
    }
    public void dump(PrintStream out, int index) {
        out.println("  "+index+": "+_space);
        if (_next != null) {
            _next.dump(out, index+1);
        }
    }
}
