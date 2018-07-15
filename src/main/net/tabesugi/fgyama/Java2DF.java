/**
 * Java2DF
 * Dataflow analyzer for Java
 */
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.apache.bcel.*;
import org.apache.bcel.classfile.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;
import org.w3c.dom.*;


// ProgNode: a DFNode that corresponds to an actual program point.
abstract class ProgNode extends DFNode {

    public ASTNode ast;

    public ProgNode(
        DFGraph graph, DFVarSpace space, DFType type, DFVarRef ref,
        ASTNode ast) {
        super(graph, space, type, ref);
        this.ast = ast;
    }

    @Override
    public Element toXML(Document document) {
        Element elem = super.toXML(document);
        if (this.ast != null) {
            Element east = document.createElement("ast");
            east.setAttribute("type", Integer.toString(this.ast.getNodeType()));
            east.setAttribute("start", Integer.toString(this.ast.getStartPosition()));
            east.setAttribute("length", Integer.toString(this.ast.getLength()));
            elem.appendChild(east);
        }
        return elem;
    }
}

// SingleAssignNode:
class SingleAssignNode extends ProgNode {

    public SingleAssignNode(
        DFGraph graph, DFVarSpace space, DFVarRef ref,
        ASTNode ast) {
        super(graph, space, ref.getType(), ref, ast);
    }

    @Override
    public String getKind() {
        return "assign";
    }
}

// ArrayAssignNode:
class ArrayAssignNode extends ProgNode {

    public ArrayAssignNode(
        DFGraph graph, DFVarSpace space, DFVarRef ref,
        ASTNode ast, DFNode array, DFNode index) {
        super(graph, space, null, ref, ast);
        this.accept(array, "array");
        this.accept(index, "index");
    }

    @Override
    public String getKind() {
        return "arrayassign";
    }
}

// FieldAssignNode:
class FieldAssignNode extends ProgNode {

    public FieldAssignNode(
        DFGraph graph, DFVarSpace space, DFVarRef ref,
        ASTNode ast, DFNode obj) {
        super(graph, space, null, ref, ast);
        if (obj != null) {
            this.accept(obj, "obj");
        }
    }

    @Override
    public String getKind() {
        return "fieldassign";
    }
}

// VarRefNode: represnets a variable reference.
class VarRefNode extends ProgNode {

    public VarRefNode(
        DFGraph graph, DFVarSpace space, DFVarRef ref,
        ASTNode ast) {
        super(graph, space, ref.getType(), ref, ast);
    }

    @Override
    public String getKind() {
        return "ref";
    }
}

// ArrayAccessNode
class ArrayAccessNode extends ProgNode {

    public ArrayAccessNode(
        DFGraph graph, DFVarSpace space, DFVarRef ref,
        ASTNode ast, DFNode array, DFNode index) {
        super(graph, space, null, ref, ast);
        this.accept(array, "array");
        this.accept(index, "index");
    }

    @Override
    public String getKind() {
        return "arrayaccess";
    }
}

// FieldAccessNode
class FieldAccessNode extends ProgNode {

    public FieldAccessNode(
        DFGraph graph, DFVarSpace space, DFVarRef ref,
        ASTNode ast, DFNode obj) {
        super(graph, space, null, ref, ast);
        if (obj != null) {
            this.accept(obj, "obj");
        }
    }

    @Override
    public String getKind() {
        return "fieldaccess";
    }
}

// PrefixNode
class PrefixNode extends ProgNode {

    public PrefixExpression.Operator op;

    public PrefixNode(
        DFGraph graph, DFVarSpace space, DFVarRef ref,
        ASTNode ast, PrefixExpression.Operator op) {
        super(graph, space, null, ref, ast);
        this.op = op;
    }

    @Override
    public String getKind() {
        return "prefix";
    }

    @Override
    public String getData() {
        return this.op.toString();
    }
}

// PostfixNode
class PostfixNode extends ProgNode {

    public PostfixExpression.Operator op;

    public PostfixNode(
        DFGraph graph, DFVarSpace space, DFVarRef ref,
        ASTNode ast, PostfixExpression.Operator op) {
        super(graph, space, null, ref, ast);
        this.op = op;
    }

    @Override
    public String getKind() {
        return "postfix";
    }

    @Override
    public String getData() {
        return this.op.toString();
    }
}

// InfixNode
class InfixNode extends ProgNode {

    public InfixExpression.Operator op;

    public InfixNode(
        DFGraph graph, DFVarSpace space, DFType type,
        ASTNode ast, InfixExpression.Operator op,
        DFNode lvalue, DFNode rvalue) {
        super(graph, space, type, null, ast);
        this.op = op;
        this.accept(lvalue, "L");
        this.accept(rvalue, "R");
    }

    @Override
    public String getKind() {
        return "infix";
    }

    @Override
    public String getData() {
        return this.op.toString();
    }
}

// TypeCastNode
class TypeCastNode extends ProgNode {

    public TypeCastNode(
        DFGraph graph, DFVarSpace space, DFType type,
        ASTNode ast) {
        super(graph, space, type, null, ast);
        assert(type != null);
    }

    @Override
    public String getKind() {
        return "typecast";
    }

    @Override
    public String getData() {
        return this.getType().getName();
    }
}

// InstanceofNode
class InstanceofNode extends ProgNode {

    public DFType type;

    public InstanceofNode(
        DFGraph graph, DFVarSpace space,
        ASTNode ast, DFType type) {
        super(graph, space, DFBasicType.BOOLEAN, null, ast);
        assert(type != null);
        this.type = type;
    }

    @Override
    public String getKind() {
        return "instanceof";
    }

    @Override
    public String getData() {
        return this.getType().getName();
    }
}

// CaseNode
class CaseNode extends ProgNode {

    public List<DFNode> matches = new ArrayList<DFNode>();

    public CaseNode(
        DFGraph graph, DFVarSpace space,
        ASTNode ast) {
        super(graph, space, null, null, ast);
    }

    @Override
    public String getKind() {
        return "case";
    }

    @Override
    public String getData() {
        if (this.matches.isEmpty()) {
            return "default";
        } else {
            return "case("+this.matches.size()+")";
        }
    }

    public void addMatch(DFNode node) {
        String label = "match"+this.matches.size();
        this.accept(node, label);
        this.matches.add(node);
    }
}

// AssignOpNode
class AssignOpNode extends ProgNode {

    public Assignment.Operator op;

    public AssignOpNode(
        DFGraph graph, DFVarSpace space, DFVarRef ref,
        ASTNode ast, Assignment.Operator op,
        DFNode lvalue, DFNode rvalue) {
        super(graph, space, rvalue.getType(), ref, ast);
        this.op = op;
        if (lvalue != null) {
            this.accept(lvalue, "L");
        }
        this.accept(rvalue, "R");
    }

    @Override
    public String getKind() {
        return "assignop";
    }

    @Override
    public String getData() {
        return this.op.toString();
    }
}

// ArgNode: represnets a function argument.
class ArgNode extends ProgNode {

    public int index;

    public ArgNode(
        DFGraph graph, DFVarSpace space, DFVarRef ref,
        ASTNode ast, int index) {
        super(graph, space, ref.getType(), ref, ast);
        this.index = index;
    }

    @Override
    public String getKind() {
        return "arg";
    }

    @Override
    public String getData() {
        return ("arg"+this.index);
    }
}

// ConstNode: represents a constant value.
class ConstNode extends ProgNode {

    public String data;

    public ConstNode(
        DFGraph graph, DFVarSpace space, DFType type,
        ASTNode ast, String data) {
        super(graph, space, type, null, ast);
        this.data = data;
    }

    @Override
    public String getKind() {
        return "const";
    }

    @Override
    public String getData() {
        return this.data;
    }
}

// ArrayValueNode: represents an array.
class ArrayValueNode extends ProgNode {

    public List<DFNode> values = new ArrayList<DFNode>();

    public ArrayValueNode(
        DFGraph graph, DFVarSpace space,
        ASTNode ast) {
        super(graph, space, null, null, ast);
    }

    @Override
    public String getKind() {
        return "arrayvalue";
    }

    @Override
    public String getData() {
        return Integer.toString(this.values.size());
    }

    public void addValue(DFNode value) {
        String label = "value"+this.values.size();
        this.accept(value, label);
        this.values.add(value);
    }
}

// JoinNode
class JoinNode extends ProgNode {

    public boolean recvTrue = false;
    public boolean recvFalse = false;

    public JoinNode(
        DFGraph graph, DFVarSpace space, DFVarRef ref,
        ASTNode ast, DFNode cond) {
        super(graph, space, null, ref, ast);
        this.accept(cond, "cond");
    }

    @Override
    public String getKind() {
        return "join";
    }

    @Override
    public void finish(DFComponent cpt) {
        if (!this.isClosed()) {
            this.close(cpt.getValue(this.getRef()));
        }
    }

    public void recv(boolean cond, DFNode node) {
        if (cond) {
            assert(!this.recvTrue);
            this.recvTrue = true;
            this.accept(node, "true");
        } else {
            assert(!this.recvFalse);
            this.recvFalse = true;
            this.accept(node, "false");
        }
    }

    public boolean isClosed() {
        return (this.recvTrue && this.recvFalse);
    };

    public void close(DFNode node) {
        if (!this.recvTrue) {
            assert(this.recvFalse);
            this.recvTrue = true;
            this.accept(node, "true");
        }
        if (!this.recvFalse) {
            assert(this.recvTrue);
            this.recvFalse = true;
            this.accept(node, "false");
        }
    }
}

// LoopBeginNode
class LoopBeginNode extends ProgNode {

    public LoopBeginNode(
        DFGraph graph, DFVarSpace space, DFVarRef ref,
        ASTNode ast, DFNode enter) {
        super(graph, space, null, ref, ast);
        this.accept(enter, "enter");
    }

    @Override
    public String getKind() {
        return "begin";
    }

    public void setRepeat(DFNode repeat) {
        this.accept(repeat, "repeat");
    }

    public void setEnd(LoopEndNode end) {
        this.accept(end, "_end");
    }
}

// LoopEndNode
class LoopEndNode extends ProgNode {

    public LoopEndNode(
        DFGraph graph, DFVarSpace space, DFVarRef ref,
        ASTNode ast, DFNode cond) {
        super(graph, space, null, ref, ast);
        this.accept(cond, "cond");
    }

    @Override
    public String getKind() {
        return "end";
    }

    public void setBegin(LoopBeginNode begin) {
        this.accept(begin, "_begin");
    }
}

// LoopRepeatNode
class LoopRepeatNode extends ProgNode {

    public LoopRepeatNode(
        DFGraph graph, DFVarSpace space, DFVarRef ref,
        ASTNode ast) {
        super(graph, space, null, ref, ast);
    }

    @Override
    public String getKind() {
        return "repeat";
    }

    public void setLoop(DFNode end) {
        this.accept(end, "_loop");
    }
}

// IterNode
class IterNode extends ProgNode {

    public IterNode(
        DFGraph graph, DFVarSpace space, DFVarRef ref,
        ASTNode ast) {
        super(graph, space, null, ref, ast);
    }

    @Override
    public String getKind() {
        return "iter";
    }
}

// CallNode
abstract class CallNode extends ProgNode {

    public DFNode[] args;
    public DFNode exception;

    public CallNode(
        DFGraph graph, DFVarSpace space, DFType type, DFVarRef ref,
        ASTNode ast) {
        super(graph, space, type, ref, ast);
        this.args = null;
        this.exception = null;
    }

    @Override
    public String getKind() {
        return "call";
    }

    public void setArgs(DFNode[] args) {
        for (int i = 0; i < args.length; i++) {
            String label = "arg"+i;
            this.accept(args[i], label);
        }
        this.args = args;
    }
}

// MethodCallNode
class MethodCallNode extends CallNode {

    public DFMethod[] methods;

    public MethodCallNode(
        DFGraph graph, DFVarSpace space, DFMethod[] methods,
        ASTNode ast, DFNode obj) {
        super(graph, space, methods[0].getReturnType(), null, ast);
        if (obj != null) {
            this.accept(obj, "obj");
        }
        this.methods = methods;
    }

    @Override
    public String getData() {
        int n = 0;
        String data = "";
        for (DFMethod method : this.methods) {
            if (0 < n) {
                data += " ";
            }
            data += method.getSignature();
            n++;
        }
        return data;
    }
}

// CreateObjectNode
class CreateObjectNode extends CallNode {

    public CreateObjectNode(
        DFGraph graph, DFVarSpace space, DFType type,
        ASTNode ast, DFNode obj) {
        super(graph, space, type, null, ast);
        assert(type != null);
        if (obj != null) {
            this.accept(obj, "obj");
        }
    }

    @Override
    public String getKind() {
        return "new";
    }

    @Override
    public String getData() {
        return this.getType().getName();
    }
}

// ReturnNode: represents a return value.
class ReturnNode extends ProgNode {

    public ReturnNode(
        DFGraph graph, DFVarSpace space,
        ASTNode ast) throws VariableNotFound {
        super(graph, space, null, space.lookupReturn(), ast);
    }

    @Override
    public String getKind() {
        return "return";
    }
}

// ExceptionNode
class ExceptionNode extends ProgNode {

    public ExceptionNode(
        DFGraph graph, DFVarSpace space,
        ASTNode ast, DFNode value) throws VariableNotFound {
        super(graph, space, null, space.lookupException(), ast);
        this.accept(value);
    }

    @Override
    public String getKind() {
        return "exception";
    }
}


//  Java2DF
//
public class Java2DF {

    /// General graph operations.

    /**
     * Creates a value node.
     */
    @SuppressWarnings("unchecked")
    public DFComponent processExpression(
        DFGraph graph, DFTypeFinder finder, DFVarSpace varSpace,
        DFFrame frame, DFComponent cpt, Expression expr)
        throws UnsupportedSyntax, EntityNotFound {
        try {
            if (expr instanceof Annotation) {

            } else if (expr instanceof Name) {
                Name name = (Name)expr;
                if (name.isSimpleName()) {
                    DFVarRef ref = varSpace.lookupVarOrField((SimpleName)name);
                    DFNode node = new VarRefNode(graph, varSpace, ref, expr);
                    node.accept(cpt.getValue(ref));
                    cpt.setRValue(node);
                    frame.addInput(ref);
                } else {
                    QualifiedName qname = (QualifiedName)name;
                    DFNode obj = null;
                    DFClassSpace klass;
                    try {
                        // Try assuming it's a variable access.
                        cpt = processExpression(
                            graph, finder, varSpace, frame, cpt, qname.getQualifier());
                        obj = cpt.getRValue();
                        klass = finder.resolveClass(obj.getType());
                    } catch (EntityNotFound e) {
                        // Turned out it's a class variable.
                        klass = finder.lookupClass(qname.getQualifier());
                    }
                    SimpleName fieldName = qname.getName();
                    DFVarRef ref = klass.lookupField(fieldName);
                    DFNode node = new FieldAccessNode(graph, varSpace, ref, qname, obj);
                    node.accept(cpt.getValue(ref));
                    cpt.setRValue(node);
                    frame.addInput(ref);
                }

            } else if (expr instanceof ThisExpression) {
                DFVarRef ref = varSpace.lookupThis();
                DFNode node = new VarRefNode(graph, varSpace, ref, expr);
                node.accept(cpt.getValue(ref));
                cpt.setRValue(node);
                frame.addInput(ref);

            } else if (expr instanceof BooleanLiteral) {
                boolean value = ((BooleanLiteral)expr).booleanValue();
                cpt.setRValue(new ConstNode(
                                  graph, varSpace, DFBasicType.BOOLEAN,
                                  expr, Boolean.toString(value)));

            } else if (expr instanceof CharacterLiteral) {
                char value = ((CharacterLiteral)expr).charValue();
                cpt.setRValue(new ConstNode(
                                  graph, varSpace, DFBasicType.CHAR,
                                  expr, Utils.quote(value)));

            } else if (expr instanceof NullLiteral) {
                cpt.setRValue(new ConstNode(
                                  graph, varSpace, DFNullType.NULL,
                                  expr, "null"));

            } else if (expr instanceof NumberLiteral) {
                String value = ((NumberLiteral)expr).getToken();
                cpt.setRValue(new ConstNode(
                                  graph, varSpace, DFBasicType.NUMBER,
                                  expr, value));

            } else if (expr instanceof StringLiteral) {
                String value = ((StringLiteral)expr).getLiteralValue();
                cpt.setRValue(new ConstNode(
                                  graph, varSpace,
                                  new DFClassType(DFRootTypeSpace.STRING_CLASS),
                                  expr, Utils.quote(value)));

            } else if (expr instanceof TypeLiteral) {
                Type value = ((TypeLiteral)expr).getType();
                cpt.setRValue(new ConstNode(
                                  graph, varSpace, DFBasicType.TYPE,
                                  expr, Utils.getTypeName(value)));

            } else if (expr instanceof PrefixExpression) {
                PrefixExpression prefix = (PrefixExpression)expr;
                PrefixExpression.Operator op = prefix.getOperator();
                Expression operand = prefix.getOperand();
                cpt = processExpression(
                    graph, finder, varSpace, frame, cpt, operand);
                if (op == PrefixExpression.Operator.INCREMENT ||
                    op == PrefixExpression.Operator.DECREMENT) {
                    cpt = processAssignment(
                        graph, finder, varSpace, frame, cpt, operand);
                    DFNode assign = cpt.getLValue();
                    DFNode value = new PrefixNode(
                        graph, varSpace, assign.getRef(),
                        expr, op);
                    value.accept(cpt.getRValue());
                    assign.accept(value);
                    cpt.setOutput(assign);
                    cpt.setRValue(value);
                    frame.addInput(assign.getRef());
                } else {
                    DFNode value = new PrefixNode(
                        graph, varSpace, null,
                        expr, op);
                    value.accept(cpt.getRValue());
                    cpt.setRValue(value);
                }

            } else if (expr instanceof PostfixExpression) {
                PostfixExpression postfix = (PostfixExpression)expr;
                PostfixExpression.Operator op = postfix.getOperator();
                Expression operand = postfix.getOperand();
                cpt = processAssignment(
                    graph, finder, varSpace, frame, cpt, operand);
                if (op == PostfixExpression.Operator.INCREMENT ||
                    op == PostfixExpression.Operator.DECREMENT) {
                    DFNode assign = cpt.getLValue();
                    cpt = processExpression(
                        graph, finder, varSpace, frame, cpt, operand);
                    DFNode node = new PostfixNode(
                        graph, varSpace, assign.getRef(), expr, op);
                    node.accept(cpt.getRValue());
                    assign.accept(node);
                    cpt.setOutput(assign);
                    frame.addInput(assign.getRef());
                }

            } else if (expr instanceof InfixExpression) {
                InfixExpression infix = (InfixExpression)expr;
                InfixExpression.Operator op = infix.getOperator();
                cpt = processExpression(
                    graph, finder, varSpace, frame, cpt,
                    infix.getLeftOperand());
                DFNode lvalue = cpt.getRValue();
                cpt = processExpression(
                    graph, finder, varSpace, frame, cpt,
                    infix.getRightOperand());
                DFNode rvalue = cpt.getRValue();
                DFType type = lvalue.getType(); // XXX Todo: implicit type coersion.
                cpt.setRValue(new InfixNode(
                                  graph, varSpace, type, expr, op, lvalue, rvalue));

            } else if (expr instanceof ParenthesizedExpression) {
                ParenthesizedExpression paren = (ParenthesizedExpression)expr;
                cpt = processExpression(
                    graph, finder, varSpace, frame, cpt,
                    paren.getExpression());

            } else if (expr instanceof Assignment) {
                Assignment assn = (Assignment)expr;
                Assignment.Operator op = assn.getOperator();
                cpt = processAssignment(
                    graph, finder, varSpace, frame, cpt,
                    assn.getLeftHandSide());
                DFNode assign = cpt.getLValue();
                cpt = processExpression(
                    graph, finder, varSpace, frame, cpt,
                    assn.getRightHandSide());
                DFNode rvalue = cpt.getRValue();
                DFNode lvalue = ((op == Assignment.Operator.ASSIGN)?
                                 null : cpt.getValue(assign.getRef()));
                assign.accept(new AssignOpNode(
                                  graph, varSpace, assign.getRef(), assn,
                                  op, lvalue, rvalue));
                cpt.setOutput(assign);
                cpt.setRValue(assign);
                frame.addInput(assign.getRef());

            } else if (expr instanceof VariableDeclarationExpression) {
                VariableDeclarationExpression decl = (VariableDeclarationExpression)expr;
                cpt = processVariableDeclaration(
                    graph, finder, varSpace, frame, cpt,
                    decl.fragments());

            } else if (expr instanceof MethodInvocation) {
                MethodInvocation invoke = (MethodInvocation)expr;
                Expression expr1 = invoke.getExpression();
                DFNode obj = null;
                DFClassSpace klass = null;
                if (expr1 == null) {
                    obj = cpt.getValue(varSpace.lookupThis());
                } else {
                    if (expr1 instanceof Name) {
                        try {
                            klass = finder.lookupClass((Name)expr1);
                        } catch (EntityNotFound e) {
                        }
                    }
                    if (klass == null) {
                        cpt = processExpression(
                            graph, finder, varSpace, frame, cpt, expr1);
                        obj = cpt.getRValue();
                    }
                }
                if (obj != null) {
                    klass = finder.resolveClass(obj.getType());
                }
                List<DFNode> argList = new ArrayList<DFNode>();
                List<DFType> typeList = new ArrayList<DFType>();
                for (Expression arg : (List<Expression>) invoke.arguments()) {
                    cpt = processExpression(
                        graph, finder, varSpace, frame, cpt, arg);
                    DFNode node = cpt.getRValue();
                    argList.add(node);
                    typeList.add(node.getType());
                }
                DFNode[] args = new DFNode[argList.size()];
                argList.toArray(args);
                DFType[] argTypes = new DFType[typeList.size()];
                typeList.toArray(argTypes);
                DFMethod[] methods = klass.lookupMethods(invoke.getName(), argTypes);
                if (methods == null) {
                    String id = invoke.getName().getIdentifier();
                    DFMethod fallback = new DFMethod(klass, null, id, false, null, null);
                    Logger.error("Fallback method: "+klass+": "+fallback);
                    methods = new DFMethod[] { fallback };
                }
                MethodCallNode call = new MethodCallNode(
                    graph, varSpace, methods, invoke, obj);
                call.setArgs(args);
                cpt.setRValue(call);
                if (call.exception != null) {
                    DFFrame dstFrame = frame.find(DFFrame.TRY);
                    cpt.addExit(new DFExit(call.exception, dstFrame));
                }

            } else if (expr instanceof SuperMethodInvocation) {
                SuperMethodInvocation sinvoke = (SuperMethodInvocation)expr;
                DFNode obj = cpt.getValue(varSpace.lookupThis());
                List<DFNode> argList = new ArrayList<DFNode>();
                List<DFType> typeList = new ArrayList<DFType>();
                for (Expression arg : (List<Expression>) sinvoke.arguments()) {
                    cpt = processExpression(
                        graph, finder, varSpace, frame, cpt, arg);
                    DFNode node = cpt.getRValue();
                    argList.add(node);
                    typeList.add(node.getType());
                }
                DFNode[] args = new DFNode[argList.size()];
                argList.toArray(args);
                DFType[] argTypes = new DFType[typeList.size()];
                typeList.toArray(argTypes);
                DFClassSpace klass = finder.resolveClass(obj.getType());
                DFClassSpace baseKlass = klass.getBase();
                DFMethod[] methods = baseKlass.lookupMethods(sinvoke.getName(), argTypes);
                if (methods == null) {
                    String id = sinvoke.getName().getIdentifier();
                    DFMethod fallback = new DFMethod(baseKlass, null, id, false, null, null);
                    Logger.error("Fallback method: "+baseKlass+": "+fallback);
                    methods = new DFMethod[] { fallback };
                }
                MethodCallNode call = new MethodCallNode(
                    graph, varSpace, methods, sinvoke, obj);
                call.setArgs(args);
                cpt.setRValue(call);
                if (call.exception != null) {
                    DFFrame dstFrame = frame.find(DFFrame.TRY);
                    cpt.addExit(new DFExit(call.exception, dstFrame));
                }

            } else if (expr instanceof ArrayCreation) {
                ArrayCreation ac = (ArrayCreation)expr;
                for (Expression dim : (List<Expression>) ac.dimensions()) {
                    // XXX cpt.getRValue() is not used (for now).
                    cpt = processExpression(
                        graph, finder, varSpace, frame, cpt, dim);
                }
                ArrayInitializer init = ac.getInitializer();
                if (init != null) {
                    cpt = processExpression(
                        graph, finder, varSpace, frame, cpt, init);
                } else {
                    cpt.setRValue(new ArrayValueNode(graph, varSpace, ac));
                }

            } else if (expr instanceof ArrayInitializer) {
                ArrayInitializer init = (ArrayInitializer)expr;
                ArrayValueNode arr = new ArrayValueNode(graph, varSpace, init);
                for (Expression expr1 : (List<Expression>) init.expressions()) {
                    cpt = processExpression(
                        graph, finder, varSpace, frame, cpt, expr1);
                    arr.addValue(cpt.getRValue());
                }
                cpt.setRValue(arr);
                // XXX array ref is not used.

            } else if (expr instanceof ArrayAccess) {
                ArrayAccess aa = (ArrayAccess)expr;
                cpt = processExpression(
                    graph, finder, varSpace, frame, cpt, aa.getArray());
                DFNode array = cpt.getRValue();
                cpt = processExpression(
                    graph, finder, varSpace, frame, cpt, aa.getIndex());
                DFVarRef ref = this.rootSpace.getArrayRef(array.getType());
                DFNode index = cpt.getRValue();
                DFNode node = new ArrayAccessNode(
                    graph, varSpace, ref, aa, array, index);
                node.accept(cpt.getValue(ref));
                cpt.setRValue(node);
                frame.addInput(ref);

            } else if (expr instanceof FieldAccess) {
                FieldAccess fa = (FieldAccess)expr;
                Expression expr1 = fa.getExpression();
                DFNode obj = null;
                DFClassSpace klass = null;
                if (expr1 instanceof Name) {
                    try {
                        klass = finder.lookupClass((Name)expr1);
                    } catch (EntityNotFound e) {
                    }
                }
                if (klass == null) {
                    cpt = processExpression(
                        graph, finder, varSpace, frame, cpt, expr1);
                    obj = cpt.getRValue();
                    klass = finder.resolveClass(obj.getType());
                }
                SimpleName fieldName = fa.getName();
                DFVarRef ref = klass.lookupField(fieldName);
                DFNode node = new FieldAccessNode(graph, varSpace, ref, fa, obj);
                node.accept(cpt.getValue(ref));
                cpt.setRValue(node);
                frame.addInput(ref);

            } else if (expr instanceof SuperFieldAccess) {
                SuperFieldAccess sfa = (SuperFieldAccess)expr;
                SimpleName fieldName = sfa.getName();
                DFNode obj = cpt.getValue(varSpace.lookupThis());
                DFClassSpace klass = finder.resolveClass(obj.getType());
                DFVarRef ref = klass.lookupField(fieldName);
                DFNode node = new FieldAccessNode(graph, varSpace, ref, sfa, obj);
                node.accept(cpt.getValue(ref));
                cpt.setRValue(node);
                frame.addInput(ref);

            } else if (expr instanceof CastExpression) {
                CastExpression cast = (CastExpression)expr;
                DFType type = finder.resolve(cast.getType());
                cpt = processExpression(
                    graph, finder, varSpace, frame, cpt, cast.getExpression());
                DFNode node = new TypeCastNode(graph, varSpace, type, cast);
                node.accept(cpt.getRValue());
                cpt.setRValue(node);

            } else if (expr instanceof ClassInstanceCreation) {
                ClassInstanceCreation cstr = (ClassInstanceCreation)expr;
                AnonymousClassDeclaration anonDecl = cstr.getAnonymousClassDeclaration();
                DFType instType;
                if (anonDecl != null) {
                    String id = "anonymous";
                    DFClassSpace baseKlass = finder.resolveClass(cstr.getType());
                    DFTypeSpace anonSpace = new DFTypeSpace(varSpace.getFullName()+"/"+id);
                    DFClassSpace anonKlass = new DFAnonClassSpace(
			anonSpace, varSpace, id, baseKlass);
                    anonSpace.addClass(anonKlass);
                    for (BodyDeclaration body :
                             (List<BodyDeclaration>) anonDecl.bodyDeclarations()) {
                        anonSpace.build(null, body, varSpace);
                    }
                    try {
                        for (BodyDeclaration body :
                                 (List<BodyDeclaration>) anonDecl.bodyDeclarations()) {
                            anonKlass.build(finder, body);
                        }
                        anonKlass.addOverrides();
                        processBodyDeclarations(
                            finder, anonKlass, anonDecl.bodyDeclarations());
                        instType = new DFClassType(anonKlass);
                    } catch (EntityNotFound e) {
                        instType = null; // XXX what happened?
                    }
                } else {
                    instType = finder.resolve(cstr.getType());
                }
                Expression expr1 = cstr.getExpression();
                DFNode obj = null;
                if (expr1 != null) {
                    cpt = processExpression(
                        graph, finder, varSpace, frame, cpt, expr1);
                    obj = cpt.getRValue();
                }
                List<DFNode> argList = new ArrayList<DFNode>();
                for (Expression arg : (List<Expression>) cstr.arguments()) {
                    cpt = processExpression(
                        graph, finder, varSpace, frame, cpt, arg);
                    argList.add(cpt.getRValue());
                }
                DFNode[] args = new DFNode[argList.size()];
                argList.toArray(args);
                CreateObjectNode call = new CreateObjectNode(
                    graph, varSpace, instType, cstr, obj);
                call.setArgs(args);
                cpt.setRValue(call);

            } else if (expr instanceof ConditionalExpression) {
                ConditionalExpression cond = (ConditionalExpression)expr;
                cpt = processExpression(
                    graph, finder, varSpace, frame, cpt, cond.getExpression());
                DFNode condValue = cpt.getRValue();
                cpt = processExpression(
                    graph, finder, varSpace, frame, cpt, cond.getThenExpression());
                DFNode trueValue = cpt.getRValue();
                cpt = processExpression(
                    graph, finder, varSpace, frame, cpt, cond.getElseExpression());
                DFNode falseValue = cpt.getRValue();
                JoinNode join = new JoinNode(graph, varSpace, null, expr, condValue);
                join.recv(true, trueValue);
                join.recv(false, falseValue);
                cpt.setRValue(join);

            } else if (expr instanceof InstanceofExpression) {
                InstanceofExpression instof = (InstanceofExpression)expr;
                DFType type = finder.resolve(instof.getRightOperand());
                cpt = processExpression(
                    graph, finder, varSpace, frame, cpt,
                    instof.getLeftOperand());
                DFNode node = new InstanceofNode(graph, varSpace, instof, type);
                node.accept(cpt.getRValue());
                cpt.setRValue(node);

            } else if (expr instanceof LambdaExpression) {
                LambdaExpression lambda = (LambdaExpression)expr;
                String id = "lambda";
                ASTNode body = lambda.getBody();
                DFTypeSpace anonSpace = new DFTypeSpace(varSpace.getFullName()+"/"+id);
                DFClassSpace anonKlass = new DFAnonClassSpace(
		    anonSpace, varSpace, id, null);
                if (body instanceof Statement) {
                    // XXX TODO Statement lambda
                } else if (body instanceof Expression) {
                    // XXX TODO Expresssion lambda
                } else {
                    throw new UnsupportedSyntax(body);
                }
                DFType instType = new DFClassType(anonKlass);
                CreateObjectNode call = new CreateObjectNode(
                    graph, varSpace, instType, lambda, null);
                cpt.setRValue(call);

            } else if (expr instanceof MethodReference) {
                // MethodReference
                //  CreationReference
                //  ExpressionMethodReference
                //  SuperMethodReference
                //  TypeMethodReference
                MethodReference mref = (MethodReference)expr;
                DFTypeSpace anonSpace = new DFTypeSpace("MethodRef");
                DFClassSpace anonKlass = new DFAnonClassSpace(
		    anonSpace, varSpace, "methodref", null);
                // XXX TODO method ref
                DFType instType = new DFClassType(anonKlass);
                CreateObjectNode call = new CreateObjectNode(
                    graph, varSpace, instType, mref, null);
                cpt.setRValue(call);

            } else {
                // ???
                throw new UnsupportedSyntax(expr);
            }
        } catch (EntityNotFound e) {
            e.setAst(expr);
            throw e;
        }

        return cpt;
    }

    /**
     * Creates an assignment node.
     */
    @SuppressWarnings("unchecked")
    public DFComponent processAssignment(
        DFGraph graph, DFTypeFinder finder, DFVarSpace varSpace,
        DFFrame frame, DFComponent cpt, Expression expr)
        throws UnsupportedSyntax, EntityNotFound {

        if (expr instanceof Name) {
            Name name = (Name)expr;
            if (name.isSimpleName()) {
                DFVarRef ref = varSpace.lookupVarOrField((SimpleName)name);
                cpt.setLValue(new SingleAssignNode(graph, varSpace, ref, expr));
                frame.addOutput(ref);
            } else {
                QualifiedName qname = (QualifiedName)name;
                DFNode obj = null;
                DFClassSpace klass;
                try {
                    // Try assuming it's a variable access.
                    cpt = processExpression(
                        graph, finder, varSpace, frame, cpt, qname.getQualifier());
                    obj = cpt.getRValue();
                    klass = finder.resolveClass(obj.getType());
                } catch (EntityNotFound e) {
                    // Turned out it's a class variable.
                    klass = finder.lookupClass(qname.getQualifier());
                }
                SimpleName fieldName = qname.getName();
                DFVarRef ref = klass.lookupField(fieldName);
                cpt.setLValue(new FieldAssignNode(graph, varSpace, ref, expr, obj));
                frame.addOutput(ref);
            }

        } else if (expr instanceof ArrayAccess) {
            ArrayAccess aa = (ArrayAccess)expr;
            cpt = processExpression(
                graph, finder, varSpace, frame, cpt, aa.getArray());
            DFNode array = cpt.getRValue();
            cpt = processExpression(
                graph, finder, varSpace, frame, cpt, aa.getIndex());
            DFVarRef ref = this.rootSpace.getArrayRef(array.getType());
            DFNode index = cpt.getRValue();
            DFNode node = new ArrayAssignNode(
                graph, varSpace, ref, expr, array, index);
            cpt.setLValue(node);
            frame.addOutput(ref);

        } else if (expr instanceof FieldAccess) {
            FieldAccess fa = (FieldAccess)expr;
            Expression expr1 = fa.getExpression();
            cpt = processExpression(
                graph, finder, varSpace, frame, cpt, expr1);
            DFNode obj = cpt.getRValue();
            DFClassSpace klass = finder.resolveClass(obj.getType());
            SimpleName fieldName = fa.getName();
            DFVarRef ref = klass.lookupField(fieldName);
            cpt.setLValue(new FieldAssignNode(graph, varSpace, ref, expr, obj));
            frame.addOutput(ref);

        } else {
            throw new UnsupportedSyntax(expr);
        }

        return cpt;
    }

    /**
     * Creates a new variable node.
     */
    public DFComponent processVariableDeclaration(
        DFGraph graph, DFTypeFinder finder, DFVarSpace varSpace,
        DFFrame frame, DFComponent cpt, List<VariableDeclarationFragment> frags)
        throws UnsupportedSyntax, EntityNotFound {

        for (VariableDeclarationFragment frag : frags) {
            DFVarRef ref = varSpace.lookupVar(frag.getName());
            Expression init = frag.getInitializer();
            if (init != null) {
                cpt = processExpression(
                    graph, finder, varSpace, frame, cpt, init);
                DFNode assign = new SingleAssignNode(graph, varSpace, ref, frag);
                assign.accept(cpt.getRValue());
                cpt.setOutput(assign);
            }
            frame.addOutput(ref);
        }
        return cpt;
    }

    /**
     * Combines two components into one.
     * A JoinNode is added to each variable.
     */
    public DFComponent processConditional(
        DFGraph graph, DFVarSpace varSpace,
        DFFrame frame, DFComponent cpt,
        ASTNode ast, DFNode condValue,
        DFComponent trueCpt, DFComponent falseCpt) {

        // outRefs: all the references from both component.
        List<DFVarRef> outRefs = new ArrayList<DFVarRef>();
        if (trueCpt != null) {
            for (DFVarRef ref : trueCpt.getInputRefs()) {
                DFNode src = trueCpt.getInput(ref);
                assert src != null;
                src.accept(cpt.getValue(ref));
            }
            outRefs.addAll(Arrays.asList(trueCpt.getOutputRefs()));
        }
        if (falseCpt != null) {
            for (DFVarRef ref : falseCpt.getInputRefs()) {
                DFNode src = falseCpt.getInput(ref);
                assert src != null;
                src.accept(cpt.getValue(ref));
            }
            outRefs.addAll(Arrays.asList(falseCpt.getOutputRefs()));
        }

        // Attach a JoinNode to each variable.
        Set<DFVarRef> used = new HashSet<DFVarRef>();
        for (DFVarRef ref : outRefs) {
            if (used.contains(ref)) continue;
            used.add(ref);
            JoinNode join = new JoinNode(graph, varSpace, ref, ast, condValue);
            if (trueCpt != null) {
                DFNode dst = trueCpt.getOutput(ref);
                if (dst != null) {
                    join.recv(true, dst);
                }
            }
            if (falseCpt != null) {
                DFNode dst = falseCpt.getOutput(ref);
                if (dst != null) {
                    join.recv(false, dst);
                }
            }
            if (!join.isClosed()) {
                join.close(cpt.getValue(ref));
            }
            cpt.setOutput(join);
        }

        // Take care of exits.
        if (trueCpt != null) {
            for (DFExit exit : trueCpt.getExits()) {
                DFNode node = exit.getNode();
                JoinNode join = new JoinNode(
                    graph, varSpace, node.getRef(), null, condValue);
                join.recv(true, node);
                cpt.addExit(exit.wrap(join));
            }
        }
        if (falseCpt != null) {
            for (DFExit exit : falseCpt.getExits()) {
                DFNode node = exit.getNode();
                JoinNode join = new JoinNode(
                    graph, varSpace, node.getRef(), null, condValue);
                join.recv(false, node);
                cpt.addExit(exit.wrap(join));
            }
        }

        return cpt;
    }

    /**
     * Expands the graph for the loop variables.
     */
    public DFComponent processLoop(
        DFGraph graph, DFVarSpace varSpace,
        DFFrame frame, DFComponent cpt,
        ASTNode ast, DFNode condValue,
        DFFrame loopFrame, DFComponent loopCpt, boolean preTest)
        throws UnsupportedSyntax {

        // Add four nodes for each loop variable.
        Map<DFVarRef, LoopBeginNode> begins =
            new HashMap<DFVarRef, LoopBeginNode>();
        Map<DFVarRef, LoopRepeatNode> repeats =
            new HashMap<DFVarRef, LoopRepeatNode>();
        Map<DFVarRef, DFNode> ends =
            new HashMap<DFVarRef, DFNode>();
        DFVarRef[] loopRefs = loopFrame.getInsAndOuts();
        for (DFVarRef ref : loopRefs) {
            DFNode src = cpt.getValue(ref);
            LoopBeginNode begin = new LoopBeginNode(graph, varSpace, ref, ast, src);
            LoopRepeatNode repeat = new LoopRepeatNode(graph, varSpace, ref, ast);
            LoopEndNode end = new LoopEndNode(graph, varSpace, ref, ast, condValue);
            begin.setEnd(end);
            end.setBegin(begin);
            begins.put(ref, begin);
            ends.put(ref, end);
            repeats.put(ref, repeat);
        }

        if (preTest) {  // Repeat -> [S] -> Begin -> End
            // Connect the repeats to the loop inputs.
            for (DFVarRef ref : loopCpt.getInputRefs()) {
                DFNode input = loopCpt.getInput(ref);
                DFNode src = repeats.get(ref);
                if (src == null) {
                    src = cpt.getValue(ref);
                }
                input.accept(src);
            }
            // Connect the loop outputs to the begins.
            for (DFVarRef ref : loopCpt.getOutputRefs()) {
                DFNode output = loopCpt.getOutput(ref);
                LoopBeginNode begin = begins.get(ref);
                if (begin != null) {
                    begin.setRepeat(output);
                } else {
                    //assert !loopRefs.contains(ref);
                    cpt.setOutput(output);
                }
            }
            // Connect the beings and ends.
            for (DFVarRef ref : loopRefs) {
                LoopBeginNode begin = begins.get(ref);
                DFNode end = ends.get(ref);
                end.accept(begin);
            }

        } else {  // Begin -> [S] -> End -> Repeat
            // Connect the begins to the loop inputs.
            for (DFVarRef ref : loopCpt.getInputRefs()) {
                DFNode input = loopCpt.getInput(ref);
                DFNode src = begins.get(ref);
                if (src == null) {
                    src = cpt.getValue(ref);
                }
                input.accept(src);
            }
            // Connect the loop outputs to the ends.
            for (DFVarRef ref : loopCpt.getOutputRefs()) {
                DFNode output = loopCpt.getOutput(ref);
                DFNode dst = ends.get(ref);
                if (dst != null) {
                    dst.accept(output);
                } else {
                    //assert !loopRefs.contains(ref);
                    cpt.setOutput(output);
                }
            }
            // Connect the repeats and begins.
            for (DFVarRef ref : loopRefs) {
                LoopRepeatNode repeat = repeats.get(ref);
                LoopBeginNode begin = begins.get(ref);
                begin.setRepeat(repeat);
            }
        }

        // Redirect the continue statements.
        for (DFExit exit : loopCpt.getExits()) {
            if (exit.isCont() && exit.getFrame() == loopFrame) {
                DFNode node = exit.getNode();
                DFNode end = ends.get(node.getRef());
                if (end == null) {
                    end = cpt.getValue(node.getRef());
                }
                if (node instanceof JoinNode) {
                    ((JoinNode)node).close(end);
                }
                ends.put(node.getRef(), node);
            } else {
                cpt.addExit(exit);
            }
        }

        // Closing the loop.
        for (DFVarRef ref : loopRefs) {
            DFNode end = ends.get(ref);
            LoopRepeatNode repeat = repeats.get(ref);
            cpt.setOutput(end);
            repeat.setLoop(end);
        }

        return cpt;
    }

    /// Statement processors.
    @SuppressWarnings("unchecked")
    public DFComponent processBlock(
        DFGraph graph, DFTypeFinder finder, DFVarSpace varSpace,
        DFFrame frame, DFComponent cpt, Block block)
        throws UnsupportedSyntax, EntityNotFound {
        DFVarSpace childSpace = varSpace.getChildByAST(block);
        for (Statement cstmt : (List<Statement>) block.statements()) {
            cpt = processStatement(
                graph, finder, childSpace, frame, cpt, cstmt);
        }
        return cpt;
    }

    @SuppressWarnings("unchecked")
    public DFComponent processVariableDeclarationStatement(
        DFGraph graph, DFTypeFinder finder, DFVarSpace varSpace,
        DFFrame frame, DFComponent cpt, VariableDeclarationStatement varStmt)
        throws UnsupportedSyntax, EntityNotFound {
        return processVariableDeclaration(
            graph, finder, varSpace, frame, cpt, varStmt.fragments());
    }

    public DFComponent processExpressionStatement(
        DFGraph graph, DFTypeFinder finder, DFVarSpace varSpace,
        DFFrame frame, DFComponent cpt, ExpressionStatement exprStmt)
        throws UnsupportedSyntax, EntityNotFound {
        Expression expr = exprStmt.getExpression();
        return processExpression(
            graph, finder, varSpace, frame, cpt, expr);
    }

    public DFComponent processIfStatement(
        DFGraph graph, DFTypeFinder finder, DFVarSpace varSpace,
        DFFrame frame, DFComponent cpt, IfStatement ifStmt)
        throws UnsupportedSyntax, EntityNotFound {
        Expression expr = ifStmt.getExpression();
        cpt = processExpression(graph, finder, varSpace, frame, cpt, expr);
        DFNode condValue = cpt.getRValue();

        Statement thenStmt = ifStmt.getThenStatement();
        DFComponent thenCpt = new DFComponent(graph, varSpace);
        thenCpt = processStatement(
            graph, finder, varSpace, frame, thenCpt, thenStmt);

        Statement elseStmt = ifStmt.getElseStatement();
        DFComponent elseCpt = null;
        if (elseStmt != null) {
            elseCpt = new DFComponent(graph, varSpace);
            elseCpt = processStatement(
                graph, finder, varSpace, frame, elseCpt, elseStmt);
        }
        return processConditional(
            graph, varSpace, frame, cpt, ifStmt,
            condValue, thenCpt, elseCpt);
    }

    private DFComponent processCaseStatement(
        DFGraph graph, DFVarSpace varSpace,
        DFFrame frame, DFComponent cpt, ASTNode apt,
        DFNode caseNode, DFComponent caseCpt) {

        for (DFVarRef ref : caseCpt.getInputRefs()) {
            DFNode src = caseCpt.getInput(ref);
            src.accept(cpt.getValue(ref));
        }

        for (DFVarRef ref : caseCpt.getOutputRefs()) {
            DFNode dst = caseCpt.getOutput(ref);
            JoinNode join = new JoinNode(graph, varSpace, ref, apt, caseNode);
            join.recv(true, dst);
            join.close(cpt.getValue(ref));
            cpt.setOutput(join);
        }

        return cpt;
    }

    @SuppressWarnings("unchecked")
    public DFComponent processSwitchStatement(
        DFGraph graph, DFTypeFinder finder, DFVarSpace varSpace,
        DFFrame frame, DFComponent cpt, SwitchStatement switchStmt)
        throws UnsupportedSyntax, EntityNotFound {
        DFVarSpace switchSpace = varSpace.getChildByAST(switchStmt);
        DFFrame switchFrame = frame.getChildByAST(switchStmt);
        cpt = processExpression(
            graph, finder, varSpace,
            frame, cpt, switchStmt.getExpression());
        DFNode switchValue = cpt.getRValue();

        SwitchCase switchCase = null;
        CaseNode caseNode = null;
        DFComponent caseCpt = null;
        for (Statement stmt : (List<Statement>) switchStmt.statements()) {
            if (stmt instanceof SwitchCase) {
                if (caseCpt != null) {
                    // switchCase, caseNode and caseCpt must be non-null.
                    cpt = processCaseStatement(
                        graph, switchSpace, switchFrame,
                        cpt, switchCase, caseNode, caseCpt);
                }
                switchCase = (SwitchCase)stmt;
                caseNode = new CaseNode(graph, switchSpace, stmt);
                caseNode.accept(switchValue);
                caseCpt = new DFComponent(graph, switchSpace);
                Expression expr = switchCase.getExpression();
                if (expr != null) {
                    cpt = processExpression(
                        graph, finder, switchSpace, frame, cpt, expr);
                    caseNode.addMatch(cpt.getRValue());
                } else {
                    // "default" case.
                }
            } else {
                if (caseCpt == null) {
                    // no "case" statement.
                    throw new UnsupportedSyntax(stmt);
                }
                caseCpt = processStatement(
                    graph, finder, switchSpace,
                    switchFrame, caseCpt, stmt);
            }
        }
        if (caseCpt != null) {
            cpt = processCaseStatement(
                graph, switchSpace, switchFrame,
                cpt, switchCase, caseNode, caseCpt);
        }
        cpt.endFrame(switchFrame);
        return cpt;
    }

    public DFComponent processWhileStatement(
        DFGraph graph, DFTypeFinder finder, DFVarSpace varSpace,
        DFFrame frame, DFComponent cpt, WhileStatement whileStmt)
        throws UnsupportedSyntax, EntityNotFound {
        DFVarSpace loopSpace = varSpace.getChildByAST(whileStmt);
        DFFrame loopFrame = frame.getChildByAST(whileStmt);
        DFComponent loopCpt = new DFComponent(graph, loopSpace);
        loopCpt = processExpression(
            graph, finder, loopSpace, frame, loopCpt,
            whileStmt.getExpression());
        DFNode condValue = loopCpt.getRValue();
        loopCpt = processStatement(
            graph, finder, loopSpace, loopFrame, loopCpt,
            whileStmt.getBody());
        cpt = processLoop(
            graph, loopSpace, frame, cpt, whileStmt,
            condValue, loopFrame, loopCpt, true);
        cpt.endFrame(loopFrame);
        return cpt;
    }

    public DFComponent processDoStatement(
        DFGraph graph, DFTypeFinder finder, DFVarSpace varSpace,
        DFFrame frame, DFComponent cpt, DoStatement doStmt)
        throws UnsupportedSyntax, EntityNotFound {
        DFVarSpace loopSpace = varSpace.getChildByAST(doStmt);
        DFFrame loopFrame = frame.getChildByAST(doStmt);
        DFComponent loopCpt = new DFComponent(graph, loopSpace);
        loopCpt = processStatement(
            graph, finder, loopSpace, loopFrame, loopCpt,
            doStmt.getBody());
        loopCpt = processExpression(
            graph, finder, loopSpace, loopFrame, loopCpt,
            doStmt.getExpression());
        DFNode condValue = loopCpt.getRValue();
        cpt = processLoop(
            graph, loopSpace, frame, cpt, doStmt,
            condValue, loopFrame, loopCpt, false);
        cpt.endFrame(loopFrame);
        return cpt;
    }

    @SuppressWarnings("unchecked")
    public DFComponent processForStatement(
        DFGraph graph, DFTypeFinder finder, DFVarSpace varSpace,
        DFFrame frame, DFComponent cpt, ForStatement forStmt)
        throws UnsupportedSyntax, EntityNotFound {
        DFVarSpace loopSpace = varSpace.getChildByAST(forStmt);
        DFFrame loopFrame = frame.getChildByAST(forStmt);
        DFComponent loopCpt = new DFComponent(graph, loopSpace);
        for (Expression init : (List<Expression>) forStmt.initializers()) {
            cpt = processExpression(
                graph, finder, loopSpace, frame, cpt, init);
        }
        Expression expr = forStmt.getExpression();
        DFNode condValue;
        if (expr != null) {
            loopCpt = processExpression(
                graph, finder, loopSpace, loopFrame, loopCpt, expr);
            condValue = loopCpt.getRValue();
        } else {
            condValue = new ConstNode(graph, loopSpace, DFBasicType.BOOLEAN, null, "true");
        }
        loopCpt = processStatement(
            graph, finder, loopSpace, loopFrame, loopCpt,
            forStmt.getBody());
        for (Expression update : (List<Expression>) forStmt.updaters()) {
            loopCpt = processExpression(
                graph, finder, loopSpace, loopFrame, loopCpt, update);
        }
        cpt = processLoop(
            graph, loopSpace, frame, cpt, forStmt,
            condValue, loopFrame, loopCpt, true);
        cpt.endFrame(loopFrame);
        return cpt;
    }

    @SuppressWarnings("unchecked")
    public DFComponent processEnhancedForStatement(
        DFGraph graph, DFTypeFinder finder, DFVarSpace varSpace,
        DFFrame frame, DFComponent cpt, EnhancedForStatement eForStmt)
        throws UnsupportedSyntax, EntityNotFound {
        DFVarSpace loopSpace = varSpace.getChildByAST(eForStmt);
        DFFrame loopFrame = frame.getChildByAST(eForStmt);
        DFComponent loopCpt = new DFComponent(graph, loopSpace);
        Expression expr = eForStmt.getExpression();
        loopCpt = processExpression(
            graph, finder, loopSpace, frame, loopCpt, expr);
        SingleVariableDeclaration decl = eForStmt.getParameter();
        DFVarRef ref = loopSpace.lookupVar(decl.getName());
        DFNode iterValue = new IterNode(graph, loopSpace, ref, expr);
        iterValue.accept(loopCpt.getRValue());
        SingleAssignNode assign = new SingleAssignNode(graph, loopSpace, ref, expr);
        assign.accept(iterValue);
        cpt.setOutput(assign);
        loopCpt = processStatement(
            graph, finder, loopSpace, loopFrame, loopCpt,
            eForStmt.getBody());
        cpt = processLoop(
            graph, loopSpace, frame, cpt, eForStmt,
            iterValue, loopFrame, loopCpt, true);
        cpt.endFrame(loopFrame);
        return cpt;
    }

    @SuppressWarnings("unchecked")
    public DFComponent processStatement(
        DFGraph graph, DFTypeFinder finder, DFVarSpace varSpace,
        DFFrame frame, DFComponent cpt, Statement stmt)
        throws UnsupportedSyntax, EntityNotFound {

        if (stmt instanceof AssertStatement) {
            // XXX Ignore asserts.

        } else if (stmt instanceof Block) {
            cpt = processBlock(
                graph, finder, varSpace, frame, cpt, (Block)stmt);

        } else if (stmt instanceof EmptyStatement) {

        } else if (stmt instanceof VariableDeclarationStatement) {
            cpt = processVariableDeclarationStatement(
                graph, finder, varSpace, frame, cpt,
                (VariableDeclarationStatement)stmt);

        } else if (stmt instanceof ExpressionStatement) {
            cpt = processExpressionStatement(
                graph, finder, varSpace, frame, cpt,
                (ExpressionStatement)stmt);

        } else if (stmt instanceof IfStatement) {
            cpt = processIfStatement(
                graph, finder, varSpace, frame, cpt,
                (IfStatement)stmt);

        } else if (stmt instanceof SwitchStatement) {
            cpt = processSwitchStatement(
                graph, finder, varSpace, frame, cpt,
                (SwitchStatement)stmt);

        } else if (stmt instanceof SwitchCase) {
            // Invalid "case" placement.
            throw new UnsupportedSyntax(stmt);

        } else if (stmt instanceof WhileStatement) {
            cpt = processWhileStatement(
                graph, finder, varSpace, frame, cpt,
                (WhileStatement)stmt);

        } else if (stmt instanceof DoStatement) {
            cpt = processDoStatement(
                graph, finder, varSpace, frame, cpt,
                (DoStatement)stmt);

        } else if (stmt instanceof ForStatement) {
            cpt = processForStatement(
                graph, finder, varSpace, frame, cpt,
                (ForStatement)stmt);

        } else if (stmt instanceof EnhancedForStatement) {
            cpt = processEnhancedForStatement(
                graph, finder, varSpace, frame, cpt,
                (EnhancedForStatement)stmt);

        } else if (stmt instanceof ReturnStatement) {
            ReturnStatement rtrnStmt = (ReturnStatement)stmt;
            DFFrame dstFrame = frame.find(DFFrame.METHOD);
            Expression expr = rtrnStmt.getExpression();
            if (expr != null) {
                cpt = processExpression(
                    graph, finder, varSpace, frame, cpt, expr);
                ReturnNode rtrn = new ReturnNode(graph, varSpace, rtrnStmt);
                rtrn.accept(cpt.getRValue());
                cpt.addExit(new DFExit(rtrn, dstFrame));
            }
            for (DFFrame frm = frame; frm != null; frm = frm.getParent()) {
                for (DFVarRef ref : frm.getOutputs()) {
                    cpt.addExit(new DFExit(cpt.getValue(ref), dstFrame));
                }
                if (frm == dstFrame) break;
            }

        } else if (stmt instanceof BreakStatement) {
            BreakStatement breakStmt = (BreakStatement)stmt;
            SimpleName labelName = breakStmt.getLabel();
            String dstLabel = (labelName == null)? null : labelName.getIdentifier();
            DFFrame dstFrame = frame.find(dstLabel);
            for (DFFrame frm = frame; frm != null; frm = frm.getParent()) {
                for (DFVarRef ref : frm.getOutputs()) {
                    cpt.addExit(new DFExit(cpt.getValue(ref), dstFrame));
                }
                if (frm == dstFrame) break;
            }

        } else if (stmt instanceof ContinueStatement) {
            ContinueStatement contStmt = (ContinueStatement)stmt;
            SimpleName labelName = contStmt.getLabel();
            String dstLabel = (labelName == null)? null : labelName.getIdentifier();
            DFFrame dstFrame = frame.find(dstLabel);
            for (DFFrame frm = frame; frm != null; frm = frm.getParent()) {
                for (DFVarRef ref : frm.getOutputs()) {
                    cpt.addExit(new DFExit(cpt.getValue(ref), dstFrame, true));
                }
                if (frm == dstFrame) break;
            }

        } else if (stmt instanceof LabeledStatement) {
            LabeledStatement labeledStmt = (LabeledStatement)stmt;
            DFFrame labeledFrame = frame.getChildByAST(labeledStmt);
            cpt = processStatement(
                graph, finder, varSpace, labeledFrame,
                cpt, labeledStmt.getBody());

        } else if (stmt instanceof SynchronizedStatement) {
            SynchronizedStatement syncStmt = (SynchronizedStatement)stmt;
            cpt = processStatement(
                graph, finder, varSpace, frame,
                cpt, syncStmt.getBody());

        } else if (stmt instanceof TryStatement) {
            // XXX Ignore catch statements (for now).
            TryStatement tryStmt = (TryStatement)stmt;
            DFFrame tryFrame = frame.getChildByAST(tryStmt);
            cpt = processStatement(
                graph, finder, varSpace, tryFrame,
                cpt, tryStmt.getBody());
            Block finBlock = tryStmt.getFinally();
            if (finBlock != null) {
                cpt = processStatement(
                    graph, finder, varSpace, frame, cpt, finBlock);
            }

        } else if (stmt instanceof ThrowStatement) {
            ThrowStatement throwStmt = (ThrowStatement)stmt;
            cpt = processExpression(
                graph, finder, varSpace, frame,
                cpt, throwStmt.getExpression());
            ExceptionNode exception = new ExceptionNode(
                graph, varSpace, stmt, cpt.getRValue());
            DFFrame dstFrame = frame.find(DFFrame.TRY);
            cpt.addExit(new DFExit(exception, dstFrame));
            for (DFFrame frm = frame; frm != null; frm = frm.getParent()) {
                for (DFVarRef ref : frm.getOutputs()) {
                    cpt.addExit(new DFExit(cpt.getValue(ref), dstFrame, true));
                }
                if (frm == dstFrame) break;
            }

        } else if (stmt instanceof ConstructorInvocation) {
            // XXX Ignore all side effects.
            ConstructorInvocation ci = (ConstructorInvocation)stmt;
            for (Expression arg : (List<Expression>) ci.arguments()) {
                cpt = processExpression(
                    graph, finder, varSpace, frame, cpt, arg);
            }

        } else if (stmt instanceof SuperConstructorInvocation) {
            // XXX Ignore all side effects.
            SuperConstructorInvocation sci = (SuperConstructorInvocation)stmt;
            for (Expression arg : (List<Expression>) sci.arguments()) {
                cpt = processExpression(
                    graph, finder, varSpace, frame, cpt, arg);
            }

        } else if (stmt instanceof TypeDeclarationStatement) {
            // Ignore TypeDeclarationStatement because
            // it was eventually picked up as MethodDeclaration.

        } else {
            throw new UnsupportedSyntax(stmt);
        }

        return cpt;
    }

    @SuppressWarnings("unchecked")
    private void buildInlineClasses(
        DFTypeSpace typeSpace, DFTypeFinder finder, Statement stmt)
        throws UnsupportedSyntax, EntityNotFound {

        if (stmt instanceof AssertStatement) {

        } else if (stmt instanceof Block) {
            Block block = (Block)stmt;
            for (Statement cstmt :
                     (List<Statement>) block.statements()) {
                this.buildInlineClasses(typeSpace, finder, cstmt);
            }

        } else if (stmt instanceof EmptyStatement) {

        } else if (stmt instanceof VariableDeclarationStatement) {

        } else if (stmt instanceof ExpressionStatement) {

        } else if (stmt instanceof ReturnStatement) {

        } else if (stmt instanceof IfStatement) {
            IfStatement ifStmt = (IfStatement)stmt;
            Statement thenStmt = ifStmt.getThenStatement();
            this.buildInlineClasses(typeSpace, finder, thenStmt);
            Statement elseStmt = ifStmt.getElseStatement();
            if (elseStmt != null) {
                this.buildInlineClasses(typeSpace, finder, elseStmt);
            }

        } else if (stmt instanceof SwitchStatement) {
            SwitchStatement switchStmt = (SwitchStatement)stmt;
            for (Statement cstmt :
                     (List<Statement>) switchStmt.statements()) {
                this.buildInlineClasses(typeSpace, finder, cstmt);
            }

        } else if (stmt instanceof SwitchCase) {

        } else if (stmt instanceof WhileStatement) {
            WhileStatement whileStmt = (WhileStatement)stmt;
            Statement body = whileStmt.getBody();
            this.buildInlineClasses(typeSpace, finder, body);

        } else if (stmt instanceof DoStatement) {
            DoStatement doStmt = (DoStatement)stmt;
            Statement body = doStmt.getBody();
            this.buildInlineClasses(typeSpace, finder, body);

        } else if (stmt instanceof ForStatement) {
            ForStatement forStmt = (ForStatement)stmt;
            Statement body = forStmt.getBody();
            this.buildInlineClasses(typeSpace, finder, body);

        } else if (stmt instanceof EnhancedForStatement) {
            EnhancedForStatement eForStmt = (EnhancedForStatement)stmt;
            Statement body = eForStmt.getBody();
            this.buildInlineClasses(typeSpace, finder, body);

        } else if (stmt instanceof BreakStatement) {

        } else if (stmt instanceof ContinueStatement) {

        } else if (stmt instanceof LabeledStatement) {
            LabeledStatement labeledStmt = (LabeledStatement)stmt;
            Statement body = labeledStmt.getBody();
            this.buildInlineClasses(typeSpace, finder, body);

        } else if (stmt instanceof SynchronizedStatement) {
            SynchronizedStatement syncStmt = (SynchronizedStatement)stmt;
            Block block = syncStmt.getBody();
            this.buildInlineClasses(typeSpace, finder, block);

        } else if (stmt instanceof TryStatement) {
            TryStatement tryStmt = (TryStatement)stmt;
            Block block = tryStmt.getBody();
            this.buildInlineClasses(typeSpace, finder, block);
            for (CatchClause cc :
                     (List<CatchClause>) tryStmt.catchClauses()) {
                this.buildInlineClasses(typeSpace, finder, cc.getBody());
            }
            Block finBlock = tryStmt.getFinally();
            if (finBlock != null) {
                this.buildInlineClasses(typeSpace, finder, finBlock);
            }

        } else if (stmt instanceof ThrowStatement) {

        } else if (stmt instanceof ConstructorInvocation) {

        } else if (stmt instanceof SuperConstructorInvocation) {

        } else if (stmt instanceof TypeDeclarationStatement) {
            TypeDeclarationStatement typeDeclStmt = (TypeDeclarationStatement)stmt;
            this.buildInlineClasses(typeSpace, finder, typeDeclStmt.getDeclaration());

        } else {
            throw new UnsupportedSyntax(stmt);

        }
    }

    @SuppressWarnings("unchecked")
    private void buildInlineClasses(
        DFTypeSpace typeSpace, DFTypeFinder finder,
        AbstractTypeDeclaration abstDecl)
        throws UnsupportedSyntax, EntityNotFound {
        if (abstDecl instanceof TypeDeclaration) {
            TypeDeclaration typeDecl = (TypeDeclaration)abstDecl;
            DFClassSpace klass = typeSpace.getClass(typeDecl.getName());
            klass.build(finder, typeDecl);
            processBodyDeclarations(
                finder, klass, typeDecl.bodyDeclarations());
        } else {
            // XXX enum not supported.
            throw new UnsupportedSyntax(abstDecl);
        }
    }

    private DFTypeFinder prepareTypeFinder(List<ImportDeclaration> imports) {
        DFTypeFinder finder = new DFTypeFinder(this.rootSpace);
        finder = new DFTypeFinder(finder, this.rootSpace.lookupSpace("java.lang"));
        DFTypeSpace importSpace = new DFTypeSpace("Import");
        int n = 0;
        for (ImportDeclaration importDecl : imports) {
            try {
                // XXX support static import
                assert(!importDecl.isStatic());
                Name name = importDecl.getName();
                if (importDecl.isOnDemand()) {
                    Logger.info("Import: "+name+".*");
                    finder = new DFTypeFinder(finder, this.rootSpace.lookupSpace(name));
                } else {
                    assert(name.isQualifiedName());
                    DFClassSpace klass = this.rootSpace.getClass(name);
                    Logger.info("Import: "+name);
                    importSpace.addClass(klass);
                    n++;
                }
            } catch (TypeNotFound e) {
                Logger.error("Import: class not found: "+e.name);
            }
        }
        if (0 < n) {
            finder = new DFTypeFinder(finder, importSpace);
        }
        return finder;
    }

    /// Top-level functions.

    public DFRootTypeSpace rootSpace;
    public Exporter exporter;

    public Java2DF(
        DFRootTypeSpace rootSpace,
        Exporter exporter) {
        this.rootSpace = rootSpace;
        this.exporter = exporter;
    }

    /**
     * Performs dataflow analysis for a given method.
     */
    @SuppressWarnings("unchecked")
    public void processFieldDeclaration(
        DFGraph graph, DFTypeFinder finder, DFClassSpace klass,
        DFFrame frame, FieldDeclaration fieldDecl)
        throws UnsupportedSyntax, EntityNotFound {
        for (VariableDeclarationFragment frag :
                 (List<VariableDeclarationFragment>) fieldDecl.fragments()) {
            DFVarRef ref = klass.lookupField(frag.getName());
            Expression init = frag.getInitializer();
            if (init != null) {
                DFComponent cpt = new DFComponent(graph, klass);
                cpt = processExpression(
                    graph, finder, klass, frame, cpt, init);
                DFNode assign = new SingleAssignNode(graph, klass, ref, frag);
                assign.accept(cpt.getRValue());
            }
        }
    }

    @SuppressWarnings("unchecked")
    public DFGraph processMethodDeclaration(
        DFTypeFinder finder, DFClassSpace klass,
        MethodDeclaration methodDecl)
        throws UnsupportedSyntax, EntityNotFound {
        // Ignore method prototypes.
        if (methodDecl.getBody() == null) return null;
        DFMethod method = klass.getMethodByAST(methodDecl);
        assert(method != null);
        DFTypeSpace methodSpace = method.getChildSpace();
        if (methodSpace != null) {
            finder = new DFTypeFinder(finder, methodSpace);
        }
        DFType[] argTypes = finder.resolveList(methodDecl);
        DFVarSpace varSpace = new DFVarSpace(klass, methodDecl.getName());
        // add a typespace for inline classes.
        DFTypeSpace typeSpace = new DFTypeSpace(varSpace.getFullName()+"/inline");
        finder = new DFTypeFinder(finder, typeSpace);
        try {
            // Setup an initial space.
            List<DFClassSpace> classes = new ArrayList<DFClassSpace>();
            typeSpace.build(classes, methodDecl.getBody(), varSpace);
            this.buildInlineClasses(typeSpace, finder, methodDecl.getBody());
            // Add overrides.
            for (DFClassSpace klass1 : classes) {
                klass1.addOverrides();
            }
            varSpace.build(finder, methodDecl);
            //varSpace.dump();
            DFFrame frame = new DFFrame(DFFrame.METHOD);
            frame.build(varSpace, methodDecl.getBody());

            DFGraph graph = new DFGraph(varSpace, method);
            DFComponent cpt = new DFComponent(graph, varSpace);
            // XXX Ignore isContructor().
            // XXX Ignore isVarargs().
            int i = 0;
            for (SingleVariableDeclaration decl :
                     (List<SingleVariableDeclaration>) methodDecl.parameters()) {
                // XXX Ignore modifiers and dimensions.
                DFVarRef ref = varSpace.lookupVar(decl.getName());
                DFNode param = new ArgNode(graph, varSpace, ref, decl, i++);
                DFNode assign = new SingleAssignNode(graph, varSpace, ref, decl);
                assign.accept(param);
                cpt.setOutput(assign);
            }

            // Process the function body.
            cpt = processStatement(
                graph, finder, varSpace, frame, cpt, methodDecl.getBody());
            cpt.endFrame(frame);
            frame.dump();
            // Remove redundant nodes.
            graph.cleanup();

            Logger.info("Success: "+method.getSignature());
            return graph;
        } catch (UnsupportedSyntax e) {
            //e.printStackTrace();
            e.name = method.getSignature();
            throw e;
        } catch (EntityNotFound e) {
            Logger.error("Entity not found: "+e.name+" ast="+e.ast+" method="+method);
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    public void processBodyDeclarations(
        DFTypeFinder finder, DFClassSpace klass,
        List<BodyDeclaration> decls)
        throws EntityNotFound {
        DFGraph classGraph = new DFGraph(klass);
        DFFrame frame = new DFFrame(DFFrame.CLASS);
        DFTypeSpace typeSpace = klass.getChildSpace();
        // lookup base/child classes.
        finder = klass.addFinders(finder);
        for (BodyDeclaration body : decls) {
            try {
                if (body instanceof TypeDeclaration) {
                    TypeDeclaration typeDecl = (TypeDeclaration)body;
                    processTypeDeclaration(typeSpace, finder, typeDecl);
                } else if (body instanceof FieldDeclaration) {
                    processFieldDeclaration(
                        classGraph, finder, klass,
                        frame, (FieldDeclaration)body);
                } else if (body instanceof MethodDeclaration) {
                    DFGraph graph = processMethodDeclaration(
                        finder, klass, (MethodDeclaration)body);
                    if (this.exporter != null && graph != null) {
                        this.exporter.writeGraph(graph);
                    }
                } else if (body instanceof Initializer) {
                    Block block = ((Initializer)body).getBody();
                    klass.build(finder, block);
                    frame.build(klass, block);
                    DFComponent cpt = new DFComponent(classGraph, klass);
                    cpt = processStatement(
                        classGraph, finder, klass,
                        frame, cpt, block);
                    cpt.endFrame(frame);
                }
            } catch (UnsupportedSyntax e) {
                String astName = e.ast.getClass().getName();
                Logger.error("Fail: "+e.name+" (Unsupported: "+astName+") "+e.ast);
                if (this.exporter != null) {
                    this.exporter.writeError(e.name, astName);
                }
            }
        }
        if (this.exporter != null) {
            this.exporter.writeGraph(classGraph);
        }
    }

    @SuppressWarnings("unchecked")
    public void processTypeDeclaration(
        DFTypeSpace typeSpace, DFTypeFinder finder, TypeDeclaration typeDecl)
        throws EntityNotFound {
        DFClassSpace klass = typeSpace.getClass(typeDecl.getName());
        processBodyDeclarations(
            finder, klass, typeDecl.bodyDeclarations());
    }

    public CompilationUnit parseFile(String path)
        throws IOException {
        String src = Utils.readFile(path);
        Map<String, String> options = JavaCore.getOptions();
        JavaCore.setComplianceOptions(JavaCore.VERSION_1_7, options);
        ASTParser parser = ASTParser.newParser(AST.JLS8);
        parser.setSource(src.toCharArray());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setResolveBindings(false);
        parser.setEnvironment(null, null, null, false);
        parser.setCompilerOptions(options);
        return (CompilationUnit)parser.createAST(null);
    }

    // pass1
    public void buildTypeSpace(
        List<DFClassSpace> classes, CompilationUnit cunit) {
        DFTypeSpace typeSpace = this.rootSpace.lookupSpace(cunit.getPackage());
        try {
            typeSpace.build(classes, cunit);
        } catch (UnsupportedSyntax e) {
            String astName = e.ast.getClass().getName();
            Logger.error("Fail: "+e.name+" (Unsupported: "+astName+") "+e.ast);
        }
    }

    // pass2
    @SuppressWarnings("unchecked")
    public void buildClassSpace(CompilationUnit cunit) throws EntityNotFound {
        DFTypeSpace typeSpace = this.rootSpace.lookupSpace(cunit.getPackage());
        DFTypeFinder finder = prepareTypeFinder(cunit.imports());
        finder = new DFTypeFinder(finder, typeSpace);
        try {
            for (TypeDeclaration typeDecl :
                     (List<TypeDeclaration>) cunit.types()) {
                DFClassSpace klass = typeSpace.getClass(typeDecl.getName());
                klass.build(finder, typeDecl);
            }
        } catch (UnsupportedSyntax e) {
            String astName = e.ast.getClass().getName();
            Logger.error("Pass2: unsupported: "+e.name+" (Unsupported: "+astName+") "+e.ast);
        } catch (TypeNotFound e) {
            Logger.error("Pass2: type not found: "+e.name+" ast="+e.ast);
            throw e;
        }
    }

    // pass3
    @SuppressWarnings("unchecked")
    public void buildGraphs(CompilationUnit cunit)
        throws EntityNotFound {
        DFTypeSpace typeSpace = this.rootSpace.lookupSpace(cunit.getPackage());
        DFTypeFinder finder = prepareTypeFinder(cunit.imports());
        finder = new DFTypeFinder(finder, typeSpace);
	for (TypeDeclaration typeDecl :
		 (List<TypeDeclaration>) cunit.types()) {
	    processTypeDeclaration(typeSpace, finder, typeDecl);
	}
    }

    /**
     * Provides a command line interface.
     *
     * Usage: java Java2DF [-o output] input.java ...
     */
    public static void main(String[] args)
        throws IOException, EntityNotFound {

        // Parse the options.
        List<String> files = new ArrayList<String>();
        OutputStream output = System.out;
        String sep = System.getProperty("path.separator");

        DFRootTypeSpace rootSpace = new DFRootTypeSpace();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--")) {
                for (; i < args.length; i++) {
                    files.add(args[i]);
                }
            } else if (arg.equals("-o")) {
                String path = args[++i];
                try {
                    output = new FileOutputStream(path);
                    Logger.info("Exporting: "+path);
                } catch (IOException e) {
                    System.err.println("Cannot open output file: "+path);
                }
            } else if (arg.equals("-C")) {
                for (String path : args[++i].split(sep)) {
                    rootSpace.loadJarFile(path);
                }
            } else if (arg.startsWith("-")) {
                System.err.println("Unknown option: "+arg);
                System.exit(1);
            } else {
                files.add(arg);
            }
        }

        // Process files.
        XmlExporter exporter = new XmlExporter();
        rootSpace.loadDefaultClasses();
        Java2DF converter = new Java2DF(rootSpace, exporter);
        List<DFClassSpace> classes = new ArrayList<DFClassSpace>();
        for (String path : files) {
            Logger.info("Pass1: "+path);
            try {
                CompilationUnit cunit = converter.parseFile(path);
                converter.buildTypeSpace(classes, cunit);
            } catch (IOException e) {
                System.err.println("Cannot open input file: "+path);
	    }
        }
        for (String path : files) {
            Logger.info("Pass2: "+path);
            try {
                CompilationUnit cunit = converter.parseFile(path);
                converter.buildClassSpace(cunit);
            } catch (IOException e) {
                System.err.println("Cannot open input file: "+path);
            } catch (EntityNotFound e) {
                System.err.println("Pass2: Error at "+path);
		throw e;
	    }
        }
        // Add overrides.
        for (DFClassSpace klass : classes) {
            klass.addOverrides();
        }
        for (String path : files) {
            Logger.info("Pass3: "+path);
            try {
                CompilationUnit cunit = converter.parseFile(path);
                exporter.startFile(path);
                converter.buildGraphs(cunit);
                exporter.endFile();
            } catch (IOException e) {
                System.err.println("Cannot open input file: "+path);
            } catch (EntityNotFound e) {
                System.err.println("Pass3: Error at "+path);
		throw e;
            }
        }
        //converter.rootSpace.dump();
        exporter.close();

        Utils.printXml(output, exporter.document);
        output.close();
    }
}
