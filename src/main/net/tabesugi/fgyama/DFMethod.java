//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import javax.xml.stream.*;


//  DFMethod
//  Abstract Method type that belongs to a DFKlass.
//
//  Usage:
//    1. new DFMethod()
//    2. getXXX(), ...
//
//  Implement:
//    parameterize(paramTypes)
//    getFuncType()
//
public abstract class DFMethod extends DFTypeSpace implements Comparable<DFMethod> {

    // CallStyle:
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
    private String _methodId;     // Unique identifier for a method.
    private DFKlass _klass;       // Klass it belongs.
    private CallStyle _callStyle; // Calling style.
    private boolean _abstract;    // true if the method is abstract.
    private String _methodName;   // Method name (not unique).

    // These fields are available only for generic methods.
    private ConsistentHashMap<String, DFMapType> _mapTypes = null;
    private ConsistentHashMap<String, DFMethod> _concreteMethods = null;

    // These fields are available only for parameterized methods.
    private DFMethod _genericMethod = null;
    private Map<String, DFKlass> _paramTypes = null;

    // List of callers for this method.
    private ConsistentHashSet<DFMethod> _callers =
        new ConsistentHashSet<DFMethod>();

    // List of subclass' methods overriding this method.
    private List<DFMethod> _overriders = new ArrayList<DFMethod>();
    // List of superclass' methods being overriden by this method.
    private List<DFMethod> _overriding = new ArrayList<DFMethod>();

    // Normal constructor.
    public DFMethod(
        DFKlass klass, CallStyle callStyle, boolean isAbstract,
        String methodId, String methodName) {
        super(methodId, klass);

        _methodId = methodId;
        _klass = klass;
        _callStyle = callStyle;
        _abstract = isAbstract;
        _methodName = methodName;
    }

    // Protected constructor for a parameterized method.
    protected DFMethod(
        DFMethod genericMethod, Map<String, DFKlass> paramTypes)
        throws InvalidSyntax {
        // A parameterized method has its own separate typespace
        // that is NOT accessible from the outside.
        super(genericMethod._methodId + DFTypeSpace.getParamName(paramTypes),
              genericMethod._klass);

        _methodId = genericMethod._methodId + DFTypeSpace.getParamName(paramTypes);
        _klass = genericMethod._klass;
        _callStyle = genericMethod._callStyle;
        _abstract = genericMethod._abstract;
        _methodName = genericMethod._methodName;

        _genericMethod = genericMethod;
        _paramTypes = paramTypes;
    }

    @Override
    public String toString() {
        if (_mapTypes != null) {
            return ("<DFMethod("+this.getSignature()+":"+Utils.join(_mapTypes.keys())+")>");
        }
        return ("<DFMethod("+this.getSignature()+")>");
    }

    public boolean equals(DFMethod method) {
        if (!_methodName.equals(method._methodName)) return false;
        return this.getFuncType().equals(method.getFuncType());
    }

    @Override
    public int compareTo(DFMethod method) {
        if (this == method) return 0;
        return _methodId.compareTo(method._methodId);
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

    // Creates a parameterized method.
    public DFMethod getConcreteMethod(Map<DFMapType, DFKlass> typeMap) {
        assert _paramTypes == null;
        if (_mapTypes == null) return this;
        //Logger.info("DFMethod.getConcreteMethod:", this, typeMap);
        List<DFMapType> mapTypes = _mapTypes.values();
        HashMap<String, DFKlass> paramTypes = new HashMap<String, DFKlass>();
        for (int i = 0; i < mapTypes.size(); i++) {
            DFMapType mapType = mapTypes.get(i);
            DFKlass type;
            if (typeMap != null && typeMap.containsKey(mapType)) {
                type = typeMap.get(mapType);
            } else {
                type = mapType.toKlass();
            }
            paramTypes.put(mapType.getName(), type);
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

    public boolean isAbstract() {
        return _abstract;
    }

    public boolean isGeneric() {
        return _mapTypes != null && 0 < _mapTypes.size();
    }

    public String getMethodId() {
        return _methodId;
    }

    public DFKlass getKlass() {
        return _klass;
    }

    public CallStyle getCallStyle() {
        return _callStyle;
    }

    public String getName() {
        return _methodName;
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

    public DFMethod getGenericMethod() {
        return _genericMethod;
    }

    public List<DFMethod> getConcreteMethods() {
        assert _concreteMethods != null;
        return _concreteMethods.values();
    }

    public void addCaller(DFMethod method) {
        _callers.add(method);
    }

    public ConsistentHashSet<DFMethod> getCallers() {
        return _callers;
    }

    public boolean addOverrider(DFMethod method) {
        if (method._callStyle != CallStyle.Lambda &&
            !_methodName.equals(method._methodName)) return false;
        if (!this.getFuncType().equals(method.getFuncType())) return false;
        //Logger.info("DFMethod.addOverrider:", this, "<-", method);
        _overriders.add(method);
        method._overriding.add(this);
        return true;
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

    private void listOverriders(List<Overrider> overriders, int prio) {
        overriders.add(new Overrider(this, prio));
        for (DFMethod method : _overriders) {
            method.listOverriders(overriders, prio+1);
        }
    }

    public List<DFMethod> getOverridings() {
        return _overriding;
    }

    public int canAccept(DFType[] argTypes, Map<DFMapType, DFKlass> typeMap) {
        return this.getFuncType().canAccept(argTypes, typeMap);
    }

    // Get the method signature.
    public abstract DFFunctionType getFuncType();

    // Parameterize the klass.
    protected abstract DFMethod parameterize(Map<String, DFKlass> paramTypes)
        throws InvalidSyntax;

    protected void setMapTypes(DFMapType[] mapTypes)
        throws InvalidSyntax {
        if (mapTypes == null) return;
        assert _mapTypes == null;
        _mapTypes = new ConsistentHashMap<String, DFMapType>();
        for (DFMapType mapType : mapTypes) {
            _mapTypes.put(mapType.getName(), mapType);
        }
        _concreteMethods = new ConsistentHashMap<String, DFMethod>();
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
