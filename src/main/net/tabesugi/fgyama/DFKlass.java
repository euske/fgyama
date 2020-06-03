//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import javax.xml.stream.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFKlass
//  Abstract Klass type.
//  Implement: isSubclassOf(klass, typeMap)
//
public abstract class DFKlass extends DFTypeSpace implements DFType {

    private enum LoadState {
	Unloaded,
	Loading,
	Loaded,
    };
    private LoadState _state = LoadState.Unloaded;

    // These fields are available upon construction.
    private String _name;
    private DFTypeSpace _outerSpace;
    private DFVarScope _outerScope;
    private DFKlass _outerKlass;  // can be the same as outerSpace, or null.

    // These fields are available after initScope().
    private KlassScope _klassScope;
    private List<FieldRef> _fields = null;
    private List<DFMethod> _methods = null;
    private Map<String, DFMethod> _id2method = null;

    // These fields are available after setMapTypes(). (Stage1)
    private ConsistentHashMap<String, DFMapType> _mapTypes = null;
    private ConsistentHashMap<String, DFKlass> _concreteKlasses = null;

    // This field is available after setFinder(). (Stage2)
    private DFTypeFinder _finder = null;

    // These fields are available only for parameterized klasses.
    private DFKlass _genericKlass = null;
    private ConsistentHashMap<String, DFKlass> _paramTypes = null;

    public DFKlass(
        String name, DFTypeSpace outerSpace, DFVarScope outerScope,
        DFKlass outerKlass) {
	super(name, outerSpace);
        _name = name;
        _outerSpace = outerSpace;
	_outerScope = outerScope;
	_outerKlass = outerKlass;
    }

    protected DFKlass(DFKlass genericKlass, DFKlass[] paramTypes) {
        this(genericKlass.getName() + DFTypeSpace.getParamName(paramTypes),
             genericKlass.getOuterSpace(),
             genericKlass.getOuterScope(),
             genericKlass._outerKlass);
        // A parameterized Klass is NOT accessible from
        // the outer namespace but it creates its own subspace.
	_finder = genericKlass._finder;
        _genericKlass = genericKlass;
        _paramTypes = new ConsistentHashMap<String, DFKlass>();
	List<DFMapType> mapTypes = genericKlass.getMapTypes();
        for (int i = 0; i < paramTypes.length; i++) {
            DFMapType mapType = mapTypes.get(i);
            assert mapType != null;
            DFKlass paramType = paramTypes[i];
            assert paramType != null;
            assert !(paramType instanceof DFMapType);
            _paramTypes.put(mapType.getName(), paramType);
        }
    }

    @Override
    public String toString() {
        if (_mapTypes != null) {
            return ("<DFKlass("+this.getTypeName()+
		    ":"+Utils.join(_mapTypes.keys())+")>");
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
        return "L"+_outerSpace.getSpaceName()+_name+";";
    }

    @Override
    public DFKlass toKlass() {
        return this;
    }

    @Override
    public int canConvertFrom(DFType type, Map<DFMapType, DFKlass> typeMap) {
        if (type instanceof DFNullType) return 0;
        DFKlass klass = type.toKlass();
        if (klass == null) return -1;
        // type is-a this.
        return klass.isSubclassOf(this, typeMap);
    }

    public int isSubclassOf(DFKlass klass, Map<DFMapType, DFKlass> typeMap) {
        if (this == klass) return 0;
        if (_genericKlass != null && _genericKlass == klass.getGenericKlass()) {
            // A<T> isSubclassOf B<S>?
            // types0: T
            List<DFKlass> types0 = _paramTypes.values();
            assert types0 != null;
            // types1: S
            List<DFKlass> types1 = klass._paramTypes.values();
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

        DFKlass baseKlass = this.getBaseKlass();
        if (baseKlass != null) {
            int dist = baseKlass.isSubclassOf(klass, typeMap);
            if (0 <= dist) return dist+1;
        }

        DFKlass[] baseIfaces = this.getBaseIfaces();
        if (baseIfaces != null) {
            for (DFKlass iface : baseIfaces) {
                int dist = iface.isSubclassOf(klass, typeMap);
                if (0 <= dist) return dist+1;
            }
        }
        return -1;
    }

    public boolean isDefined() {
        return (_state == LoadState.Loaded);
    }

    public boolean isInterface() {
        return false;
    }

    public String getName() {
	return _name;
    }

    public DFTypeSpace getOuterSpace() {
	return _outerSpace;
    }

    public DFVarScope getOuterScope() {
	return _outerScope;
    }

    public DFVarScope getKlassScope() {
        return _klassScope;
    }

    public DFKlass getGenericKlass() {
	return _genericKlass;
    }

    public DFKlass getBaseKlass() {
        if (this == DFBuiltinTypes.getObjectKlass()) {
            return null;
        } else {
            return DFBuiltinTypes.getObjectKlass();
        }
    }

    public DFKlass[] getBaseIfaces() {
        return null;
    }

    public boolean isFuncInterface() {
        assert this.isDefined();
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

    public boolean isGeneric() {
        return _mapTypes != null;
    }

    public boolean isEnum() {
        return false;
    }

    public void setFinder(DFTypeFinder finder) {
        assert _state == LoadState.Unloaded;
        //assert _finder == null || _finder == finder;
	_finder = finder;
    }

    public DFTypeFinder getFinder() {
        if (_outerKlass != null) {
            assert _finder == null;
            return new DFTypeFinder(this, _outerKlass.getFinder());
        } else {
            //assert _finder != null;
            return new DFTypeFinder(this, _finder);
        }
    }

    public void load()
        throws InvalidSyntax {
        // an unspecified parameterized klass cannot be loaded.
        if (_state != LoadState.Unloaded) return;
        _state = LoadState.Loading;
        this.build();
        _state = LoadState.Loaded;
    }

    protected void build()
        throws InvalidSyntax {
        if (_outerKlass != null) {
            _outerKlass.load();
        }
    }

    // Creates a parameterized klass.
    public DFKlass getConcreteKlass(DFKlass[] paramTypes)
	throws InvalidSyntax {
        //Logger.info("DFKlass.getConcreteKlass:", this, Utils.join(paramTypes));
        List<DFMapType> mapTypes = this.getMapTypes();
        assert _paramTypes == null;
        assert paramTypes.length <= mapTypes.size();
        if (paramTypes.length < mapTypes.size()) {
            DFKlass[] types = new DFKlass[mapTypes.size()];
            for (int i = 0; i < mapTypes.size(); i++) {
                if (i < paramTypes.length) {
                    types[i] = paramTypes[i];
                } else {
                    types[i] = mapTypes.get(i).toKlass();
                }
            }
            paramTypes = types;
        }
        String name = DFTypeSpace.getParamName(paramTypes);
        DFKlass klass = _concreteKlasses.get(name);
        if (klass == null) {
            klass = this.parameterize(paramTypes);
            _concreteKlasses.put(name, klass);
        }
        return klass;
    }

    public void writeXML(XMLStreamWriter writer)
        throws XMLStreamException {
        assert this.isDefined();
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
		List<DFMapType> mapTypes = _genericKlass.getMapTypes();
		List<DFKlass> paramTypes = _paramTypes.values();
                for (int i = 0; i < paramTypes.size(); i++) {
		    DFMapType mapType = mapTypes.get(i);
                    DFKlass paramType = paramTypes.get(i);
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
        for (FieldRef field : this.getFields()) {
            field.writeXML(writer);
        }
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
        DFKlass klass = super.getKlass(id);
        if (klass != null) return klass;
        if (this.isDefined()) {
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
        }
        return null;
    }

    // Field/Method-related things.

    protected DFKlass parameterize(DFKlass[] paramTypes)
	throws InvalidSyntax {
        return this;
    }

    protected void initScope() {
        _klassScope = new KlassScope(_outerScope, _name);
        _fields = new ArrayList<FieldRef>();
        _methods = new ArrayList<DFMethod>();
        _id2method = new HashMap<String, DFMethod>();
    }

    protected void setMapTypes(DFMapType[] mapTypes) {
        assert _mapTypes == null;
        assert _paramTypes == null;
        assert _concreteKlasses == null;
	_mapTypes = new ConsistentHashMap<String, DFMapType>();
	for (DFMapType mapType : mapTypes) {
	    _mapTypes.put(mapType.getName(), mapType);
        }
        _concreteKlasses = new ConsistentHashMap<String, DFKlass>();
    }

    protected List<DFMapType> getMapTypes() {
        assert _mapTypes != null;
        return _mapTypes.values();
    }

    public List<FieldRef> getFields() {
        assert _fields != null;
	return _fields;
    }

    public DFRef getField(SimpleName name) {
	return this.getField(name.getIdentifier());
    }

    public DFRef getField(String id) {
	assert _klassScope != null;
	return _klassScope.getField(id);
    }

    public List<DFMethod> getMethods() {
        assert _methods != null;
	return _methods;
    }

    public DFMethod getMethod(String key) {
        assert _id2method != null;
        return _id2method.get(key);
    }

    public DFMethod getFuncMethod() {
        for (DFMethod method : this.getMethods()) {
            if (method.isAbstract()) return method;
        }
	return null;
    }

    public DFMethod findMethod(
        DFMethod.CallStyle callStyle, String id, DFType[] argTypes) {
        //Logger.info("DFKlass.findMethod", this, callStyle, id, Utils.join(argTypes));
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
            int dist = method1.canAccept(argTypes, typeMap);
            if (dist < 0) continue;
            if (bestDist < 0 || dist < bestDist) {
		DFMethod method = method1.getConcreteKlass(typeMap);
		if (method != null) {
		    bestDist = dist;
		    bestMethod = method;
		}
            }
        }
	if (bestMethod == null && _outerKlass != null) {
	    bestMethod = _outerKlass.findMethod(callStyle, id, argTypes);
	}
        return bestMethod;
    }

    public DFMethod findMethod(
        DFMethod.CallStyle callStyle, SimpleName name, DFType[] argTypes) {
        String id = (name == null)? null : name.getIdentifier();
        return this.findMethod(callStyle, id, argTypes);
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
	    name, name, this.getKlassScope());
        method.setFuncType(new DFFunctionType(argTypes, DFUnknownType.UNKNOWN));
        // Do not adds to _methods because it might be being referenced.
        _id2method.put(name, method);
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
	    DFRef ref = DFKlass.this.getField(id);
	    if (ref != null) return ref;
	    return super.lookupVar(id);
        }

        public DFRef getField(String id) {
            return _id2field.get(id);
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
