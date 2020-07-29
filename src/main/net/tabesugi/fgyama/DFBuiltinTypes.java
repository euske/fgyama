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
        DFTypeSpace langSpace = rootSpace.lookupSpace("java.lang");
        _object = (DFJarFileKlass)langSpace.getKlass("Object");
        _class = (DFJarFileKlass)langSpace.getKlass("Class");
        _enum = (DFJarFileKlass)langSpace.getKlass("Enum");
        _string = (DFJarFileKlass)langSpace.getKlass("String");
        _byte = (DFJarFileKlass)langSpace.getKlass("Byte");
        _character = (DFJarFileKlass)langSpace.getKlass("Character");
        _short = (DFJarFileKlass)langSpace.getKlass("Short");
        _integer = (DFJarFileKlass)langSpace.getKlass("Integer");
        _long = (DFJarFileKlass)langSpace.getKlass("Long");
        _float = (DFJarFileKlass)langSpace.getKlass("Float");
        _double = (DFJarFileKlass)langSpace.getKlass("Double");
        _boolean = (DFJarFileKlass)langSpace.getKlass("Boolean");
        _void = (DFJarFileKlass)langSpace.getKlass("Void");
        _exception = (DFJarFileKlass)langSpace.getKlass("Exception");
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

}
