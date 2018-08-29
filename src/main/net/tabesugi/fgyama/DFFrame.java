//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFFrame
//
public class DFFrame {

    private String _label;
    private DFFrame _parent;

    private Map<String, DFFrame> _ast2child =
        new HashMap<String, DFFrame>();
    private Set<DFVarRef> _inputs =
        new HashSet<DFVarRef>();
    private Set<DFVarRef> _outputs =
        new HashSet<DFVarRef>();
    private List<DFExit> _exits =
        new ArrayList<DFExit>();

    public static final String TRY = "@TRY";
    public static final String METHOD = "@METHOD";
    public static final String CLASS = "@CLASS";

    public DFFrame(String label) {
        this(label, null);
    }

    public DFFrame(String label, DFFrame parent) {
        _label = label;
        _parent = parent;
    }

    @Override
    public String toString() {
        return ("<DFFrame("+_label+")>");
    }

    public DFFrame addChild(String label, ASTNode ast) {
        DFFrame frame = new DFFrame(label, this);
        _ast2child.put(Utils.encodeASTNode(ast), frame);
        return frame;
    }

    public String getLabel() {
        return _label;
    }

    public DFFrame getParent() {
        return _parent;
    }

    public DFFrame getChildByAST(ASTNode ast) {
        String key = Utils.encodeASTNode(ast);
        assert(_ast2child.containsKey(key));
        return _ast2child.get(key);
    }

    public void addInput(DFVarRef ref) {
        _inputs.add(ref);
    }

    public void addOutput(DFVarRef ref) {
        _outputs.add(ref);
    }

    public DFFrame find(String label) {
        if (label == null) return this;
        DFFrame frame = this;
        while (frame.getParent() != null) {
            if (frame.getLabel() != null &&
                frame.getLabel().equals(label)) break;
            frame = frame.getParent();
        }
        return frame;
    }

    public DFVarRef[] getInputs() {
        DFVarRef[] refs = new DFVarRef[_inputs.size()];
        _inputs.toArray(refs);
        Arrays.sort(refs);
        return refs;
    }

    public DFVarRef[] getOutputs() {
        DFVarRef[] refs = new DFVarRef[_outputs.size()];
        _outputs.toArray(refs);
        Arrays.sort(refs);
        return refs;
    }

    public DFVarRef[] getInsAndOuts() {
        Set<DFVarRef> inouts = new HashSet<DFVarRef>(_inputs);
        inouts.retainAll(_outputs);
        DFVarRef[] refs = new DFVarRef[inouts.size()];
        inouts.toArray(refs);
        Arrays.sort(refs);
        return refs;
    }

    public DFExit[] getExits() {
        DFExit[] exits = new DFExit[_exits.size()];
        _exits.toArray(exits);
        return exits;
    }

    public void addExit(DFExit exit) {
        _exits.add(exit);
    }

    public void addAllExits(DFFrame frame, DFComponent cpt, boolean cont) {
        while (frame != this) {
            for (DFVarRef ref : frame.getOutputs()) {
                this.addExit(new DFExit(cpt.getCurrent(ref), cont));
            }
            frame = frame.getParent();
        }
    }

    public void finish(DFComponent cpt) {
        for (DFExit exit : _exits) {
            DFNode node = exit.getNode();
            node.finish(cpt);
            cpt.setCurrent(node);
        }
    }

    @SuppressWarnings("unchecked")
    public void build(DFVarSpace varSpace, Statement ast)
        throws UnsupportedSyntax {

        if (ast instanceof AssertStatement) {

        } else if (ast instanceof Block) {
            DFVarSpace childSpace = varSpace.getChildByAST(ast);
            Block block = (Block)ast;
            for (Statement stmt :
                     (List<Statement>) block.statements()) {
                this.build(childSpace, stmt);
            }

        } else if (ast instanceof EmptyStatement) {

        } else if (ast instanceof VariableDeclarationStatement) {

        } else if (ast instanceof ExpressionStatement) {

        } else if (ast instanceof ReturnStatement) {

        } else if (ast instanceof IfStatement) {
            IfStatement ifStmt = (IfStatement)ast;
            Statement thenStmt = ifStmt.getThenStatement();
            DFFrame thenFrame = this.addChild(null, thenStmt);
            thenFrame.build(varSpace, thenStmt);
            Statement elseStmt = ifStmt.getElseStatement();
            if (elseStmt != null) {
                DFFrame elseFrame = this.addChild(null, elseStmt);
                elseFrame.build(varSpace, elseStmt);
            }

        } else if (ast instanceof SwitchStatement) {
            DFVarSpace childSpace = varSpace.getChildByAST(ast);
            SwitchStatement switchStmt = (SwitchStatement)ast;
            DFFrame childFrame = this.addChild(null, ast);
            for (Statement stmt :
                     (List<Statement>) switchStmt.statements()) {
                childFrame.build(childSpace, stmt);
            }

        } else if (ast instanceof SwitchCase) {

        } else if (ast instanceof WhileStatement) {
            DFVarSpace childSpace = varSpace.getChildByAST(ast);
            WhileStatement whileStmt = (WhileStatement)ast;
            DFFrame childFrame = this.addChild(null, ast);
            Statement stmt = whileStmt.getBody();
            childFrame.build(childSpace, stmt);

        } else if (ast instanceof DoStatement) {
            DFVarSpace childSpace = varSpace.getChildByAST(ast);
            DoStatement doStmt = (DoStatement)ast;
            DFFrame childFrame = this.addChild(null, ast);
            Statement stmt = doStmt.getBody();
            childFrame.build(childSpace, stmt);

        } else if (ast instanceof ForStatement) {
            DFVarSpace childSpace = varSpace.getChildByAST(ast);
            ForStatement forStmt = (ForStatement)ast;
            DFFrame childFrame = this.addChild(null, ast);
            Statement stmt = forStmt.getBody();
            childFrame.build(childSpace, stmt);

        } else if (ast instanceof EnhancedForStatement) {
            DFVarSpace childSpace = varSpace.getChildByAST(ast);
            EnhancedForStatement eForStmt = (EnhancedForStatement)ast;
            DFFrame childFrame = this.addChild(null, ast);
            Statement stmt = eForStmt.getBody();
            childFrame.build(childSpace, stmt);

        } else if (ast instanceof BreakStatement) {

        } else if (ast instanceof ContinueStatement) {

        } else if (ast instanceof LabeledStatement) {
            LabeledStatement labeledStmt = (LabeledStatement)ast;
            SimpleName labelName = labeledStmt.getLabel();
            String label = labelName.getIdentifier();
            DFFrame childFrame = this.addChild(label, ast);
            Statement stmt = labeledStmt.getBody();
            childFrame.build(varSpace, stmt);

        } else if (ast instanceof SynchronizedStatement) {
            SynchronizedStatement syncStmt = (SynchronizedStatement)ast;
            Block block = syncStmt.getBody();
            this.build(varSpace, block);

        } else if (ast instanceof TryStatement) {
            TryStatement tryStmt = (TryStatement)ast;
            Block block = tryStmt.getBody();
            DFFrame tryFrame = this.addChild(DFFrame.TRY, ast);
            tryFrame.build(varSpace, block);
            for (CatchClause cc :
                     (List<CatchClause>) tryStmt.catchClauses()) {
                DFVarSpace childSpace = varSpace.getChildByAST(cc);
                this.build(childSpace, cc.getBody());
            }
            Block finBlock = tryStmt.getFinally();
            if (finBlock != null) {
                this.build(varSpace, finBlock);
            }

        } else if (ast instanceof ThrowStatement) {

        } else if (ast instanceof ConstructorInvocation) {

        } else if (ast instanceof SuperConstructorInvocation) {

        } else if (ast instanceof TypeDeclarationStatement) {

        } else {
            throw new UnsupportedSyntax(ast);

        }
    }

    // dump: for debugging.
    public void dump() {
        dump(System.err, "");
    }
    public void dump(PrintStream out, String indent) {
        out.println(indent+_label+" {");
        String i2 = indent + "  ";
        StringBuilder inputs = new StringBuilder();
        for (DFVarRef ref : _inputs) {
            inputs.append(" "+ref);
        }
        out.println(i2+"inputs:"+inputs);
        StringBuilder outputs = new StringBuilder();
        for (DFVarRef ref : _outputs) {
            outputs.append(" "+ref);
        }
        out.println(i2+"outputs:"+outputs);
        StringBuilder inouts = new StringBuilder();
        for (DFVarRef ref : this.getInsAndOuts()) {
            inouts.append(" "+ref);
        }
        out.println(i2+"in/outs:"+inouts);
        for (DFFrame frame : _ast2child.values()) {
            frame.dump(out, i2);
        }
        out.println(indent+"}");
    }
}
