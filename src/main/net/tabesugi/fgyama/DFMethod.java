//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFMethod
//
public class DFMethod implements Comparable<DFMethod> {

    private DFKlass _klass;
    private DFTypeSpace _childSpace;
    private String _name;
    private DFCallStyle _callStyle;
    private DFMethodType _methodType;

    private SortedSet<DFMethod> _callers =
        new TreeSet<DFMethod>();

    private DFTypeFinder _finder = null;
    private DFLocalVarScope _scope = null;
    private ASTNode _ast = null;

    private DFFrame _frame = null;

    private List<DFOverride> _overrides = new ArrayList<DFOverride>();

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
        DFKlass klass, DFTypeSpace childSpace,
        String name, DFCallStyle callStyle, DFMethodType methodType) {
        _klass = klass;
        _childSpace = childSpace;
        _name = name;
        _callStyle = callStyle;
        _methodType = methodType;
    }

    private DFMethod(
        DFMethod method, DFMethodType methodType, DFOverride[] overrides) {
        _klass = method._klass;
        _childSpace = method._childSpace;
        _name = method._name;
        _callStyle = method._callStyle;
        _finder = method._finder;
        _scope = method._scope;
        _ast = method._ast;
        _methodType = methodType;
	for (DFOverride override : overrides) {
	    _overrides.add(override);
	}
    }

    @Override
    public String toString() {
        return ("<DFMethod("+this.getSignature()+">");
    }

    @Override
    public int compareTo(DFMethod method) {
        return _name.compareTo(method._name);
    }

    public boolean equals(DFMethod method) {
        if (!_name.equals(method._name)) return false;
        return _methodType.equals(method._methodType);
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

    public DFTypeSpace getChildSpace() {
        return _childSpace;
    }

    public DFType getReturnType() {
        return _methodType.getReturnType();
    }

    public int canAccept(String name, DFType[] argTypes) {
        if (!_name.equals(name)) return -1;
        return _methodType.canAccept(argTypes, null);
    }

    public void addOverride(DFMethod method, int level) {
	DFOverride override = new DFOverride(method, level);
	//Logger.info("DFMethod.addOverride:", this, "<-", override);
        _overrides.add(override);
    }

    public DFMethod[] getOverrides() {
        DFOverride[] overrides = new DFOverride[_overrides.size()];
        _overrides.toArray(overrides);
        Arrays.sort(overrides);
        DFMethod[] methods = new DFMethod[overrides.length+1];
	for (int i = 0; i < overrides.length; i++) {
	    methods[i] = overrides[i].method;
	}
	methods[overrides.length] = this;
        return methods;
    }

    public DFMethod parameterize(Map<DFMapType, DFType> typeMap) {
        DFMethodType methodType = _methodType.parameterize(typeMap);
        boolean changed = (_methodType != methodType);
        DFOverride[] overrides = new DFOverride[_overrides.size()];
        for (int i = 0; i < overrides.length; i++) {
            DFOverride override = _overrides.get(i);
	    DFMethod method0 = override.method;
            DFMethod method1 = method0.parameterize(typeMap);
            changed = changed || (method0 != method1);
            overrides[i] = new DFOverride(method1, override.level);
        }
        if (changed) {
            return new DFMethod(this, methodType, overrides);
        }
        return this;
    }

    public void addCaller(DFMethod method) {
        _callers.add(method);
    }

    public SortedSet<DFMethod> getCallers() {
        return _callers;
    }

    public void setFinder(DFTypeFinder finder) {
	_finder = finder;
    }
    public DFTypeFinder getFinder() {
        return new DFTypeFinder(_finder, _childSpace);
    }

    public void setScope(DFLocalVarScope scope) {
        _scope = scope;
    }
    public DFLocalVarScope getScope() {
        return _scope;
    }

    public void setTree(ASTNode ast) {
	_ast = ast;
    }
    public ASTNode getTree() {
        return _ast;
    }

    public DFFrame getFrame() {
        return _frame;
    }

    public void buildFrame()
        throws UnsupportedSyntax, TypeNotFound {
	if (_ast == null) return;
	assert _finder != null;
	assert _scope != null;
	if (_ast instanceof MethodDeclaration) {
	    _scope.build(_finder, (MethodDeclaration)_ast);
	} else if (_ast instanceof Initializer) {
	    _scope.build(_finder, (Initializer)_ast);
	}  else {
	    throw new UnsupportedSyntax(_ast);
	}
        //_scope.dump();
        _frame = new DFFrame(DFFrame.RETURNABLE);
        try {
	    if (_ast instanceof MethodDeclaration) {
		_frame.build(_finder, this, _scope, (MethodDeclaration)_ast);
	    } else if (_ast instanceof Initializer) {
		_frame.build(_finder, this, _scope, (Initializer)_ast);
	    }  else {
		throw new UnsupportedSyntax(_ast);
	    }
        } catch (EntityNotFound e) {
            // XXX ignore EntityNotFound for now
            Logger.error("Entity not found:", e.name, "ast="+e.ast, "method="+this);
        }
    }
}
