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

    public DFClassSpace getBase() {
        return this;            // XXX support base class.
    }

    public String getName() {
	return _typeSpace.getName()+"/"+super.getName();
    }

    public DFVarSpace addChild(String name) {
        return new DFVarSpace(this, name);
    }

    public DFVarRef lookupThis() {
        return this.lookupRef("#this");
    }

    public DFVarRef addField(SimpleName name, DFType type) {
        return this.addRef("."+name.getIdentifier(), type);
    }

    protected DFVarRef lookupField(String id) {
	return this.lookupRef("."+id);
    }
    public DFVarRef lookupField(SimpleName name) {
        DFVarRef ref = this.lookupField(name.getIdentifier());
        if (ref != null) return ref;
        String id = Utils.resolveName(name);
        if (id != null) return new DFVarRef(null, id, null);
        return new DFVarRef(null, name.getIdentifier(), null);
    }

    public DFMethod addMethod(String id, DFType returnType) {
	DFMethod method = _id2method.get(id);
	if (method == null) {
            method = new DFMethod(this, id, returnType);
            _id2method.put(id, method);
        }
	return method;
    }

    public DFMethod addMethod(SimpleName name, DFType returnType) {
        return this.addMethod(name.getIdentifier(), returnType);
    }

    public DFMethod lookupMethod(SimpleName name) {
	DFMethod method = _id2method.get(name.getIdentifier());
	if (method != null) return method;
        String id = Utils.resolveName(name);
        if (id != null) { return new DFMethod(null, id, null); }
        return this.addMethod(name.getIdentifier(), null);
    }

    public void dumpContents(PrintStream out, String indent) {
	super.dumpContents(out, indent);
	for (DFMethod method : _id2method.values()) {
	    out.println(indent+"defined: "+method);
	}
    }

    @SuppressWarnings("unchecked")
    public void build(DFTypeSpace typeSpace, TypeDeclaration typeDecl)
	throws UnsupportedSyntax {

        typeSpace = typeSpace.lookupSpace(typeDecl.getName());

        for (BodyDeclaration body :
                 (List<BodyDeclaration>) typeDecl.bodyDeclarations()) {
            if (body instanceof TypeDeclaration) {
                TypeDeclaration decl = (TypeDeclaration)body;
                DFClassSpace klass = typeSpace.lookupClass(decl.getName());
                klass.build(typeSpace, decl);

            } else if (body instanceof FieldDeclaration) {
		// XXX support static field.
                FieldDeclaration decl = (FieldDeclaration)body;
		DFType type = typeSpace.resolve(decl.getType());
		for (VariableDeclarationFragment frag :
			 (List<VariableDeclarationFragment>) decl.fragments()) {
		    this.addField(frag.getName(), type);
		}

            } else if (body instanceof MethodDeclaration) {
		// XXX support static method.
                MethodDeclaration decl = (MethodDeclaration)body;
		DFType returnType = typeSpace.resolve(decl.getReturnType2());
		this.addMethod(decl.getName(), returnType);

            } else {
                throw new UnsupportedSyntax(body);
            }
        }
    }
}
