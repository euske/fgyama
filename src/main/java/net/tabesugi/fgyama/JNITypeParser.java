//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  JNITypeParser
//
public class JNITypeParser {

    private String _text;
    private int _pos;

    public JNITypeParser(String text) {
        _text = text;
        _pos = 0;
    }

    public DFType resolveType(DFTypeFinder finder)
        throws TypeNotFound {
        if (_text.length() <= _pos) return null;
        //Logger.info("  resolveType:", _text.substring(_pos), "finder="+finder);
        assert _text.charAt(_pos) != '<';
        switch (_text.charAt(_pos)) {
        case 'B':
            _pos++;
            return DFBasicType.BYTE;
        case 'C':
            _pos++;
            return DFBasicType.CHAR;
        case 'S':
            _pos++;
            return DFBasicType.SHORT;
        case 'I':
            _pos++;
            return DFBasicType.INT;
        case 'J':
            _pos++;
            return DFBasicType.LONG;
        case 'F':
            _pos++;
            return DFBasicType.FLOAT;
        case 'D':
            _pos++;
            return DFBasicType.DOUBLE;
        case 'Z':
            _pos++;
            return DFBasicType.BOOLEAN;
        case 'V':
            _pos++;
            return DFBasicType.VOID;
        case 'L':
            _pos++;
            for (int i = _pos; i < _text.length(); i++) {
                char c2 =  _text.charAt(i);
                if (c2 == ';') {
                    String name = _text.substring(_pos, i);
                    _pos = i+1;
                    return finder.resolveKlass(name.replace('/','.'));
                } else if (c2 == '<') {
                    String name = _text.substring(_pos, i);
                    _pos = i;
                    DFKlass klass = finder.resolveKlass(name.replace('/','.'));
                    DFType[] types = this.resolveTypes(finder, '<', '>');
                    DFKlass[] paramTypes = new DFKlass[types.length];
                    for (int j = 0; j < types.length; j++) {
                        paramTypes[j] = types[j].toKlass();
                    }
                    klass = klass.getReifiedKlass(paramTypes);
                    char c3 = _text.charAt(_pos);
                    if (c3 == ';') {
                        _pos++;
                        return klass;
                    } else if (c3 == '.') {
                        _pos++;
                        i = _pos;
                        finder = new DFTypeFinder(klass, finder);
                    } else {
                        assert false; // ???
                    }
                }
            }
            break;
        case 'T':
            _pos++;
            for (int i = _pos; i < _text.length(); i++) {
                if (_text.charAt(i) == ';') {
                    String name = _text.substring(_pos, i);
                    _pos = i+1;
                    return finder.resolveKlass(name);
                }
            }
            break;
        case '[':
            for (int i = _pos; i < _text.length(); i++) {
                if (_text.charAt(i) != '[') {
                    int ndims = i-_pos;
                    _pos = i;
                    DFType elemType = this.resolveType(finder);
                    return DFArrayType.getArray(elemType, ndims);
                }
            }
            break;
        case '+':
            _pos++;
            return this.resolveType(finder);
        case '-':
            _pos++;
            // XXX Treat lowerbound class as Object.
            this.resolveType(finder);
            return DFBuiltinTypes.getObjectKlass();
        case '*':
            _pos++;
            return DFBuiltinTypes.getObjectKlass();
        case '(':
            DFType[] argTypes = this.resolveTypes(finder, '(', ')');
            DFType returnType = this.resolveType(finder);
            return new DFFuncType(argTypes, returnType);
        case '?':
            return DFUnknownType.UNKNOWN;
        default:
            break;
        }
        throw new TypeNotFound(_text.substring(_pos));
    }

    private DFType[] resolveTypes(DFTypeFinder finder, char start, char end)
        throws TypeNotFound {
        assert _text.charAt(_pos) == start;
        _pos++;
        List<DFType> types = new ArrayList<DFType>();
        while (_text.charAt(_pos) != end) {
            types.add(this.resolveType(finder));
        }
        _pos++;
        DFType[] a = new DFType[types.size()];
        types.toArray(a);
        return a;
    }

    public static class TypeSlot {
        public String id;
        public String sig;
        public TypeSlot(String id, String sig) {
            this.id = id;
            this.sig = sig;
        }
    }

    public TypeSlot[] getTypeSlots() {
        if (_text.charAt(_pos) != '<') return null;
        _pos++;
        List<TypeSlot> list = new ArrayList<TypeSlot>();
        while (_text.charAt(_pos) != '>') {
            int i = _text.indexOf(':', _pos);
            String id = _text.substring(_pos, i);
            _pos = i+1;
            if (_text.charAt(_pos) == ':') {
                _pos++;   // ???
            }
            i = skipType(_text, _pos);
            String sig = _text.substring(_pos, i);
            list.add(new TypeSlot(id, sig));
            _pos = i;
        }
        if (list.isEmpty()) return null;
        _pos++;
        TypeSlot[] slots = new TypeSlot[list.size()];
        list.toArray(slots);
        return slots;
    }

    private static int skipType(String text, int pos) {
        if (text.length() <= pos) return pos;
        //Logger.info("  skipType:", text.substring(pos));
        switch (text.charAt(pos)) {
        case 'B':
        case 'C':
        case 'S':
        case 'I':
        case 'J':
        case 'F':
        case 'D':
        case 'Z':
        case 'V':
            pos++;
            break;
        case 'L':
            pos++;
            for (int i = pos; i < text.length(); i++) {
                char c2 =  text.charAt(i);
                if (c2 == ';') {
                    String name = text.substring(pos, i);
                    pos = i+1;
                    break;
                } else if (c2 == '<') {
                    String name = text.substring(pos, i);
                    pos = skipTypes(text, i, '<', '>');
                    assert text.charAt(pos) == ';';
                    pos++;
                    break;
                }
            }
            break;
        case 'T':
            pos++;
            for (int i = pos; i < text.length(); i++) {
                if (text.charAt(i) == ';') {
                    String name = text.substring(pos, i);
                    pos = i+1;
                    break;
                }
            }
            break;
        case '[':
            for (int i = pos; i < text.length(); i++) {
                if (text.charAt(i) != '[') {
                    pos = skipType(text, i);
                    break;
                }
            }
            break;
        case '+':
        case '-':
            pos++;
            pos = skipType(text, pos);
            break;
        case '*':
            pos++;
            break;
        case '(':
            pos = skipTypes(text, pos, '(', ')');
            pos = skipType(text, pos);
            break;
        default:
            break;
        }
        return pos;
    }

    private static int skipTypes(String text, int pos, char start, char end) {
        assert text.charAt(pos) == start;
        pos++;
        while (text.charAt(pos) != end) {
            pos = skipType(text, pos);
        }
        pos++;
        return pos;
    }
}
