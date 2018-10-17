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
    private boolean _static;
    private DFMethodType _methodType;

    private List<DFMethod> _overrides = new ArrayList<DFMethod>();

    public DFMethod(
        DFKlass klass, DFTypeSpace childSpace,
        String name, boolean isStatic, DFMethodType methodType) {
        _klass = klass;
        _childSpace = childSpace;
        _name = name;
        _static = isStatic;
        _methodType = methodType;
        _overrides.add(this);
    }

    public DFMethod(
        DFKlass klass, DFTypeSpace childSpace,
        String name, boolean isStatic, DFMethodType methodType,
        DFMethod[] overrides) {
        this(klass, childSpace, name, isStatic, methodType);
        for (DFMethod method : overrides) {
            _overrides.add(method);
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

    public DFTypeSpace getChildSpace() {
        return _childSpace;
    }

    public DFType getReturnType() {
        return _methodType.getReturnType();
    }

    public int canAccept(String name, DFType[] argTypes) {
        if (!_name.equals(name)) return -1;
        return _methodType.canAccept(argTypes);
    }

    public void addOverride(DFMethod method) {
        _overrides.add(method);
    }

    public DFMethod[] getOverrides() {
        DFMethod[] methods = new DFMethod[_overrides.size()];
        _overrides.toArray(methods);
        return methods;
    }

    public DFMethod parameterize(DFType[] types) {
        DFMethodType methodType = _methodType.parameterize(types);
        boolean changed = (methodType != _methodType);
        DFMethod[] overrides = new DFMethod[_overrides.size()-1];
        for (int i = 1; i < overrides.length; i++) {
            DFMethod method0 = _overrides.get(i);
            DFMethod method1 = method0.parameterize(types);
            overrides[i-1] = method1;
            if (method0 != method1) {
                changed = true;
            }
        }
        if (changed) {
            return new DFMethod(
                _klass, _childSpace, _name, _static,
                methodType, overrides);
        }
        return this;
    }
}
