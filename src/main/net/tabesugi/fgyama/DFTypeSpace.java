//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;
import org.w3c.dom.*;


//  DFTypeSpace
//
public class DFTypeSpace {

    private DFTypeSpace _root;
    private String _name;
    private DFTypeSpace _parent;
    private DFClassSpace _default;

    private List<DFTypeSpace> _children =
	new ArrayList<DFTypeSpace>();
    private Map<String, DFTypeSpace> _id2child =
	new HashMap<String, DFTypeSpace>();
    private Map<String, DFClassSpace> _id2class =
	new HashMap<String, DFClassSpace>();

    public DFTypeSpace(String name) {
        _root = this;
	_name = name;
        _parent = null;
	_default = new DFClassSpace(this);
    }

    public DFTypeSpace(DFTypeSpace parent, String name) {
        _root = parent._root;
	_name = name;
	_parent = parent;
	_default = null;
    }

    public DFTypeSpace(DFTypeSpace space) {
        _root = space._root;
        _name = space._name;
        _parent = space._parent;
        _default = space._default;
        _children = new ArrayList<DFTypeSpace>(space._children);
        _id2child = new HashMap<String, DFTypeSpace>(space._id2child);
        _id2class = new HashMap<String, DFClassSpace>(space._id2class);
    }

    @Override
    public String toString() {
	return ("<DFTypeSpace("+_name+")>");
    }

    public String getName() {
        if (_parent == null) {
            return _name;
        } else {
            return _parent.getName()+"."+_name;
        }
    }

    public DFClassSpace getDefaultClass() {
	return _default;
    }

    public DFClassSpace addClass(SimpleName name) {
	DFClassSpace klass = new DFClassSpace(this, name);
        this.addClass(name, klass);
	return klass;
    }

    public void addClass(SimpleName name, DFClassSpace klass) {
        String id = name.getIdentifier();
        assert(!_id2class.containsKey(id));
	_id2class.put(id, klass);
    }

    private DFClassSpace lookupClass(String id) {
        if (id.startsWith(".")) {
	    int i = id.lastIndexOf('.');
	    if (i == 0) {
		DFClassSpace klass = _id2class.get(id.substring(i+1));
		if (klass != null) {
		    return klass;
		}
	    } else if (_parent != null) {
		DFTypeSpace parent = _parent.getChildSpace(id.substring(0, i));
		return parent.lookupClass("."+id.substring(i+1));
            }
        }
        return _root.getDefaultClass();
    }

    public DFClassSpace lookupClass(DFTypeRef type) {
	if (type != null) {
	    return this.lookupClass(type.getId());
	} else {
	    return _root.getDefaultClass();
	}
    }

    public DFClassSpace lookupClass(Name name) {
        if (name.isQualifiedName()) {
	    QualifiedName qname = (QualifiedName)name;
            DFTypeSpace parent = this.getChildSpace(qname.getQualifier());
            return parent.lookupClass(qname.getName());
        } else {
            SimpleName sname = (SimpleName)name;
            return this.lookupClass("."+sname.getIdentifier());
        }
    }

    private DFTypeSpace getChildSpace(String id) {
        DFTypeSpace space = _id2child.get(id);
        if (space == null) {
            space = new DFTypeSpace(this, id);
            _children.add(space);
            _id2child.put(id, space);
        }
        return space;
    }

    public DFTypeSpace getChildSpace(Name name) {
        if (name.isQualifiedName()) {
	    QualifiedName qname = (QualifiedName)name;
	    DFTypeSpace parent = (_parent != null)? _parent : this;
	    parent = parent.getChildSpace(qname.getQualifier());
            return parent.getChildSpace(qname.getName());
        } else {
            SimpleName sname = (SimpleName)name;
            return this.getChildSpace(sname.getIdentifier());
        }
    }

    // dump: for debugging.
    public void dump() {
	dump(System.err, "");
    }
    public void dump(PrintStream out, String indent) {
	out.println(indent+_name+" {");
	String i2 = indent + "  ";
	for (DFClassSpace klass : _id2class.values()) {
	    out.println(i2+"defined: "+klass);
	}
	for (DFTypeSpace space : _children) {
	    space.dump(out, i2);
	}
	out.println(indent+"}");
    }

    @SuppressWarnings("unchecked")
    public void build(TypeDeclaration typeDecl)
	throws UnsupportedSyntax {

        DFClassSpace klass = this.addClass(typeDecl.getName());
        DFTypeSpace child = this.getChildSpace(typeDecl.getName());

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
