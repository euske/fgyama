//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;
import org.w3c.dom.*;


//  DFTypeScope
//
public class DFTypeScope {

    private DFTypeScope _root;
    private String _name;
    private DFTypeScope _parent;
    private DFClassScope _default;

    private List<DFTypeScope> _children =
	new ArrayList<DFTypeScope>();
    private Map<String, DFTypeScope> _id2child =
	new HashMap<String, DFTypeScope>();
    private Map<String, DFClassScope> _id2class =
	new HashMap<String, DFClassScope>();

    public DFTypeScope(String name) {
        _root = this;
	_name = name;
        _parent = null;
	_default = new DFClassScope(this);
    }

    public DFTypeScope(DFTypeScope parent, String name) {
        _root = parent._root;
	_name = name;
	_parent = parent;
	_default = null;
    }

    public DFTypeScope(DFTypeScope scope) {
        _root = scope._root;
        _name = scope._name;
        _parent = scope._parent;
        _default = scope._default;
        _children = new ArrayList<DFTypeScope>(scope._children);
        _id2child = new HashMap<String, DFTypeScope>(scope._id2child);
        _id2class = new HashMap<String, DFClassScope>(scope._id2class);
    }

    @Override
    public String toString() {
	return ("<DFTypeScope("+_name+")>");
    }

    public String getName() {
        if (_parent == null) {
            return _name;
        } else {
            return _parent.getName()+"."+_name;
        }
    }

    public DFClassScope getDefaultClass() {
	return _default;
    }

    public DFClassScope addClass(SimpleName name) {
	DFClassScope klass = new DFClassScope(this, name);
        this.addClass(name, klass);
	return klass;
    }

    public void addClass(SimpleName name, DFClassScope klass) {
        String id = name.getIdentifier();
        assert(!_id2class.containsKey(id));
	_id2class.put(id, klass);
    }

    private DFClassScope lookupClass(String id) {
        if (id.startsWith(".")) {
	    int i = id.lastIndexOf('.');
	    if (i == 0) {
		DFClassScope klass = _id2class.get(id.substring(i+1));
		if (klass != null) {
		    return klass;
		}
	    } else if (_parent != null) {
		DFTypeScope parent = _parent.getChildScope(id.substring(0, i));
		return parent.lookupClass("."+id.substring(i+1));
            }
        }
        return _root.getDefaultClass();
    }

    public DFClassScope lookupClass(DFTypeRef type) {
	if (type != null) {
	    return this.lookupClass(type.getId());
	} else {
	    return _root.getDefaultClass();
	}
    }

    public DFClassScope lookupClass(Name name) {
        if (name.isQualifiedName()) {
	    QualifiedName qname = (QualifiedName)name;
            DFTypeScope parent = this.getChildScope(qname.getQualifier());
            return parent.lookupClass(qname.getName());
        } else {
            SimpleName sname = (SimpleName)name;
            return this.lookupClass("."+sname.getIdentifier());
        }
    }

    private DFTypeScope getChildScope(String id) {
        DFTypeScope scope = _id2child.get(id);
        if (scope == null) {
            scope = new DFTypeScope(this, id);
            _children.add(scope);
            _id2child.put(id, scope);
        }
        return scope;
    }

    public DFTypeScope getChildScope(Name name) {
        if (name.isQualifiedName()) {
	    QualifiedName qname = (QualifiedName)name;
	    DFTypeScope parent = (_parent != null)? _parent : this;
	    parent = parent.getChildScope(qname.getQualifier());
            return parent.getChildScope(qname.getName());
        } else {
            SimpleName sname = (SimpleName)name;
            return this.getChildScope(sname.getIdentifier());
        }
    }

    // dump: for debugging.
    public void dump() {
	dump(System.err, "");
    }
    public void dump(PrintStream out, String indent) {
	out.println(indent+_name+" {");
	String i2 = indent + "  ";
	for (DFClassScope klass : _id2class.values()) {
	    out.println(i2+"defined: "+klass);
	}
	for (DFTypeScope scope : _children) {
	    scope.dump(out, i2);
	}
	out.println(indent+"}");
    }

    @SuppressWarnings("unchecked")
    public void build(TypeDeclaration typeDecl)
	throws UnsupportedSyntax {

        DFClassScope klass = this.addClass(typeDecl.getName());
        DFTypeScope child = this.getChildScope(typeDecl.getName());

        for (BodyDeclaration body :
                 (List<BodyDeclaration>) typeDecl.bodyDeclarations()) {
            if (body instanceof TypeDeclaration) {
                child.build((TypeDeclaration)body);

            } else if (body instanceof FieldDeclaration) {
		// XXX support static field.
                FieldDeclaration decl = (FieldDeclaration)body;
		DFTypeRef type = new DFTypeRef(decl.getType());
		for (VariableDeclarationFragment frag :
			 (List<VariableDeclarationFragment>) decl.fragments()) {
		    klass.addField(frag.getName(), type);
		}

            } else if (body instanceof MethodDeclaration) {
                MethodDeclaration decl = (MethodDeclaration)body;
		DFTypeRef returnType = new DFTypeRef(decl.getReturnType2());
		klass.addMethod(decl.getName(), returnType);

            } else {
                throw new UnsupportedSyntax(body);
            }
        }
    }
}
