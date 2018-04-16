//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFClassScope
//
public class DFClassScope extends DFVarScope {

    private DFTypeScope _typeScope;

    private Map<String, DFMethod> _id2method =
	new HashMap<String, DFMethod>();

    public DFClassScope(DFTypeScope typeScope) {
        super("unknown");
	_typeScope = typeScope;
    }

    public DFClassScope(DFTypeScope typeScope, SimpleName name) {
        super(name.getIdentifier());
	_typeScope = typeScope;
	this.addRef("#this", new DFTypeRef(name));
    }

    public DFClassScope getBase() {
        return this;            // XXX support base class.
    }

    public String getName() {
	return _typeScope.getName()+"/"+super.getName();
    }

    public DFVarScope addChild(String name) {
        return new DFVarScope(this, name);
    }

    public DFVarRef lookupThis() {
        return this.lookupRef("#this", false);
    }

    public DFVarRef addField(SimpleName name, DFTypeRef type) {
        return this.addRef("."+name.getIdentifier(), type);
    }

    public DFVarRef lookupField(SimpleName name) {
	return this.lookupField(name, true);
    }
    public DFVarRef lookupField(SimpleName name, boolean add) {
        String id = Utils.resolveName(name);
        if (id != null) { return new DFVarRef(null, id, null); }
        return this.lookupRef("."+name.getIdentifier(), add);
    }

    public DFMethod addMethod(String id, DFTypeRef returnType) {
	DFMethod method = _id2method.get(id);
	if (method == null) {
            method = new DFMethod(this, id, returnType);
            _id2method.put(id, method);
        }
	return method;
    }

    public DFMethod addMethod(SimpleName name, DFTypeRef returnType) {
        return this.addMethod(name.getIdentifier(), returnType);
    }

    public DFMethod lookupMethod(SimpleName name) {
        String id = Utils.resolveName(name);
        if (id != null) { return new DFMethod(null, id, null); }
        id = name.getIdentifier();
	DFMethod method = _id2method.get(id);
	if (method != null) {
	    return method;
	} else {
	    return this.addMethod(id, null);
	}
    }

    public void dumpContents(PrintStream out, String indent) {
	super.dumpContents(out, indent);
	for (DFMethod method : _id2method.values()) {
	    out.println(indent+"defined: "+method);
	}
    }
}
