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

    private Map<String, DFMethod> _id2method =
	new HashMap<String, DFMethod>();

    public DFClassSpace(DFTypeSpace typeSpace) {
        super("unknown");
	_typeSpace = typeSpace;
    }

    public DFClassSpace(DFTypeSpace typeSpace, SimpleName name) {
        super(name.getIdentifier());
	_typeSpace = typeSpace;
	this.addRef("#this", new DFClassType(this));
    }

    @Override
    public String toString() {
	return ("<DFClassSpace("+this.getName()+")>");
    }

    public DFClassSpace getBase() {
        return this;            // XXX support base class.
    }

    public String getName() {
	return _typeSpace.getName()+"/"+super.getName();
    }

    public DFVarRef lookupThis() {
        return this.lookupRef("#this");
    }

    public DFVarRef lookupField(SimpleName name) {
        DFVarRef ref = this.lookupRef("."+name.getIdentifier());
        if (ref != null) return ref;
        String id = Utils.resolveName(name);
        if (id != null) {
            return new DFVarRef(null, id, null);
        }
        return new DFVarRef(null, "."+name.getIdentifier(), null);
    }

    public DFMethod lookupMethod(SimpleName name) {
	DFMethod method = _id2method.get(name.getIdentifier());
	if (method != null) return method;
        String id = Utils.resolveName(name);
        if (id != null) {
            return new DFMethod(null, id, null);
        }
        return this.addMethod(name, null);
    }

    private DFVarRef addField(SimpleName name, DFType type) {
        Utils.logit("DFClassSpace.addField: "+this+": "+name+" -> "+type);
        return this.addRef("."+name.getIdentifier(), type);
    }

    private DFMethod addMethod(SimpleName name, DFType returnType) {
        Utils.logit("DFClassSpace.addMethod: "+this+": "+name+" -> "+returnType);
        String id = name.getIdentifier();
	DFMethod method = _id2method.get(id);
	if (method == null) {
            method = new DFMethod(this, id, returnType);
            _id2method.put(id, method);
        }
	return method;
    }

    @SuppressWarnings("unchecked")
    public void build(TypeDeclaration typeDecl)
	throws UnsupportedSyntax {
        Utils.logit("DFClassSpace.build: "+this+": "+typeDecl.getName());
        DFTypeSpace child = _typeSpace.lookupSpace(typeDecl.getName());
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
		// XXX support static method.
                MethodDeclaration decl = (MethodDeclaration)body;
		DFType returnType = _typeSpace.resolve(decl.getReturnType2());
		this.addMethod(decl.getName(), returnType);

            } else {
                throw new UnsupportedSyntax(body);
            }
        }
    }

    // dumpContents (for debugging)
    public void dumpContents(PrintStream out, String indent) {
	super.dumpContents(out, indent);
	for (DFMethod method : _id2method.values()) {
	    out.println(indent+"defined: "+method);
	}
    }
}
