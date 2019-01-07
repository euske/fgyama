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
    private SortedSet<DFVarRef> _inputRefs =
        new TreeSet<DFVarRef>();
    private SortedSet<DFVarRef> _outputRefs =
        new TreeSet<DFVarRef>();
    private List<DFExit> _exits =
        new ArrayList<DFExit>();

    private List<DFNode> _inputNodes =
        new ArrayList<DFNode>();
    private List<DFNode> _outputNodes =
        new ArrayList<DFNode>();

    public static final String ANONYMOUS = "@ANONYMOUS";
    public static final String BREAKABLE = "@BREAKABLE";
    public static final String CATCHABLE = "@CATCHABLE";
    public static final String RETURNABLE = "@RETURNABLE";

    public DFFrame(String label) {
        this(label, null);
    }

    public DFFrame(String label, DFFrame parent) {
        assert label != null;
        _label = label;
        _parent = parent;
    }

    @Override
    public String toString() {
        return ("<DFFrame("+_label+")>");
    }

    private DFFrame addChild(String label, ASTNode ast) {
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
        assert _ast2child.containsKey(key);
        return _ast2child.get(key);
    }

    public DFFrame find(String label) {
        DFFrame frame = this;
        while (frame != null) {
            if (frame.getLabel().equals(label)) break;
            frame = frame.getParent();
        }
        return frame;
    }

    public void addInputRef(DFVarRef ref) {
        _inputRefs.add(ref);
    }

    public void addOutputRef(DFVarRef ref) {
        _outputRefs.add(ref);
    }

    public boolean expandRefs(DFFrame childFrame) {
        boolean added = false;
        for (DFVarRef ref : childFrame._inputRefs) {
            if (!_inputRefs.contains(ref)) {
                _inputRefs.add(ref);
                added = true;
            }
        }
        for (DFVarRef ref : childFrame._outputRefs) {
            if (!_outputRefs.contains(ref)) {
                _outputRefs.add(ref);
                added = true;
            }
        }
        return added;
    }

    private void removeRefs(DFVarScope childScope) {
        for (DFVarRef ref : childScope.getRefs()) {
            _inputRefs.remove(ref);
            _outputRefs.remove(ref);
        }
    }

    public DFVarRef[] getInputRefs() {
        DFVarRef[] refs = new DFVarRef[_inputRefs.size()];
        _inputRefs.toArray(refs);
        return refs;
    }

    public DFVarRef[] getOutputRefs() {
        DFVarRef[] refs = new DFVarRef[_outputRefs.size()];
        _outputRefs.toArray(refs);
        return refs;
    }

    public DFVarRef[] getInsAndOuts() {
        SortedSet<DFVarRef> inouts = new TreeSet<DFVarRef>(_inputRefs);
        inouts.retainAll(_outputRefs);
        DFVarRef[] refs = new DFVarRef[inouts.size()];
        inouts.toArray(refs);
        return refs;
    }

    public DFExit[] getExits() {
        DFExit[] exits = new DFExit[_exits.size()];
        _exits.toArray(exits);
        return exits;
    }

    public void addExit(DFExit exit) {
        //Logger.info("DFFrame.addExit: "+this+": "+exit);
        _exits.add(exit);
    }

    public void close(DFContext ctx) {
        for (DFExit exit : _exits) {
            if (exit.getFrame() == this) {
                //Logger.info("DFFrame.Exit: "+this+": "+exit);
                DFNode node = exit.getNode();
                node.close(ctx.get(node.getRef()));
                ctx.set(node);
            }
        }
        for (DFVarRef ref : _inputRefs) {
            DFNode node = ctx.getFirst(ref);
            assert node != null;
            _inputNodes.add(node);
        }
        for (DFVarRef ref : _outputRefs) {
            DFNode node = ctx.get(ref);
            assert node != null;
            _outputNodes.add(node);
        }
    }

    public DFNode[] getInputNodes() {
        DFNode[] nodes = new DFNode[_inputNodes.size()];
        _inputNodes.toArray(nodes);
        return nodes;
    }

    public DFNode[] getOutputNodes() {
        DFNode[] nodes = new DFNode[_outputNodes.size()];
        _outputNodes.toArray(nodes);
        return nodes;
    }

    @SuppressWarnings("unchecked")
    public void build(
        DFTypeFinder finder, DFMethod method, DFVarScope scope,
        MethodDeclaration methodDecl)
        throws UnsupportedSyntax, EntityNotFound {
        int i = 0;
        for (SingleVariableDeclaration decl :
                 (List<SingleVariableDeclaration>) methodDecl.parameters()) {
            // XXX Ignore modifiers and dimensions.
            DFVarRef ref = scope.lookupArgument(i);
            this.addInputRef(ref);
            i++;
        }
        this.build(finder, method, scope, methodDecl.getBody());
    }

    public void build(
        DFTypeFinder finder, DFMethod method, DFVarScope scope,
        Initializer initializer)
        throws UnsupportedSyntax, EntityNotFound {
        this.build(finder, method, scope, initializer.getBody());
    }

    @SuppressWarnings("unchecked")
    private void build(
        DFTypeFinder finder, DFMethod method, DFVarScope scope,
        Statement stmt)
        throws UnsupportedSyntax, EntityNotFound {
        assert stmt != null;

        if (stmt instanceof AssertStatement) {

        } else if (stmt instanceof Block) {
            DFVarScope childScope = scope.getChildByAST(stmt);
            Block block = (Block)stmt;
            for (Statement cstmt :
                     (List<Statement>) block.statements()) {
                this.build(finder, method, childScope, cstmt);
            }
            this.removeRefs(childScope);

        } else if (stmt instanceof EmptyStatement) {

        } else if (stmt instanceof VariableDeclarationStatement) {
            VariableDeclarationStatement varStmt =
                (VariableDeclarationStatement)stmt;
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>) varStmt.fragments()) {
                DFVarRef ref = scope.lookupVar(frag.getName());
                this.addOutputRef(ref);
                Expression init = frag.getInitializer();
                if (init != null) {
                    this.build(finder, method, scope, init);
                }
            }

        } else if (stmt instanceof ExpressionStatement) {
            ExpressionStatement exprStmt = (ExpressionStatement)stmt;
            this.build(finder, method, scope, exprStmt.getExpression());

        } else if (stmt instanceof IfStatement) {
            IfStatement ifStmt = (IfStatement)stmt;
            this.build(finder, method, scope, ifStmt.getExpression());
            Statement thenStmt = ifStmt.getThenStatement();
            DFFrame thenFrame = this.addChild(DFFrame.ANONYMOUS, thenStmt);
            thenFrame.build(finder, method, scope, thenStmt);
            this.expandRefs(thenFrame);
            Statement elseStmt = ifStmt.getElseStatement();
            if (elseStmt != null) {
                DFFrame elseFrame = this.addChild(DFFrame.ANONYMOUS, elseStmt);
                elseFrame.build(finder, method, scope, elseStmt);
                this.expandRefs(elseFrame);
            }

        } else if (stmt instanceof SwitchStatement) {
            SwitchStatement switchStmt = (SwitchStatement)stmt;
            DFType type = this.build(finder, method, scope, switchStmt.getExpression());
            DFKlass enumKlass = null;
            if (type instanceof DFKlass &&
                ((DFKlass)type).isEnum()) {
                enumKlass = finder.resolveKlass(type);
            }
            DFVarScope childScope = scope.getChildByAST(stmt);
            DFFrame childFrame = this.addChild(DFFrame.BREAKABLE, stmt);
            for (Statement cstmt :
                     (List<Statement>) switchStmt.statements()) {
                if (cstmt instanceof SwitchCase) {
                    SwitchCase switchCase = (SwitchCase)cstmt;
                    Expression expr = switchCase.getExpression();
                    if (expr != null) {
                        if (enumKlass != null && expr instanceof SimpleName) {
                            // special treatment for enum.
                            DFVarRef ref = enumKlass.lookupField((SimpleName)expr);
                            this.addInputRef(ref);
                        } else {
                            childFrame.build(finder, method, childScope, expr);
                        }
                    }
                } else {
                    childFrame.build(finder, method, childScope, cstmt);
                }
            }
            childFrame.removeRefs(childScope);
            this.expandRefs(childFrame);

        } else if (stmt instanceof WhileStatement) {
            WhileStatement whileStmt = (WhileStatement)stmt;
            DFVarScope childScope = scope.getChildByAST(stmt);
            DFFrame childFrame = this.addChild(DFFrame.BREAKABLE, stmt);
            childFrame.build(finder, method, scope, whileStmt.getExpression());
            childFrame.build(finder, method, childScope, whileStmt.getBody());
            childFrame.removeRefs(childScope);
            this.expandRefs(childFrame);

        } else if (stmt instanceof DoStatement) {
            DoStatement doStmt = (DoStatement)stmt;
            DFVarScope childScope = scope.getChildByAST(stmt);
            DFFrame childFrame = this.addChild(DFFrame.BREAKABLE, stmt);
            childFrame.build(finder, method, childScope, doStmt.getBody());
            childFrame.build(finder, method, scope, doStmt.getExpression());
            childFrame.removeRefs(childScope);
            this.expandRefs(childFrame);

        } else if (stmt instanceof ForStatement) {
            ForStatement forStmt = (ForStatement)stmt;
            DFVarScope childScope = scope.getChildByAST(stmt);
            for (Expression init : (List<Expression>) forStmt.initializers()) {
                this.build(finder, method, childScope, init);
            }
            DFFrame childFrame = this.addChild(DFFrame.BREAKABLE, stmt);
            Expression expr = forStmt.getExpression();
            if (expr != null) {
                childFrame.build(finder, method, childScope, expr);
            }
            childFrame.build(finder, method, childScope, forStmt.getBody());
            for (Expression update : (List<Expression>) forStmt.updaters()) {
                childFrame.build(finder, method, childScope, update);
            }
            childFrame.removeRefs(childScope);
            this.expandRefs(childFrame);

        } else if (stmt instanceof EnhancedForStatement) {
            EnhancedForStatement eForStmt = (EnhancedForStatement)stmt;
            this.build(finder, method, scope, eForStmt.getExpression());
            DFVarScope childScope = scope.getChildByAST(stmt);
            DFFrame childFrame = this.addChild(DFFrame.BREAKABLE, stmt);
            childFrame.build(finder, method, childScope, eForStmt.getBody());
            childFrame.removeRefs(childScope);
            this.expandRefs(childFrame);

        } else if (stmt instanceof ReturnStatement) {
            ReturnStatement rtrnStmt = (ReturnStatement)stmt;
            Expression expr = rtrnStmt.getExpression();
            if (expr != null) {
                this.build(finder, method, scope, expr);
            }
            this.addOutputRef(scope.lookupReturn());

        } else if (stmt instanceof BreakStatement) {

        } else if (stmt instanceof ContinueStatement) {

        } else if (stmt instanceof LabeledStatement) {
            LabeledStatement labeledStmt = (LabeledStatement)stmt;
            SimpleName labelName = labeledStmt.getLabel();
            String label = labelName.getIdentifier();
            DFFrame childFrame = this.addChild(label, stmt);
            childFrame.build(finder, method, scope, labeledStmt.getBody());
            this.expandRefs(childFrame);

        } else if (stmt instanceof SynchronizedStatement) {
            SynchronizedStatement syncStmt = (SynchronizedStatement)stmt;
            this.build(finder, method, scope, syncStmt.getBody());

        } else if (stmt instanceof TryStatement) {
            TryStatement tryStmt = (TryStatement)stmt;
            DFVarScope tryScope = scope.getChildByAST(stmt);
            DFFrame tryFrame = this.addChild(DFFrame.CATCHABLE, stmt);
            tryFrame.build(finder, method, tryScope, tryStmt.getBody());
            tryFrame.removeRefs(tryScope);
            this.expandRefs(tryFrame);
            for (CatchClause cc :
                     (List<CatchClause>) tryStmt.catchClauses()) {
                DFVarScope catchScope = scope.getChildByAST(cc);
                DFFrame catchFrame = this.addChild(DFFrame.ANONYMOUS, cc);
                catchFrame.build(finder, method, catchScope, cc.getBody());
                catchFrame.removeRefs(catchScope);
                this.expandRefs(catchFrame);
            }
            Block finBlock = tryStmt.getFinally();
            if (finBlock != null) {
                this.build(finder, method, scope, finBlock);
            }

        } else if (stmt instanceof ThrowStatement) {
            ThrowStatement throwStmt = (ThrowStatement)stmt;
            this.build(finder, method, scope, throwStmt.getExpression());
            this.addOutputRef(scope.lookupException());

        } else if (stmt instanceof ConstructorInvocation) {
            ConstructorInvocation ci = (ConstructorInvocation)stmt;
            for (Expression arg : (List<Expression>) ci.arguments()) {
                this.build(finder, method, scope, arg);
            }

        } else if (stmt instanceof SuperConstructorInvocation) {
            SuperConstructorInvocation sci = (SuperConstructorInvocation)stmt;
            for (Expression arg : (List<Expression>) sci.arguments()) {
                this.build(finder, method, scope, arg);
            }

        } else if (stmt instanceof TypeDeclarationStatement) {

        } else {
            throw new UnsupportedSyntax(stmt);

        }
    }

    @SuppressWarnings("unchecked")
    private DFType build(
        DFTypeFinder finder, DFMethod method, DFVarScope scope,
        Expression expr)
        throws UnsupportedSyntax, EntityNotFound {
        assert expr != null;

        try {
	    if (expr instanceof Annotation) {
		return null;

	    } else if (expr instanceof Name) {
		Name name = (Name)expr;
		DFVarRef ref;
		if (name.isSimpleName()) {
		    ref = scope.lookupVar((SimpleName)name);
		} else {
		    QualifiedName qname = (QualifiedName)name;
		    DFKlass klass;
		    try {
			// Try assuming it's a variable access.
			DFType type = this.build(finder, method, scope, qname.getQualifier());
			if (type == null) return type;
			klass = finder.resolveKlass(type);
		    } catch (EntityNotFound e) {
			// Turned out it's a class variable.
			klass = finder.lookupKlass(qname.getQualifier());
		    }
		    SimpleName fieldName = qname.getName();
		    ref = klass.lookupField(fieldName);
		}
		this.addInputRef(ref);
		return ref.getRefType();

	    } else if (expr instanceof ThisExpression) {
		ThisExpression thisExpr = (ThisExpression)expr;
		Name name = thisExpr.getQualifier();
		DFVarRef ref;
		if (name != null) {
		    DFKlass klass = finder.lookupKlass(name);
		    ref = klass.getKlassScope().lookupThis();
		} else {
		    ref = scope.lookupThis();
		}
		this.addInputRef(ref);
		return ref.getRefType();

	    } else if (expr instanceof BooleanLiteral) {
		return DFBasicType.BOOLEAN;

	    } else if (expr instanceof CharacterLiteral) {
		return DFBasicType.CHAR;

	    } else if (expr instanceof NullLiteral) {
		return DFNullType.NULL;

	    } else if (expr instanceof NumberLiteral) {
		return DFBasicType.INT;

	    } else if (expr instanceof StringLiteral) {
		return DFBuiltinTypes.getStringKlass();

	    } else if (expr instanceof TypeLiteral) {
		return DFBuiltinTypes.getClassKlass();

	    } else if (expr instanceof PrefixExpression) {
		PrefixExpression prefix = (PrefixExpression)expr;
		PrefixExpression.Operator op = prefix.getOperator();
		Expression operand = prefix.getOperand();
		if (op == PrefixExpression.Operator.INCREMENT ||
		    op == PrefixExpression.Operator.DECREMENT) {
		    this.buildAssignment(finder, method, scope, operand);
		}
		return this.build(finder, method, scope, operand);

	    } else if (expr instanceof PostfixExpression) {
		PostfixExpression postfix = (PostfixExpression)expr;
		PostfixExpression.Operator op = postfix.getOperator();
		Expression operand = postfix.getOperand();
		if (op == PostfixExpression.Operator.INCREMENT ||
		    op == PostfixExpression.Operator.DECREMENT) {
		    this.buildAssignment(finder, method, scope, operand);
		}
		return this.build(finder, method, scope, operand);

	    } else if (expr instanceof InfixExpression) {
		InfixExpression infix = (InfixExpression)expr;
		InfixExpression.Operator op = infix.getOperator();
		DFType left = this.build(finder, method, scope, infix.getLeftOperand());
		DFType right = this.build(finder, method, scope, infix.getRightOperand());
		return DFType.inferInfixType(left, op, right);

	    } else if (expr instanceof ParenthesizedExpression) {
		ParenthesizedExpression paren = (ParenthesizedExpression)expr;
		return this.build(finder, method, scope, paren.getExpression());

	    } else if (expr instanceof Assignment) {
		Assignment assn = (Assignment)expr;
		Assignment.Operator op = assn.getOperator();
		if (op != Assignment.Operator.ASSIGN) {
		    this.build(finder, method, scope, assn.getLeftHandSide());
		}
		this.buildAssignment(finder, method, scope, assn.getLeftHandSide());
		return this.build(finder, method, scope, assn.getRightHandSide());

	    } else if (expr instanceof VariableDeclarationExpression) {
		VariableDeclarationExpression decl =
		    (VariableDeclarationExpression)expr;
		for (VariableDeclarationFragment frag :
			 (List<VariableDeclarationFragment>) decl.fragments()) {
		    DFVarRef ref = scope.lookupVar(frag.getName());
		    this.addOutputRef(ref);
		    Expression init = frag.getInitializer();
		    if (init != null) {
			this.build(finder, method, scope, init);
		    }
		}
		return null; // XXX what do?

	    } else if (expr instanceof MethodInvocation) {
		MethodInvocation invoke = (MethodInvocation)expr;
		Expression expr1 = invoke.getExpression();
		DFKlass klass = null;
		if (expr1 == null) {
		    DFVarRef ref = scope.lookupThis();
		    this.addInputRef(ref);
		    klass = finder.resolveKlass(ref.getRefType());
		} else {
		    if (expr1 instanceof Name) {
			try {
			    klass = finder.lookupKlass((Name)expr1);
			} catch (TypeNotFound e) {
			}
		    }
		    if (klass == null) {
			DFType type = this.build(finder, method, scope, expr1);
			if (type == null) return type;
			klass = finder.resolveKlass(type);
		    }
		}
		List<DFType> typeList = new ArrayList<DFType>();
		for (Expression arg : (List<Expression>) invoke.arguments()) {
		    DFType type = this.build(finder, method, scope, arg);
		    if (type == null) return type;
		    typeList.add(type);
		}
		DFType[] argTypes = new DFType[typeList.size()];
		typeList.toArray(argTypes);
		try {
		    DFMethod method1 = klass.lookupMethod(
			invoke.getName(), argTypes);
		    method1.addCaller(method);
		    return method1.getReturnType();
		} catch (MethodNotFound e) {
		    return null;
		}

	    } else if (expr instanceof SuperMethodInvocation) {
		SuperMethodInvocation sinvoke = (SuperMethodInvocation)expr;
		List<DFType> typeList = new ArrayList<DFType>();
		for (Expression arg : (List<Expression>) sinvoke.arguments()) {
		    DFType type = this.build(finder, method, scope, arg);
		    if (type == null) return type;
		    typeList.add(type);
		}
		DFType[] argTypes = new DFType[typeList.size()];
		typeList.toArray(argTypes);
		DFKlass klass =
		    finder.resolveKlass(scope.lookupThis().getRefType());
		DFKlass baseKlass = klass.getBaseKlass();
		try {
		    DFMethod method1 = baseKlass.lookupMethod(
			sinvoke.getName(), argTypes);
		    method1.addCaller(method);
		    return method1.getReturnType();
		} catch (MethodNotFound e) {
		    return null;
		}

	    } else if (expr instanceof ArrayCreation) {
		ArrayCreation ac = (ArrayCreation)expr;
		for (Expression dim : (List<Expression>) ac.dimensions()) {
		    this.build(finder, method, scope, dim);
		}
		ArrayInitializer init = ac.getInitializer();
		if (init != null) {
		    this.build(finder, method, scope, init);
		}
		return finder.resolve(ac.getType().getElementType());

	    } else if (expr instanceof ArrayInitializer) {
		ArrayInitializer init = (ArrayInitializer)expr;
		DFType type = null;
		for (Expression expr1 : (List<Expression>) init.expressions()) {
		    type = this.build(finder, method, scope, expr1);
		}
		return type;

	    } else if (expr instanceof ArrayAccess) {
		ArrayAccess aa = (ArrayAccess)expr;
		this.build(finder, method, scope, aa.getIndex());
		DFType type = this.build(finder, method, scope, aa.getArray());
		if (type instanceof DFArrayType) {
		    DFVarRef ref = scope.lookupArray(type);
		    this.addInputRef(ref);
		    type = ((DFArrayType)type).getElemType();
		}
		return type;

	    } else if (expr instanceof FieldAccess) {
		FieldAccess fa = (FieldAccess)expr;
		Expression expr1 = fa.getExpression();
		DFKlass klass = null;
		if (expr1 instanceof Name) {
		    try {
			klass = finder.lookupKlass((Name)expr1);
		    } catch (TypeNotFound e) {
		    }
		}
		if (klass == null) {
		    DFType type = this.build(finder, method, scope, expr1);
		    if (type == null) return type;
		    klass = finder.resolveKlass(type);
		}
		SimpleName fieldName = fa.getName();
		DFVarRef ref = klass.lookupField(fieldName);
		this.addInputRef(ref);
		return ref.getRefType();

	    } else if (expr instanceof SuperFieldAccess) {
		SuperFieldAccess sfa = (SuperFieldAccess)expr;
		SimpleName fieldName = sfa.getName();
		DFKlass klass = finder.resolveKlass(
		    scope.lookupThis().getRefType()).getBaseKlass();
		DFVarRef ref = klass.lookupField(fieldName);
		this.addInputRef(ref);
		return ref.getRefType();

	    } else if (expr instanceof CastExpression) {
		CastExpression cast = (CastExpression)expr;
		this.build(finder, method, scope, cast.getExpression());
		return finder.resolve(cast.getType());

	    } else if (expr instanceof ClassInstanceCreation) {
		ClassInstanceCreation cstr = (ClassInstanceCreation)expr;
		AnonymousClassDeclaration anonDecl =
		    cstr.getAnonymousClassDeclaration();
		DFType instType;
		if (anonDecl != null) {
		    String id = Utils.encodeASTNode(anonDecl);
		    DFTypeSpace anonSpace = method.getChildSpace().lookupSpace(id);
		    instType = anonSpace.getKlass(id);
		} else {
		    instType = finder.resolve(cstr.getType());
		}
		Expression expr1 = cstr.getExpression();
		if (expr1 != null) {
		    this.build(finder, method, scope, expr1);
		}
		for (Expression arg : (List<Expression>) cstr.arguments()) {
		    this.build(finder, method, scope, arg);
		}
		return instType;

	    } else if (expr instanceof ConditionalExpression) {
		ConditionalExpression cond = (ConditionalExpression)expr;
		this.build(finder, method, scope, cond.getExpression());
		this.build(finder, method, scope, cond.getThenExpression());
		return this.build(finder, method, scope, cond.getElseExpression());

	    } else if (expr instanceof InstanceofExpression) {
		return DFBasicType.BOOLEAN;

	    } else if (expr instanceof LambdaExpression) {
		LambdaExpression lambda = (LambdaExpression)expr;
		String id = "lambda";
		ASTNode body = lambda.getBody();
		DFTypeSpace anonSpace = new DFTypeSpace(null, id);
		DFKlass klass = finder.resolveKlass(
		    scope.lookupThis().getRefType());
		DFKlass anonKlass = new DFKlass(
		    id, anonSpace, klass, scope,
		    DFBuiltinTypes.getObjectKlass());
		if (body instanceof Statement) {
		    // XXX TODO Statement lambda
		} else if (body instanceof Expression) {
		    // XXX TODO Expresssion lambda
		} else {
		    throw new UnsupportedSyntax(body);
		}
		return anonKlass;

	    } else if (expr instanceof MethodReference) {
		// MethodReference
		//  CreationReference
		//  ExpressionMethodReference
		//  SuperMethodReference
		//  TypeMethodReference
		throw new UnsupportedSyntax(expr);

	    } else {
		// ???
		throw new UnsupportedSyntax(expr);
	    }
        } catch (EntityNotFound e) {
            e.setAst(expr);
            throw e;
        }
    }

    private void buildAssignment(
        DFTypeFinder finder, DFMethod method, DFVarScope scope,
        Expression expr)
        throws UnsupportedSyntax, EntityNotFound {
        assert expr != null;

        if (expr instanceof Name) {
            Name name = (Name)expr;
            DFVarRef ref;
            if (name.isSimpleName()) {
                ref = scope.lookupVar((SimpleName)name);
            } else {
                QualifiedName qname = (QualifiedName)name;
                DFKlass klass;
                try {
                    // Try assuming it's a variable access.
                    DFType type = this.build(finder, method, scope, qname.getQualifier());
                    if (type == null) return;
                    klass = finder.resolveKlass(type);
                } catch (EntityNotFound e) {
                    // Turned out it's a class variable.
                    klass = finder.lookupKlass(qname.getQualifier());
                }
                SimpleName fieldName = qname.getName();
                ref = klass.lookupField(fieldName);
            }
            this.addOutputRef(ref);

        } else if (expr instanceof ArrayAccess) {
            ArrayAccess aa = (ArrayAccess)expr;
            DFType type = this.build(finder, method, scope, aa.getArray());
            this.build(finder, method, scope, aa.getIndex());
            if (type instanceof DFArrayType) {
                DFVarRef ref = scope.lookupArray(type);
                this.addOutputRef(ref);
            }

        } else if (expr instanceof FieldAccess) {
            FieldAccess fa = (FieldAccess)expr;
            Expression expr1 = fa.getExpression();
            DFKlass klass = null;
            if (expr1 instanceof Name) {
                try {
                    klass = finder.lookupKlass((Name)expr1);
                } catch (TypeNotFound e) {
                }
            }
            if (klass == null) {
                DFType type = this.build(finder, method, scope, expr1);
                if (type == null) return;
                klass = finder.resolveKlass(type);
            }
            SimpleName fieldName = fa.getName();
            DFVarRef ref = klass.lookupField(fieldName);
            this.addOutputRef(ref);

        } else if (expr instanceof SuperFieldAccess) {
            SuperFieldAccess sfa = (SuperFieldAccess)expr;
            SimpleName fieldName = sfa.getName();
            DFKlass klass = finder.resolveKlass(
                scope.lookupThis().getRefType()).getBaseKlass();
            DFVarRef ref = klass.lookupField(fieldName);
            this.addOutputRef(ref);

        } else {
            throw new UnsupportedSyntax(expr);
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
        for (DFVarRef ref : this.getInputRefs()) {
            inputs.append(" "+ref);
        }
        out.println(i2+"inputs:"+inputs);
        StringBuilder outputs = new StringBuilder();
        for (DFVarRef ref : this.getOutputRefs()) {
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
