//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
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
}

// VarAssignNode:
class VarAssignNode extends SingleAssignNode {

    public VarAssignNode(
        DFGraph graph, DFVarScope scope, DFRef ref,
        ASTNode ast) {
        super(graph, scope, ref, ast);
    }

    @Override
    public String getKind() {
        return "assign_var";
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
        return "assign_array";
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
        return "assign_field";
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
        return "ref_var";
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
        return "ref_array";
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
        return "ref_field";
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
        return "op_prefix";
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
        return "op_postfix";
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
        return "op_infix";
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
        return "op_typecast";
    }

    @Override
    public String getData() {
        return this.getNodeType().getTypeName();
    }
}

// TypeCheckNode
class TypeCheckNode extends ProgNode {

    public DFType type;

    public TypeCheckNode(
        DFGraph graph, DFVarScope scope,
        ASTNode ast, DFType type) {
        super(graph, scope, DFBasicType.BOOLEAN, null, ast);
        assert type != null;
        this.type = type;
    }

    @Override
    public String getKind() {
        return "op_typecheck";
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
        return "op_assign";
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
        return "value";
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

    public void merge(DFNode node) {
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

    public void setRepeat(LoopRepeatNode repeat) {
        this.accept(repeat, "_repeat");
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

    public void setEnd(DFNode end) {
        this.accept(end, "_end");
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
        return "op_iter";
    }
}

// CallNode
abstract class CallNode extends ProgNode {

    public DFFunctionType funcType;
    public DFNode[] args;
    public DFNode exception;

    public CallNode(
        DFGraph graph, DFVarScope scope, DFType type, DFRef ref,
        ASTNode ast, DFFunctionType funcType) {
        super(graph, scope, type, ref, ast);
        this.funcType = funcType;
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
        super(graph, scope, methods[0].getFuncType().getReturnType(), null,
              ast, methods[0].getFuncType());
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
        super(graph, scope, type, null,
              ast, constructor.getFuncType());
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


//  DFMethod
//
public class DFMethod extends DFTypeSpace implements DFGraph, Comparable<DFMethod> {

    private DFKlass _klass;
    private String _name;
    private DFCallStyle _callStyle;
    private DFMethodScope _scope;
    private DFFrame _frame;

    private DFTypeFinder _finder = null;
    private DFMapType[] _mapTypes = null;
    private Map<String, DFType> _mapTypeMap = null;
    private DFFunctionType _funcType = null;

    private ConsistentHashSet<DFMethod> _callers =
        new ConsistentHashSet<DFMethod>();

    private ASTNode _ast = null;

    // List of subclass' methods overriding this method.
    private List<DFMethod> _overrides = new ArrayList<DFMethod>();

    public DFMethod(
        DFKlass klass, String id, DFCallStyle callStyle,
        String name, DFVarScope outer) {
        super(id, klass);
        _klass = klass;
        _name = name;
        _callStyle = callStyle;
        _scope = new DFMethodScope(outer, id);
        _frame = new DFFrame(DFFrame.RETURNABLE);
    }

    public void setFinder(DFTypeFinder finder) {
        _finder = new DFTypeFinder(this, finder);
    }

    public void setMapTypes(DFMapType[] mapTypes)
	throws InvalidSyntax {
	assert _finder != null;
        _mapTypes = mapTypes;
        _mapTypeMap = new HashMap<String, DFType>();
        for (DFMapType mapType : _mapTypes) {
	    mapType.build(_finder);
            _mapTypeMap.put(mapType.getTypeName(), mapType.toKlass());
        }
    }

    public void setFuncType(DFFunctionType funcType) {
        _funcType = funcType;
    }

    @Override
    public String toString() {
        return ("<DFMethod("+this.getSpaceName()+") "+this.getSignature()+">");
    }

    @Override
    public int compareTo(DFMethod method) {
        if (this == method) return 0;
        return _name.compareTo(method._name);
    }

    public boolean equals(DFMethod method) {
        if (!_name.equals(method._name)) return false;
        return _funcType.equals(method._funcType);
    }

    public String getName() {
        return _name;
    }

    public String getSignature() {
        String name;
        if (_klass != null) {
            name = _klass.getTypeName()+"."+_name;
        } else {
            name = "!"+_name;
        }
        if (_funcType != null) {
            name += _funcType.getTypeName();
        }
        return name;
    }

    public DFCallStyle getCallStyle() {
        return _callStyle;
    }

    public DFFunctionType getFuncType() {
        return _funcType;
    }

    public DFType getType(String id) {
        if (_mapTypeMap != null) {
            DFType type = _mapTypeMap.get(id);
            if (type != null) return type;
        }
        return super.getType(id);
    }

    public int canAccept(DFType[] argTypes) {
        Map<DFMapType, DFType> typeMap = new HashMap<DFMapType, DFType>();
        return _funcType.canAccept(argTypes, typeMap);
    }

    public void addOverride(DFMethod method) {
	//Logger.info("DFMethod.addOverride:", this, "<-", method);
        _overrides.add(method);
    }

    private void listOverrides(List<DFOverride> overrides, int prio) {
        overrides.add(new DFOverride(this, prio));
        for (DFMethod method : _overrides) {
            method.listOverrides(overrides, prio+1);
        }
    }

    public DFMethod[] getOverrides() {
        List<DFOverride> overrides = new ArrayList<DFOverride>();
        this.listOverrides(overrides, 0);
        DFOverride[] a = new DFOverride[overrides.size()];
        overrides.toArray(a);
        Arrays.sort(a);
        DFMethod[] methods = new DFMethod[a.length];
	for (int i = 0; i < a.length; i++) {
	    methods[i] = a[i].method;
	}
        return methods;
    }

    public void addCaller(DFMethod method) {
        _callers.add(method);
    }

    public ConsistentHashSet<DFMethod> getCallers() {
        return _callers;
    }

    public DFTypeFinder getFinder() {
        return _finder;
    }

    public void setTree(ASTNode ast) {
	_ast = ast;
    }

    public DFLocalScope getScope() {
        return _scope;
    }

    public DFFrame getFrame() {
        return _frame;
    }

    @SuppressWarnings("unchecked")
    public void buildScope()
        throws InvalidSyntax {
	if (_ast == null) return;
	assert _scope != null;
	if (_ast instanceof MethodDeclaration) {
	    _scope.buildMethodDecl(_finder, (MethodDeclaration)_ast);
	} else if (_ast instanceof AbstractTypeDeclaration) {
	    _scope.buildBodyDecls(_finder, ((AbstractTypeDeclaration)_ast).bodyDeclarations());
        } else if (_ast instanceof AnonymousClassDeclaration) {
            _scope.buildBodyDecls(_finder, ((AnonymousClassDeclaration)_ast).bodyDeclarations());
	}  else {
	    throw new InvalidSyntax(_ast);
	}
        //_scope.dump();
    }

    @SuppressWarnings("unchecked")
    public void buildFrame()
        throws InvalidSyntax {
	if (_ast == null) return;
	assert _scope != null;
        assert _frame != null;
        if (_ast instanceof MethodDeclaration) {
            _frame.buildMethodDecl(
                _finder, this, _scope, (MethodDeclaration)_ast);
	} else if (_ast instanceof AbstractTypeDeclaration) {
            _frame.buildBodyDecls(
                _finder, this, _scope, ((AbstractTypeDeclaration)_ast).bodyDeclarations());
        } else if (_ast instanceof AnonymousClassDeclaration) {
            _frame.buildBodyDecls(
                _finder, this, _scope, ((AnonymousClassDeclaration)_ast).bodyDeclarations());
        }  else {
            throw new InvalidSyntax(_ast);
        }
    }

    // DFOverride
    private class DFOverride implements Comparable<DFOverride> {

	public DFMethod method;
	public int level;

	public DFOverride(DFMethod method, int level) {
	    this.method = method;
	    this.level = level;
	}

	@Override
	public String toString() {
	    return ("<DFOverride: "+this.method+" ("+this.level+")>");
	}

	@Override
	public int compareTo(DFOverride override) {
	    if (this.level != override.level) {
		return override.level - this.level;
	    } else {
		return this.method.compareTo(override.method);
	    }
	}
    }

    /// General graph operations.

    /**
     * Creates a value node.
     */
    @SuppressWarnings("unchecked")
    private void processExpression(
        DFContext ctx, DFTypeSpace typeSpace, DFGraph graph,
        DFTypeFinder finder, DFLocalScope scope, DFFrame frame, Expression expr)
        throws InvalidSyntax, EntityNotFound {
        assert expr != null;

        try {
            if (expr instanceof Annotation) {
		// "@Annotation"

            } else if (expr instanceof Name) {
		// "a.b"
                Name name = (Name)expr;
                if (name.isSimpleName()) {
                    DFRef ref = scope.lookupVar((SimpleName)name);
                    DFNode node;
                    if (ref.isLocal()) {
                        node = new VarRefNode(graph, scope, ref, expr);
                    } else {
                        DFNode obj = ctx.get(scope.lookupThis());
                        node = new FieldRefNode(graph, scope, ref, expr, obj);
                    }
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
                    DFType type = DFNode.inferPrefixType(
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
                DFType type = DFNode.inferInfixType(
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
                        // fallback method.
                        String id = invoke.getName().getIdentifier();
                        DFMethod fallback = new DFMethod(
                            klass, id, DFCallStyle.InstanceMethod, id, null);
                        fallback.setFuncType(
                            new DFFunctionType(argTypes, DFUnknownType.UNKNOWN));
                        Logger.error(
                            "DFMethod.processExpression: MethodNotFound",
                            this, klass, expr);
                        Logger.info("Fallback method:", klass, ":", fallback);
                        method = fallback;
                    }
                }
                DFMethod methods[] = method.getOverrides();
                MethodCallNode call = new MethodCallNode(
                    graph, scope, methods, invoke, obj);
                call.setArgs(args);
                {
                    ConsistentHashSet<DFRef> refs = new ConsistentHashSet<DFRef>();
                    for (DFMethod method1 : methods) {
                        DFFrame frame1 = method1.getFrame();
                        if (frame1 == null) continue;
                        refs.addAll(frame1.getInputRefs());
                    }
                    for (DFRef ref : refs) {
                        if (ref.isLocal()) continue;
                        call.accept(ctx.get(ref), ref.getFullName());
                    }
                }
                ctx.setRValue(new ReceiveNode(
                                  graph, scope, null, invoke, call, "#return"));
                {
                    ConsistentHashSet<DFRef> refs = new ConsistentHashSet<DFRef>();
                    for (DFMethod method1 : methods) {
                        DFFrame frame1 = method1.getFrame();
                        if (frame1 == null) continue;
                        refs.addAll(frame1.getOutputRefs());
                    }
                    for (DFRef ref : refs) {
                        if (ref.isLocal()) continue;
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
                    // fallback method.
                    String id = sinvoke.getName().getIdentifier();
                    DFMethod fallback = new DFMethod(
                        baseKlass, id, DFCallStyle.InstanceMethod, id, null);
                    fallback.setFuncType(
                        new DFFunctionType(argTypes, DFUnknownType.UNKNOWN));
                    Logger.error(
                        "DFMethod.processExpression: MethodNotFound",
                        this, baseKlass, expr);
                    Logger.info("Fallback method:", baseKlass, ":", fallback);
                    method = fallback;
                }
                DFMethod methods[] = new DFMethod[] { method };
                MethodCallNode call = new MethodCallNode(
                    graph, scope, methods, sinvoke, obj);
                call.setArgs(args);
                DFFrame frame1 = method.getFrame();
                if (frame1 != null) {
                    for (DFRef ref : frame1.getInputRefs()) {
                        if (ref.isLocal()) continue;
                        call.accept(ctx.get(ref), ref.getFullName());
                    }
                }
                ctx.setRValue(new ReceiveNode(
                                  graph, scope, null, sinvoke, call, "#return"));
                if (frame1 != null) {
                    for (DFRef ref : frame1.getOutputRefs()) {
                        if (ref.isLocal()) continue;
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
                node.accept(ctx.get(ref));
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
		    DFType anonType = typeSpace.getType(id);
                    if (anonType == null) {
                        throw new TypeNotFound(id);
                    }
		    instKlass = anonType.toKlass();
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
                        if (ref.isLocal()) continue;
                        call.accept(ctx.get(ref), ref.getFullName());
                    }
                }
                ctx.setRValue(new ReceiveNode(
                                  graph, scope, null, cstr, call, "#return"));
                if (frame1 != null) {
                    for (DFRef ref : frame1.getOutputRefs()) {
                        if (ref.isLocal()) continue;
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
                DFNode node = new TypeCheckNode(graph, scope, instof, type);
                node.accept(ctx.getRValue());
                ctx.setRValue(node);

            } else if (expr instanceof LambdaExpression) {
		// "x -> { ... }"
                LambdaExpression lambda = (LambdaExpression)expr;
                ASTNode body = lambda.getBody();
                if (body instanceof Statement) {
                } else if (body instanceof Expression) {
                } else {
                    throw new InvalidSyntax(body);
                }
                ctx.setRValue(new DFNode(graph, scope,  DFUnknownType.UNKNOWN, null));
		// XXX TODO LambdaExpression
		Logger.error("Unsupported: LambdaExpression", expr);

            } else if (expr instanceof MethodReference) {
                //  CreationReference
                //  ExpressionMethodReference
                //  SuperMethodReference
                //  TypeMethodReference
                MethodReference mref = (MethodReference)expr;
                DFKlass klass = scope.lookupThis().getRefType().toKlass();
                ctx.setRValue(new DFNode(graph, scope,  DFUnknownType.UNKNOWN, null));
                // XXX TODO MethodReference
		Logger.error("Unsupported: MethodReference", expr);

            } else {
                throw new InvalidSyntax(expr);
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
        DFTypeFinder finder, DFLocalScope scope, DFFrame frame,
        Expression expr)
        throws InvalidSyntax, EntityNotFound {
        assert expr != null;

        if (expr instanceof Name) {
	    // "a.b"
            Name name = (Name)expr;
            if (name.isSimpleName()) {
                DFRef ref = scope.lookupVar((SimpleName)name);
                DFNode node;
                if (ref.isLocal()) {
                    node = new VarAssignNode(graph, scope, ref, expr);
                } else {
                    DFNode obj = ctx.get(scope.lookupThis());
                    node = new FieldAssignNode(graph, scope, ref, expr, obj);
                }
                ctx.setLValue(node);
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

	} else if (expr instanceof ParenthesizedExpression) {
	    ParenthesizedExpression paren = (ParenthesizedExpression)expr;
	    processAssignment(
		ctx, typeSpace, graph, finder, scope, frame,
		paren.getExpression());

        } else {
            throw new InvalidSyntax(expr);
        }
    }

    /**
     * Creates a new variable node.
     */
    private void processVariableDeclaration(
        DFContext ctx, DFTypeSpace typeSpace, DFGraph graph,
        DFTypeFinder finder, DFLocalScope scope, DFFrame frame,
	List<VariableDeclarationFragment> frags)
        throws InvalidSyntax, EntityNotFound {

        for (VariableDeclarationFragment frag : frags) {
            DFRef ref = scope.lookupVar(frag.getName());
            Expression init = frag.getInitializer();
            if (init != null) {
                processExpression(
                    ctx, typeSpace, graph, finder, scope, frame, init);
                DFNode value = ctx.getRValue();
                if (value != null) {
                    DFNode assign = new VarAssignNode(graph, scope, ref, frag);
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
        DFContext ctx, DFGraph graph, DFLocalScope scope,
        DFFrame frame, ASTNode ast, DFNode condValue,
        DFFrame loopFrame, DFContext loopCtx, boolean preTest)
        throws InvalidSyntax {

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
            end.setRepeat(repeat);
            begins.put(ref, begin);
            ends.put(ref, end);
            repeats.put(ref, repeat);
        }

        if (preTest) {  // Repeat -> [S] -> Begin -> End
            // Connect the repeats to the loop inputs.
            for (DFNode input : loopCtx.getFirsts()) {
                if (input.hasValue()) continue;
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
                if (input.hasValue()) continue;
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
            if (exit.isContinue()) {
                DFNode node = exit.getNode();
                DFNode end = ends.get(node.getRef());
                if (end == null) {
                    end = ctx.get(node.getRef());
                }
                if (node instanceof JoinNode) {
                    ((JoinNode)node).merge(end);
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
            repeat.setEnd(end);
        }
    }

    /// Statement processors.
    @SuppressWarnings("unchecked")
    private void processBlock(
        DFContext ctx, DFTypeSpace typeSpace, DFGraph graph,
        DFTypeFinder finder, DFLocalScope scope, DFFrame frame,
	Block block)
        throws InvalidSyntax, EntityNotFound {
        DFLocalScope innerScope = scope.getChildByAST(block);
        for (Statement cstmt : (List<Statement>) block.statements()) {
            processStatement(
                ctx, typeSpace, graph, finder, innerScope, frame, cstmt);
        }
    }

    @SuppressWarnings("unchecked")
    private void processVariableDeclarationStatement(
        DFContext ctx, DFTypeSpace typeSpace, DFGraph graph,
        DFTypeFinder finder, DFLocalScope scope, DFFrame frame,
	VariableDeclarationStatement varStmt)
        throws InvalidSyntax, EntityNotFound {
        processVariableDeclaration(
            ctx, typeSpace, graph, finder, scope, frame,
            varStmt.fragments());
    }

    private void processExpressionStatement(
        DFContext ctx, DFTypeSpace typeSpace, DFGraph graph,
        DFTypeFinder finder, DFLocalScope scope, DFFrame frame,
	ExpressionStatement exprStmt)
        throws InvalidSyntax, EntityNotFound {
        processExpression(
            ctx, typeSpace, graph, finder, scope, frame,
            exprStmt.getExpression());
    }

    private void processIfStatement(
        DFContext ctx, DFTypeSpace typeSpace, DFGraph graph,
        DFTypeFinder finder, DFLocalScope scope, DFFrame frame,
	IfStatement ifStmt)
        throws InvalidSyntax, EntityNotFound {
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
        ConsistentHashSet<DFRef> outRefs = new ConsistentHashSet<DFRef>();
        if (thenFrame != null && thenCtx != null) {
            for (DFNode src : thenCtx.getFirsts()) {
                if (src.hasValue()) continue;
                src.accept(ctx.get(src.getRef()));
            }
            outRefs.addAll(thenFrame.getOutputRefs());
        }
        if (elseFrame != null && elseCtx != null) {
            for (DFNode src : elseCtx.getFirsts()) {
                if (src.hasValue()) continue;
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
                join.merge(ctx.get(ref));
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
        DFContext ctx, DFGraph graph, DFLocalScope scope,
        DFFrame frame, ASTNode apt,
        DFNode caseNode, DFContext caseCtx) {

        for (DFNode src : caseCtx.getFirsts()) {
            if (src.hasValue()) continue;
            src.accept(ctx.get(src.getRef()));
        }

        for (DFRef ref : frame.getOutputRefs()) {
            DFNode dst = caseCtx.get(ref);
            if (dst != null) {
                JoinNode join = new JoinNode(
                    graph, scope, ref.getRefType(), ref, apt, caseNode);
                join.recv(true, dst);
                join.merge(ctx.get(ref));
                ctx.set(join);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void processSwitchStatement(
        DFContext ctx, DFTypeSpace typeSpace, DFGraph graph,
        DFTypeFinder finder, DFLocalScope scope, DFFrame frame,
	SwitchStatement switchStmt)
        throws InvalidSyntax, EntityNotFound {
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
        DFLocalScope switchScope = scope.getChildByAST(switchStmt);
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
                    throw new InvalidSyntax(stmt);
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
        DFTypeFinder finder, DFLocalScope scope, DFFrame frame,
	WhileStatement whileStmt)
        throws InvalidSyntax, EntityNotFound {
        DFLocalScope loopScope = scope.getChildByAST(whileStmt);
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
        DFTypeFinder finder, DFLocalScope scope, DFFrame frame,
	DoStatement doStmt)
        throws InvalidSyntax, EntityNotFound {
        DFLocalScope loopScope = scope.getChildByAST(doStmt);
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
        DFTypeFinder finder, DFLocalScope scope, DFFrame frame,
	ForStatement forStmt)
        throws InvalidSyntax, EntityNotFound {
        DFLocalScope loopScope = scope.getChildByAST(forStmt);
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
	DFTypeFinder finder, DFLocalScope scope, DFFrame frame,
	EnhancedForStatement eForStmt)
        throws InvalidSyntax, EntityNotFound {
        Expression expr = eForStmt.getExpression();
        processExpression(
            ctx, typeSpace, graph, finder, scope, frame, expr);
        DFLocalScope loopScope = scope.getChildByAST(eForStmt);
        DFFrame loopFrame = frame.getChildByAST(eForStmt);
        DFContext loopCtx = new DFContext(graph, loopScope);
        SingleVariableDeclaration decl = eForStmt.getParameter();
        DFRef ref = loopScope.lookupVar(decl.getName());
        DFNode iterValue = new IterNode(graph, loopScope, ref, expr);
        iterValue.accept(ctx.getRValue());
        VarAssignNode assign = new VarAssignNode(graph, loopScope, ref, expr);
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
        DFTypeFinder finder, DFLocalScope scope, DFFrame frame,
	TryStatement tryStmt)
        throws InvalidSyntax, EntityNotFound {
        ConsistentHashSet<DFRef> outRefs = new ConsistentHashSet<DFRef>();

        DFLocalScope tryScope = scope.getChildByAST(tryStmt);
        DFFrame tryFrame = frame.getChildByAST(tryStmt);
        DFContext tryCtx = new DFContext(graph, tryScope);
        processStatement(
            tryCtx, typeSpace, graph, finder, tryScope, tryFrame,
            tryStmt.getBody());
        for (DFNode src : tryCtx.getFirsts()) {
            if (src.hasValue()) continue;
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
            DFLocalScope catchScope = scope.getChildByAST(cc);
            DFFrame catchFrame = frame.getChildByAST(cc);
            DFContext catchCtx = new DFContext(graph, catchScope);
            DFRef ref = catchScope.lookupVar(decl.getName());
            CatchNode cat = new CatchNode(graph, catchScope, ref, decl, exc);
            catchCtx.set(cat);
            processStatement(
                catchCtx, typeSpace, graph, finder, catchScope, catchFrame,
                cc.getBody());
            for (DFNode src : catchCtx.getFirsts()) {
                if (src.hasValue()) continue;
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
        DFTypeFinder finder, DFLocalScope scope, DFFrame frame,
	Statement stmt)
        throws InvalidSyntax, EntityNotFound {
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
            throw new InvalidSyntax(stmt);

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
                    if (ref.isLocal()) continue;
                    call.accept(ctx.get(ref), ref.getFullName());
                }
            }
            if (frame1 != null) {
                for (DFRef ref : frame1.getOutputRefs()) {
                    if (ref.isLocal()) continue;
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
                    if (ref.isLocal()) continue;
                    call.accept(ctx.get(ref), ref.getFullName());
                }
            }
            if (frame1 != null) {
                for (DFRef ref : frame1.getOutputRefs()) {
                    if (ref.isLocal()) continue;
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
            throw new InvalidSyntax(stmt);
        }
    }

    private void processInitializer(
        DFContext ctx, DFTypeSpace typeSpace, DFGraph graph,
        DFTypeFinder finder, DFLocalScope scope, DFFrame frame,
	Initializer initializer)
        throws InvalidSyntax, EntityNotFound {
        DFLocalScope innerScope = scope.getChildByAST(initializer);
        processStatement(
            ctx, typeSpace, graph, finder, innerScope, frame,
            initializer.getBody());
    }

    /**
     * Performs dataflow analysis for a given method.
     */
    @SuppressWarnings("unchecked")
    public DFGraph processMethod()
        throws InvalidSyntax, EntityNotFound {
        if (_ast == null) return null;
        assert _scope != null;
        assert _frame != null;
        assert _finder != null;
        MethodDeclaration methodDecl = (MethodDeclaration)_ast;
        if (methodDecl.getBody() == null) return null;

        DFGraph graph = this;
        DFContext ctx = new DFContext(graph, _scope);

        // Setup inputs.
        int i = 0;
        for (SingleVariableDeclaration decl :
                 (List<SingleVariableDeclaration>)methodDecl.parameters()) {
            DFRef ref = _scope.lookupArgument(i);
            DFNode input = new InputNode(graph, _scope, ref, decl);
            ctx.set(input);
            DFNode assign = new VarAssignNode(
                graph, _scope, _scope.lookupVar(decl.getName()), decl);
            assign.accept(input);
            ctx.set(assign);
            i++;
        }
        for (DFRef ref : _frame.getInputRefs()) {
            if (ref.isLocal()) continue;
            DFNode input = new InputNode(graph, _scope, ref, null);
            ctx.set(input);
        }
        {
            DFRef ref = _scope.lookupThis();
            DFNode input = new InputNode(graph, _scope, ref, null);
            ctx.set(input);
        }

        // Process the function body.
        try {
            processStatement(
                ctx, this, graph, _finder, _scope, _frame,
                methodDecl.getBody());
        } catch (MethodNotFound e) {
            e.setMethod(this);
            Logger.error(
                "DFMethod.processMethod: MethodNotFound",
                this, e.name+"("+Utils.join(e.argTypes)+")");
            throw e;
        } catch (EntityNotFound e) {
            e.setMethod(this);
            Logger.error(
                "DFMethod.processMethod: EntityNotFound",
                this, e.name);
            throw e;
        }

        // Cleanup.
        _frame.close(ctx);
        //_frame.dump();
        for (DFRef ref : _frame.getOutputRefs()) {
            if (ref.isLocal()) continue;
            DFNode output = new OutputNode(graph, _scope, ref, null);
            output.accept(ctx.get(ref));
        }

        this.cleanup();
        return this;
    }

    @SuppressWarnings("unchecked")
    public DFGraph processKlassBody()
        throws InvalidSyntax, EntityNotFound {
        // lookup base/inner klasses.
        assert _ast != null;
        List<BodyDeclaration> decls;
        if (_ast instanceof AbstractTypeDeclaration) {
            decls = ((AbstractTypeDeclaration)_ast).bodyDeclarations();
        } else if (_ast instanceof AnonymousClassDeclaration) {
            decls = ((AnonymousClassDeclaration)_ast).bodyDeclarations();
        } else {
            throw new InvalidSyntax(_ast);
        }

        assert _scope != null;
        assert _frame != null;
        assert _finder != null;
        DFGraph graph = this;
        DFContext ctx = new DFContext(graph, _scope);

        for (BodyDeclaration body : decls) {
            if (body instanceof AbstractTypeDeclaration) {
                // Inner classes are processed separately.

            } else if (body instanceof FieldDeclaration) {
                FieldDeclaration fieldDecl = (FieldDeclaration)body;
                for (VariableDeclarationFragment frag :
                         (List<VariableDeclarationFragment>) fieldDecl.fragments()) {
                    DFRef ref = _klass.lookupField(frag.getName());
                    DFNode value = null;
                    Expression init = frag.getInitializer();
                    if (init != null) {
                        processExpression(
                            ctx, this, graph, _finder,
                            _scope, _frame, init);
                        value = ctx.getRValue();
                    }
                    if (value == null) {
                        // uninitialized field: default = null.
                        value = new ConstNode(
                            graph, _scope,
                            DFNullType.NULL, null, "uninitialized");
                    }
                    DFNode assign = new VarAssignNode(
                        graph, _scope, ref, frag);
                    assign.accept(value);
                }

            } else if (body instanceof MethodDeclaration) {

            } else if (body instanceof EnumConstantDeclaration) {

            } else if (body instanceof AnnotationTypeMemberDeclaration) {

            } else if (body instanceof Initializer) {
                Initializer initializer = (Initializer)body;
                processInitializer(
                    ctx, this, graph, _finder, _scope, _frame,
                    initializer);

            } else {
                throw new InvalidSyntax(body);
            }
        }
        _frame.close(ctx);

        this.cleanup();
        return this;
    }

    // DFGraph methods.

    private static int _graphIdBase = 1;
    private int _graphId = 0;

    public String getGraphId() {
        if (_graphId == 0) {
            // Assign a unique id.
            _graphId = _graphIdBase++;
        }
        return "G"+_graphId+"_"+_name;
    }

    private List<DFNode> _nodes =
        new ArrayList<DFNode>();

    public int addNode(DFNode node) {
        _nodes.add(node);
        return _nodes.size();
    }

    public void cleanup() {
        Set<DFNode> removed = new HashSet<DFNode>();
        for (DFNode node : _nodes) {
            if (node.getKind() == null && node.purge()) {
                removed.add(node);
            }
        }
        // Do not remove input/output nodes.
        DFFrame frame = this.getFrame();
        for (DFNode node : frame.getInputNodes()) {
            removed.remove(node);
        }
        for (DFNode node : frame.getOutputNodes()) {
            removed.remove(node);
        }
        for (DFNode node : removed) {
            _nodes.remove(node);
        }
    }

    public Element toXML(Document document) {
        Element elem = document.createElement("method");
        elem.setAttribute("name", this.getSignature());
        elem.setAttribute("style", _callStyle.toString());
        for (DFMethod caller : _callers) {
            Element ecaller = document.createElement("caller");
            ecaller.setAttribute("name", caller.getSignature());
            elem.appendChild(ecaller);
        }
        for (DFMethod override : this.getOverrides()) {
            if (override == this) continue;
            Element eoverride = document.createElement("override");
            eoverride.setAttribute("name", override.getSignature());
            elem.appendChild(eoverride);
        }
        if (_ast != null) {
            Element east = document.createElement("ast");
            int start = _ast.getStartPosition();
            int end = start + _ast.getLength();
            east.setAttribute("type", Integer.toString(_ast.getNodeType()));
            east.setAttribute("start", Integer.toString(start));
            east.setAttribute("end", Integer.toString(end));
            elem.appendChild(east);
        }
        DFNode[] nodes = new DFNode[_nodes.size()];
        _nodes.toArray(nodes);
        Arrays.sort(nodes);
        elem.appendChild(_scope.toXML(document, nodes));
        return elem;
    }

    private class DFMethodScope extends DFLocalScope {

        private DFInternalRef _exception;
        private DFInternalRef _return = null;
        private DFInternalRef[] _arguments = null;

        protected DFMethodScope(DFVarScope outer, String name) {
            super(outer, name);
            _exception = new DFInternalRef(
                DFBuiltinTypes.getExceptionKlass(), "exception");
        }

        @Override
        public DFRef lookupArgument(int index)
            throws VariableNotFound {
            if (_arguments == null) throw new VariableNotFound("#arg"+index);
            return _arguments[index];
        }

        @Override
        public DFRef lookupReturn()
            throws VariableNotFound {
            if (_return == null) throw new VariableNotFound("#return");
            return _return;
        }

        @Override
        public DFRef lookupException()
            throws VariableNotFound {
            if (_exception == null) throw new VariableNotFound("#exception");
            return _exception;
        }

        /**
         * Lists all the variables defined inside a method.
         */
        @SuppressWarnings("unchecked")
        public void buildMethodDecl(
            DFTypeFinder finder, MethodDeclaration methodDecl)
            throws InvalidSyntax {
            //Logger.info("DFLocalScope.build:", this);
            if (methodDecl.getBody() == null) return;
            Type returnType = methodDecl.getReturnType2();
            DFType type;
            if (returnType == null) {
                type = DFBasicType.VOID;
            } else {
                type = finder.resolveSafe(returnType);
            }
            _return = new DFInternalRef(type, "return");
            _arguments = new DFInternalRef[methodDecl.parameters().size()];
            int i = 0;
            for (SingleVariableDeclaration decl :
                     (List<SingleVariableDeclaration>) methodDecl.parameters()) {
                DFType argType = finder.resolveSafe(decl.getType());
                if (decl.isVarargs()) {
                    argType = new DFArrayType(argType, 1);
                }
                int ndims = decl.getExtraDimensions();
                if (ndims != 0) {
                    argType = new DFArrayType(argType, ndims);
                }
                _arguments[i] = new DFInternalRef(argType, "arg"+i);
                this.addVar(decl.getName(), argType);
                i++;
            }
            this.buildStmt(finder, methodDecl.getBody());
        }

        public void buildBodyDecls(
            DFTypeFinder finder, List<BodyDeclaration> decls)
            throws InvalidSyntax {
            for (BodyDeclaration body : decls) {
                if (body instanceof Initializer) {
                    Initializer initializer = (Initializer)body;
                    DFLocalScope innerScope = this.getChildByAST(body);
                    innerScope.buildStmt(finder, initializer.getBody());
                }
            }
        }

        private class DFInternalRef extends DFRef {

            private String _name;

            public DFInternalRef(DFType type, String name) {
                super(type);
                _name = name;
            }

            @Override
            public boolean isLocal() {
                return true;
            }
            @Override
            public boolean isInternal() {
                return true;
            }

            @Override
            public String getFullName() {
                return "#"+_name;
            }
        }
    }
}
