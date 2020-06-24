//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import java.util.jar.*;
import javax.xml.stream.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  AbstTypeDeclKlass
//
class AbstTypeDeclKlass extends DFSourceKlass {

    private AbstractTypeDeclaration _abstTypeDecl;

    @SuppressWarnings("unchecked")
    public AbstTypeDeclKlass(
        AbstractTypeDeclaration abstTypeDecl,
        DFTypeSpace outerSpace, DFSourceKlass outerKlass,
        String filePath, DFVarScope outerScope)
        throws InvalidSyntax {
        super(abstTypeDecl.getName().getIdentifier(),
              outerSpace, outerKlass, filePath, outerScope);
        _abstTypeDecl = abstTypeDecl;
        this.buildTypeFromDecls(abstTypeDecl.bodyDeclarations());
    }

    @SuppressWarnings("unchecked")
    private AbstTypeDeclKlass(
        AbstTypeDeclKlass genericKlass, DFKlass[] paramTypes)
        throws InvalidSyntax {
        super(genericKlass, paramTypes);
        _abstTypeDecl = genericKlass._abstTypeDecl;
        this.buildTypeFromDecls(_abstTypeDecl.bodyDeclarations());
    }

    protected void build() throws InvalidSyntax {
        if (this.getGenericKlass() == null &&
            _abstTypeDecl instanceof TypeDeclaration) {
            DFTypeFinder finder = this.getFinder();
            TypeDeclaration typeDecl = (TypeDeclaration)_abstTypeDecl;
            DFMapType[] mapTypes = this.createMapTypes(typeDecl.typeParameters());
            if (mapTypes != null) {
                for (DFMapType mapType : mapTypes) {
                    mapType.setBaseFinder(finder);
                }
                this.setMapTypes(mapTypes);
            }
        }
        this.buildMembersFromAbstTypeDecl(_abstTypeDecl);
    }

    // Constructor for a parameterized klass.
    @Override
    protected DFKlass parameterize(DFKlass[] paramTypes)
        throws InvalidSyntax {
        assert paramTypes != null;
        return new AbstTypeDeclKlass(this, paramTypes);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void loadKlasses(Set<DFSourceKlass> klasses)
        throws InvalidSyntax {
        super.loadKlasses(klasses);
        this.loadKlassesDecls(klasses, _abstTypeDecl.bodyDeclarations());
    }
}


//  AnonymousKlass
//
class AnonymousKlass extends DFSourceKlass {

    private ClassInstanceCreation _cstr;

    @SuppressWarnings("unchecked")
    protected AnonymousKlass(
        ClassInstanceCreation cstr,
        DFTypeSpace outerSpace, DFSourceKlass outerKlass,
        String filePath, DFVarScope outerScope)
        throws InvalidSyntax {
        super(Utils.encodeASTNode(cstr),
              outerSpace, outerKlass, filePath, outerScope);
        _cstr = cstr;
        this.buildTypeFromDecls(
            cstr.getAnonymousClassDeclaration().bodyDeclarations());
    }

    protected void build() throws InvalidSyntax {
        this.buildMembersFromAnonDecl(_cstr);
    }
}


//  DFSourceKlass
//
public abstract class DFSourceKlass extends DFKlass {

    // These fields are set at the constructor.
    private DFKlass _outerKlass;  // can be the same as outerSpace, or null.
    private String _filePath;
    private DFVarScope _outerScope;
    private KlassScope _klassScope;

    // This field is available after setBaseFinder(). (Stage2)
    private DFTypeFinder _finder = null;

    // The following fields are available after the klass is loaded. (Stage3)
    private boolean _interface = false;
    private DFKlass _baseKlass = null;
    private DFKlass[] _baseIfaces = null;
    private InitMethod _initMethod = null;

    protected DFSourceKlass(
        String name, DFTypeSpace outerSpace, DFSourceKlass outerKlass,
        String filePath, DFVarScope outerScope)
        throws InvalidSyntax {
        super(name, outerSpace);
        assert outerKlass == null || outerKlass == outerSpace;
        _outerKlass = outerKlass;
        _filePath = filePath;
        _outerScope = outerScope;
        _klassScope = new KlassScope(outerScope, name);
    }

    protected DFSourceKlass(
        DFSourceKlass genericKlass, DFKlass[] paramTypes)
        throws InvalidSyntax {
        super(genericKlass, paramTypes);
        _outerKlass = genericKlass._outerKlass;
        _filePath = genericKlass._filePath;
        _outerScope = genericKlass._outerScope;
        _klassScope = new KlassScope(genericKlass._outerScope, this.getName());
        _finder = new DFTypeFinder(this, genericKlass._finder);
    }

    protected DFKlass parameterize(DFKlass[] paramTypes)
        throws InvalidSyntax {
        assert false;
        return null;
    }

    @Override
    public void writeXML(XMLStreamWriter writer)
        throws XMLStreamException {
        writer.writeAttribute("path", this.getFilePath());
        super.writeXML(writer);
    }

    @Override
    protected void dumpContents(PrintStream out, String indent) {
        super.dumpContents(out, indent);
        if (_baseKlass != null) {
            _baseKlass.dump(out, indent);
        }
        if (_baseIfaces != null) {
            for (DFKlass iface : _baseIfaces) {
                if (iface != null) {
                    iface.dump(out, indent);
                }
            }
        }
    }

    public String getFilePath() {
        return _filePath;
    }

    @Override
    public boolean isInterface() {
        assert this.isDefined();
        return _interface;
    }

    @Override
    public boolean isEnum() {
        assert this.isDefined();
        return (_baseKlass != null &&
                _baseKlass.getGenericKlass() == DFBuiltinTypes.getEnumKlass());
    }

    @Override
    public DFKlass getBaseKlass() {
        assert this.isDefined();
        if (_baseKlass != null) return _baseKlass;
        return super.getBaseKlass();
    }

    @Override
    public DFKlass[] getBaseIfaces() {
        assert this.isDefined();
        return _baseIfaces;
    }

    public DFMethod getInitMethod() {
        assert this.isDefined();
        return _initMethod;
    }

    public DFVarScope getOuterScope() {
        return _outerScope;
    }

    public DFVarScope getKlassScope() {
        return _klassScope;
    }

    @Override
    public DFMethod findMethod(
        DFMethod.CallStyle callStyle, String id, DFType[] argTypes) {
        assert this.isDefined();
        DFMethod method = super.findMethod(callStyle, id, argTypes);
        if (method != null) return method;
        if (_outerKlass != null) {
            method = _outerKlass.findMethod(callStyle, id, argTypes);
            if (method != null) return method;
        }
        if (_baseKlass != null) {
            method = _baseKlass.findMethod(callStyle, id, argTypes);
            if (method != null) return method;
        }
        if (_baseIfaces != null) {
            for (DFKlass iface : _baseIfaces) {
                method = iface.findMethod(callStyle, id, argTypes);
                if (method != null) return method;
            }
        }
        return null;
    }

    public void overrideMethods() {
        // override the methods.
        for (DFMethod method : this.getMethods()) {
            if (_baseKlass != null) {
                this.overrideMethod(_baseKlass, method);
            }
            if (_baseIfaces != null) {
                for (DFKlass iface : _baseIfaces) {
                    this.overrideMethod(iface, method);
                }
            }
        }
    }

    private void overrideMethod(DFKlass klass, DFMethod method1) {
        for (DFMethod method0 : klass.getMethods()) {
            if (method0.addOverrider(method1)) break;
        }
    }

    public void setBaseFinder(DFTypeFinder baseFinder) {
        assert _finder == null;
        _finder = new DFTypeFinder(this, baseFinder);
    }

    public DFTypeFinder getFinder() {
        return _finder;
    }

    // Only used by DFLambdaKlass.
    protected void setBaseKlass(DFKlass klass) {
        _baseKlass = klass;
    }

    @SuppressWarnings("unchecked")
    private boolean isStatic(BodyDeclaration body) {
        for (IExtendedModifier imod :
                 (List<IExtendedModifier>) body.modifiers()) {
            if (imod.isModifier()) {
                if (((Modifier)imod).isStatic()) return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    protected void buildTypeFromDecls(List<BodyDeclaration> decls)
        throws InvalidSyntax {

        for (BodyDeclaration body : decls) {
            if (body instanceof AbstractTypeDeclaration) {
                AbstractTypeDeclaration abstTypeDecl = (AbstractTypeDeclaration)body;
                String id = abstTypeDecl.getName().getIdentifier();
                DFSourceKlass klass = new AbstTypeDeclKlass(
                    abstTypeDecl, this, this,
                    this.getFilePath(), this.getKlassScope());
                this.addKlass(id, klass);

            } else if (body instanceof FieldDeclaration) {

            } else if (body instanceof MethodDeclaration) {

            } else if (body instanceof EnumConstantDeclaration) {

            } else if (body instanceof AnnotationTypeMemberDeclaration) {

            } else if (body instanceof Initializer) {

            } else {
                throw new InvalidSyntax(body);
            }
        }
    }

    protected void buildMembersFromAbstTypeDecl(
        AbstractTypeDeclaration abstTypeDecl)
        throws InvalidSyntax {
        if (abstTypeDecl instanceof TypeDeclaration) {
            this.buildMembersFromTypeDecl((TypeDeclaration)abstTypeDecl);

        } else if (abstTypeDecl instanceof EnumDeclaration) {
            this.buildMembersFromEnumDecl((EnumDeclaration)abstTypeDecl);

        } else if (abstTypeDecl instanceof AnnotationTypeDeclaration) {
            this.buildMembersFromAnnotTypeDecl((AnnotationTypeDeclaration)abstTypeDecl);
        }
    }

    @SuppressWarnings("unchecked")
    protected void buildMembersFromAnonDecl(
        ClassInstanceCreation cstr)
        throws InvalidSyntax {
        // Get superclass.
        Type superClass = cstr.getType();
        if (superClass == null) {
            _baseKlass = DFBuiltinTypes.getObjectKlass();
        } else {
            try {
                _baseKlass = _finder.resolve(superClass).toKlass();
                _baseKlass.load();
            } catch (TypeNotFound e) {
                Logger.error(
                    "DFKlass.buildMembersFromAnonDecl: TypeNotFound (baseKlass)",
                    this, e.name);
            }
        }
        this.buildMembers(cstr.getAnonymousClassDeclaration().bodyDeclarations());
    }

    @SuppressWarnings("unchecked")
    private void buildMembersFromTypeDecl(
        TypeDeclaration typeDecl)
        throws InvalidSyntax {
        _interface = typeDecl.isInterface();
        // Load base klasses/interfaces.
        // Get superclass.
        Type superClass = typeDecl.getSuperclassType();
        if (superClass == null) {
            _baseKlass = DFBuiltinTypes.getObjectKlass();
        } else {
            try {
                _baseKlass = _finder.resolve(superClass).toKlass();
                _baseKlass.load();
            } catch (TypeNotFound e) {
                Logger.error(
                    "DFKlass.buildMembersFromTypeDecl: TypeNotFound (baseKlass)",
                    this, e.name);
            }
        }
        // Get interfaces.
        List<Type> ifaces = typeDecl.superInterfaceTypes();
        _baseIfaces = new DFKlass[ifaces.size()];
        for (int i = 0; i < ifaces.size(); i++) {
            DFKlass iface = DFBuiltinTypes.getObjectKlass();
            try {
                iface = _finder.resolve(ifaces.get(i)).toKlass();
            } catch (TypeNotFound e) {
                Logger.error(
                    "DFKlass.buildMembersFromTypeDecl: TypeNotFound (iface)",
                    this, e.name);
            }
            _baseIfaces[i] = iface;
        }
        for (DFKlass iface : _baseIfaces) {
            iface.load();
        }
        this.buildMembers(typeDecl.bodyDeclarations());
    }

    @SuppressWarnings("unchecked")
    private void buildMembersFromEnumDecl(
        EnumDeclaration enumDecl)
        throws InvalidSyntax {
        // Load base klasses/interfaces.
        // Get superclass.
        DFKlass enumKlass = DFBuiltinTypes.getEnumKlass();
        _baseKlass = enumKlass.getConcreteKlass(new DFKlass[] { this });
        _baseKlass.load();
        // Get interfaces.
        List<Type> ifaces = enumDecl.superInterfaceTypes();
        _baseIfaces = new DFKlass[ifaces.size()];
        for (int i = 0; i < ifaces.size(); i++) {
            DFKlass iface = DFBuiltinTypes.getObjectKlass();
            try {
                iface = _finder.resolve(ifaces.get(i)).toKlass();
            } catch (TypeNotFound e) {
                Logger.error(
                    "DFKlass.buildMembersFromEnumDecl: TypeNotFound (iface)",
                    this, e.name);
            }
            _baseIfaces[i] = iface;
        }
        for (DFKlass iface : _baseIfaces) {
            iface.load();
        }
        // Get constants.
        for (EnumConstantDeclaration econst :
                 (List<EnumConstantDeclaration>) enumDecl.enumConstants()) {
            this.addField(this, econst.getName(), true);
        }
        // Enum has a special method "values()".
        DFMethod method = new EnumValuesMethod(this);
        this.addMethod(method, null);
        this.buildMembers(enumDecl.bodyDeclarations());
    }

    @SuppressWarnings("unchecked")
    private void buildMembersFromAnnotTypeDecl(
        AnnotationTypeDeclaration annotTypeDecl)
        throws InvalidSyntax {
        this.buildMembers(annotTypeDecl.bodyDeclarations());
    }

    @SuppressWarnings("unchecked")
    private void buildMembers(List<BodyDeclaration> decls)
        throws InvalidSyntax {

        assert _initMethod == null;
        _initMethod = new InitMethod(this, decls, _finder);

        for (BodyDeclaration body : decls) {
            if (body instanceof AbstractTypeDeclaration) {
                // Child klasses are loaded independently.

            } else if (body instanceof FieldDeclaration) {
                FieldDeclaration decl = (FieldDeclaration)body;
                DFType fldType = _finder.resolveSafe(decl.getType());
                for (VariableDeclarationFragment frag :
                         (List<VariableDeclarationFragment>) decl.fragments()) {
                    DFType ft = fldType;
                    int ndims = frag.getExtraDimensions();
                    if (ndims != 0) {
                        ft = DFArrayType.getType(ft, ndims);
                    }
                    this.addField(ft, frag.getName(), isStatic(decl));
                    Expression init = frag.getInitializer();
                    if (init != null) {
                        _initMethod.buildType(init);
                    }
                }

            } else if (body instanceof MethodDeclaration) {
                MethodDeclaration decl = (MethodDeclaration)body;
                String id = Utils.encodeASTNode(decl);
                String name;
                DFMethod.CallStyle callStyle;
                if (decl.isConstructor()) {
                    name = "<init>";
                    callStyle = DFMethod.CallStyle.Constructor;
                } else {
                    name = decl.getName().getIdentifier();
                    callStyle = (isStatic(decl))?
                        DFMethod.CallStyle.StaticMethod :
                        DFMethod.CallStyle.InstanceMethod;
                }
                Statement stmt = decl.getBody();
                DFMethod method = new DefinedMethod(
                    this, callStyle, (stmt == null), id, name, decl, _finder);
                this.addMethod(method, id);

            } else if (body instanceof EnumConstantDeclaration) {

            } else if (body instanceof AnnotationTypeMemberDeclaration) {
                AnnotationTypeMemberDeclaration decl =
                    (AnnotationTypeMemberDeclaration)body;
                DFType type = _finder.resolveSafe(decl.getType());
                this.addField(type, decl.getName(), isStatic(decl));

            } else if (body instanceof Initializer) {
                Initializer initializer = (Initializer)body;
                Statement stmt = initializer.getBody();
                if (stmt != null) {
                    _initMethod.buildType(stmt);
                }

            } else {
                throw new InvalidSyntax(body);
            }
        }
    }

    public void loadKlasses(Set<DFSourceKlass> klasses)
        throws InvalidSyntax {
        if (this.isGeneric()) return;
        if (klasses.contains(this)) return;
        klasses.add(this);
        //Logger.info("loadKlasses:", this);
        this.load();
    }

    @SuppressWarnings("unchecked")
    protected void loadKlassesDecls(
        Set<DFSourceKlass> klasses, List<BodyDeclaration> decls)
        throws InvalidSyntax {
        assert _initMethod != null;

        for (BodyDeclaration body : decls) {
            if (body instanceof AbstractTypeDeclaration) {
                AbstractTypeDeclaration decl = (AbstractTypeDeclaration)body;
                DFKlass innerType = this.getKlass(decl.getName());
                assert innerType instanceof DFSourceKlass;
                ((DFSourceKlass)innerType).loadKlasses(klasses);

            } else if (body instanceof FieldDeclaration) {
                FieldDeclaration decl = (FieldDeclaration)body;
                DFType fldType = _finder.resolveSafe(decl.getType());
                if (fldType instanceof DFSourceKlass) {
                    ((DFSourceKlass)fldType).loadKlasses(klasses);
                }

            } else if (body instanceof MethodDeclaration) {
                MethodDeclaration decl = (MethodDeclaration)body;
                String id = Utils.encodeASTNode(decl);
                DFMethod method = this.getMethod(id);
                assert method instanceof DFSourceMethod;
                ((DFSourceMethod)method).loadKlasses(klasses);

            } else if (body instanceof EnumConstantDeclaration) {

            } else if (body instanceof AnnotationTypeMemberDeclaration) {
                AnnotationTypeMemberDeclaration decl =
                    (AnnotationTypeMemberDeclaration)body;
                DFType type = _finder.resolveSafe(decl.getType());
                if (type instanceof DFSourceKlass) {
                    ((DFSourceKlass)type).loadKlasses(klasses);
                }

            } else if (body instanceof Initializer) {

            } else {
                throw new InvalidSyntax(body);
            }
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
            _this = new ThisRef(DFSourceKlass.this);
        }

        @Override
        public String getScopeName() {
            return DFSourceKlass.this.getTypeName();
        }

        @Override
        public DFRef lookupThis() {
            return _this;
        }

        @Override
        public DFRef lookupVar(String id)
            throws VariableNotFound {
            DFRef ref = DFSourceKlass.this.getField(id);
            if (ref != null) return ref;
            return super.lookupVar(id);
        }

        // dumpContents (for debugging)
        protected void dumpContents(PrintStream out, String indent) {
            super.dumpContents(out, indent);
            for (DFRef ref : DFSourceKlass.this.getFields()) {
                out.println(indent+"defined: "+ref);
            }
            for (DFMethod method : DFSourceKlass.this.getMethods()) {
                out.println(indent+"defined: "+method);
            }
        }
    }
}
