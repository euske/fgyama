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
    private Map<String, DFParamType> _id2paramtype =
	new HashMap<String, DFParamType>();

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

    public DFClassSpace createClass(SimpleName name) {
        return this.createClass(name.getIdentifier());
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

    public DFParamType getParamType(String id) {
        return _id2paramtype.get(id);
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
        List<TypeParameter> tps = typeDecl.typeParameters();
        DFParamType[] pts = new DFParamType[tps.size()];
        for (int i = 0; i < tps.size(); i++) {
            String id = tps.get(i).getName().getIdentifier();
            DFParamType pt = new DFParamType(klass, i, id);
            pts[i] = pt;
            child._id2paramtype.put(id, pt);
        }
        klass.setParamTypes(pts);
        for (BodyDeclaration body :
                 (List<BodyDeclaration>) typeDecl.bodyDeclarations()) {
            child.build(body);
        }
    }

    public void build(AbstractTypeDeclaration abstTypeDecl)
        throws UnsupportedSyntax {
        if (abstTypeDecl instanceof TypeDeclaration) {
            this.build((TypeDeclaration)abstTypeDecl);
        } else {
            // XXX enum not supported.
            throw new UnsupportedSyntax(abstTypeDecl);
        }
    }

    public void build(BodyDeclaration body)
	throws UnsupportedSyntax {
        if (body instanceof AbstractTypeDeclaration) {
            this.build((AbstractTypeDeclaration)body);
        } else if (body instanceof FieldDeclaration) {
            ;
        } else if (body instanceof MethodDeclaration) {
            ;
        } else if (body instanceof Initializer) {
	    ;
        } else {
            throw new UnsupportedSyntax(body);
        }
    }

    @SuppressWarnings("unchecked")
    public void build(Statement ast)
	throws UnsupportedSyntax {

	if (ast instanceof AssertStatement) {

	} else if (ast instanceof Block) {
	    Block block = (Block)ast;
	    for (Statement stmt :
		     (List<Statement>) block.statements()) {
		this.build(stmt);
	    }

	} else if (ast instanceof EmptyStatement) {

	} else if (ast instanceof VariableDeclarationStatement) {

	} else if (ast instanceof ExpressionStatement) {

	} else if (ast instanceof ReturnStatement) {

	} else if (ast instanceof IfStatement) {
	    IfStatement ifStmt = (IfStatement)ast;
	    Statement thenStmt = ifStmt.getThenStatement();
	    this.build(thenStmt);
	    Statement elseStmt = ifStmt.getElseStatement();
	    if (elseStmt != null) {
		this.build(elseStmt);
	    }

	} else if (ast instanceof SwitchStatement) {
	    SwitchStatement switchStmt = (SwitchStatement)ast;
	    for (Statement stmt :
		     (List<Statement>) switchStmt.statements()) {
		this.build(stmt);
	    }

	} else if (ast instanceof SwitchCase) {

	} else if (ast instanceof WhileStatement) {
	    WhileStatement whileStmt = (WhileStatement)ast;
	    Statement stmt = whileStmt.getBody();
	    this.build(stmt);

	} else if (ast instanceof DoStatement) {
	    DoStatement doStmt = (DoStatement)ast;
	    Statement stmt = doStmt.getBody();
	    this.build(stmt);

	} else if (ast instanceof ForStatement) {
	    ForStatement forStmt = (ForStatement)ast;
	    Statement stmt = forStmt.getBody();
	    this.build(stmt);

	} else if (ast instanceof EnhancedForStatement) {
	    EnhancedForStatement eForStmt = (EnhancedForStatement)ast;
	    Statement stmt = eForStmt.getBody();
	    this.build(stmt);

	} else if (ast instanceof BreakStatement) {

	} else if (ast instanceof ContinueStatement) {

	} else if (ast instanceof LabeledStatement) {
	    LabeledStatement labeledStmt = (LabeledStatement)ast;
	    Statement stmt = labeledStmt.getBody();
	    this.build(stmt);

	} else if (ast instanceof SynchronizedStatement) {
	    SynchronizedStatement syncStmt = (SynchronizedStatement)ast;
	    Block block = syncStmt.getBody();
	    this.build(block);

	} else if (ast instanceof TryStatement) {
	    TryStatement tryStmt = (TryStatement)ast;
	    Block block = tryStmt.getBody();
	    this.build(block);
	    for (CatchClause cc :
		     (List<CatchClause>) tryStmt.catchClauses()) {
		this.build(cc.getBody());
	    }
	    Block finBlock = tryStmt.getFinally();
	    if (finBlock != null) {
		this.build(finBlock);
	    }

	} else if (ast instanceof ThrowStatement) {

	} else if (ast instanceof ConstructorInvocation) {

	} else if (ast instanceof SuperConstructorInvocation) {

	} else if (ast instanceof TypeDeclarationStatement) {
            TypeDeclarationStatement typeDeclStmt = (TypeDeclarationStatement)ast;
            this.build(typeDeclStmt.getDeclaration());

	} else {
	    throw new UnsupportedSyntax(ast);

	}
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
