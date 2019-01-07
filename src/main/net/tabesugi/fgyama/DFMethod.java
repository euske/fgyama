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

    private Set<DFMethod> _callers =
        new HashSet<DFMethod>();

    private DFTypeFinder _finder = null;
    private DFVarScope _scope = null;
    private DFFrame _frame = null;
    private ASTNode _ast = null;

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
        String name, DFCallStyle callStyle, DFMethodType methodType,
	DFTypeFinder finder) {
        _klass = klass;
        _childSpace = childSpace;
        _name = name;
        _callStyle = callStyle;
        _methodType = methodType;
	_finder = finder;
    }

    public DFMethod(
        DFKlass klass, DFTypeSpace childSpace,
        String name, DFCallStyle callStyle, DFMethodType methodType,
        DFTypeFinder finder, DFOverride[] overrides) {
        _klass = klass;
        _childSpace = childSpace;
        _name = name;
        _callStyle = callStyle;
        _methodType = methodType;
	_finder = finder;
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
	//Logger.info("DFMethod.addOverride: "+this+" <- "+override);
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

    public DFMethod parameterize(Map<DFParamType, DFType> typeMap) {
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
            return new DFMethod(
                _klass, _childSpace, _name, _callStyle,
                methodType, _finder, overrides);
        }
        return this;
    }

    public void addCaller(DFMethod method) {
        _callers.add(method);
    }

    public DFMethod[] getCallers() {
        DFMethod[] callers = new DFMethod[_callers.size()];
        _callers.toArray(callers);
        return callers;
    }

    public DFTypeFinder getFinder() {
        return new DFTypeFinder(_finder, _childSpace);
    }

    public DFVarScope getScope() {
        return _scope;
    }

    public DFFrame getFrame() {
        return _frame;
    }

    public ASTNode getTree() {
        return _ast;
    }

    public void build(
        DFLocalVarScope scope, MethodDeclaration decl)
        throws UnsupportedSyntax, TypeNotFound {
        scope.build(_finder, decl);
        _scope = scope;
        //_scope.dump();
        _frame = new DFFrame(DFFrame.RETURNABLE);
        try {
            _frame.build(_finder, this, _scope, decl);
        } catch (EntityNotFound e) {
            // XXX ignore EntityNotFound for now
            Logger.error("Entity not found: "+e.name+" ast="+e.ast+" method="+this);
        }
        _ast = decl;
    }

    public void build(
        DFLocalVarScope scope, Initializer initializer)
        throws UnsupportedSyntax, TypeNotFound {
        scope.build(_finder, initializer);
        _scope = scope;
        _frame = new DFFrame(DFFrame.RETURNABLE);
        try {
            _frame.build(_finder, this, _scope, initializer);
        } catch (EntityNotFound e) {
            // XXX ignore EntityNotFound for now
        }
        _ast = initializer;
    }
}
