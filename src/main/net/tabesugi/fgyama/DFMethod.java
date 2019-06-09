//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFMethod
//
public class DFMethod extends DFTypeSpace implements Comparable<DFMethod> {

    private DFKlass _klass;
    private String _name;
    private DFCallStyle _callStyle;
    private DFLocalVarScope _scope;

    private DFTypeFinder _finder = null;
    private DFMapType[] _mapTypes = null;
    private DFTypeSpace _mapTypeSpace = null;
    private DFMethodType _methodType = null;

    private SortedSet<DFMethod> _callers =
        new TreeSet<DFMethod>();

    private ASTNode _ast = null;

    private DFFrame _frame = null;

    // List of subclass' methods overriding this method.
    private List<DFMethod> _overrides = new ArrayList<DFMethod>();

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

    public DFMethod(
        DFKlass klass, String name, DFCallStyle callStyle,
        DFLocalVarScope scope) {
        super(name, klass);
        _klass = klass;
        _name = name;
        _callStyle = callStyle;
        _scope = scope;
    }

    public void setFinder(DFTypeFinder finder) {
        _finder = new DFTypeFinder(this, finder);
    }

    public void setMapTypes(DFMapType[] mapTypes) {
        _mapTypes = mapTypes;
        _mapTypeSpace = DFTypeSpace.createMapTypeSpace(mapTypes);
        _mapTypeSpace.buildMapTypes(_finder, mapTypes);
    }

    public void setMethodType(DFMethodType methodType) {
        _methodType = methodType;
    }

    @Override
    public String toString() {
        return ("<DFMethod("+this.getSignature()+">");
    }

    @Override
    public int compareTo(DFMethod method) {
        if (this == method) return 0;
        return _name.compareTo(method._name);
    }

    public boolean equals(DFMethod method) {
        if (!_name.equals(method._name)) return false;
        return _methodType.equals(method._methodType);
    }

    public boolean isGeneric() {
        return (_mapTypes != null);
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
        return name + _methodType.getTypeName();
    }

    public DFCallStyle getCallStyle() {
        return _callStyle;
    }

    public DFType getReturnType() {
        return _methodType.getReturnType();
    }

    public DFKlass getKlass(String id)
        throws TypeNotFound {
        if (_mapTypeSpace != null) {
            try {
                return _mapTypeSpace.getKlass(id);
            } catch (TypeNotFound e) {
            }
        }
        return super.getKlass(id);
    }

    public int canAccept(DFType[] argTypes) {
        Map<DFMapType, DFType> typeMap = new HashMap<DFMapType, DFType>();
        return _methodType.canAccept(argTypes, typeMap);
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

    public SortedSet<DFMethod> getCallers() {
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

    public DFLocalVarScope getScope() {
        return _scope;
    }
    public DFFrame getFrame() {
        return _frame;
    }

    public void buildScope()
        throws UnsupportedSyntax, TypeNotFound {
	if (_ast == null) return;
	assert _scope != null;
	DFTypeFinder finder = this.getFinder();
	if (_ast instanceof MethodDeclaration) {
	    _scope.buildMethodDecl(finder, (MethodDeclaration)_ast);
	} else if (_ast instanceof Initializer) {
	    _scope.buildInitializer(finder, (Initializer)_ast);
	}  else {
	    throw new UnsupportedSyntax(_ast);
	}
        //_scope.dump();
    }

    public void buildFrame()
        throws UnsupportedSyntax, TypeNotFound {
	if (_ast == null) return;
	assert _scope != null;
	DFTypeFinder finder = this.getFinder();
        _frame = new DFFrame(DFFrame.RETURNABLE);
        try {
	    if (_ast instanceof MethodDeclaration) {
		_frame.buildMethodDecl(
                    finder, this, _scope, (MethodDeclaration)_ast);
	    } else if (_ast instanceof Initializer) {
		_frame.buildInitializer(
                    finder, this, _scope, (Initializer)_ast);
	    }  else {
		throw new UnsupportedSyntax(_ast);
	    }
        } catch (EntityNotFound e) {
            // XXX ignore EntityNotFound for now
            Logger.error("Entity not found:", e.name, "ast="+e.ast, "method="+this);
        }
    }
}
