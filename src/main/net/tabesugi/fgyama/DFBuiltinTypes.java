//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;


//  DFBuiltinTypes
//
public class DFBuiltinTypes {

    public static void initialize(DFRootTypeCollection rootSpace)
        throws IOException, TypeNotFound {
        // Note: some of the built-in classes are self-referential
        // that cannot be automatically loaded. So create them manually.
        DFTypeCollection java_lang = rootSpace.lookupSpace("java.lang");
        _object = java_lang.createKlass(null, null, "Object");
        _class = java_lang.createKlass(null, null, "Class");
        _enum = java_lang.createKlass(null, null, "Enum");
        _string = java_lang.createKlass(null, null, "String");
        _byte = java_lang.createKlass(null, null, "Byte");
        _character = java_lang.createKlass(null, null, "Character");
        _short = java_lang.createKlass(null, null, "Short");
        _integer = java_lang.createKlass(null, null, "Integer");
        _long = java_lang.createKlass(null, null, "Long");
        _float = java_lang.createKlass(null, null, "Float");
        _double = java_lang.createKlass(null, null, "Double");
        _boolean = java_lang.createKlass(null, null, "Boolean");
        _exception = java_lang.createKlass(null, null, "Exception");
        _array = new ArrayKlass(java_lang, _object);
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
        _exception.load();
    }

    private static class ArrayKlass extends DFKlass {
        public ArrayKlass(DFTypeCollection typeSpace, DFKlass baseKlass) {
            super("_Array", typeSpace, null, null, baseKlass);
            this.addField("length", false, DFBasicType.INT);
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
