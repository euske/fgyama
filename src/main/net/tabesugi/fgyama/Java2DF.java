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
    public Element toXML(
	Document document, Set<DFNode> input, Set<DFNode> output) {
        Element elem = super.toXML(document, input, output);
        if (_ast != null) {
            Element east = document.createElement("ast");
            east.setAttribute("type", Integer.toString(_ast.getNodeType()));
            east.setAttribute("start", Integer.toString(_ast.getStartPosition()));
            east.setAttribute("length", Integer.toString(_ast.getLength()));
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
        DFGraph graph, DFVarScope scope, DFRef ref,
        ASTNode ast, PrefixExpression.Operator op) {
        super(graph, scope, (ref != null)? ref.getRefType() : null, ref, ast);
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
        DFGraph graph, DFVarScope scope, DFRef ref,
        ASTNode ast, DFNode cond) {
        super(graph, scope, (ref != null)? ref.getRefType() : null, ref, ast);
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

// LoopBeginNode
class LoopBeginNode extends ProgNode {

    public LoopBeginNode(
        DFGraph graph, DFVarScope scope, DFRef ref,
        ASTNode ast, DFNode enter) {
        super(graph, scope, ref.getRefType(), ref, ast);
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
        DFGraph graph, DFVarScope scope, DFRef ref,
        ASTNode ast, DFNode cond) {
        super(graph, scope, ref.getRefType(), ref, ast);
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
        DFGraph graph, DFVarScope scope, DFRef ref,
        ASTNode ast) {
        super(graph, scope, ref.getRefType(), ref, ast);
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

// UpdateNode:
class UpdateNode extends SingleAssignNode {

    public UpdateNode(
        DFGraph graph, DFVarScope scope, DFRef ref,
        ASTNode ast, CallNode call) {
        super(graph, scope, ref, ast);
        this.accept(call, "update");
    }
}

// CreateObjectNode
class CreateObjectNode extends CallNode {

    public CreateObjectNode(
        DFGraph graph, DFVarScope scope, DFType type,
        ASTNode ast, DFNode obj) {
        super(graph, scope, type, null, ast);
        assert type != null;
        if (obj != null) {
            this.accept(obj, "#this");
        }
    }

    @Override
    public String getKind() {
        return "new";
    }

    @Override
    public String getData() {
        return this.getNodeType().getTypeName();
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

// DFModuleScope
class DFModuleScope extends DFVarScope {

    private Map<String, DFRef> _refs =
	new HashMap<String, DFRef>();
    private List<DFMethod> _methods =
	new ArrayList<DFMethod>();

    public DFModuleScope(DFVarScope parent, String path) {
	super(parent, "["+path+"]");
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
	    int dist = method.canAccept(id, argTypes);
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
                DFMethod method = klass.lookupMethod(name, null);
                _methods.add(method);
            } catch (MethodNotFound ee) {
            }
	}
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
    private void processExpression(
        DFContext ctx, DFTypeSpace typeSpace, DFGraph graph,
        DFTypeFinder finder, DFVarScope scope, DFFrame frame, Expression expr)
        throws UnsupportedSyntax, EntityNotFound {
        assert expr != null;

        try {
            if (expr instanceof Annotation) {

            } else if (expr instanceof Name) {
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
                        klass = finder.resolveKlass(obj.getNodeType());
                    } catch (EntityNotFound e) {
                        // Turned out it's a class variable.
                        klass = finder.lookupKlass(qname.getQualifier());
                    }
                    SimpleName fieldName = qname.getName();
                    DFRef ref = klass.lookupField(fieldName);
                    DFNode node = new FieldRefNode(graph, scope, ref, qname, obj);
                    node.accept(ctx.get(ref));
                    ctx.setRValue(node);
                }

            } else if (expr instanceof ThisExpression) {
                ThisExpression thisExpr = (ThisExpression)expr;
                Name name = thisExpr.getQualifier();
                DFRef ref;
                if (name != null) {
                    DFKlass klass = finder.lookupKlass(name);
                    ref = klass.getKlassScope().lookupThis();
                } else {
                    ref = scope.lookupThis();
                }
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
                                  graph, scope, DFBasicType.INT,
                                  expr, value));

            } else if (expr instanceof StringLiteral) {
                String value = ((StringLiteral)expr).getLiteralValue();
                ctx.setRValue(new ConstNode(
                                  graph, scope,
                                  DFBuiltinTypes.getStringKlass(),
                                  expr, Utils.quote(value)));

            } else if (expr instanceof TypeLiteral) {
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
                if (op == PrefixExpression.Operator.INCREMENT ||
                    op == PrefixExpression.Operator.DECREMENT) {
                    processAssignment(
                        ctx, typeSpace, graph, finder, scope, frame, operand);
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
                DFType type = DFType.inferInfixType(
                    lvalue.getNodeType(), op, rvalue.getNodeType());
                ctx.setRValue(new InfixNode(
                                  graph, scope, type, expr, op, lvalue, rvalue));

            } else if (expr instanceof ParenthesizedExpression) {
                ParenthesizedExpression paren = (ParenthesizedExpression)expr;
                processExpression(
                    ctx, typeSpace, graph, finder, scope, frame,
                    paren.getExpression());

            } else if (expr instanceof Assignment) {
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
                VariableDeclarationExpression decl = (VariableDeclarationExpression)expr;
                processVariableDeclaration(
                    ctx, typeSpace, graph, finder, scope, frame,
                    decl.fragments());

            } else if (expr instanceof MethodInvocation) {
                MethodInvocation invoke = (MethodInvocation)expr;
                Expression expr1 = invoke.getExpression();
                DFNode obj = null;
                DFKlass klass = null;
                if (expr1 == null) {
                    obj = ctx.get(scope.lookupThis());
                } else {
                    if (expr1 instanceof Name) {
                        try {
                            klass = finder.lookupKlass((Name)expr1);
                        } catch (TypeNotFound e) {
                        }
                    }
                    if (klass == null) {
                        processExpression(
                            ctx, typeSpace, graph, finder, scope, frame, expr1);
                        obj = ctx.getRValue();
                    }
                }
                if (obj != null) {
                    klass = finder.resolveKlass(obj.getNodeType());
                }
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
                    method = klass.lookupMethod(invoke.getName(), argTypes);
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
                            klass, null, id, DFCallStyle.InstanceMethod,
                            new DFMethodType(argTypes, null));
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
                ctx.setRValue(call);
                {
                    SortedSet<DFRef> refs = new TreeSet<DFRef>();
                    for (DFMethod method1 : methods) {
                        DFFrame frame1 = method1.getFrame();
                        if (frame1 == null) continue;
                        refs.addAll(frame1.getInputRefs());
                    }
                    for (DFRef ref : refs) {
                        if (ref.isLocal() || ref.isInternal()) continue;
                        ctx.set(new UpdateNode(
                                    graph, scope, ref, invoke, call));
                    }
                }
                if (call.exception != null) {
                    DFFrame dstFrame = frame.find(DFFrame.CATCHABLE);
                    frame.addExit(new DFExit(dstFrame, call.exception));
                }

            } else if (expr instanceof SuperMethodInvocation) {
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
                DFKlass klass = finder.resolveKlass(obj.getNodeType());
                DFKlass baseKlass = klass.getBaseKlass();
                assert baseKlass != null;
                DFMethod method;
                try {
                    method = baseKlass.lookupMethod(sinvoke.getName(), argTypes);
                } catch (MethodNotFound e) {
                    if (0 < _strict) {
                        e.setAst(expr);
                        throw e;
                    }
                    // fallback method.
                    String id = sinvoke.getName().getIdentifier();
                    DFMethod fallback = new DFMethod(
                        baseKlass, null, id, DFCallStyle.InstanceMethod,
                        new DFMethodType(argTypes, null));
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
                ctx.setRValue(call);
                if (frame1 != null) {
                    for (DFRef ref : frame1.getOutputRefs()) {
                        if (ref.isLocal() || ref.isInternal()) continue;
                        ctx.set(new UpdateNode(
                                    graph, scope, ref, sinvoke, call));
                    }
                }
                if (call.exception != null) {
                    DFFrame dstFrame = frame.find(DFFrame.CATCHABLE);
                    frame.addExit(new DFExit(dstFrame, call.exception));
                }

            } else if (expr instanceof ArrayCreation) {
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
                FieldAccess fa = (FieldAccess)expr;
                Expression expr1 = fa.getExpression();
                DFNode obj = null;
                DFKlass klass = null;
                if (expr1 instanceof Name) {
                    try {
                        klass = finder.lookupKlass((Name)expr1);
                    } catch (TypeNotFound e) {
                    }
                }
                if (klass == null) {
                    processExpression(
                        ctx, typeSpace, graph, finder, scope, frame, expr1);
                    obj = ctx.getRValue();
                    klass = finder.resolveKlass(obj.getNodeType());
                }
                SimpleName fieldName = fa.getName();
                DFRef ref = klass.lookupField(fieldName);
                DFNode node = new FieldRefNode(graph, scope, ref, fa, obj);
                node.accept(ctx.get(ref));
                ctx.setRValue(node);

            } else if (expr instanceof SuperFieldAccess) {
                SuperFieldAccess sfa = (SuperFieldAccess)expr;
                SimpleName fieldName = sfa.getName();
                DFNode obj = ctx.get(scope.lookupThis());
                DFKlass klass = finder.resolveKlass(obj.getNodeType()).getBaseKlass();
                DFRef ref = klass.lookupField(fieldName);
                DFNode node = new FieldRefNode(graph, scope, ref, sfa, obj);
                node.accept(ctx.get(ref));
                ctx.setRValue(node);

            } else if (expr instanceof CastExpression) {
                CastExpression cast = (CastExpression)expr;
                DFType type = finder.resolve(cast.getType());
                processExpression(
                    ctx, typeSpace, graph, finder, scope, frame,
		    cast.getExpression());
                DFNode node = new TypeCastNode(graph, scope, type, cast);
                node.accept(ctx.getRValue());
                ctx.setRValue(node);

            } else if (expr instanceof ClassInstanceCreation) {
                ClassInstanceCreation cstr = (ClassInstanceCreation)expr;
                AnonymousClassDeclaration anonDecl =
                    cstr.getAnonymousClassDeclaration();
                DFKlass instKlass;
                if (anonDecl != null) {
                    String id = Utils.encodeASTNode(anonDecl);
                    DFTypeSpace anonSpace = typeSpace.lookupSpace(id);
		    DFKlass anonKlass = anonSpace.getKlass(id);
		    processBodyDeclarations(
			anonKlass, anonDecl, anonDecl.bodyDeclarations());
		    instKlass = anonKlass;
                } else {
                    instKlass = finder.resolveKlass(cstr.getType());
                }
                Expression expr1 = cstr.getExpression();
                DFNode obj = null;
                if (expr1 != null) {
                    processExpression(
                        ctx, typeSpace, graph, finder, scope, frame, expr1);
                    obj = ctx.getRValue();
                }
                List<DFNode> argList = new ArrayList<DFNode>();
                for (Expression arg : (List<Expression>) cstr.arguments()) {
                    processExpression(
                        ctx, typeSpace, graph, finder, scope, frame, arg);
                    argList.add(ctx.getRValue());
                }
                DFNode[] args = new DFNode[argList.size()];
                argList.toArray(args);
                CreateObjectNode call = new CreateObjectNode(
                    graph, scope, instKlass, cstr, obj);
                call.setArgs(args);
                DFMethod method = instKlass.getConstructor();
                DFFrame frame1 = method.getFrame();
                if (frame1 != null) {
                    for (DFRef ref : frame1.getInputRefs()) {
                        if (ref.isLocal() || ref.isInternal()) continue;
                        call.accept(ctx.get(ref), ref.getFullName());
                    }
                }
                ctx.setRValue(call);
                if (frame1 != null) {
                    for (DFRef ref : frame1.getOutputRefs()) {
                        if (ref.isLocal() || ref.isInternal()) continue;
                        ctx.set(new UpdateNode(
                                    graph, scope, ref, cstr, call));
                    }
                }
                if (call.exception != null) {
                    DFFrame dstFrame = frame.find(DFFrame.CATCHABLE);
                    frame.addExit(new DFExit(dstFrame, call.exception));
                }

            } else if (expr instanceof ConditionalExpression) {
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
                JoinNode join = new JoinNode(graph, scope, null, expr, condValue);
                join.recv(true, trueValue);
                join.recv(false, falseValue);
                ctx.setRValue(join);

            } else if (expr instanceof InstanceofExpression) {
                InstanceofExpression instof = (InstanceofExpression)expr;
                DFType type = finder.resolve(instof.getRightOperand());
                processExpression(
                    ctx, typeSpace, graph, finder, scope, frame,
                    instof.getLeftOperand());
                DFNode node = new InstanceofNode(graph, scope, instof, type);
                node.accept(ctx.getRValue());
                ctx.setRValue(node);

            } else if (expr instanceof LambdaExpression) {
                LambdaExpression lambda = (LambdaExpression)expr;
                String id = "lambda";
                ASTNode body = lambda.getBody();
                DFKlass klass = finder.resolveKlass(
                    scope.lookupThis().getRefType());
                DFTypeSpace anonSpace = new DFTypeSpace(null, id);
                DFKlass anonKlass = new DFKlass(
                    id, anonSpace, klass, scope,
                    DFBuiltinTypes.getObjectKlass());
                assert body != null;
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
                DFKlass klass = finder.resolveKlass(
                    scope.lookupThis().getRefType());
                DFTypeSpace anonSpace = new DFTypeSpace(null, "MethodRef");
                DFKlass anonKlass = new DFKlass(
                    "methodref", anonSpace, klass, scope,
                    DFBuiltinTypes.getObjectKlass());
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
            Name name = (Name)expr;
            if (name.isSimpleName()) {
                DFRef ref = scope.lookupVar((SimpleName)name);
                ctx.setLValue(new SingleAssignNode(graph, scope, ref, expr));
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
                    klass = finder.resolveKlass(obj.getNodeType());
                } catch (EntityNotFound e) {
                    // Turned out it's a class variable.
                    klass = finder.lookupKlass(qname.getQualifier());
                }
                SimpleName fieldName = qname.getName();
                DFRef ref = klass.lookupField(fieldName);
                ctx.setLValue(new FieldAssignNode(graph, scope, ref, expr, obj));
            }

        } else if (expr instanceof ArrayAccess) {
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
            FieldAccess fa = (FieldAccess)expr;
            Expression expr1 = fa.getExpression();
            processExpression(
                ctx, typeSpace, graph, finder, scope, frame, expr1);
            DFNode obj = ctx.getRValue();
            DFKlass klass = finder.resolveKlass(obj.getNodeType());
            SimpleName fieldName = fa.getName();
            DFRef ref = klass.lookupField(fieldName);
            ctx.setLValue(new FieldAssignNode(graph, scope, ref, expr, obj));

        } else if (expr instanceof SuperFieldAccess) {
            SuperFieldAccess sfa = (SuperFieldAccess)expr;
            SimpleName fieldName = sfa.getName();
            DFNode obj = ctx.get(scope.lookupThis());
            DFKlass klass = finder.resolveKlass(obj.getNodeType()).getBaseKlass();
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
        DFVarScope childScope = scope.getChildByAST(block);
        for (Statement cstmt : (List<Statement>) block.statements()) {
            processStatement(
                ctx, typeSpace, graph, finder, childScope, frame, cstmt);
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
            if (thenCtx == null || elseCtx == null) {
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
            thenFrame.close(ctx);
        }
        if (elseFrame != null) {
            for (DFExit exit : elseFrame.getExits()) {
                DFNode node = exit.getNode();
                JoinNode join = new JoinNode(
                    graph, scope, node.getRef(), null, condValue);
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
                JoinNode join = new JoinNode(graph, scope, ref, apt, caseNode);
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
            enumKlass = finder.resolveKlass(type);
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
            // XXX Ignore asserts.

        } else if (stmt instanceof Block) {
            processBlock(
                ctx, typeSpace, graph, finder, scope, frame,
		(Block)stmt);

        } else if (stmt instanceof EmptyStatement) {

        } else if (stmt instanceof VariableDeclarationStatement) {
            processVariableDeclarationStatement(
                ctx, typeSpace, graph, finder, scope, frame,
                (VariableDeclarationStatement)stmt);

        } else if (stmt instanceof ExpressionStatement) {
            processExpressionStatement(
                ctx, typeSpace, graph, finder, scope, frame,
                (ExpressionStatement)stmt);

        } else if (stmt instanceof IfStatement) {
            processIfStatement(
                ctx, typeSpace, graph, finder, scope, frame,
                (IfStatement)stmt);

        } else if (stmt instanceof SwitchStatement) {
            processSwitchStatement(
                ctx, typeSpace, graph, finder, scope, frame,
                (SwitchStatement)stmt);

        } else if (stmt instanceof SwitchCase) {
            // Invalid "case" placement.
            throw new UnsupportedSyntax(stmt);

        } else if (stmt instanceof WhileStatement) {
            processWhileStatement(
                ctx, typeSpace, graph, finder, scope, frame,
                (WhileStatement)stmt);

        } else if (stmt instanceof DoStatement) {
            processDoStatement(
                ctx, typeSpace, graph, finder, scope, frame,
                (DoStatement)stmt);

        } else if (stmt instanceof ForStatement) {
            processForStatement(
                ctx, typeSpace, graph, finder, scope, frame,
                (ForStatement)stmt);

        } else if (stmt instanceof EnhancedForStatement) {
            processEnhancedForStatement(
                ctx, typeSpace, graph, finder, scope, frame,
                (EnhancedForStatement)stmt);

        } else if (stmt instanceof ReturnStatement) {
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
            BreakStatement breakStmt = (BreakStatement)stmt;
            SimpleName labelName = breakStmt.getLabel();
            String dstLabel = (labelName != null)?
                labelName.getIdentifier() : DFFrame.BREAKABLE;
            DFFrame dstFrame = frame.find(dstLabel);
            for (DFRef ref : dstFrame.getOutputRefs()) {
                frame.addExit(new DFExit(dstFrame, ctx.get(ref)));
            }

        } else if (stmt instanceof ContinueStatement) {
            ContinueStatement contStmt = (ContinueStatement)stmt;
            SimpleName labelName = contStmt.getLabel();
            String dstLabel = (labelName != null)?
                labelName.getIdentifier() : DFFrame.BREAKABLE;
            DFFrame dstFrame = frame.find(dstLabel);
            for (DFRef ref : dstFrame.getOutputRefs()) {
                frame.addExit(new DFExit(dstFrame, ctx.get(ref), true));
            }

        } else if (stmt instanceof LabeledStatement) {
            LabeledStatement labeledStmt = (LabeledStatement)stmt;
            DFFrame labeledFrame = frame.getChildByAST(labeledStmt);
            processStatement(
                ctx, typeSpace, graph, finder, scope, labeledFrame,
                labeledStmt.getBody());
            labeledFrame.close(ctx);

        } else if (stmt instanceof SynchronizedStatement) {
            SynchronizedStatement syncStmt = (SynchronizedStatement)stmt;
            processExpression(
                ctx, typeSpace, graph, finder, scope, frame,
                syncStmt.getExpression());
            processStatement(
                ctx, typeSpace, graph, finder, scope, frame,
                syncStmt.getBody());

        } else if (stmt instanceof TryStatement) {
            processTryStatement(
                ctx, typeSpace, graph, finder, scope, frame,
                (TryStatement)stmt);

        } else if (stmt instanceof ThrowStatement) {
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
            // XXX Use MethodCallNode.
            ConstructorInvocation ci = (ConstructorInvocation)stmt;
            for (Expression arg : (List<Expression>) ci.arguments()) {
                processExpression(
                    ctx, typeSpace, graph, finder, scope, frame, arg);
            }

        } else if (stmt instanceof SuperConstructorInvocation) {
            // XXX Use MethodCallNode.
            SuperConstructorInvocation sci = (SuperConstructorInvocation)stmt;
            for (Expression arg : (List<Expression>) sci.arguments()) {
                processExpression(
                    ctx, typeSpace, graph, finder, scope, frame, arg);
            }

        } else if (stmt instanceof TypeDeclarationStatement) {
            TypeDeclarationStatement typeDeclStmt = (TypeDeclarationStatement)stmt;
	    AbstractTypeDeclaration abstTypeDecl = typeDeclStmt.getDeclaration();
	    if (abstTypeDecl instanceof TypeDeclaration) {
		TypeDeclaration typeDecl = (TypeDeclaration)abstTypeDecl;
		DFKlass klass = typeSpace.getKlass(typeDecl.getName());
		processBodyDeclarations(
		    klass, typeDecl, typeDecl.bodyDeclarations());
	    } else if (abstTypeDecl instanceof EnumDeclaration) {
		EnumDeclaration enumDecl = (EnumDeclaration)abstTypeDecl;
		DFKlass klass = typeSpace.getKlass(enumDecl.getName());
		processBodyDeclarations(
		    klass, enumDecl, enumDecl.bodyDeclarations());
	    }

        } else {
            throw new UnsupportedSyntax(stmt);
        }
    }

    private DFTypeFinder prepareTypeFinder(
        DFTypeSpace packageSpace, List<ImportDeclaration> imports) {
        DFTypeFinder finder = new DFTypeFinder(_rootSpace);
        finder = new DFTypeFinder(finder, _rootSpace.lookupSpace("java.lang"));
        finder = new DFTypeFinder(finder, packageSpace);
        DFTypeSpace importSpace = new DFTypeSpace(null, "Import");
        int n = 0;
        for (ImportDeclaration importDecl : imports) {
            Name name = importDecl.getName();
	    if (importDecl.isOnDemand()) {
		Logger.debug("Import:", name+".*");
		finder = new DFTypeFinder(finder, _rootSpace.lookupSpace(name));
	    } else {
		assert name.isQualifiedName();
		try {
		    DFKlass klass = _rootSpace.getKlass(name);
		    Logger.debug("Import:", name);
		    importSpace.addKlass(klass);
		    n++;
		} catch (TypeNotFound e) {
		    if (!importDecl.isStatic()) {
			Logger.error("Import: Class not found:", e.name);
		    }
		}
	    }
        }
        if (0 < n) {
            finder = new DFTypeFinder(finder, importSpace);
        }
        return finder;
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
        DFTypeSpace methodSpace = method.getChildSpace();
        DFGraph graph = new DFGraph(scope, method, ast);
        DFContext ctx = new DFContext(graph, scope);
        DFTypeFinder finder = method.getFinder();
        if (parameters != null) {
            int i = 0;
            for (SingleVariableDeclaration decl : parameters) {
                // XXX Ignore modifiers and dimensions.
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

        try {
            // Process the function body.
            processStatement(
                ctx, methodSpace, graph, finder, scope, frame, body);
            frame.close(ctx);
            //frame.dump();
        } catch (MethodNotFound e) {
            e.setMethod(method);
            Logger.error("MethodNotFound:", e.name+"("+Utils.join(", ", e.argTypes)+")");
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
    private void processBodyDeclarations(
        DFKlass klass, ASTNode ast, List<BodyDeclaration> decls)
        throws UnsupportedSyntax, EntityNotFound {
        // lookup base/child klasses.
        DFVarScope klassScope = klass.getKlassScope();
        DFFrame klassFrame = new DFFrame(DFFrame.ANONYMOUS);
	DFTypeSpace klassSpace = klass.getKlassSpace();
        DFGraph klassGraph = new DFGraph(klassScope, null, ast);
        DFContext klassCtx = new DFContext(klassGraph, klassScope);
        DFTypeFinder finder = klass.getFinder();
        for (BodyDeclaration body : decls) {
            if (body instanceof AbstractTypeDeclaration) {
                AbstractTypeDeclaration abstTypeDecl = (AbstractTypeDeclaration)body;
                DFKlass childKlass = klassSpace.getKlass(abstTypeDecl.getName());
                processBodyDeclarations(
                    childKlass, abstTypeDecl,
                    abstTypeDecl.bodyDeclarations());

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
                MethodDeclaration methodDecl = (MethodDeclaration)body;

            } else if (body instanceof EnumConstantDeclaration) {
                EnumConstantDeclaration econst = (EnumConstantDeclaration)body;
                // XXX ignore AnonymousClassDeclaration
                // XXX ignore Arguments

            } else if (body instanceof AnnotationTypeMemberDeclaration) {
                AnnotationTypeMemberDeclaration annot =
                    (AnnotationTypeMemberDeclaration)body;
                // XXX ignore annotations.

            } else if (body instanceof Initializer) {

            } else {
                throw new UnsupportedSyntax(body);
            }
        }
        exportGraph(klassGraph);
    }

    /// Top-level functions.

    private DFRootTypeSpace _rootSpace;
    private int _strict;
    private List<Exporter> _exporters =
        new ArrayList<Exporter>();
    private DFGlobalVarScope _globalScope =
        new DFGlobalVarScope();
    private Map<String, DFModuleScope> _moduleScope =
        new HashMap<String, DFModuleScope>();
    private Map<String, DFKlass[]> _klassList =
        new HashMap<String, DFKlass[]>();

    public Java2DF(
        DFRootTypeSpace rootSpace, int strict)
        throws IOException, TypeNotFound {
        _rootSpace = rootSpace;
        _strict = strict;
        DFBuiltinTypes.initialize(rootSpace);
    }

    public void addExporter(Exporter exporter) {
        _exporters.add(exporter);
    }

    public void removeExporter(Exporter exporter) {
        _exporters.remove(exporter);
    }

    protected void exportGraph(DFGraph graph) {
        graph.cleanup();
        for (Exporter exporter : _exporters) {
            exporter.writeGraph(graph);
        }
    }

    // Pass1: populate TypeSpaces.
    public void buildTypeSpace(String key, CompilationUnit cunit) {
        DFTypeSpace packageSpace = _rootSpace.lookupSpace(cunit.getPackage());
        DFModuleScope module = new DFModuleScope(_globalScope, key);
        _moduleScope.put(key, module);
        try {
            DFKlass[] klasses = packageSpace.buildModuleSpace(cunit, module);
            for (DFKlass klass : klasses) {
                Logger.debug("Pass1: created:", klass);
            }
            _klassList.put(key, klasses);
        } catch (UnsupportedSyntax e) {
            Logger.error("Pass1: Unsupported at", key, e.name, "("+e.getAstName()+")");
        }
    }

    // Pass2: set references to external Klasses.
    @SuppressWarnings("unchecked")
    public void setTypeFinder(String key, CompilationUnit cunit) {
        DFTypeSpace packageSpace = _rootSpace.lookupSpace(cunit.getPackage());
        DFTypeFinder finder = prepareTypeFinder(packageSpace, cunit.imports());
        DFKlass[] klasses = _klassList.get(key);
        for (DFKlass klass : klasses) {
            klass.setFinder(finder);
        }
    }

    // Pass3: load class definitions and define parameterized Klasses.
    @SuppressWarnings("unchecked")
    public void loadKlasses(String key, CompilationUnit cunit)
        throws TypeNotFound {
        // Process static imports.
        DFModuleScope module = _moduleScope.get(key);
        for (ImportDeclaration importDecl :
                 (List<ImportDeclaration>) cunit.imports()) {
            if (!importDecl.isStatic()) continue;
            Name name = importDecl.getName();
            DFKlass klass;
            try {
                if (importDecl.isOnDemand()) {
                    klass = _rootSpace.getKlass(name);
                    klass.load();
                    module.importStatic(klass);
                } else {
                    QualifiedName qname = (QualifiedName)name;
                    klass = _rootSpace.getKlass(qname.getQualifier());
                    klass.load();
                    module.importStatic(klass, qname.getName());
                }
            } catch (TypeNotFound e) {
                Logger.error("Pass3: TypeNotFound at", key, "("+e.name+")");
                if (0 < _strict) throw e;
            }
        }
        DFKlass[] klasses = _klassList.get(key);
        for (DFKlass klass : klasses) {
            try {
                klass.load();
            } catch (TypeNotFound e) {
                Logger.error("Pass3: TypeNotFound at", key, "("+e.name+")");
                if (0 < _strict) throw e;
            }
        }
    }

    // Pass3.5: list all methods.
    public void listMethods(String key) {
        // Extend the klass list.
        List<DFKlass> klasses = new ArrayList<DFKlass>();
	for (DFKlass klass : _klassList.get(key)) {
            if (klass.isParameterized()) {
                klasses.addAll(Arrays.asList(klass.getParamKlasses()));
            } else {
                klasses.add(klass);
            }
        }
	for (DFKlass klass : klasses) {
            klass.addOverrides();
        }
        DFKlass[] a = new DFKlass[klasses.size()];
        klasses.toArray(a);
        _klassList.put(key, a);
    }

    // Pass4: build all methods.
    public void buildMethods(String key)
        throws EntityNotFound {
        DFKlass[] klasses = _klassList.get(key);
        Queue<DFMethod> queue = new ArrayDeque<DFMethod>();
	for (DFKlass klass : klasses) {
	    for (DFMethod method : klass.getMethods()) {
		try {
		    method.buildScopeAndFrame();
                    queue.add(method);
		} catch (UnsupportedSyntax e) {
                    Logger.error("Pass4: Unsupported at", key, e.name, "("+e.getAstName()+")");
                } catch (EntityNotFound e) {
                    Logger.error("Pass4: EntityNotFound at", key, "("+e.name+")");
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
    public void buildGraphs(String key, CompilationUnit cunit)
        throws EntityNotFound {
        DFTypeSpace packageSpace = _rootSpace.lookupSpace(cunit.getPackage());
        DFTypeFinder finder = prepareTypeFinder(packageSpace, cunit.imports());
	for (AbstractTypeDeclaration abstTypeDecl :
		 (List<AbstractTypeDeclaration>) cunit.types()) {
            DFKlass klass = packageSpace.getKlass(abstTypeDecl.getName());
            try {
                processBodyDeclarations(
                    klass, abstTypeDecl,
                    abstTypeDecl.bodyDeclarations());
            } catch (UnsupportedSyntax e) {
                Logger.error("Pass5: Unsupported at", key, e.name, "("+e.getAstName()+")");
            } catch (EntityNotFound e) {
                Logger.error("Pass5: EntityNotFound at", key, "("+e.name+")");
                if (0 < _strict) throw e;
            }
        }
        DFKlass[] klasses = _klassList.get(key);
        for (DFKlass klass : klasses) {
            for (DFMethod method : klass.getMethods()) {
                if (method.getFrame() == null) continue;
                ASTNode ast = method.getTree();
                try {
                    if (ast instanceof MethodDeclaration) {
                        MethodDeclaration methodDecl = (MethodDeclaration)ast;
                        if (methodDecl.getBody() != null) {
                            DFGraph graph = processMethod(
                                method, methodDecl,
                                methodDecl.getBody(), methodDecl.parameters());
                            exportGraph(graph);
                        }
                    } else if (ast instanceof Initializer) {
                        Initializer initializer = (Initializer)ast;
                        DFGraph graph = processMethod(
                            klass.getInitializer(), initializer,
                            initializer.getBody(), null);
                        exportGraph(graph);
                    }
                } catch (UnsupportedSyntax e) {
                    Logger.error("Pass5: Unsupported at", key, e.name, "("+e.getAstName()+")");
                } catch (EntityNotFound e) {
                    Logger.error("Pass5: EntityNotFound at", key, "("+e.name+")");
                    if (0 < _strict) throw e;
                }
            }
        }
    }

    /**
     * Provides a command line interface.
     *
     * Usage: java Java2DF [-o output] input.java ...
     */
    public static void main(String[] args)
        throws IOException {

        // Parse the options.
        List<String> files = new ArrayList<String>();
        Set<String> processed = null;
        OutputStream output = System.out;
        String sep = System.getProperty("path.separator");
        int strict = 0;
        Logger.LogLevel = 0;

        DFRootTypeSpace rootSpace = new DFRootTypeSpace();
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
                System.err.println("usage: Java2DF [-v] [-S] [-i input] [-o output] [-C jar] [-p path] [path ...]");
                System.exit(1);
                return;
            } else {
                files.add(arg);
            }
        }

        // Process files.
        Java2DF converter;
        try {
            converter = new Java2DF(rootSpace, strict);
        } catch (TypeNotFound e) {
            Logger.error("Class not found:", e.name);
            System.err.println("Fatal error at initialization.");
            System.exit(1);
            return;
        }
        XmlExporter exporter = new XmlExporter();
        converter.addExporter(exporter);
        Map<String, CompilationUnit> srcs =
            new HashMap<String, CompilationUnit>();
        for (String path : files) {
            Logger.info("Pass1:", path);
            try {
                CompilationUnit cunit = Utils.parseFile(path);
                srcs.put(path, cunit);
                converter.buildTypeSpace(path, cunit);
            } catch (IOException e) {
                System.err.println("Cannot open input file: "+path);
            }
        }
        for (String path : files) {
            Logger.info("Pass2:", path);
            CompilationUnit cunit = srcs.get(path);
            converter.setTypeFinder(path, cunit);
        }
        try {
            for (String path : files) {
                Logger.info("Pass3:", path);
                CompilationUnit cunit = srcs.get(path);
                converter.loadKlasses(path, cunit);
            }
            for (String path : files) {
                converter.listMethods(path);
            }
            for (String path : files) {
                Logger.info("Pass4:", path);
                converter.buildMethods(path);
            }
            for (String path : files) {
                if (processed != null && !processed.contains(path)) continue;
                Logger.info("Pass5:", path);
                CompilationUnit cunit = srcs.get(path);
                exporter.startFile(path);
                converter.buildGraphs(path, cunit);
                exporter.endFile();
            }
        } catch (EntityNotFound e) {
            e.printStackTrace();
            Logger.error("EntityNotFound: method="+e.method+", ast="+e.ast);
        }
        exporter.close();

        Utils.printXml(output, exporter.document);
        output.close();
    }
}
