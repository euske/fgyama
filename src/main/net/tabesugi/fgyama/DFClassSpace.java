//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFClassSpace
//
public class DFClassSpace extends DFVarSpace {

    private DFTypeSpace _typeSpace;
    private DFClassSpace _baseKlass;
    private DFClassSpace[] _baseIfaces;

    private List<DFMethod> _methods = new ArrayList<DFMethod>();

    public DFClassSpace(DFTypeSpace typeSpace) {
        super("unknown");
	_typeSpace = typeSpace;
    }

    public DFClassSpace(DFTypeSpace typeSpace, String id) {
        super(id);
	_typeSpace = typeSpace;
	this.addRef("#this", new DFClassType(this));
    }

    @Override
    public String toString() {
	return ("<DFClassSpace("+this.getName()+")>");
    }

    public DFClassSpace getBase() {
        return _baseKlass;
    }

    public String getName() {
	return _typeSpace.getName()+"/"+super.getName();
    }

    public int isBaseOf(DFClassSpace klass) {
        int dist = 0;
        while (klass != null) {
            if (klass == this) return dist;
            dist++;
            klass = klass._baseKlass;
        }
        return -1;
    }

    public DFVarRef lookupThis() {
        return this.lookupRef("#this");
    }

    protected DFVarRef lookupField(String id) {
        return this.lookupRef("."+id);
    }

    public DFVarRef lookupField(SimpleName name) {
        DFVarRef ref = this.lookupField(name.getIdentifier());
        if (ref != null) return ref;
        String id = Utils.resolveName(name);
        if (id != null) {
            return new DFVarRef(null, id, null);
        }
        return new DFVarRef(null, "."+name.getIdentifier(), null);
    }

    private DFMethod lookupMethod1(SimpleName name, DFType[] argTypes) {
        String id = name.getIdentifier();
        int bestDist = -1;
        DFMethod bestMethod = null;
        for (DFMethod method : _methods) {
            int dist = method.canAccept(id, argTypes);
            if (dist < 0) continue;
            if (bestDist < 0 || dist < bestDist) {
                bestDist = dist;
                bestMethod = method;
            }
        }
        return bestMethod;
    }

    public DFMethod[] lookupMethods(SimpleName name, DFType[] argTypes) {
        DFClassSpace klass = this;
        while (klass != null) {
            DFMethod method = klass.lookupMethod1(name, argTypes);
            if (method != null) {
                return method.getOverrides();
            }
            klass = klass._baseKlass;
        }
        // fallback...
        DFMethod fallback;
        String id = Utils.resolveName(name);
        if (id != null) {
            fallback = new DFMethod(null, id, false, null, null);
        } else {
            fallback = this.addMethod(name, false, null, null);
        }
        return new DFMethod[] { fallback };
    }

    public DFMethod lookupMethod(MethodDeclaration methodDecl) {
        DFType[] argTypes = getTypeList(methodDecl);
        return this.lookupMethod1(methodDecl.getName(), argTypes);
    }

    private DFVarRef addField(SimpleName name, DFType type) {
        DFVarRef ref = this.addRef("."+name.getIdentifier(), type);
        Utils.logit("DFClassSpace.addField: "+ref);
        return ref;
    }

    private void overrideMethod(DFMethod method1) {
        for (DFMethod method0 : _methods) {
            if (method0.equals(method1)) {
                method0.addOverride(method1);
                Utils.logit("DFClassSpace.overrideMethod: "+method0+" : "+method1);
                break;
            }
        }
        if (_baseKlass != null) {
            _baseKlass.overrideMethod(method1);
        }
    }

    private DFMethod addMethod(
        SimpleName name, boolean isStatic,
        DFType[] argTypes, DFType returnType) {
        String id = name.getIdentifier();
	DFMethod method = new DFMethod(this, id, isStatic, argTypes, returnType);
        Utils.logit("DFClassSpace.addMethod: "+method);
        _methods.add(method);
        if (_baseKlass != null) {
            _baseKlass.overrideMethod(method);
        }
        return method;
    }

    @SuppressWarnings("unchecked")
    private DFType[] getTypeList(MethodDeclaration decl) {
        List<DFType> types = new ArrayList<DFType>();
        for (SingleVariableDeclaration varDecl :
                 (List<SingleVariableDeclaration>) decl.parameters()) {
            types.add(_typeSpace.resolve(varDecl.getType()));
        }
        DFType[] argTypes = new DFType[types.size()];
        types.toArray(argTypes);
        return argTypes;
    }

    @SuppressWarnings("unchecked")
    private boolean isStatic(BodyDeclaration body) {
        for (IExtendedModifier imod :
                 (List<IExtendedModifier>) body.modifiers()) {
            if (imod.isModifier()) {
                if (((Modifier)imod).isStatic()) return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public void build(TypeDeclaration typeDecl)
	throws UnsupportedSyntax {
        Utils.logit("DFClassSpace.build: "+this+": "+typeDecl.getName());
        DFTypeSpace child = _typeSpace.lookupSpace(typeDecl.getName());

        _baseKlass = _typeSpace.resolveClass(typeDecl.getSuperclassType());
        List<Type> ifaces = typeDecl.superInterfaceTypes();
        _baseIfaces = new DFClassSpace[ifaces.size()];
        for (int i = 0; i < ifaces.size(); i++) {
            _baseIfaces[i] = _typeSpace.resolveClass(ifaces.get(i));
        }

        for (BodyDeclaration body :
                 (List<BodyDeclaration>) typeDecl.bodyDeclarations()) {
            if (body instanceof TypeDeclaration) {
                TypeDeclaration decl = (TypeDeclaration)body;
                DFClassSpace klass = child.lookupClass(decl.getName());
                assert(klass != null);
                klass.build(decl);

            } else if (body instanceof FieldDeclaration) {
		// XXX support static field.
                FieldDeclaration decl = (FieldDeclaration)body;
		DFType type = _typeSpace.resolve(decl.getType());
		for (VariableDeclarationFragment frag :
			 (List<VariableDeclarationFragment>) decl.fragments()) {
		    this.addField(frag.getName(), type);
		}

            } else if (body instanceof MethodDeclaration) {
                MethodDeclaration decl = (MethodDeclaration)body;
                DFType[] argTypes = getTypeList(decl);
                DFType returnType;
                if (decl.isConstructor()) {
                    returnType = new DFClassType(this);
                } else {
                    returnType = _typeSpace.resolve(decl.getReturnType2());
                }
                this.addMethod(decl.getName(), isStatic(decl), argTypes, returnType);

            } else {
                throw new UnsupportedSyntax(body);
            }
        }
    }

    // dumpContents (for debugging)
    public void dumpContents(PrintStream out, String indent) {
	super.dumpContents(out, indent);
	for (DFMethod method : _methods) {
	    out.println(indent+"defined: "+method);
	}
    }
}
