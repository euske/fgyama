//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


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
        _space = space;
        _next = next;
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
        return this.lookupKlass(name.getFullyQualifiedName());
    }

    public DFKlass lookupKlass(String name)
        throws TypeNotFound {
        name = name.replace('$', '.');
        DFTypeFinder finder = this;
        while (finder != null) {
            DFKlass klass = finder._space.getKlass(name);
            if (klass != null) return klass;
            finder = finder._next;
        }
        throw new TypeNotFound(name, this);
    }

    @SuppressWarnings("unchecked")
    public DFType resolve(Type type)
        throws TypeNotFound {
        if (type instanceof SimpleType) {
            return ((DFKlass)this.resolve1(type)).getDefaultKlass();
        } else {
            return this.resolve1(type);
        }
    }
    @SuppressWarnings("unchecked")
    private DFType resolve1(Type type)
        throws TypeNotFound {
        if (type instanceof SimpleType) {
            SimpleType stype = (SimpleType)type;
            return this.lookupKlass(stype.getName());
        } else if (type instanceof PrimitiveType) {
            PrimitiveType ptype = (PrimitiveType)type;
            return DFBasicType.getType(ptype.getPrimitiveTypeCode());
        } else if (type instanceof ArrayType) {
            ArrayType atype = (ArrayType)type;
            DFType elemType = this.resolve(atype.getElementType());
            int ndims = atype.getDimensions();
            return DFArrayType.getType(elemType, ndims);
        } else if (type instanceof ParameterizedType) {
            ParameterizedType ptype = (ParameterizedType)type;
            DFType genericType = this.resolve1(ptype.getType());
            assert genericType instanceof DFKlass;
            return this.getParameterized((DFKlass)genericType, ptype.typeArguments());
        } else if (type instanceof QualifiedType) {
            QualifiedType qtype = (QualifiedType)type;
            DFKlass klass = (DFKlass)this.resolve(qtype.getQualifier());
            DFKlass innerKlass = klass.getKlass(qtype.getName());
            if (innerKlass == null) {
                throw new TypeNotFound(qtype.toString(), this);
            }
            return innerKlass;
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
            return DFArrayType.getType(elemType, ndims);
        } else if (type instanceof org.apache.bcel.generic.ObjectType) {
            org.apache.bcel.generic.ObjectType otype =
                (org.apache.bcel.generic.ObjectType)type;
            String className = otype.getClassName();
            return this.lookupKlass(className).getDefaultKlass();
        } else {
            // ???
            throw new TypeNotFound(type.toString());
        }
    }

    public DFType resolveSafe(Type type) {
        try {
            return this.resolve(type);
        } catch (TypeNotFound e) {
            e.setAst(type);
            Logger.error("DFTypeFinder.resolveSafe: TypeNotFound", e.name);
            return DFUnknownType.UNKNOWN;
        }
    }

    public DFType resolveSafe(org.apache.bcel.generic.Type type) {
        try {
            return this.resolve(type);
        } catch (TypeNotFound e) {
            Logger.error("DFTypeFinder.resolveSafe: TypeNotFound", e.name);
            return DFUnknownType.UNKNOWN;
        }
    }

    public DFKlass getParameterized(DFKlass klass, List<Type> typeArgs)
        throws TypeNotFound {
        if (typeArgs == null || typeArgs.isEmpty()) return klass;
        DFKlass[] paramTypes = new DFKlass[typeArgs.size()];
        for (int i = 0; i < typeArgs.size(); i++) {
            paramTypes[i] = this.resolve(typeArgs.get(i)).toKlass();
        }
        return klass.getConcreteKlass(paramTypes);
    }

    // dump: for debugging.
    public void dump() {
        dump(System.err);
    }
    public void dump(PrintStream out) {
        DFTypeFinder finder = this;
        while (finder != null) {
            finder._space.dump(out, "  ");
            finder = finder._next;
            out.println();
        }
    }
}
