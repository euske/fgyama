//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;


//  DFBuiltinTypes
//
public class DFBuiltinTypes {

    public static void initialize(DFRootTypeSpace rootSpace)
        throws IOException, InvalidSyntax {
        // Note: manually create some of the built-in classes that are
        // self-referential and cannot be automatically loaded.
        DFTypeSpace java_lang = rootSpace.lookupSpace("java.lang");
        _object = createKlass(java_lang, "Object");
        _class = createKlass(java_lang, "Class");
        _enum = createKlass(java_lang, "Enum");
        _string = createKlass(java_lang, "String");
        _byte = createKlass(java_lang, "Byte");
        _character = createKlass(java_lang, "Character");
        _short = createKlass(java_lang, "Short");
        _integer = createKlass(java_lang, "Integer");
        _long = createKlass(java_lang, "Long");
        _float = createKlass(java_lang, "Float");
        _double = createKlass(java_lang, "Double");
        _boolean = createKlass(java_lang, "Boolean");
        _exception = createKlass(java_lang, "Exception");
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

    private static DFKlass createKlass(DFTypeSpace typeSpace, String id) {
        return typeSpace.addKlass(
            id, new DFKlass(id, typeSpace, null, null, _object));
    }

    private static class ArrayKlass extends DFKlass {
        public ArrayKlass(DFTypeSpace typeSpace, DFKlass baseKlass) {
            super("_Array", typeSpace, null, null, baseKlass);
            this.loadManually(false, baseKlass, null);
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
