//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
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
        AbstTypeDeclKlass genericKlass, Map<String, DFKlass> paramTypes)
        throws InvalidSyntax {
        super(genericKlass, paramTypes);

        _abstTypeDecl = genericKlass._abstTypeDecl;
        this.buildTypeFromDecls(_abstTypeDecl.bodyDeclarations());

        DFTypeFinder finder = this.getFinder();
        for (DFKlass klass : this.getInnerKlasses()) {
            if (klass instanceof DFSourceKlass) {
                ((DFSourceKlass)klass).setBaseFinder(finder);
            }
        }
    }

    public ASTNode getAST() {
        return _abstTypeDecl;
    }

    @SuppressWarnings("unchecked")
    protected void build() {
        try {
            if (_abstTypeDecl instanceof TypeDeclaration) {
                if (this.getGenericKlass() == null) {
                    DFTypeFinder finder = this.getFinder();
                    TypeDeclaration typeDecl = (TypeDeclaration)_abstTypeDecl;
                    DFMapType[] mapTypes = this.createMapTypes(
                        finder, typeDecl.typeParameters());
                    if (mapTypes != null) {
                        this.setMapTypes(mapTypes);
                    }
                }
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
            Logger.error("AbstTypeDeclKlass.build:", e);
        }
    }

    // Constructor for a parameterized klass.
    protected DFKlass parameterize(Map<String, DFKlass> paramTypes) {
        assert paramTypes != null;
        try {
            return new AbstTypeDeclKlass(this, paramTypes);
        } catch (InvalidSyntax e) {
            Logger.error("AbstTypeDeclKlass.parameterize:", e);
            return this;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void enumKlasses(Collection<DFSourceKlass> klasses) {
        if (klasses.contains(this)) return;
        super.enumKlasses(klasses);
        try {
            this.enumKlassesDecls(klasses, _abstTypeDecl.bodyDeclarations());
        } catch (InvalidSyntax e) {
            Logger.error("AbstTypeDeclKlass.enumKlasses:", e);
        }
    }
}
