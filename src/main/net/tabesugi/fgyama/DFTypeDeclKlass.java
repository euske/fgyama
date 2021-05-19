//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFTypeDeclKlass
//
class DFTypeDeclKlass extends DFSourceKlass {

    private AbstractTypeDeclaration _abstTypeDecl;
    private DefaultKlass[] _defaultKlasses = null;

    @SuppressWarnings("unchecked")
    public DFTypeDeclKlass(
        AbstractTypeDeclaration abstTypeDecl,
        DFTypeSpace outerSpace, DFSourceKlass outerKlass, DFVarScope outerScope,
        String filePath, boolean analyze)
        throws InvalidSyntax, EntityDuplicate {
        super(abstTypeDecl.getName().getIdentifier(),
              outerSpace, outerKlass, outerScope, filePath, analyze);

        _abstTypeDecl = abstTypeDecl;
        if (_abstTypeDecl instanceof TypeDeclaration) {
            TypeDeclaration typeDecl = (TypeDeclaration)_abstTypeDecl;
            List<TypeParameter> tps = typeDecl.typeParameters();
            if (!tps.isEmpty()) {
                ConsistentHashMap<String, DFKlass> typeSlots =
                    new ConsistentHashMap<String, DFKlass>();
                _defaultKlasses = new DefaultKlass[tps.size()];
                for (int i = 0; i < tps.size(); i++) {
                    TypeParameter tp = tps.get(i);
                    String id = tp.getName().getIdentifier();
                    DefaultKlass klass = new DefaultKlass(id, tp.typeBounds());
                    typeSlots.put(id, klass);
                    _defaultKlasses[i] = klass;
                }
                this.setTypeSlots(typeSlots);
            }
        }
        this.buildTypeFromDecls(_abstTypeDecl.bodyDeclarations());
    }

    @SuppressWarnings("unchecked")
    private DFTypeDeclKlass(
        DFTypeDeclKlass genericKlass, Map<String, DFKlass> paramTypes)
        throws InvalidSyntax, EntityDuplicate {
        super(genericKlass, paramTypes);

        _abstTypeDecl = genericKlass._abstTypeDecl;
        this.buildTypeFromDecls(_abstTypeDecl.bodyDeclarations());

        DFTypeFinder finder = this.getFinder();
        for (DFKlass klass : this.getInnerKlasses()) {
            if (klass instanceof DFSourceKlass) {
                ((DFSourceKlass)klass).initializeFinder(finder);
            }
        }
    }

    public ASTNode getAST() {
        return _abstTypeDecl;
    }

    public void initializeFinder(DFTypeFinder parentFinder) {
        super.initializeFinder(parentFinder);
        if (_defaultKlasses != null) {
            DFTypeFinder finder = this.getFinder();
            for (DefaultKlass klass : _defaultKlasses) {
                klass.setFinder(finder);
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected void build() {
        try {
            if (_abstTypeDecl instanceof TypeDeclaration) {
                this.buildMembersFromTypeDecl(
                    (TypeDeclaration)_abstTypeDecl);

            } else if (_abstTypeDecl instanceof EnumDeclaration) {
                this.buildMembersFromEnumDecl(
                    (EnumDeclaration)_abstTypeDecl);

            } else if (_abstTypeDecl instanceof AnnotationTypeDeclaration) {
                this.buildMembersFromAnnotTypeDecl(
                    (AnnotationTypeDeclaration)_abstTypeDecl);
            }
        } catch (InvalidSyntax e) {
            Logger.error(
                "DFTypeDeclKlass.build: InvalidSyntax: ",
                Utils.getASTSource(e.ast), this);
        } catch (EntityDuplicate e) {
            Logger.error(
                "DFTypeDeclKlass.build: EntityDuplicate: ",
                e.name, this);
        }
    }

    // Constructor for a parameterized klass.
    protected DFKlass parameterize(Map<String, DFKlass> paramTypes) {
        assert paramTypes != null;
        try {
            return new DFTypeDeclKlass(this, paramTypes);
        } catch (InvalidSyntax e) {
            Logger.error(
                "DFTypeDeclKlass.parameterize: InvalidSyntax: ",
                Utils.getASTSource(e.ast), this);
            return this;
        } catch (EntityDuplicate e) {
            Logger.error(
                "DFTypeDeclKlass.parameterize: EntityDuplicate: ",
                e.name, this);
            return this;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean listUsedKlasses(Collection<DFSourceKlass> klasses) {
        if (!super.listUsedKlasses(klasses)) return false;
        try {
            this.listUsedDecls(klasses, _abstTypeDecl.bodyDeclarations());
        } catch (InvalidSyntax e) {
            Logger.error(
                "DFTypeDeclKlass.listUsedKlasses:",
                Utils.getASTSource(e.ast), this);
        }
        return true;
    }

    // DefaultKlass
    class DefaultKlass extends DFKlass {

        private List<Type> _types;
        private DFTypeFinder _finder = null;
        private DFKlass _baseKlass = null;

        public DefaultKlass(String name, List<Type> types) {
            super(name, DFTypeDeclKlass.this, null, null);
            _types = types;
        }

        @Override
        public String toString() {
            return ("<DefaultKlass("+this.getName()+")");
        }

        @Override
        public String getTypeName() {
            return "T"+this.getName()+";";
        }

        public boolean isInterface() {
            this.load();
            return _baseKlass.isInterface();
        }

        public boolean isEnum() {
            this.load();
            return _baseKlass.isEnum();
        }

        public DFKlass getBaseKlass() {
            this.load();
            return _baseKlass;
        }

        public DFKlass[] getBaseIfaces() {
            return null;
        }

        public DFMethod[] getMethods() {
            this.load();
            return _baseKlass.getMethods();
        }

        public FieldRef[] getFields() {
            this.load();
            return _baseKlass.getFields();
        }

        protected DFKlass parameterize(Map<String, DFKlass> paramTypes) {
            assert false;
            return null;
        }

        protected void setFinder(DFTypeFinder finder) {
            _finder = finder;
        }

        @Override
        public int canConvertFrom(DFKlass klass, Map<DFMapType, DFKlass> typeMap)
            throws TypeIncompatible {
            this.load();
            return _baseKlass.canConvertFrom(klass, typeMap);
        }

        private void load() {
            assert _finder != null;
            if (_baseKlass != null) return;
            _baseKlass = DFBuiltinTypes.getObjectKlass();
            for (Type type : _types) {
                try {
                    _baseKlass = _finder.resolve(type).toKlass();
                } catch (TypeNotFound e) {
                    Logger.error(
                        "DefaultKlass.load: TypeNotFound",
                        Utils.getASTSource(type), this);
                }
                break;
            }
        }
    }
}
