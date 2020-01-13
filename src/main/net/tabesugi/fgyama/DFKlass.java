//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import java.util.jar.*;
import javax.xml.stream.*;
import org.apache.bcel.*;
import org.apache.bcel.classfile.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFKlass
//
public class DFKlass extends DFTypeSpace implements DFType {

    private enum LoadState {
	Unloaded,
	Loading,
	Loaded,
    };

    // These fields are available upon construction.
    private String _name;
    private DFTypeSpace _outerSpace;
    private DFKlass _outerKlass; // can be the same as outerSpace, or null.
    private DFVarScope _outerScope;
    private KlassScope _klassScope;

    // These fields are set immediately after construction.
    private ASTNode _ast = null;
    private String _filePath = null;
    private String _jarPath = null;
    private String _entPath = null;

    // These fields are available after setMapTypes(). (Stage1)
    private ConsistentHashMap<String, DFMapType> _mapTypes = null;
    private ConsistentHashMap<String, DFKlass> _concreteKlasses = null;

    // This field is available after setFinder(). (Stage2)
    private DFTypeFinder _finder = null;

    // The following fields are available after the klass is loaded. (Stage3)
    private LoadState _state = LoadState.Unloaded;
    private boolean _interface = false;
    private DFKlass _baseKlass = null;
    private DFKlass[] _baseIfaces = null;
    private DFMethod _initMethod = null;

    private List<FieldRef> _fields = null;
    private List<DFMethod> _methods = null;
    private Map<String, DFMethod> _id2method = null;

    // These fields are available only for parameterized klasses.
    private DFKlass _genericKlass = null;
    private ConsistentHashMap<String, DFType> _paramTypes = null;

    public DFKlass(
        String name, DFTypeSpace outerSpace,
        DFKlass outerKlass, DFVarScope outerScope,
        DFKlass baseKlass) {
	super(name, outerSpace);
        _name = name;
        _outerSpace = outerSpace;
        _outerKlass = outerKlass;
	_outerScope = outerScope;
        // Set temporary baseKlass until this is fully loaded.
        _baseKlass = baseKlass;
    }

    @Override
    public String toString() {
        if (_mapTypes != null) {
            String name = "L"+_outerSpace.getSpaceName()+_name;
            return ("<DFKlass("+name+":"+Utils.join(_mapTypes.keys())+")>");
        }
        return ("<DFKlass("+this.getTypeName()+")>");
    }

    @Override
    public boolean equals(DFType type) {
        return (this == type);
    }

    // Set the klass AST from a source code.
    public void setKlassTree(String filePath, ASTNode ast)
	throws InvalidSyntax {
        _filePath = filePath;
        _ast = ast;
    }

    // Set the klass code from a JAR.
    public void setJarPath(String jarPath, String entPath) {
        _jarPath = jarPath;
        _entPath = entPath;
    }

    public boolean isGeneric() {
        return _mapTypes != null;
    }

    // Set the map types from a source code.
    @SuppressWarnings("unchecked")
    public void setMapTypes(List<TypeParameter> tps) {
        assert _mapTypes == null;
        assert _paramTypes == null;
        assert _concreteKlasses == null;
	DFMapType[] mapTypes = this.getMapTypes(tps);
	if (mapTypes == null) return;
	_mapTypes = new ConsistentHashMap<String, DFMapType>();
	for (DFMapType mapType : mapTypes) {
	    _mapTypes.put(mapType.getName(), mapType);
        }
        _concreteKlasses = new ConsistentHashMap<String, DFKlass>();
    }

    // Set the map types from a JAR.
    public void setMapTypes(String sig) {
        assert _mapTypes == null;
        assert _paramTypes == null;
        assert _concreteKlasses == null;
        DFMapType[] mapTypes = JNITypeParser.getMapTypes(sig, this);
	if (mapTypes == null) return;
	_mapTypes = new ConsistentHashMap<String, DFMapType>();
	for (DFMapType mapType : mapTypes) {
	    _mapTypes.put(mapType.getName(), mapType);
        }
        _concreteKlasses = new ConsistentHashMap<String, DFKlass>();
    }

    // Creates a parameterized klass.
    public DFKlass parameterize(DFType[] paramTypes)
	throws InvalidSyntax {
        //Logger.info("DFKlass.parameterize:", this, Utils.join(paramTypes));
        assert _mapTypes != null;
        assert _paramTypes == null;
        assert paramTypes.length <= _mapTypes.size();
        if (paramTypes.length < _mapTypes.size()) {
	    List<DFMapType> mapTypes = _mapTypes.values();
            DFType[] types = new DFType[mapTypes.size()];
            for (int i = 0; i < mapTypes.size(); i++) {
                if (i < paramTypes.length) {
                    types[i] = paramTypes[i];
                } else {
                    types[i] = mapTypes.get(i).toKlass();
                }
            }
            paramTypes = types;
        }
        String name = getParamName(paramTypes);
        DFKlass klass = _concreteKlasses.get(name);
        if (klass == null) {
            klass = new DFKlass(this, paramTypes);
            _concreteKlasses.put(name, klass);
        }
        return klass;
    }

    // Constructor for a parameterized klass.
    @SuppressWarnings("unchecked")
    private DFKlass(
        DFKlass genericKlass, DFType[] paramTypes)
	throws InvalidSyntax {
	super(genericKlass._name + getParamName(paramTypes), genericKlass._outerSpace);
        assert genericKlass != null;
        assert paramTypes != null;
        // A parameterized Klass is NOT accessible from
        // the outer namespace but it creates its own subspace.
        _name = genericKlass._name + getParamName(paramTypes);
        _outerSpace = genericKlass._outerSpace;
        _outerKlass = genericKlass._outerKlass;
        _outerScope = genericKlass._outerScope;
        _baseKlass = genericKlass._baseKlass;

        _genericKlass = genericKlass;
        _paramTypes = new ConsistentHashMap<String, DFType>();
	List<DFMapType> mapTypes = genericKlass._mapTypes.values();
        for (int i = 0; i < paramTypes.length; i++) {
            DFMapType mapType = mapTypes.get(i);
            DFType paramType = paramTypes[i];
            assert mapType != null;
            assert paramType != null;
            _paramTypes.put(mapType.getName(), paramType);
        }

        _ast = genericKlass._ast;
        _filePath = genericKlass._filePath;
        _jarPath = genericKlass._jarPath;
        _entPath = genericKlass._entPath;

        _finder = genericKlass._finder;
        // Recreate the entire subspace.
	if (_ast != null) {
            this.initBuild();
	    this.buildTypeFromDecls(_ast);
	} else {
            // In case of a .jar class, refer to the same inner classes.
	    for (DFKlass klass : genericKlass.getInnerKlasses()) {
		this.addKlass(klass._name, klass);
	    }
        }

        // not loaded yet!
        assert _state == LoadState.Unloaded;
    }

    public void writeXML(XMLStreamWriter writer)
        throws XMLStreamException {
        writer.writeStartElement("class");
        writer.writeAttribute("path", this.getFilePath());
        writer.writeAttribute("name", this.getTypeName());
        writer.writeAttribute("interface", Boolean.toString(_interface));
        if (_baseKlass != null) {
            writer.writeAttribute("extends", _baseKlass.getTypeName());
        }
        if (_baseIfaces != null && 0 < _baseIfaces.length) {
            StringBuilder b = new StringBuilder();
            for (DFKlass iface : _baseIfaces) {
                if (0 < b.length()) {
                    b.append(" ");
                }
                b.append(iface.getTypeName());
            }
            writer.writeAttribute("implements", b.toString());
        }
        if (_genericKlass != null) {
            writer.writeAttribute("generic", _genericKlass.getTypeName());
            if (_paramTypes != null) {
		List<DFMapType> mapTypes = _genericKlass._mapTypes.values();
		List<DFType> paramTypes = _paramTypes.values();
                for (int i = 0; i < paramTypes.size(); i++) {
		    DFMapType mapType = mapTypes.get(i);
                    DFType paramType = paramTypes.get(i);
                    writer.writeStartElement("param");
                    writer.writeAttribute("name", mapType.getName());
                    writer.writeAttribute("type", paramType.getTypeName());
                    writer.writeEndElement();
                }
            }
        }
        if (_concreteKlasses != null) {
            for (DFKlass pklass : _concreteKlasses.values()) {
                writer.writeStartElement("parameterized");
                writer.writeAttribute("type", pklass.getTypeName());
                writer.writeEndElement();
            }
        }
        for (FieldRef field : _fields) {
            field.writeXML(writer);
        }
        //writer.writeEndElement();
    }

    @Override
    public String getTypeName() {
        return "L"+_outerSpace.getSpaceName()+_name+";";
    }

    @Override
    public DFKlass toKlass() {
        return this;
    }

    @Override
    public int canConvertFrom(DFType type, Map<DFMapType, DFType> typeMap) {
        if (type instanceof DFNullType) return 0;
        DFKlass klass = type.toKlass();
        if (klass == null) return -1;
        // type is-a this.
        return klass.isSubclassOf(this, typeMap);
    }

    public int isSubclassOf(DFKlass klass, Map<DFMapType, DFType> typeMap) {
        if (this == klass) return 0;
        if (_genericKlass == klass || klass._genericKlass == this ||
            (_genericKlass != null && _genericKlass == klass._genericKlass)) {
            // A<T> isSubclassOf B<S>?
            // types0: T
            List<DFType> types0 = (_mapTypes != null)?
		getMappedTypes(_mapTypes.values()) : _paramTypes.values();
            assert types0 != null;
            // types1: S
            List<DFType> types1 = (klass._mapTypes != null)?
		getMappedTypes(klass._mapTypes.values()) : klass._paramTypes.values();
            assert types1 != null;
            //assert types0.length == types1.length;
            // T isSubclassOf S? -> S canConvertFrom T?
            int dist = 0;
            for (int i = 0; i < Math.min(types0.size(), types1.size()); i++) {
                int d = types1.get(i).canConvertFrom(types0.get(i), typeMap);
                if (d < 0) return -1;
                dist += d;
            }
            return dist;
        }
        if (_baseKlass != null) {
            int dist = _baseKlass.isSubclassOf(klass, typeMap);
            if (0 <= dist) return dist+1;
        }
        if (_baseIfaces != null) {
            for (DFKlass iface : _baseIfaces) {
                int dist = iface.isSubclassOf(klass, typeMap);
                if (0 <= dist) return dist+1;
            }
        }
        return -1;
    }

    protected void initBuild() {
        _klassScope = new KlassScope(_outerScope, _name);
        _fields = new ArrayList<FieldRef>();
        _methods = new ArrayList<DFMethod>();
        _id2method = new HashMap<String, DFMethod>();
    }

    protected void buildTypeManually(
        boolean isInterface, DFKlass baseKlass, DFKlass[] baseIfaces) {
	// Because this instance is created in such an intermediate state,
	// not everything might be defined yet.
        assert _state == LoadState.Unloaded;
        _interface = isInterface;
	_baseKlass = baseKlass;
        _baseIfaces = baseIfaces;
        this.initBuild();
        _state = LoadState.Loaded;
    }

    @SuppressWarnings("unchecked")
    protected void buildTypeFromDecls(ASTNode ast)
	throws InvalidSyntax {

        List<BodyDeclaration> decls;
        if (ast instanceof AbstractTypeDeclaration) {
            decls = ((AbstractTypeDeclaration)ast).bodyDeclarations();
        } else if (ast instanceof ClassInstanceCreation) {
	    ClassInstanceCreation cstr = (ClassInstanceCreation)ast;
            decls = cstr.getAnonymousClassDeclaration().bodyDeclarations();
        } else {
            throw new InvalidSyntax(ast);
        }

        assert _fields != null;
        assert _methods != null;
        assert _id2method != null;

        _initMethod = new DFMethod(
            this, DFMethod.CallStyle.Initializer, false,
            "<clinit>", "<clinit>", _klassScope);
        _initMethod.setTree(ast);
        _initMethod.buildFuncType(this);

        for (BodyDeclaration body : decls) {
	    if (body instanceof AbstractTypeDeclaration) {
                AbstractTypeDeclaration abstTypeDecl = (AbstractTypeDeclaration)body;
                String path = this.getFilePath();
		DFKlass klass = this.buildTypeFromAST(
                    path, abstTypeDecl, this, _klassScope);
                klass.setKlassTree(path, abstTypeDecl);

	    } else if (body instanceof FieldDeclaration) {
                FieldDeclaration fieldDecl = (FieldDeclaration)body;
                for (VariableDeclarationFragment frag :
                         (List<VariableDeclarationFragment>) fieldDecl.fragments()) {
                    Expression init = frag.getInitializer();
                    if (init != null) {
                        this.buildTypeFromExpr(init, _initMethod, _initMethod.getScope());
                    }
                }

	    } else if (body instanceof MethodDeclaration) {
                MethodDeclaration methodDecl = (MethodDeclaration)body;
                String id = Utils.encodeASTNode(methodDecl);
                String name;
                DFMethod.CallStyle callStyle;
                if (methodDecl.isConstructor()) {
                    name = "<init>";
                    callStyle = DFMethod.CallStyle.Constructor;
                } else {
                    name = methodDecl.getName().getIdentifier();
                    callStyle = (isStatic(methodDecl))?
                        DFMethod.CallStyle.StaticMethod :
                        DFMethod.CallStyle.InstanceMethod;
                }
                Statement stmt = methodDecl.getBody();
                DFMethod method = new DFMethod(
                    this, callStyle, (stmt == null),
		    id, name, _klassScope);
		method.setMapTypes(methodDecl.typeParameters());
                method.setTree(methodDecl);
                this.addMethod(method, id);
                if (stmt != null) {
                    this.buildTypeFromStmt(stmt, method, method.getScope());
                }

	    } else if (body instanceof AnnotationTypeMemberDeclaration) {
		;

	    } else if (body instanceof Initializer) {
		Initializer initializer = (Initializer)body;
                Statement stmt = initializer.getBody();
                if (stmt != null) {
                    this.buildTypeFromStmt(stmt, _initMethod, _initMethod.getScope());
                }

	    } else {
		throw new InvalidSyntax(body);
	    }
	}
    }

    @SuppressWarnings("unchecked")
    protected void buildTypeFromStmt(
        Statement ast,
        DFTypeSpace space, DFLocalScope outerScope)
        throws InvalidSyntax {
        assert ast != null;

        if (ast instanceof AssertStatement) {

        } else if (ast instanceof Block) {
            Block block = (Block)ast;
            DFLocalScope innerScope = outerScope.addChild(ast);
            for (Statement stmt :
                     (List<Statement>) block.statements()) {
                this.buildTypeFromStmt(stmt, space, innerScope);
            }

        } else if (ast instanceof EmptyStatement) {

        } else if (ast instanceof VariableDeclarationStatement) {
            VariableDeclarationStatement varStmt =
                (VariableDeclarationStatement)ast;
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>) varStmt.fragments()) {
                Expression expr = frag.getInitializer();
                if (expr != null) {
                    this.buildTypeFromExpr(expr, space, outerScope);
                }
            }

        } else if (ast instanceof ExpressionStatement) {
            ExpressionStatement exprStmt = (ExpressionStatement)ast;
            this.buildTypeFromExpr(exprStmt.getExpression(), space, outerScope);

        } else if (ast instanceof ReturnStatement) {
            ReturnStatement returnStmt = (ReturnStatement)ast;
            Expression expr = returnStmt.getExpression();
            if (expr != null) {
                this.buildTypeFromExpr(expr, space, outerScope);
            }

        } else if (ast instanceof IfStatement) {
            IfStatement ifStmt = (IfStatement)ast;
            this.buildTypeFromExpr(ifStmt.getExpression(), space, outerScope);
            Statement thenStmt = ifStmt.getThenStatement();
            this.buildTypeFromStmt(thenStmt, space, outerScope);
            Statement elseStmt = ifStmt.getElseStatement();
            if (elseStmt != null) {
                this.buildTypeFromStmt(elseStmt, space, outerScope);
            }

        } else if (ast instanceof SwitchStatement) {
            SwitchStatement switchStmt = (SwitchStatement)ast;
            DFLocalScope innerScope = outerScope.addChild(ast);
            this.buildTypeFromExpr(switchStmt.getExpression(), space, innerScope);
            for (Statement stmt :
                     (List<Statement>) switchStmt.statements()) {
                this.buildTypeFromStmt(stmt, space, innerScope);
            }

        } else if (ast instanceof SwitchCase) {
            SwitchCase switchCase = (SwitchCase)ast;
            Expression expr = switchCase.getExpression();
            if (expr != null) {
                this.buildTypeFromExpr(expr, space, outerScope);
            }

        } else if (ast instanceof WhileStatement) {
            WhileStatement whileStmt = (WhileStatement)ast;
            this.buildTypeFromExpr(whileStmt.getExpression(), space, outerScope);
            DFLocalScope innerScope = outerScope.addChild(ast);
            Statement stmt = whileStmt.getBody();
            this.buildTypeFromStmt(stmt, space, innerScope);

        } else if (ast instanceof DoStatement) {
            DoStatement doStmt = (DoStatement)ast;
            DFLocalScope innerScope = outerScope.addChild(ast);
            Statement stmt = doStmt.getBody();
            this.buildTypeFromStmt(stmt, space, innerScope);
            this.buildTypeFromExpr(doStmt.getExpression(), space, innerScope);

        } else if (ast instanceof ForStatement) {
            ForStatement forStmt = (ForStatement)ast;
            DFLocalScope innerScope = outerScope.addChild(ast);
            for (Expression init :
                     (List<Expression>) forStmt.initializers()) {
                this.buildTypeFromExpr(init, space, innerScope);
            }
            Expression expr = forStmt.getExpression();
            if (expr != null) {
                this.buildTypeFromExpr(expr, space, innerScope);
            }
            Statement stmt = forStmt.getBody();
            this.buildTypeFromStmt(stmt, space, innerScope);
            for (Expression update :
                     (List<Expression>) forStmt.updaters()) {
                this.buildTypeFromExpr(update, space, innerScope);
            }

        } else if (ast instanceof EnhancedForStatement) {
            EnhancedForStatement eForStmt = (EnhancedForStatement)ast;
            this.buildTypeFromExpr(eForStmt.getExpression(), space, outerScope);
            DFLocalScope innerScope = outerScope.addChild(ast);
            this.buildTypeFromStmt(eForStmt.getBody(), space, innerScope);

        } else if (ast instanceof BreakStatement) {

        } else if (ast instanceof ContinueStatement) {

        } else if (ast instanceof LabeledStatement) {
            LabeledStatement labeledStmt = (LabeledStatement)ast;
            Statement stmt = labeledStmt.getBody();
            this.buildTypeFromStmt(stmt, space, outerScope);

        } else if (ast instanceof SynchronizedStatement) {
            SynchronizedStatement syncStmt = (SynchronizedStatement)ast;
            this.buildTypeFromExpr(syncStmt.getExpression(), space, outerScope);
            this.buildTypeFromStmt(syncStmt.getBody(), space, outerScope);

        } else if (ast instanceof TryStatement) {
            TryStatement tryStmt = (TryStatement)ast;
            DFLocalScope innerScope = outerScope.addChild(ast);
            for (VariableDeclarationExpression decl :
                     (List<VariableDeclarationExpression>) tryStmt.resources()) {
                this.buildTypeFromExpr(decl, space, innerScope);
            }
            this.buildTypeFromStmt(tryStmt.getBody(), space, innerScope);
            for (CatchClause cc :
                     (List<CatchClause>) tryStmt.catchClauses()) {
                DFLocalScope catchScope = outerScope.addChild(cc);
                this.buildTypeFromStmt(cc.getBody(), space, catchScope);
            }
            Block finBlock = tryStmt.getFinally();
            if (finBlock != null) {
                this.buildTypeFromStmt(finBlock, space, outerScope);
            }

        } else if (ast instanceof ThrowStatement) {
            ThrowStatement throwStmt = (ThrowStatement)ast;
            Expression expr = throwStmt.getExpression();
            if (expr != null) {
                this.buildTypeFromExpr(expr, space, outerScope);
            }

        } else if (ast instanceof ConstructorInvocation) {
            ConstructorInvocation ci = (ConstructorInvocation)ast;
            for (Expression expr :
                     (List<Expression>) ci.arguments()) {
                this.buildTypeFromExpr(expr, space, outerScope);
            }

        } else if (ast instanceof SuperConstructorInvocation) {
            SuperConstructorInvocation sci = (SuperConstructorInvocation)ast;
            for (Expression expr :
                     (List<Expression>) sci.arguments()) {
                this.buildTypeFromExpr(expr, space, outerScope);
            }

        } else if (ast instanceof TypeDeclarationStatement) {
            TypeDeclarationStatement typeDeclStmt = (TypeDeclarationStatement)ast;
            AbstractTypeDeclaration abstTypeDecl = typeDeclStmt.getDeclaration();
            String path = this.getFilePath();
            DFKlass klass = space.buildTypeFromAST(path, abstTypeDecl, this, outerScope);
            klass.setKlassTree(path, abstTypeDecl);

        } else {
            throw new InvalidSyntax(ast);

        }
    }

    @SuppressWarnings("unchecked")
    protected void buildTypeFromExpr(
        Expression expr,
        DFTypeSpace space, DFVarScope outerScope)
        throws InvalidSyntax {
        assert expr != null;

        if (expr instanceof Annotation) {

        } else if (expr instanceof Name) {

        } else if (expr instanceof ThisExpression) {

        } else if (expr instanceof BooleanLiteral) {

        } else if (expr instanceof CharacterLiteral) {

        } else if (expr instanceof NullLiteral) {

        } else if (expr instanceof NumberLiteral) {

        } else if (expr instanceof StringLiteral) {

        } else if (expr instanceof TypeLiteral) {

        } else if (expr instanceof PrefixExpression) {
            PrefixExpression prefix = (PrefixExpression)expr;
            this.buildTypeFromExpr(prefix.getOperand(), space, outerScope);

        } else if (expr instanceof PostfixExpression) {
            PostfixExpression postfix = (PostfixExpression)expr;
            this.buildTypeFromExpr(postfix.getOperand(), space, outerScope);

        } else if (expr instanceof InfixExpression) {
            InfixExpression infix = (InfixExpression)expr;
            this.buildTypeFromExpr(infix.getLeftOperand(), space, outerScope);
            this.buildTypeFromExpr(infix.getRightOperand(), space, outerScope);

        } else if (expr instanceof ParenthesizedExpression) {
            ParenthesizedExpression paren = (ParenthesizedExpression)expr;
            this.buildTypeFromExpr(paren.getExpression(), space, outerScope);

        } else if (expr instanceof Assignment) {
            Assignment assn = (Assignment)expr;
            this.buildTypeFromExpr(assn.getLeftHandSide(), space, outerScope);
            this.buildTypeFromExpr(assn.getRightHandSide(), space, outerScope);

        } else if (expr instanceof VariableDeclarationExpression) {
            VariableDeclarationExpression decl =
                (VariableDeclarationExpression)expr;
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>) decl.fragments()) {
                Expression init = frag.getInitializer();
                if (init != null) {
                    this.buildTypeFromExpr(init, space, outerScope);
                }
            }

        } else if (expr instanceof MethodInvocation) {
            MethodInvocation invoke = (MethodInvocation)expr;
            Expression expr1 = invoke.getExpression();
            if (expr1 != null) {
                this.buildTypeFromExpr(expr1, space, outerScope);
            }
            for (Expression arg : (List<Expression>) invoke.arguments()) {
                this.buildTypeFromExpr(arg, space, outerScope);
            }

        } else if (expr instanceof SuperMethodInvocation) {
            SuperMethodInvocation sinvoke = (SuperMethodInvocation)expr;
            for (Expression arg : (List<Expression>) sinvoke.arguments()) {
                this.buildTypeFromExpr(arg, space, outerScope);
            }

        } else if (expr instanceof ArrayCreation) {
            ArrayCreation ac = (ArrayCreation)expr;
            for (Expression dim : (List<Expression>) ac.dimensions()) {
                this.buildTypeFromExpr(dim, space, outerScope);
            }
            ArrayInitializer init = ac.getInitializer();
            if (init != null) {
                this.buildTypeFromExpr(init, space, outerScope);
            }

        } else if (expr instanceof ArrayInitializer) {
            ArrayInitializer init = (ArrayInitializer)expr;
            for (Expression expr1 : (List<Expression>) init.expressions()) {
                this.buildTypeFromExpr(expr1, space, outerScope);
            }

        } else if (expr instanceof ArrayAccess) {
            ArrayAccess aa = (ArrayAccess)expr;
            this.buildTypeFromExpr(aa.getIndex(), space, outerScope);
            this.buildTypeFromExpr(aa.getArray(), space, outerScope);

        } else if (expr instanceof FieldAccess) {
            FieldAccess fa = (FieldAccess)expr;
            this.buildTypeFromExpr(fa.getExpression(), space, outerScope);

        } else if (expr instanceof SuperFieldAccess) {

        } else if (expr instanceof CastExpression) {
            CastExpression cast = (CastExpression)expr;
            this.buildTypeFromExpr(cast.getExpression(), space, outerScope);

        } else if (expr instanceof ClassInstanceCreation) {
            ClassInstanceCreation cstr = (ClassInstanceCreation)expr;
            Expression expr1 = cstr.getExpression();
            if (expr1 != null) {
                this.buildTypeFromExpr(expr1, space, outerScope);
            }
            for (Expression arg : (List<Expression>) cstr.arguments()) {
                this.buildTypeFromExpr(arg, space, outerScope);
            }
            if (cstr.getAnonymousClassDeclaration() != null) {
                String id = Utils.encodeASTNode(cstr);
                DFKlass anonKlass = new DFKlass(
                    id, space, this, outerScope, DFBuiltinTypes.getObjectKlass());
                space.addKlass(id, anonKlass);
                anonKlass.setKlassTree(this.getFilePath(), cstr);
            }

        } else if (expr instanceof ConditionalExpression) {
            ConditionalExpression cond = (ConditionalExpression)expr;
            this.buildTypeFromExpr(cond.getExpression(), space, outerScope);
            this.buildTypeFromExpr(cond.getThenExpression(), space, outerScope);
            this.buildTypeFromExpr(cond.getElseExpression(), space, outerScope);

        } else if (expr instanceof InstanceofExpression) {

        } else if (expr instanceof LambdaExpression) {
            LambdaExpression lambda = (LambdaExpression)expr;
            String id = Utils.encodeASTNode(lambda);
            DFKlass lambdaKlass = space.addKlass(
                id, new DFLambdaKlass(id, space, this, outerScope));
            lambdaKlass.setKlassTree(this.getFilePath(), lambda);

        } else if (expr instanceof MethodReference) {
            //  CreationReference
            //  ExpressionMethodReference
            //  SuperMethodReference
            //  TypeMethodReference
            MethodReference methodref = (MethodReference)expr;
            String id = Utils.encodeASTNode(methodref);
            DFKlass methodRefKlass = space.addKlass(
                id, new DFMethodRefKlass(id, space, this, outerScope));
            methodRefKlass.setKlassTree(this.getFilePath(), methodref);

        } else {
            // ???
            throw new InvalidSyntax(expr);
        }
    }

    public String getFilePath() {
        return _filePath;
    }
    public ASTNode getTree() {
        return _ast;
    }

    public DFVarScope getKlassScope() {
        return _klassScope;
    }

    public void setFinder(DFTypeFinder finder) {
        assert _state == LoadState.Unloaded;
        //assert _finder == null || _finder == finder;
	_finder = new DFTypeFinder(this, finder);
    }

    public DFTypeFinder getFinder() {
        if (_outerKlass != null) {
            assert _finder == null;
            return new DFTypeFinder(this, _outerKlass.getFinder());
        } else {
            return _finder;
        }
    }

    @Override
    public DFType getType(String id) {
        if (_mapTypes != null) {
            DFMapType mapType = _mapTypes.get(id);
            if (mapType != null) return mapType;
        }
        if (_paramTypes != null) {
            DFType paramType = _paramTypes.get(id);
            if (paramType != null) return paramType;
        }
        DFType type = super.getType(id);
        if (type != null) return type;
        if (_baseKlass != null) {
            type = _baseKlass.getType(id);
            if (type != null) return type;
        }
        if (_baseIfaces != null) {
            for (DFKlass iface : _baseIfaces) {
                if (iface != null) {
                    type = iface.getType(id);
                    if (type != null) return type;
                }
            }
        }
        return null;
    }

    // Only used by DFLambdaKlass.
    protected void setBaseKlass(DFKlass klass) {
        _baseKlass = klass;
    }

    public DFKlass getBaseKlass() {
        assert _state == LoadState.Loaded;
        return _baseKlass;
    }

    public DFKlass[] getBaseIfaces() {
        assert _state == LoadState.Loaded;
        return _baseIfaces;
    }

    public boolean isEnum() {
        assert _state == LoadState.Loaded;
        return (_baseKlass != null &&
		_baseKlass._genericKlass ==
                DFBuiltinTypes.getEnumKlass());
    }

    public boolean isDefined() {
        return (_state == LoadState.Loaded);
    }

    public boolean isInterface() {
        return _interface;
    }

    public boolean isFuncInterface() {
	assert _state == LoadState.Loaded;
        if (!_interface) return false;
        // Count the number of abstract methods.
        int n = 0;
        for (DFMethod method : _methods) {
            if (method.isAbstract()) {
                n++;
            }
        }
        return (n == 1);
    }

    public DFMethod getFuncMethod() {
        for (DFMethod method : _methods) {
            if (method.isAbstract()) return method;
        }
	return null;
    }

    public DFMethod getInitMethod() {
        assert _state == LoadState.Loaded;
        return _initMethod;
    }

    protected DFRef lookupField(String id)
        throws VariableNotFound {
        assert _state == LoadState.Loaded;
        if (_klassScope != null) {
            try {
                return _klassScope.lookupField(id);
            } catch (VariableNotFound e) {
            }
        }
        if (_baseKlass != null) {
            try {
                return _baseKlass.lookupField(id);
            } catch (VariableNotFound e) {
            }
        }
        if (_baseIfaces != null) {
            for (DFKlass iface : _baseIfaces) {
                try {
                    return iface.lookupField(id);
                } catch (VariableNotFound e) {
                }
            }
        }
        throw new VariableNotFound("."+id);
    }

    public DFRef lookupField(SimpleName name)
        throws VariableNotFound {
        return this.lookupField(name.getIdentifier());
    }

    public List<FieldRef> getFields() {
        assert _fields != null;
	return _fields;
    }

    public List<DFMethod> getMethods() {
        assert _methods != null;
	return _methods;
    }

    private DFMethod lookupMethod1(
        DFMethod.CallStyle callStyle, String id, DFType[] argTypes) {
        int bestDist = -1;
        DFMethod bestMethod = null;
        for (DFMethod method1 : this.getMethods()) {
            DFMethod.CallStyle callStyle1 = method1.getCallStyle();
            if (!(callStyle == callStyle1 ||
                  (callStyle == DFMethod.CallStyle.InstanceOrStatic &&
                   (callStyle1 == DFMethod.CallStyle.InstanceMethod ||
                    callStyle1 == DFMethod.CallStyle.StaticMethod)))) continue;
            if (id != null && !id.equals(method1.getName())) continue;
	    Map<DFMapType, DFType> typeMap = new HashMap<DFMapType, DFType>();
            int dist = method1.canAccept(argTypes, typeMap);
            if (dist < 0) continue;
            if (bestDist < 0 || dist < bestDist) {
		DFMethod method = method1.parameterize(typeMap);
		if (method != null) {
		    bestDist = dist;
		    bestMethod = method;
		}
            }
        }
        return bestMethod;
    }

    public DFMethod lookupMethod(
        DFMethod.CallStyle callStyle, SimpleName name, DFType[] argTypes)
        throws MethodNotFound {
        String id = (name == null)? null : name.getIdentifier();
        return this.lookupMethod(callStyle, id, argTypes);
    }

    public DFMethod lookupMethod(
        DFMethod.CallStyle callStyle, String id, DFType[] argTypes)
        throws MethodNotFound {
        assert _state == LoadState.Loaded;
        DFMethod method = this.lookupMethod1(callStyle, id, argTypes);
        if (method != null) {
            return method;
        }
        if (_outerKlass != null) {
            try {
                return _outerKlass.lookupMethod(callStyle, id, argTypes);
            } catch (MethodNotFound e) {
            }
        }
        if (_baseKlass != null) {
            try {
                return _baseKlass.lookupMethod(callStyle, id, argTypes);
            } catch (MethodNotFound e) {
            }
        }
        if (_baseIfaces != null) {
            for (DFKlass iface : _baseIfaces) {
                try {
                    return iface.lookupMethod(callStyle, id, argTypes);
                } catch (MethodNotFound e) {
                }
            }
        }
        throw new MethodNotFound(
            (id == null)? callStyle.toString() : id, argTypes);
    }

    protected DFRef addField(
        SimpleName name, boolean isStatic, DFType type) {
        return this.addField(name.getIdentifier(), isStatic, type);
    }

    protected DFRef addField(
        String id, boolean isStatic, DFType type) {
        assert _klassScope != null;
        FieldRef ref = _klassScope.addField(id, isStatic, type);
        //Logger.info("DFKlass.addField:", ref);
        assert _fields != null;
	_fields.add(ref);
        return ref;
    }

    public DFMethod getMethod(String key) {
        assert _id2method != null;
        return _id2method.get(key);
    }

    protected DFMethod addMethod(DFMethod method, String key) {
        //Logger.info("DFKlass.addMethod:", method);
        assert _methods != null;
        assert _id2method != null;
        _methods.add(method);
        if (key != null) {
            _id2method.put(key, method);
        }
        return method;
    }

    public DFMethod addFallbackMethod(String name, DFType[] argTypes) {
        assert _id2method != null;
        DFMethod method = new DFMethod(
            this, DFMethod.CallStyle.InstanceMethod, false,
	    name, name, _klassScope);
        method.setFuncType(new DFFunctionType(argTypes, DFUnknownType.UNKNOWN));
        // Do not adds to _methods because it might be being referenced.
        _id2method.put(name, method);
        return method;
    }

    public void overrideMethods() {
        // override the methods.
        for (DFMethod method : _methods) {
            if (_baseKlass != null) {
                _baseKlass.overrideMethod(method);
            }
            if (_baseIfaces != null) {
                for (DFKlass iface : _baseIfaces) {
                    iface.overrideMethod(method);
                }
            }
        }
    }

    private void overrideMethod(DFMethod method1) {
        for (DFMethod method0 : getMethods()) {
            if (method0.addOverrider(method1)) break;
        }
    }

    @SuppressWarnings("unchecked")
    private boolean isStatic(BodyDeclaration body) {
        for (IExtendedModifier imod :
                 (List<IExtendedModifier>) body.modifiers()) {
            if (imod.isModifier()) {
                if (((Modifier)imod).isStatic()) return true;
            }
        }
        return false;
    }

    public void load()
        throws InvalidSyntax {
        // an unspecified parameterized klass cannot be loaded.
        if (_state != LoadState.Unloaded) return;
        _state = LoadState.Loading;
        if (_outerKlass != null) {
            _outerKlass.load();
        }
        DFTypeFinder finder = this.getFinder();
        assert finder != null;
        assert _ast != null || _jarPath != null;
        if (_mapTypes != null) {
            // a generic class is only referred to, but not built.
            for (DFMapType mapType : _mapTypes.values()) {
		mapType.build(finder);
            }
        } else if (_ast != null) {
            this.initBuild();
            this.buildTypeFromDecls(_ast);
            this.loadMembersFromAST(finder, _ast);
        } else if (_jarPath != null) {
            this.initBuild();
            try {
                JarFile jarfile = new JarFile(_jarPath);
                try {
                    JarEntry je = jarfile.getJarEntry(_entPath);
                    InputStream strm = jarfile.getInputStream(je);
                    JavaClass jklass = new ClassParser(strm, _entPath).parse();
                    this.loadMembersFromJKlass(finder, jklass);
                } finally {
                    jarfile.close();
                }
            } catch (IOException e) {
                Logger.error(
                    "DFKlass.load: IOException",
                    this, _jarPath+"/"+_entPath);
            }
        }
        _state = LoadState.Loaded;
    }

    private void loadMembersFromJKlass(DFTypeFinder finder, JavaClass jklass)
        throws InvalidSyntax {
        //Logger.info("DFKlass.loadMembersFromJKlass:", this, finder);
        _interface = jklass.isInterface();

        // Load base klasses/interfaces.
        String sig = Utils.getJKlassSignature(jklass.getAttributes());
	if (this == DFBuiltinTypes.getObjectKlass()) {
	    ;
	} else if (sig != null) {
            //Logger.info("jklass:", this, sig);
	    JNITypeParser parser = new JNITypeParser(sig);
	    try {
		_baseKlass = parser.resolveType(finder).toKlass();
	    } catch (TypeNotFound e) {
		Logger.error(
                    "DFKlass.loadMembersFromJKlass: TypeNotFound (baseKlass)",
                    this, e.name, sig);
	    }
	    _baseKlass.load();
	    List<DFKlass> ifaces = new ArrayList<DFKlass>();
	    for (;;) {
		DFType iface = DFBuiltinTypes.getObjectKlass();
		try {
		    iface = parser.resolveType(finder);
		} catch (TypeNotFound e) {
		    Logger.error(
                        "DFKlass.loadMembersFromJKlass: TypeNotFound (iface)",
                        this, e.name, sig);
		}
		if (iface == null) break;
		ifaces.add(iface.toKlass());
	    }
	    _baseIfaces = new DFKlass[ifaces.size()];
	    ifaces.toArray(_baseIfaces);
	    for (DFKlass iface : _baseIfaces) {
		iface.load();
	    }
        } else {
	    String superClass = jklass.getSuperclassName();
	    if (superClass != null && !superClass.equals(jklass.getClassName())) {
		try {
		    _baseKlass = finder.lookupType(superClass).toKlass();
		} catch (TypeNotFound e) {
		    Logger.error(
                        "DFKlass.loadMembersFromJKlass: TypeNotFound (baseKlass)",
                        this, e.name);
		}
	    }
	    _baseKlass.load();
	    String[] ifaces = jklass.getInterfaceNames();
	    if (ifaces != null) {
                _baseIfaces = new DFKlass[ifaces.length];
		for (int i = 0; i < ifaces.length; i++) {
		    DFKlass iface = DFBuiltinTypes.getObjectKlass();
		    try {
			iface = finder.lookupType(ifaces[i]).toKlass();
		    } catch (TypeNotFound e) {
			Logger.error(
                            "DFKlass.loadMembersFromJKlass: TypeNotFound (iface)",
                            this, e.name);
		    }
		    _baseIfaces[i] = iface;
		}
                for (DFKlass iface : _baseIfaces) {
                    iface.load();
                }
	    }
	}
        // Extend a TypeFinder for this klass.
        if (_outerKlass != null) {
            _outerKlass.load();
        }
        finder = new DFTypeFinder(this, finder);
        // Define fields.
        for (Field fld : jklass.getFields()) {
            if (fld.isPrivate()) continue;
            sig = Utils.getJKlassSignature(fld.getAttributes());
	    DFType type;
	    try {
		if (sig != null) {
		    //Logger.info("fld:", fld.getName(), sig);
		    JNITypeParser parser = new JNITypeParser(sig);
		    type = parser.resolveType(finder);
		} else {
		    type = finder.resolve(fld.getType());
		}
	    } catch (TypeNotFound e) {
		Logger.error(
                    "DFKlass.loadMembersFromJKlass: TypeNotFound (field)",
                    this, e.name, sig);
		type = DFUnknownType.UNKNOWN;
	    }
	    this.addField(fld.getName(), fld.isStatic(), type);
        }
        // Define methods.
        for (Method meth : jklass.getMethods()) {
            if (meth.isPrivate()) continue;
            sig = Utils.getJKlassSignature(meth.getAttributes());
            String name = meth.getName();
            DFMethod.CallStyle callStyle;
            if (meth.getName().equals("<init>")) {
                callStyle = DFMethod.CallStyle.Constructor;
            } else if (meth.isStatic()) {
                callStyle = DFMethod.CallStyle.StaticMethod;
            } else {
                callStyle = DFMethod.CallStyle.InstanceMethod;
            }
            DFMethod method = new DFMethod(
                this, callStyle, meth.isAbstract(),
		sig, name, null);
            method.setFinder(finder);
            DFFunctionType funcType;
	    if (sig != null) {
                //Logger.info("meth:", meth.getName(), sig);
		method.setMapTypes(sig);
		JNITypeParser parser = new JNITypeParser(sig);
		try {
		    funcType = (DFFunctionType)parser.resolveType(method.getFinder());
		} catch (TypeNotFound e) {
		    Logger.error(
                        "DFKlass.loadMembersFromJKlass: TypeNotFound (method)",
                        this, e.name, sig);
		    continue;
		}
	    } else {
		org.apache.bcel.generic.Type[] args = meth.getArgumentTypes();
		DFType[] argTypes = new DFType[args.length];
		for (int i = 0; i < args.length; i++) {
		    argTypes[i] = finder.resolveSafe(args[i]);
		}
		DFType returnType = finder.resolveSafe(meth.getReturnType());
                funcType = new DFFunctionType(argTypes, returnType);
	    }
            // For varargs methods, the last argument is declared as an array
            // so no special treatment is required here.
            funcType.setVarArgs(meth.isVarArgs());
            ExceptionTable excTable = meth.getExceptionTable();
            if (excTable != null) {
                String[] excNames = excTable.getExceptionNames();
                DFKlass[] exceptions = new DFKlass[excNames.length];
                for (int i = 0; i < excNames.length; i++) {
		    DFType type;
		    try {
			type = finder.lookupType(excNames[i]);
		    } catch (TypeNotFound e) {
			Logger.error(
                            "DFKlass.loadMembersFromJKlass: TypeNotFound (exception)",
                            this, e.name);
			type = DFUnknownType.UNKNOWN;
		    }
		    exceptions[i] = type.toKlass();
                }
                funcType.setExceptions(exceptions);
            }
            method.setFuncType(funcType);
            this.addMethod(method, null);
        }
    }

    @SuppressWarnings("unchecked")
    protected void loadMembersFromAST(DFTypeFinder finder, ASTNode ast)
        throws InvalidSyntax {
        //Logger.info("DFKlass.loadMembersFromAST:", this, finder);
        if (ast instanceof AbstractTypeDeclaration) {
            this.loadMembersFromAbstTypeDecl(finder, (AbstractTypeDeclaration)ast);

        } else if (ast instanceof ClassInstanceCreation) {
            this.loadMembersFromAnonDecl(finder, (ClassInstanceCreation)ast);

        } else {
            throw new InvalidSyntax(ast);
        }
    }

    private void loadMembersFromAbstTypeDecl(
        DFTypeFinder finder, AbstractTypeDeclaration abstTypeDecl)
        throws InvalidSyntax {
        if (abstTypeDecl instanceof TypeDeclaration) {
            this.loadMembersFromTypeDecl(finder, (TypeDeclaration)abstTypeDecl);

        } else if (abstTypeDecl instanceof EnumDeclaration) {
            this.loadMembersFromEnumDecl(finder, (EnumDeclaration)abstTypeDecl);

        } else if (abstTypeDecl instanceof AnnotationTypeDeclaration) {
            this.loadMembersFromAnnotTypeDecl(finder, (AnnotationTypeDeclaration)abstTypeDecl);
        }
    }

    @SuppressWarnings("unchecked")
    private void loadMembersFromTypeDecl(
        DFTypeFinder finder, TypeDeclaration typeDecl)
        throws InvalidSyntax {
        _interface = typeDecl.isInterface();
        // Load base klasses/interfaces.
	// Get superclass.
	Type superClass = typeDecl.getSuperclassType();
	if (superClass != null) {
	    try {
		_baseKlass = finder.resolve(superClass).toKlass();
	    } catch (TypeNotFound e) {
		Logger.error(
                    "DFKlass.loadMembersFromTypeDecl: TypeNotFound (baseKlass)",
                    this, e.name);
	    }
	}
	_baseKlass.load();
	// Get interfaces.
	List<Type> ifaces = typeDecl.superInterfaceTypes();
	_baseIfaces = new DFKlass[ifaces.size()];
	for (int i = 0; i < ifaces.size(); i++) {
	    DFKlass iface = DFBuiltinTypes.getObjectKlass();
	    try {
		iface = finder.resolve(ifaces.get(i)).toKlass();
	    } catch (TypeNotFound e) {
		Logger.error(
                    "DFKlass.loadMembersFromTypeDecl: TypeNotFound (iface)",
                    this, e.name);
	    }
	    _baseIfaces[i] = iface;
	}
	for (DFKlass iface : _baseIfaces) {
	    iface.load();
	}
	this.loadMembers(finder, typeDecl.bodyDeclarations());
    }

    @SuppressWarnings("unchecked")
    private void loadMembersFromAnonDecl(
        DFTypeFinder finder, ClassInstanceCreation cstr)
        throws InvalidSyntax {
	// Get superclass.
	Type superClass = cstr.getType();
	if (superClass != null) {
	    try {
		_baseKlass = finder.resolve(superClass).toKlass();
	    } catch (TypeNotFound e) {
		Logger.error(
                    "DFKlass.loadMembersFromAnonDecl: TypeNotFound (baseKlass)",
                    this, e.name);
	    }
	}
	_baseKlass.load();
	this.loadMembers(
	    finder, cstr.getAnonymousClassDeclaration().bodyDeclarations());
    }

    @SuppressWarnings("unchecked")
    private void loadMembersFromEnumDecl(
        DFTypeFinder finder, EnumDeclaration enumDecl)
        throws InvalidSyntax {
        // Load base klasses/interfaces.
	// Get superclass.
	DFKlass enumKlass = DFBuiltinTypes.getEnumKlass();
	_baseKlass = enumKlass.parameterize(new DFType[] { this });
	_baseKlass.load();
	// Get interfaces.
	List<Type> ifaces = enumDecl.superInterfaceTypes();
	_baseIfaces = new DFKlass[ifaces.size()];
	for (int i = 0; i < ifaces.size(); i++) {
	    DFKlass iface = DFBuiltinTypes.getObjectKlass();
	    try {
		iface = finder.resolve(ifaces.get(i)).toKlass();
	    } catch (TypeNotFound e) {
		Logger.error(
                    "DFKlass.loadMembersFromEnumDecl: TypeNotFound (iface)",
                    this, e.name);
	    }
	    _baseIfaces[i] = iface;
	}
	for (DFKlass iface : _baseIfaces) {
	    iface.load();
	}
	// Get constants.
	for (EnumConstantDeclaration econst :
		 (List<EnumConstantDeclaration>) enumDecl.enumConstants()) {
	    this.addField(econst.getName(), true, this);
	}
	// Enum has a special method "values()".
	DFMethod method = new DFMethod(
	    this, DFMethod.CallStyle.InstanceMethod, false,
            "values", "values", null);
	method.setFuncType(
	    new DFFunctionType(new DFType[] {}, DFArrayType.getType(this, 1)));
	this.addMethod(method, null);
	this.loadMembers(finder, enumDecl.bodyDeclarations());
    }

    @SuppressWarnings("unchecked")
    private void loadMembersFromAnnotTypeDecl(
        DFTypeFinder finder, AnnotationTypeDeclaration annotTypeDecl)
        throws InvalidSyntax {
	this.loadMembers(finder, annotTypeDecl.bodyDeclarations());
    }

    @SuppressWarnings("unchecked")
    private void loadMembers(DFTypeFinder finder, List<BodyDeclaration> decls)
        throws InvalidSyntax {
        if (_initMethod != null) {
            _initMethod.setFinder(finder);
        }

        for (BodyDeclaration body : decls) {
            if (body instanceof AbstractTypeDeclaration) {
                // Child klasses are loaded independently.

            } else if (body instanceof FieldDeclaration) {
                FieldDeclaration decl = (FieldDeclaration)body;
                DFType fldType = finder.resolveSafe(decl.getType());
                for (VariableDeclarationFragment frag :
                         (List<VariableDeclarationFragment>) decl.fragments()) {
                    DFType ft = fldType;
                    int ndims = frag.getExtraDimensions();
                    if (ndims != 0) {
                        ft = DFArrayType.getType(ft, ndims);
                    }
                    this.addField(frag.getName(), isStatic(decl), ft);
                }

            } else if (body instanceof MethodDeclaration) {
                MethodDeclaration decl = (MethodDeclaration)body;
                String id = Utils.encodeASTNode(decl);
                DFMethod method = this.getMethod(id);
                method.setFinder(finder);
                method.buildFuncType(this);

            } else if (body instanceof EnumConstantDeclaration) {

            } else if (body instanceof AnnotationTypeMemberDeclaration) {
                AnnotationTypeMemberDeclaration decl =
                    (AnnotationTypeMemberDeclaration)body;
                DFType type = finder.resolveSafe(decl.getType());
                this.addField(decl.getName(), isStatic(decl), type);

            } else if (body instanceof Initializer) {

            } else {
                throw new InvalidSyntax(body);
            }
        }
    }

    public static List<DFType> getMappedTypes(List<DFMapType> mapTypes) {
	List<DFType> types = new ArrayList<DFType>();
	for (DFMapType mapType : mapTypes) {
	    types.add(mapType.toKlass());
	}
	return types;
    }

    public static String getParamName(List<DFType> paramTypes) {
	DFType[] a = new DFType[paramTypes.size()];
	paramTypes.toArray(a);
	return getParamName(a);
    }

    public static String getParamName(DFType[] paramTypes) {
        StringBuilder b = new StringBuilder();
        for (DFType type : paramTypes) {
            if (0 < b.length()) {
                b.append(",");
            }
            b.append(type.getTypeName());
        }
        return "<"+b.toString()+">";
    }

    protected void dumpContents(PrintStream out, String indent) {
        super.dumpContents(out, indent);
        if (_mapTypes != null) {
            for (Map.Entry<String,DFMapType> e : _mapTypes.entrySet()) {
                out.println(indent+"map: "+e.getKey()+" "+e.getValue());
            }
        }
        if (_paramTypes != null) {
            for (Map.Entry<String,DFType> e : _paramTypes.entrySet()) {
                out.println(indent+"param: "+e.getKey()+" "+e.getValue());
            }
        }
        if (_genericKlass != null) {
            _genericKlass.dump(out, indent);
        }
        if (_baseKlass != null) {
            _baseKlass.dump(out, indent);
        }
        if (_baseIfaces != null) {
            for (DFKlass iface : _baseIfaces) {
                if (iface != null) {
                    iface.dump(out, indent);
                }
            }
        }
    }

    // FieldRef
    public class FieldRef extends DFRef {

        private String _name;
        private boolean _static;

        public FieldRef(DFType type, String name, boolean isStatic) {
            super(type);
            _name = name;
            _static = isStatic;
        }

        public void writeXML(XMLStreamWriter writer)
            throws XMLStreamException {
            writer.writeStartElement("field");
            writer.writeAttribute("name", this.getFullName());
            writer.writeAttribute("type", this.getRefType().getTypeName());
            writer.writeAttribute("static", Boolean.toString(_static));
            writer.writeEndElement();
        }

        public String getName() {
            return _name;
        }

        @Override
        public boolean isLocal() {
            return false;
        }

        public boolean isStatic() {
            return _static;
        }

        @Override
        public String getFullName() {
            return "@"+DFKlass.this.getTypeName()+"/."+_name;
        }
    }

    // ThisRef
    private class ThisRef extends DFRef {
        public ThisRef(DFType type) {
            super(type);
        }

        @Override
        public boolean isLocal() {
            return false;
        }

        @Override
        public String getFullName() {
            return "#this";
        }
    }

    // KlassScope
    private class KlassScope extends DFVarScope {

        private DFRef _this;
        private Map<String, DFRef> _id2field =
            new HashMap<String, DFRef>();

        public KlassScope(DFVarScope outer, String id) {
            super(outer, id);
            _this = new ThisRef(DFKlass.this);
        }

        @Override
        public String getScopeName() {
            return DFKlass.this.getTypeName();
        }

        @Override
        public DFRef lookupThis() {
            return _this;
        }

        @Override
        public DFRef lookupVar(String id)
            throws VariableNotFound {
            try {
                return DFKlass.this.lookupField(id);
            } catch (VariableNotFound e) {
                return super.lookupVar(id);
            }
        }

        public DFRef lookupField(String id)
            throws VariableNotFound {
            DFRef ref = _id2field.get(id);
            if (ref == null) throw new VariableNotFound(id);
            return ref;
        }

        protected FieldRef addField(
            String id, boolean isStatic, DFType type) {
            FieldRef ref = new FieldRef(type, id, isStatic);
            _id2field.put(id, ref);
            return ref;
        }

        // dumpContents (for debugging)
        protected void dumpContents(PrintStream out, String indent) {
            super.dumpContents(out, indent);
            for (DFRef ref : DFKlass.this.getFields()) {
                out.println(indent+"defined: "+ref);
            }
            for (DFMethod method : DFKlass.this.getMethods()) {
                out.println(indent+"defined: "+method);
            }
        }
    }
}
