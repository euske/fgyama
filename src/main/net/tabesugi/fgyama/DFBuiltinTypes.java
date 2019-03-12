//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;


//  DFBuiltinTypes
//
public class DFBuiltinTypes {

    public static void initialize(DFRootTypeSpace rootSpace)
        throws IOException, TypeNotFound {
        File homeDir = new File(System.getProperty("java.home"));
        File libDir = new File(homeDir, "lib");
        File rtFile = new File(libDir, "rt.jar");
        rootSpace.loadJarFile(rtFile.getAbsolutePath());
        DFTypeSpace java_lang = rootSpace.lookupSpace("java.lang");
        _object = java_lang.getKlass("Object");
        _class = java_lang.getKlass("Class");
        _enum = java_lang.getKlass("Enum");
        _string = java_lang.getKlass("String");
        _byte = java_lang.getKlass("Byte");
        _character = java_lang.getKlass("Character");
        _short = java_lang.getKlass("Short");
        _integer = java_lang.getKlass("Integer");
        _long = java_lang.getKlass("Long");
        _float = java_lang.getKlass("Float");
        _double = java_lang.getKlass("Double");
        _boolean = java_lang.getKlass("Boolean");
        _exception = java_lang.getKlass("Exception");
        _array = new ArrayKlass(java_lang, _object);
        _object.load();
        //_class.load();
        //_enum.load();
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
        public ArrayKlass(DFTypeSpace typeSpace, DFKlass baseKlass) {
            super("_Array", typeSpace, null, null);
            this.setBaseKlass(baseKlass);
            this.setBuilt();
            this.addField("length", false, DFBasicType.INT);
        }
    }

    private static DFKlass _object = null;
    public static DFKlass getObjectKlass() {
        return _object;
    }

    private static DFKlass _class = null;
    public static DFKlass getClassKlass() {
        return _class;
    }

    private static DFKlass _enum = null;
    public static DFKlass getEnumKlass() {
        return _enum;
    }

    private static DFKlass _string = null;
    public static DFKlass getStringKlass() {
        return _string;
    }

    private static DFKlass _byte = null;
    public static DFKlass getByteKlass() {
        return _byte;
    }

    private static DFKlass _character = null;
    public static DFKlass getCharacterKlass() {
        return _character;
    }

    private static DFKlass _short = null;
    public static DFKlass getShortKlass() {
        return _short;
    }

    private static DFKlass _integer = null;
    public static DFKlass getIntegerKlass() {
        return _integer;
    }

    private static DFKlass _long = null;
    public static DFKlass getLongKlass() {
        return _long;
    }

    private static DFKlass _float = null;
    public static DFKlass getFloatKlass() {
        return _float;
    }

    private static DFKlass _double = null;
    public static DFKlass getDoubleKlass() {
        return _double;
    }

    private static DFKlass _boolean = null;
    public static DFKlass getBooleanKlass() {
        return _boolean;
    }

    private static DFKlass _exception = null;
    public static DFKlass getExceptionKlass() {
        return _exception;
    }

    private static DFKlass _array = null;
    public static DFKlass getArrayKlass() {
        return _array;
    }

}
