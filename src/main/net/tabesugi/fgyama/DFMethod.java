//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import javax.xml.stream.*;


//  DFMethod
//
public abstract class DFMethod extends DFTypeSpace implements Comparable<DFMethod> {

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

    // These fields are available upon construction.
    private DFKlass _klass;
    private CallStyle _callStyle;
    private boolean _abstract;
    private String _methodId;
    private String _methodName;

    // These fields are available after setMapTypes(). (Stage3)
    private ConsistentHashMap<String, DFMapType> _mapTypes = null;
    private ConsistentHashMap<String, DFMethod> _concreteMethods = null;

    // These fields are available only for parameterized methods;
    private DFMethod _genericMethod = null;
    private ConsistentHashMap<String, DFKlass> _paramTypes = null;

    private ConsistentHashSet<DFMethod> _callers =
        new ConsistentHashSet<DFMethod>();

    // List of subclass' methods overriding this method.
    private List<DFMethod> _overriders = new ArrayList<DFMethod>();
    // List of superclass' methods being overriden by this method.
    private List<DFMethod> _overriding = new ArrayList<DFMethod>();

    public DFMethod(
        DFKlass klass, CallStyle callStyle, boolean isAbstract,
        String methodId, String methodName) {
        super(methodId, klass);
        _klass = klass;
        _callStyle = callStyle;
        _abstract = isAbstract;
        _methodId = methodId;
        _methodName = methodName;
    }

    // Constructor for a parameterized method.
    protected DFMethod(
        DFMethod genericMethod, DFKlass[] paramTypes)
        throws InvalidSyntax {
        super(genericMethod._methodId + DFTypeSpace.getParamName(paramTypes),
              genericMethod._klass);
        _klass = genericMethod._klass;
        _callStyle = genericMethod._callStyle;
        _abstract = genericMethod._abstract;
        _methodId = genericMethod._methodId + DFTypeSpace.getParamName(paramTypes);
        _methodName = genericMethod._methodName;

        _genericMethod = genericMethod;
        _paramTypes = new ConsistentHashMap<String, DFKlass>();
        List<DFMapType> mapTypes = genericMethod._mapTypes.values();
        for (int i = 0; i < paramTypes.length; i++) {
            DFMapType mapType = mapTypes.get(i);
            DFKlass paramType = paramTypes[i];
            assert mapType != null;
            assert paramType != null;
            _paramTypes.put(mapType.getName(), paramType);
        }
    }

    protected abstract DFMethod parameterize(DFKlass[] paramTypes)
        throws InvalidSyntax;

    @Override
    public String toString() {
        if (_mapTypes != null) {
            return ("<DFMethod("+this.getSignature()+":"+Utils.join(_mapTypes.keys())+")>");
        }
        return ("<DFMethod("+this.getSignature()+")>");
    }

    @Override
    public int compareTo(DFMethod method) {
        if (this == method) return 0;
        return _methodName.compareTo(method._methodName);
    }

    public boolean equals(DFMethod method) {
        if (!_methodName.equals(method._methodName)) return false;
        return this.getFuncType().equals(method.getFuncType());
    }

    public boolean isGeneric() {
        return _mapTypes != null && 0 < _mapTypes.size();
    }

    public void setMapTypes(DFMapType[] mapTypes)
        throws InvalidSyntax {
        if (mapTypes == null) return;
        assert _mapTypes == null;
        _mapTypes = new ConsistentHashMap<String, DFMapType>();
        for (DFMapType mapType : mapTypes) {
            _mapTypes.put(mapType.getName(), mapType);
        }
        _concreteMethods = new ConsistentHashMap<String, DFMethod>();
    }

    // Creates a parameterized method.
    public DFMethod getConcreteMethod(Map<DFMapType, DFKlass> typeMap) {
        if (_mapTypes == null) return this;
        //Logger.info("DFMethod.getConcreteMethod:", this, typeMap);
        List<DFMapType> mapTypes = _mapTypes.values();
        DFKlass[] paramTypes = new DFKlass[mapTypes.size()];
        for (int i = 0; i < mapTypes.size(); i++) {
            DFMapType mapType = mapTypes.get(i);
            if (typeMap != null && typeMap.containsKey(mapType)) {
                paramTypes[i] = typeMap.get(mapType);
            } else {
                paramTypes[i] = mapType.toKlass();
            }
        }
        String name = DFTypeSpace.getParamName(paramTypes);
        DFMethod method = _concreteMethods.get(name);
        if (method == null) {
            try {
                method = this.parameterize(paramTypes);
                _concreteMethods.put(name, method);
            } catch (InvalidSyntax e) {
            }
        }
        return method;
    }

    public DFMethod getGenericMethod() {
        return _genericMethod;
    }

    public List<DFMethod> getConcreteMethods() {
        assert _concreteMethods != null;
        return _concreteMethods.values();
    }

    public DFKlass getKlass() {
        return _klass;
    }

    public String getName() {
        return _methodName;
    }

    public String getMethodId() {
        return _methodId;
    }

    public String getSignature() {
        String name;
        if (_klass != null) {
            name = _klass.getTypeName()+"."+_methodName;
        } else {
            name = "!"+_methodName;
        }
        if (this.getFuncType() != null) {
            name += this.getFuncType().getTypeName();
        }
        return name;
    }

    public CallStyle getCallStyle() {
        return _callStyle;
    }

    public boolean isAbstract() {
        return _abstract;
    }

    public abstract DFFunctionType getFuncType();

    public boolean addOverrider(DFMethod method) {
        if (method._callStyle != CallStyle.Lambda &&
            !_methodName.equals(method._methodName)) return false;
        if (!this.getFuncType().equals(method.getFuncType())) return false;
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

    @Override
    public DFKlass getKlass(String id) {
        if (_mapTypes != null) {
            DFMapType mapType = _mapTypes.get(id);
            if (mapType != null) return mapType;
        }
        if (_paramTypes != null) {
            DFKlass paramType = _paramTypes.get(id);
            if (paramType != null) return paramType;
        }
        return super.getKlass(id);
    }

    public int canAccept(DFType[] argTypes, Map<DFMapType, DFKlass> typeMap) {
        return this.getFuncType().canAccept(argTypes, typeMap);
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

}
