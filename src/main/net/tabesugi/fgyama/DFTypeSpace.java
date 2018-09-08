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
    private Map<String, DFClass> _id2klass =
        new HashMap<String, DFClass>();
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

    public DFClass createClass(DFVarScope parent, SimpleName name) {
        return this.createClass(parent, name.getIdentifier());
    }
    public DFClass createClass(DFVarScope parent, String id) {
        int i = id.indexOf('.');
        if (0 <= i) {
            DFTypeSpace space = this.lookupSpace(id.substring(0, i));
            return space.createClass(parent, id.substring(i+1));
        } else {
            DFTypeSpace child = this.lookupSpace(id);
            DFClass klass = new DFClass(this, child, parent, id);
            //Logger.info("DFTypeSpace.createClass: "+klass);
            return this.addClass(klass);
        }
    }

    public DFClass addClass(DFClass klass) {
        String id = klass.getClassName();
        assert(id.indexOf('.') < 0);
        //assert(!_id2klass.containsKey(id));
        _id2klass.put(id, klass);
        //Logger.info("DFTypeSpace.addClass: "+this+": "+id);
        return klass;
    }

    public DFClass getClass(Name name)
        throws TypeNotFound {
        return this.getClass(name.getFullyQualifiedName());
    }
    public DFClass getClass(String id)
        throws TypeNotFound {
        //Logger.info("DFTypeSpace.getClass: "+this+": "+id);
        DFClass klass = null;
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

    public void addParamType(String id, DFParamType pt) {
        _id2paramtype.put(id, pt);
    }

    public DFParamType getParamType(String id) {
        return _id2paramtype.get(id);
    }

    @SuppressWarnings("unchecked")
    public void build(
        List<DFClass> classes,
        CompilationUnit cunit, DFGlobalVarScope global)
        throws UnsupportedSyntax {
        for (AbstractTypeDeclaration abstTypeDecl :
                 (List<AbstractTypeDeclaration>) cunit.types()) {
            this.build(classes, abstTypeDecl, global);
        }
    }

    public void build(
        List<DFClass> classes,
        AbstractTypeDeclaration abstTypeDecl, DFVarScope parent)
        throws UnsupportedSyntax {
        if (abstTypeDecl instanceof TypeDeclaration) {
            this.build(classes, (TypeDeclaration)abstTypeDecl, parent);
        } else if (abstTypeDecl instanceof EnumDeclaration) {
            // XXX enum not supported.
            //this.build(classes, (EnumDeclaration)abstTypeDecl, parent);
        } else if (abstTypeDecl instanceof AnnotationTypeDeclaration) {
            ;
        } else {
            throw new UnsupportedSyntax(abstTypeDecl);
        }
    }

    @SuppressWarnings("unchecked")
    public void build(
        List<DFClass> classes,
        TypeDeclaration typeDecl, DFVarScope parent)
        throws UnsupportedSyntax {
        //Logger.info("DFTypeSpace.build: "+this+": "+typeDecl.getName());
        DFClass klass = this.createClass(parent, typeDecl.getName());
        if (classes != null) {
            classes.add(klass);
        }
        DFTypeSpace child = klass.getChildSpace();
        List<TypeParameter> tps = (List<TypeParameter>) typeDecl.typeParameters();
        DFParamType[] pts = new DFParamType[tps.size()];
        for (int i = 0; i < tps.size(); i++) {
            TypeParameter tp = tps.get(i);
            String id = tp.getName().getIdentifier();
            DFParamType pt = new DFParamType(child, i, id);
            child.addParamType(id, pt);
            pts[i] = pt;
        }
        klass.setParamTypes(pts);
        for (BodyDeclaration body :
                 (List<BodyDeclaration>) typeDecl.bodyDeclarations()) {
            child.build(classes, body, klass);
        }
    }

    public void build(
        List<DFClass> classes,
        BodyDeclaration body, DFVarScope parent)
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
        List<DFClass> classes,
        Statement ast, DFVarScope parent)
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
        for (DFClass klass : _id2klass.values()) {
            out.println(i2+"defined: "+klass);
        }
        for (DFTypeSpace space : _children) {
            space.dump(out, i2);
        }
        out.println(indent+"}");
    }
}
