//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFClassType
//
public class DFClassType extends DFTypeRef {

    private DFTypeScope _scope;

    private Map<String, DFRef> _name2ref = new HashMap<String, DFRef>();
    private Map<String, DFMethod> _name2method = new HashMap<String, DFMethod>();

    public DFClassType(DFTypeScope scope, String name) {
        super(name);
	_scope = scope;
    }

    public String getName() {
	return _scope.getFullName()+"/"+super.getName();
    }

    public DFRef addRef(String name, DFTypeRef type) {
	DFRef ref = _name2ref.get(name);
	if (ref == null) {
            ref = new DFRef(null, name, type);
            _name2ref.put(name, ref);
        }
	return ref;
    }

    public DFRef addRef(SimpleName name, DFTypeRef type) {
        return this.addRef(name.getIdentifier(), type);
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
}
