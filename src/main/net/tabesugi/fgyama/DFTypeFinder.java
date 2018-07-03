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
        _space = space;
    }

    public DFTypeFinder(DFTypeFinder next, DFTypeSpace space) {
        _next = next;
        _space = space;
    }

    @Override
    public String toString() {
        return ("<DFTypeFinder: "+_space+" "+_next+">");
    }

    public DFClassSpace lookupClass(Name name)
        throws TypeNotFound {
        return this.lookupClass(name.getFullyQualifiedName());
    }

    public DFClassSpace lookupClass(String name)
        throws TypeNotFound {
        DFClassSpace klass;
        //Logger.info("lookupClass: "+_space+": "+name);
        name = name.replace('$', '.');
        try {
            klass = _space.getClass(name);
        } catch (TypeNotFound e) {
            if (_next != null) {
                klass = _next.lookupClass(name);
            } else {
                throw new TypeNotFound(name);
            }
        }
        klass.load(this);
        return klass;
    }

    public DFParamType lookupParamType(Name name) {
        return this.lookupParamType(name.getFullyQualifiedName());
    }

    public DFParamType lookupParamType(String name) {
        DFParamType pt = _space.getParamType(name);
        if (pt != null) return pt;
        if (_next != null) {
            return _next.lookupParamType(name);
        } else {
            return null;
        }
    }

    public DFClassSpace resolveClass(Type type)
        throws TypeNotFound {
        return this.resolveClass(resolve(type));
    }

    public DFClassSpace resolveClass(DFType type)
        throws TypeNotFound {
        if (type == null) {
            // treat unknown class as Object.
            return DFRootTypeSpace.OBJECT_CLASS;
        } else if (type instanceof DFArrayType) {
            return DFRootTypeSpace.ARRAY_CLASS;
        } else if (type instanceof DFClassType) {
            return ((DFClassType)type).getKlass();
        } else {
            throw new TypeNotFound(type.getName());
        }
    }

    @SuppressWarnings("unchecked")
    public DFType resolve(Type type)
        throws TypeNotFound {
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
            DFParamType pt = this.lookupParamType(stype.getName());
            if (pt != null) return pt;
            DFClassSpace klass = this.lookupClass(stype.getName());
            return new DFClassType(klass);
        } else if (type instanceof ParameterizedType) {
            ParameterizedType ptype = (ParameterizedType)type;
            List<Type> args = (List<Type>) ptype.typeArguments();
            DFType genericType = this.resolve(ptype.getType());
            assert(genericType instanceof DFClassType);
            DFClassSpace genericKlass = ((DFClassType)genericType).getKlass();
            DFType[] argTypes = new DFType[args.size()];
            for (int i = 0; i < args.size(); i++) {
                argTypes[i] = this.resolve(args.get(i));
            }
            DFParamClass paramKlass = new DFParamClass(genericKlass, argTypes);
            return new DFClassType(paramKlass);
        } else {
            // ???
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
            DFClassSpace klass = this.lookupClass(className);
            return new DFClassType(klass);
        } else {
            // ???
            throw new TypeNotFound(type.toString());
        }
    }

    @SuppressWarnings("unchecked")
    public DFType[] resolveList(MethodDeclaration decl)
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
}
