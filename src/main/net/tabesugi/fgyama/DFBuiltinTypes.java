//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;


//  DFBuiltinTypes
//
public class DFBuiltinTypes {

    private static DFTypeFinder _finder;
    private static DFTypeSpace _langSpace;

    public static void initialize(DFRootTypeSpace rootSpace)
        throws IOException, InvalidSyntax {
        // Note: manually create some of the built-in classes that are
        // self-referential and cannot be automatically loaded.
        _finder = new DFTypeFinder(rootSpace);
        _langSpace = rootSpace.lookupSpace("java.lang");
        _object = createKlass("Object");
        _class = createKlass("Class");
        _enum = createKlass("Enum");
        _string = createKlass("String");
        _byte = createKlass("Byte");
        _character = createKlass("Character");
        _short = createKlass("Short");
        _integer = createKlass("Integer");
        _long = createKlass("Long");
        _float = createKlass("Float");
        _double = createKlass("Double");
        _boolean = createKlass("Boolean");
        _void = createKlass("Void");
        _exception = createKlass("Exception");
        _array = new ArrayKlass();
        File homeDir = new File(System.getProperty("java.home"));
        File libDir = new File(homeDir, "lib");
        File rtFile = new File(libDir, "rt.jar");
        rootSpace.loadJarFile(rtFile.getAbsolutePath());
        _object.load();
        _class.load();
        _enum.load();
        _string.load();
        _byte.load();
        _character.load();
        _short.load();
        _integer.load();
        _long.load();
        _float.load();
        _double.load();
        _boolean.load();
        _void.load();
        _exception.load();
    }

    private static DFKlass createKlass(String id) {
        DFJarFileKlass klass = new DFJarFileKlass(id, _langSpace, null, null);
        klass.setFinder(_finder);
        _langSpace.addKlass(id, klass);
        return klass;
    }

    private static class ArrayKlass extends DFKlass {
        public ArrayKlass() {
            super("_Array", _langSpace, null, null);
            this.initScope();
            this.addField("length", false, DFBasicType.INT);
        }
        public int isSubclassOf(DFKlass klass, Map<DFMapType, DFKlass> typeMap) {
            if (this == klass) return 0;
            return -1;
        }
    }

    private static DFKlass _object = null;
    public static DFKlass getObjectKlass() {
        assert _object != null;
        return _object;
    }

    private static DFKlass _class = null;
    public static DFKlass getClassKlass() {
        assert _class != null;
        return _class;
    }

    private static DFKlass _enum = null;
    public static DFKlass getEnumKlass() {
        assert _enum != null;
        return _enum;
    }

    private static DFKlass _string = null;
    public static DFKlass getStringKlass() {
        assert _string != null;
        return _string;
    }

    private static DFKlass _byte = null;
    public static DFKlass getByteKlass() {
        assert _byte != null;
        return _byte;
    }

    private static DFKlass _character = null;
    public static DFKlass getCharacterKlass() {
        assert _character != null;
        return _character;
    }

    private static DFKlass _short = null;
    public static DFKlass getShortKlass() {
        assert _short != null;
        return _short;
    }

    private static DFKlass _integer = null;
    public static DFKlass getIntegerKlass() {
        assert _integer != null;
        return _integer;
    }

    private static DFKlass _long = null;
    public static DFKlass getLongKlass() {
        assert _long != null;
        return _long;
    }

    private static DFKlass _float = null;
    public static DFKlass getFloatKlass() {
        assert _float != null;
        return _float;
    }

    private static DFKlass _double = null;
    public static DFKlass getDoubleKlass() {
        assert _double != null;
        return _double;
    }

    private static DFKlass _boolean = null;
    public static DFKlass getBooleanKlass() {
        assert _boolean != null;
        return _boolean;
    }

    private static DFKlass _void = null;
    public static DFKlass getVoidKlass() {
        assert _void != null;
        return _void;
    }

    private static DFKlass _exception = null;
    public static DFKlass getExceptionKlass() {
        assert _exception != null;
        return _exception;
    }

    private static DFKlass _array = null;
    public static DFKlass getArrayKlass() {
        assert _array != null;
        return _array;
    }

}
