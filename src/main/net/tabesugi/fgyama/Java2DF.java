/**
 * Java2DF
 * Dataflow analyzer for Java
 */
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.w3c.dom.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  Java2DF
//
public class Java2DF {

    private class SourceFile {

        public String path;
        public CompilationUnit cunit;
        public boolean analyze;

        public SourceFile(String path, CompilationUnit cunit, boolean analyze) {
            this.path = path;
            this.cunit = cunit;
            this.analyze = analyze;
        }

        @Override
        public String toString() {
            return "<"+this.path+">";
        }
    }

    private DFRootTypeSpace _rootSpace;
    private DFGlobalScope _globalScope =
        new DFGlobalScope();
    private Map<String, SourceFile> _sourceFiles =
        new ConsistentHashMap<String, SourceFile>();
    private Map<SourceFile, DFFileScope> _fileScope =
        new HashMap<SourceFile, DFFileScope>();
    private Map<SourceFile, List<DFSourceKlass>> _fileKlasses =
        new HashMap<SourceFile, List<DFSourceKlass>>();

    /// Top-level functions.

    public Java2DF() {
        _rootSpace = new DFRootTypeSpace();
    }

    public void loadDefaults()
        throws IOException, InvalidSyntax {
        // Initialize base classes.
        File homeDir = new File(System.getProperty("java.home"));
        File libDir = new File(homeDir, "lib");
        File rtFile = new File(libDir, "rt.jar");
        _rootSpace.loadJarFile(rtFile);
        DFBuiltinTypes.initialize(_rootSpace);
    }

    public void loadJarFile(File file) throws IOException {
        _rootSpace.loadJarFile(file);
    }

    public void clearSourceFiles() {
        _sourceFiles.clear();
    }

    public void addSourceFile(String path)
        throws IOException {
        return addSourceFile(path, true);
    }

    public void addSourceFile(String path, boolean analyze)
        throws IOException {
        File file = new File(path);
        String key = file.getCanonicalPath();
        if (!_sourceFiles.containsKey(key)) {
            CompilationUnit cunit = Utils.parseFile(file);
            _sourceFiles.put(key, new SourceFile(path, cunit, analyze));
        }
    }

    public Collection<DFSourceKlass> getSourceKlasses()
        throws InvalidSyntax {

        // Stage1: populate TypeSpaces.
        for (SourceFile src : _sourceFiles.values()) {
            Logger.info("Stage1:", src);
            this.buildTypeSpace(src);
        }

        // Stage2: set references to external Klasses.
        for (SourceFile src : _sourceFiles.values()) {
            Logger.info("Stage2:", src);
            this.setTypeFinder(src);
        }

        // Stage3: list class definitions and define parameterized Klasses.
        ConsistentHashSet<DFSourceKlass> klasses =
            new ConsistentHashSet<DFSourceKlass>();
        for (SourceFile src : _sourceFiles.values()) {
            Logger.info("Stage3:", src);
            this.listUsedKlasses(src, klasses);
        }

        // Stage4: expand classes.
        Logger.info("Stage4: expanding "+klasses.size()+" klasses...");
        this.expandKlasses(klasses);

        return klasses;
    }

    @SuppressWarnings("unchecked")
    private void buildTypeSpace(SourceFile src)
        throws InvalidSyntax {
        DFTypeSpace packageSpace = _rootSpace.lookupSpace(src.cunit.getPackage());
        DFFileScope fileScope = new DFFileScope(_globalScope, src.path);
        _fileScope.put(src, fileScope);
        List<DFSourceKlass> klasses = new ArrayList<DFSourceKlass>();
        for (AbstractTypeDeclaration abstTypeDecl :
                 (List<AbstractTypeDeclaration>) src.cunit.types()) {
            DFSourceKlass klass = new AbstTypeDeclKlass(
                abstTypeDecl, packageSpace, null, fileScope,
                src.path, src.analyze);
            packageSpace.addKlass(abstTypeDecl.getName().getIdentifier(), klass);
            Logger.debug("Stage1: Created:", klass);
            klasses.add(klass);
        }
        _fileKlasses.put(src, klasses);
    }

    @SuppressWarnings("unchecked")
    private void setTypeFinder(SourceFile src) {
        // Search path for types: ROOT -> java.lang -> package -> imports.
        DFTypeFinder finder = new DFTypeFinder(_rootSpace);
        finder = new DFTypeFinder(_rootSpace.lookupSpace("java.lang"), finder);
        DFTypeSpace packageSpace = _rootSpace.lookupSpace(src.cunit.getPackage());
        finder = new DFTypeFinder(packageSpace, finder);
        // Populate the import space.
        DFTypeSpace importSpace = new DFTypeSpace("import:"+src.path);
        for (ImportDeclaration importDecl :
                 (List<ImportDeclaration>) src.cunit.imports()) {
            Name name = importDecl.getName();
            if (importDecl.isOnDemand()) {
                Logger.debug("Import:", name+".*");
                finder = new DFTypeFinder(_rootSpace.lookupSpace(name), finder);
            } else if (!importDecl.isStatic()) {
                assert name.isQualifiedName();
                DFKlass klass = _rootSpace.getKlass(name);
                if (klass != null) {
                    Logger.debug("Import:", name);
                    String id = ((QualifiedName)name).getName().getIdentifier();
                    importSpace.addKlass(id, klass);
                } else {
                    Logger.error("Import: Class not found:", name);
                }
            }
        }
        // Set a top-level finder.
        finder = new DFTypeFinder(importSpace, finder);
        for (DFSourceKlass klass : _fileKlasses.get(src)) {
            klass.setBaseFinder(finder);
        }
    }

    @SuppressWarnings("unchecked")
    private void listUsedKlasses(
        SourceFile src, Collection<DFSourceKlass> klasses)
        throws InvalidSyntax {
        // Process static imports.
        DFFileScope fileScope = _fileScope.get(src);
        for (ImportDeclaration importDecl :
                 (List<ImportDeclaration>) src.cunit.imports()) {
            if (!importDecl.isStatic()) continue;
            Name name = importDecl.getName();
            if (importDecl.isOnDemand()) {
                DFKlass klass = _rootSpace.getKlass(name);
                fileScope.importStatic(klass);
            } else {
                QualifiedName qname = (QualifiedName)name;
                DFKlass klass = _rootSpace.getKlass(qname.getQualifier());
                fileScope.importStatic(klass, qname.getName());
            }
        }
        // List all the klasses used.
        for (DFSourceKlass klass : _fileKlasses.get(src)) {
            klass.listUsedKlasses(klasses);
        }
    }

    private void expandKlasses(Collection<DFSourceKlass> klasses)
        throws InvalidSyntax {
        // At this point, all the methods in all the used classes
        // (public, inner, in-statement and anonymous) are known.
        Queue<DFSourceMethod> queue = new ArrayDeque<DFSourceMethod>();

        // List method overrides.
        for (DFSourceKlass klass : klasses) {
            klass.overrideMethods();
        }

        // Build call graphs (normal classes).
        Collection<DFSourceKlass> defined = new ArrayList<DFSourceKlass>();
        for (DFSourceKlass klass : klasses) {
            klass.listDefinedKlasses(defined);
            for (DFMethod method : klass.getMethods()) {
                if (method instanceof DFSourceMethod) {
                    queue.add((DFSourceMethod)method);
                }
            }
        }

        // Repeat until there is no newly defined klass.
        while (!defined.isEmpty()) {
            klasses.addAll(defined);
            Collection<DFSourceKlass> tmp = new ArrayList<DFSourceKlass>();
            for (DFSourceKlass klass : defined) {
                klass.overrideMethods();
            }
            // Build call graphs (lambda and methodref).
            for (DFSourceKlass klass : defined) {
                klass.listDefinedKlasses(tmp);
                for (DFMethod method : klass.getMethods()) {
                    if (method instanceof DFSourceMethod) {
                        queue.add((DFSourceMethod)method);
                    }
                }
            }
            defined = tmp;
        }

        // Expand input/output refs of each method
        // based on the methods it calls.
        while (!queue.isEmpty()) {
            DFSourceMethod callee = queue.remove();
            for (DFMethod caller : callee.getCallers()) {
                if (caller instanceof DFSourceMethod) {
                    if (((DFSourceMethod)caller).expandRefs(callee)) {
                        queue.add((DFSourceMethod)caller);
                    }
                }
            }
        }
    }

    // Stage5: perform the analysis for each method.
    @SuppressWarnings("unchecked")
    public void analyzeKlass(Exporter exporter, DFSourceKlass klass, boolean strict)
        throws InvalidSyntax, EntityNotFound {
        try {
            assert klass.isResolved();
            exporter.startKlass(klass);
            List<DFMethod> methods = new ArrayList<DFMethod>();
            DFMethod init = klass.getInitMethod();
            if (init != null) {
                methods.add(init);
            }
            for (DFMethod method : klass.getMethods()) {
                methods.add(method);
                if (method.isGeneric()) {
                    methods.addAll(method.getConcreteMethods());
                }
            }
            for (DFMethod method : methods) {
                if (method instanceof DFSourceMethod) {
                    DFSourceMethod srcMethod = (DFSourceMethod)method;
                    try {
                        Logger.info("Stage5:", method.getSignature());
                        DFGraph graph = srcMethod.getGraph(exporter);
                        if (graph != null) {
                            exporter.writeMethod(srcMethod, graph);
                        }
                    } catch (EntityNotFound e) {
                        if (strict) throw e;
                    }
                }
            }
        } finally {
            exporter.endKlass();
        }
    }

    public void analyzeKlass(Exporter exporter, DFSourceKlass klass)
        throws InvalidSyntax, EntityNotFound {
        this.analyzeKlass(exporter, klass, false);
    }

    /**
     * Provides a command line interface.
     *
     * Usage: java Java2DF [-o output] input.java ...
     */
    public static void main(String[] args)
        throws IOException, InvalidSyntax, EntityNotFound {

        // Parse the options.
        List<String> files = new ArrayList<String>();
        List<String> classpath = new ArrayList<String>();
        OutputStream output = System.out;
        String sep = System.getProperty("path.separator");
        boolean strict = false;
        boolean reformat = true;
        Logger.LogLevel = 0;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--")) {
                while (i < args.length) {
                    files.add(args[++i]);
                }
            } else if (arg.equals("-v")) {
                Logger.LogLevel++;
            } else if (arg.equals("-i")) {
                String path = args[++i];
                InputStream input = System.in;
                try {
                    if (!path.equals("-")) {
                        input = new FileInputStream(path);
                    }
                    Logger.info("Input file:", path);
                    BufferedReader reader = new BufferedReader(
                        new InputStreamReader(input));
                    while (true) {
                        String line = reader.readLine();
                        if (line == null) break;
                        files.add(line);
                    }
                } catch (IOException e) {
                    System.err.println("Cannot open input file: "+path);
                }
            } else if (arg.equals("-o")) {
                String path = args[++i];
                try {
                    output = new BufferedOutputStream(new FileOutputStream(path));
                    Logger.info("Exporting:", path);
                } catch (IOException e) {
                    System.err.println("Cannot open output file: "+path);
                }
            } else if (arg.equals("-C")) {
                for (String path : args[++i].split(sep)) {
                    classpath.add(path);
                }
            } else if (arg.equals("-a")) {
                DFSourceMethod.setDefaultTransparent(true);
            } else if (arg.equals("-S")) {
                strict = true;
            } else if (arg.equals("-s")) {
                reformat = false;
            } else if (arg.startsWith("-")) {
                System.err.println("Unknown option: "+arg);
                System.err.println(
                    "usage: Java2DF [-v] [-i input] [-o output]" +
                    " [-C classpath] [-a] [-S] [-s] [path ...]");
                System.exit(1);
                return;
            } else {
                files.add(arg);
            }
        }

        Java2DF converter = new Java2DF();
        converter.loadDefaults();

        // Add the target souce files first.
        for (String path : files) {
            for (File file : Utils.enumerateFiles(path)) {
                String name = file.getPath();
                if (name.endsWith(".java")) {
                    Logger.info("Parsing:", name);
                    try {
                        converter.addSourceFile(name, true);
                    } catch (IOException e) {
                        Logger.error("Parsing: IOException at "+path);
                        throw e;
                    }
                }
            }
        }

        // Add the source files from the classpath.
        // (ones which are already added are skipped.)
        for (String path : classpath) {
            if (path.endsWith(".jar")) {
                converter.loadJarFile(new File(path));
            } else {
                for (File file : Utils.enumerateFiles(path)) {
                    String name = file.getPath();
                    if (name.endsWith(".java")) {
                        try {
                            converter.addSourceFile(name, false);
                        } catch (IOException e) {
                            Logger.error("Parsing: IOException at "+path);
                            throw e;
                        }
                    }
                }
            }
        }

        Collection<DFSourceKlass> klasses = converter.getSourceKlasses();

        ByteArrayOutputStream temp = null;
        if (reformat) {
            temp = new ByteArrayOutputStream();
        }

        XmlExporter exporter = new XmlExporter((temp != null)? temp : output);
        for (DFSourceKlass klass : klasses) {
            if (!klass.isAnalyze()) continue;
            try {
                converter.analyzeKlass(exporter, klass, strict);
            } catch (EntityNotFound e) {
                Logger.error("Stage5: EntityNotFound at", klass,
                             "("+e.name+", method="+e.method+
                             ", ast="+e.ast+")");
                throw e;
            }
        }
        exporter.close();

        if (temp != null) {
            temp.close();
            try {
                InputStream in = new ByteArrayInputStream(temp.toByteArray());
                Document document = Utils.readXml(in);
                in.close();
                document.setXmlStandalone(true);
                Utils.printXml(output, document);
            } catch (Exception e) {
            }
        }

        output.close();
    }
}


//  DFFileScope
//  File-wide scope for methods and variables.
class DFFileScope extends DFVarScope {

    private Map<String, DFRef> _refs =
        new HashMap<String, DFRef>();
    private List<DFMethod> _methods =
        new ArrayList<DFMethod>();

    public DFFileScope(DFVarScope outer, String path) {
        super(outer, "["+path+"]");
    }

    @Override
    public DFRef lookupVar(String id)
        throws VariableNotFound {
        DFRef ref = _refs.get(id);
        if (ref != null) {
            return ref;
        } else {
            return super.lookupVar(id);
        }
    }

    @Override
    public DFMethod findStaticMethod(SimpleName name, DFType[] argTypes) {
        String id = name.getIdentifier();
        int bestDist = -1;
        DFMethod bestMethod = null;
        for (DFMethod method1 : _methods) {
            if (!id.equals(method1.getName())) continue;
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

    public void importStatic(DFKlass klass) {
        Logger.debug("ImportStatic:", klass+".*");
        for (DFKlass.FieldRef ref : klass.getFields()) {
            _refs.put(ref.getName(), ref);
        }
        for (DFMethod method : klass.getMethods()) {
            _methods.add(method);
        }
    }

    public void importStatic(DFKlass klass, SimpleName name) {
        Logger.debug("ImportStatic:", klass+"."+name);
        String id = name.getIdentifier();
        DFRef ref = klass.getField(id);
        if (ref != null) {
            _refs.put(id, ref);
        } else {
            DFMethod method = klass.findMethod(
                DFMethod.CallStyle.StaticMethod, name, null);
            if (method != null) {
                _methods.add(method);
            }
        }
    }
}
