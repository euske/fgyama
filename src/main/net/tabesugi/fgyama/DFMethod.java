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
    private ConsistentHashMap<String, DFMapKlass> _mapKlasses = null;
    private ConsistentHashMap<String, DFMethod> _reifiedMethods = null;

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
        DFMethod genericMethod, Map<String, DFKlass> paramTypes) {
        // A parameterized method has its own separate typespace
        // that is NOT accessible from the outside.
        super(genericMethod._methodId + DFTypeSpace.getReifiedName(paramTypes),
              genericMethod._klass);

        _methodId = genericMethod._methodId + DFTypeSpace.getReifiedName(paramTypes);
        _klass = genericMethod._klass;
        _callStyle = genericMethod._callStyle;
        _abstract = genericMethod._abstract;
        _methodName = genericMethod._methodName;

        _genericMethod = genericMethod;
        _paramTypes = paramTypes;
    }

    @Override
    public String toString() {
        if (_mapKlasses != null) {
            return ("<DFMethod("+this.getSignature()+" "+Utils.join(_mapKlasses.keys())+")>");
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
    public DFKlass lookupKlass(String id)
        throws TypeNotFound {
        if (_mapKlasses != null) {
            DFMapKlass mapKlass = _mapKlasses.get(id);
            if (mapKlass != null) return mapKlass;
        }
        if (_paramTypes != null) {
            DFKlass paramType = _paramTypes.get(id);
            if (paramType != null) return paramType;
        }
        return super.lookupKlass(id);
    }

    // Creates a parameterized method.
    public DFMethod getReifiedMethod(Map<DFMapKlass, DFKlass> typeMap) {
        if (_mapKlasses == null) return this;
        //Logger.info("DFMethod.getReifiedMethod:", this, typeMap);
        List<DFMapKlass> mapKlasses = _mapKlasses.values();
        HashMap<String, DFKlass> paramTypes = new HashMap<String, DFKlass>();
        for (int i = 0; i < mapKlasses.size(); i++) {
            DFMapKlass mapKlass = mapKlasses.get(i);
            DFKlass type = mapKlass;
            if (typeMap != null && typeMap.containsKey(mapKlass)) {
                type = typeMap.get(mapKlass);
            }
            paramTypes.put(mapKlass.getName(), type);
        }
        String name = DFTypeSpace.getReifiedName(paramTypes);
        DFMethod method = _reifiedMethods.get(name);
        if (method == null) {
            method = this.parameterize(paramTypes);
            _reifiedMethods.put(name, method);
        }
        return method;
    }

    public boolean isAbstract() {
        return _abstract;
    }

    public boolean isGeneric() {
        return _mapKlasses != null;
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

    public List<DFMethod> getReifiedMethods() {
        assert _reifiedMethods != null;
        return _reifiedMethods.values();
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

    public int canAccept(
        DFType[] argTypes, DFType returnType, Map<DFMapKlass, DFKlass> typeMap)
        throws TypeIncompatible {
        return this.getFuncType().canAccept(argTypes, returnType, typeMap);
    }

    // Returns true if it is an abstract, non-Object method.
    public boolean isFuncMethod() {
        if (_callStyle != CallStyle.InstanceMethod) return false;
        if (!_abstract) return false;
        String sig = this.getFuncType().getTypeName();
        // Check if this is one of Object methods.
        if (_methodName.equals("equals")) {
            return (!sig.equals("(Ljava/lang/Object;)Z"));
        }
        if (_methodName.equals("hashCode")) {
            return (!sig.equals("()I"));
        }
        if (_methodName.equals("toString")) {
            return (!sig.equals("()Ljava/lang/String;"));
        }
        if (_methodName.equals("clone")) {
            return (!sig.equals("()Ljava/lang/Object;"));
        }
        return true;
    }

    // Get the method signature.
    public abstract DFFuncType getFuncType();

    // Parameterize the klass.
    protected abstract DFMethod parameterize(Map<String, DFKlass> paramTypes);

    protected void setMapKlasses(DFMapKlass[] mapKlasses) {
        assert mapKlasses != null;
        assert _mapKlasses == null;
        assert _paramTypes == null;
        assert _reifiedMethods == null;
        _mapKlasses = new ConsistentHashMap<String, DFMapKlass>();
        for (DFMapKlass mapKlass : mapKlasses) {
            _mapKlasses.put(mapKlass.getName(), mapKlass);
        }
        _reifiedMethods = new ConsistentHashMap<String, DFMethod>();
    }

    public void writeXML(XMLStreamWriter writer, int graphId)
        throws InvalidSyntax, EntityNotFound, XMLStreamException {
        writer.writeAttribute("id", this.getSignature());
        writer.writeAttribute("name", this.getName());
        writer.writeAttribute("style", this.getCallStyle().toString());
        if (this.isAbstract()) {
            writer.writeAttribute("abstract", Boolean.toString(true));
        }
        for (DFMethod caller : this.getCallers()) {
            writer.writeStartElement("caller");
            writer.writeAttribute("id", caller.getSignature());
            writer.writeEndElement();
        }
        for (DFMethod overrider : this.getOverriders()) {
            if (overrider == this) continue;
            writer.writeStartElement("overrider");
            writer.writeAttribute("id", overrider.getSignature());
            writer.writeEndElement();
        }
        for (DFMethod overriding : this.getOverridings()) {
            writer.writeStartElement("overriding");
            writer.writeAttribute("id", overriding.getSignature());
            writer.writeEndElement();
        }
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
