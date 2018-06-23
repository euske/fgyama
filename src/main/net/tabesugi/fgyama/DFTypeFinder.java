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
        throws EntityNotFound {
        return this.lookupClass(name.getFullyQualifiedName());
    }

    public DFClassSpace lookupClass(String name)
        throws EntityNotFound {
        try {
            return _space.lookupClass(name);
        } catch (EntityNotFound e) {
            if (_next != null) {
                return _next.lookupClass(name);
            } else {
                throw new EntityNotFound(name);
            }
        }
    }

    public DFClassSpace resolveClass(Type type)
        throws EntityNotFound {
        try {
            return _space.resolveClass(type);
        } catch (EntityNotFound e) {
            if (_next != null) {
                return _next.resolveClass(type);
            } else {
                throw e;
            }
        }
    }

    public DFClassSpace resolveClass(DFType type)
        throws EntityNotFound {
        try {
            return _space.resolveClass(type);
        } catch (EntityNotFound e) {
            if (_next != null) {
                return _next.resolveClass(type);
            } else {
                throw e;
            }
        }
    }

    public DFType resolve(Type type)
        throws EntityNotFound {
        try {
            return _space.resolve(type);
        } catch (EntityNotFound e) {
            if (_next != null) {
                return _next.resolve(type);
            } else {
                throw e;
            }
        }
    }

    public DFType resolve(org.apache.bcel.generic.Type type)
        throws EntityNotFound {
        try {
            return _space.resolve(type);
        } catch (EntityNotFound e) {
            if (_next != null) {
                return _next.resolve(type);
            } else {
                throw e;
            }
        }
    }

    @SuppressWarnings("unchecked")
    public DFType[] resolveList(MethodDeclaration decl)
        throws EntityNotFound {
        try {
            return _space.resolveList(decl);
        } catch (EntityNotFound e) {
            if (_next != null) {
                return _next.resolveList(decl);
            } else {
                throw e;
            }
        }
    }
}
