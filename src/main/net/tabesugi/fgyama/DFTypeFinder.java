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

    private DFTypeFinder _next = null;
    private DFTypeSpace _space;

    public DFTypeFinder(DFTypeSpace space) {
        assert space != null;
        _space = space;
    }

    public DFTypeFinder(DFTypeFinder next, DFTypeSpace space) {
        assert next != null;
        assert space != null;
        _next = next;
        _space = space;
    }

    @Override
    public String toString() {
        return ("<DFTypeFinder: "+_space+" "+_next+">");
    }

    public DFTypeFinder extend(DFKlass klass) {
        DFTypeFinder finder = this;
        if (klass instanceof DFParamKlass) {
            DFKlass genericKlass = ((DFParamKlass)klass).getGeneric();
            finder = finder.extend(genericKlass);
        } else {
            DFKlass baseKlass = klass.getBaseKlass();
            if (baseKlass != null) {
                finder = finder.extend(baseKlass);
            }
            DFKlass[] baseIfaces = klass.getBaseIfaces();
            if (baseIfaces != null) {
                for (DFKlass iface : baseIfaces) {
                    finder = finder.extend(iface);
                }
            }
            DFTypeSpace klassSpace = klass.getKlassSpace();
            if (klassSpace != null) {
                finder = new DFTypeFinder(finder, klassSpace);
            }
        }
        return finder;
    }

    public DFKlass lookupKlass(Name name)
        throws TypeNotFound {
        return this.lookupKlass(name.getFullyQualifiedName());
    }

    public DFKlass lookupKlass(String name)
        throws TypeNotFound {
        DFKlass klass;
        try {
            klass = _space.getKlass(name.replace('$', '.'));
        } catch (TypeNotFound e) {
            if (_next != null) {
                return _next.lookupKlass(name);
            } else {
                throw new TypeNotFound(name);
            }
        }
        klass.load();
        return klass;
    }

    public DFKlass resolveKlass(Type type)
        throws TypeNotFound {
        return this.resolveKlass(resolve(type));
    }

    public DFKlass resolveKlass(DFType type)
        throws TypeNotFound {
        if (type == null) {
            // treat unknown class as Object.
            return DFBuiltinTypes.getObjectKlass();
        } else if (type instanceof DFArrayType) {
            return DFBuiltinTypes.getArrayKlass();
        } else if (type instanceof DFKlass) {
            return (DFKlass)type;
        } else {
            throw new TypeNotFound(type.getTypeName());
        }
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
            return this.lookupKlass(stype.getName());
        } else if (type instanceof ParameterizedType) {
            ParameterizedType ptype = (ParameterizedType)type;
            List<Type> args = (List<Type>) ptype.typeArguments();
            DFType genericType = this.resolve(ptype.getType());
            assert genericType instanceof DFKlass;
            DFKlass genericKlass = (DFKlass)genericType;
            DFType[] mapTypes = new DFType[args.size()];
            for (int i = 0; i < args.size(); i++) {
                mapTypes[i] = this.resolve(args.get(i));
            }
            return genericKlass.getParamKlass(mapTypes);
        } else if (type instanceof QualifiedType) {
            QualifiedType qtype = (QualifiedType)type;
            DFKlass klass = (DFKlass)this.resolve(qtype.getQualifier());
            DFTypeSpace space = klass.getKlassSpace();
            return space.getKlass(qtype.getName());
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
            if (bound != null) {
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
