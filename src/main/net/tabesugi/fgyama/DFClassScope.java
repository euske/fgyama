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

    private Map<String, DFMethod> _name2method = new HashMap<String, DFMethod>();

    public DFClassScope(DFTypeScope typeScope, SimpleName name) {
        super(name.getIdentifier());
	_typeScope = typeScope;
	this.addRef("#this", new DFTypeRef(name));
    }

    public DFClassScope getBase() {
        return this;            // XXX
    }

    public String getName() {
	return _typeScope.getName()+"/"+super.getName();
    }

    public DFVarScope addChild(String name) {
        return new DFVarScope(this, name);
    }

    public DFRef lookupThis() {
        return this.lookupRef("#this");
    }

    public DFRef addField(SimpleName name, DFTypeRef type) {
        return this.addRef("."+name.getIdentifier(), type);
    }

    public DFRef lookupField(SimpleName name) {
        return this.lookupRef("."+name.getIdentifier());
    }

    public DFMethod addMethod(String name, DFTypeRef returnType) {
	DFMethod method = _name2method.get(name);
	if (method == null) {
            method = new DFMethod(this, name, returnType);
            _name2method.put(name, method);
        }
	return method;
    }

    public DFMethod addMethod(SimpleName name, DFTypeRef returnType) {
        return this.addMethod(name.getIdentifier(), returnType);
    }

    public DFMethod lookupMethod(String id) {
	DFMethod method = _name2method.get(id);
	if (method != null) {
	    return method;
	} else {
	    return this.addMethod(id, null);
	}
    }
}
