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
        DFGraph graph, DFVarScope scope, DFType type, DFVarRef ref,
        ASTNode ast) {
        super(graph, scope, type, ref);
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
        DFGraph graph, DFVarScope scope, DFVarRef ref,
        ASTNode ast) {
        super(graph, scope, ref.getType(), ref, ast);
    }

    @Override
    public String getKind() {
        return "assign";
    }
}

// ArrayAssignNode:
class ArrayAssignNode extends ProgNode {

    public ArrayAssignNode(
        DFGraph graph, DFVarScope scope, DFVarRef ref,
        ASTNode ast, DFNode array, DFNode index) {
        super(graph, scope, null, ref, ast);
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
        DFGraph graph, DFVarScope scope, DFVarRef ref,
        ASTNode ast, DFNode obj) {
        super(graph, scope, null, ref, ast);
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
        DFGraph graph, DFVarScope scope, DFVarRef ref,
        ASTNode ast) {
        super(graph, scope, ref.getType(), ref, ast);
    }

    @Override
    public String getKind() {
        return "ref";
    }
}

// ArrayAccessNode
class ArrayAccessNode extends ProgNode {

    public ArrayAccessNode(
        DFGraph graph, DFVarScope scope, DFVarRef ref,
        ASTNode ast, DFNode array, DFNode index) {
        super(graph, scope, null, ref, ast);
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
        DFGraph graph, DFVarScope scope, DFVarRef ref,
        ASTNode ast, DFNode obj) {
        super(graph, scope, null, ref, ast);
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
        DFGraph graph, DFVarScope scope, DFVarRef ref,
        ASTNode ast, PrefixExpression.Operator op) {
        super(graph, scope, null, ref, ast);
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
        DFGraph graph, DFVarScope scope, DFVarRef ref,
        ASTNode ast, PostfixExpression.Operator op) {
        super(graph, scope, null, ref, ast);
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
        DFGraph graph, DFVarScope scope, DFType type,
        ASTNode ast, InfixExpression.Operator op,
        DFNode lvalue, DFNode rvalue) {
        super(graph, scope, type, null, ast);
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
        DFGraph graph, DFVarScope scope, DFType type,
        ASTNode ast) {
        super(graph, scope, type, null, ast);
        assert(type != null);
    }

    @Override
    public String getKind() {
        return "typecast";
    }

    @Override
    public String getData() {
        return this.getType().getTypeName();
    }
}

// InstanceofNode
class InstanceofNode extends ProgNode {

    public DFType type;

    public InstanceofNode(
        DFGraph graph, DFVarScope scope,
        ASTNode ast, DFType type) {
        super(graph, scope, DFBasicType.BOOLEAN, null, ast);
        assert(type != null);
        this.type = type;
    }

    @Override
    public String getKind() {
        return "instanceof";
    }

    @Override
    public String getData() {
        return this.getType().getTypeName();
    }
}

// CaseNode
class CaseNode extends ProgNode {

    public List<DFNode> matches = new ArrayList<DFNode>();

    public CaseNode(
        DFGraph graph, DFVarScope scope,
        ASTNode ast) {
        super(graph, scope, null, null, ast);
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
        DFGraph graph, DFVarScope scope, DFVarRef ref,
        ASTNode ast, Assignment.Operator op,
        DFNode lvalue, DFNode rvalue) {
        super(graph, scope, rvalue.getType(), ref, ast);
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
        DFGraph graph, DFVarScope scope, DFVarRef ref,
        ASTNode ast, int index) {
        super(graph, scope, ref.getType(), ref, ast);
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
        DFGraph graph, DFVarScope scope, DFType type,
        ASTNode ast, String data) {
        super(graph, scope, type, null, ast);
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
        DFGraph graph, DFVarScope scope, DFType type,
        ASTNode ast) {
        super(graph, scope, type, null, ast);
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
        DFGraph graph, DFVarScope scope, DFVarRef ref,
        ASTNode ast, DFNode cond) {
        super(graph, scope, null, ref, ast);
        this.accept(cond, "cond");
    }

    @Override
    public String getKind() {
        return "join";
    }

    @Override
    public void finish(DFContext ctx) {
        if (!this.isClosed()) {
            this.close(ctx.get(this.getRef()));
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
        DFGraph graph, DFVarScope scope, DFVarRef ref,
        ASTNode ast, DFNode enter) {
        super(graph, scope, null, ref, ast);
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
        DFGraph graph, DFVarScope scope, DFVarRef ref,
        ASTNode ast, DFNode cond) {
        super(graph, scope, null, ref, ast);
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
        DFGraph graph, DFVarScope scope, DFVarRef ref,
        ASTNode ast) {
        super(graph, scope, null, ref, ast);
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
        DFGraph graph, DFVarScope scope, DFVarRef ref,
        ASTNode ast) {
        super(graph, scope, null, ref, ast);
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
        DFGraph graph, DFVarScope scope, DFType type, DFVarRef ref,
        ASTNode ast) {
        super(graph, scope, type, ref, ast);
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
        DFGraph graph, DFVarScope scope, DFMethod[] methods,
        ASTNode ast, DFNode obj) {
        super(graph, scope, methods[0].getReturnType(), null, ast);
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
        DFGraph graph, DFVarScope scope, DFType type,
        ASTNode ast, DFNode obj) {
        super(graph, scope, type, null, ast);
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
        return this.getType().getTypeName();
    }
}

// ReturnNode: represents a return value.
class ReturnNode extends SingleAssignNode {

    public ReturnNode(
        DFGraph graph, DFVarScope scope,
        ASTNode ast) throws VariableNotFound {
        super(graph, scope, scope.lookupReturn(), ast);
    }

    @Override
    public String getKind() {
        return "return";
    }
}

// ExceptionNode
class ExceptionNode extends ProgNode {

    public ExceptionNode(
        DFGraph graph, DFVarScope scope,
        ASTNode ast, DFNode value) throws VariableNotFound {
        super(graph, scope, null, scope.lookupException(), ast);
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
    public DFContext processExpression(
        DFGraph graph, DFTypeFinder finder, DFVarScope scope,
        DFFrame frame, DFContext ctx, Expression expr)
        throws UnsupportedSyntax, EntityNotFound {
        try {
            if (expr instanceof Annotation) {

            } else if (expr instanceof Name) {
                Name name = (Name)expr;
                if (name.isSimpleName()) {
                    DFVarRef ref = scope.lookupVar((SimpleName)name);
                    DFNode node = new VarRefNode(graph, scope, ref, expr);
                    node.accept(ctx.get(ref));
                    ctx.setRValue(node);
                } else {
                    QualifiedName qname = (QualifiedName)name;
                    DFNode obj = null;
                    DFClass klass;
                    try {
                        // Try assuming it's a variable access.
                        ctx = processExpression(
                            graph, finder, scope, frame, ctx, qname.getQualifier());
                        obj = ctx.getRValue();
                        klass = finder.resolveClass(obj.getType());
                    } catch (EntityNotFound e) {
                        // Turned out it's a class variable.
                        klass = finder.lookupClass(qname.getQualifier());
                    }
                    SimpleName fieldName = qname.getName();
                    DFVarRef ref = klass.lookupField(fieldName);
                    DFNode node = new FieldAccessNode(graph, scope, ref, qname, obj);
                    node.accept(ctx.get(ref));
                    ctx.setRValue(node);
                }

            } else if (expr instanceof ThisExpression) {
                DFVarRef ref = scope.lookupThis();
                DFNode node = new VarRefNode(graph, scope, ref, expr);
                node.accept(ctx.get(ref));
                ctx.setRValue(node);

            } else if (expr instanceof BooleanLiteral) {
                boolean value = ((BooleanLiteral)expr).booleanValue();
                ctx.setRValue(new ConstNode(
                                  graph, scope, DFBasicType.BOOLEAN,
                                  expr, Boolean.toString(value)));

            } else if (expr instanceof CharacterLiteral) {
                char value = ((CharacterLiteral)expr).charValue();
                ctx.setRValue(new ConstNode(
                                  graph, scope, DFBasicType.CHAR,
                                  expr, Utils.quote(value)));

            } else if (expr instanceof NullLiteral) {
                ctx.setRValue(new ConstNode(
                                  graph, scope, DFNullType.NULL,
                                  expr, "null"));

            } else if (expr instanceof NumberLiteral) {
                String value = ((NumberLiteral)expr).getToken();
                ctx.setRValue(new ConstNode(
                                  graph, scope, DFBasicType.NUMBER,
                                  expr, value));

            } else if (expr instanceof StringLiteral) {
                String value = ((StringLiteral)expr).getLiteralValue();
                ctx.setRValue(new ConstNode(
                                  graph, scope,
                                  DFRootTypeSpace.STRING_CLASS,
                                  expr, Utils.quote(value)));

            } else if (expr instanceof TypeLiteral) {
                Type value = ((TypeLiteral)expr).getType();
                ctx.setRValue(new ConstNode(
                                  graph, scope, DFBasicType.TYPE,
                                  expr, Utils.getTypeName(value)));

            } else if (expr instanceof PrefixExpression) {
                PrefixExpression prefix = (PrefixExpression)expr;
                PrefixExpression.Operator op = prefix.getOperator();
                Expression operand = prefix.getOperand();
                ctx = processExpression(
                    graph, finder, scope, frame, ctx, operand);
                if (op == PrefixExpression.Operator.INCREMENT ||
                    op == PrefixExpression.Operator.DECREMENT) {
                    ctx = processAssignment(
                        graph, finder, scope, frame, ctx, operand);
                    DFNode assign = ctx.getLValue();
                    DFNode value = new PrefixNode(
                        graph, scope, assign.getRef(),
                        expr, op);
                    value.accept(ctx.getRValue());
                    assign.accept(value);
                    ctx.set(assign);
                    ctx.setRValue(value);
                } else {
                    DFNode value = new PrefixNode(
                        graph, scope, null,
                        expr, op);
                    value.accept(ctx.getRValue());
                    ctx.setRValue(value);
                }

            } else if (expr instanceof PostfixExpression) {
                PostfixExpression postfix = (PostfixExpression)expr;
                PostfixExpression.Operator op = postfix.getOperator();
                Expression operand = postfix.getOperand();
                ctx = processAssignment(
                    graph, finder, scope, frame, ctx, operand);
                if (op == PostfixExpression.Operator.INCREMENT ||
                    op == PostfixExpression.Operator.DECREMENT) {
                    DFNode assign = ctx.getLValue();
                    ctx = processExpression(
                        graph, finder, scope, frame, ctx, operand);
                    DFNode node = new PostfixNode(
                        graph, scope, assign.getRef(), expr, op);
                    node.accept(ctx.getRValue());
                    assign.accept(node);
                    ctx.set(assign);
                }

            } else if (expr instanceof InfixExpression) {
                InfixExpression infix = (InfixExpression)expr;
                InfixExpression.Operator op = infix.getOperator();
                ctx = processExpression(
                    graph, finder, scope, frame, ctx,
                    infix.getLeftOperand());
                DFNode lvalue = ctx.getRValue();
                ctx = processExpression(
                    graph, finder, scope, frame, ctx,
                    infix.getRightOperand());
                DFNode rvalue = ctx.getRValue();
                DFType type = lvalue.getType(); // XXX Todo: implicit type coersion.
                ctx.setRValue(new InfixNode(
                                  graph, scope, type, expr, op, lvalue, rvalue));

            } else if (expr instanceof ParenthesizedExpression) {
                ParenthesizedExpression paren = (ParenthesizedExpression)expr;
                ctx = processExpression(
                    graph, finder, scope, frame, ctx,
                    paren.getExpression());

            } else if (expr instanceof Assignment) {
                Assignment assn = (Assignment)expr;
                Assignment.Operator op = assn.getOperator();
                ctx = processAssignment(
                    graph, finder, scope, frame, ctx,
                    assn.getLeftHandSide());
                DFNode assign = ctx.getLValue();
                ctx = processExpression(
                    graph, finder, scope, frame, ctx,
                    assn.getRightHandSide());
                DFNode rvalue = ctx.getRValue();
                DFNode lvalue = null;
                if (op != Assignment.Operator.ASSIGN) {
                    lvalue = ctx.get(assign.getRef());
                }
                assign.accept(new AssignOpNode(
                                  graph, scope, assign.getRef(), assn,
                                  op, lvalue, rvalue));
                ctx.set(assign);
                ctx.setRValue(assign);

            } else if (expr instanceof VariableDeclarationExpression) {
                VariableDeclarationExpression decl = (VariableDeclarationExpression)expr;
                ctx = processVariableDeclaration(
                    graph, finder, scope, frame, ctx,
                    decl.fragments());

            } else if (expr instanceof MethodInvocation) {
                MethodInvocation invoke = (MethodInvocation)expr;
                Expression expr1 = invoke.getExpression();
                DFNode obj = null;
                DFClass klass = null;
                if (expr1 == null) {
                    obj = ctx.get(scope.lookupThis());
                } else {
                    if (expr1 instanceof Name) {
                        try {
                            klass = finder.lookupClass((Name)expr1);
                        } catch (EntityNotFound e) {
                        }
                    }
                    if (klass == null) {
                        ctx = processExpression(
                            graph, finder, scope, frame, ctx, expr1);
                        obj = ctx.getRValue();
                    }
                }
                if (obj != null) {
                    klass = finder.resolveClass(obj.getType());
                }
                List<DFNode> argList = new ArrayList<DFNode>();
                List<DFType> typeList = new ArrayList<DFType>();
                for (Expression arg : (List<Expression>) invoke.arguments()) {
                    ctx = processExpression(
                        graph, finder, scope, frame, ctx, arg);
                    DFNode node = ctx.getRValue();
                    argList.add(node);
                    typeList.add(node.getType());
                }
                DFNode[] args = new DFNode[argList.size()];
                argList.toArray(args);
                DFType[] argTypes = new DFType[typeList.size()];
                typeList.toArray(argTypes);
                DFMethod method = klass.lookupMethod(invoke.getName(), argTypes);
                if (method == null) {
                    String id = invoke.getName().getIdentifier();
                    DFMethod fallback = new DFMethod(
                        klass, null, id, false, argTypes, null);
                    Logger.error("Fallback method: "+klass+": "+fallback);
                    method = fallback;
                }
                DFMethod methods[] = method.getOverrides();
                MethodCallNode call = new MethodCallNode(
                    graph, scope, methods, invoke, obj);
                call.setArgs(args);
                ctx.setRValue(call);
                if (call.exception != null) {
                    DFFrame dstFrame = frame.find(DFFrame.TRY);
                    frame.addExit(new DFExit(dstFrame, call.exception));
                }

            } else if (expr instanceof SuperMethodInvocation) {
                SuperMethodInvocation sinvoke = (SuperMethodInvocation)expr;
                DFNode obj = ctx.get(scope.lookupThis());
                List<DFNode> argList = new ArrayList<DFNode>();
                List<DFType> typeList = new ArrayList<DFType>();
                for (Expression arg : (List<Expression>) sinvoke.arguments()) {
                    ctx = processExpression(
                        graph, finder, scope, frame, ctx, arg);
                    DFNode node = ctx.getRValue();
                    argList.add(node);
                    typeList.add(node.getType());
                }
                DFNode[] args = new DFNode[argList.size()];
                argList.toArray(args);
                DFType[] argTypes = new DFType[typeList.size()];
                typeList.toArray(argTypes);
                DFClass klass = finder.resolveClass(obj.getType());
                DFClass baseKlass = klass.getBase();
                DFMethod method = baseKlass.lookupMethod(sinvoke.getName(), argTypes);
                if (method == null) {
                    String id = sinvoke.getName().getIdentifier();
                    DFMethod fallback = new DFMethod(
                        baseKlass, null, id, false, argTypes, null);
                    Logger.error("Fallback method: "+baseKlass+": "+fallback);
                    method = fallback;
                }
                DFMethod methods[] = new DFMethod[] { method };
                MethodCallNode call = new MethodCallNode(
                    graph, scope, methods, sinvoke, obj);
                call.setArgs(args);
                ctx.setRValue(call);
                if (call.exception != null) {
                    DFFrame dstFrame = frame.find(DFFrame.TRY);
                    frame.addExit(new DFExit(dstFrame, call.exception));
                }

            } else if (expr instanceof ArrayCreation) {
                ArrayCreation ac = (ArrayCreation)expr;
                DFType elemType = finder.resolve(ac.getType().getElementType());
                for (Expression dim : (List<Expression>) ac.dimensions()) {
                    // XXX ctx.getRValue() is not used (for now).
                    ctx = processExpression(
                        graph, finder, scope, frame, ctx, dim);
                }
                ArrayInitializer init = ac.getInitializer();
                if (init != null) {
                    ctx = processExpression(
                        graph, finder, scope, frame, ctx, init);
                } else {
                    ctx.setRValue(new ArrayValueNode(graph, scope, elemType, ac));
                }

            } else if (expr instanceof ArrayInitializer) {
                ArrayInitializer init = (ArrayInitializer)expr;
                ArrayValueNode arr = null;
                for (Expression expr1 : (List<Expression>) init.expressions()) {
                    ctx = processExpression(
                        graph, finder, scope, frame, ctx, expr1);
                    DFNode value = ctx.getRValue();
                    if (arr == null) {
                        arr = new ArrayValueNode(
                            graph, scope, value.getType(), init);
                    }
                    arr.addValue(value);
                }
                ctx.setRValue(arr);
                // XXX array ref is not used.

            } else if (expr instanceof ArrayAccess) {
                ArrayAccess aa = (ArrayAccess)expr;
                ctx = processExpression(
                    graph, finder, scope, frame, ctx, aa.getArray());
                DFNode array = ctx.getRValue();
                ctx = processExpression(
                    graph, finder, scope, frame, ctx, aa.getIndex());
                DFVarRef ref = scope.lookupArray(array.getType());
                DFNode index = ctx.getRValue();
                DFNode node = new ArrayAccessNode(
                    graph, scope, ref, aa, array, index);
                node.accept(ctx.get(ref));
                ctx.setRValue(node);

            } else if (expr instanceof FieldAccess) {
                FieldAccess fa = (FieldAccess)expr;
                Expression expr1 = fa.getExpression();
                DFNode obj = null;
                DFClass klass = null;
                if (expr1 instanceof Name) {
                    try {
                        klass = finder.lookupClass((Name)expr1);
                    } catch (EntityNotFound e) {
                    }
                }
                if (klass == null) {
                    ctx = processExpression(
                        graph, finder, scope, frame, ctx, expr1);
                    obj = ctx.getRValue();
                    klass = finder.resolveClass(obj.getType());
                }
                SimpleName fieldName = fa.getName();
                DFVarRef ref = klass.lookupField(fieldName);
                DFNode node = new FieldAccessNode(graph, scope, ref, fa, obj);
                node.accept(ctx.get(ref));
                ctx.setRValue(node);

            } else if (expr instanceof SuperFieldAccess) {
                SuperFieldAccess sfa = (SuperFieldAccess)expr;
                SimpleName fieldName = sfa.getName();
                DFNode obj = ctx.get(scope.lookupThis());
                DFClass klass = finder.resolveClass(obj.getType());
                DFVarRef ref = klass.lookupField(fieldName);
                DFNode node = new FieldAccessNode(graph, scope, ref, sfa, obj);
                node.accept(ctx.get(ref));
                ctx.setRValue(node);

            } else if (expr instanceof CastExpression) {
                CastExpression cast = (CastExpression)expr;
                DFType type = finder.resolve(cast.getType());
                ctx = processExpression(
                    graph, finder, scope, frame, ctx, cast.getExpression());
                DFNode node = new TypeCastNode(graph, scope, type, cast);
                node.accept(ctx.getRValue());
                ctx.setRValue(node);

            } else if (expr instanceof ClassInstanceCreation) {
                ClassInstanceCreation cstr = (ClassInstanceCreation)expr;
                AnonymousClassDeclaration anonDecl = cstr.getAnonymousClassDeclaration();
                DFType instType;
                if (anonDecl != null) {
                    String id = "anonymous";
                    DFClass baseKlass = finder.resolveClass(cstr.getType());
                    DFTypeSpace anonSpace = new DFTypeSpace(scope.getFullName()+"/"+id);
                    DFClass anonKlass = new DFAnonClass(id, anonSpace, scope, baseKlass);
                    anonSpace.addClass(anonKlass);
                    for (BodyDeclaration body :
                             (List<BodyDeclaration>) anonDecl.bodyDeclarations()) {
                        anonSpace.build(null, body, scope);
                    }
                    try {
                        for (BodyDeclaration body :
                                 (List<BodyDeclaration>) anonDecl.bodyDeclarations()) {
                            anonKlass.build(finder, body);
                        }
                        anonKlass.addOverrides();
                        processBodyDeclarations(
                            finder, anonKlass, anonDecl.bodyDeclarations());
                        instType = anonKlass;
                    } catch (EntityNotFound e) {
                        instType = null; // XXX what happened?
                    }
                } else {
                    instType = finder.resolve(cstr.getType());
                }
                Expression expr1 = cstr.getExpression();
                DFNode obj = null;
                if (expr1 != null) {
                    ctx = processExpression(
                        graph, finder, scope, frame, ctx, expr1);
                    obj = ctx.getRValue();
                }
                List<DFNode> argList = new ArrayList<DFNode>();
                for (Expression arg : (List<Expression>) cstr.arguments()) {
                    ctx = processExpression(
                        graph, finder, scope, frame, ctx, arg);
                    argList.add(ctx.getRValue());
                }
                DFNode[] args = new DFNode[argList.size()];
                argList.toArray(args);
                CreateObjectNode call = new CreateObjectNode(
                    graph, scope, instType, cstr, obj);
                call.setArgs(args);
                ctx.setRValue(call);

            } else if (expr instanceof ConditionalExpression) {
                ConditionalExpression cond = (ConditionalExpression)expr;
                ctx = processExpression(
                    graph, finder, scope, frame, ctx, cond.getExpression());
                DFNode condValue = ctx.getRValue();
                ctx = processExpression(
                    graph, finder, scope, frame, ctx, cond.getThenExpression());
                DFNode trueValue = ctx.getRValue();
                ctx = processExpression(
                    graph, finder, scope, frame, ctx, cond.getElseExpression());
                DFNode falseValue = ctx.getRValue();
                JoinNode join = new JoinNode(graph, scope, null, expr, condValue);
                join.recv(true, trueValue);
                join.recv(false, falseValue);
                ctx.setRValue(join);

            } else if (expr instanceof InstanceofExpression) {
                InstanceofExpression instof = (InstanceofExpression)expr;
                DFType type = finder.resolve(instof.getRightOperand());
                ctx = processExpression(
                    graph, finder, scope, frame, ctx,
                    instof.getLeftOperand());
                DFNode node = new InstanceofNode(graph, scope, instof, type);
                node.accept(ctx.getRValue());
                ctx.setRValue(node);

            } else if (expr instanceof LambdaExpression) {
                LambdaExpression lambda = (LambdaExpression)expr;
                String id = "lambda";
                ASTNode body = lambda.getBody();
                DFTypeSpace anonSpace = new DFTypeSpace(scope.getFullName()+"/"+id);
                DFClass anonKlass = new DFAnonClass(id, anonSpace, scope);
                if (body instanceof Statement) {
                    // XXX TODO Statement lambda
                } else if (body instanceof Expression) {
                    // XXX TODO Expresssion lambda
                } else {
                    throw new UnsupportedSyntax(body);
                }
                CreateObjectNode call = new CreateObjectNode(
                    graph, scope, anonKlass, lambda, null);
                ctx.setRValue(call);

            } else if (expr instanceof MethodReference) {
                // MethodReference
                //  CreationReference
                //  ExpressionMethodReference
                //  SuperMethodReference
                //  TypeMethodReference
                MethodReference mref = (MethodReference)expr;
                DFTypeSpace anonSpace = new DFTypeSpace("MethodRef");
                DFClass anonKlass = new DFAnonClass("methodref", anonSpace, scope);
                // XXX TODO method ref
                CreateObjectNode call = new CreateObjectNode(
                    graph, scope, anonKlass, mref, null);
                ctx.setRValue(call);

            } else {
                // ???
                throw new UnsupportedSyntax(expr);
            }
        } catch (EntityNotFound e) {
            e.setAst(expr);
            throw e;
        }

        return ctx;
    }

    /**
     * Creates an assignment node.
     */
    @SuppressWarnings("unchecked")
    public DFContext processAssignment(
        DFGraph graph, DFTypeFinder finder, DFVarScope scope,
        DFFrame frame, DFContext ctx, Expression expr)
        throws UnsupportedSyntax, EntityNotFound {

        if (expr instanceof Name) {
            Name name = (Name)expr;
            if (name.isSimpleName()) {
                DFVarRef ref = scope.lookupVar((SimpleName)name);
                ctx.setLValue(new SingleAssignNode(graph, scope, ref, expr));
            } else {
                QualifiedName qname = (QualifiedName)name;
                DFNode obj = null;
                DFClass klass;
                try {
                    // Try assuming it's a variable access.
                    ctx = processExpression(
                        graph, finder, scope, frame, ctx, qname.getQualifier());
                    obj = ctx.getRValue();
                    klass = finder.resolveClass(obj.getType());
                } catch (EntityNotFound e) {
                    // Turned out it's a class variable.
                    klass = finder.lookupClass(qname.getQualifier());
                }
                SimpleName fieldName = qname.getName();
                DFVarRef ref = klass.lookupField(fieldName);
                ctx.setLValue(new FieldAssignNode(graph, scope, ref, expr, obj));
            }

        } else if (expr instanceof ArrayAccess) {
            ArrayAccess aa = (ArrayAccess)expr;
            ctx = processExpression(
                graph, finder, scope, frame, ctx, aa.getArray());
            DFNode array = ctx.getRValue();
            ctx = processExpression(
                graph, finder, scope, frame, ctx, aa.getIndex());
            DFVarRef ref = scope.lookupArray(array.getType());
            DFNode index = ctx.getRValue();
            DFNode node = new ArrayAssignNode(
                graph, scope, ref, expr, array, index);
            ctx.setLValue(node);

        } else if (expr instanceof FieldAccess) {
            FieldAccess fa = (FieldAccess)expr;
            Expression expr1 = fa.getExpression();
            ctx = processExpression(
                graph, finder, scope, frame, ctx, expr1);
            DFNode obj = ctx.getRValue();
            DFClass klass = finder.resolveClass(obj.getType());
            SimpleName fieldName = fa.getName();
            DFVarRef ref = klass.lookupField(fieldName);
            ctx.setLValue(new FieldAssignNode(graph, scope, ref, expr, obj));

        } else {
            throw new UnsupportedSyntax(expr);
        }

        return ctx;
    }

    /**
     * Creates a new variable node.
     */
    public DFContext processVariableDeclaration(
        DFGraph graph, DFTypeFinder finder, DFVarScope scope,
        DFFrame frame, DFContext ctx, List<VariableDeclarationFragment> frags)
        throws UnsupportedSyntax, EntityNotFound {

        for (VariableDeclarationFragment frag : frags) {
            DFVarRef ref = scope.lookupVar(frag.getName());
            Expression init = frag.getInitializer();
            if (init != null) {
                ctx = processExpression(
                    graph, finder, scope, frame, ctx, init);
                DFNode assign = new SingleAssignNode(graph, scope, ref, frag);
                assign.accept(ctx.getRValue());
                ctx.set(assign);
            }
        }
        return ctx;
    }

    /**
     * Expands the graph for the loop variables.
     */
    public DFContext processLoop(
        DFGraph graph, DFVarScope scope,
        DFFrame frame, DFContext ctx,
        ASTNode ast, DFNode condValue,
        DFFrame loopFrame, DFContext loopCtx, boolean preTest)
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
            DFNode src = ctx.get(ref);
            LoopBeginNode begin = new LoopBeginNode(graph, scope, ref, ast, src);
            LoopRepeatNode repeat = new LoopRepeatNode(graph, scope, ref, ast);
            LoopEndNode end = new LoopEndNode(graph, scope, ref, ast, condValue);
            begin.setEnd(end);
            end.setBegin(begin);
            begins.put(ref, begin);
            ends.put(ref, end);
            repeats.put(ref, repeat);
        }

        if (preTest) {  // Repeat -> [S] -> Begin -> End
            // Connect the repeats to the loop inputs.
            for (DFNode input : loopCtx.getFirsts()) {
                if (input.hasInput()) continue;
                DFVarRef ref = input.getRef();
                DFNode src = repeats.get(ref);
                if (src == null) {
                    src = ctx.get(ref);
                }
                input.accept(src);
            }
            // Connect the loop outputs to the begins.
            for (DFVarRef ref : loopFrame.getOutputs()) {
                DFNode output = loopCtx.get(ref);
                if (output != null) {
                    LoopBeginNode begin = begins.get(ref);
                    if (begin != null) {
                        begin.setRepeat(output);
                    } else {
                        //assert !loopRefs.contains(ref);
                        ctx.set(output);
                    }
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
            for (DFNode input : loopCtx.getFirsts()) {
                if (input.hasInput()) continue;
                DFVarRef ref = input.getRef();
                DFNode src = begins.get(ref);
                if (src == null) {
                    src = ctx.get(ref);
                }
                input.accept(src);
            }
            // Connect the loop outputs to the ends.
            for (DFVarRef ref : loopFrame.getOutputs()) {
                DFNode output = loopCtx.get(ref);
                if (output != null) {
                    DFNode dst = ends.get(ref);
                    if (dst != null) {
                        dst.accept(output);
                    } else {
                        //assert !loopRefs.contains(ref);
                        ctx.set(output);
                    }
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
        for (DFExit exit : loopFrame.getExits()) {
            if (exit.isCont()) {
                DFNode node = exit.getNode();
                DFNode end = ends.get(node.getRef());
                if (end == null) {
                    end = ctx.get(node.getRef());
                }
                if (node instanceof JoinNode) {
                    ((JoinNode)node).close(end);
                }
                ends.put(node.getRef(), node);
            } else {
                frame.addExit(exit);
            }
        }

        // Closing the loop.
        for (DFVarRef ref : loopRefs) {
            DFNode end = ends.get(ref);
            LoopRepeatNode repeat = repeats.get(ref);
            ctx.set(end);
            repeat.setLoop(end);
        }

        return ctx;
    }

    /// Statement processors.
    @SuppressWarnings("unchecked")
    public DFContext processBlock(
        DFGraph graph, DFTypeFinder finder, DFVarScope scope,
        DFFrame frame, DFContext ctx, Block block)
        throws UnsupportedSyntax, EntityNotFound {
        DFVarScope childScope = scope.getChildByAST(block);
        for (Statement cstmt : (List<Statement>) block.statements()) {
            ctx = processStatement(
                graph, finder, childScope, frame, ctx, cstmt);
        }
        return ctx;
    }

    @SuppressWarnings("unchecked")
    public DFContext processVariableDeclarationStatement(
        DFGraph graph, DFTypeFinder finder, DFVarScope scope,
        DFFrame frame, DFContext ctx, VariableDeclarationStatement varStmt)
        throws UnsupportedSyntax, EntityNotFound {
        return processVariableDeclaration(
            graph, finder, scope, frame, ctx, varStmt.fragments());
    }

    public DFContext processExpressionStatement(
        DFGraph graph, DFTypeFinder finder, DFVarScope scope,
        DFFrame frame, DFContext ctx, ExpressionStatement exprStmt)
        throws UnsupportedSyntax, EntityNotFound {
        Expression expr = exprStmt.getExpression();
        return processExpression(
            graph, finder, scope, frame, ctx, expr);
    }

    public DFContext processIfStatement(
        DFGraph graph, DFTypeFinder finder, DFVarScope scope,
        DFFrame frame, DFContext ctx, IfStatement ifStmt)
        throws UnsupportedSyntax, EntityNotFound {
        Expression expr = ifStmt.getExpression();
        ctx = processExpression(graph, finder, scope, frame, ctx, expr);
        DFNode condValue = ctx.getRValue();

        Statement thenStmt = ifStmt.getThenStatement();
        DFContext thenCtx = new DFContext(graph, scope);
        DFFrame thenFrame = frame.getChildByAST(thenStmt);
        thenCtx = processStatement(
            graph, finder, scope, thenFrame, thenCtx, thenStmt);

        Statement elseStmt = ifStmt.getElseStatement();
        DFContext elseCtx = null;
        DFFrame elseFrame = null;
        if (elseStmt != null) {
            elseFrame = frame.getChildByAST(elseStmt);
            elseCtx = new DFContext(graph, scope);
            elseCtx = processStatement(
                graph, finder, scope, elseFrame, elseCtx, elseStmt);
        }

        // Combines two contexts into one.
        // A JoinNode is added to each variable.

        // outRefs: all the references from both contexts.
        List<DFVarRef> outRefs = new ArrayList<DFVarRef>();
        if (thenFrame != null && thenCtx != null) {
            for (DFNode src : thenCtx.getFirsts()) {
                if (src.hasInput()) continue;
                src.accept(ctx.get(src.getRef()));
            }
            outRefs.addAll(Arrays.asList(thenFrame.getOutputs()));
        }
        if (elseFrame != null && elseCtx != null) {
            for (DFNode src : elseCtx.getFirsts()) {
                if (src.hasInput()) continue;
                src.accept(ctx.get(src.getRef()));
            }
            outRefs.addAll(Arrays.asList(elseFrame.getOutputs()));
        }

        // Attach a JoinNode to each variable.
        Set<DFVarRef> used = new HashSet<DFVarRef>();
        for (DFVarRef ref : outRefs) {
            if (used.contains(ref)) continue;
            used.add(ref);
            JoinNode join = new JoinNode(graph, scope, ref, ifStmt, condValue);
            if (thenCtx != null) {
                DFNode dst = thenCtx.get(ref);
                if (dst != null) {
                    join.recv(true, dst);
                }
            }
            if (elseCtx != null) {
                DFNode dst = elseCtx.get(ref);
                if (dst != null) {
                    join.recv(false, dst);
                }
            }
            if (!join.isClosed()) {
                join.close(ctx.get(ref));
            }
            ctx.set(join);
        }

        // Take care of exits.
        if (thenFrame != null) {
            for (DFExit exit : thenFrame.getExits()) {
                DFNode node = exit.getNode();
                JoinNode join = new JoinNode(
                    graph, scope, node.getRef(), null, condValue);
                join.recv(true, node);
                frame.addExit(exit.wrap(join));
            }
            thenFrame.close(thenCtx);
        }
        if (elseFrame != null) {
            for (DFExit exit : elseFrame.getExits()) {
                DFNode node = exit.getNode();
                JoinNode join = new JoinNode(
                    graph, scope, node.getRef(), null, condValue);
                join.recv(false, node);
                frame.addExit(exit.wrap(join));
            }
            elseFrame.close(elseCtx);
        }

        return ctx;
    }

    private DFContext processCaseStatement(
        DFGraph graph, DFVarScope scope,
        DFFrame frame, DFContext ctx, ASTNode apt,
        DFNode caseNode, DFContext caseCtx) {

        for (DFNode src : caseCtx.getFirsts()) {
            if (src.hasInput()) continue;
            src.accept(ctx.get(src.getRef()));
        }

        for (DFVarRef ref : frame.getOutputs()) {
            DFNode dst = caseCtx.get(ref);
            if (dst != null) {
                JoinNode join = new JoinNode(graph, scope, ref, apt, caseNode);
                join.recv(true, dst);
                join.close(ctx.get(ref));
                ctx.set(join);
            }
        }

        return ctx;
    }

    @SuppressWarnings("unchecked")
    public DFContext processSwitchStatement(
        DFGraph graph, DFTypeFinder finder, DFVarScope scope,
        DFFrame frame, DFContext ctx, SwitchStatement switchStmt)
        throws UnsupportedSyntax, EntityNotFound {
        DFVarScope switchScope = scope.getChildByAST(switchStmt);
        ctx = processExpression(
            graph, finder, scope,
            frame, ctx, switchStmt.getExpression());
        DFNode switchValue = ctx.getRValue();
        DFFrame switchFrame = frame.getChildByAST(switchStmt);

        SwitchCase switchCase = null;
        CaseNode caseNode = null;
        DFContext caseCtx = null;
        for (Statement stmt : (List<Statement>) switchStmt.statements()) {
            if (stmt instanceof SwitchCase) {
                if (caseCtx != null) {
                    // switchCase, caseNode and caseCtx must be non-null.
                    ctx = processCaseStatement(
                        graph, switchScope, switchFrame,
                        ctx, switchCase, caseNode, caseCtx);
                }
                switchCase = (SwitchCase)stmt;
                caseNode = new CaseNode(graph, switchScope, stmt);
                caseNode.accept(switchValue);
                caseCtx = new DFContext(graph, switchScope);
                Expression expr = switchCase.getExpression();
                if (expr != null) {
                    ctx = processExpression(
                        graph, finder, switchScope, switchFrame, ctx, expr);
                    caseNode.addMatch(ctx.getRValue());
                } else {
                    // "default" case.
                }
            } else {
                if (caseCtx == null) {
                    // no "case" statement.
                    throw new UnsupportedSyntax(stmt);
                }
                caseCtx = processStatement(
                    graph, finder, switchScope,
                    switchFrame, caseCtx, stmt);
            }
        }
        if (caseCtx != null) {
            ctx = processCaseStatement(
                graph, switchScope, switchFrame,
                ctx, switchCase, caseNode, caseCtx);
        }
        switchFrame.close(ctx);
        return ctx;
    }

    public DFContext processWhileStatement(
        DFGraph graph, DFTypeFinder finder, DFVarScope scope,
        DFFrame frame, DFContext ctx, WhileStatement whileStmt)
        throws UnsupportedSyntax, EntityNotFound {
        DFVarScope loopScope = scope.getChildByAST(whileStmt);
        DFFrame loopFrame = frame.getChildByAST(whileStmt);
        DFContext loopCtx = new DFContext(graph, loopScope);
        loopCtx = processExpression(
            graph, finder, scope, loopFrame, loopCtx,
            whileStmt.getExpression());
        DFNode condValue = loopCtx.getRValue();
        loopCtx = processStatement(
            graph, finder, loopScope, loopFrame, loopCtx,
            whileStmt.getBody());
        ctx = processLoop(
            graph, loopScope, frame, ctx, whileStmt,
            condValue, loopFrame, loopCtx, true);
        loopFrame.close(ctx);
        return ctx;
    }

    public DFContext processDoStatement(
        DFGraph graph, DFTypeFinder finder, DFVarScope scope,
        DFFrame frame, DFContext ctx, DoStatement doStmt)
        throws UnsupportedSyntax, EntityNotFound {
        DFVarScope loopScope = scope.getChildByAST(doStmt);
        DFFrame loopFrame = frame.getChildByAST(doStmt);
        DFContext loopCtx = new DFContext(graph, loopScope);
        loopCtx = processStatement(
            graph, finder, loopScope, loopFrame, loopCtx,
            doStmt.getBody());
        loopCtx = processExpression(
            graph, finder, loopScope, loopFrame, loopCtx,
            doStmt.getExpression());
        DFNode condValue = loopCtx.getRValue();
        ctx = processLoop(
            graph, loopScope, frame, ctx, doStmt,
            condValue, loopFrame, loopCtx, false);
        loopFrame.close(ctx);
        return ctx;
    }

    @SuppressWarnings("unchecked")
    public DFContext processForStatement(
        DFGraph graph, DFTypeFinder finder, DFVarScope scope,
        DFFrame frame, DFContext ctx, ForStatement forStmt)
        throws UnsupportedSyntax, EntityNotFound {
        DFVarScope loopScope = scope.getChildByAST(forStmt);
        DFFrame loopFrame = frame.getChildByAST(forStmt);
        DFContext loopCtx = new DFContext(graph, loopScope);
        for (Expression init : (List<Expression>) forStmt.initializers()) {
            ctx = processExpression(
                graph, finder, loopScope, frame, ctx, init);
        }
        Expression expr = forStmt.getExpression();
        DFNode condValue;
        if (expr != null) {
            loopCtx = processExpression(
                graph, finder, loopScope, loopFrame, loopCtx, expr);
            condValue = loopCtx.getRValue();
        } else {
            condValue = new ConstNode(graph, loopScope, DFBasicType.BOOLEAN, null, "true");
        }
        loopCtx = processStatement(
            graph, finder, loopScope, loopFrame, loopCtx,
            forStmt.getBody());
        for (Expression update : (List<Expression>) forStmt.updaters()) {
            loopCtx = processExpression(
                graph, finder, loopScope, loopFrame, loopCtx, update);
        }
        ctx = processLoop(
            graph, loopScope, frame, ctx, forStmt,
            condValue, loopFrame, loopCtx, true);
        loopFrame.close(ctx);
        return ctx;
    }

    @SuppressWarnings("unchecked")
    public DFContext processEnhancedForStatement(
        DFGraph graph, DFTypeFinder finder, DFVarScope scope,
        DFFrame frame, DFContext ctx, EnhancedForStatement eForStmt)
        throws UnsupportedSyntax, EntityNotFound {
        DFVarScope loopScope = scope.getChildByAST(eForStmt);
        DFFrame loopFrame = frame.getChildByAST(eForStmt);
        DFContext loopCtx = new DFContext(graph, loopScope);
        Expression expr = eForStmt.getExpression();
        loopCtx = processExpression(
            graph, finder, loopScope, loopFrame, loopCtx, expr);
        SingleVariableDeclaration decl = eForStmt.getParameter();
        DFVarRef ref = loopScope.lookupVar(decl.getName());
        DFNode iterValue = new IterNode(graph, loopScope, ref, expr);
        iterValue.accept(loopCtx.getRValue());
        SingleAssignNode assign = new SingleAssignNode(graph, loopScope, ref, expr);
        assign.accept(iterValue);
        ctx.set(assign);
        loopCtx = processStatement(
            graph, finder, loopScope, loopFrame, loopCtx,
            eForStmt.getBody());
        ctx = processLoop(
            graph, loopScope, frame, ctx, eForStmt,
            iterValue, loopFrame, loopCtx, true);
        loopFrame.close(ctx);
        return ctx;
    }

    @SuppressWarnings("unchecked")
    public DFContext processStatement(
        DFGraph graph, DFTypeFinder finder, DFVarScope scope,
        DFFrame frame, DFContext ctx, Statement stmt)
        throws UnsupportedSyntax, EntityNotFound {

        if (stmt instanceof AssertStatement) {
            // XXX Ignore asserts.

        } else if (stmt instanceof Block) {
            ctx = processBlock(
                graph, finder, scope, frame, ctx, (Block)stmt);

        } else if (stmt instanceof EmptyStatement) {

        } else if (stmt instanceof VariableDeclarationStatement) {
            ctx = processVariableDeclarationStatement(
                graph, finder, scope, frame, ctx,
                (VariableDeclarationStatement)stmt);

        } else if (stmt instanceof ExpressionStatement) {
            ctx = processExpressionStatement(
                graph, finder, scope, frame, ctx,
                (ExpressionStatement)stmt);

        } else if (stmt instanceof IfStatement) {
            ctx = processIfStatement(
                graph, finder, scope, frame, ctx,
                (IfStatement)stmt);

        } else if (stmt instanceof SwitchStatement) {
            ctx = processSwitchStatement(
                graph, finder, scope, frame, ctx,
                (SwitchStatement)stmt);

        } else if (stmt instanceof SwitchCase) {
            // Invalid "case" placement.
            throw new UnsupportedSyntax(stmt);

        } else if (stmt instanceof WhileStatement) {
            ctx = processWhileStatement(
                graph, finder, scope, frame, ctx,
                (WhileStatement)stmt);

        } else if (stmt instanceof DoStatement) {
            ctx = processDoStatement(
                graph, finder, scope, frame, ctx,
                (DoStatement)stmt);

        } else if (stmt instanceof ForStatement) {
            ctx = processForStatement(
                graph, finder, scope, frame, ctx,
                (ForStatement)stmt);

        } else if (stmt instanceof EnhancedForStatement) {
            ctx = processEnhancedForStatement(
                graph, finder, scope, frame, ctx,
                (EnhancedForStatement)stmt);

        } else if (stmt instanceof ReturnStatement) {
            ReturnStatement rtrnStmt = (ReturnStatement)stmt;
            DFFrame dstFrame = frame.find(DFFrame.METHOD);
            Expression expr = rtrnStmt.getExpression();
            if (expr != null) {
                ctx = processExpression(
                    graph, finder, scope, frame, ctx, expr);
                ReturnNode rtrn = new ReturnNode(graph, scope, rtrnStmt);
                rtrn.accept(ctx.getRValue());
                ctx.set(rtrn);
                frame.addExit(new DFExit(dstFrame, rtrn));
            }
            for (DFVarRef ref : dstFrame.getOutputs()) {
                frame.addExit(new DFExit(dstFrame, ctx.get(ref)));
            }

        } else if (stmt instanceof BreakStatement) {
            BreakStatement breakStmt = (BreakStatement)stmt;
            SimpleName labelName = breakStmt.getLabel();
            String dstLabel = (labelName != null)?
                labelName.getIdentifier() : DFFrame.LOOP;
            DFFrame dstFrame = frame.find(dstLabel);
            for (DFVarRef ref : dstFrame.getOutputs()) {
                frame.addExit(new DFExit(dstFrame, ctx.get(ref)));
            }

        } else if (stmt instanceof ContinueStatement) {
            ContinueStatement contStmt = (ContinueStatement)stmt;
            SimpleName labelName = contStmt.getLabel();
            String dstLabel = (labelName != null)?
                labelName.getIdentifier() : DFFrame.LOOP;
            DFFrame dstFrame = frame.find(dstLabel);
            for (DFVarRef ref : dstFrame.getOutputs()) {
                frame.addExit(new DFExit(dstFrame, ctx.get(ref), true));
            }

        } else if (stmt instanceof LabeledStatement) {
            LabeledStatement labeledStmt = (LabeledStatement)stmt;
            DFFrame labeledFrame = frame.getChildByAST(labeledStmt);
            ctx = processStatement(
                graph, finder, scope, labeledFrame,
                ctx, labeledStmt.getBody());
            labeledFrame.close(ctx);

        } else if (stmt instanceof SynchronizedStatement) {
            SynchronizedStatement syncStmt = (SynchronizedStatement)stmt;
            ctx = processStatement(
                graph, finder, scope, frame,
                ctx, syncStmt.getBody());

        } else if (stmt instanceof TryStatement) {
            // XXX Ignore catch statements (for now).
            TryStatement tryStmt = (TryStatement)stmt;
            DFFrame tryFrame = frame.getChildByAST(tryStmt);
            ctx = processStatement(
                graph, finder, scope, tryFrame,
                ctx, tryStmt.getBody());
            tryFrame.close(ctx);
            for (CatchClause cc :
                     (List<CatchClause>) tryStmt.catchClauses()) {
                SingleVariableDeclaration decl = cc.getException();
                DFVarScope childScope = scope.getChildByAST(cc);
                DFVarRef ref = childScope.lookupVar(decl.getName());
                //this.addOutput(ref);
                ctx = processStatement(
                    graph, finder, childScope, frame, ctx, cc.getBody());
            }
            Block finBlock = tryStmt.getFinally();
            if (finBlock != null) {
                ctx = processStatement(
                    graph, finder, scope, frame, ctx, finBlock);
            }

        } else if (stmt instanceof ThrowStatement) {
            ThrowStatement throwStmt = (ThrowStatement)stmt;
            ctx = processExpression(
                graph, finder, scope, frame,
                ctx, throwStmt.getExpression());
            ExceptionNode exception = new ExceptionNode(
                graph, scope, stmt, ctx.getRValue());
            DFFrame dstFrame = frame.find(DFFrame.TRY);
            if (dstFrame != null) {
                frame.addExit(new DFExit(dstFrame, exception));
                for (DFVarRef ref : dstFrame.getOutputs()) {
                    frame.addExit(new DFExit(dstFrame, ctx.get(ref)));
                }
            }

        } else if (stmt instanceof ConstructorInvocation) {
            // XXX Use MethodCallNode.
            ConstructorInvocation ci = (ConstructorInvocation)stmt;
            for (Expression arg : (List<Expression>) ci.arguments()) {
                ctx = processExpression(
                    graph, finder, scope, frame, ctx, arg);
            }

        } else if (stmt instanceof SuperConstructorInvocation) {
            // XXX Use MethodCallNode.
            SuperConstructorInvocation sci = (SuperConstructorInvocation)stmt;
            for (Expression arg : (List<Expression>) sci.arguments()) {
                ctx = processExpression(
                    graph, finder, scope, frame, ctx, arg);
            }

        } else if (stmt instanceof TypeDeclarationStatement) {
            // Ignore TypeDeclarationStatement because
            // it was eventually picked up as MethodDeclaration.

        } else {
            throw new UnsupportedSyntax(stmt);
        }

        return ctx;
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
            DFClass klass = typeSpace.getClass(typeDecl.getName());
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
                    DFClass klass = this.rootSpace.getClass(name);
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
        DFGraph graph, DFTypeFinder finder, DFClass klass,
        DFFrame frame, FieldDeclaration fieldDecl)
        throws UnsupportedSyntax, EntityNotFound {
        for (VariableDeclarationFragment frag :
                 (List<VariableDeclarationFragment>) fieldDecl.fragments()) {
            DFVarRef ref = klass.lookupField(frag.getName());
            Expression init = frag.getInitializer();
            if (init != null) {
                DFVarScope scope = klass.getScope();
                DFContext ctx = new DFContext(graph, scope);
                ctx = processExpression(
                    graph, finder, scope, frame, ctx, init);
                DFNode assign = new SingleAssignNode(graph, scope, ref, frag);
                assign.accept(ctx.getRValue());
            }
        }
    }

    @SuppressWarnings("unchecked")
    public DFGraph processMethodDeclaration(
        DFTypeFinder finder, DFClass klass,
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
        DFVarScope scope = new DFVarScope(klass.getScope(), methodDecl.getName());
        // add a typespace for inline classes.
        DFTypeSpace typeSpace = new DFTypeSpace(scope.getFullName()+"/inline");
        finder = new DFTypeFinder(finder, typeSpace);
        try {
            // Setup an initial space.
            List<DFClass> classes = new ArrayList<DFClass>();
            typeSpace.build(classes, methodDecl.getBody(), scope);
            this.buildInlineClasses(typeSpace, finder, methodDecl.getBody());
            // Add overrides.
            for (DFClass klass1 : classes) {
                klass1.addOverrides();
            }
            scope.build(finder, methodDecl);
            //scope.dump();
            DFFrame frame = new DFFrame(DFFrame.METHOD);
            frame.build(finder, scope, methodDecl.getBody());

            DFGraph graph = new DFGraph(scope, frame, method);
            DFContext ctx = new DFContext(graph, scope);
            // XXX Ignore isContructor().
            // XXX Ignore isVarargs().
            int i = 0;
            for (SingleVariableDeclaration decl :
                     (List<SingleVariableDeclaration>) methodDecl.parameters()) {
                // XXX Ignore modifiers and dimensions.
                DFVarRef ref = scope.lookupVar(decl.getName());
                DFNode param = new ArgNode(graph, scope, ref, decl, i++);
                DFNode assign = new SingleAssignNode(graph, scope, ref, decl);
                assign.accept(param);
                ctx.set(assign);
            }

            // Process the function body.
            ctx = processStatement(
                graph, finder, scope, frame, ctx, methodDecl.getBody());
            frame.close(ctx);
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
        DFTypeFinder finder, DFClass klass,
        List<BodyDeclaration> decls)
        throws EntityNotFound {
        DFFrame frame = new DFFrame(DFFrame.CLASS);
        DFVarScope scope = klass.getScope();
        DFTypeSpace typeSpace = klass.getChildSpace();
        DFGraph classGraph = new DFGraph(scope, frame);
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
                    scope.build(finder, block);
                    frame.build(finder, scope, block);
                    DFContext ctx = new DFContext(classGraph, scope);
                    ctx = processStatement(
                        classGraph, finder, scope,
                        frame, ctx, block);
                    frame.close(ctx);
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
        DFClass klass = typeSpace.getClass(typeDecl.getName());
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
        List<DFClass> classes, CompilationUnit cunit) {
        DFTypeSpace typeSpace = this.rootSpace.lookupSpace(cunit.getPackage());
        DFGlobalVarScope global = this.rootSpace.getGlobalScope();
        try {
            typeSpace.build(classes, cunit, global);
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
            for (AbstractTypeDeclaration abstTypeDecl :
                     (List<AbstractTypeDeclaration>) cunit.types()) {
                if (abstTypeDecl instanceof TypeDeclaration) {
                    TypeDeclaration typeDecl = (TypeDeclaration)abstTypeDecl;
                    DFClass klass = typeSpace.getClass(typeDecl.getName());
                    klass.build(finder, typeDecl);
                } else if (abstTypeDecl instanceof EnumDeclaration) {
                    // XXX enum not supported.
                }
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
	for (AbstractTypeDeclaration abstTypeDecl :
		 (List<AbstractTypeDeclaration>) cunit.types()) {
            if (abstTypeDecl instanceof TypeDeclaration) {
                TypeDeclaration typeDecl = (TypeDeclaration)abstTypeDecl;
                processTypeDeclaration(typeSpace, finder, typeDecl);
            }
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
        List<DFClass> classes = new ArrayList<DFClass>();
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
                System.err.println("Pass2: Error at "+path+" ("+e.name+")");
		throw e;
	    }
        }
        // Add overrides.
        for (DFClass klass : classes) {
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
                System.err.println("Pass3: Error at "+path+" ("+e.name+")");
		throw e;
            }
        }
        //converter.rootSpace.dump();
        exporter.close();

        Utils.printXml(output, exporter.document);
        output.close();
    }
}
