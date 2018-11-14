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

    private DFTypeSpace _parent;
    private String _name;

    private List<DFTypeSpace> _children =
        new ArrayList<DFTypeSpace>();
    private Map<String, DFTypeSpace> _id2space =
        new HashMap<String, DFTypeSpace>();
    private Map<String, DFKlass> _id2klass =
        new HashMap<String, DFKlass>();

    public DFTypeSpace(DFTypeSpace parent, String name) {
        _parent = parent;
        _name = name;
    }

    @Override
    public String toString() {
        return ("<DFTypeSpace("+this.getFullName()+")>");
    }

    public String getFullName() {
        if (_parent == null) {
            return "";
        } else {
            return _parent.getFullName()+_name+"/";
        }
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
        int i = id.lastIndexOf('.');
        if (0 <= i) {
            DFTypeSpace space = this.lookupSpace(id.substring(0, i));
            return space.lookupSpace(id.substring(i+1));
        }
        DFKlass klass = _id2klass.get(id);
        if (klass != null) {
            return klass.getKlassSpace();
        }
        DFTypeSpace space = _id2space.get(id);
        if (space == null) {
            space = new DFTypeSpace(this, id);
            _children.add(space);
            _id2space.put(id, space);
            //Logger.info("DFTypeSpace.addChild: "+this+": "+id);
        }
        return space;
    }

    protected DFKlass createKlass(
        DFKlass parentKlass, DFVarScope parentScope, SimpleName name) {
        return this.createKlass(
            parentKlass, parentScope, name.getIdentifier());
    }

    protected DFKlass createKlass(
        DFKlass parentKlass, DFVarScope parentScope, String id) {
        assert id.indexOf('.') < 0;
        DFKlass klass = _id2klass.get(id);
        if (klass != null) return klass;
        klass = new DFKlass(
            id, this, parentKlass, parentScope,
            DFRootTypeSpace.getObjectKlass());
        //Logger.info("DFTypeSpace.createKlass: "+klass);
        return this.addKlass(klass);
    }

    public DFParamType createParamType(String id) {
        DFParamType pt = new DFParamType(id, this);
        this.addKlass(pt);
        return pt;
    }

    public DFKlass addKlass(DFKlass klass) {
        String id = klass.getKlassName();
        assert id.indexOf('.') < 0;
        //assert !_id2klass.containsKey(id);
        _id2klass.put(id, klass);
        //Logger.info("DFTypeSpace.addKlass: "+this+": "+id);
        return klass;
    }

    public DFKlass getKlass(Name name)
        throws TypeNotFound {
        return this.getKlass(name.getFullyQualifiedName());
    }
    public DFKlass getKlass(String id)
        throws TypeNotFound {
        //Logger.info("DFTypeSpace.getKlass: "+this+": "+id);
        int i = id.lastIndexOf('.');
        if (0 <= i) {
            DFTypeSpace space = this.lookupSpace(id.substring(0, i));
            return space.getKlass(id.substring(i+1));
        }
        DFKlass klass = _id2klass.get(id);
        if (klass == null) {
            throw new TypeNotFound(this.getFullName()+id);
        }
        return klass;
    }

    @SuppressWarnings("unchecked")
    public void build(
        List<DFKlass> klasses,
        CompilationUnit cunit, DFVarScope scope)
        throws UnsupportedSyntax {
        for (AbstractTypeDeclaration abstTypeDecl :
                 (List<AbstractTypeDeclaration>) cunit.types()) {
            this.build(klasses, abstTypeDecl, null, scope);
        }
    }

    public void build(
        List<DFKlass> klasses, AbstractTypeDeclaration abstTypeDecl,
        DFKlass parentKlass, DFVarScope parentScope)
        throws UnsupportedSyntax {
        assert abstTypeDecl != null;
        if (abstTypeDecl instanceof TypeDeclaration) {
            this.build(klasses, (TypeDeclaration)abstTypeDecl,
                       parentKlass, parentScope);
        } else if (abstTypeDecl instanceof EnumDeclaration) {
            this.build(klasses, (EnumDeclaration)abstTypeDecl,
                       parentKlass, parentScope);
        } else if (abstTypeDecl instanceof AnnotationTypeDeclaration) {
            this.build(klasses, (AnnotationTypeDeclaration)abstTypeDecl,
                       parentKlass, parentScope);
        } else {
            throw new UnsupportedSyntax(abstTypeDecl);
        }
    }

    @SuppressWarnings("unchecked")
    public void build(
        List<DFKlass> klasses, TypeDeclaration typeDecl,
        DFKlass parentKlass, DFVarScope parentScope)
        throws UnsupportedSyntax {
        //Logger.info("DFTypeSpace.build: "+this+": "+typeDecl.getName());
        DFKlass klass = this.createKlass(
            parentKlass, parentScope, typeDecl.getName());
        if (klasses != null) {
            klasses.add(klass);
        }
        klass.addParamTypes(typeDecl.typeParameters());
        DFTypeSpace child = klass.getKlassSpace();
        for (BodyDeclaration body :
                 (List<BodyDeclaration>) typeDecl.bodyDeclarations()) {
            child.build(klasses, body, klass, klass.getKlassScope());
        }
    }

    @SuppressWarnings("unchecked")
    public void build(
        List<DFKlass> klasses, EnumDeclaration enumDecl,
        DFKlass parentKlass, DFVarScope parentScope)
        throws UnsupportedSyntax {
        //Logger.info("DFTypeSpace.build: "+this+": "+enumDecl.getName());
        DFKlass klass = this.createKlass(
            parentKlass, parentScope, enumDecl.getName());
        if (klasses != null) {
            klasses.add(klass);
        }
        DFTypeSpace child = klass.getKlassSpace();
        for (BodyDeclaration body :
                 (List<BodyDeclaration>) enumDecl.bodyDeclarations()) {
            child.build(klasses, body, klass, klass.getKlassScope());
        }
    }

    @SuppressWarnings("unchecked")
    public void build(
        List<DFKlass> klasses, AnnotationTypeDeclaration annotTypeDecl,
        DFKlass parentKlass, DFVarScope parentScope)
        throws UnsupportedSyntax {
        //Logger.info("DFTypeSpace.build: "+this+": "+annotTypeDecl.getName());
        DFKlass klass = this.createKlass(
            parentKlass, parentScope, annotTypeDecl.getName());
        if (klasses != null) {
            klasses.add(klass);
        }
        DFTypeSpace child = klass.getKlassSpace();
        for (BodyDeclaration body :
                 (List<BodyDeclaration>) annotTypeDecl.bodyDeclarations()) {
            child.build(klasses, body, klass, klass.getKlassScope());
        }
    }

    public void build(
        List<DFKlass> klasses, BodyDeclaration body,
        DFKlass parentKlass, DFVarScope parentScope)
        throws UnsupportedSyntax {
        assert body != null;
        if (body instanceof AbstractTypeDeclaration) {
            this.build(klasses, (AbstractTypeDeclaration)body,
                       parentKlass, parentScope);
        } else if (body instanceof FieldDeclaration) {
            ;
        } else if (body instanceof MethodDeclaration) {
            ;
        } else if (body instanceof AnnotationTypeMemberDeclaration) {
            ;
        } else if (body instanceof Initializer) {
            ;
        } else {
            throw new UnsupportedSyntax(body);
        }
    }

    @SuppressWarnings("unchecked")
    public void build(
        List<DFKlass> klasses, Statement ast,
        DFKlass parentKlass, DFVarScope parentScope)
        throws UnsupportedSyntax {
        assert ast != null;

        if (ast instanceof AssertStatement) {

        } else if (ast instanceof Block) {
            Block block = (Block)ast;
            for (Statement stmt :
                     (List<Statement>) block.statements()) {
                this.build(klasses, stmt, parentKlass, parentScope);
            }

        } else if (ast instanceof EmptyStatement) {

        } else if (ast instanceof VariableDeclarationStatement) {

        } else if (ast instanceof ExpressionStatement) {

        } else if (ast instanceof ReturnStatement) {

        } else if (ast instanceof IfStatement) {
            IfStatement ifStmt = (IfStatement)ast;
            Statement thenStmt = ifStmt.getThenStatement();
            this.build(klasses, thenStmt, parentKlass, parentScope);
            Statement elseStmt = ifStmt.getElseStatement();
            if (elseStmt != null) {
                this.build(klasses, elseStmt, parentKlass, parentScope);
            }

        } else if (ast instanceof SwitchStatement) {
            SwitchStatement switchStmt = (SwitchStatement)ast;
            for (Statement stmt :
                     (List<Statement>) switchStmt.statements()) {
                this.build(klasses, stmt, parentKlass, parentScope);
            }

        } else if (ast instanceof SwitchCase) {

        } else if (ast instanceof WhileStatement) {
            WhileStatement whileStmt = (WhileStatement)ast;
            Statement stmt = whileStmt.getBody();
            this.build(klasses, stmt, parentKlass, parentScope);

        } else if (ast instanceof DoStatement) {
            DoStatement doStmt = (DoStatement)ast;
            Statement stmt = doStmt.getBody();
            this.build(klasses, stmt, parentKlass, parentScope);

        } else if (ast instanceof ForStatement) {
            ForStatement forStmt = (ForStatement)ast;
            Statement stmt = forStmt.getBody();
            this.build(klasses, stmt, parentKlass, parentScope);

        } else if (ast instanceof EnhancedForStatement) {
            EnhancedForStatement eForStmt = (EnhancedForStatement)ast;
            Statement stmt = eForStmt.getBody();
            this.build(klasses, stmt, parentKlass, parentScope);

        } else if (ast instanceof BreakStatement) {

        } else if (ast instanceof ContinueStatement) {

        } else if (ast instanceof LabeledStatement) {
            LabeledStatement labeledStmt = (LabeledStatement)ast;
            Statement stmt = labeledStmt.getBody();
            this.build(klasses, stmt, parentKlass, parentScope);

        } else if (ast instanceof SynchronizedStatement) {
            SynchronizedStatement syncStmt = (SynchronizedStatement)ast;
            Block block = syncStmt.getBody();
            this.build(klasses, block, parentKlass, parentScope);

        } else if (ast instanceof TryStatement) {
            TryStatement tryStmt = (TryStatement)ast;
            Block block = tryStmt.getBody();
            this.build(klasses, block, parentKlass, parentScope);
            for (CatchClause cc :
                     (List<CatchClause>) tryStmt.catchClauses()) {
                this.build(klasses, cc.getBody(), parentKlass, parentScope);
            }
            Block finBlock = tryStmt.getFinally();
            if (finBlock != null) {
                this.build(klasses, finBlock, parentKlass, parentScope);
            }

        } else if (ast instanceof ThrowStatement) {

        } else if (ast instanceof ConstructorInvocation) {

        } else if (ast instanceof SuperConstructorInvocation) {

        } else if (ast instanceof TypeDeclarationStatement) {
            TypeDeclarationStatement typeDeclStmt = (TypeDeclarationStatement)ast;
            this.build(klasses, typeDeclStmt.getDeclaration(),
                       parentKlass, parentScope);

        } else {
            throw new UnsupportedSyntax(ast);

        }
    }

    // dump: for debugging.
    public void dump() {
        dump(System.err, "");
    }
    public void dump(PrintStream out, String indent) {
        out.println(indent+this.getFullName()+" {");
        String i2 = indent + "  ";
        for (DFKlass klass : _id2klass.values()) {
            out.println(i2+"defined: "+klass);
        }
        for (DFTypeSpace space : _children) {
            space.dump(out, i2);
        }
        out.println(indent+"}");
    }
}
