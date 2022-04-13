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
        DFTypeSpace langSpace = rootSpace.getSubSpace("java.lang");
        assert langSpace != null;
        _object = (DFClsFileKlass)langSpace.getKlass("Object");
        _class = (DFClsFileKlass)langSpace.getKlass("Class");
        _enum = (DFClsFileKlass)langSpace.getKlass("Enum");
        _string = (DFClsFileKlass)langSpace.getKlass("String");
        _byte = (DFClsFileKlass)langSpace.getKlass("Byte");
        _character = (DFClsFileKlass)langSpace.getKlass("Character");
        _short = (DFClsFileKlass)langSpace.getKlass("Short");
        _integer = (DFClsFileKlass)langSpace.getKlass("Integer");
        _long = (DFClsFileKlass)langSpace.getKlass("Long");
        _float = (DFClsFileKlass)langSpace.getKlass("Float");
        _double = (DFClsFileKlass)langSpace.getKlass("Double");
        _boolean = (DFClsFileKlass)langSpace.getKlass("Boolean");
        _void = (DFClsFileKlass)langSpace.getKlass("Void");
        _exception = (DFClsFileKlass)langSpace.getKlass("Exception");
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
