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

    public DFTypeFinder finder;

    private String _text;
    private int _pos;

    public JNITypeParser(DFTypeFinder finder, String text) {
        this.finder = finder;
        _text = text;
        _pos = 0;
    }

    public DFType getType()
        throws TypeNotFound {
        char c = _text.charAt(_pos);
        _pos++;

        switch (c) {
        case 'B':
            return DFBasicType.BYTE;
        case 'C':
            return DFBasicType.CHAR;
        case 'S':
            return DFBasicType.SHORT;
        case 'I':
            return DFBasicType.INT;
        case 'J':
            return DFBasicType.LONG;
        case 'F':
            return DFBasicType.FLOAT;
        case 'D':
            return DFBasicType.DOUBLE;
        case 'Z':
            return DFBasicType.BOOLEAN;
        case 'V':
            return DFBasicType.VOID;
        case 'L':
            for (int i = _pos; i < _text.length(); i++) {
                char c2 =  _text.charAt(i);
                if (c2 == '<' || c2 == ';') {
                    String name = _text.substring(_pos, i);
                    _pos = i+1;
                    DFKlass klass = this.finder.lookupKlass(name.replace('/','.'));
                    if (c2 == ';') {
                        return klass;
                    }
                    List<DFType> types = new ArrayList<DFType>();
                    while (_text.charAt(_pos) != '>') {
                        types.add(this.getType());
                    }
                    _pos++;
                    DFType[] argTypes = new DFType[types.size()];
                    types.toArray(argTypes);
                    return klass.getParamKlass(argTypes);
                }
            }
            break;
        case 'T':
            for (int i = _pos; i < _text.length(); i++) {
                if (_text.charAt(i) == ';') {
                    String name = _text.substring(_pos, i);
                    _pos = i+1;
                    return this.finder.lookupParamType(name);
                }
            }
            break;
        case '[':
            for (int i = _pos; i < _text.length(); i++) {
                if (_text.charAt(i) != '[') {
                    int ndims = 1+_pos-i;
                    _pos = i;
                    DFType elemType = this.getType();
                    return new DFArrayType(elemType, ndims);
                }
            }
            break;
        case '+':
            return this.getType();
        case '-':
        case '*':
            return DFRootTypeSpace.OBJECT_KLASS;
        case '(':
            List<DFType> types = new ArrayList<DFType>();
            while (_text.charAt(_pos) != '>') {
                types.add(this.getType());
            }
            _pos++;
            DFType[] argTypes = new DFType[types.size()];
            types.toArray(argTypes);
            DFType returnType = this.getType();
            return new DFMethodType(argTypes, returnType);
        default:
            break;
        }
        throw new TypeNotFound(_text.substring(_pos));
    }
}
