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
            for (int i = _pos+1; i < _text.length(); i++) {
                char c2 =  _text.charAt(i);
                if (c2 == '<' || c2 == ';') {
                    String name = _text.substring(_pos+1, i);
                    DFKlass klass = this.finder.lookupKlass(name.replace('/','.'));
                    if (c2 == ';') {
                        _pos = i+1;
                        return klass;
                    }
                    DFType[] argTypes = this.getArgTypes('<', '>');
                    return klass.getParamKlass(argTypes);
                }
            }
            break;
        case 'T':
            for (int i = _pos; i < _text.length(); i++) {
                if (_text.charAt(i) == ';') {
                    String name = _text.substring(_pos+1, i);
                    _pos = i+1;
                    return this.finder.lookupParamType(name);
                }
            }
            break;
        case '[':
            for (int i = _pos; i < _text.length(); i++) {
                if (_text.charAt(i) != '[') {
                    int ndims = _pos-i;
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
            return DFRootTypeSpace.getObjectKlass();
        case '(':
            DFType[] argTypes = this.getArgTypes('(', ')');
            DFType returnType = this.getType();
            return new DFMethodType(argTypes, returnType);
        default:
            break;
        }
        throw new TypeNotFound(_text.substring(_pos));
    }

    public DFType[] getArgTypes(char start, char end)
        throws TypeNotFound {
        if (_text.charAt(_pos) != start) return null;
        List<DFType> types = new ArrayList<DFType>();
        while (_text.charAt(_pos) != end) {
            types.add(this.getType());
        }
        _pos++;
        DFType[] argTypes = new DFType[types.size()];
        types.toArray(argTypes);
        return argTypes;
    }
}
