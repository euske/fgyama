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
            int dist = method1.canAccept(argTypes, typeMap);
            if (dist < 0) continue;
            if (bestDist < 0 || dist < bestDist) {
                DFMethod method = method1.getConcreteMethod(typeMap);
                if (method != null) {
                    bestDist = dist;
                    bestMethod = method;
                }
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


//  Java2DF
//
public class Java2DF {

    private class Entry {
        String key;
        CompilationUnit cunit;
        public Entry(String key, CompilationUnit cunit) {
            this.key = key;
            this.cunit = cunit;
        }
    }

    private DFRootTypeSpace _rootSpace;
    private List<Exporter> _exporters =
        new ArrayList<Exporter>();
    private DFGlobalScope _globalScope =
        new DFGlobalScope();
    private List<Entry> _sourceFiles =
        new ArrayList<Entry>();
    private Map<String, DFFileScope> _fileScope =
        new HashMap<String, DFFileScope>();
    private Map<String, List<DFSourceKlass>> _fileKlasses =
        new HashMap<String, List<DFSourceKlass>>();

    /// Top-level functions.

    public Java2DF(
        DFRootTypeSpace rootSpace) {
        _rootSpace = rootSpace;
    }

    public void addExporter(Exporter exporter) {
        _exporters.add(exporter);
    }

    public void removeExporter(Exporter exporter) {
        _exporters.remove(exporter);
    }

    private void exportGraph(DFGraph graph) {
        for (Exporter exporter : _exporters) {
            exporter.writeGraph(graph);
        }
    }
    private void startKlass(DFSourceKlass klass) {
        for (Exporter exporter : _exporters) {
            exporter.startKlass(klass);
        }
    }
    private void endKlass() {
        for (Exporter exporter : _exporters) {
            exporter.endKlass();
        }
    }

    public void clearSourceFiles() {
        _sourceFiles.clear();
    }

    public void addSourceFile(String key, CompilationUnit cunit) {
        _sourceFiles.add(new Entry(key, cunit));
    }

    public Collection<DFSourceKlass> processAll()
        throws InvalidSyntax {
        // Stage1: populate TypeSpaces.
        for (Entry e : _sourceFiles) {
            Logger.info("Stage1:", e.key);
            this.buildTypeSpace(e.key, e.cunit);
        }

        // Stage2: set references to external Klasses.
        for (Entry e : _sourceFiles) {
            Logger.info("Stage2:", e.key);
            this.setTypeFinder(e.key, e.cunit);
        }

        // Stage3: load class definitions and define parameterized Klasses.
        ConsistentHashSet<DFSourceKlass> klasses =
            new ConsistentHashSet<DFSourceKlass>();
        for (Entry e : _sourceFiles) {
            Logger.info("Stage3:", e.key);
            this.loadKlasses(e.key, e.cunit, klasses);
        }

        // Stage4: list all methods.
        Logger.info("Stage4:");
        this.listMethods(klasses);

        return klasses;
    }

    @SuppressWarnings("unchecked")
    private void buildTypeSpace(String key, CompilationUnit cunit)
        throws InvalidSyntax {
        DFTypeSpace packageSpace = _rootSpace.lookupSpace(cunit.getPackage());
        DFFileScope fileScope = new DFFileScope(_globalScope, key);
        _fileScope.put(key, fileScope);
        List<DFSourceKlass> klasses = new ArrayList<DFSourceKlass>();
        for (AbstractTypeDeclaration abstTypeDecl :
                 (List<AbstractTypeDeclaration>) cunit.types()) {
            DFSourceKlass klass = new DFAbstTypeDeclKlass(
                key, abstTypeDecl, packageSpace, fileScope, null);
            packageSpace.addKlass(abstTypeDecl.getName().getIdentifier(), klass);
            Logger.debug("Stage1: Created:", klass);
            klasses.add(klass);
        }
        _fileKlasses.put(key, klasses);
    }

    @SuppressWarnings("unchecked")
    private void setTypeFinder(String key, CompilationUnit cunit) {
        // Search path for types: ROOT -> java.lang -> package -> imports.
        DFTypeFinder finder = new DFTypeFinder(_rootSpace);
        finder = new DFTypeFinder(_rootSpace.lookupSpace("java.lang"), finder);
        DFTypeSpace packageSpace = _rootSpace.lookupSpace(cunit.getPackage());
        finder = new DFTypeFinder(packageSpace, finder);
        // Populate the import space.
        DFTypeSpace importSpace = new DFTypeSpace("import:"+key);
        for (ImportDeclaration importDecl :
                 (List<ImportDeclaration>) cunit.imports()) {
            Name name = importDecl.getName();
            if (importDecl.isOnDemand()) {
                Logger.debug("Import:", name+".*");
                finder = new DFTypeFinder(_rootSpace.lookupSpace(name), finder);
            } else {
                assert name.isQualifiedName();
                DFKlass klass = _rootSpace.getKlass(name);
                if (klass != null) {
                    Logger.debug("Import:", name);
                    String id = ((QualifiedName)name).getName().getIdentifier();
                    importSpace.addKlass(id, klass);
                } else {
                    if (!importDecl.isStatic()) {
                        Logger.error("Import: Class not found:", name);
                    }
                }
            }
        }
        // Set a top-level finder.
        finder = new DFTypeFinder(importSpace, finder);
        for (DFSourceKlass klass : _fileKlasses.get(key)) {
            klass.setBaseFinder(finder);
        }
    }

    @SuppressWarnings("unchecked")
    private void loadKlasses(
        String key, CompilationUnit cunit, Set<DFSourceKlass> klasses)
        throws InvalidSyntax {
        // Process static imports.
        DFFileScope fileScope = _fileScope.get(key);
        for (ImportDeclaration importDecl :
                 (List<ImportDeclaration>) cunit.imports()) {
            if (!importDecl.isStatic()) continue;
            Name name = importDecl.getName();
            if (importDecl.isOnDemand()) {
                DFKlass klass = _rootSpace.getKlass(name);
                klass.load();
                fileScope.importStatic(klass);
            } else {
                QualifiedName qname = (QualifiedName)name;
                DFKlass klass = _rootSpace.getKlass(qname.getQualifier());
                klass.load();
                fileScope.importStatic(klass, qname.getName());
            }
        }
        // Enumerate all the klasses used.
        for (DFSourceKlass klass : _fileKlasses.get(key)) {
            klass.loadKlasses(klasses);
        }
    }

    private void listMethods(Set<DFSourceKlass> klasses)
        throws InvalidSyntax {
        // At this point, all the methods in all the used classes
        // (public, inner, in-statement and anonymous) are known.
        Queue<DFMethod> queue = new ArrayDeque<DFMethod>();

        // List method overrides.
        for (DFSourceKlass klass : klasses) {
            assert !(klass.isGeneric());
            assert !(klass instanceof DFLambdaKlass);
            klass.overrideMethods();
        }

        // Build method scopes (normal classes).
        for (DFSourceKlass klass : klasses) {
            DFMethod init = klass.getInitMethod();
            if (init != null) {
                init.buildScope();
            }
            for (DFMethod method : klass.getMethods()) {
                method.buildScope();
            }
        }

        // Build call graphs (normal classes).
        List<DFSourceKlass> defined = new ArrayList<DFSourceKlass>();
        for (DFSourceKlass klass : klasses) {
            DFMethod init = klass.getInitMethod();
            if (init != null) {
                init.enumRefs(defined);
            }
            for (DFMethod method : klass.getMethods()) {
                method.enumRefs(defined);
                queue.add(method);
            }
        }

        while (!defined.isEmpty()) {
            klasses.addAll(defined);
            List<DFSourceKlass> defined2 = new ArrayList<DFSourceKlass>();
            for (DFSourceKlass klass : defined) {
                klass.overrideMethods();
            }
            // Build method scopes (lambdas).
            for (DFSourceKlass klass : defined) {
                assert klass.isDefined();
                for (DFMethod method : klass.getMethods()) {
                    method.buildScope();
                }
            }
            // Build call graphs (lambdas).
            for (DFSourceKlass klass : defined) {
                assert klass.isDefined();
                for (DFMethod method : klass.getMethods()) {
                    method.enumRefs(defined2);
                    queue.add(method);
                }
            }
            defined = defined2;
        }

        // Expand callee refs recursively.
        while (!queue.isEmpty()) {
            DFMethod method = queue.remove();
            for (DFMethod caller : method.getCallers()) {
                if (caller.expandRefs(method)) {
                    queue.add(caller);
                }
            }
        }
    }

    // Stage5: generate graphs for each method.
    @SuppressWarnings("unchecked")
    public void buildGraphs(Counter counter, DFSourceKlass klass, boolean strict)
        throws InvalidSyntax, EntityNotFound {
        try {
            this.startKlass(klass);
            if (!(klass instanceof DFLambdaKlass)) {
                try {
                    DFMethod init = klass.getInitMethod();
                    if (init != null) {
                        Logger.info("Stage5:", init.getSignature());
                        DFGraph graph = init.processKlassBody(counter);
                        if (graph != null) {
                            this.exportGraph(graph);
                        }
                    }
                } catch (EntityNotFound e) {
                    if (strict) throw e;
                }
            }
            List<DFMethod> methods = new ArrayList<DFMethod>();
            for (DFMethod method : klass.getMethods()) {
                if (method.isGeneric()) {
                    methods.addAll(method.getConcreteMethods());
                } else {
                    methods.add(method);
                }
            }
            for (DFMethod method : methods) {
                assert !method.isGeneric();
                try {
                    Logger.info("Stage5:", method.getSignature());
                    DFGraph graph = method.processMethod(counter);
                    if (graph != null) {
                        this.exportGraph(graph);
                    }
                } catch (EntityNotFound e) {
                    if (strict) throw e;
                }
            }
        } finally {
            this.endKlass();
        }
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
        List<String> jarfiles = new ArrayList<String>();
        Set<String> processed = new HashSet<String>();
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
            } else if (arg.equals("-v")) {
                Logger.LogLevel++;
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
                    jarfiles.add(path);
                }
            } else if (arg.equals("-p")) {
                processed.add(args[++i]);
            } else if (arg.equals("-S")) {
                strict = true;
            } else if (arg.equals("-s")) {
                reformat = false;
            } else if (arg.equals("-a")) {
                DFMethod.setDefaultTransparent(true);
            } else if (arg.startsWith("-")) {
                System.err.println("Unknown option: "+arg);
                System.err.println(
                    "usage: Java2DF [-v] [-a] [-S] [-i input] [-o output]" +
                    " [-C jar] [-p path] [-s] [path ...]");
                System.exit(1);
                return;
            } else {
                files.add(arg);
            }
        }

        // Initialize base classes.
        File homeDir = new File(System.getProperty("java.home"));
        File libDir = new File(homeDir, "lib");
        File rtFile = new File(libDir, "rt.jar");
        DFRootTypeSpace rootSpace = new DFRootTypeSpace();
        rootSpace.loadJarFile(rtFile.getAbsolutePath());
        DFBuiltinTypes.initialize(rootSpace);
        for (String path : jarfiles) {
            rootSpace.loadJarFile(path);
        }

        // Process files.
        Java2DF converter = new Java2DF(rootSpace);
        for (String path : files) {
            Logger.info("Parsing:", path);
            try {
                CompilationUnit cunit = Utils.parseFile(path);
                converter.addSourceFile(path, cunit);
            } catch (IOException e) {
                Logger.error("Parsing: IOException at "+path);
                throw e;
            }
        }

        Collection<DFSourceKlass> klasses = converter.processAll();

        ByteArrayOutputStream temp = null;
        if (reformat) {
            temp = new ByteArrayOutputStream();
        }

        XmlExporter exporter = new XmlExporter((temp != null)? temp : output);
        converter.addExporter(exporter);
        Counter counter = new Counter(1);
        for (DFSourceKlass klass : klasses) {
            if (!processed.isEmpty() && !processed.contains(klass.getFilePath())) continue;
            try {
                converter.buildGraphs(counter, klass, strict);
            } catch (EntityNotFound e) {
                Logger.error("Stage5: EntityNotFound at", klass,
                             "("+e.name+", method="+e.method+
                             ", ast="+e.ast+")");
                throw e;
            }
        }
        converter.removeExporter(exporter);
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
