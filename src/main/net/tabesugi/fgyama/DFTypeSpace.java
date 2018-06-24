//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.apache.bcel.*;
import org.apache.bcel.classfile.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;
import org.w3c.dom.*;


//  DFTypeSpace
//
public class DFTypeSpace {

    private DFTypeSpace _root;
    private String _name = null;

    private List<DFTypeSpace> _children =
	new ArrayList<DFTypeSpace>();
    private Map<String, DFTypeSpace> _id2space =
	new HashMap<String, DFTypeSpace>();
    private Map<String, DFClassSpace> _id2klass =
	new HashMap<String, DFClassSpace>();

    public DFTypeSpace() {
        _root = this;
    }

    public DFTypeSpace(DFTypeSpace parent, String name) {
        _root = parent._root;
	if (parent == _root) {
	    _name = name;
	} else {
	    _name = parent._name + "." + name;
	}
    }

    @Override
    public String toString() {
	return ("<DFTypeSpace("+_name+")>");
    }

    public String getFullName() {
	return _name;
    }

    public String getAnonName() {
        return "anon"+_children.size(); // assign unique id.
    }

    public DFTypeSpace lookupSpace(PackageDeclaration pkgDecl) {
        if (pkgDecl == null) {
            return this;
        } else {
            return this.lookupSpace(pkgDecl.getName());
        }
    }

    public DFTypeSpace lookupSpace(Name name) {
        return this.lookupSpace(name.getFullyQualifiedName());
    }

    public DFTypeSpace lookupSpace(String id) {
        int i = id.indexOf('.');
        if (0 <= i) {
            DFTypeSpace space = this.lookupSpace(id.substring(0, i));
            return space.lookupSpace(id.substring(i+1));
        } else {
            DFTypeSpace space = _id2space.get(id);
            if (space == null) {
                space = new DFTypeSpace(this, id);
                _children.add(space);
                _id2space.put(id, space);
                //Utils.logit("DFTypeSpace.addChild: "+this+": "+id);
            }
            return space;
        }
    }

    public DFClassSpace createClass(String id) {
        int i = id.indexOf('.');
        if (0 <= i) {
            DFTypeSpace space = this.lookupSpace(id.substring(0, i));
            return space.createClass(id.substring(i+1));
        } else {
            DFTypeSpace child = this.lookupSpace(id);
            DFClassSpace klass = new DFClassSpace(this, child, id);
            //Utils.logit("DFTypeSpace.createClass: "+this+": "+klass);
            return this.addClass(klass);
        }
    }

    public DFClassSpace addClass(DFClassSpace klass) {
        String id = klass.getBaseName();
        assert(id.indexOf('.') < 0);
        //assert(!_id2klass.containsKey(id));
	_id2klass.put(id, klass);
        //Utils.logit("DFTypeSpace.addClass: "+this+": "+id);
	return klass;
    }

    public void addClasses(DFTypeSpace typeSpace) {
        for (DFClassSpace klass : typeSpace._id2klass.values()) {
            this.addClass(klass);
        }
    }

    public DFClassSpace getClass(Name name)
        throws EntityNotFound {
        return this.getClass(name.getFullyQualifiedName());
    }
    public DFClassSpace getClass(String id)
        throws EntityNotFound {
        //Utils.logit("DFTypeSpace.getClass: "+this+": "+id);
        DFTypeSpace space = this;
        while (space != null) {
            int i = id.indexOf('.');
            if (i < 0) {
                DFClassSpace klass = space._id2klass.get(id);
                if (klass == null) {
                    throw new EntityNotFound(id);
                }
                return klass;
            }
            space = space._id2space.get(id.substring(0, i));
            id = id.substring(i+1);
        }
        throw new EntityNotFound(id);
    }

    @SuppressWarnings("unchecked")
    public void build(CompilationUnit cunit)
	throws UnsupportedSyntax {
        for (TypeDeclaration typeDecl :
                 (List<TypeDeclaration>) cunit.types()) {
            this.build(typeDecl);
        }
    }

    @SuppressWarnings("unchecked")
    public void build(TypeDeclaration typeDecl)
	throws UnsupportedSyntax {
        //Utils.logit("DFTypeSpace.build: "+this+": "+typeDecl.getName());
        DFClassSpace klass = this.createClass(typeDecl.getName().getIdentifier());
        DFTypeSpace child = klass.getChildSpace();
        for (BodyDeclaration body :
                 (List<BodyDeclaration>) typeDecl.bodyDeclarations()) {
            child.build(body);
        }
    }

    public void build(BodyDeclaration body)
	throws UnsupportedSyntax {
        if (body instanceof TypeDeclaration) {
            this.build((TypeDeclaration)body);
        } else if (body instanceof FieldDeclaration) {
            ;
        } else if (body instanceof MethodDeclaration) {
            this.build(((MethodDeclaration)body).getBody());
        } else if (body instanceof Initializer) {
	    ;
        } else {
            throw new UnsupportedSyntax(body);
        }
    }

    public void build(Block block)
	throws UnsupportedSyntax {

    }

    // dump: for debugging.
    public void dump() {
	dump(System.err, "");
    }
    public void dump(PrintStream out, String indent) {
	out.println(indent+_name+" {");
	String i2 = indent + "  ";
	for (DFClassSpace klass : _id2klass.values()) {
	    out.println(i2+"defined: "+klass);
	}
	for (DFTypeSpace space : _children) {
	    space.dump(out, i2);
	}
	out.println(indent+"}");
    }
}
