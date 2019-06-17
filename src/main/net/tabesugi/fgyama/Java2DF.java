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

    private ASTNode _ast;

    public ProgNode(
        DFGraph graph, DFVarScope scope, DFType type, DFRef ref,
        ASTNode ast) {
        super(graph, scope, type, ref);
        _ast = ast;
    }

    @Override
    public Element toXML(Document document) {
        Element elem = super.toXML(document);
        if (_ast != null) {
            Element east = document.createElement("ast");
            int start = _ast.getStartPosition();
            int end = start + _ast.getLength();
            east.setAttribute("type", Integer.toString(_ast.getNodeType()));
            east.setAttribute("start", Integer.toString(start));
            east.setAttribute("end", Integer.toString(end));
            elem.appendChild(east);
        }
        return elem;
    }
}

// SingleAssignNode:
class SingleAssignNode extends ProgNode {

    public SingleAssignNode(
        DFGraph graph, DFVarScope scope, DFRef ref,
        ASTNode ast) {
        super(graph, scope, ref.getRefType(), ref, ast);
    }

    @Override
    public String getKind() {
        return "assign";
    }
}

// ArrayAssignNode:
class ArrayAssignNode extends ProgNode {

    public ArrayAssignNode(
        DFGraph graph, DFVarScope scope, DFRef ref,
        ASTNode ast, DFNode array, DFNode index) {
        super(graph, scope, ref.getRefType(), ref, ast);
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
        DFGraph graph, DFVarScope scope, DFRef ref,
        ASTNode ast, DFNode obj) {
        super(graph, scope, ref.getRefType(), ref, ast);
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
        DFGraph graph, DFVarScope scope, DFRef ref,
        ASTNode ast) {
        super(graph, scope, ref.getRefType(), ref, ast);
    }

    @Override
    public String getKind() {
        return "ref";
    }
}

// ArrayRefNode
class ArrayRefNode extends ProgNode {

    public ArrayRefNode(
        DFGraph graph, DFVarScope scope, DFRef ref,
        ASTNode ast, DFNode array, DFNode index) {
        super(graph, scope, ref.getRefType(), ref, ast);
        this.accept(array, "array");
        this.accept(index, "index");
    }

    @Override
    public String getKind() {
        return "arrayref";
    }
}

// FieldRefNode
class FieldRefNode extends ProgNode {

    public FieldRefNode(
        DFGraph graph, DFVarScope scope, DFRef ref,
        ASTNode ast, DFNode obj) {
        super(graph, scope, ref.getRefType(), ref, ast);
        if (obj != null) {
            this.accept(obj, "obj");
        }
    }

    @Override
    public String getKind() {
        return "fieldref";
    }
}

// PrefixNode
class PrefixNode extends ProgNode {

    public PrefixExpression.Operator op;

    public PrefixNode(
        DFGraph graph, DFVarScope scope, DFType type, DFRef ref,
        ASTNode ast, PrefixExpression.Operator op) {
        super(graph, scope, type, ref, ast);
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
        DFGraph graph, DFVarScope scope, DFRef ref,
        ASTNode ast, PostfixExpression.Operator op) {
        super(graph, scope, ref.getRefType(), ref, ast);
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
        assert type != null;
    }

    @Override
    public String getKind() {
        return "typecast";
    }

    @Override
    public String getData() {
        return this.getNodeType().getTypeName();
    }
}

// InstanceofNode
class InstanceofNode extends ProgNode {

    public DFType type;

    public InstanceofNode(
        DFGraph graph, DFVarScope scope,
        ASTNode ast, DFType type) {
        super(graph, scope, DFBasicType.BOOLEAN, null, ast);
        assert type != null;
        this.type = type;
    }

    @Override
    public String getKind() {
        return "instanceof";
    }

    @Override
    public String getData() {
        return this.getNodeType().getTypeName();
    }
}

// CaseNode
class CaseNode extends ProgNode {

    public List<DFNode> matches = new ArrayList<DFNode>();

    public CaseNode(
        DFGraph graph, DFVarScope scope,
        ASTNode ast) {
        super(graph, scope, DFUnknownType.UNKNOWN, null, ast);
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
class AssignOpNode extends SingleAssignNode {

    public Assignment.Operator op;

    public AssignOpNode(
        DFGraph graph, DFVarScope scope, DFRef ref,
        ASTNode ast, Assignment.Operator op,
        DFNode lvalue, DFNode rvalue) {
        super(graph, scope, ref, ast);
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

// ValueSetNode: represents an array.
class ValueSetNode extends ProgNode {

    public List<DFNode> values = new ArrayList<DFNode>();

    public ValueSetNode(
        DFGraph graph, DFVarScope scope, DFType type,
        ASTNode ast) {
        super(graph, scope, type, null, ast);
    }

    @Override
    public String getKind() {
        return "valueset";
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
        DFGraph graph, DFVarScope scope, DFType type, DFRef ref,
        ASTNode ast, DFNode cond) {
        super(graph, scope, type, ref, ast);
        this.accept(cond, "cond");
    }

    @Override
    public String getKind() {
        return "join";
    }

    public void recv(boolean cond, DFNode node) {
        if (cond) {
            assert !this.recvTrue;
            this.recvTrue = true;
            this.accept(node, "true");
        } else {
            assert !this.recvFalse;
            this.recvFalse = true;
            this.accept(node, "false");
        }
    }

    @Override
    public void close(DFNode node) {
        if (!this.recvTrue) {
            assert this.recvFalse;
            this.recvTrue = true;
            this.accept(node, "true");
        }
        if (!this.recvFalse) {
            assert this.recvTrue;
            this.recvFalse = true;
            this.accept(node, "false");
        }
    }
}

// LoopNode
class LoopNode extends ProgNode {

    public String loopId;

    public LoopNode(
        DFGraph graph, DFVarScope scope, DFRef ref,
        ASTNode ast, String loopId) {
        super(graph, scope, ref.getRefType(), ref, ast);
        this.loopId = loopId;
    }

    @Override
    public String getData() {
        return this.loopId;
    }
}

// LoopBeginNode
class LoopBeginNode extends LoopNode {

    public LoopBeginNode(
        DFGraph graph, DFVarScope scope, DFRef ref,
        ASTNode ast, String loopId, DFNode enter) {
        super(graph, scope, ref, ast, loopId);
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
class LoopEndNode extends LoopNode {

    public LoopEndNode(
        DFGraph graph, DFVarScope scope, DFRef ref,
        ASTNode ast, String loopId, DFNode cond) {
        super(graph, scope, ref, ast, loopId);
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
class LoopRepeatNode extends LoopNode {

    public LoopRepeatNode(
        DFGraph graph, DFVarScope scope, DFRef ref,
        ASTNode ast, String loopId) {
        super(graph, scope, ref, ast, loopId);
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
        DFGraph graph, DFVarScope scope, DFRef ref,
        ASTNode ast) {
        super(graph, scope, ref.getRefType(), ref, ast);
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
        DFGraph graph, DFVarScope scope, DFType type, DFRef ref,
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
            String label = "#arg"+i;
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
            this.accept(obj, "#this");
        }
        this.methods = methods;
    }

    @Override
    public String getData() {
        StringBuilder b = new StringBuilder();
        for (DFMethod method : this.methods) {
            if (0 < b.length()) {
                b.append(" ");
            }
            b.append(method.getSignature());
        }
        return b.toString();
    }
}

// ReceiveNode:
class ReceiveNode extends ProgNode {

    public ReceiveNode(
        DFGraph graph, DFVarScope scope, DFRef ref,
        ASTNode ast, CallNode call, String label) {
        super(graph, scope, (ref != null)? ref.getRefType() : call.getNodeType(),
              ref, ast);
        this.accept(call, label);
    }

    @Override
    public String getKind() {
        return "receive";
    }
}

// CreateObjectNode
class CreateObjectNode extends CallNode {

    public DFMethod constructor;

    public CreateObjectNode(
        DFGraph graph, DFVarScope scope, DFType type, DFMethod constructor,
        ASTNode ast, DFNode obj) {
        super(graph, scope, type, null, ast);
        if (obj != null) {
            this.accept(obj, "#this");
        }
        this.constructor = constructor;
    }

    @Override
    public String getKind() {
        return "new";
    }

    @Override
    public String getData() {
        return this.constructor.getSignature();
    }
}

// InputNode: represnets a function argument.
class InputNode extends SingleAssignNode {

    public InputNode(
        DFGraph graph, DFVarScope scope, DFRef ref,
        ASTNode ast) {
        super(graph, scope, ref, ast);
    }

    @Override
    public String getKind() {
        return "input";
    }
}

// OutputNode: represents a return value.
class OutputNode extends SingleAssignNode {

    public OutputNode(
        DFGraph graph, DFVarScope scope, DFRef ref,
        ASTNode ast) {
        super(graph, scope, ref, ast);
    }

    @Override
    public String getKind() {
        return "output";
    }
}

// ThrowNode
class ThrowNode extends ProgNode {

    public ThrowNode(
        DFGraph graph, DFVarScope scope,
        ASTNode ast, DFNode value) throws VariableNotFound {
        super(graph, scope, DFBuiltinTypes.getExceptionKlass(),
              scope.lookupException(), ast);
        this.accept(value);
    }

    @Override
    public String getKind() {
        return "throw";
    }
}

// CatchNode
class CatchNode extends SingleAssignNode {

    public CatchNode(
        DFGraph graph, DFVarScope scope, DFRef ref,
        ASTNode ast, DFNode value) {
        super(graph, scope, ref, ast);
        this.accept(value);
    }

    @Override
    public String getKind() {
        return "catch";
    }
}

// CatchJoin
class CatchJoin extends SingleAssignNode {

    public CatchJoin(
        DFGraph graph, DFVarScope scope, DFRef ref,
        ASTNode ast) {
        super(graph, scope, ref, ast);
    }

    @Override
    public String getKind() {
        return "join";
    }

    public void recv(DFType type, DFNode node) {
        if (type != null) {
            this.accept(node, type.getTypeName());
        } else {
            this.accept(node);
        }
    }

}

//  DFFileScope
//  File-wide scope for methods and variables.
class DFFileScope extends DFVarScope {

    private Map<String, DFRef> _refs =
	new HashMap<String, DFRef>();
    private List<DFMethod> _methods =
	new ArrayList<DFMethod>();

    public DFFileScope(DFVarScope outer, String path) {
	super(outer, "["+path+"]");
    }

    @Override
    protected DFRef lookupVar1(String id)
	throws VariableNotFound {
	DFRef ref = _refs.get("."+id);
	if (ref != null) {
	    return ref;
	} else {
	    return super.lookupVar1(id);
	}
    }

    public DFMethod lookupStaticMethod(SimpleName name, DFType[] argTypes)
        throws MethodNotFound {
	String id = name.getIdentifier();
	int bestDist = -1;
	DFMethod bestMethod = null;
	for (DFMethod method : _methods) {
            if (!id.equals(method.getName())) continue;
	    int dist = method.canAccept(argTypes);
	    if (dist < 0) continue;
	    if (bestDist < 0 || dist < bestDist) {
		bestDist = dist;
		bestMethod = method;
	    }
	}
        if (bestMethod == null) throw new MethodNotFound(id, argTypes);
	return bestMethod;
    }

    public void importStatic(DFKlass klass) {
	Logger.debug("ImportStatic:", klass+".*");
	for (DFRef ref : klass.getFields()) {
	    _refs.put(ref.getName(), ref);
	}
	for (DFMethod method : klass.getMethods()) {
	    _methods.add(method);
	}
    }

    public void importStatic(DFKlass klass, SimpleName name) {
	Logger.debug("ImportStatic:", klass+"."+name);
	String id = name.getIdentifier();
	try {
	    DFRef ref = klass.lookupField(name);
	    _refs.put(ref.getName(), ref);
	} catch (VariableNotFound e) {
            try {
                DFMethod method = klass.lookupMethod(
                    DFCallStyle.StaticMethod, name, null);
                _methods.add(method);
            } catch (MethodNotFound ee) {
            }
	}
    }
}


//  Java2DF
//
public class Java2DF {

    public static DFType inferPrefixType(
        DFType type, PrefixExpression.Operator op) {
        if (op == PrefixExpression.Operator.NOT) {
            return DFBasicType.BOOLEAN;
        } else {
            return type;
        }
    }

    public static DFType inferInfixType(
        DFType left, InfixExpression.Operator op, DFType right) {
        if (op == InfixExpression.Operator.EQUALS ||
            op == InfixExpression.Operator.NOT_EQUALS ||
            op == InfixExpression.Operator.LESS ||
            op == InfixExpression.Operator.GREATER ||
            op == InfixExpression.Operator.LESS_EQUALS ||
            op == InfixExpression.Operator.GREATER_EQUALS ||
            op == InfixExpression.Operator.CONDITIONAL_AND ||
            op == InfixExpression.Operator.CONDITIONAL_OR) {
            return DFBasicType.BOOLEAN;
        } else if (op == InfixExpression.Operator.PLUS &&
                   (left == DFBuiltinTypes.getStringKlass() ||
                    right == DFBuiltinTypes.getStringKlass())) {
            return DFBuiltinTypes.getStringKlass();
        } else if (left instanceof DFUnknownType ||
                   right instanceof DFUnknownType) {
            return (left instanceof DFUnknownType)? right : left;
        } else if (0 <= left.canConvertFrom(right, null)) {
            return left;
        } else {
            return right;
        }
    }

    /// General graph operations.

    /**
     * Creates a value node.
     */
    @SuppressWarnings("unchecked")
    private void processExpression(
        DFContext ctx, DFTypeSpace typeSpace, DFGraph graph,
        DFTypeFinder finder, DFVarScope scope, DFFrame frame, Expression expr)
        throws UnsupportedSyntax, EntityNotFound {
        assert expr != null;

        try {
            if (expr instanceof Annotation) {
		// "@Annotation"

            } else if (expr instanceof Name) {
		// "a.b"
                Name name = (Name)expr;
                if (name.isSimpleName()) {
                    DFRef ref = scope.lookupVar((SimpleName)name);
                    DFNode node = new VarRefNode(graph, scope, ref, expr);
                    node.accept(ctx.get(ref));
                    ctx.setRValue(node);
                } else {
                    QualifiedName qname = (QualifiedName)name;
                    DFNode obj = null;
                    DFKlass klass;
                    try {
                        // Try assuming it's a variable access.
                        processExpression(
			    ctx, typeSpace, graph, finder, scope, frame,
			    qname.getQualifier());
                        obj = ctx.getRValue();
                        klass = obj.getNodeType().toKlass();
                    } catch (EntityNotFound e) {
                        // Turned out it's a class variable.
                        klass = finder.lookupType(qname.getQualifier()).toKlass();
                    }
                    SimpleName fieldName = qname.getName();
                    DFRef ref = klass.lookupField(fieldName);
                    DFNode node = new FieldRefNode(graph, scope, ref, qname, obj);
                    node.accept(ctx.get(ref));
                    ctx.setRValue(node);
                }

            } else if (expr instanceof ThisExpression) {
		// "this"
                ThisExpression thisExpr = (ThisExpression)expr;
                Name name = thisExpr.getQualifier();
                DFRef ref;
                if (name != null) {
                    DFKlass klass = finder.lookupType(name).toKlass();
                    ref = klass.getKlassScope().lookupThis();
                } else {
                    ref = scope.lookupThis();
                }
                DFNode node = new VarRefNode(graph, scope, ref, expr);
                node.accept(ctx.get(ref));
                ctx.setRValue(node);

            } else if (expr instanceof BooleanLiteral) {
		// "true", "false"
                boolean value = ((BooleanLiteral)expr).booleanValue();
                ctx.setRValue(new ConstNode(
                                  graph, scope, DFBasicType.BOOLEAN,
                                  expr, Boolean.toString(value)));

            } else if (expr instanceof CharacterLiteral) {
		// "'c'"
                char value = ((CharacterLiteral)expr).charValue();
                ctx.setRValue(new ConstNode(
                                  graph, scope, DFBasicType.CHAR,
                                  expr, Utils.quote(value)));

            } else if (expr instanceof NullLiteral) {
		// "null"
                ctx.setRValue(new ConstNode(
                                  graph, scope, DFNullType.NULL,
                                  expr, "null"));

            } else if (expr instanceof NumberLiteral) {
		// "42"
                String value = ((NumberLiteral)expr).getToken();
                ctx.setRValue(new ConstNode(
                                  graph, scope, DFBasicType.INT,
                                  expr, value));

            } else if (expr instanceof StringLiteral) {
		// ""abc""
                String value = ((StringLiteral)expr).getLiteralValue();
                ctx.setRValue(new ConstNode(
                                  graph, scope,
                                  DFBuiltinTypes.getStringKlass(),
                                  expr, Utils.quote(value)));

            } else if (expr instanceof TypeLiteral) {
		// "A.class"
                Type value = ((TypeLiteral)expr).getType();
                ctx.setRValue(new ConstNode(
                                  graph, scope,
                                  DFBuiltinTypes.getClassKlass(),
                                  expr, Utils.getTypeName(value)));

            } else if (expr instanceof PrefixExpression) {
                PrefixExpression prefix = (PrefixExpression)expr;
                PrefixExpression.Operator op = prefix.getOperator();
                Expression operand = prefix.getOperand();
                processExpression(
                    ctx, typeSpace, graph, finder, scope, frame, operand);
                DFNode value = ctx.getRValue();
                if (op == PrefixExpression.Operator.INCREMENT ||
                    op == PrefixExpression.Operator.DECREMENT) {
		    // "++x"
                    processAssignment(
                        ctx, typeSpace, graph, finder, scope, frame, operand);
                    DFNode assign = ctx.getLValue();
                    DFRef ref = assign.getRef();
                    DFNode node = new PrefixNode(
                        graph, scope, ref.getRefType(), ref, expr, op);
                    node.accept(ctx.getRValue());
                    assign.accept(node);
                    ctx.set(assign);
                    ctx.setRValue(node);
                } else {
		    // "!a", "+a", "-a", "~a"
                    DFType type = inferPrefixType(
                        value.getNodeType(), op);
                    DFNode node = new PrefixNode(
                        graph, scope, type, null, expr, op);
                    node.accept(value);
                    ctx.setRValue(node);
                }

            } else if (expr instanceof PostfixExpression) {
		// "y--"
                PostfixExpression postfix = (PostfixExpression)expr;
                PostfixExpression.Operator op = postfix.getOperator();
                Expression operand = postfix.getOperand();
                processAssignment(
                    ctx, typeSpace, graph, finder, scope, frame, operand);
                if (op == PostfixExpression.Operator.INCREMENT ||
                    op == PostfixExpression.Operator.DECREMENT) {
                    DFNode assign = ctx.getLValue();
                    processExpression(
                        ctx, typeSpace, graph, finder, scope, frame, operand);
                    DFNode node = new PostfixNode(
                        graph, scope, assign.getRef(), expr, op);
                    node.accept(ctx.getRValue());
                    assign.accept(node);
                    ctx.set(assign);
                }

            } else if (expr instanceof InfixExpression) {
		// "a+b"
                InfixExpression infix = (InfixExpression)expr;
                InfixExpression.Operator op = infix.getOperator();
                processExpression(
                    ctx, typeSpace, graph, finder, scope, frame,
                    infix.getLeftOperand());
                DFNode lvalue = ctx.getRValue();
                processExpression(
                    ctx, typeSpace, graph, finder, scope, frame,
                    infix.getRightOperand());
                DFNode rvalue = ctx.getRValue();
                DFType type = inferInfixType(
                    lvalue.getNodeType(), op, rvalue.getNodeType());
                ctx.setRValue(new InfixNode(
                                  graph, scope, type, expr, op, lvalue, rvalue));

            } else if (expr instanceof ParenthesizedExpression) {
		// "(expr)"
                ParenthesizedExpression paren = (ParenthesizedExpression)expr;
                processExpression(
                    ctx, typeSpace, graph, finder, scope, frame,
                    paren.getExpression());

            } else if (expr instanceof Assignment) {
		// "p = q"
                Assignment assn = (Assignment)expr;
                Assignment.Operator op = assn.getOperator();
                processAssignment(
                    ctx, typeSpace, graph, finder, scope, frame,
                    assn.getLeftHandSide());
                DFNode assign = ctx.getLValue();
                processExpression(
                    ctx, typeSpace, graph, finder, scope, frame,
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
		// "int a=2"
                VariableDeclarationExpression decl = (VariableDeclarationExpression)expr;
                processVariableDeclaration(
                    ctx, typeSpace, graph, finder, scope, frame,
                    decl.fragments());

            } else if (expr instanceof MethodInvocation) {
                MethodInvocation invoke = (MethodInvocation)expr;
                Expression expr1 = invoke.getExpression();
                DFCallStyle callStyle;
                DFNode obj = null;
                DFType type = null;
                if (expr1 == null) {
                    // "method()"
                    obj = ctx.get(scope.lookupThis());
                    type = obj.getNodeType();
                    callStyle = DFCallStyle.InstanceOrStatic;
                } else {
                    callStyle = DFCallStyle.InstanceMethod;
                    if (expr1 instanceof Name) {
                        // "ClassName.method()"
                        try {
                            type = finder.lookupType((Name)expr1);
                            callStyle = DFCallStyle.StaticMethod;
                        } catch (TypeNotFound e) {
                        }
                    }
                    if (type == null) {
                        // "expr.method()"
                        processExpression(
                            ctx, typeSpace, graph, finder, scope, frame, expr1);
                        obj = ctx.getRValue();
                        type = obj.getNodeType();
                    }
                }
                DFKlass klass = type.toKlass();
                List<DFNode> argList = new ArrayList<DFNode>();
                List<DFType> typeList = new ArrayList<DFType>();
                for (Expression arg : (List<Expression>) invoke.arguments()) {
                    processExpression(
                        ctx, typeSpace, graph, finder, scope, frame, arg);
                    DFNode node = ctx.getRValue();
                    argList.add(node);
                    typeList.add(node.getNodeType());
                }
                DFNode[] args = new DFNode[argList.size()];
                argList.toArray(args);
                DFType[] argTypes = new DFType[typeList.size()];
                typeList.toArray(argTypes);
                DFMethod method;
                try {
                    method = klass.lookupMethod(
                        callStyle, invoke.getName(), argTypes);
                } catch (MethodNotFound e) {
		    // try static imports.
                    try {
                        method = scope.lookupStaticMethod(invoke.getName(), argTypes);
                    } catch (MethodNotFound ee) {
                        if (0 < _strict) {
                            e.setAst(expr);
                            throw ee;
                        }
                        // fallback method.
                        String id = invoke.getName().getIdentifier();
                        DFMethod fallback = new DFMethod(
                            klass, id, DFCallStyle.InstanceMethod, id, null);
                        fallback.setMethodType(
                            new DFMethodType(argTypes, DFUnknownType.UNKNOWN));
                        Logger.error("Fallback method:", klass, ":", fallback);
                        method = fallback;
                    }
                }
                DFMethod methods[] = method.getOverrides();
                MethodCallNode call = new MethodCallNode(
                    graph, scope, methods, invoke, obj);
                call.setArgs(args);
                {
                    SortedSet<DFRef> refs = new TreeSet<DFRef>();
                    for (DFMethod method1 : methods) {
                        DFFrame frame1 = method1.getFrame();
                        if (frame1 == null) continue;
                        refs.addAll(frame1.getInputRefs());
                    }
                    for (DFRef ref : refs) {
                        if (ref.isLocal() || ref.isInternal()) continue;
                        call.accept(ctx.get(ref), ref.getFullName());
                    }
                }
                ctx.setRValue(new ReceiveNode(
                                  graph, scope, null, invoke, call, "#return"));
                {
                    SortedSet<DFRef> refs = new TreeSet<DFRef>();
                    for (DFMethod method1 : methods) {
                        DFFrame frame1 = method1.getFrame();
                        if (frame1 == null) continue;
                        refs.addAll(frame1.getOutputRefs());
                    }
                    for (DFRef ref : refs) {
                        if (ref.isLocal() || ref.isInternal()) continue;
                        ctx.set(new ReceiveNode(
                                    graph, scope, ref, invoke, call, ref.getFullName()));
                    }
                }
                if (call.exception != null) {
                    DFFrame dstFrame = frame.find(DFFrame.CATCHABLE);
                    frame.addExit(new DFExit(dstFrame, call.exception));
                }

            } else if (expr instanceof SuperMethodInvocation) {
		// "super.method()"
                SuperMethodInvocation sinvoke = (SuperMethodInvocation)expr;
                DFNode obj = ctx.get(scope.lookupThis());
                List<DFNode> argList = new ArrayList<DFNode>();
                List<DFType> typeList = new ArrayList<DFType>();
                for (Expression arg : (List<Expression>) sinvoke.arguments()) {
                    processExpression(
                        ctx, typeSpace, graph, finder, scope, frame, arg);
                    DFNode node = ctx.getRValue();
                    argList.add(node);
                    typeList.add(node.getNodeType());
                }
                DFNode[] args = new DFNode[argList.size()];
                argList.toArray(args);
                DFType[] argTypes = new DFType[typeList.size()];
                typeList.toArray(argTypes);
                DFKlass klass = obj.getNodeType().toKlass();
                DFKlass baseKlass = klass.getBaseKlass();
                assert baseKlass != null;
                DFMethod method;
                try {
                    method = baseKlass.lookupMethod(
                        DFCallStyle.InstanceMethod, sinvoke.getName(), argTypes);
                } catch (MethodNotFound e) {
                    if (0 < _strict) {
                        e.setAst(expr);
                        throw e;
                    }
                    // fallback method.
                    String id = sinvoke.getName().getIdentifier();
                    DFMethod fallback = new DFMethod(
                        baseKlass, id, DFCallStyle.InstanceMethod, id, null);
                    fallback.setMethodType(
                        new DFMethodType(argTypes, DFUnknownType.UNKNOWN));
                    Logger.error("Fallback method:", baseKlass, ":", fallback);
                    method = fallback;
                }
                DFMethod methods[] = new DFMethod[] { method };
                MethodCallNode call = new MethodCallNode(
                    graph, scope, methods, sinvoke, obj);
                call.setArgs(args);
                DFFrame frame1 = method.getFrame();
                if (frame1 != null) {
                    for (DFRef ref : frame1.getInputRefs()) {
                        if (ref.isLocal() || ref.isInternal()) continue;
                        call.accept(ctx.get(ref), ref.getFullName());
                    }
                }
                ctx.setRValue(new ReceiveNode(
                                  graph, scope, null, sinvoke, call, "#return"));
                if (frame1 != null) {
                    for (DFRef ref : frame1.getOutputRefs()) {
                        if (ref.isLocal() || ref.isInternal()) continue;
                        ctx.set(new ReceiveNode(
                                    graph, scope, ref, sinvoke, call, ref.getFullName()));
                    }
                }
                if (call.exception != null) {
                    DFFrame dstFrame = frame.find(DFFrame.CATCHABLE);
                    frame.addExit(new DFExit(dstFrame, call.exception));
                }

            } else if (expr instanceof ArrayCreation) {
		// "new int[10]"
                ArrayCreation ac = (ArrayCreation)expr;
                DFType arrayType = finder.resolve(ac.getType());
                for (Expression dim : (List<Expression>) ac.dimensions()) {
                    // XXX ctx.getRValue() is not used (for now).
                    processExpression(
                        ctx, typeSpace, graph, finder, scope, frame, dim);
                }
                ArrayInitializer init = ac.getInitializer();
                if (init != null) {
                    processExpression(
                        ctx, typeSpace, graph, finder, scope, frame, init);
                }
                if (init == null || ctx.getRValue() == null) {
                    ctx.setRValue(new ValueSetNode(graph, scope, arrayType, ac));
                }

            } else if (expr instanceof ArrayInitializer) {
		// "{ 5,9,4,0 }"
                ArrayInitializer init = (ArrayInitializer)expr;
                ValueSetNode arr = null;
                for (Expression expr1 : (List<Expression>) init.expressions()) {
                    processExpression(
                        ctx, typeSpace, graph, finder, scope, frame, expr1);
                    DFNode value = ctx.getRValue();
                    if (arr == null) {
                        arr = new ValueSetNode(
                            graph, scope, value.getNodeType(), init);
                    }
                    arr.addValue(value);
                }
                if (arr != null) {
                    ctx.setRValue(arr);
                }
                // XXX array ref is not used.

            } else if (expr instanceof ArrayAccess) {
		// "a[0]"
                ArrayAccess aa = (ArrayAccess)expr;
                processExpression(
                    ctx, typeSpace, graph, finder, scope, frame,
		    aa.getArray());
                DFNode array = ctx.getRValue();
                processExpression(
                    ctx, typeSpace, graph, finder, scope, frame,
		    aa.getIndex());
                DFRef ref = scope.lookupArray(array.getNodeType());
                DFNode index = ctx.getRValue();
                DFNode node = new ArrayRefNode(
                    graph, scope, ref, aa, array, index);
                ctx.setRValue(node);

            } else if (expr instanceof FieldAccess) {
		// "(expr).foo"
                FieldAccess fa = (FieldAccess)expr;
                Expression expr1 = fa.getExpression();
                DFNode obj = null;
                DFType type = null;
                if (expr1 instanceof Name) {
                    try {
                        type = finder.lookupType((Name)expr1);
                    } catch (TypeNotFound e) {
                    }
                }
                if (type == null) {
                    processExpression(
                        ctx, typeSpace, graph, finder, scope, frame, expr1);
                    obj = ctx.getRValue();
                    type = obj.getNodeType();
                }
                DFKlass klass = type.toKlass();
                SimpleName fieldName = fa.getName();
                DFRef ref = klass.lookupField(fieldName);
                DFNode node = new FieldRefNode(graph, scope, ref, fa, obj);
                node.accept(ctx.get(ref));
                ctx.setRValue(node);

            } else if (expr instanceof SuperFieldAccess) {
		// "super.baa"
                SuperFieldAccess sfa = (SuperFieldAccess)expr;
                SimpleName fieldName = sfa.getName();
                DFNode obj = ctx.get(scope.lookupThis());
                DFKlass klass = obj.getNodeType().toKlass().getBaseKlass();
                DFRef ref = klass.lookupField(fieldName);
                DFNode node = new FieldRefNode(graph, scope, ref, sfa, obj);
                node.accept(ctx.get(ref));
                ctx.setRValue(node);

            } else if (expr instanceof CastExpression) {
		// "(String)"
                CastExpression cast = (CastExpression)expr;
                DFType type = finder.resolve(cast.getType());
                processExpression(
                    ctx, typeSpace, graph, finder, scope, frame,
		    cast.getExpression());
                DFNode node = new TypeCastNode(graph, scope, type, cast);
                node.accept(ctx.getRValue());
                ctx.setRValue(node);

            } else if (expr instanceof ClassInstanceCreation) {
		// "new T()"
                ClassInstanceCreation cstr = (ClassInstanceCreation)expr;
                AnonymousClassDeclaration anonDecl =
                    cstr.getAnonymousClassDeclaration();
                DFKlass instKlass;
                if (anonDecl != null) {
                    // Anonymous classes are processed separately.
                    String id = Utils.encodeASTNode(anonDecl);
		    DFKlass anonKlass = typeSpace.getType(id).toKlass();
		    instKlass = anonKlass;
                } else {
                    instKlass = finder.resolve(cstr.getType()).toKlass();
                }
                Expression expr1 = cstr.getExpression();
                DFNode obj = null;
                if (expr1 != null) {
                    processExpression(
                        ctx, typeSpace, graph, finder, scope, frame, expr1);
                    obj = ctx.getRValue();
                }
                List<DFNode> argList = new ArrayList<DFNode>();
                List<DFType> typeList = new ArrayList<DFType>();
                for (Expression arg : (List<Expression>) cstr.arguments()) {
                    processExpression(
                        ctx, typeSpace, graph, finder, scope, frame, arg);
                    DFNode node = ctx.getRValue();
                    argList.add(node);
                    typeList.add(node.getNodeType());
                }
                DFNode[] args = new DFNode[argList.size()];
                argList.toArray(args);
                DFType[] argTypes = new DFType[typeList.size()];
                typeList.toArray(argTypes);
                DFMethod constructor = instKlass.lookupMethod(
                    DFCallStyle.Constructor, null, argTypes);
                CreateObjectNode call = new CreateObjectNode(
                    graph, scope, instKlass, constructor, cstr, obj);
                call.setArgs(args);
                DFFrame frame1 = constructor.getFrame();
                if (frame1 != null) {
                    for (DFRef ref : frame1.getInputRefs()) {
                        if (ref.isLocal() || ref.isInternal()) continue;
                        call.accept(ctx.get(ref), ref.getFullName());
                    }
                }
                ctx.setRValue(new ReceiveNode(
                                  graph, scope, null, cstr, call, "#return"));
                if (frame1 != null) {
                    for (DFRef ref : frame1.getOutputRefs()) {
                        if (ref.isLocal() || ref.isInternal()) continue;
                        ctx.set(new ReceiveNode(
                                    graph, scope, ref, cstr, call, ref.getFullName()));
                    }
                }
                if (call.exception != null) {
                    DFFrame dstFrame = frame.find(DFFrame.CATCHABLE);
                    frame.addExit(new DFExit(dstFrame, call.exception));
                }

            } else if (expr instanceof ConditionalExpression) {
		// "c? a : b"
                ConditionalExpression cond = (ConditionalExpression)expr;
                processExpression(
                    ctx, typeSpace, graph, finder, scope, frame,
		    cond.getExpression());
                DFNode condValue = ctx.getRValue();
                processExpression(
                    ctx, typeSpace, graph, finder, scope, frame,
		    cond.getThenExpression());
                DFNode trueValue = ctx.getRValue();
                processExpression(
                    ctx, typeSpace, graph, finder, scope, frame,
		    cond.getElseExpression());
                DFNode falseValue = ctx.getRValue();
                JoinNode join = new JoinNode(
                    graph, scope, trueValue.getNodeType(), null, expr, condValue);
                join.recv(true, trueValue);
                join.recv(false, falseValue);
                ctx.setRValue(join);

            } else if (expr instanceof InstanceofExpression) {
		// "a instanceof A"
                InstanceofExpression instof = (InstanceofExpression)expr;
                DFType type = finder.resolve(instof.getRightOperand());
                processExpression(
                    ctx, typeSpace, graph, finder, scope, frame,
                    instof.getLeftOperand());
                DFNode node = new InstanceofNode(graph, scope, instof, type);
                node.accept(ctx.getRValue());
                ctx.setRValue(node);

            } else if (expr instanceof LambdaExpression) {
		// "x -> { ... }"
                LambdaExpression lambda = (LambdaExpression)expr;
                ASTNode body = lambda.getBody();
                if (body instanceof Statement) {
                    // XXX TODO Statement lambda
                } else if (body instanceof Expression) {
                    // XXX TODO Expresssion lambda
                } else {
                    throw new UnsupportedSyntax(body);
                }
		// XXX TODO
                ctx.setRValue(new DFNode(graph, scope,  DFUnknownType.UNKNOWN, null));

            } else if (expr instanceof MethodReference) {
                // MethodReference
                //  CreationReference
                //  ExpressionMethodReference
                //  SuperMethodReference
                //  TypeMethodReference
                MethodReference mref = (MethodReference)expr;
                // XXX TODO method ref
                DFKlass klass = scope.lookupThis().getRefType().toKlass();
                DFTypeSpace anonSpace = new DFTypeSpace("MethodRef");
                DFKlass anonKlass = new DFKlass(
                    "methodref", anonSpace, klass, scope);
                DFMethod constructor = anonKlass.lookupMethod(
                    DFCallStyle.Constructor, null, null);
                CreateObjectNode call = new CreateObjectNode(
                    graph, scope, anonKlass, constructor, mref, null);
                ctx.setRValue(call);

            } else {
                // ???
                throw new UnsupportedSyntax(expr);
            }
        } catch (EntityNotFound e) {
            e.setAst(expr);
            throw e;
        }
    }

    /**
     * Creates an assignment node.
     */
    @SuppressWarnings("unchecked")
    private void processAssignment(
        DFContext ctx, DFTypeSpace typeSpace, DFGraph graph,
        DFTypeFinder finder, DFVarScope scope, DFFrame frame,
        Expression expr)
        throws UnsupportedSyntax, EntityNotFound {
        assert expr != null;

        if (expr instanceof Name) {
	    // "a.b"
            Name name = (Name)expr;
            if (name.isSimpleName()) {
                DFRef ref = scope.lookupVar((SimpleName)name);
                ctx.setLValue(new SingleAssignNode(graph, scope, ref, expr));
            } else {
                QualifiedName qname = (QualifiedName)name;
                DFNode obj = null;
                DFType type = null;
                try {
                    // Try assuming it's a variable access.
                    processExpression(
                        ctx, typeSpace, graph, finder, scope, frame,
			qname.getQualifier());
                    obj = ctx.getRValue();
                    type = obj.getNodeType();
                } catch (EntityNotFound e) {
                    // Turned out it's a class variable.
                    type = finder.lookupType(qname.getQualifier());
                }
                DFKlass klass = type.toKlass();
                SimpleName fieldName = qname.getName();
                DFRef ref = klass.lookupField(fieldName);
                ctx.setLValue(new FieldAssignNode(graph, scope, ref, expr, obj));
            }

        } else if (expr instanceof ArrayAccess) {
	    // "a[0]"
            ArrayAccess aa = (ArrayAccess)expr;
            processExpression(
                ctx, typeSpace, graph, finder, scope, frame,
		aa.getArray());
            DFNode array = ctx.getRValue();
            processExpression(
                ctx, typeSpace, graph, finder, scope, frame,
		aa.getIndex());
            DFRef ref = scope.lookupArray(array.getNodeType());
            DFNode index = ctx.getRValue();
            DFNode node = new ArrayAssignNode(
                graph, scope, ref, expr, array, index);
            ctx.setLValue(node);

        } else if (expr instanceof FieldAccess) {
	    // "(expr).foo"
            FieldAccess fa = (FieldAccess)expr;
            Expression expr1 = fa.getExpression();
            processExpression(
                ctx, typeSpace, graph, finder, scope, frame, expr1);
            DFNode obj = ctx.getRValue();
            DFKlass klass = obj.getNodeType().toKlass();
            SimpleName fieldName = fa.getName();
            DFRef ref = klass.lookupField(fieldName);
            ctx.setLValue(new FieldAssignNode(graph, scope, ref, expr, obj));

        } else if (expr instanceof SuperFieldAccess) {
	    // "super.baa"
            SuperFieldAccess sfa = (SuperFieldAccess)expr;
            SimpleName fieldName = sfa.getName();
            DFNode obj = ctx.get(scope.lookupThis());
            DFKlass klass = obj.getNodeType().toKlass().getBaseKlass();
            DFRef ref = klass.lookupField(fieldName);
            ctx.setLValue(new FieldAssignNode(graph, scope, ref, expr, obj));

        } else {
            throw new UnsupportedSyntax(expr);
        }
    }

    /**
     * Creates a new variable node.
     */
    private void processVariableDeclaration(
        DFContext ctx, DFTypeSpace typeSpace, DFGraph graph,
        DFTypeFinder finder, DFVarScope scope, DFFrame frame,
	List<VariableDeclarationFragment> frags)
        throws UnsupportedSyntax, EntityNotFound {

        for (VariableDeclarationFragment frag : frags) {
            DFRef ref = scope.lookupVar(frag.getName());
            Expression init = frag.getInitializer();
            if (init != null) {
                processExpression(
                    ctx, typeSpace, graph, finder, scope, frame, init);
                DFNode value = ctx.getRValue();
                if (value != null) {
                    DFNode assign = new SingleAssignNode(graph, scope, ref, frag);
                    assign.accept(value);
                    ctx.set(assign);
                }
            }
        }
    }

    /**
     * Expands the graph for the loop variables.
     */
    private void processLoop(
        DFContext ctx, DFGraph graph, DFVarScope scope,
        DFFrame frame, ASTNode ast, DFNode condValue,
        DFFrame loopFrame, DFContext loopCtx, boolean preTest)
        throws UnsupportedSyntax {

        String loopId = Utils.encodeASTNode(ast);
        // Add four nodes for each loop variable.
        Map<DFRef, LoopBeginNode> begins =
            new HashMap<DFRef, LoopBeginNode>();
        Map<DFRef, LoopRepeatNode> repeats =
            new HashMap<DFRef, LoopRepeatNode>();
        Map<DFRef, DFNode> ends =
            new HashMap<DFRef, DFNode>();
        DFRef[] loopRefs = loopCtx.getChanged();
        for (DFRef ref : loopRefs) {
            DFNode src = ctx.get(ref);
            LoopBeginNode begin = new LoopBeginNode(
                graph, scope, ref, ast, loopId, src);
            LoopRepeatNode repeat = new LoopRepeatNode(
                graph, scope, ref, ast, loopId);
            LoopEndNode end = new LoopEndNode(
                graph, scope, ref, ast, loopId, condValue);
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
                DFRef ref = input.getRef();
                DFNode src = repeats.get(ref);
                if (src == null) {
                    src = ctx.get(ref);
                }
                input.accept(src);
            }
            // Connect the loop outputs to the begins.
            for (DFRef ref : loopRefs) {
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
            for (DFRef ref : loopRefs) {
                LoopBeginNode begin = begins.get(ref);
                DFNode end = ends.get(ref);
                end.accept(begin);
            }

        } else {  // Begin -> [S] -> End -> Repeat
            // Connect the begins to the loop inputs.
            for (DFNode input : loopCtx.getFirsts()) {
                if (input.hasInput()) continue;
                DFRef ref = input.getRef();
                DFNode src = begins.get(ref);
                if (src == null) {
                    src = ctx.get(ref);
                }
                input.accept(src);
            }
            // Connect the loop outputs to the ends.
            for (DFRef ref : loopRefs) {
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
            for (DFRef ref : loopRefs) {
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
        for (DFRef ref : loopRefs) {
            DFNode end = ends.get(ref);
            LoopRepeatNode repeat = repeats.get(ref);
            ctx.set(end);
            repeat.setLoop(end);
        }
    }

    /// Statement processors.
    @SuppressWarnings("unchecked")
    private void processBlock(
        DFContext ctx, DFTypeSpace typeSpace, DFGraph graph,
        DFTypeFinder finder, DFVarScope scope, DFFrame frame,
	Block block)
        throws UnsupportedSyntax, EntityNotFound {
        DFVarScope innerScope = scope.getChildByAST(block);
        for (Statement cstmt : (List<Statement>) block.statements()) {
            processStatement(
                ctx, typeSpace, graph, finder, innerScope, frame, cstmt);
        }
    }

    @SuppressWarnings("unchecked")
    private void processVariableDeclarationStatement(
        DFContext ctx, DFTypeSpace typeSpace, DFGraph graph,
        DFTypeFinder finder, DFVarScope scope, DFFrame frame,
	VariableDeclarationStatement varStmt)
        throws UnsupportedSyntax, EntityNotFound {
        processVariableDeclaration(
            ctx, typeSpace, graph, finder, scope, frame,
            varStmt.fragments());
    }

    private void processExpressionStatement(
        DFContext ctx, DFTypeSpace typeSpace, DFGraph graph,
        DFTypeFinder finder, DFVarScope scope, DFFrame frame,
	ExpressionStatement exprStmt)
        throws UnsupportedSyntax, EntityNotFound {
        processExpression(
            ctx, typeSpace, graph, finder, scope, frame,
            exprStmt.getExpression());
    }

    private void processIfStatement(
        DFContext ctx, DFTypeSpace typeSpace, DFGraph graph,
        DFTypeFinder finder, DFVarScope scope, DFFrame frame,
	IfStatement ifStmt)
        throws UnsupportedSyntax, EntityNotFound {
        processExpression(
	    ctx, typeSpace, graph, finder, scope, frame,
            ifStmt.getExpression());
        DFNode condValue = ctx.getRValue();

        Statement thenStmt = ifStmt.getThenStatement();
        DFContext thenCtx = new DFContext(graph, scope);
        DFFrame thenFrame = frame.getChildByAST(thenStmt);
        processStatement(
            thenCtx, typeSpace, graph, finder, scope, thenFrame, thenStmt);

        Statement elseStmt = ifStmt.getElseStatement();
        DFContext elseCtx = null;
        DFFrame elseFrame = null;
        if (elseStmt != null) {
            elseFrame = frame.getChildByAST(elseStmt);
            elseCtx = new DFContext(graph, scope);
            processStatement(
                elseCtx, typeSpace, graph, finder, scope, elseFrame, elseStmt);
        }

        // Combines two contexts into one.
        // A JoinNode is added to each variable.

        // outRefs: all the references from both contexts.
        SortedSet<DFRef> outRefs = new TreeSet<DFRef>();
        if (thenFrame != null && thenCtx != null) {
            for (DFNode src : thenCtx.getFirsts()) {
                if (src.hasInput()) continue;
                src.accept(ctx.get(src.getRef()));
            }
            outRefs.addAll(thenFrame.getOutputRefs());
        }
        if (elseFrame != null && elseCtx != null) {
            for (DFNode src : elseCtx.getFirsts()) {
                if (src.hasInput()) continue;
                src.accept(ctx.get(src.getRef()));
            }
            outRefs.addAll(elseFrame.getOutputRefs());
        }

        // Attach a JoinNode to each variable.
        for (DFRef ref : outRefs) {
            JoinNode join = new JoinNode(
                graph, scope, ref.getRefType(), ref, ifStmt, condValue);
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
            if (thenCtx == null || elseCtx == null) {
                join.close(ctx.get(ref));
            }
            ctx.set(join);
        }

        // Take care of exits.
        if (thenFrame != null) {
            for (DFExit exit : thenFrame.getExits()) {
                DFNode node = exit.getNode();
                DFRef ref = node.getRef();
                JoinNode join = new JoinNode(
                    graph, scope, ref.getRefType(), ref, null, condValue);
                join.recv(true, node);
                frame.addExit(exit.wrap(join));
            }
            thenFrame.close(ctx);
        }
        if (elseFrame != null) {
            for (DFExit exit : elseFrame.getExits()) {
                DFNode node = exit.getNode();
                DFRef ref = node.getRef();
                JoinNode join = new JoinNode(
                    graph, scope, ref.getRefType(), ref, null, condValue);
                join.recv(false, node);
                frame.addExit(exit.wrap(join));
            }
            elseFrame.close(ctx);
        }
    }

    private void processCaseStatement(
        DFContext ctx, DFGraph graph, DFVarScope scope,
        DFFrame frame, ASTNode apt,
        DFNode caseNode, DFContext caseCtx) {

        for (DFNode src : caseCtx.getFirsts()) {
            if (src.hasInput()) continue;
            src.accept(ctx.get(src.getRef()));
        }

        for (DFRef ref : frame.getOutputRefs()) {
            DFNode dst = caseCtx.get(ref);
            if (dst != null) {
                JoinNode join = new JoinNode(
                    graph, scope, ref.getRefType(), ref, apt, caseNode);
                join.recv(true, dst);
                join.close(ctx.get(ref));
                ctx.set(join);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void processSwitchStatement(
        DFContext ctx, DFTypeSpace typeSpace, DFGraph graph,
        DFTypeFinder finder, DFVarScope scope, DFFrame frame,
	SwitchStatement switchStmt)
        throws UnsupportedSyntax, EntityNotFound {
        processExpression(
            ctx, typeSpace, graph, finder, scope, frame,
            switchStmt.getExpression());
        DFNode switchValue = ctx.getRValue();
        DFType type = switchValue.getNodeType();
        DFKlass enumKlass = null;
        if (type instanceof DFKlass &&
            ((DFKlass)type).isEnum()) {
            enumKlass = type.toKlass();
        }
        DFVarScope switchScope = scope.getChildByAST(switchStmt);
        DFFrame switchFrame = frame.getChildByAST(switchStmt);

        SwitchCase switchCase = null;
        CaseNode caseNode = null;
        DFContext caseCtx = null;
        for (Statement stmt : (List<Statement>) switchStmt.statements()) {
            assert stmt != null;
            if (stmt instanceof SwitchCase) {
                if (caseCtx != null) {
                    // switchCase, caseNode and caseCtx must be non-null.
                    processCaseStatement(
                        ctx, graph, switchScope, switchFrame,
                        switchCase, caseNode, caseCtx);
                }
                switchCase = (SwitchCase)stmt;
                caseNode = new CaseNode(graph, switchScope, stmt);
                caseNode.accept(switchValue);
                caseCtx = new DFContext(graph, switchScope);
                Expression expr = switchCase.getExpression();
                if (expr != null) {
                    if (enumKlass != null && expr instanceof SimpleName) {
                        // special treatment for enum.
                        DFRef ref = enumKlass.lookupField((SimpleName)expr);
                        DFNode node = new FieldRefNode(graph, scope, ref, expr, null);
                        node.accept(ctx.get(ref));
                        caseNode.addMatch(node);
                    } else {
                        processExpression(
                            ctx, typeSpace, graph, finder, switchScope, switchFrame,
			    expr);
                        caseNode.addMatch(ctx.getRValue());
                    }
                } else {
                    // "default" case.
                }
            } else {
                if (caseCtx == null) {
                    // no "case" statement.
                    throw new UnsupportedSyntax(stmt);
                }
                processStatement(
                    caseCtx, typeSpace, graph, finder, switchScope,
                    switchFrame, stmt);
            }
        }
        if (caseCtx != null) {
            processCaseStatement(
                ctx, graph, switchScope, switchFrame,
                switchCase, caseNode, caseCtx);
        }
        switchFrame.close(ctx);
    }

    private void processWhileStatement(
        DFContext ctx, DFTypeSpace typeSpace, DFGraph graph,
        DFTypeFinder finder, DFVarScope scope, DFFrame frame,
	WhileStatement whileStmt)
        throws UnsupportedSyntax, EntityNotFound {
        DFVarScope loopScope = scope.getChildByAST(whileStmt);
        DFFrame loopFrame = frame.getChildByAST(whileStmt);
        DFContext loopCtx = new DFContext(graph, loopScope);
        processExpression(
            loopCtx, typeSpace, graph, finder, scope, loopFrame,
            whileStmt.getExpression());
        DFNode condValue = loopCtx.getRValue();
        processStatement(
            loopCtx, typeSpace, graph, finder, loopScope, loopFrame,
            whileStmt.getBody());
        processLoop(
            ctx, graph, loopScope, frame, whileStmt,
            condValue, loopFrame, loopCtx, true);
        loopFrame.close(ctx);
    }

    private void processDoStatement(
        DFContext ctx, DFTypeSpace typeSpace, DFGraph graph,
        DFTypeFinder finder, DFVarScope scope, DFFrame frame,
	DoStatement doStmt)
        throws UnsupportedSyntax, EntityNotFound {
        DFVarScope loopScope = scope.getChildByAST(doStmt);
        DFFrame loopFrame = frame.getChildByAST(doStmt);
        DFContext loopCtx = new DFContext(graph, loopScope);
        processStatement(
            loopCtx, typeSpace, graph, finder, loopScope, loopFrame,
            doStmt.getBody());
        processExpression(
            loopCtx, typeSpace, graph, finder, loopScope, loopFrame,
            doStmt.getExpression());
        DFNode condValue = loopCtx.getRValue();
        processLoop(
            ctx, graph, loopScope, frame, doStmt,
            condValue, loopFrame, loopCtx, false);
        loopFrame.close(ctx);
    }

    @SuppressWarnings("unchecked")
    private void processForStatement(
        DFContext ctx, DFTypeSpace typeSpace, DFGraph graph,
        DFTypeFinder finder, DFVarScope scope, DFFrame frame,
	ForStatement forStmt)
        throws UnsupportedSyntax, EntityNotFound {
        DFVarScope loopScope = scope.getChildByAST(forStmt);
        DFContext loopCtx = new DFContext(graph, loopScope);
        for (Expression init : (List<Expression>) forStmt.initializers()) {
            processExpression(
                ctx, typeSpace, graph, finder, loopScope, frame, init);
        }
        DFFrame loopFrame = frame.getChildByAST(forStmt);
        Expression expr = forStmt.getExpression();
        DFNode condValue;
        if (expr != null) {
            processExpression(
                loopCtx, typeSpace, graph, finder, loopScope, loopFrame, expr);
            condValue = loopCtx.getRValue();
        } else {
            condValue = new ConstNode(graph, loopScope, DFBasicType.BOOLEAN, null, "true");
        }
        processStatement(
            loopCtx, typeSpace, graph, finder, loopScope, loopFrame,
            forStmt.getBody());
        for (Expression update : (List<Expression>) forStmt.updaters()) {
            processExpression(
                loopCtx, typeSpace, graph, finder, loopScope, loopFrame, update);
        }
        processLoop(
            ctx, graph, loopScope, frame, forStmt,
            condValue, loopFrame, loopCtx, true);
        loopFrame.close(ctx);
    }

    @SuppressWarnings("unchecked")
    private void processEnhancedForStatement(
        DFContext ctx, DFTypeSpace typeSpace, DFGraph graph,
	DFTypeFinder finder, DFVarScope scope, DFFrame frame,
	EnhancedForStatement eForStmt)
        throws UnsupportedSyntax, EntityNotFound {
        Expression expr = eForStmt.getExpression();
        processExpression(
            ctx, typeSpace, graph, finder, scope, frame, expr);
        DFVarScope loopScope = scope.getChildByAST(eForStmt);
        DFFrame loopFrame = frame.getChildByAST(eForStmt);
        DFContext loopCtx = new DFContext(graph, loopScope);
        SingleVariableDeclaration decl = eForStmt.getParameter();
        DFRef ref = loopScope.lookupVar(decl.getName());
        DFNode iterValue = new IterNode(graph, loopScope, ref, expr);
        iterValue.accept(ctx.getRValue());
        SingleAssignNode assign = new SingleAssignNode(graph, loopScope, ref, expr);
        assign.accept(iterValue);
        loopCtx.set(assign);
        processStatement(
            loopCtx, typeSpace, graph, finder, loopScope, loopFrame,
            eForStmt.getBody());
        processLoop(
            ctx, graph, loopScope, frame, eForStmt,
            iterValue, loopFrame, loopCtx, true);
        loopFrame.close(ctx);
    }

    @SuppressWarnings("unchecked")
    private void processTryStatement(
        DFContext ctx, DFTypeSpace typeSpace, DFGraph graph,
        DFTypeFinder finder, DFVarScope scope, DFFrame frame,
	TryStatement tryStmt)
        throws UnsupportedSyntax, EntityNotFound {
        SortedSet<DFRef> outRefs = new TreeSet<DFRef>();

        DFVarScope tryScope = scope.getChildByAST(tryStmt);
        DFFrame tryFrame = frame.getChildByAST(tryStmt);
        DFContext tryCtx = new DFContext(graph, tryScope);
        processStatement(
            tryCtx, typeSpace, graph, finder, tryScope, tryFrame,
            tryStmt.getBody());
        for (DFNode src : tryCtx.getFirsts()) {
            if (src.hasInput()) continue;
            src.accept(ctx.get(src.getRef()));
        }
        DFNode exc = tryCtx.get(tryScope.lookupException());
        outRefs.addAll(tryFrame.getOutputRefs());

        List<CatchClause> catches = tryStmt.catchClauses();
        int ncats = catches.size();
        DFType[] excs = new DFType[ncats];
        DFFrame[] frames = new DFFrame[ncats];
        DFContext[] ctxs = new DFContext[ncats];
        for (int i = 0; i < ncats; i++) {
            CatchClause cc = catches.get(i);
            SingleVariableDeclaration decl = cc.getException();
            excs[i] = finder.resolve(decl.getType());
            DFVarScope catchScope = scope.getChildByAST(cc);
            DFFrame catchFrame = frame.getChildByAST(cc);
            DFContext catchCtx = new DFContext(graph, catchScope);
            DFRef ref = catchScope.lookupVar(decl.getName());
            CatchNode cat = new CatchNode(graph, catchScope, ref, decl, exc);
            catchCtx.set(cat);
            processStatement(
                catchCtx, typeSpace, graph, finder, catchScope, catchFrame,
                cc.getBody());
            for (DFNode src : catchCtx.getFirsts()) {
                if (src.hasInput()) continue;
                src.accept(ctx.get(src.getRef()));
            }
            outRefs.addAll(catchFrame.getOutputRefs());
            frames[i] = catchFrame;
            ctxs[i] = catchCtx;
        }

        // Attach a CatchNode to each variable.
        for (DFRef ref : outRefs) {
            CatchJoin join = new CatchJoin(graph, scope, ref, tryStmt);
            {
                DFNode dst = tryCtx.get(ref);
                if (dst != null) {
                    join.recv(null, dst);
                }
            }
            for (int i = 0; i < ncats; i++) {
                DFNode dst = ctxs[i].get(ref);
                if (dst != null) {
                    join.recv(excs[i], dst);
                }
            }
            ctx.set(join);
        }

        // Take care of exits.
        for (DFExit exit : tryFrame.getExits()) {
            DFNode node = exit.getNode();
            CatchJoin join = new CatchJoin(
                graph, scope, node.getRef(), tryStmt);
            join.recv(null, node);
            frame.addExit(exit.wrap(join));
        }
        tryFrame.close(ctx);
        for (int i = 0; i < ncats; i++) {
            for (DFExit exit : frames[i].getExits()) {
                DFNode node = exit.getNode();
                CatchJoin join = new CatchJoin(
                    graph, scope, node.getRef(), tryStmt);
                join.recv(excs[i], node);
                frame.addExit(exit.wrap(join));
            }
            frames[i].close(ctx);
        }

        Block finBlock = tryStmt.getFinally();
        if (finBlock != null) {
            processStatement(
                ctx, typeSpace, graph, finder, scope, frame, finBlock);
        }
    }

    @SuppressWarnings("unchecked")
    private void processStatement(
        DFContext ctx, DFTypeSpace typeSpace, DFGraph graph,
        DFTypeFinder finder, DFVarScope scope, DFFrame frame,
	Statement stmt)
        throws UnsupportedSyntax, EntityNotFound {
        assert stmt != null;

        if (stmt instanceof AssertStatement) {
	    // "assert x;"

        } else if (stmt instanceof Block) {
	    // "{ ... }"
            processBlock(
                ctx, typeSpace, graph, finder, scope, frame,
		(Block)stmt);

        } else if (stmt instanceof EmptyStatement) {

        } else if (stmt instanceof VariableDeclarationStatement) {
	    // "int a = 2;"
            processVariableDeclarationStatement(
                ctx, typeSpace, graph, finder, scope, frame,
                (VariableDeclarationStatement)stmt);

        } else if (stmt instanceof ExpressionStatement) {
	    // "foo();"
            processExpressionStatement(
                ctx, typeSpace, graph, finder, scope, frame,
                (ExpressionStatement)stmt);

        } else if (stmt instanceof IfStatement) {
	    // "if (c) { ... } else { ... }"
            processIfStatement(
                ctx, typeSpace, graph, finder, scope, frame,
                (IfStatement)stmt);

        } else if (stmt instanceof SwitchStatement) {
	    // "switch (x) { case 0: ...; }"
            processSwitchStatement(
                ctx, typeSpace, graph, finder, scope, frame,
                (SwitchStatement)stmt);

        } else if (stmt instanceof SwitchCase) {
            // Invalid "case" placement.
            throw new UnsupportedSyntax(stmt);

        } else if (stmt instanceof WhileStatement) {
	    // "while (c) { ... }"
            processWhileStatement(
                ctx, typeSpace, graph, finder, scope, frame,
                (WhileStatement)stmt);

        } else if (stmt instanceof DoStatement) {
	    // "do { ... } while (c);"
            processDoStatement(
                ctx, typeSpace, graph, finder, scope, frame,
                (DoStatement)stmt);

        } else if (stmt instanceof ForStatement) {
	    // "for (i = 0; i < 10; i++) { ... }"
            processForStatement(
                ctx, typeSpace, graph, finder, scope, frame,
                (ForStatement)stmt);

        } else if (stmt instanceof EnhancedForStatement) {
	    // "for (x : array) { ... }"
            processEnhancedForStatement(
                ctx, typeSpace, graph, finder, scope, frame,
                (EnhancedForStatement)stmt);

        } else if (stmt instanceof ReturnStatement) {
	    // "return 42;"
            ReturnStatement rtrnStmt = (ReturnStatement)stmt;
            DFFrame dstFrame = frame.find(DFFrame.RETURNABLE);
            Expression expr = rtrnStmt.getExpression();
            if (expr != null) {
                processExpression(
                    ctx, typeSpace, graph, finder, scope, frame, expr);
                DFRef ref = scope.lookupReturn();
                OutputNode output = new OutputNode(graph, scope, ref, rtrnStmt);
                output.accept(ctx.getRValue());
                ctx.set(output);
            }
            for (DFRef ref : dstFrame.getOutputRefs()) {
                frame.addExit(new DFExit(dstFrame, ctx.get(ref)));
            }

        } else if (stmt instanceof BreakStatement) {
	    // "break;"
            BreakStatement breakStmt = (BreakStatement)stmt;
            SimpleName labelName = breakStmt.getLabel();
            String dstLabel = (labelName != null)?
                labelName.getIdentifier() : DFFrame.BREAKABLE;
            DFFrame dstFrame = frame.find(dstLabel);
            for (DFRef ref : dstFrame.getOutputRefs()) {
                frame.addExit(new DFExit(dstFrame, ctx.get(ref)));
            }

        } else if (stmt instanceof ContinueStatement) {
	    // "continue;"
            ContinueStatement contStmt = (ContinueStatement)stmt;
            SimpleName labelName = contStmt.getLabel();
            String dstLabel = (labelName != null)?
                labelName.getIdentifier() : DFFrame.BREAKABLE;
            DFFrame dstFrame = frame.find(dstLabel);
            for (DFRef ref : dstFrame.getOutputRefs()) {
                frame.addExit(new DFExit(dstFrame, ctx.get(ref), true));
            }

        } else if (stmt instanceof LabeledStatement) {
	    // "here:"
            LabeledStatement labeledStmt = (LabeledStatement)stmt;
            DFFrame labeledFrame = frame.getChildByAST(labeledStmt);
            processStatement(
                ctx, typeSpace, graph, finder, scope, labeledFrame,
                labeledStmt.getBody());
            labeledFrame.close(ctx);

        } else if (stmt instanceof SynchronizedStatement) {
	    // "synchronized (this) { ... }"
            SynchronizedStatement syncStmt = (SynchronizedStatement)stmt;
            processExpression(
                ctx, typeSpace, graph, finder, scope, frame,
                syncStmt.getExpression());
            processStatement(
                ctx, typeSpace, graph, finder, scope, frame,
                syncStmt.getBody());

        } else if (stmt instanceof TryStatement) {
	    // "try { ... } catch (e) { ... }"
            processTryStatement(
                ctx, typeSpace, graph, finder, scope, frame,
                (TryStatement)stmt);

        } else if (stmt instanceof ThrowStatement) {
	    // "throw e;"
            ThrowStatement throwStmt = (ThrowStatement)stmt;
            processExpression(
                ctx, typeSpace, graph, finder, scope, frame,
                throwStmt.getExpression());
            ThrowNode exception = new ThrowNode(
                graph, scope, stmt, ctx.getRValue());
            DFFrame dstFrame = frame.find(DFFrame.CATCHABLE);
            if (dstFrame != null) {
                frame.addExit(new DFExit(dstFrame, exception));
                for (DFRef ref : dstFrame.getOutputRefs()) {
                    frame.addExit(new DFExit(dstFrame, ctx.get(ref)));
                }
            }

        } else if (stmt instanceof ConstructorInvocation) {
	    // "this(args)"
            ConstructorInvocation ci = (ConstructorInvocation)stmt;
            DFNode obj = ctx.get(scope.lookupThis());
            List<DFNode> argList = new ArrayList<DFNode>();
            List<DFType> typeList = new ArrayList<DFType>();
            for (Expression arg : (List<Expression>) ci.arguments()) {
                processExpression(
                    ctx, typeSpace, graph, finder, scope, frame, arg);
                DFNode node = ctx.getRValue();
                argList.add(node);
                typeList.add(node.getNodeType());
            }
            DFNode[] args = new DFNode[argList.size()];
            argList.toArray(args);
            DFType[] argTypes = new DFType[typeList.size()];
            typeList.toArray(argTypes);
            DFKlass klass = scope.lookupThis().getRefType().toKlass();
            DFMethod constructor = klass.lookupMethod(
                DFCallStyle.Constructor, null, argTypes);
            DFMethod methods[] = new DFMethod[] { constructor };
            MethodCallNode call = new MethodCallNode(
                graph, scope, methods, ci, obj);
            call.setArgs(args);
            DFFrame frame1 = constructor.getFrame();
            if (frame1 != null) {
                for (DFRef ref : frame1.getInputRefs()) {
                    if (ref.isLocal() || ref.isInternal()) continue;
                    call.accept(ctx.get(ref), ref.getFullName());
                }
            }
            if (frame1 != null) {
                for (DFRef ref : frame1.getOutputRefs()) {
                    if (ref.isLocal() || ref.isInternal()) continue;
                    ctx.set(new ReceiveNode(
                                graph, scope, ref, ci, call, ref.getFullName()));
                }
            }
            if (call.exception != null) {
                DFFrame dstFrame = frame.find(DFFrame.CATCHABLE);
                frame.addExit(new DFExit(dstFrame, call.exception));
            }

        } else if (stmt instanceof SuperConstructorInvocation) {
	    // "super(args)"
            SuperConstructorInvocation sci = (SuperConstructorInvocation)stmt;
            DFNode obj = ctx.get(scope.lookupThis());
            List<DFNode> argList = new ArrayList<DFNode>();
            List<DFType> typeList = new ArrayList<DFType>();
            for (Expression arg : (List<Expression>) sci.arguments()) {
                processExpression(
                    ctx, typeSpace, graph, finder, scope, frame, arg);
                DFNode node = ctx.getRValue();
                argList.add(node);
                typeList.add(node.getNodeType());
            }
            DFNode[] args = new DFNode[argList.size()];
            argList.toArray(args);
            DFType[] argTypes = new DFType[typeList.size()];
            typeList.toArray(argTypes);
            DFKlass klass = obj.getNodeType().toKlass();
            DFKlass baseKlass = klass.getBaseKlass();
            assert baseKlass != null;
            DFMethod constructor = baseKlass.lookupMethod(
                DFCallStyle.Constructor, null, argTypes);
            DFMethod methods[] = new DFMethod[] { constructor };
            MethodCallNode call = new MethodCallNode(
                graph, scope, methods, sci, obj);
            call.setArgs(args);
            DFFrame frame1 = constructor.getFrame();
            if (frame1 != null) {
                for (DFRef ref : frame1.getInputRefs()) {
                    if (ref.isLocal() || ref.isInternal()) continue;
                    call.accept(ctx.get(ref), ref.getFullName());
                }
            }
            if (frame1 != null) {
                for (DFRef ref : frame1.getOutputRefs()) {
                    if (ref.isLocal() || ref.isInternal()) continue;
                    ctx.set(new ReceiveNode(
                                graph, scope, ref, sci, call, ref.getFullName()));
                }
            }
            if (call.exception != null) {
                DFFrame dstFrame = frame.find(DFFrame.CATCHABLE);
                frame.addExit(new DFExit(dstFrame, call.exception));
            }

        } else if (stmt instanceof TypeDeclarationStatement) {
	    // "class K { ... }"
            // Inline classes are processed separately.

        } else {
            throw new UnsupportedSyntax(stmt);
        }
    }

    /**
     * Performs dataflow analysis for a given method.
     */
    @SuppressWarnings("unchecked")
    private DFGraph processMethod(
        DFMethod method, ASTNode ast,
        Statement body, List<SingleVariableDeclaration> parameters)
        throws UnsupportedSyntax, EntityNotFound {
        DFVarScope scope = method.getScope();
        assert scope != null;
        DFFrame frame = method.getFrame();
        assert frame != null;
        DFGraph graph = new DFGraph(scope, method, ast);
        DFContext ctx = new DFContext(graph, scope);
        DFTypeFinder finder = method.getFinder();
        if (parameters != null) {
            int i = 0;
            for (SingleVariableDeclaration decl : parameters) {
                DFRef ref = scope.lookupArgument(i);
                DFNode input = new InputNode(graph, scope, ref, decl);
                ctx.set(input);
                DFNode assign = new SingleAssignNode(
                    graph, scope, scope.lookupVar(decl.getName()), decl);
                assign.accept(input);
                ctx.set(assign);
                i++;
            }
        }
        for (DFRef ref : frame.getInputRefs()) {
            if (ref.isLocal() || ref.isInternal()) continue;
            DFNode input = new InputNode(graph, scope, ref, null);
            ctx.set(input);
        }
        {
            DFRef ref = scope.lookupThis();
            DFNode input = new InputNode(graph, scope, ref, null);
            ctx.set(input);
        }

        try {
            // Process the function body.
            processStatement(
                ctx, method, graph, finder, scope, frame, body);
            frame.close(ctx);
            for (DFRef ref : frame.getOutputRefs()) {
                if (ref.isLocal() || ref.isInternal()) continue;
                DFNode output = new OutputNode(graph, scope, ref, null);
                output.accept(ctx.get(ref));
            }
            //frame.dump();
        } catch (MethodNotFound e) {
            e.setMethod(method);
            Logger.error("MethodNotFound:", e.name+"("+Utils.join(e.argTypes)+")");
            throw e;
        } catch (EntityNotFound e) {
            e.setMethod(method);
            Logger.error("EntityNotFound:", e.name);
            throw e;
        }

        Logger.debug("Success:", method.getSignature());
        return graph;
    }

    @SuppressWarnings("unchecked")
    private DFGraph processKlassBody(DFKlass klass)
        throws UnsupportedSyntax, EntityNotFound {
        // lookup base/inner klasses.
        ASTNode ast = klass.getTree();
        List<BodyDeclaration> decls;
        if (ast instanceof AbstractTypeDeclaration) {
            decls = ((AbstractTypeDeclaration)ast).bodyDeclarations();
        } else if (ast instanceof AnonymousClassDeclaration) {
            decls = ((AnonymousClassDeclaration)ast).bodyDeclarations();
        } else {
            throw new UnsupportedSyntax(ast);
        }
        DFVarScope klassScope = klass.getKlassScope();
        DFFrame klassFrame = new DFFrame(DFFrame.ANONYMOUS);
	DFTypeSpace klassSpace = klass;
        DFGraph klassGraph = new DFGraph(klassScope, null, ast);
        DFContext klassCtx = new DFContext(klassGraph, klassScope);
        DFTypeFinder finder = klass.getFinder();
        for (BodyDeclaration body : decls) {
            if (body instanceof AbstractTypeDeclaration) {
                // Inner classes are processed separately.

            } else if (body instanceof FieldDeclaration) {
                FieldDeclaration fieldDecl = (FieldDeclaration)body;
                for (VariableDeclarationFragment frag :
                         (List<VariableDeclarationFragment>) fieldDecl.fragments()) {
                    DFRef ref = klass.lookupField(frag.getName());
                    DFNode value = null;
                    Expression init = frag.getInitializer();
                    if (init != null) {
                        processExpression(
                            klassCtx, klassSpace, klassGraph, finder,
                            klassScope, klassFrame, init);
                        value = klassCtx.getRValue();
                    }
                    if (value == null) {
                        // uninitialized field: default = null.
                        value = new ConstNode(
                            klassGraph, klassScope,
                            DFNullType.NULL, null, "uninitialized");
                    }
                    DFNode assign = new SingleAssignNode(
                        klassGraph, klassScope, ref, frag);
                    assign.accept(value);
                }

            } else if (body instanceof MethodDeclaration) {

            } else if (body instanceof EnumConstantDeclaration) {

            } else if (body instanceof AnnotationTypeMemberDeclaration) {

            } else if (body instanceof Initializer) {

            } else {
                throw new UnsupportedSyntax(body);
            }
        }
        return klassGraph;
    }

    private void enumKlasses(DFKlass klass, Set<DFKlass> klasses)
        throws TypeNotFound {
        klass.load();
        ASTNode ast = klass.getTree();
        if (ast == null) return;
        if (klasses.contains(klass)) return;
        //Logger.info("enumKlasses:", klass);
        klasses.add(klass);
        DFTypeFinder finder = klass.getFinder();
        List<DFKlass> toLoad = new ArrayList<DFKlass>();
        try {
            this.enumKlassesDecl(finder, klass, ast, klasses);
        } catch (UnsupportedSyntax e) {
        }
    }

    @SuppressWarnings("unchecked")
    private void enumKlassesDecl(
        DFTypeFinder finder, DFKlass klass,
        ASTNode ast, Set<DFKlass> klasses)
        throws UnsupportedSyntax, TypeNotFound {
        if (ast instanceof AbstractTypeDeclaration) {
            AbstractTypeDeclaration abstDecl = (AbstractTypeDeclaration)ast;
            this.enumKlassesDecls(
                finder, klass, abstDecl.bodyDeclarations(), klasses);
        } else if (ast instanceof AnonymousClassDeclaration) {
            AnonymousClassDeclaration anonDecl = (AnonymousClassDeclaration)ast;
            this.enumKlassesDecls(
                finder, klass, anonDecl.bodyDeclarations(), klasses);
        } else {
            throw new UnsupportedSyntax(ast);
        }
    }

    @SuppressWarnings("unchecked")
    private void enumKlassesDecls(
        DFTypeFinder finder, DFKlass klass,
        List<BodyDeclaration> decls, Set<DFKlass> klasses)
        throws UnsupportedSyntax, TypeNotFound {

        for (BodyDeclaration body : decls) {
            if (body instanceof AbstractTypeDeclaration) {
                AbstractTypeDeclaration decl = (AbstractTypeDeclaration)body;
                try {
                    DFKlass innerKlass = klass.getType(decl.getName()).toKlass();
                    enumKlasses(innerKlass, klasses);
                } catch (TypeNotFound e) {
                    e.setAst(decl);
                    if (0 < _strict) throw e;
                }

            } else if (body instanceof FieldDeclaration) {
                FieldDeclaration decl = (FieldDeclaration)body;
                try {
                    DFType fldType = finder.resolve(decl.getType());
                    if (fldType instanceof DFKlass) {
                        enumKlasses((DFKlass)fldType, klasses);
                    }
                } catch (TypeNotFound e) {
                    e.setAst(decl);
                    if (0 < _strict) throw e;
                }

                for (VariableDeclarationFragment frag :
                         (List<VariableDeclarationFragment>) decl.fragments()) {
                    Expression expr = frag.getInitializer();
                    if (expr != null) {
                        this.enumKlassesExpr(finder, klass, expr, klasses);
                    }
                }

            } else if (body instanceof MethodDeclaration) {
                MethodDeclaration decl = (MethodDeclaration)body;
                String id = Utils.encodeASTNode(decl);
                DFMethod method = klass.getMethod(id);
                DFTypeFinder finder2 = method.getFinder();
                try {
                    for (DFType argType : finder2.resolveArgs(decl)) {
                        if (argType instanceof DFKlass) {
                            enumKlasses((DFKlass)argType, klasses);
                        }
                    }
                    if (!decl.isConstructor()) {
                        DFType returnType = finder2.resolve(
                            decl.getReturnType2());
                        if (returnType instanceof DFKlass) {
                            enumKlasses((DFKlass)returnType, klasses);
                        }
                    }
                } catch (TypeNotFound e) {
                    e.setAst(decl);
                    if (0 < _strict) throw e;
		}
                if (decl.getBody() != null) {
                    this.enumKlassesStmt(
                        finder2, method, decl.getBody(), klasses);
                }

            } else if (body instanceof EnumConstantDeclaration) {

            } else if (body instanceof AnnotationTypeMemberDeclaration) {
                AnnotationTypeMemberDeclaration decl =
                    (AnnotationTypeMemberDeclaration)body;
                try {
                    DFType type = finder.resolve(decl.getType());
                    if (type instanceof DFKlass) {
                        enumKlasses((DFKlass)type, klasses);
                    }
                } catch (TypeNotFound e) {
                    e.setAst(decl);
                    if (0 < _strict) throw e;
                }

            } else if (body instanceof Initializer) {
                Initializer initializer = (Initializer)body;
                this.enumKlassesStmt(
                    finder, klass, initializer.getBody(), klasses);

            } else {
                throw new UnsupportedSyntax(body);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void enumKlassesStmt(
        DFTypeFinder finder, DFTypeSpace klass,
        Statement ast, Set<DFKlass> klasses)
        throws UnsupportedSyntax, TypeNotFound {
        assert ast != null;

        if (ast instanceof AssertStatement) {

        } else if (ast instanceof Block) {
            Block block = (Block)ast;
            for (Statement stmt :
                     (List<Statement>) block.statements()) {
                this.enumKlassesStmt(finder, klass, stmt, klasses);
            }

        } else if (ast instanceof EmptyStatement) {

        } else if (ast instanceof VariableDeclarationStatement) {
            VariableDeclarationStatement varStmt =
                (VariableDeclarationStatement)ast;
            try {
                DFType varType = finder.resolve(varStmt.getType());
                if (varType instanceof DFKlass) {
                    enumKlasses((DFKlass)varType, klasses);
                }
            } catch (TypeNotFound e) {
                e.setAst(varStmt);
                if (0 < _strict) throw e;
            }
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>) varStmt.fragments()) {
                Expression expr = frag.getInitializer();
                if (expr != null) {
                    this.enumKlassesExpr(finder, klass, expr, klasses);
                }
            }

        } else if (ast instanceof ExpressionStatement) {
            ExpressionStatement exprStmt = (ExpressionStatement)ast;
            Expression expr = exprStmt.getExpression();
            this.enumKlassesExpr(finder, klass, expr, klasses);

        } else if (ast instanceof ReturnStatement) {
            ReturnStatement returnStmt = (ReturnStatement)ast;
            Expression expr = returnStmt.getExpression();
            if (expr != null) {
                this.enumKlassesExpr(finder, klass, expr, klasses);
            }

        } else if (ast instanceof IfStatement) {
            IfStatement ifStmt = (IfStatement)ast;
            Expression expr = ifStmt.getExpression();
            this.enumKlassesExpr(finder, klass, expr, klasses);
            Statement thenStmt = ifStmt.getThenStatement();
            this.enumKlassesStmt(finder, klass, thenStmt, klasses);
            Statement elseStmt = ifStmt.getElseStatement();
            if (elseStmt != null) {
                this.enumKlassesStmt(finder, klass, elseStmt, klasses);
            }

        } else if (ast instanceof SwitchStatement) {
            SwitchStatement switchStmt = (SwitchStatement)ast;
            Expression expr = switchStmt.getExpression();
            this.enumKlassesExpr(finder, klass, expr, klasses);
            for (Statement stmt :
                     (List<Statement>) switchStmt.statements()) {
                this.enumKlassesStmt(finder, klass, stmt, klasses);
            }

        } else if (ast instanceof SwitchCase) {
            SwitchCase switchCase = (SwitchCase)ast;
            Expression expr = switchCase.getExpression();
            if (expr != null) {
                this.enumKlassesExpr(finder, klass, expr, klasses);
            }

        } else if (ast instanceof WhileStatement) {
            WhileStatement whileStmt = (WhileStatement)ast;
            Expression expr = whileStmt.getExpression();
            this.enumKlassesExpr(finder, klass, expr, klasses);
            Statement stmt = whileStmt.getBody();
            this.enumKlassesStmt(finder, klass, stmt, klasses);

        } else if (ast instanceof DoStatement) {
            DoStatement doStmt = (DoStatement)ast;
            Statement stmt = doStmt.getBody();
            this.enumKlassesStmt(finder, klass, stmt, klasses);
            Expression expr = doStmt.getExpression();
            this.enumKlassesExpr(finder, klass, expr, klasses);

        } else if (ast instanceof ForStatement) {
            ForStatement forStmt = (ForStatement)ast;
            for (Expression init :
                     (List<Expression>) forStmt.initializers()) {
                this.enumKlassesExpr(finder, klass, init, klasses);
            }
            Expression expr = forStmt.getExpression();
            if (expr != null) {
                this.enumKlassesExpr(finder, klass, expr, klasses);
            }
            Statement stmt = forStmt.getBody();
            this.enumKlassesStmt(finder, klass, stmt, klasses);
            for (Expression update :
                     (List<Expression>) forStmt.updaters()) {
                this.enumKlassesExpr(finder, klass, update, klasses);
            }

        } else if (ast instanceof EnhancedForStatement) {
            EnhancedForStatement eForStmt = (EnhancedForStatement)ast;
            this.enumKlassesExpr(finder, klass, eForStmt.getExpression(), klasses);
            SingleVariableDeclaration decl = eForStmt.getParameter();
            try {
                DFType varType = finder.resolve(decl.getType());
                if (varType instanceof DFKlass) {
                    enumKlasses((DFKlass)varType, klasses);
                }
            } catch (TypeNotFound e) {
                e.setAst(decl);
                if (0 < _strict) throw e;
            }
            Statement stmt = eForStmt.getBody();
            this.enumKlassesStmt(finder, klass, stmt, klasses);

        } else if (ast instanceof BreakStatement) {

        } else if (ast instanceof ContinueStatement) {

        } else if (ast instanceof LabeledStatement) {
            LabeledStatement labeledStmt = (LabeledStatement)ast;
            Statement stmt = labeledStmt.getBody();
            this.enumKlassesStmt(finder, klass, stmt, klasses);

        } else if (ast instanceof SynchronizedStatement) {
            SynchronizedStatement syncStmt = (SynchronizedStatement)ast;
            this.enumKlassesExpr(finder, klass, syncStmt.getExpression(), klasses);
            this.enumKlassesStmt(finder, klass, syncStmt.getBody(), klasses);

        } else if (ast instanceof TryStatement) {
            TryStatement tryStmt = (TryStatement)ast;
            for (VariableDeclarationExpression decl :
                     (List<VariableDeclarationExpression>) tryStmt.resources()) {
                this.enumKlassesExpr(finder, klass, decl, klasses);
            }
            this.enumKlassesStmt(finder, klass, tryStmt.getBody(), klasses);
            for (CatchClause cc :
                     (List<CatchClause>) tryStmt.catchClauses()) {
                SingleVariableDeclaration decl = cc.getException();
                try {
                    DFType varType = finder.resolve(decl.getType());
                    if (varType instanceof DFKlass) {
                        enumKlasses((DFKlass)varType, klasses);
                    }
                } catch (TypeNotFound e) {
                    e.setAst(decl);
                    if (0 < _strict) throw e;
                }
                this.enumKlassesStmt(finder, klass, cc.getBody(), klasses);
            }
            Block finBlock = tryStmt.getFinally();
            if (finBlock != null) {
                this.enumKlassesStmt(finder, klass, finBlock, klasses);
            }

        } else if (ast instanceof ThrowStatement) {
            ThrowStatement throwStmt = (ThrowStatement)ast;
            Expression expr = throwStmt.getExpression();
            if (expr != null) {
                this.enumKlassesExpr(finder, klass, expr, klasses);
            }

        } else if (ast instanceof ConstructorInvocation) {
            ConstructorInvocation ci = (ConstructorInvocation)ast;
            for (Expression expr :
                     (List<Expression>) ci.arguments()) {
                this.enumKlassesExpr(finder, klass, expr, klasses);
            }

        } else if (ast instanceof SuperConstructorInvocation) {
            SuperConstructorInvocation sci = (SuperConstructorInvocation)ast;
            for (Expression expr :
                     (List<Expression>) sci.arguments()) {
                this.enumKlassesExpr(finder, klass, expr, klasses);
            }

        } else if (ast instanceof TypeDeclarationStatement) {
            TypeDeclarationStatement decl = (TypeDeclarationStatement)ast;
            AbstractTypeDeclaration abstDecl = decl.getDeclaration();
            DFKlass innerKlass = klass.getType(abstDecl.getName()).toKlass();
            this.enumKlasses(innerKlass, klasses);

        } else {
            throw new UnsupportedSyntax(ast);

        }
    }

    @SuppressWarnings("unchecked")
    private void enumKlassesExpr(
        DFTypeFinder finder, DFTypeSpace klass,
        Expression ast, Set<DFKlass> klasses)
        throws UnsupportedSyntax, TypeNotFound {
        assert ast != null;

        if (ast instanceof Annotation) {

        } else if (ast instanceof Name) {

        } else if (ast instanceof ThisExpression) {
            // "this"
            ThisExpression thisExpr = (ThisExpression)ast;
            Name name = thisExpr.getQualifier();
            if (name != null) {
                try {
                    DFKlass innerKlass = finder.lookupType(name).toKlass();
                    enumKlasses(innerKlass, klasses);
                } catch (TypeNotFound e) {
                    e.setAst(name);
                    if (0 < _strict) throw e;
                }
            }

        } else if (ast instanceof BooleanLiteral) {

        } else if (ast instanceof CharacterLiteral) {

        } else if (ast instanceof NullLiteral) {

        } else if (ast instanceof NumberLiteral) {

        } else if (ast instanceof StringLiteral) {

        } else if (ast instanceof TypeLiteral) {

        } else if (ast instanceof PrefixExpression) {
            PrefixExpression prefix = (PrefixExpression)ast;
            PrefixExpression.Operator op = prefix.getOperator();
            Expression operand = prefix.getOperand();
            this.enumKlassesExpr(finder, klass, operand, klasses);
            if (op == PrefixExpression.Operator.INCREMENT ||
                op == PrefixExpression.Operator.DECREMENT) {
                this.enumKlassesExpr(finder, klass, operand, klasses);
            }

        } else if (ast instanceof PostfixExpression) {
            PostfixExpression postfix = (PostfixExpression)ast;
            PostfixExpression.Operator op = postfix.getOperator();
            Expression operand = postfix.getOperand();
            this.enumKlassesExpr(finder, klass, operand, klasses);
            if (op == PostfixExpression.Operator.INCREMENT ||
                op == PostfixExpression.Operator.DECREMENT) {
                this.enumKlassesExpr(finder, klass, operand, klasses);
            }

        } else if (ast instanceof InfixExpression) {
            InfixExpression infix = (InfixExpression)ast;
            InfixExpression.Operator op = infix.getOperator();
            Expression loperand = infix.getLeftOperand();
            this.enumKlassesExpr(finder, klass, loperand, klasses);
            Expression roperand = infix.getRightOperand();
            this.enumKlassesExpr(finder, klass, roperand, klasses);

        } else if (ast instanceof ParenthesizedExpression) {
            ParenthesizedExpression paren = (ParenthesizedExpression)ast;
            this.enumKlassesExpr(finder, klass, paren.getExpression(), klasses);

        } else if (ast instanceof Assignment) {
            Assignment assn = (Assignment)ast;
            Assignment.Operator op = assn.getOperator();
            this.enumKlassesExpr(finder, klass, assn.getLeftHandSide(), klasses);
            if (op != Assignment.Operator.ASSIGN) {
                this.enumKlassesExpr(finder, klass, assn.getLeftHandSide(), klasses);
            }
            this.enumKlassesExpr(finder, klass, assn.getRightHandSide(), klasses);

        } else if (ast instanceof VariableDeclarationExpression) {
            VariableDeclarationExpression decl = (VariableDeclarationExpression)ast;
            try {
                DFType varType = finder.resolve(decl.getType());
                if (varType instanceof DFKlass) {
                    enumKlasses((DFKlass)varType, klasses);
                }
            } catch (TypeNotFound e) {
                e.setAst(decl);
                if (0 < _strict) throw e;
            }
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>) decl.fragments()) {
                Expression expr = frag.getInitializer();
                if (expr != null) {
                    this.enumKlassesExpr(finder, klass, expr, klasses);
                }
            }

        } else if (ast instanceof MethodInvocation) {
            MethodInvocation invoke = (MethodInvocation)ast;
            Expression expr = invoke.getExpression();
            if (expr instanceof Name) {
                try {
                    DFKlass innerKlass = finder.lookupType((Name)expr).toKlass();
                    enumKlasses(innerKlass, klasses);
                } catch (TypeNotFound e) {
                }
            } else if (expr != null) {
                this.enumKlassesExpr(finder, klass, expr, klasses);
            }
            for (Expression arg :
                     (List<Expression>) invoke.arguments()) {
                this.enumKlassesExpr(finder, klass, arg, klasses);
            }

        } else if (ast instanceof SuperMethodInvocation) {
            SuperMethodInvocation si = (SuperMethodInvocation)ast;
            for (Expression arg :
                     (List<Expression>) si.arguments()) {
                this.enumKlassesExpr(finder, klass, arg, klasses);
            }

        } else if (ast instanceof ArrayCreation) {
            ArrayCreation ac = (ArrayCreation)ast;
            for (Expression dim :
                     (List<Expression>) ac.dimensions()) {
                this.enumKlassesExpr(finder, klass, dim, klasses);
            }
            ArrayInitializer init = ac.getInitializer();
            if (init != null) {
                this.enumKlassesExpr(finder, klass, init, klasses);
            }
            try {
                DFType type = finder.resolve(ac.getType().getElementType());
                if (type instanceof DFKlass) {
                    enumKlasses((DFKlass)type, klasses);
                }
            } catch (TypeNotFound e) {
                e.setAst(ac);
                if (0 < _strict) throw e;
            }

        } else if (ast instanceof ArrayInitializer) {
            ArrayInitializer init = (ArrayInitializer)ast;
            for (Expression expr :
                     (List<Expression>) init.expressions()) {
                this.enumKlassesExpr(finder, klass, expr, klasses);
            }

        } else if (ast instanceof ArrayAccess) {
            ArrayAccess aa = (ArrayAccess)ast;
            this.enumKlassesExpr(finder, klass, aa.getArray(), klasses);
            this.enumKlassesExpr(finder, klass, aa.getIndex(), klasses);

        } else if (ast instanceof FieldAccess) {
            FieldAccess fa = (FieldAccess)ast;
            SimpleName fieldName = fa.getName();
            this.enumKlassesExpr(finder, klass, fa.getExpression(), klasses);

        } else if (ast instanceof SuperFieldAccess) {
            SuperFieldAccess sfa = (SuperFieldAccess)ast;
            SimpleName fieldName = sfa.getName();

        } else if (ast instanceof CastExpression) {
            CastExpression cast = (CastExpression)ast;
            this.enumKlassesExpr(finder, klass, cast.getExpression(), klasses);
            try {
                DFType type = finder.resolve(cast.getType());
                if (type instanceof DFKlass) {
                    enumKlasses((DFKlass)type, klasses);
                }
            } catch (TypeNotFound e) {
                e.setAst(cast);
                if (0 < _strict) throw e;
            }

        } else if (ast instanceof ClassInstanceCreation) {
            ClassInstanceCreation cstr = (ClassInstanceCreation)ast;
            AnonymousClassDeclaration anonDecl = cstr.getAnonymousClassDeclaration();
            try {
                DFType instType;
                if (anonDecl != null) {
                    String id = Utils.encodeASTNode(anonDecl);
                    instType = klass.getType(id);
                } else {
                    instType = finder.resolve(cstr.getType());
                }
                if (instType instanceof DFKlass) {
                    enumKlasses((DFKlass)instType, klasses);
                }
            } catch (TypeNotFound e) {
                e.setAst(cstr);
                if (0 < _strict) throw e;
            }
            Expression expr = cstr.getExpression();
            if (expr != null) {
                this.enumKlassesExpr(finder, klass, expr, klasses);
            }
            for (Expression arg :
                     (List<Expression>) cstr.arguments()) {
                this.enumKlassesExpr(finder, klass, arg, klasses);
            }

        } else if (ast instanceof ConditionalExpression) {
            ConditionalExpression cond = (ConditionalExpression)ast;
            this.enumKlassesExpr(finder, klass, cond.getExpression(), klasses);
            this.enumKlassesExpr(finder, klass, cond.getThenExpression(), klasses);
            this.enumKlassesExpr(finder, klass, cond.getElseExpression(), klasses);

        } else if (ast instanceof InstanceofExpression) {
            InstanceofExpression instof = (InstanceofExpression)ast;
            this.enumKlassesExpr(finder, klass, instof.getLeftOperand(), klasses);

        } else {
            // LambdaExpression
            // MethodReference
            //  CreationReference
            //  ExpressionMethodReference
            //  SuperMethodReference
            //  TypeMethodReference
            throw new UnsupportedSyntax(ast);

        }
    }

    /// Top-level functions.

    private DFRootTypeSpace _rootSpace;
    private int _strict;
    private List<Exporter> _exporters =
        new ArrayList<Exporter>();
    private DFGlobalVarScope _globalScope =
        new DFGlobalVarScope();
    private Map<String, DFFileScope> _fileScope =
        new HashMap<String, DFFileScope>();

    public Java2DF(
        DFRootTypeSpace rootSpace, int strict) {
        _rootSpace = rootSpace;
        _strict = strict;
    }

    public void addExporter(Exporter exporter) {
        _exporters.add(exporter);
    }

    public void removeExporter(Exporter exporter) {
        _exporters.remove(exporter);
    }

    private void exportGraph(DFGraph graph) {
        graph.cleanup();
        for (Exporter exporter : _exporters) {
            exporter.writeGraph(graph);
        }
    }
    private void startKlass(DFKlass klass) {
        for (Exporter exporter : _exporters) {
            exporter.startKlass(klass);
        }
    }
    private void endKlass() {
        for (Exporter exporter : _exporters) {
            exporter.endKlass();
        }
    }

    // Pass1: populate TypeSpaces.
    @SuppressWarnings("unchecked")
    public void buildTypeSpace(String key, CompilationUnit cunit)
        throws UnsupportedSyntax {
        DFTypeSpace packageSpace = _rootSpace.lookupSpace(cunit.getPackage());
        DFFileScope fileScope = new DFFileScope(_globalScope, key);
        _fileScope.put(key, fileScope);
	for (AbstractTypeDeclaration abstTypeDecl :
		 (List<AbstractTypeDeclaration>) cunit.types()) {
	    try {
		DFKlass klass = packageSpace.buildAbstTypeDecl(
		    key, abstTypeDecl, null, fileScope);
                Logger.debug("Pass1: Created:", klass);
	    } catch (UnsupportedSyntax e) {
		Logger.error("Pass1: UnsupportedSyntax at",
                             key, e.name, "("+e.getAstName()+")");
                if (0 < _strict) throw e;
	    }
	}
    }

    // Pass2: set references to external Klasses.
    @SuppressWarnings("unchecked")
    public void setTypeFinder(String key, CompilationUnit cunit) {
	// Search path for types: ROOT -> java.lang -> package -> imports.
        DFTypeFinder finder = new DFTypeFinder(_rootSpace);
        finder = new DFTypeFinder(_rootSpace.lookupSpace("java.lang"), finder);
        DFTypeSpace packageSpace = _rootSpace.lookupSpace(cunit.getPackage());
        finder = new DFTypeFinder(packageSpace, finder);
	// Populate the import space.
        DFTypeSpace importSpace = new DFTypeSpace("import:"+key);
        for (ImportDeclaration importDecl :
                 (List<ImportDeclaration>) cunit.imports()) {
            Name name = importDecl.getName();
	    if (importDecl.isOnDemand()) {
		Logger.debug("Import:", name+".*");
		finder = new DFTypeFinder(_rootSpace.lookupSpace(name), finder);
	    } else {
		assert name.isQualifiedName();
		try {
		    DFKlass klass = _rootSpace.getType(name).toKlass();
		    Logger.debug("Import:", name);
                    String id = ((QualifiedName)name).getName().getIdentifier();
		    importSpace.addKlass(id, klass);
		} catch (TypeNotFound e) {
		    if (!importDecl.isStatic()) {
			Logger.error("Import: Class not found:", e.name);
		    }
		}
	    }
        }
	finder = new DFTypeFinder(importSpace, finder);
	for (AbstractTypeDeclaration abstTypeDecl :
		 (List<AbstractTypeDeclaration>) cunit.types()) {
	    try {
		DFKlass klass = packageSpace.getType(abstTypeDecl.getName()).toKlass();
                klass.setFinder(finder);
	    } catch (TypeNotFound e) {
	    }
	}
    }

    // Pass3: load class definitions and define parameterized Klasses.
    @SuppressWarnings("unchecked")
    public void loadKlasses(
        String key, CompilationUnit cunit, Set<DFKlass> klasses)
        throws TypeNotFound {
        // Process static imports.
        DFFileScope fileScope = _fileScope.get(key);
        for (ImportDeclaration importDecl :
                 (List<ImportDeclaration>) cunit.imports()) {
            if (!importDecl.isStatic()) continue;
            Name name = importDecl.getName();
            DFKlass klass;
            try {
                if (importDecl.isOnDemand()) {
                    klass = _rootSpace.getType(name).toKlass();
                    klass.load();
                    fileScope.importStatic(klass);
                } else {
                    QualifiedName qname = (QualifiedName)name;
                    klass = _rootSpace.getType(qname.getQualifier()).toKlass();
                    klass.load();
                    fileScope.importStatic(klass, qname.getName());
                }
            } catch (TypeNotFound e) {
                if (0 < _strict) throw e;
            }
        }
        DFTypeSpace packageSpace = _rootSpace.lookupSpace(cunit.getPackage());
	for (AbstractTypeDeclaration abstTypeDecl :
		 (List<AbstractTypeDeclaration>) cunit.types()) {
            DFKlass klass = packageSpace.getType(abstTypeDecl.getName()).toKlass();
            try {
                enumKlasses(klass, klasses);
            } catch (TypeNotFound e) {
                if (0 < _strict) throw e;
            }
        }
    }

    // Pass4: list all methods.
    public void listMethods(Set<DFKlass> klasses)
        throws UnsupportedSyntax, TypeNotFound {
        // At this point, all the methods in all the used classes
        // (public, inner, in-statement and anonymous) are known.

        // Build method scopes.
        for (DFKlass klass : klasses) {
            klass.overrideMethods();
            DFMethod init = klass.getInitializer();
            if (init != null) {
                try {
                    init.buildScope();
                } catch (UnsupportedSyntax e) {
                    if (0 < _strict) throw e;
                } catch (TypeNotFound e) {
                    if (0 < _strict) throw e;
                }
            }
            for (DFMethod method : klass.getMethods()) {
                try {
                    method.buildScope();
                } catch (UnsupportedSyntax e) {
                    if (0 < _strict) throw e;
                } catch (TypeNotFound e) {
                    if (0 < _strict) throw e;
                }
            }
        }

        // Build call graphs.
        Queue<DFMethod> queue = new ArrayDeque<DFMethod>();
        for (DFKlass klass : klasses) {
            DFMethod init = klass.getInitializer();
            if (init != null) {
                try {
                    init.buildFrame();
                } catch (UnsupportedSyntax e) {
                    if (0 < _strict) throw e;
                } catch (EntityNotFound e) {
                    if (0 < _strict) throw e;
                }
            }
            for (DFMethod method : klass.getMethods()) {
                try {
                    method.buildFrame();
                    queue.add(method);
                } catch (UnsupportedSyntax e) {
                    if (0 < _strict) throw e;
                } catch (EntityNotFound e) {
                    if (0 < _strict) throw e;
                }
            }
        }

        // Expand callee frames recursively.
        while (!queue.isEmpty()) {
            DFMethod method = queue.remove();
            DFFrame fcallee = method.getFrame();
            if (fcallee == null) continue;
            for (DFMethod caller : method.getCallers()) {
                DFFrame fcaller = caller.getFrame();
                if (fcaller == null) continue;
                if (fcaller.expandRefs(fcallee)) {
                    queue.add(caller);
                }
            }
        }
    }

    // Pass5: generate graphs for each method.
    @SuppressWarnings("unchecked")
    public void buildGraphs(DFKlass klass)
        throws UnsupportedSyntax, EntityNotFound {
        this.startKlass(klass);
        try {
            DFGraph graph = processKlassBody(klass);
            this.exportGraph(graph);
        } catch (UnsupportedSyntax e) {
            if (0 < _strict) throw e;
        } catch (EntityNotFound e) {
            if (0 < _strict) throw e;
        }
        DFMethod init = klass.getInitializer();
        if (init != null) {
            try {
                Initializer initializer = (Initializer)init.getTree();
                DFGraph graph = processMethod(
                    init, initializer,
                    initializer.getBody(), null);
                this.exportGraph(graph);
            } catch (UnsupportedSyntax e) {
                if (0 < _strict) throw e;
            } catch (EntityNotFound e) {
                if (0 < _strict) throw e;
            }
        }
        for (DFMethod method : klass.getMethods()) {
            if (method.getFrame() == null) continue;
            MethodDeclaration methodDecl = (MethodDeclaration)method.getTree();
            try {
                if (methodDecl.getBody() != null) {
                    DFGraph graph = processMethod(
                        method, methodDecl,
                        methodDecl.getBody(), methodDecl.parameters());
                    this.exportGraph(graph);
                }
            } catch (UnsupportedSyntax e) {
                if (0 < _strict) throw e;
            } catch (EntityNotFound e) {
                if (0 < _strict) throw e;
            }
        }
        this.endKlass();
    }

    /**
     * Provides a command line interface.
     *
     * Usage: java Java2DF [-o output] input.java ...
     */
    public static void main(String[] args)
        throws IOException, UnsupportedSyntax, EntityNotFound {

        // Parse the options.
        List<String> files = new ArrayList<String>();
        Set<String> processed = null;
        OutputStream output = System.out;
        String sep = System.getProperty("path.separator");
        int strict = 0;
        Logger.LogLevel = 0;

        DFRootTypeSpace rootSpace = new DFRootTypeSpace();
        try {
            DFBuiltinTypes.initialize(rootSpace);
        } catch (TypeNotFound e) {
            e.printStackTrace();
            Logger.error("Class not found:", e.name);
            System.err.println("Fatal error at initialization.");
            System.exit(1);
            return;
        }
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--")) {
                while (i < args.length) {
                    files.add(args[++i]);
                }
	    } else if (arg.equals("-i")) {
		String path = args[++i];
		InputStream input = System.in;
		try {
		    if (!path.equals("-")) {
			input = new FileInputStream(path);
		    }
		    Logger.info("Input file:", path);
		    BufferedReader reader = new BufferedReader(
			new InputStreamReader(input));
		    while (true) {
			String line = reader.readLine();
			if (line == null) break;
			files.add(line);
		    }
		} catch (IOException e) {
		    System.err.println("Cannot open input file: "+path);
		}
            } else if (arg.equals("-v")) {
		Logger.LogLevel++;
            } else if (arg.equals("-o")) {
                String path = args[++i];
                try {
                    output = new FileOutputStream(path);
                    Logger.info("Exporting:", path);
                } catch (IOException e) {
                    System.err.println("Cannot open output file: "+path);
                }
            } else if (arg.equals("-p")) {
                if (processed == null) {
                    processed = new HashSet<String>();
                }
                processed.add(args[++i]);
            } else if (arg.equals("-C")) {
                for (String path : args[++i].split(sep)) {
                    rootSpace.loadJarFile(path);
                }
            } else if (arg.equals("-S")) {
                strict++;
            } else if (arg.startsWith("-")) {
                System.err.println("Unknown option: "+arg);
                System.err.println(
		    "usage: Java2DF [-v] [-S] [-i input] [-o output]" +
		    " [-C jar] [-p path] [path ...]");
                System.exit(1);
                return;
            } else {
                files.add(arg);
            }
        }

        // Process files.
        Java2DF converter = new Java2DF(rootSpace, strict);
        Map<String, CompilationUnit> srcs =
            new HashMap<String, CompilationUnit>();
        for (String path : files) {
            Logger.info("Pass1:", path);
            try {
                CompilationUnit cunit = Utils.parseFile(path);
                srcs.put(path, cunit);
                converter.buildTypeSpace(path, cunit);
            } catch (IOException e) {
                Logger.error("Pass1: IOException at "+path);
                throw e;
	    } catch (UnsupportedSyntax e) {
                throw e;
            }
        }
        for (String path : files) {
            Logger.info("Pass2:", path);
            CompilationUnit cunit = srcs.get(path);
            converter.setTypeFinder(path, cunit);
        }
        Set<DFKlass> klasses = new TreeSet<DFKlass>();
        for (String path : files) {
            Logger.info("Pass3:", path);
            CompilationUnit cunit = srcs.get(path);
            try {
                converter.loadKlasses(path, cunit, klasses);
            } catch (TypeNotFound e) {
                Logger.error("Pass3: TypeNotFound at", path,
                             "("+e.name+", method="+e.method+
                             ", ast="+e.ast+", finder="+e.finder+")");
                if (e.finder != null) {
                    e.finder.dump();
                }
                throw e;
            }
        }
        Logger.info("Pass4.");
        try {
            converter.listMethods(klasses);
        } catch (UnsupportedSyntax e) {
            Logger.error("Pass4: UnsupportedSyntax",
                         e.name, "("+e.getAstName()+")");
            throw e;
        } catch (TypeNotFound e) {
            Logger.error("Pass4: TypeNotFound",
                         "("+e.name+", method="+e.method+
                         ", ast="+e.ast+", finder="+e.finder+")");
            throw e;
        }

        XmlExporter exporter = new XmlExporter();
        converter.addExporter(exporter);
        for (DFKlass klass : klasses) {
            if (processed != null && !processed.contains(klass.getTypeName())) continue;
            Logger.info("Pass5:", klass);
            try {
                converter.buildGraphs(klass);
            } catch (UnsupportedSyntax e) {
                Logger.error("Pass5: Unsupported at", klass,
                             e.name, "("+e.getAstName()+")");
                throw e;
            } catch (EntityNotFound e) {
                Logger.error("Pass5: EntityNotFound at", klass,
                             "("+e.name+", method="+e.method+
                             ", ast="+e.ast+")");
                throw e;
            }
        }
        exporter.close();

        Utils.printXml(output, exporter.document);
        output.close();
    }
}
