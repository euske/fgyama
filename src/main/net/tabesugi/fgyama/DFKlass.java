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

    public static final int MAX_REIFY_DEPTH = 2;

    // These fields are available upon construction.
    private String _name;
    private DFTypeSpace _outerSpace;
    private DFKlass _outerKlass;  // can be the same as outerSpace, or null.
    private DFVarScope _outerScope;
    private KlassScope _klassScope;

    // These fields are available only for generic klasses.
    private ConsistentHashMap<String, DFKlass> _typeSlots = null;
    private ConsistentHashMap<String, DFKlass> _reifiedKlasses = null;

    // These fields are available only for parameterized klasses.
    private DFKlass _genericKlass = null;
    private Map<String, DFKlass> _paramTypes = null;
    private int _reifyDepth = 0;

    // Normal constructor.
    public DFKlass(
        String name,
        DFTypeSpace outerSpace, DFKlass outerKlass, DFVarScope outerScope) {
        super(name, outerSpace);

        _name = name;
        _outerSpace = outerSpace;
        _outerKlass = outerKlass;
        _outerScope = outerScope;
        _klassScope = new KlassScope(outerScope, name);
        if (_outerKlass != null) {
            _reifyDepth = _outerKlass._reifyDepth;
        }
    }

    // Protected constructor for a parameterized klass.
    protected DFKlass(DFKlass genericKlass, Map<String, DFKlass> paramTypes) {
        // A parameterized Klass has its own separate typespace
        // that is NOT accessible from the outside.
        this(genericKlass.getName() + DFTypeSpace.getReifiedName(paramTypes),
             genericKlass._outerSpace, genericKlass._outerKlass, genericKlass._outerScope);

        _genericKlass = genericKlass;
        _paramTypes = paramTypes;
        if (_genericKlass._outerKlass != null) {
            _reifyDepth = _genericKlass._outerKlass._reifyDepth;
        }
        for (DFKlass klass : paramTypes.values()) {
            _reifyDepth = Math.max(_reifyDepth, klass._reifyDepth+1);
        }
    }

    @Override
    public String toString() {
        if (_typeSlots != null) {
            return ("<DFKlass("+this.getTypeName()+" "+
                    Utils.join(_typeSlots.keys())+")>");
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
        if (_typeSlots != null) {
            StringBuilder b = new StringBuilder();
            for (String k : _typeSlots.keys()) {
                if (0 < b.length()) {
                    b.append(",");
                }
                b.append(_typeSlots.get(k).getTypeName());
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
        if (_typeSlots != null) {
            // If this is a generic klass,
            DFKlass defaultKlass = _typeSlots.get(id);
            if (defaultKlass != null) return defaultKlass;
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

        if (klass instanceof DFLambdaKlass) {
            return ((DFLambdaKlass)klass).canConvertTo(this);
        } else if (klass instanceof DFMethodRefKlass) {
            return ((DFMethodRefKlass)klass).canConvertTo(this);
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
    public DFKlass getReifiedKlass(DFKlass[] argTypes) {
        //Logger.info("DFKlass.getReifiedKlass:", this, Utils.join(argTypes));
        assert _typeSlots != null;
        assert _paramTypes == null;
        assert argTypes.length <= _typeSlots.size();
        HashMap<String, DFKlass> paramTypes = new HashMap<String, DFKlass>();
        int i = 0;
        for (String key : _typeSlots.keys()) {
            DFKlass type = _typeSlots.get(key);
            if (argTypes != null && i < argTypes.length) {
                DFKlass argType = argTypes[i];
                if (argType._reifyDepth < MAX_REIFY_DEPTH) {
                    type = argType;
                }
            }
            paramTypes.put(key, type);
            i++;
        }
        // Try to reuse an existing class.
        String name = DFTypeSpace.getReifiedName(paramTypes);
        DFKlass klass = _reifiedKlasses.get(name);
        if (klass == null) {
            klass = this.parameterize(paramTypes);
            _reifiedKlasses.put(name, klass);
        }
        return klass;
    }

    public boolean isResolved() {
        if (_paramTypes != null) {
            for (DFKlass klass : _paramTypes.values()) {
                if (!klass.isResolved()) return false;
            }
        }
        return true;
    }

    public String getName() {
        return _name;
    }

    public DFKlass getGenericKlass() {
        return _genericKlass;
    }

    public DFTypeSpace getOuterSpace() {
        return _outerSpace;
    }

    public DFKlass getOuterKlass() {
        return _outerKlass;
    }

    public DFVarScope getOuterScope() {
        return _outerScope;
    }

    public DFVarScope getKlassScope() {
        return _klassScope;
    }

    public DFMethod getFuncMethod() {
        if (!this.isInterface()) return null;
        DFMethod funcMethod = null;
        for (DFMethod method : this.getMethods()) {
            if (method.isAbstract()) {
                if (funcMethod != null) return null; // More than one.
                funcMethod = method;
            }
        }
        return funcMethod;
    }

    protected DFMethod findMethod1(
        DFMethod.CallStyle callStyle, String id, DFType[] argTypes) {
        //Logger.info("DFKlass.findMethod1", this, callStyle, id, Utils.join(argTypes));
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
                    DFMethod method = method1.getReifiedMethod(typeMap);
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
        DFMethod.CallStyle callStyle, String id, DFType[] argTypes) {
        DFMethod method = this.findMethod1(callStyle, id, argTypes);
        if (method != null) return method;
        DFKlass outerKlass = this.getOuterKlass();
        if (outerKlass != null) {
            method = outerKlass.findMethod(callStyle, id, argTypes);
            if (method != null) return method;
        }
        DFKlass baseKlass = this.getBaseKlass();
        if (baseKlass != null) {
            method = baseKlass.findMethod(callStyle, id, argTypes);
            if (method != null) return method;
        }
        DFKlass[] baseIfaces = this.getBaseIfaces();
        if (baseIfaces != null) {
            for (DFKlass iface : baseIfaces) {
                method = iface.findMethod(callStyle, id, argTypes);
                if (method != null) return method;
            }
        }
        return null;
    }

    public DFMethod findMethod(
        DFMethod.CallStyle callStyle, SimpleName name, DFType[] argTypes) {
        String id = (name == null)? null : name.getIdentifier();
        return this.findMethod(callStyle, id, argTypes);
    }

    public DFMethod createFallbackMethod(String name, DFType[] argTypes) {
        return new FallbackMethod(this, name, argTypes);
    }

    public FieldRef getField(SimpleName name) {
        return this.getField(name.getIdentifier());
    }

    public FieldRef getField(String id) {
        DFKlass baseKlass = this.getBaseKlass();
        if (baseKlass != null) {
            FieldRef ref = baseKlass.getField(id);
            if (ref != null) return ref;
        }
        DFKlass[] baseIfaces = this.getBaseIfaces();
        if (baseIfaces != null) {
            for (DFKlass iface : baseIfaces) {
                FieldRef ref = iface.getField(id);
                if (ref != null) return ref;
            }
        }
        return null;
    }

    public abstract boolean isInterface();
    public abstract boolean isEnum();
    public abstract DFKlass getBaseKlass();
    public abstract DFKlass[] getBaseIfaces();
    public abstract DFMethod[] getMethods();
    public abstract FieldRef[] getFields();

    public void writeXML(XMLStreamWriter writer)
        throws XMLStreamException {
        writer.writeAttribute("name", this.getTypeName());
        if (this.isInterface()) {
            writer.writeAttribute("interface", Boolean.toString(true));
        }
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
                ConsistentHashMap<String, DFKlass> typeSlots = _genericKlass._typeSlots;
                for (String key : typeSlots.keys()) {
                    DFKlass paramType = _paramTypes.get(key);
                    writer.writeStartElement("param");
                    writer.writeAttribute("name", key);
                    writer.writeAttribute("type", paramType.getTypeName());
                    writer.writeEndElement();
                }
            }
        }
        if (_reifiedKlasses != null) {
            for (DFKlass pklass : _reifiedKlasses.values()) {
                writer.writeStartElement("parameterized");
                writer.writeAttribute("type", pklass.getTypeName());
                writer.writeEndElement();
            }
        }
        for (FieldRef field : this.getFields()) {
            field.writeXML(writer);
        }
    }

    protected boolean isRecursive(DFKlass genericKlass) {
        assert genericKlass != null;
        if (_paramTypes == null) return false;
        for (DFKlass klass : _paramTypes.values()) {
            if (klass._genericKlass == genericKlass) return true;
            if (klass.isRecursive(genericKlass)) return true;
        }
        return false;
    }

    /// For constructions.

    protected abstract DFKlass parameterize(Map<String, DFKlass> paramTypes);

    protected void setTypeSlots(ConsistentHashMap<String, DFKlass> typeSlots) {
        assert typeSlots != null;
        assert _typeSlots == null;
        assert _paramTypes == null;
        assert _reifiedKlasses == null;
        _typeSlots = typeSlots;
        _reifiedKlasses = new ConsistentHashMap<String, DFKlass>();
    }

    @Override
    protected void dumpContents(PrintStream out, String indent) {
        super.dumpContents(out, indent);
        if (_typeSlots != null) {
            for (Map.Entry<String,DFKlass> e : _typeSlots.entrySet()) {
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
