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

    private DFTypeFinder _finder;
    private String _text;
    private int _pos;

    public JNITypeParser(DFTypeFinder finder, String text) {
	_finder = finder;
        _text = text;
        _pos = 0;
    }

    public DFType getType()
        throws TypeNotFound {
	if (_text.length() <= _pos) return null;
	//Logger.info("getType: "+_text.substring(_pos));
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
                    return _finder.lookupKlass(name.replace('/','.'));
		} else if (c2 == '<') {
                    String name = _text.substring(_pos, i);
		    _pos = i;
		    DFKlass klass = _finder.lookupKlass(name.replace('/','.'));
                    DFType[] argTypes = this.getArgTypes('<', '>');
		    assert _text.charAt(_pos) == ';';
		    _pos++;
                    return klass.getParamKlass(argTypes);
                }
            }
            break;
        case 'T':
	    _pos++;
            for (int i = _pos; i < _text.length(); i++) {
                if (_text.charAt(i) == ';') {
                    String name = _text.substring(_pos, i);
                    _pos = i+1;
                    return _finder.lookupParamType(name);
                }
            }
            break;
        case '[':
            for (int i = _pos; i < _text.length(); i++) {
                if (_text.charAt(i) != '[') {
                    int ndims = i-_pos;
                    _pos = i;
                    DFType elemType = this.getType();
                    return new DFArrayType(elemType, ndims);
                }
            }
            break;
        case '+':
            _pos++;
            return this.getType();
        case '-':
        case '*':
            _pos++;
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

    private DFType[] getArgTypes(char start, char end)
        throws TypeNotFound {
	assert _text.charAt(_pos) == start;
        _pos++;
        List<DFType> types = new ArrayList<DFType>();
        while (_text.charAt(_pos) != end) {
            types.add(this.getType());
        }
        _pos++;
        DFType[] argTypes = new DFType[types.size()];
        types.toArray(argTypes);
        return argTypes;
    }

    public DFParamType[] getParamTypes(DFTypeSpace childSpace)
        throws TypeNotFound {
        if (_text.charAt(_pos) != '<') return null;
        _pos++;
        List<DFParamType> params = new ArrayList<DFParamType>();
	_finder = new DFTypeFinder(_finder, childSpace);
        while (_text.charAt(_pos) != '>') {
	    int i = _text.indexOf(':', _pos);
	    String id = _text.substring(_pos, i);
	    _pos = i+1;
            DFParamType pt = new DFParamType(id, childSpace, params.size());
	    childSpace.addParamType(id, pt);
	    if (_text.charAt(_pos) == ':') {
		_pos++;		// ???
	    }
	    pt.build(_finder, this);
	    _finder = pt.addFinders(_finder);
            params.add(pt);
        }
        _pos++;
        DFParamType[] paramTypes = new DFParamType[params.size()];
        params.toArray(paramTypes);
        return paramTypes;
    }
}
