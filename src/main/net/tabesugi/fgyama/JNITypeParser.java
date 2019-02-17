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

    public DFType getType(DFTypeFinder finder)
        throws TypeNotFound {
	if (_text.length() <= _pos) return null;
	//Logger.info("  getType:", _text.substring(_pos), "finder="+finder);
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
                    return finder.lookupKlass(name.replace('/','.'));
		} else if (c2 == '<') {
                    String name = _text.substring(_pos, i);
		    _pos = i;
		    DFKlass klass = finder.lookupKlass(name.replace('/','.'));
                    DFType[] paramTypes = this.getTypes(finder, '<', '>');
                    klass = klass.parameterize(paramTypes);
                    char c3 = _text.charAt(_pos);
                    if (c3 == ';') {
                        _pos++;
                        return klass;
                    } else if (c3 == '.') {
                        _pos++;
                        i = _pos;
                        finder = finder.extend(klass);
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
		    return finder.lookupKlass(name);
                }
            }
            break;
        case '[':
            for (int i = _pos; i < _text.length(); i++) {
                if (_text.charAt(i) != '[') {
                    int ndims = i-_pos;
                    _pos = i;
                    DFType elemType = this.getType(finder);
                    return new DFArrayType(elemType, ndims);
                }
            }
            break;
        case '+':
            _pos++;
            return this.getType(finder);
        case '-':
            _pos++;
            this.getType(finder); // XXX Treat lowerbound class as Object.
            return DFBuiltinTypes.getObjectKlass();
        case '*':
            _pos++;
            return DFBuiltinTypes.getObjectKlass();
        case '(':
            DFType[] argTypes = this.getTypes(finder, '(', ')');
            DFType returnType = this.getType(finder);
            return new DFMethodType(argTypes, returnType);
        default:
            break;
        }
        throw new TypeNotFound(_text.substring(_pos));
    }

    private DFType[] getTypes(DFTypeFinder finder, char start, char end)
        throws TypeNotFound {
	assert _text.charAt(_pos) == start;
        _pos++;
        List<DFType> types = new ArrayList<DFType>();
        while (_text.charAt(_pos) != end) {
            types.add(this.getType(finder));
        }
        _pos++;
        DFType[] a = new DFType[types.size()];
        types.toArray(a);
        return a;
    }

    public void buildMapTypes(DFTypeFinder finder, DFMapType[] mapTypes)
        throws TypeNotFound {
        if (_text.charAt(_pos) != '<') return;
        _pos++;
	int n = 0;
        while (_text.charAt(_pos) != '>') {
	    int i = _text.indexOf(':', _pos);
	    String id = _text.substring(_pos, i);
	    _pos = i+1;
	    if (_text.charAt(_pos) == ':') {
		_pos++;	 // ???
	    }
            DFMapType pt = mapTypes[n++];
            pt.setBaseKlass((DFKlass)getType(finder));
        }
        _pos++;
    }

    public void skipMapTypes() {
        if (_text.charAt(_pos) != '<') return;
        _pos++;
	int n = 0;
        while (_text.charAt(_pos) != '>') {
	    int i = _text.indexOf(':', _pos);
	    String id = _text.substring(_pos, i);
	    _pos = i+1;
	    if (_text.charAt(_pos) == ':') {
		_pos++;	 // ???
	    }
	    _pos = skipType(_text, _pos);
        }
        _pos++;
    }

    public static DFMapType[] getMapTypes(String text, DFTypeSpace typeSpace) {
	int pos = 0;
        if (text.charAt(pos) != '<') return null;
        pos++;
        List<DFMapType> params = new ArrayList<DFMapType>();
        while (text.charAt(pos) != '>') {
	    int i = text.indexOf(':', pos);
	    String id = text.substring(pos, i);
	    pos = i+1;
	    if (text.charAt(pos) == ':') {
		pos++;	 // ???
	    }
            DFMapType pt = typeSpace.createMapType(id);
            params.add(pt);
	    pos = skipType(text, pos);
        }
        pos++;
        if (params.size() == 0) return null;
        DFMapType[] mapTypes = new DFMapType[params.size()];
        params.toArray(mapTypes);
        return mapTypes;
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
