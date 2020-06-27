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
        AbstTypeDeclKlass genericKlass, DFKlass[] paramTypes)
        throws InvalidSyntax {
        super(genericKlass, paramTypes);
        _abstTypeDecl = genericKlass._abstTypeDecl;
        this.buildTypeFromDecls(_abstTypeDecl.bodyDeclarations());
    }

    @SuppressWarnings("unchecked")
    protected void build() throws InvalidSyntax {
        if (_abstTypeDecl instanceof TypeDeclaration) {
            if (this.getGenericKlass() == null) {
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
            this.buildMembersFromTypeDecl(
                (TypeDeclaration)_abstTypeDecl);

        } else if (_abstTypeDecl instanceof EnumDeclaration) {
            this.buildMembersFromEnumDecl(
                (EnumDeclaration)_abstTypeDecl);

        } else if (_abstTypeDecl instanceof AnnotationTypeDeclaration) {
            this.buildMembersFromAnnotTypeDecl(
                (AnnotationTypeDeclaration)_abstTypeDecl);
        }
    }

    // Constructor for a parameterized klass.
    protected DFKlass parameterize(DFKlass[] paramTypes)
        throws InvalidSyntax {
        assert paramTypes != null;
        return new AbstTypeDeclKlass(this, paramTypes);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void loadKlasses(Collection<DFSourceKlass> klasses)
        throws InvalidSyntax {
        if (klasses.contains(this)) return;
        super.loadKlasses(klasses);
        this.loadKlassesDecls(klasses, _abstTypeDecl.bodyDeclarations());
    }
}
