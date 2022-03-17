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

    public static int MaxReifyDepth = 2;

    // These fields are available upon construction.
    private String _name;
    private DFTypeSpace _outerSpace;
    private DFKlass _outerKlass;  // can be the same as outerSpace, or null.
    private DFVarScope _outerScope;
    private KlassScope _klassScope;
    private DFRef _this;

    // These fields are available only for generic klasses.
    private ConsistentHashMap<String, DFKlass> _typeSlots = null;
    private ConsistentHashMap<String, DFKlass> _reifiedKlasses = null;

    // These fields are available only for parameterized klasses.
    private DFKlass _genericKlass = null;
    private Map<String, DFKlass> _paramTypes = null;
    private int _reifyDepth = -1;  // not computed yet.

    // Normal constructor.
    public DFKlass(
        String name,
        DFTypeSpace outerSpace, DFKlass outerKlass, DFVarScope outerScope) {
        super(name, outerSpace);
        assert _outerSpace != this;
        assert _outerKlass != this;

        _name = name;
        _outerSpace = outerSpace;
        _outerKlass = outerKlass;
        _outerScope = outerScope;
        _klassScope = new KlassScope(outerScope, name);
        _this = new ThisRef();
    }

    // Protected constructor for a parameterized klass.
    protected DFKlass(DFKlass genericKlass, Map<String, DFKlass> paramTypes) {
        // A parameterized Klass has its own separate typespace
        // that is NOT accessible from the outside.
        this(genericKlass.getName() + DFTypeSpace.getReifiedName(paramTypes),
             genericKlass._outerSpace, genericKlass._outerKlass, genericKlass._outerScope);

        _genericKlass = genericKlass;
        _paramTypes = paramTypes;
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
                b.append(_typeSlots.get(k).getTypeName());
            }
            return "L"+_outerSpace.getSpaceName()+_name+"<"+b.toString()+">;";
        } else {
            return "L"+_outerSpace.getSpaceName()+_name+";";
        }
    }

    public DFKlass[] getBaseKlasses() {
        List<DFKlass> klasses = new ArrayList<DFKlass>();
        DFKlass klass = this;
        while (klass != null) {
            klasses.add(klass);
            klass = klass.getBaseKlass();
        }
        DFKlass[] a = new DFKlass[klasses.size()];
        klasses.toArray(a);
        return a;
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
    public int canConvertFrom(DFType type, Map<DFMapKlass, DFKlass> typeMap)
        throws TypeIncompatible {
        if (type instanceof DFNullType) return 0;
        DFKlass klass = type.toKlass();
        if (klass == null) throw new TypeIncompatible(this, type);
        return this.canConvertFrom(klass, typeMap);
    }

    public int canConvertFrom(DFKlass klass, Map<DFMapKlass, DFKlass> typeMap)
        throws TypeIncompatible {
        if (this == klass) return 0;
        if (_typeSlots != null && this == klass._genericKlass) {
            // A<S1,S2,...> canConvertFrom A<T1,T2,...>?
            // == Si canConvertFrom T1
            assert klass._paramTypes != null;
            assert _typeSlots.size() == klass._paramTypes.size();
            int dist = 0;
            for (Map.Entry<String,DFKlass> e : _typeSlots.entrySet()) {
                String k = e.getKey();
                DFKlass type0 = e.getValue();
                DFKlass type1 = klass._paramTypes.get(k);
                assert type1 != null;
                dist += type0.canConvertFrom(type1, typeMap);
            }
            return dist;
        } else if (_genericKlass != null && _genericKlass == klass._genericKlass) {
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
        if (_typeSlots == null) return this;
        //Logger.info("DFKlass.getReifiedKlass:", this, Utils.join(argTypes));
        assert _paramTypes == null;
        assert argTypes.length <= _typeSlots.size();
        HashMap<String, DFKlass> paramTypes = new HashMap<String, DFKlass>();
        int i = 0;
        for (String key : _typeSlots.keys()) {
            DFKlass type = _typeSlots.get(key);
            if (argTypes != null && i < argTypes.length) {
                DFKlass argType = argTypes[i];
                if (argType.getReifyDepth() < MaxReifyDepth) {
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

    public DFRef getThisRef() {
        return _this;
    }

    public DFMethod getFuncMethod() {
        if (!this.isInterface()) return null;
        DFMethod funcMethod = null;
        for (DFMethod method : this.getMethods()) {
            if (method.isFuncMethod()) {
                if (funcMethod != null) return null; // More than one.
                funcMethod = method;
            }
        }
        return funcMethod;
    }

    protected DFMethod findMethod1(
        DFMethod.CallStyle callStyle, String id,
        DFType[] argTypes, DFType returnType) {
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
            Map<DFMapKlass, DFKlass> typeMap = new HashMap<DFMapKlass, DFKlass>();
            try {
                int dist = method1.canAccept(argTypes, returnType, typeMap);
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

    public DFMethod lookupMethod(
        DFMethod.CallStyle callStyle, String id,
        DFType[] argTypes, DFType returnType)
        throws MethodNotFound {
        DFMethod method = this.findMethod1(
            callStyle, id, argTypes, returnType);
        if (method != null) return method;

        DFKlass outerKlass = this.getOuterKlass();
        if (outerKlass != null) {
            try {
                return outerKlass.lookupMethod(
                    callStyle, id, argTypes, returnType);
            } catch (MethodNotFound e) {
            }
        }
        DFKlass baseKlass = this.getBaseKlass();
        if (baseKlass != null) {
            try {
                return baseKlass.lookupMethod(
                    callStyle, id, argTypes, returnType);
            } catch (MethodNotFound e) {
            }
        }
        DFKlass[] baseIfaces = this.getBaseIfaces();
        if (baseIfaces != null) {
            for (DFKlass iface : baseIfaces) {
                try {
                    return iface.lookupMethod(
                        callStyle, id, argTypes, returnType);
                } catch (MethodNotFound e) {
                }
            }
        }

        if (id == null) { id = "<init>"; }
        throw new MethodNotFound(this.getTypeName()+"."+id, argTypes, returnType);
    }

    public DFMethod lookupMethod(
        DFMethod.CallStyle callStyle, SimpleName name,
        DFType[] argTypes, DFType returnType)
        throws MethodNotFound {
        String id = (name == null)? null : name.getIdentifier();
        return this.lookupMethod(callStyle, id, argTypes, returnType);
    }

    public DFMethod lookupMethod(
        DFMethod.CallStyle callStyle, String id, DFType[] argTypes)
        throws MethodNotFound {
        return this.lookupMethod(callStyle, id, argTypes, null);
    }

    public DFMethod lookupMethod(
        DFMethod.CallStyle callStyle, SimpleName name, DFType[] argTypes)
        throws MethodNotFound {
        return this.lookupMethod(callStyle, name, argTypes, null);
    }

    public DFMethod createFallbackMethod(
        DFMethod.CallStyle callStyle, String id,
        DFType[] argTypes, DFType returnType) {
        return new FallbackMethod(id, callStyle, argTypes, returnType);
    }

    public DFMethod createFallbackMethod(
        DFMethod.CallStyle callStyle, SimpleName name,
        DFType[] argTypes, DFType returnType) {
        String id = (name == null)? null : name.getIdentifier();
        return this.createFallbackMethod(callStyle, id, argTypes, returnType);
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

    public int getReifyDepth() {
        if (_reifyDepth < 0) {
            int depth = 0;
            // At least it has the same depth as its base klass.
            DFKlass baseKlass = this.getBaseKlass();
            if (baseKlass != null) {
                depth = Math.max(depth, baseKlass.getReifyDepth());
            }
            // At least it has the same depth as its outer klass.
            if (_outerKlass != null) {
                depth = Math.max(depth, _outerKlass.getReifyDepth());
            }
            // It has a higher depth than its paramater klass.
            if (_paramTypes != null) {
                for (DFKlass klass : _paramTypes.values()) {
                    depth = Math.max(depth, klass.getReifyDepth()+1);
                }
            }
            _reifyDepth = depth;
        }
        return _reifyDepth;
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

    public class ThisRef extends DFRef {

        public ThisRef() {
            super(DFKlass.this);
        }

        @Override
        public DFVarScope getScope() {
            return _klassScope;
        }

        @Override
        public String getFullName() {
            return "@"+DFKlass.this.getTypeName();
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
        public DFVarScope getScope() {
            return _klassScope;
        }

        public boolean isStatic() {
            return _static;
        }

        @Override
        public String getFullName() {
            return "."+DFKlass.this.getTypeName()+"/."+_name;
        }
    }


    // KlassScope
    private class KlassScope extends DFVarScope {

        public KlassScope(DFVarScope outer, String id) {
            super(outer, id);
        }

        @Override
        public String getScopeName() {
            return DFKlass.this.getTypeName();
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

    //  FallbackMethod
    //  A dummy entry used for an unknown method.
    private class FallbackMethod extends DFMethod {

        DFFuncType _funcType;

        public FallbackMethod(
            String methodName, CallStyle callStyle,
            DFType[] argTypes, DFType returnType) {
            super(DFKlass.this, callStyle, false, methodName, methodName);
            assert returnType != null;
            // Ugly hack to prevent lambda or methodref from being
            // used for the argtypes of a fallback method.
            argTypes = argTypes.clone();
            for (int i = 0; i < argTypes.length; i++) {
                if (argTypes[i] instanceof DFLambdaKlass ||
                    argTypes[i] instanceof DFMethodRefKlass) {
                    argTypes[i] = DFUnknownType.UNKNOWN;
                }
            }
            _funcType = new DFFuncType(argTypes, returnType);
        }

        @Override
        public String toString() {
            return ("<FallbackMethod("+this.getSignature()+")>");
        }

        @Override
        protected DFMethod parameterize(Map<String, DFKlass> paramTypes) {
            assert false;
            return null;
        }

        public DFFuncType getFuncType() {
            return _funcType;
        }
    }
}
