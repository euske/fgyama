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
        this.toString(path);
        return ("<DFTypeFinder: "+Utils.join(path)+">");
    }
    private void toString(List<DFTypeSpace> path) {
        path.add(_space);
        if (_next != null) {
            _next.toString(path);
        }
    }

    public DFType lookupType(Name name)
        throws TypeNotFound {
        return this.lookupType(name.getFullyQualifiedName());
    }

    public DFType lookupType(String name)
        throws TypeNotFound {
        name = name.replace('$', '.');
        DFTypeFinder finder = this;
        while (finder != null) {
            DFType type = finder._space.getKlass(name);
            if (type != null) return type;
            finder = finder._next;
        }
        throw new TypeNotFound(name, this);
    }

    @SuppressWarnings("unchecked")
    public DFType resolve(Type type)
        throws InvalidSyntax, TypeNotFound {
        if (type instanceof PrimitiveType) {
            PrimitiveType ptype = (PrimitiveType)type;
            return DFBasicType.getType(ptype.getPrimitiveTypeCode());
        } else if (type instanceof ArrayType) {
            ArrayType atype = (ArrayType)type;
            DFType elemType = this.resolve(atype.getElementType());
            int ndims = atype.getDimensions();
            return DFArrayType.getType(elemType, ndims);
        } else if (type instanceof SimpleType) {
            SimpleType stype = (SimpleType)type;
            DFType klassType = this.lookupType(stype.getName());
            klassType.toKlass().load();
            return klassType;
        } else if (type instanceof ParameterizedType) {
            ParameterizedType ptype = (ParameterizedType)type;
            List<Type> args = (List<Type>) ptype.typeArguments();
            DFType genericType = this.resolve(ptype.getType());
            assert genericType instanceof DFKlass;
            DFKlass genericKlass = (DFKlass)genericType;
            DFKlass[] paramTypes = new DFKlass[args.size()];
            for (int i = 0; i < args.size(); i++) {
                paramTypes[i] = this.resolve(args.get(i)).toKlass();
            }
            DFKlass paramKlass = genericKlass.getConcreteKlass(paramTypes);
            paramKlass.load();
            return paramKlass;
        } else if (type instanceof QualifiedType) {
            QualifiedType qtype = (QualifiedType)type;
            DFKlass klass = (DFKlass)this.resolve(qtype.getQualifier());
            DFType innerType = klass.getKlass(qtype.getName());
            if (innerType == null) {
                throw new TypeNotFound(qtype.toString(), this);
            }
            DFKlass innerKlass = innerType.toKlass();
            innerKlass.load();
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
        throws InvalidSyntax, TypeNotFound {
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
            DFType klassType = this.lookupType(className);
            klassType.toKlass().load();
            return klassType;
        } else {
            // ???
            throw new TypeNotFound(type.toString());
        }
    }

    public DFType resolveSafe(Type type)
        throws InvalidSyntax {
        try {
            return this.resolve(type);
        } catch (TypeNotFound e) {
            e.setAst(type);
            Logger.error("DFTypeFinder.resolveSafe: TypeNotFound", e.name);
            return DFUnknownType.UNKNOWN;
        }
    }

    public DFType resolveSafe(org.apache.bcel.generic.Type type)
        throws InvalidSyntax {
        try {
            return this.resolve(type);
        } catch (TypeNotFound e) {
            Logger.error("DFTypeFinder.resolveSafe: TypeNotFound", e.name);
            return DFUnknownType.UNKNOWN;
        }
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
