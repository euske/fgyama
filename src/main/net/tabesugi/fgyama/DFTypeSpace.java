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

    public DFTypeSpace(String name) {
        _root = this;
        _name = name;
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
                //Logger.info("DFTypeSpace.addChild: "+this+": "+id);
            }
            return space;
        }
    }

    public DFClassSpace createClass(DFVarSpace parent, SimpleName name) {
        return this.createClass(parent, name.getIdentifier());
    }
    public DFClassSpace createClass(DFVarSpace parent, String id) {
        int i = id.indexOf('.');
        if (0 <= i) {
            DFTypeSpace space = this.lookupSpace(id.substring(0, i));
            return space.createClass(parent, id.substring(i+1));
        } else {
            DFTypeSpace child = this.lookupSpace(id);
            DFClassSpace klass = new DFClassSpace(this, child, parent, id);
            //Logger.info("DFTypeSpace.createClass: "+klass);
            return this.addClass(klass);
        }
    }

    public DFClassSpace addClass(DFClassSpace klass) {
        String id = klass.getBaseName();
        assert(id.indexOf('.') < 0);
        //assert(!_id2klass.containsKey(id));
        _id2klass.put(id, klass);
        //Logger.info("DFTypeSpace.addClass: "+this+": "+id);
        return klass;
    }

    public void addClasses(DFTypeSpace typeSpace) {
        for (DFClassSpace klass : typeSpace._id2klass.values()) {
            this.addClass(klass);
        }
    }

    public DFClassSpace getClass(Name name)
        throws TypeNotFound {
        return this.getClass(name.getFullyQualifiedName());
    }
    public DFClassSpace getClass(String id)
        throws TypeNotFound {
        //Logger.info("DFTypeSpace.getClass: "+this+": "+id);
        DFClassSpace klass = null;
        DFTypeSpace space = this;
        while (space != null) {
            int i = id.indexOf('.');
            if (i < 0) {
                klass = space._id2klass.get(id);
                if (klass == null) {
                    throw new TypeNotFound(id);
                }
                return klass;
            }
            String key = id.substring(0, i);
            klass = space._id2klass.get(key);
            if (klass != null) {
                // Search inner classes from now...
                space = klass.getChildSpace();
            } else {
                space = space._id2space.get(key);
            }
            id = id.substring(i+1);
        }
        throw new TypeNotFound(id);
    }

    public void addParamType(DFParamType pt) {
        _id2paramtype.put(pt.getBaseName(), pt);
    }

    public DFParamType getParamType(String id) {
        return _id2paramtype.get(id);
    }

    @SuppressWarnings("unchecked")
    public void build(
        List<DFClassSpace> classes,
        CompilationUnit cunit, DFGlobalVarSpace global)
        throws UnsupportedSyntax {
        for (TypeDeclaration typeDecl :
                 (List<TypeDeclaration>) cunit.types()) {
            this.build(classes, typeDecl, global);
        }
    }

    @SuppressWarnings("unchecked")
    public void build(
        List<DFClassSpace> classes,
        TypeDeclaration typeDecl, DFVarSpace parent)
        throws UnsupportedSyntax {
        //Logger.info("DFTypeSpace.build: "+this+": "+typeDecl.getName());
        DFClassSpace klass = this.createClass(parent, typeDecl.getName());
        if (classes != null) {
            classes.add(klass);
        }
        DFTypeSpace child = klass.getChildSpace();
        DFParamType[] pts = DFParamType.createParamTypes(
            child, typeDecl.typeParameters());
        for (DFParamType pt : pts) {
            child.addParamType(pt);
        }
        klass.setParamTypes(pts);
        for (BodyDeclaration body :
                 (List<BodyDeclaration>) typeDecl.bodyDeclarations()) {
            child.build(classes, body, klass);
        }
    }

    public void build(
        List<DFClassSpace> classes,
        AbstractTypeDeclaration abstTypeDecl, DFVarSpace parent)
        throws UnsupportedSyntax {
        if (abstTypeDecl instanceof TypeDeclaration) {
            this.build(classes, (TypeDeclaration)abstTypeDecl, parent);
        } else {
            // XXX enum not supported.
            throw new UnsupportedSyntax(abstTypeDecl);
        }
    }

    public void build(
        List<DFClassSpace> classes,
        BodyDeclaration body, DFVarSpace parent)
        throws UnsupportedSyntax {
        if (body instanceof AbstractTypeDeclaration) {
            this.build(classes, (AbstractTypeDeclaration)body, parent);
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
    public void build(
        List<DFClassSpace> classes,
        Statement ast, DFVarSpace parent)
        throws UnsupportedSyntax {

        if (ast instanceof AssertStatement) {

        } else if (ast instanceof Block) {
            Block block = (Block)ast;
            for (Statement stmt :
                     (List<Statement>) block.statements()) {
                this.build(classes, stmt, parent);
            }

        } else if (ast instanceof EmptyStatement) {

        } else if (ast instanceof VariableDeclarationStatement) {

        } else if (ast instanceof ExpressionStatement) {

        } else if (ast instanceof ReturnStatement) {

        } else if (ast instanceof IfStatement) {
            IfStatement ifStmt = (IfStatement)ast;
            Statement thenStmt = ifStmt.getThenStatement();
            this.build(classes, thenStmt, parent);
            Statement elseStmt = ifStmt.getElseStatement();
            if (elseStmt != null) {
                this.build(classes, elseStmt, parent);
            }

        } else if (ast instanceof SwitchStatement) {
            SwitchStatement switchStmt = (SwitchStatement)ast;
            for (Statement stmt :
                     (List<Statement>) switchStmt.statements()) {
                this.build(classes, stmt, parent);
            }

        } else if (ast instanceof SwitchCase) {

        } else if (ast instanceof WhileStatement) {
            WhileStatement whileStmt = (WhileStatement)ast;
            Statement stmt = whileStmt.getBody();
            this.build(classes, stmt, parent);

        } else if (ast instanceof DoStatement) {
            DoStatement doStmt = (DoStatement)ast;
            Statement stmt = doStmt.getBody();
            this.build(classes, stmt, parent);

        } else if (ast instanceof ForStatement) {
            ForStatement forStmt = (ForStatement)ast;
            Statement stmt = forStmt.getBody();
            this.build(classes, stmt, parent);

        } else if (ast instanceof EnhancedForStatement) {
            EnhancedForStatement eForStmt = (EnhancedForStatement)ast;
            Statement stmt = eForStmt.getBody();
            this.build(classes, stmt, parent);

        } else if (ast instanceof BreakStatement) {

        } else if (ast instanceof ContinueStatement) {

        } else if (ast instanceof LabeledStatement) {
            LabeledStatement labeledStmt = (LabeledStatement)ast;
            Statement stmt = labeledStmt.getBody();
            this.build(classes, stmt, parent);

        } else if (ast instanceof SynchronizedStatement) {
            SynchronizedStatement syncStmt = (SynchronizedStatement)ast;
            Block block = syncStmt.getBody();
            this.build(classes, block, parent);

        } else if (ast instanceof TryStatement) {
            TryStatement tryStmt = (TryStatement)ast;
            Block block = tryStmt.getBody();
            this.build(classes, block, parent);
            for (CatchClause cc :
                     (List<CatchClause>) tryStmt.catchClauses()) {
                this.build(classes, cc.getBody(), parent);
            }
            Block finBlock = tryStmt.getFinally();
            if (finBlock != null) {
                this.build(classes, finBlock, parent);
            }

        } else if (ast instanceof ThrowStatement) {

        } else if (ast instanceof ConstructorInvocation) {

        } else if (ast instanceof SuperConstructorInvocation) {

        } else if (ast instanceof TypeDeclarationStatement) {
            TypeDeclarationStatement typeDeclStmt = (TypeDeclarationStatement)ast;
            this.build(classes, typeDeclStmt.getDeclaration(), parent);

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
