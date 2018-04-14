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

    private List<DFTypeScope> _children = new ArrayList<DFTypeScope>();
    private Map<String, DFTypeScope> _name2child = new HashMap<String, DFTypeScope>();
    private Map<String, DFClassScope> _name2class = new HashMap<String, DFClassScope>();

    public DFTypeScope(String name) {
        _root = this;
	_name = name;
        _parent = null;
    }

    public DFTypeScope(DFTypeScope parent, String name) {
        _root = parent._root;
	_name = name;
	_parent = parent;
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

    protected DFClassScope lookupClass(String name) {
        DFClassScope klass = _name2class.get(name);
        if (klass != null) {
            return klass;
        } else if (_parent != null) {
            return _parent.lookupClass(name);
        } else {
            return null;
        }
    }

    public DFClassScope lookupClass(SimpleName name) {
	return this.lookupClass(name.getIdentifier());
    }

    public DFClassScope lookupClass(DFTypeRef type) {
	return this.lookupClass(type.getName());
    }

    public DFClassScope addClass(SimpleName name) {
	DFClassScope klass = new DFClassScope(this, name);
	_name2class.put(name.getIdentifier(), klass);
	return klass;
    }

    public DFTypeScope addChildScope(String name) {
        DFTypeScope scope = _name2child.get(name);
        if (scope == null) {
            scope = new DFTypeScope(this, name);
            _children.add(scope);
            _name2child.put(name, scope);
        }
        return scope;
    }

    public DFTypeScope addChildScope(Name name) {
        if (name.isQualifiedName()) {
	    QualifiedName qname = (QualifiedName)name;
	    DFTypeScope parent = (_parent != null)? _parent : this;
	    parent = parent.addChildScope(qname.getQualifier());
            return parent.addChildScope(qname.getName());
        } else {
            SimpleName sname = (SimpleName)name;
            return this.addChildScope(sname.getIdentifier());
        }
    }

    // dump: for debugging.
    public void dump() {
	dump(System.out, "");
    }
    public void dump(PrintStream out, String indent) {
	out.println(indent+_name+" {");
	String i2 = indent + "  ";
	for (DFClassScope klass : _name2class.values()) {
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
        DFTypeScope child = this.addChildScope(typeDecl.getName());

        for (BodyDeclaration body :
                 (List<BodyDeclaration>) typeDecl.bodyDeclarations()) {
            if (body instanceof TypeDeclaration) {
                child.build((TypeDeclaration)body);

            } else if (body instanceof FieldDeclaration) {
                FieldDeclaration decl = (FieldDeclaration)body;
		for (VariableDeclarationFragment frag :
			 (List<VariableDeclarationFragment>) decl.fragments()) {
		    DFTypeRef type = new DFTypeRef(decl.getType());
		    klass.addRef(frag.getName(), type);
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
