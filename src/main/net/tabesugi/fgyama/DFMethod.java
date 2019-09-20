//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import javax.xml.stream.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


// BreakExit
class BreakExit extends DFExit {

    public BreakExit(DFFrame frame, DFNode node) {
        super(frame, node);
        // frame.getLabel() can be either @BREAKABLE or a label.
    }

    @Override
    public int compareTo(DFExit exit) {
        boolean m0 = this.getNode().canMerge();
        boolean m1 = exit.getNode().canMerge();
        if (m0 && !m1) {
            return +1;
        } else if (!m0 && m1) {
            return -1;
        } else {
            return super.compareTo(exit);
        }
    }
}

// ContinueExit
class ContinueExit extends DFExit {

    public ContinueExit(DFFrame frame, DFNode node) {
        super(frame, node);
        // frame.getLabel() can be either @BREAKABLE or a label.
    }
}

// ReturnExit
class ReturnExit extends DFExit {

    public ReturnExit(DFFrame frame, DFNode node) {
        super(frame, node);
        assert frame.getLabel() == DFFrame.RETURNABLE;
    }

    @Override
    public int compareTo(DFExit exit) {
        boolean m0 = this.getNode().canMerge();
        boolean m1 = exit.getNode().canMerge();
        if (m0 && !m1) {
            return +1;
        } else if (!m0 && m1) {
            return -1;
        } else {
            return super.compareTo(exit);
        }
    }
}

// ThrowExit
class ThrowExit extends DFExit {

    private DFKlass _excKlass;

    public ThrowExit(DFFrame frame, DFNode node, DFKlass excKlass) {
        super(frame, node);
        _excKlass = excKlass;
    }

    public DFKlass getExcKlass() {
        return _excKlass;
    }
}

// SingleAssignNode:
class SingleAssignNode extends DFNode {

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
class ArrayAssignNode extends DFNode {

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
class FieldAssignNode extends DFNode {

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
class VarRefNode extends DFNode {

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
class ArrayRefNode extends DFNode {

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
class FieldRefNode extends DFNode {

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
class PrefixNode extends DFNode {

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
class PostfixNode extends DFNode {

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
class InfixNode extends DFNode {

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
class TypeCastNode extends DFNode {

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
class TypeCheckNode extends DFNode {

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
class CaseNode extends DFNode {

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
class ConstNode extends DFNode {

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
class ValueSetNode extends DFNode {

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

// CaptureNode: represents variable captures (for lambdas).
class CaptureNode extends DFNode {

    public CaptureNode(
        DFGraph graph, DFVarScope scope, DFType type,
        ASTNode ast) {
        super(graph, scope, type, null, ast);
    }

    @Override
    public String getKind() {
        return "capture";
    }
}

// JoinNode
class JoinNode extends DFNode {

    private DFNode.Link _linkCond;
    private DFNode.Link _linkTrue = null;
    private DFNode.Link _linkFalse = null;

    public JoinNode(
        DFGraph graph, DFVarScope scope, DFType type, DFRef ref,
        ASTNode ast, DFNode cond) {
        super(graph, scope, type, ref, ast);
        _linkCond = this.accept(cond, "cond");
    }

    @Override
    public String getKind() {
        return "join";
    }

    public void recv(boolean cond, DFNode node) {
        if (cond) {
            assert _linkTrue == null;
            _linkTrue = this.accept(node, "true");
        } else {
            assert _linkFalse == null;
            _linkFalse = this.accept(node, "false");
        }
    }

    @Override
    public boolean canMerge() {
        return (_linkTrue == null || _linkFalse == null);
    }

    @Override
    public void merge(DFNode node) {
        if (_linkTrue == null) {
            assert _linkFalse != null;
            _linkTrue = this.accept(node, "true");
        } else if (_linkFalse == null) {
            assert _linkTrue != null;
            _linkFalse = this.accept(node, "false");
        } else {
            Logger.error("JoinNode: cannot merge:", this, node);
            assert false;
        }
    }

    @Override
    public boolean purge() {
        if (_linkTrue == null) {
            assert _linkFalse != null;
            unlink(_linkFalse.getSrc());
            return true;
        } else if (_linkFalse == null) {
            assert _linkTrue != null;
            unlink(_linkTrue.getSrc());
            return true;
        } else {
            DFNode srcTrue = _linkTrue.getSrc();
            if (srcTrue instanceof JoinNode &&
                ((JoinNode)srcTrue)._linkCond.getSrc() == _linkCond.getSrc() &&
                ((JoinNode)srcTrue)._linkFalse != null &&
                ((JoinNode)srcTrue)._linkFalse.getSrc() == _linkFalse.getSrc()) {
                unlink(srcTrue);
                return true;
            }
            DFNode srcFalse = _linkFalse.getSrc();
            if (srcFalse instanceof JoinNode &&
                ((JoinNode)srcFalse)._linkCond.getSrc() == _linkCond.getSrc() &&
                ((JoinNode)srcFalse)._linkTrue != null &&
                ((JoinNode)srcFalse)._linkTrue.getSrc() == _linkTrue.getSrc()) {
                unlink(srcFalse);
                return true;
            }
            return false;
        }
    }
}

// LoopNode
class LoopNode extends DFNode {

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
        ASTNode ast, String loopId, DFNode init) {
        super(graph, scope, ref, ast, loopId);
        this.accept(init, "init");
    }

    @Override
    public String getKind() {
        return "begin";
    }

    public void setCont(DFNode cont) {
        this.accept(cont, "cont");
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
class IterNode extends DFNode {

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
abstract class CallNode extends DFNode {

    public DFFunctionType funcType;
    public DFNode[] args;

    public CallNode(
        DFGraph graph, DFVarScope scope, DFType type, DFRef ref,
        ASTNode ast, DFFunctionType funcType) {
        super(graph, scope, type, ref, ast);
        this.funcType = funcType;
        this.args = null;
    }

    @Override
    public String getKind() {
        return "call";
    }

    public void setArgs(DFNode[] args) {
        assert this.args == null;
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
        DFGraph graph, DFVarScope scope,
        ASTNode ast, DFFunctionType funcType,
        DFNode obj, DFMethod[] methods) {
        super(graph, scope, funcType.getReturnType(), null,
              ast, funcType);
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
class ReceiveNode extends DFNode {

    public ReceiveNode(
        DFGraph graph, DFVarScope scope, CallNode call,
        ASTNode ast) {
        super(graph, scope, call.getNodeType(), null, ast);
        this.accept(call);
    }

    public ReceiveNode(
        DFGraph graph, DFVarScope scope, CallNode call,
        ASTNode ast, DFRef ref) {
        super(graph, scope, ref.getRefType(), ref, ast);
        this.accept(call, ref.getFullName());
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

// ReturnNode:
class ReturnNode extends SingleAssignNode {

    public ReturnNode(
        DFGraph graph, DFVarScope scope, DFRef ref,
        ASTNode ast) {
        super(graph, scope, ref, ast);
    }

    @Override
    public String getKind() {
        return "return";
    }
}

// ThrowNode
class ThrowNode extends SingleAssignNode {

    public ThrowNode(
        DFGraph graph, DFVarScope scope, DFRef ref, ASTNode ast) {
        super(graph, scope, ref, ast);
    }

    @Override
    public String getKind() {
        return "throw";
    }
}

// CatchNode
class CatchNode extends SingleAssignNode {

    private int _nexcs = 0;

    public CatchNode(
        DFGraph graph, DFVarScope scope, DFRef ref,
        ASTNode ast) {
        super(graph, scope, ref, ast);
    }

    @Override
    public Link accept(DFNode node) {
        String label = "exc"+(_nexcs++);
        return this.accept(node, label);
    }

    @Override
    public String getKind() {
        return "catch";
    }
}

// CatchJoin
class CatchJoin extends DFNode {

    public CatchJoin(
        DFGraph graph, DFVarScope scope, ASTNode ast,
        DFNode node, DFKlass catchKlass) {
        super(graph, scope, node.getNodeType(), node.getRef(), ast);
        this.accept(node, catchKlass.getTypeName());
    }

    @Override
    public String getKind() {
        return "catchjoin";
    }

    @Override
    public boolean canMerge() {
        return !this.hasValue();
    }

    public void merge(DFNode node) {
        assert !this.hasValue();
        this.accept(node);
    }

}


//  DFMethod
//
public class DFMethod extends DFTypeSpace implements Comparable<DFMethod> {

    public enum CallStyle {
        Constructor,
        InstanceMethod,
        StaticMethod,
        Lambda,
        InstanceOrStatic,           // for search only.
        Initializer;

        @Override
        public String toString() {
            switch (this) {
            case InstanceMethod:
                return "instance";
            case StaticMethod:
                return "static";
            case Initializer:
                return "initializer";
            case Constructor:
                return "constructor";
            case Lambda:
                return "lambda";
            default:
                return null;
            }
        }
    }

    private DFKlass _klass;
    private String _name;
    private CallStyle _callStyle;
    private MethodScope _scope;
    private DFFrame _frame;
    private boolean _abstract;

    private DFTypeFinder _finder = null;
    private DFMapType[] _mapTypes = null;
    private Map<String, DFType> _mapTypeMap = null;
    private DFFunctionType _funcType = null;

    private ConsistentHashSet<DFMethod> _callers =
        new ConsistentHashSet<DFMethod>();

    private ASTNode _ast = null;

    // List of subclass' methods overriding this method.
    private List<DFMethod> _overriders = new ArrayList<DFMethod>();
    // List of superclass' methods being overriden by this method.
    private List<DFMethod> _overriding = new ArrayList<DFMethod>();

    public DFMethod(
        DFKlass klass, String id, CallStyle callStyle,
        String name, DFVarScope outer, boolean isAbstract) {
        super(id, klass);
        _klass = klass;
        _name = name;
        _callStyle = callStyle;
        _scope = new MethodScope(outer, id);
        _frame = new DFFrame(this, DFFrame.RETURNABLE);
        _abstract = isAbstract;
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
	    // Set the Object type temporarily for circular definition.
            _mapTypeMap.put(mapType.getTypeName(), DFBuiltinTypes.getObjectKlass());
	    mapType.build(_finder);
            _mapTypeMap.put(mapType.getTypeName(), mapType.toKlass());
        }
    }

    public void setFuncType(DFFunctionType funcType) {
        _funcType = funcType;
    }

    @Override
    public String toString() {
        return ("<DFMethod("+this.getSignature()+")>");
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

    public DFKlass getKlass() {
        return _klass;
    }

    public CallStyle getCallStyle() {
        return _callStyle;
    }

    public boolean isAbstract() {
        return _abstract;
    }

    public DFFunctionType getFuncType() {
        return _funcType;
    }

    @Override
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

    public boolean addOverrider(DFMethod method) {
        if (method._callStyle != CallStyle.Lambda &&
            !_name.equals(method._name)) return false;
        if (!_funcType.equals(method._funcType)) return false;
	//Logger.info("DFMethod.addOverrider:", this, "<-", method);
        _overriders.add(method);
        method._overriding.add(this);
        return true;
    }

    private void listOverriders(List<Overrider> overriders, int prio) {
        overriders.add(new Overrider(this, prio));
        for (DFMethod method : _overriders) {
            method.listOverriders(overriders, prio+1);
        }
    }

    private List<DFMethod> _allOverriders = null;
    public List<DFMethod> getOverriders() {
	// Cache for future reference.
	if (_allOverriders == null) {
	    List<Overrider> overriders = new ArrayList<Overrider>();
	    this.listOverriders(overriders, 0);
	    Collections.sort(overriders);
	    _allOverriders = new ArrayList<DFMethod>();
	    for (Overrider overrider : overriders) {
		_allOverriders.add(overrider.method);
	    }
	}
        return _allOverriders;
    }

    public List<DFMethod> getOverridings() {
	return _overriding;
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

    public ASTNode getTree() {
	return _ast;
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
        } else if (_ast instanceof ClassInstanceCreation) {
	    ClassInstanceCreation cstr = (ClassInstanceCreation)_ast;
            _scope.buildBodyDecls(
		_finder, cstr.getAnonymousClassDeclaration().bodyDeclarations());
        } else if (_ast instanceof LambdaExpression) {
            _scope.buildLambda(_finder, (LambdaExpression)_ast);
	}  else {
	    throw new InvalidSyntax(_ast);
	}
        //_scope.dump();
    }

    @SuppressWarnings("unchecked")
    public void buildFrame(List<DFKlass> defined)
        throws InvalidSyntax {
	if (_ast == null) return;
	assert _scope != null;
        assert _frame != null;
        if (_ast instanceof MethodDeclaration) {
            _frame.buildMethodDecl(
                defined, _scope, (MethodDeclaration)_ast);
	} else if (_ast instanceof AbstractTypeDeclaration) {
            _frame.buildBodyDecls(
                defined, _scope, ((AbstractTypeDeclaration)_ast).bodyDeclarations());
        } else if (_ast instanceof ClassInstanceCreation) {
	    ClassInstanceCreation cstr = (ClassInstanceCreation)_ast;
            _frame.buildBodyDecls(
                defined, _scope,
		cstr.getAnonymousClassDeclaration().bodyDeclarations());
        } else if (_ast instanceof LambdaExpression) {
            _frame.buildLambda(
                defined, _scope, (LambdaExpression)_ast);
        } else {
            throw new InvalidSyntax(_ast);
        }
        //_frame.dump();
    }

    // Overrider
    private class Overrider implements Comparable<Overrider> {

	public DFMethod method;
	public int level;

	public Overrider(DFMethod method, int level) {
	    this.method = method;
	    this.level = level;
	}

	@Override
	public String toString() {
	    return ("<Overrider: "+this.method+" ("+this.level+")>");
	}

	@Override
	public int compareTo(Overrider overrider) {
	    if (this.level != overrider.level) {
		return overrider.level - this.level;
	    } else {
		return this.method.compareTo(overrider.method);
	    }
	}
    }

    /// General graph operations.

    /**
     * Creates a value node.
     */
    @SuppressWarnings("unchecked")
    private void processExpression(
        DFContext ctx, DFTypeSpace typeSpace, MethodGraph graph,
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
                CallStyle callStyle;
                DFNode obj = null;
                DFType type = null;
                if (expr1 == null) {
                    // "method()"
                    obj = ctx.get(scope.lookupThis());
                    type = obj.getNodeType();
                    callStyle = CallStyle.InstanceOrStatic;
                } else {
                    callStyle = CallStyle.InstanceMethod;
                    if (expr1 instanceof Name) {
                        // "ClassName.method()"
                        try {
                            type = finder.lookupType((Name)expr1);
                            callStyle = CallStyle.StaticMethod;
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
		int nargs = invoke.arguments().size();
		DFNode[] args = new DFNode[nargs];
		DFType[] argTypes = new DFType[nargs];
		for (int i = 0; i < nargs; i++) {
		    Expression arg = (Expression)invoke.arguments().get(i);
                    processExpression(
                        ctx, typeSpace, graph, finder, scope, frame, arg);
                    DFNode node = ctx.getRValue();
		    args[i] = node;
		    argTypes[i] = node.getNodeType();
                }
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
                            klass, id, CallStyle.InstanceMethod, id, null, false);
                        fallback.setFuncType(
                            new DFFunctionType(argTypes, DFUnknownType.UNKNOWN));
                        Logger.error(
                            "DFMethod.processExpression: MethodNotFound",
                            this, klass, expr);
                        Logger.info("Fallback method:", klass, ":", fallback);
                        method = fallback;
                    }
                }
                List<DFMethod> overriders = method.getOverriders();
                DFMethod[] methods = new DFMethod[overriders.size()];
                overriders.toArray(methods);
                DFFunctionType funcType = method.getFuncType();
                MethodCallNode call = new MethodCallNode(
                    graph, scope, invoke, funcType, obj, methods);
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
                                  graph, scope, call, invoke));
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
                                    graph, scope, call, invoke, ref));
                    }
                }
                // TODO: catch and forward exceptions.
                // for (DFNode exception : funcType.getExceptions()) {
                //     DFFrame dstFrame = frame.find(DFFrame.CATCHABLE);
                //     graph.addExit(frame, new DFExit(dstFrame, exception));
                // }

            } else if (expr instanceof SuperMethodInvocation) {
		// "super.method()"
                SuperMethodInvocation sinvoke = (SuperMethodInvocation)expr;
                DFNode obj = ctx.get(scope.lookupThis());
		int nargs = sinvoke.arguments().size();
		DFNode[] args = new DFNode[nargs];
		DFType[] argTypes = new DFType[nargs];
		for (int i = 0; i < nargs; i++) {
		    Expression arg = (Expression)sinvoke.arguments().get(i);
                    processExpression(
                        ctx, typeSpace, graph, finder, scope, frame, arg);
                    DFNode node = ctx.getRValue();
		    args[i] = node;
		    argTypes[i] = node.getNodeType();
                }
                DFKlass klass = obj.getNodeType().toKlass();
                DFKlass baseKlass = klass.getBaseKlass();
                assert baseKlass != null;
                DFMethod method;
                try {
                    method = baseKlass.lookupMethod(
                        CallStyle.InstanceMethod, sinvoke.getName(), argTypes);
                } catch (MethodNotFound e) {
                    // fallback method.
                    String id = sinvoke.getName().getIdentifier();
                    DFMethod fallback = new DFMethod(
                        baseKlass, id, CallStyle.InstanceMethod, id, null, false);
                    fallback.setFuncType(
                        new DFFunctionType(argTypes, DFUnknownType.UNKNOWN));
                    Logger.error(
                        "DFMethod.processExpression: MethodNotFound",
                        this, baseKlass, expr);
                    Logger.info("Fallback method:", baseKlass, ":", fallback);
                    method = fallback;
                }
                DFMethod methods[] = new DFMethod[] { method };
                DFFunctionType funcType = method.getFuncType();
                MethodCallNode call = new MethodCallNode(
                    graph, scope, sinvoke, funcType, obj, methods);
                call.setArgs(args);
                DFFrame frame1 = method.getFrame();
                if (frame1 != null) {
                    for (DFRef ref : frame1.getInputRefs()) {
                        if (ref.isLocal()) continue;
                        call.accept(ctx.get(ref), ref.getFullName());
                    }
                }
                ctx.setRValue(new ReceiveNode(
                                  graph, scope, call, sinvoke));
                if (frame1 != null) {
                    for (DFRef ref : frame1.getOutputRefs()) {
                        if (ref.isLocal()) continue;
                        ctx.set(new ReceiveNode(
                                    graph, scope, call, sinvoke, ref));
                    }
                }
                // TODO: catch and forward exceptions.
                // for (DFNode exception : funcType.getExceptions()) {
                //     DFFrame dstFrame = frame.find(DFFrame.CATCHABLE);
                //     graph.addExit(frame, new DFExit(dstFrame, exception));
                // }

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
                DFKlass instKlass;
                if (cstr.getAnonymousClassDeclaration() != null) {
                    // Anonymous classes are processed separately.
                    String id = Utils.encodeASTNode(cstr);
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
		int nargs = cstr.arguments().size();
		DFNode[] args = new DFNode[nargs];
		DFType[] argTypes = new DFType[nargs];
		for (int i = 0; i < nargs; i++) {
		    Expression arg = (Expression)cstr.arguments().get(i);
                    processExpression(
                        ctx, typeSpace, graph, finder, scope, frame, arg);
                    DFNode node = ctx.getRValue();
		    args[i] = node;
		    argTypes[i] = node.getNodeType();
                }
                DFMethod constructor = instKlass.lookupMethod(
                    CallStyle.Constructor, null, argTypes);
                DFFunctionType funcType = constructor.getFuncType();
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
                                  graph, scope, call, cstr));
                if (frame1 != null) {
                    for (DFRef ref : frame1.getOutputRefs()) {
                        if (ref.isLocal()) continue;
                        ctx.set(new ReceiveNode(
                                    graph, scope, call, cstr, ref));
                    }
                }
                // TODO: catch and forward exceptions.
                // for (DFNode exception : funcType.getExceptions()) {
                //     DFFrame dstFrame = frame.find(DFFrame.CATCHABLE);
                //     graph.addExit(frame, new DFExit(dstFrame, exception));
                // }

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
                String id = Utils.encodeASTNode(lambda);
                DFType lambdaType = typeSpace.getType(id);
		assert lambdaType instanceof DFFunctionalKlass;
                // Capture values.
                CaptureNode node = new CaptureNode(graph, scope, lambdaType, lambda);
                for (DFFunctionalKlass.CapturedRef captured :
                         ((DFFunctionalKlass)lambdaType).getCapturedRefs()) {
                    node.accept(ctx.get(captured.getOriginal()), captured.getFullName());
                }
                ctx.setRValue(node);

            } else if (expr instanceof MethodReference) {
                //  CreationReference
                //  ExpressionMethodReference
                //  SuperMethodReference
                //  TypeMethodReference
                MethodReference methodref = (MethodReference)expr;
                // XXX TODO MethodReference
		Logger.error("Unsupported: MethodReference", expr);
                String id = Utils.encodeASTNode(methodref);
                DFType methodrefType = typeSpace.getType(id);
		assert methodrefType != null;
                ctx.setRValue(
                    new DFNode(graph, scope, methodrefType, null, null));

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
        DFContext ctx, DFTypeSpace typeSpace, MethodGraph graph,
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
        DFContext ctx, DFTypeSpace typeSpace, MethodGraph graph,
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
        DFContext ctx, MethodGraph graph, DFLocalScope scope,
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
        Collection<DFRef> loopRefs = loopFrame.getOutputRefs();
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
                        begin.setCont(output);
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
                begin.setCont(repeat);
            }
        }

        // Redirect the continue statements.
        assert frame != loopFrame;
        for (DFExit exit : graph.getExits(loopFrame)) {
            if (exit.getFrame() != loopFrame) continue;
            if (exit instanceof ContinueExit) {
                DFNode node = exit.getNode();
                DFNode end = ends.get(node.getRef());
                if (end == null) {
                    end = ctx.get(node.getRef());
                }
                if (node.canMerge()) {
                    node.merge(end);
                }
                ends.put(node.getRef(), node);
            }
        }

        // Closing the loop.
        for (DFRef ref : loopRefs) {
            DFNode end = ends.get(ref);
            LoopRepeatNode repeat = repeats.get(ref);
            ctx.set(end);
            repeat.setEnd(end);
        }

        this.endBreaks(ctx, graph, frame, loopFrame);
    }

    /// Statement processors.
    @SuppressWarnings("unchecked")
    private void processBlock(
        DFContext ctx, DFTypeSpace typeSpace, MethodGraph graph,
        DFTypeFinder finder, DFLocalScope scope, DFFrame frame,
	Block block)
        throws InvalidSyntax, EntityNotFound {
        DFLocalScope innerScope = scope.getChildByAST(block);
        DFFrame innerFrame = frame.getChildByAST(block);
        for (Statement cstmt : (List<Statement>) block.statements()) {
            processStatement(
                ctx, typeSpace, graph, finder, innerScope, innerFrame, cstmt);
        }
        this.endBreaks(ctx, graph, frame, innerFrame);
    }

    @SuppressWarnings("unchecked")
    private void processVariableDeclarationStatement(
        DFContext ctx, DFTypeSpace typeSpace, MethodGraph graph,
        DFTypeFinder finder, DFLocalScope scope, DFFrame frame,
	VariableDeclarationStatement varStmt)
        throws InvalidSyntax, EntityNotFound {
        processVariableDeclaration(
            ctx, typeSpace, graph, finder, scope, frame,
            varStmt.fragments());
    }

    private void processExpressionStatement(
        DFContext ctx, DFTypeSpace typeSpace, MethodGraph graph,
        DFTypeFinder finder, DFLocalScope scope, DFFrame frame,
	ExpressionStatement exprStmt)
        throws InvalidSyntax, EntityNotFound {
        processExpression(
            ctx, typeSpace, graph, finder, scope, frame,
            exprStmt.getExpression());
    }

    private void processIfStatement(
        DFContext ctx, DFTypeSpace typeSpace, MethodGraph graph,
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
            assert frame != thenFrame;
            for (DFExit exit : graph.getExits(thenFrame)) {
                DFNode node = exit.getNode();
                DFRef ref = node.getRef();
                JoinNode join = new JoinNode(
                    graph, scope, ref.getRefType(), ref, null, condValue);
                join.recv(true, node);
                exit.setNode(join);
                graph.addExit(frame, exit);
            }
        }
        if (elseFrame != null) {
            assert frame != elseFrame;
            for (DFExit exit : graph.getExits(elseFrame)) {
                DFNode node = exit.getNode();
                DFRef ref = node.getRef();
                JoinNode join = new JoinNode(
                    graph, scope, ref.getRefType(), ref, null, condValue);
                join.recv(false, node);
                exit.setNode(join);
                graph.addExit(frame, exit);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void processSwitchStatement(
        DFContext ctx, DFTypeSpace typeSpace, MethodGraph graph,
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
        SwitchCase switchCase = null;
        DFFrame caseFrame = null;
        CaseNode caseNode = null;
        DFContext caseCtx = null;
        for (Statement cstmt : (List<Statement>) switchStmt.statements()) {
            assert cstmt != null;
            if (cstmt instanceof SwitchCase) {
                if (caseFrame != null) {
                    assert switchCase != null;
                    assert caseNode != null;
                    assert caseCtx != null;
                    processSwitchCase(
                        ctx, graph, switchScope, frame,
                        caseFrame, switchCase, caseNode, caseCtx);
                }
                caseFrame = frame.getChildByAST(cstmt);
                switchCase = (SwitchCase)cstmt;
                caseNode = new CaseNode(graph, switchScope, cstmt);
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
                            ctx, typeSpace, graph, finder, switchScope, caseFrame,
			    expr);
                        caseNode.addMatch(ctx.getRValue());
                    }
                } else {
                    // "default" case.
                }
            } else {
                if (caseFrame == null) {
                    // no "case" statement.
                    throw new InvalidSyntax(cstmt);
                }
                processStatement(
                    caseCtx, typeSpace, graph, finder, switchScope,
                    caseFrame, cstmt);
            }
        }
        if (caseFrame != null) {
            assert switchCase != null;
            assert caseNode != null;
            assert caseCtx != null;
            processSwitchCase(
                ctx, graph, switchScope, frame,
                caseFrame, switchCase, caseNode, caseCtx);
        }
    }

    private void processSwitchCase(
        DFContext ctx, MethodGraph graph, DFLocalScope scope,
        DFFrame frame, DFFrame caseFrame, ASTNode apt,
        DFNode caseNode, DFContext caseCtx) {

        for (DFNode src : caseCtx.getFirsts()) {
            if (src.hasValue()) continue;
            src.accept(ctx.get(src.getRef()));
        }

        for (DFRef ref : caseFrame.getOutputRefs()) {
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

    private void processWhileStatement(
        DFContext ctx, DFTypeSpace typeSpace, MethodGraph graph,
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
    }

    private void processDoStatement(
        DFContext ctx, DFTypeSpace typeSpace, MethodGraph graph,
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
    }

    @SuppressWarnings("unchecked")
    private void processForStatement(
        DFContext ctx, DFTypeSpace typeSpace, MethodGraph graph,
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
    }

    @SuppressWarnings("unchecked")
    private void processEnhancedForStatement(
        DFContext ctx, DFTypeSpace typeSpace, MethodGraph graph,
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
    }

    @SuppressWarnings("unchecked")
    private void processTryStatement(
        DFContext ctx, DFTypeSpace typeSpace, MethodGraph graph,
        DFTypeFinder finder, DFLocalScope scope, DFFrame frame,
	TryStatement tryStmt)
        throws InvalidSyntax, EntityNotFound {
        List<CatchClause> catches = (List<CatchClause>)tryStmt.catchClauses();

        // Find the innermost catch frame so that
        // it can catch all the specified Exceptions.
        DFFrame tryFrame = frame;
        for (int i = catches.size()-1; 0 <= i; i--) {
            CatchClause cc = catches.get(i);
            tryFrame = tryFrame.getChildByAST(cc);
        }

        // Execute the try clause.
        DFLocalScope tryScope = scope.getChildByAST(tryStmt);
        DFContext tryCtx = new DFContext(graph, tryScope);
        processStatement(
            tryCtx, typeSpace, graph, finder, tryScope, tryFrame,
            tryStmt.getBody());
        for (DFNode src : tryCtx.getFirsts()) {
            if (src.hasValue()) continue;
            src.accept(ctx.get(src.getRef()));
        }

        // Catch each speficied Exception in *a reverse order*.
        DFFrame catchFrame = tryFrame;
        for (int i = catches.size()-1; 0 <= i; i--) {
            CatchClause cc = catches.get(i);
            SingleVariableDeclaration decl = cc.getException();
            DFLocalScope catchScope = scope.getChildByAST(cc);
            DFContext catchCtx = new DFContext(graph, catchScope);
            DFKlass catchKlass = catchFrame.getCatchKlass();
            assert catchKlass != null;
            DFRef catchRef = catchScope.lookupVar(decl.getName());
            CatchNode cat = new CatchNode(graph, catchScope, catchRef, decl);
            catchCtx.set(cat);
            // Take care of exits.
            DFRef excRef = _scope.lookupException(catchKlass);
            DFFrame parentFrame = catchFrame.getOuterFrame();
            for (DFExit exit : graph.getExits(catchFrame)) {
                DFNode src = exit.getNode();
                if (exit.getFrame() == catchFrame) {
                    assert exit instanceof ThrowExit;
                    DFRef ref = src.getRef();
                    if (ref == excRef) {
                        cat.accept(src);
                    } else {
                        DFNode dst = ctx.getLast(ref);
                        if (dst == null) {
                            dst = src;
                        } else if (dst.canMerge()) {
                            dst.merge(src);
                        } else if (src.canMerge()) {
                            src.merge(dst);
                            dst = src;
                        } else {
                            Logger.error("DFMethod.catch: Conflict:", dst, "<-", src);
                            continue;
                        }
                        ctx.set(dst);
                    }
                } else {
                    CatchJoin join = new CatchJoin(
                        graph, scope, cc, src, catchKlass);
                    exit.setNode(join);
                    graph.addExit(parentFrame, exit);
                }
            }
            processStatement(
                catchCtx, typeSpace, graph, finder, catchScope, frame,
                cc.getBody());
            for (DFNode src : catchCtx.getFirsts()) {
                if (src.hasValue()) continue;
                src.accept(ctx.get(src.getRef()));
            }
            catchFrame = parentFrame;
        }

        // XXX Take care of ALL exits from tryFrame.
        Block finBlock = tryStmt.getFinally();
        if (finBlock != null) {
            processStatement(
                ctx, typeSpace, graph, finder, scope, frame, finBlock);
        }
    }

    @SuppressWarnings("unchecked")
    private void processStatement(
        DFContext ctx, DFTypeSpace typeSpace, MethodGraph graph,
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
            assert dstFrame != null;
            Expression expr = rtrnStmt.getExpression();
            if (expr != null) {
                processExpression(
                    ctx, typeSpace, graph, finder, scope, frame, expr);
                DFRef ref = _scope.lookupReturn();
                ReturnNode ret = new ReturnNode(graph, scope, ref, rtrnStmt);
                ret.accept(ctx.getRValue());
                graph.addExit(frame, new ReturnExit(dstFrame, ret));
            }
            for (DFRef ref : dstFrame.getOutputRefs()) {
                graph.addExit(frame, new ReturnExit(dstFrame, ctx.get(ref)));
            }

        } else if (stmt instanceof BreakStatement) {
	    // "break;"
            BreakStatement breakStmt = (BreakStatement)stmt;
            SimpleName labelName = breakStmt.getLabel();
            String dstLabel = (labelName != null)?
                labelName.getIdentifier() : DFFrame.BREAKABLE;
            DFFrame dstFrame = frame.find(dstLabel);
            for (DFRef ref : dstFrame.getOutputRefs()) {
                graph.addExit(frame, new BreakExit(dstFrame, ctx.get(ref)));
            }

        } else if (stmt instanceof ContinueStatement) {
	    // "continue;"
            ContinueStatement contStmt = (ContinueStatement)stmt;
            SimpleName labelName = contStmt.getLabel();
            String dstLabel = (labelName != null)?
                labelName.getIdentifier() : DFFrame.BREAKABLE;
            DFFrame dstFrame = frame.find(dstLabel);
            for (DFRef ref : dstFrame.getOutputRefs()) {
                graph.addExit(frame, new ContinueExit(dstFrame, ctx.get(ref)));
            }

        } else if (stmt instanceof LabeledStatement) {
	    // "here:"
            LabeledStatement labeledStmt = (LabeledStatement)stmt;
            DFFrame labeledFrame = frame.getChildByAST(labeledStmt);
            processStatement(
                ctx, typeSpace, graph, finder, scope, labeledFrame,
                labeledStmt.getBody());
            this.endBreaks(ctx, graph, frame, labeledFrame);

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
            DFNode exc = ctx.getRValue();
            DFKlass excKlass = exc.getNodeType().toKlass();
            DFRef excRef = _scope.lookupException(excKlass);
            ThrowNode thrown = new ThrowNode(graph, scope, excRef, stmt);
            thrown.accept(exc);
            // Find out the catch clause. If not, the entire method throws.
            DFFrame dstFrame = frame.find(excKlass);
            if (dstFrame == null) {
                dstFrame = frame.find(DFFrame.RETURNABLE);
                assert dstFrame != null;
            }
            graph.addExit(frame, new ThrowExit(dstFrame, thrown, excKlass));
            for (DFRef ref : dstFrame.getOutputRefs()) {
                graph.addExit(frame, new ThrowExit(dstFrame, ctx.get(ref), excKlass));
            }

        } else if (stmt instanceof ConstructorInvocation) {
	    // "this(args)"
            ConstructorInvocation ci = (ConstructorInvocation)stmt;
            DFNode obj = ctx.get(scope.lookupThis());
	    int nargs = ci.arguments().size();
	    DFNode[] args = new DFNode[nargs];
	    DFType[] argTypes = new DFType[nargs];
	    for (int i = 0; i < nargs; i++) {
		Expression arg = (Expression)ci.arguments().get(i);
                processExpression(
                    ctx, typeSpace, graph, finder, scope, frame, arg);
                DFNode node = ctx.getRValue();
		args[i] = node;
		argTypes[i] = node.getNodeType();
            }
            DFKlass klass = scope.lookupThis().getRefType().toKlass();
            DFMethod constructor = klass.lookupMethod(
                CallStyle.Constructor, null, argTypes);
            DFMethod methods[] = new DFMethod[] { constructor };
            DFFunctionType funcType = constructor.getFuncType();
            MethodCallNode call = new MethodCallNode(
                graph, scope, ci, funcType, obj, methods);
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
                                graph, scope, call, ci, ref));
                }
            }
            // TODO: catch and forward exceptions.
            // for (DFNode exception : funcType.getExceptions()) {
            //     DFFrame dstFrame = frame.find(DFFrame.CATCHABLE);
            //     graph.addExit(frame, new DFExit(dstFrame, exception));
            // }

        } else if (stmt instanceof SuperConstructorInvocation) {
	    // "super(args)"
            SuperConstructorInvocation sci = (SuperConstructorInvocation)stmt;
            DFNode obj = ctx.get(scope.lookupThis());
	    int nargs = sci.arguments().size();
	    DFNode[] args = new DFNode[nargs];
	    DFType[] argTypes = new DFType[nargs];
	    for (int i = 0; i < nargs; i++) {
		Expression arg = (Expression)sci.arguments().get(i);
                processExpression(
                    ctx, typeSpace, graph, finder, scope, frame, arg);
                DFNode node = ctx.getRValue();
		args[i] = node;
		argTypes[i] = node.getNodeType();
            }
            DFKlass klass = obj.getNodeType().toKlass();
            DFKlass baseKlass = klass.getBaseKlass();
            assert baseKlass != null;
            DFMethod constructor = baseKlass.lookupMethod(
                CallStyle.Constructor, null, argTypes);
            DFMethod methods[] = new DFMethod[] { constructor };
            DFFunctionType funcType = constructor.getFuncType();
            MethodCallNode call = new MethodCallNode(
                graph, scope, sci, funcType, obj, methods);
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
                                graph, scope, call, sci, ref));
                }
            }
            // TODO: catch and forward exceptions.
            // for (DFNode exception : funcType.getExceptions()) {
            //     DFFrame dstFrame = frame.find(DFFrame.CATCHABLE);
            //     graph.addExit(frame, new DFExit(dstFrame, exception));
            // }

        } else if (stmt instanceof TypeDeclarationStatement) {
	    // "class K { ... }"
            // Inline classes are processed separately.

        } else {
            throw new InvalidSyntax(stmt);
        }
    }

    private void processInitializer(
        DFContext ctx, DFTypeSpace typeSpace, MethodGraph graph,
        DFTypeFinder finder, DFLocalScope scope, DFFrame frame,
	Initializer initializer)
        throws InvalidSyntax, EntityNotFound {
        DFLocalScope innerScope = scope.getChildByAST(initializer);
        processStatement(
            ctx, typeSpace, graph, finder, innerScope, frame,
            initializer.getBody());
    }

    // endBreaks: ends a BREAKABLE Frame.
    private void endBreaks(
        DFContext ctx, MethodGraph graph, DFFrame outerFrame,
        DFFrame endFrame) {
        // endFrame.getLabel() can be either @BREAKABLE or a label.
        ConsistentHashMap<DFRef, List<DFExit>> ref2exits =
            new ConsistentHashMap<DFRef, List<DFExit>>();
        for (DFExit exit : graph.getExits(endFrame)) {
            if (exit.getFrame() != endFrame) {
                // Pass through the outer frame.
                graph.addExit(outerFrame, exit);
            } else if (exit instanceof BreakExit) {
                DFNode src = exit.getNode();
                DFRef ref = src.getRef();
                List<DFExit> a = ref2exits.get(ref);
                if (a == null) {
                    a = new ArrayList<DFExit>();
                    ref2exits.put(ref, a);
                }
                a.add(exit);
            }
        }
        for (DFRef ref : ref2exits.keys()) {
            List<DFExit> a = ref2exits.get(ref);
            Collections.sort(a);
            for (DFExit exit : a) {
                DFNode src = exit.getNode();
                DFNode dst = ctx.getLast(ref);
                if (dst == null) {
                    ctx.set(src);
                } else if (src == dst) {
                    ;
                } else if (dst.canMerge()) {
                    dst.merge(src);
                } else if (src.canMerge()) {
                    src.merge(dst);
                    ctx.set(src);
                } else {
                    Logger.error("DFMethod.endBreaks: cannot merge:", ref, dst, src);
                    //assert false;
                }
            }
        }
    }

    private void closeFrame(DFContext ctx, MethodGraph graph) {
        ConsistentHashMap<DFRef, List<DFExit>> ref2exits =
            new ConsistentHashMap<DFRef, List<DFExit>>();
        for (DFExit exit : graph.getExits(_frame)) {
            assert exit.getFrame() == _frame;
            DFNode src = exit.getNode();
            DFRef ref = src.getRef();
            List<DFExit> a = ref2exits.get(ref);
            if (a == null) {
                a = new ArrayList<DFExit>();
                ref2exits.put(ref, a);
            }
            a.add(exit);
        }
        for (DFRef ref : ref2exits.keys()) {
            List<DFExit> a = ref2exits.get(ref);
            Collections.sort(a);
            for (DFExit exit : a) {
                assert (exit instanceof ReturnExit ||
                        exit instanceof ThrowExit);
                DFNode src = exit.getNode();
                DFNode dst = ctx.getLast(ref);
                if (exit instanceof ThrowExit) {
                    DFKlass excKlass = ((ThrowExit)exit).getExcKlass();
                    if (dst == null) {
                        ctx.set(src);
                    } else {
                        CatchJoin join = new CatchJoin(
                            graph, _scope, null, src, excKlass);
                        join.merge(dst);
                        ctx.set(join);
                    }
                } else {
                    if (dst == null) {
                        //Logger.info(" set", src);
                        ctx.set(src);
                    } else if (src == dst) {
                        ;
                    } else if (dst.canMerge()) {
                        dst.merge(src);
                        //Logger.info(" merge", dst, "<-", src);
                    } else if (src.canMerge()) {
                        //Logger.info(" merge", src, "<-", dst);
                        src.merge(dst);
                        ctx.set(src);
                    } else {
                        Logger.error("DFMethod.closeFrame: cannot merge:", ref, dst, src);
                        //assert false;
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void setupMethodDecl(
        MethodGraph graph, DFContext ctx, MethodDeclaration methodDecl)
        throws InvalidSyntax, EntityNotFound {
        int i = 0;
        for (VariableDeclaration decl :
                 (List<VariableDeclaration>)methodDecl.parameters()) {
            DFRef ref = _scope.lookupArgument(i);
            DFNode input = new InputNode(graph, _scope, ref, decl);
            ctx.set(input);
            DFNode assign = new VarAssignNode(
                graph, _scope, _scope.lookupVar(decl.getName()), decl);
            assign.accept(input);
            ctx.set(assign);
            i++;
        }
    }

    @SuppressWarnings("unchecked")
    private void setupLambda(
        MethodGraph graph, DFContext ctx, LambdaExpression lambda)
        throws InvalidSyntax, EntityNotFound {
        int i = 0;
        for (VariableDeclaration decl :
                 (List<VariableDeclaration>)lambda.parameters()) {
            DFRef ref = _scope.lookupArgument(i);
            DFNode input = new InputNode(graph, _scope, ref, decl);
            ctx.set(input);
            DFNode assign = new VarAssignNode(
                graph, _scope, _scope.lookupVar(decl.getName()), decl);
            assign.accept(input);
            ctx.set(assign);
            i++;
        }
    }

    @SuppressWarnings("unchecked")
    public DFGraph processKlassBody(Counter counter)
        throws InvalidSyntax, EntityNotFound {
        // lookup base/inner klasses.
        assert _ast != null;
        List<BodyDeclaration> decls;
        if (_ast instanceof AbstractTypeDeclaration) {
            decls = ((AbstractTypeDeclaration)_ast).bodyDeclarations();
        } else if (_ast instanceof ClassInstanceCreation) {
	    ClassInstanceCreation cstr = (ClassInstanceCreation)_ast;
            decls = cstr.getAnonymousClassDeclaration().bodyDeclarations();
        } else {
            throw new InvalidSyntax(_ast);
        }

        assert _scope != null;
        assert _frame != null;
        assert _finder != null;
        MethodGraph graph = new MethodGraph("K"+counter.getNewId()+"_"+_name);
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
        this.closeFrame(ctx, graph);

        graph.cleanup(null);
        return graph;
    }

    /**
     * Performs dataflow analysis for a given method.
     */
    @SuppressWarnings("unchecked")
    public DFGraph processMethod(Counter counter)
        throws InvalidSyntax, EntityNotFound {
        if (_ast == null) return null;

        assert _scope != null;
        assert _frame != null;
        assert _finder != null;
        MethodGraph graph = new MethodGraph("M"+counter.getNewId()+"_"+_name);
        DFContext ctx = new DFContext(graph, _scope);
        ASTNode body;

        if (_ast instanceof MethodDeclaration) {
            MethodDeclaration methodDecl = (MethodDeclaration)_ast;
            body = methodDecl.getBody();
            if (body == null) return null;
            this.setupMethodDecl(graph, ctx, methodDecl);
        } else if (_ast instanceof LambdaExpression) {
            LambdaExpression lambda = (LambdaExpression)_ast;
            body = lambda.getBody();
            this.setupLambda(graph, ctx, lambda);
        } else {
            throw new InvalidSyntax(_ast);
        }

        // Create input nodes.
        ConsistentHashSet<DFNode> preserved = new ConsistentHashSet<DFNode>();
        {
            DFRef ref = _scope.lookupThis();
            DFNode input = new InputNode(graph, _scope, ref, null);
            ctx.set(input);
            preserved.add(input);
        }
        for (DFRef ref : _frame.getInputRefs()) {
            if (ref.isLocal()) continue;
            if (ref instanceof MethodScope.InternalRef) continue;
            DFNode input = new InputNode(graph, _scope, ref, null);
            ctx.set(input);
            preserved.add(input);
        }

        try {
            if (body instanceof Statement) {
                processStatement(
                    ctx, this, graph, _finder, _scope, _frame,
                    (Statement)body);
            } else if (body instanceof Expression) {
                processExpression(
                    ctx, this, graph, _finder, _scope, _frame,
                    (Expression)body);
                DFRef ref = _scope.lookupReturn();
                ReturnNode ret = new ReturnNode(graph, _scope, ref, body);
                ret.accept(ctx.getRValue());
                graph.addExit(_frame, new ReturnExit(_frame, ret));
            }
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
        this.closeFrame(ctx, graph);

        // Create output nodes.
        {
            DFRef ref = _scope.lookupReturn();
            if (ctx.getLast(ref) != null) {
                DFNode output = new OutputNode(graph, _scope, ref, null);
                output.accept(ctx.getLast(ref));
                preserved.add(output);
            }
        }
        for (DFRef ref : _scope.getExcRefs()) {
            if (ctx.getLast(ref) != null) {
                DFNode output = new OutputNode(graph, _scope, ref, null);
                output.accept(ctx.getLast(ref));
                preserved.add(output);
            }
        }
        for (DFRef ref : _frame.getOutputRefs()) {
            if (ref.isLocal() &&
                !(ref instanceof MethodScope.InternalRef)) continue;
            DFNode output = new OutputNode(graph, _scope, ref, null);
            output.accept(ctx.get(ref));
            preserved.add(output);
        }

        // Do not remove input/output nodes.
        graph.cleanup(preserved);
        return graph;
    }

    private class MethodGraph implements DFGraph {

	private String _graphId;

	private List<DFNode> _nodes =
	    new ArrayList<DFNode>();
        private Map<DFFrame, List<DFExit>> _exits =
            new HashMap<DFFrame, List<DFExit>>();

	public MethodGraph(String graphId) {
	    _graphId = graphId;
	}

	public String getGraphId() {
	    return _graphId;
	}

	public int addNode(DFNode node) {
	    _nodes.add(node);
	    return _nodes.size();
	}

        public void addExit(DFFrame frame, DFExit exit) {
            //Logger.info("DFFrame.addExit:", frame, ":", exit);
            List<DFExit> a = _exits.get(frame);
            if (a == null) {
                a = new ArrayList<DFExit>();
                _exits.put(frame, a);
            }
            a.add(exit);
        }

        public List<DFExit> getExits(DFFrame frame) {
            List<DFExit> a = _exits.get(frame);
            if (a == null) {
                a = new ArrayList<DFExit>();
                _exits.put(frame, a);
            }
            return a;
        }

	public void cleanup(Set<DFNode> preserved) {
	    Set<DFNode> toremove = new HashSet<DFNode>();
	    while (true) {
		boolean changed = false;
		for (DFNode node : _nodes) {
		    if (preserved != null && preserved.contains(node)) continue;
		    if (toremove.contains(node)) continue;
		    if (node.purge()) {
			toremove.add(node);
			changed = true;
		    }
		}
		if (!changed) break;
	    }
	    for (DFNode node : toremove) {
		_nodes.remove(node);
	    }
            Collections.sort(_nodes);
	}

	public void writeXML(XMLStreamWriter writer)
	    throws XMLStreamException {
	    DFMethod method = DFMethod.this;
	    writer.writeStartElement("method");
	    writer.writeAttribute("name", method.getSignature());
	    writer.writeAttribute("style", method.getCallStyle().toString());
	    writer.writeAttribute("abstract", Boolean.toString(method.isAbstract()));
	    for (DFMethod caller : method.getCallers()) {
		writer.writeStartElement("caller");
		writer.writeAttribute("name", caller.getSignature());
		writer.writeEndElement();
	    }
	    for (DFMethod overrider : method.getOverriders()) {
		if (overrider == method) continue;
		writer.writeStartElement("overrider");
		writer.writeAttribute("name", overrider.getSignature());
		writer.writeEndElement();
	    }
	    for (DFMethod overriding : method.getOverridings()) {
		writer.writeStartElement("overriding");
		writer.writeAttribute("name", overriding.getSignature());
		writer.writeEndElement();
	    }
	    if (method._ast != null) {
		Utils.writeXML(writer, method.getTree());
	    }
	    DFNode[] nodes = new DFNode[_nodes.size()];
	    _nodes.toArray(nodes);
	    method.getScope().writeXML(writer, nodes);
	    writer.writeEndElement();
	}
    }

    private class MethodScope extends DFLocalScope {

        private InternalRef _return = null;
        private InternalRef[] _arguments = null;
        private ConsistentHashMap<DFType, DFRef> _exceptions =
            new ConsistentHashMap<DFType, DFRef>();

        protected MethodScope(DFVarScope outer, String name) {
            super(outer, name);
        }

	public boolean isDefined() {
	    return _return != null && _arguments != null;
	}

        @Override
        public DFRef lookupArgument(int index)
            throws VariableNotFound {
	    assert _arguments != null;
            return _arguments[index];
        }

        public DFRef lookupReturn() {
	    assert _return != null;
            return _return;
        }

        public DFRef lookupException(DFType type) {
            DFRef ref = _exceptions.get(type);
            if (ref == null) {
                ref = new InternalRef(type, type.getTypeName());
                _exceptions.put(type, ref);
            }
            return ref;
        }

        public List<DFRef> getExcRefs() {
            return _exceptions.values();
        }

        private void buildInternalRefs(List<VariableDeclaration> parameters) {
	    DFFunctionType funcType = DFMethod.this.getFuncType();
	    DFType[] argTypes = funcType.getArgTypes();
            _return = new InternalRef(funcType.getReturnType(), "return");
            _arguments = new InternalRef[parameters.size()];
            int i = 0;
            for (VariableDeclaration decl : parameters) {
                DFType argType = argTypes[i];
                int ndims = decl.getExtraDimensions();
                if (ndims != 0) {
                    argType = DFArrayType.getType(argType, ndims);
                }
                _arguments[i] = new InternalRef(argType, "arg"+i);
                this.addVar(decl.getName(), argType);
                i++;
            }
        }

        /**
         * Lists all the variables defined inside a method.
         */
        @SuppressWarnings("unchecked")
        public void buildMethodDecl(
            DFTypeFinder finder, MethodDeclaration methodDecl)
            throws InvalidSyntax {
            //Logger.info("MethodScope.buildMethodDecl:", this);
            Statement stmt = methodDecl.getBody();
            if (stmt == null) return;
            this.buildInternalRefs(methodDecl.parameters());
            this.buildStmt(finder, stmt);
        }

        @SuppressWarnings("unchecked")
        public void buildLambda(
            DFTypeFinder finder, LambdaExpression lambda)
            throws InvalidSyntax {
            //Logger.info("MethodScope.buildLambda:", this);
            ASTNode body = lambda.getBody();
            this.buildInternalRefs(lambda.parameters());
            if (body instanceof Statement) {
                this.buildStmt(finder, (Statement)body);
            } else if (body instanceof Expression) {
                this.buildExpr(finder, (Expression)body);
            } else {
                throw new InvalidSyntax(body);
            }
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

        // Special references that are used in a method.
        // (not a real variable.)
        private class InternalRef extends DFRef {

            private String _name;

            public InternalRef(DFType type, String name) {
                super(type);
                _name = name;
            }

            @Override
            public boolean isLocal() {
                return true;
            }

            @Override
            public String getFullName() {
                return "#"+_name;
            }
        }
    }
}
