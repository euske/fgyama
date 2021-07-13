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
        addSourceFile(path, true);
    }

    public void addSourceFile(String path, boolean analyze)
        throws IOException {
        File file = new File(path);
        String key = file.getCanonicalPath();
        if (!_sourceFiles.containsKey(key)) {
            CompilationUnit cunit = Utils.parseFile(file);
            cunit.setProperty("path", path);
            SourceFile srcFile = new SourceFile(path, cunit, analyze);
            _sourceFiles.put(key, srcFile);
        }
    }

    public Collection<DFSourceKlass> getSourceKlasses(boolean expand)
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
        Set<DFSourceKlass> klasses = new ConsistentHashSet<DFSourceKlass>();
        for (SourceFile src : _sourceFiles.values()) {
            Logger.info("Stage3:", src);
            this.listUsedKlasses(src, klasses);
        }

        // Stage4: expand classes and method refs.
        Logger.info("Stage4: expanding "+klasses.size()+" klasses...");
        List<DFSourceMethod> methods = this.expandKlasses(klasses);
        if (expand) {
            Logger.info("Stage4: expanding "+methods.size()+" method refs...");
            this.expandRefs(methods);
        }

        return klasses;
    }

    @SuppressWarnings("unchecked")
    private void buildTypeSpace(SourceFile src)
        throws InvalidSyntax {
        DFTypeSpace packageSpace = _rootSpace.addSubSpace(src.cunit.getPackage());
        DFFileScope fileScope = new DFFileScope(_globalScope, src.path);
        _fileScope.put(src, fileScope);
        List<DFSourceKlass> klasses = new ArrayList<DFSourceKlass>();
        for (AbstractTypeDeclaration abstTypeDecl :
                 (List<AbstractTypeDeclaration>) src.cunit.types()) {
            try {
                DFSourceKlass klass = new DFTypeDeclKlass(
                    abstTypeDecl, packageSpace, null, fileScope,
                    src.path, src.analyze);
                packageSpace.addKlass(abstTypeDecl.getName().getIdentifier(), klass);
                Logger.debug("Stage1: Created:", klass);
                klasses.add(klass);
            } catch (EntityDuplicate e) {
                Logger.error("Stage1: Class duplicate:", e.name);
            }
        }
        _fileKlasses.put(src, klasses);
    }

    @SuppressWarnings("unchecked")
    private void setTypeFinder(SourceFile src) {
        // Search path for types: ROOT -> java.lang -> package -> imports.
        DFTypeFinder finder = new DFTypeFinder(_rootSpace);
        finder = new DFTypeFinder(_rootSpace.getSubSpace("java.lang"), finder);
        DFTypeSpace packageSpace = _rootSpace.getSubSpace(src.cunit.getPackage());
        finder = new DFTypeFinder(packageSpace, finder);
        // Populate the import space.
        DFTypeSpace importSpace = new DFTypeSpace("import:"+src.path, null);
        for (ImportDeclaration importDecl :
                 (List<ImportDeclaration>) src.cunit.imports()) {
            Name name = importDecl.getName();
            if (importDecl.isOnDemand()) {
                Logger.debug("Import:", name+".*");
                finder = new DFTypeFinder(_rootSpace.getSubSpace(name), finder);
            } else if (!importDecl.isStatic()) {
                assert name.isQualifiedName();
                DFKlass klass = _rootSpace.getRootKlass(name);
                if (klass != null) {
                    Logger.debug("Import:", name);
                    String id = ((QualifiedName)name).getName().getIdentifier();
                    try {
                        importSpace.addKlass(id, klass);
                    } catch (EntityDuplicate e) {
                        Logger.error("Stage2: Class duplicate:", e.name);
                    }
                } else {
                    Logger.error("Stage2: Class not found:", Utils.getASTSource(name));
                }
            }
        }
        // Set a top-level finder.
        finder = new DFTypeFinder(importSpace, finder);
        for (DFSourceKlass klass : _fileKlasses.get(src)) {
            klass.initializeFinder(finder);
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
                DFKlass klass = _rootSpace.getRootKlass(name);
                if (klass != null) {
                    fileScope.importStatic(klass);
                }
            } else {
                QualifiedName qname = (QualifiedName)name;
                DFKlass klass = _rootSpace.getRootKlass(qname.getQualifier());
                if (klass != null) {
                    fileScope.importStatic(klass, qname.getName());
                }
            }
        }
        // List all the klasses used.
        for (DFSourceKlass klass : _fileKlasses.get(src)) {
            klass.listUsedKlasses(klasses);
        }
    }

    private List<DFSourceMethod> expandKlasses(Collection<DFSourceKlass> klasses)
        throws InvalidSyntax {
        // At this point, all the methods in all the used classes
        // (public, inner, in-statement and anonymous) are known.
        List<DFSourceMethod> methods = new ArrayList<DFSourceMethod>();

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
                    methods.add((DFSourceMethod)method);
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
                        methods.add((DFSourceMethod)method);
                    }
                }
            }
            defined = tmp;
        }

        return methods;
    }

    private void expandRefs(Collection<DFSourceMethod> methods) {
        // Expand input/output refs of each method
        // based on the methods it calls.
        // Identify SCCs from the call graph:
        //   SCC.to: caller.
        //   SCC.from: callee.
        SCCFinder<DFSourceMethod> f = new SCCFinder<DFSourceMethod>(
            (DFSourceMethod method) -> {
                List<DFSourceMethod> callers = new ArrayList<DFSourceMethod>();
                for (DFMethod caller : method.getCallers()) {
                    if (caller instanceof DFSourceMethod) {
                        callers.add((DFSourceMethod)caller);
                    }
                }
                return callers;
            });
        f.add(methods);

        // RefSet: holds input/output variables for each SCC.
        class RefSet {
            SCCFinder<DFSourceMethod>.SCC scc;
            Set<DFRef> inputRefs = new ConsistentHashSet<DFRef>();
            Set<DFRef> outputRefs = new ConsistentHashSet<DFRef>();
            RefSet(SCCFinder<DFSourceMethod>.SCC scc) {
                this.scc = scc;
                for (DFSourceMethod method : scc.items) {
                    inputRefs.addAll(method.getInputRefs());
                    outputRefs.addAll(method.getOutputRefs());
                }
            }
            void fixate() {
                for (DFSourceMethod method : scc.items) {
                    method.expandRefs(this.inputRefs, this.outputRefs);
                }
            }
            void expandRefs(RefSet rset) {
                inputRefs.addAll(rset.inputRefs);
                outputRefs.addAll(rset.outputRefs);
            }
        };

        // SCCs are topologically sorted from caller -> callee.
        List<RefSet> rsets = new ArrayList<RefSet>();
        Map<SCCFinder<DFSourceMethod>.SCC, RefSet> scc2rset =
            new HashMap<SCCFinder<DFSourceMethod>.SCC, RefSet>();
        for (SCCFinder<DFSourceMethod>.SCC scc : f.getSCCs()) {
            RefSet rset = new RefSet(scc);
            rsets.add(rset);
            scc2rset.put(scc, rset);
        }

        // Reverse the list and start from the bottom callees.
        Collections.reverse(rsets);
        for (RefSet r0 : rsets) {
            r0.fixate();
            for (SCCFinder<DFSourceMethod>.SCC scc : r0.scc.to) {
                RefSet r1 = scc2rset.get(scc);
                r1.expandRefs(r0);
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
                    methods.addAll(method.getReifiedMethods());
                }
            }
            for (DFMethod method : methods) {
                Logger.info("Stage5:", method.getSignature());
                try {
                    exporter.writeMethod(method);
                } catch (EntityNotFound e) {
                    if (strict) throw e;
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
        boolean reformat = false;
        boolean expand = false;
        Logger.LogLevel = 0;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--")) {
                while (i < args.length) {
                    files.add(args[++i]);
                }
            } else if (arg.equals("-v")) {
                Logger.LogLevel++;
            } else if (arg.equals("-S")) {
                strict = true;
            } else if (arg.equals("-F")) {
                reformat = true;
            } else if (arg.equals("-E")) {
                expand = true;
            } else if (arg.startsWith("-i")) {
                String path = ((arg.length() == 2)? args[++i] : arg.substring(2));
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
            } else if (arg.startsWith("-o")) {
                String path = ((arg.length() == 2)? args[++i] : arg.substring(2));
                try {
                    output = new BufferedOutputStream(new FileOutputStream(path));
                    Logger.info("Exporting:", path);
                } catch (IOException e) {
                    System.err.println("Cannot open output file: "+path);
                }
            } else if (arg.startsWith("-C")) {
                String paths = ((arg.length() == 2)? args[++i] : arg.substring(2));
                for (String path : paths.split(sep)) {
                    classpath.add(path);
                }
            } else if (arg.startsWith("-D")) {
                String v = ((arg.length() == 2)? args[++i] : arg.substring(2));
                DFKlass.MaxReifyDepth = Integer.parseInt(v);
            } else if (arg.startsWith("-")) {
                System.err.println("Unknown option: "+arg);
                System.err.println(
                    "usage: Java2DF [-v] [-S] [-F] [-E] [-i input] [-o output]" +
                    " [-C classpath] [-D depth] [path ...]");
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

        Collection<DFSourceKlass> klasses = converter.getSourceKlasses(expand);

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
    public DFMethod lookupStaticMethod(
        SimpleName name, DFType[] argTypes, DFType returnType)
        throws MethodNotFound {
        String id = name.getIdentifier();
        int bestDist = -1;
        DFMethod bestMethod = null;
        for (DFMethod method1 : _methods) {
            if (!id.equals(method1.getName())) continue;
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
        if (bestMethod == null) throw new MethodNotFound(name, argTypes, returnType);
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
            try {
                DFMethod method = klass.lookupMethod(
                    DFMethod.CallStyle.StaticMethod, name, null, null);
                _methods.add(method);
            } catch (MethodNotFound e) {
            }
        }
    }
}
