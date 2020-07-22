//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import javax.xml.stream.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  FallbackMethod
//  A dummy entry used for an unknown method.
//
class FallbackMethod extends DFMethod {

    DFFuncType _funcType;

    public FallbackMethod(
        DFKlass klass, String methodName, DFType[] argTypes) {
        super(klass, CallStyle.InstanceMethod, false, methodName, methodName);
        _funcType = new DFFuncType(argTypes, DFUnknownType.UNKNOWN);
    }

    protected DFMethod parameterize(Map<String, DFKlass> paramTypes) {
        assert false;
        return null;
    }

    public DFFuncType getFuncType() {
        return _funcType;
    }
}


//  DFKlass
//  Abstract Klass type.
//
//  Usage:
//    1. new DFKlass()
//    2. getXXX(), ...
//
//  Implement:
//    parameterize(paramTypes)
//    build()
//
public abstract class DFKlass extends DFTypeSpace implements DFType {

    // LoadState for tracking the current klass status.
    private enum LoadState {
        Unloaded,
        Loading,
        Loaded,
    };
    private LoadState _state = LoadState.Unloaded;

    // These fields are available upon construction.
    private String _name;
    private DFTypeSpace _outerSpace;

    // List of fields.
    private List<FieldRef> _fields =
        new ArrayList<FieldRef>();
    private Map<String, FieldRef> _id2field =
        new HashMap<String, FieldRef>();
    // List of methods.
    private List<DFMethod> _methods =
        new ArrayList<DFMethod>();
    private Map<String, DFMethod> _id2method =
        new HashMap<String, DFMethod>();

    // These fields are available only for generic klasses.
    private ConsistentHashMap<String, DFMapType> _mapTypes = null;
    private ConsistentHashMap<String, DFKlass> _concreteKlasses = null;

    // These fields are available only for parameterized klasses.
    private DFKlass _genericKlass = null;
    private Map<String, DFKlass> _paramTypes = null;

    // Normal constructor.
    public DFKlass(
        String name, DFTypeSpace outerSpace) {
        super(name, outerSpace);

        _name = name;
        _outerSpace = outerSpace;
    }

    // Protected constructor for a parameterized klass.
    protected DFKlass(DFKlass genericKlass, Map<String, DFKlass> paramTypes) {
        // A parameterized Klass has its own separate typespace
        // that is NOT accessible from the outside.
        this(genericKlass.getName() + DFTypeSpace.getConcreteName(paramTypes),
             genericKlass.getOuterSpace());

        _genericKlass = genericKlass;
        _paramTypes = paramTypes;
    }

    @Override
    public String toString() {
        if (_mapTypes != null) {
            return ("<DFKlass("+this.getTypeName()+" "+
                    Utils.join(_mapTypes.keys())+")>");
        } else {
            return ("<DFKlass("+this.getTypeName()+")>");
        }
    }

    @Override
    public boolean equals(DFType type) {
        return (this == type);
    }

    @Override
    public String getTypeName() {
        if (_mapTypes != null) {
            String[] keys = new String[_mapTypes.size()];
            _mapTypes.keySet().toArray(keys);
            Arrays.sort(keys);
            StringBuilder b = new StringBuilder();
            for (String k : keys) {
                if (0 < b.length()) {
                    b.append(",");
                }
                b.append(k);
            }
            return "L"+_outerSpace.getSpaceName()+_name+"<"+b.toString()+">;";
        } else {
            return "L"+_outerSpace.getSpaceName()+_name+";";
        }
    }

    @Override
    public DFKlass toKlass() {
        return this;
    }

    @Override
    public DFKlass getKlass(String id) {
        if (_mapTypes != null) {
            // If this is a generic klass,
            DFMapType mapType = _mapTypes.get(id);
            if (mapType != null) return mapType;
            return super.getKlass(id);
        }

        if (_paramTypes != null) {
            DFKlass paramType = _paramTypes.get(id);
            if (paramType != null) return paramType;
        }

        DFKlass klass = super.getKlass(id);
        if (klass != null) return klass;

        DFKlass baseKlass = this.getBaseKlass();
        if (baseKlass != null) {
            klass = baseKlass.getKlass(id);
            if (klass != null) return klass;
        }
        DFKlass[] baseIfaces = this.getBaseIfaces();
        if (baseIfaces != null) {
            for (DFKlass iface : baseIfaces) {
                if (iface != null) {
                    klass = iface.getKlass(id);
                    if (klass != null) return klass;
                }
            }
        }

        return null;
    }

    @Override
    public int canConvertFrom(DFType type, Map<DFMapType, DFKlass> typeMap)
        throws TypeIncompatible {
        if (type instanceof DFNullType) return 0;
        DFKlass klass = type.toKlass();
        if (klass == null) throw new TypeIncompatible(this, type);
        return this.canConvertFrom(klass, typeMap);
    }

    public int canConvertFrom(DFKlass klass, Map<DFMapType, DFKlass> typeMap)
        throws TypeIncompatible {
        if (this == klass) return 0;
        if (_genericKlass != null && _genericKlass == klass._genericKlass) {
            // A<S1,S2,...> canConvertFrom A<T1,T2,...>?
            // == Si canConvertFrom T1
            assert _paramTypes != null && klass._paramTypes != null;
            assert _paramTypes.size() == klass._paramTypes.size();
            int dist = 0;
            for (Map.Entry<String,DFKlass> e : _paramTypes.entrySet()) {
                String k = e.getKey();
                DFKlass type0 = e.getValue();
                DFKlass type1 = klass._paramTypes.get(k);
                assert type1 != null;
                dist += type0.canConvertFrom(type1, typeMap);
            }
            return dist;
        }

        if (klass instanceof DFLambdaKlass ||
            klass instanceof DFMethodRefKlass) {
            if (this.isFuncInterface()) {
                return 0;
            } else {
                throw new TypeIncompatible(this, klass);
            }
        }

        DFKlass baseKlass = klass.getBaseKlass();
        if (baseKlass != null) {
            try {
                return this.canConvertFrom(baseKlass, typeMap)+1;
            } catch (TypeIncompatible e) {
            }
        }

        DFKlass[] baseIfaces = klass.getBaseIfaces();
        if (baseIfaces != null) {
            for (DFKlass iface : baseIfaces) {
                try {
                    return this.canConvertFrom(iface, typeMap)+1;
                } catch (TypeIncompatible e) {
                }
            }
        }

        throw new TypeIncompatible(this, klass);
    }

    // Creates a parameterized klass.
    public DFKlass getDefaultKlass() {
        if (_mapTypes == null) return this;
        return this.getConcreteKlass(new DFKlass[] {});
    }

    public DFKlass getConcreteKlass(DFKlass[] argTypes) {
        //Logger.info("DFKlass.getConcreteKlass:", this, Utils.join(argTypes));
        assert _mapTypes != null;
        assert _paramTypes == null;
        assert argTypes.length <= _mapTypes.size();
        List<DFMapType> mapTypes = _mapTypes.values();
        HashMap<String, DFKlass> paramTypes = new HashMap<String, DFKlass>();
        for (int i = 0; i < mapTypes.size(); i++) {
            DFMapType mapType = mapTypes.get(i);
            assert mapType != null;
            DFKlass type;
            if (argTypes != null && i < argTypes.length) {
                type = argTypes[i];
            } else {
                type = mapType.toKlass();
            }
            paramTypes.put(mapType.getName(), type);
        }
        String name = DFTypeSpace.getConcreteName(paramTypes);
        DFKlass klass = _concreteKlasses.get(name);
        if (klass == null) {
            klass = this.parameterize(paramTypes);
            _concreteKlasses.put(name, klass);
        }
        return klass;
    }

    public boolean isGeneric() {
        return _mapTypes != null;
    }

    public String getName() {
        return _name;
    }

    public DFTypeSpace getOuterSpace() {
        return _outerSpace;
    }

    public DFKlass getGenericKlass() {
        return _genericKlass;
    }

    public boolean isFuncInterface() {
        this.load();
        if (!this.isInterface()) return false;
        // Count the number of abstract methods.
        int n = 0;
        for (DFMethod method : this.getMethods()) {
            if (method.isAbstract()) {
                n++;
            }
        }
        return (n == 1);
    }

    public void writeXML(XMLStreamWriter writer)
        throws XMLStreamException {
        this.load();
        writer.writeAttribute("name", this.getTypeName());
        writer.writeAttribute("interface", Boolean.toString(this.isInterface()));
        DFKlass baseKlass = this.getBaseKlass();
        if (baseKlass != null) {
            writer.writeAttribute("extends", baseKlass.getTypeName());
        }
        DFKlass[] baseIfaces = this.getBaseIfaces();
        if (baseIfaces != null && 0 < baseIfaces.length) {
            StringBuilder b = new StringBuilder();
            for (DFKlass iface : baseIfaces) {
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
                for (int i = 0; i < mapTypes.size(); i++) {
                    String name = mapTypes.get(i).getName();
                    DFKlass paramType = _paramTypes.get(name);
                    writer.writeStartElement("param");
                    writer.writeAttribute("name", name);
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
        for (FieldRef field : this.getFields()) {
            field.writeXML(writer);
        }
    }

    public List<FieldRef> getFields() {
        this.load();
        return _fields;
    }

    public DFRef getField(SimpleName name) {
        this.load();
        return this.getField(name.getIdentifier());
    }

    public DFRef getField(String id) {
        this.load();
        return _id2field.get(id);
    }

    public List<DFMethod> getMethods() {
        this.load();
        return _methods;
    }

    public DFMethod getMethod(String key) {
        this.load();
        return _id2method.get(key);
    }

    public DFMethod getFuncMethod() {
        this.load();
        for (DFMethod method : this.getMethods()) {
            if (method.isAbstract()) return method;
        }
        return null;
    }

    public DFMethod findMethod(
        DFMethod.CallStyle callStyle, String id, DFType[] argTypes) {
        //Logger.info("DFKlass.findMethod", this, callStyle, id, Utils.join(argTypes));
        this.load();
        int bestDist = -1;
        DFMethod bestMethod = null;
        for (DFMethod method1 : this.getMethods()) {
            DFMethod.CallStyle callStyle1 = method1.getCallStyle();
            if (!(callStyle == callStyle1 ||
                  (callStyle == DFMethod.CallStyle.InstanceOrStatic &&
                   (callStyle1 == DFMethod.CallStyle.InstanceMethod ||
                    callStyle1 == DFMethod.CallStyle.StaticMethod)))) continue;
            if (id != null && !id.equals(method1.getName())) continue;
            Map<DFMapType, DFKlass> typeMap = new HashMap<DFMapType, DFKlass>();
            try {
                int dist = method1.canAccept(argTypes, typeMap);
                if (bestDist < 0 || dist < bestDist) {
                    DFMethod method = method1.getConcreteMethod(typeMap);
                    if (method != null) {
                        bestDist = dist;
                        bestMethod = method;
                    }
                }
            } catch (TypeIncompatible e) {
                continue;
            }
        }
        return bestMethod;
    }

    public DFMethod findMethod(
        DFMethod.CallStyle callStyle, SimpleName name, DFType[] argTypes) {
        this.load();
        String id = (name == null)? null : name.getIdentifier();
        return this.findMethod(callStyle, id, argTypes);
    }

    public DFMethod addFallbackMethod(String name, DFType[] argTypes) {
        this.load();
        DFMethod method = new FallbackMethod(this, name, argTypes);
        // Do not adds to _methods because it shouldn't be analyzed.
        _id2method.put(name, method);
        return method;
    }

    public abstract boolean isInterface();
    public abstract boolean isEnum();
    public abstract DFKlass getBaseKlass();
    public abstract DFKlass[] getBaseIfaces();

    /// For constructions.

    protected void load() {
        // an unspecified parameterized klass cannot be loaded.
        assert _mapTypes == null;
        if (_state == LoadState.Unloaded) {
            _state = LoadState.Loading;
            //Logger.info("build:", this);
            this.build();
            _state = LoadState.Loaded;
        }
    }

    protected abstract void build();

    protected abstract DFKlass parameterize(Map<String, DFKlass> paramTypes);

    protected void setMapTypes(DFMapType[] mapTypes) {
        assert mapTypes != null;
        assert _mapTypes == null;
        assert _paramTypes == null;
        assert _concreteKlasses == null;
        _mapTypes = new ConsistentHashMap<String, DFMapType>();
        for (DFMapType mapType : mapTypes) {
            _mapTypes.put(mapType.getName(), mapType);
        }
        _concreteKlasses = new ConsistentHashMap<String, DFKlass>();
    }

    protected void setMapTypeFinder(DFTypeFinder finder) {
        if (_mapTypes != null) {
            for (DFMapType mapType : _mapTypes.values()) {
                mapType.setFinder(finder);
            }
        }
    }

    protected DFRef addField(
        DFType type, SimpleName name, boolean isStatic) {
        return this.addField(type, name.getIdentifier(), isStatic);
    }

    protected DFRef addField(
        DFType type, String id, boolean isStatic) {
        return this.addField(new FieldRef(type, id, isStatic));
    }

    protected DFRef addField(FieldRef ref) {
        //Logger.info("DFKlass.addField:", ref);
        _fields.add(ref);
        _id2field.put(ref.getName(), ref);
        return ref;
    }

    protected DFMethod addMethod(DFMethod method, String key) {
        //Logger.info("DFKlass.addMethod:", method);
        _methods.add(method);
        if (key != null) {
            _id2method.put(key, method);
        }
        return method;
    }

    @Override
    protected void dumpContents(PrintStream out, String indent) {
        super.dumpContents(out, indent);
        if (_mapTypes != null) {
            for (Map.Entry<String,DFMapType> e : _mapTypes.entrySet()) {
                out.println(indent+"map: "+e.getKey()+" "+e.getValue());
            }
        }
        if (_paramTypes != null) {
            for (Map.Entry<String,DFKlass> e : _paramTypes.entrySet()) {
                out.println(indent+"param: "+e.getKey()+" "+e.getValue());
            }
        }
        if (_genericKlass != null) {
            _genericKlass.dump(out, indent);
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

}
