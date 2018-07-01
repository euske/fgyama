//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.apache.bcel.*;
import org.apache.bcel.classfile.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFClassSpace
//
public class DFClassSpace extends DFVarSpace {

    private DFTypeSpace _typeSpace;
    private DFTypeSpace _childSpace;
    private DFClassSpace _baseKlass;
    private DFClassSpace[] _baseIfaces;
    private DFParamType[] _paramTypes = null;
    private String _jarPath = null;
    private String _filePath = null;
    private boolean _loaded = true;

    private List<DFMethod> _methods =
        new ArrayList<DFMethod>();

    public DFClassSpace(
        DFTypeSpace typeSpace, DFTypeSpace childSpace,
        String id, DFClassSpace baseKlass) {
        super(id);
        _typeSpace = typeSpace;
        _childSpace = childSpace;
        _baseKlass = baseKlass;
    }

    public DFClassSpace(
        DFTypeSpace typeSpace, DFTypeSpace childSpace, String id) {
        this(typeSpace, childSpace, id, null);
        this.addRef("#this", new DFClassType(this));
    }

    @Override
    public String toString() {
        return ("<DFClassSpace("+this.getFullName()+")>");
    }

    public void setJarPath(String jarPath, String filePath) {
        _jarPath = jarPath;
        _filePath = filePath;
        _loaded = false;
    }

    public void setParamTypes(DFParamType[] paramTypes) {
        _paramTypes = paramTypes;
    }

    public DFTypeSpace getChildSpace() {
        return _childSpace;
    }

    public DFClassSpace getBase() {
        return _baseKlass;
    }

    public String getFullName() {
        return _typeSpace.getFullName()+"/"+super.getFullName();
    }

    public int isBaseOf(DFClassSpace klass) {
        int dist = 0;
        // XXX check interfaces too.
        while (klass != null) {
            if (klass == this) return dist;
            dist++;
            klass = klass._baseKlass;
        }
        return -1;
    }

    public DFVarRef lookupThis() {
        return this.lookupRef("#this");
    }

    protected DFVarRef lookupField(String id) {
        return this.lookupRef("."+id);
    }

    public DFVarRef lookupField(SimpleName name) {
        DFVarRef ref = this.lookupField(name.getIdentifier());
        if (ref != null) return ref;
        return new DFVarRef(null, "."+name.getIdentifier(), null);
    }

    public DFMethod lookupMethod1(SimpleName name, DFType[] argTypes) {
        String id = name.getIdentifier();
        int bestDist = -1;
        DFMethod bestMethod = null;
        for (DFMethod method : _methods) {
            int dist = method.canAccept(id, argTypes);
            if (dist < 0) continue;
            if (bestDist < 0 || dist < bestDist) {
                bestDist = dist;
                bestMethod = method;
            }
        }
        return bestMethod;
    }

    public DFMethod[] lookupMethods(SimpleName name, DFType[] argTypes) {
        DFClassSpace klass = this;
        while (klass != null) {
            DFMethod method = klass.lookupMethod1(name, argTypes);
            if (method != null) {
                return method.getOverrides();
            }
            klass = klass._baseKlass;
        }
        return null;
    }

    private DFVarRef addField(
        SimpleName name, boolean isStatic, DFType type) {
        DFVarRef ref = this.addRef("."+name.getIdentifier(), type);
        //Logger.info("DFClassSpace.addField: "+ref);
        return ref;
    }

    private void overrideMethod(DFMethod method1) {
        for (DFMethod method0 : _methods) {
            if (method0.equals(method1)) {
                method0.addOverride(method1);
                //Logger.info("DFClassSpace.overrideMethod: "+method0+" : "+method1);
                break;
            }
        }
        if (_baseKlass != null) {
            _baseKlass.overrideMethod(method1);
        }
        if (_baseIfaces != null) {
            for (DFClassSpace iface : _baseIfaces) {
                iface.overrideMethod(method1);
            }
        }
    }

    private DFMethod addMethod(
        SimpleName name, boolean isStatic,
        DFType[] argTypes, DFType returnType) {
        String id = name.getIdentifier();
        DFMethod method = new DFMethod(this, id, isStatic, argTypes, returnType);
        return this.addMethod(method);
    }

    private DFMethod addMethod(
        String id, boolean isStatic,
        DFType[] argTypes, DFType returnType) {
        DFMethod method = new DFMethod(this, id, isStatic, argTypes, returnType);
        return this.addMethod(method);
    }

    private DFMethod addMethod(DFMethod method) {
        //Logger.info("DFClassSpace.addMethod: "+method);
        _methods.add(method);
        if (_baseKlass != null) {
            _baseKlass.overrideMethod(method);
        }
        if (_baseIfaces != null) {
            for (DFClassSpace iface : _baseIfaces) {
                iface.overrideMethod(method);
            }
        }
        return method;
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

    public void load(DFTypeFinder finder) throws EntityNotFound {
        if (_loaded) return;
        _loaded = true;
        try {
            JarFile jarfile = new JarFile(_jarPath);
            try {
                JarEntry je = jarfile.getJarEntry(_filePath);
                InputStream strm = jarfile.getInputStream(je);
                JavaClass jklass = new ClassParser(strm, _filePath).parse();
                this.load(finder, jklass);
            } finally {
                jarfile.close();
            }
        } catch (IOException e) {
            Logger.error("Error: Not found: "+_jarPath+"/"+_filePath);
            throw new EntityNotFound(this.getFullName());
        }
    }

    private void load(DFTypeFinder finder, JavaClass jklass)
        throws EntityNotFound {
        String superClass = jklass.getSuperclassName();
        if (superClass != null && !superClass.equals(jklass.getClassName())) {
            _baseKlass = finder.lookupClass(superClass);
        }
        for (Field fld : jklass.getFields()) {
            if (!fld.isPublic()) continue;
            DFType type = finder.resolve(fld.getType());
            this.addRef("."+fld.getName(), type);
        }
        for (Method meth : jklass.getMethods()) {
            if (!meth.isPublic()) continue;
            org.apache.bcel.generic.Type[] args = meth.getArgumentTypes();
            DFType[] argTypes = new DFType[args.length];
            for (int i = 0; i < args.length; i++) {
                argTypes[i] = finder.resolve(args[i]);
            }
            DFType returnType = finder.resolve(meth.getReturnType());
            this.addMethod(meth.getName(), meth.isStatic(),
                           argTypes, returnType);
        }
    }

    public DFTypeFinder addFinders(DFTypeFinder finder) {
        if (_baseKlass != null) {
            finder = _baseKlass.addFinders(finder);
        }
        finder = new DFTypeFinder(finder, _childSpace);
        return finder;
    }

    @SuppressWarnings("unchecked")
    public void build(DFTypeFinder finder, TypeDeclaration typeDecl)
        throws UnsupportedSyntax, EntityNotFound {
        //Logger.info("DFClassSpace.build: "+this+": "+typeDecl.getName());
        // Get superclass.
        try {
            finder = new DFTypeFinder(finder, _childSpace);
            Type superClass = typeDecl.getSuperclassType();
            if (superClass != null) {
                _baseKlass = finder.resolveClass(superClass);
                finder = _baseKlass.addFinders(finder);
            }
            // Get interfaces.
            List<Type> ifaces = typeDecl.superInterfaceTypes();
            _baseIfaces = new DFClassSpace[ifaces.size()];
            for (int i = 0; i < ifaces.size(); i++) {
                _baseIfaces[i] = finder.resolveClass(ifaces.get(i));
            }
            // Get type parameters.
            List<TypeParameter> tps = typeDecl.typeParameters();
            for (int i = 0; i < tps.size(); i++) {
                DFParamType pt = _paramTypes[i];
                List<Type> bounds = tps.get(i).typeBounds();
                DFClassSpace[] bases = new DFClassSpace[bounds.size()];
                for (int j = 0; j < bounds.size(); j++) {
                    bases[j] = finder.resolveClass(bounds.get(j));
                }
                pt.setBases(bases);
            }
            // Lookup child classes.
            for (BodyDeclaration body :
                     (List<BodyDeclaration>) typeDecl.bodyDeclarations()) {
                this.build(finder, body);
            }
        } catch (EntityNotFound e) {
            e.setAst(typeDecl);
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    public void build(DFTypeFinder finder, BodyDeclaration body)
        throws UnsupportedSyntax, EntityNotFound {
        if (body instanceof TypeDeclaration) {
            TypeDeclaration decl = (TypeDeclaration)body;
            DFClassSpace klass = _childSpace.getClass(decl.getName());
            klass.build(finder, decl);

        } else if (body instanceof FieldDeclaration) {
            FieldDeclaration decl = (FieldDeclaration)body;
            DFType type = finder.resolve(decl.getType());
            for (VariableDeclarationFragment frag :
                     (List<VariableDeclarationFragment>) decl.fragments()) {
                this.addField(frag.getName(), isStatic(decl), type);
            }

        } else if (body instanceof MethodDeclaration) {
            MethodDeclaration decl = (MethodDeclaration)body;
            List<TypeParameter> tps = decl.typeParameters();
            DFTypeFinder finder2 = finder;
            if (0 < tps.size()) {
                DFTypeSpace typeSpace = new DFTypeSpace("MethodDecl");
                finder2 = new DFTypeFinder(finder, typeSpace);
                DFParamType[] pts = DFParamType.createParamTypes(
                    this.getFullName(), tps);
                for (DFParamType pt : pts) {
                    typeSpace.addParamType(pt);
                }
            }
            DFType[] argTypes = finder2.resolveList(decl);
            DFType returnType;
            if (decl.isConstructor()) {
                returnType = new DFClassType(this);
            } else {
                returnType = finder2.resolve(decl.getReturnType2());
            }
            this.addMethod(decl.getName(), isStatic(decl),
                           argTypes, returnType);

        } else if (body instanceof Initializer) {

        } else {
            throw new UnsupportedSyntax(body);
        }
    }

    // dumpContents (for debugging)
    public void dumpContents(PrintStream out, String indent) {
        super.dumpContents(out, indent);
        for (DFMethod method : _methods) {
            out.println(indent+"defined: "+method);
        }
    }
}
