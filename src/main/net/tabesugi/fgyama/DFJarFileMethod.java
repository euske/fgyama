//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import javax.xml.stream.*;
import org.apache.bcel.*;
import org.apache.bcel.classfile.*;


//  DFJarFileMethod
//  DFMethod defined in .jar file.
//
//  Usage:
//    1. new DFJarFileMethod(meth, finder)
//    2. getXXX(), ...
//
public class DFJarFileMethod extends DFMethod {

    DFTypeFinder _finder;
    Method _meth;
    DFFunctionType _funcType;

    // Normal constructor.
    public DFJarFileMethod(
        DFKlass klass, CallStyle callStyle, boolean isAbstract,
        String methodId, String methodName,
        Method meth, DFTypeFinder finder)
        throws InvalidSyntax {
        super(klass, callStyle, isAbstract, methodId, methodName);

        _finder = new DFTypeFinder(this, finder);
        _meth = meth;
        this.build();
    }

    // Protected constructor for a parameterized method.
    private DFJarFileMethod(
        DFJarFileMethod genericMethod, Map<String, DFKlass> paramTypes)
        throws InvalidSyntax {
        super(genericMethod, paramTypes);

        _finder = new DFTypeFinder(this, genericMethod._finder);
        _meth = genericMethod._meth;
        this.build();
    }

    public DFFunctionType getFuncType() {
        return _funcType;
    }

    // Parameterize the klass.
    @Override
    protected DFMethod parameterize(Map<String, DFKlass> paramTypes)
        throws InvalidSyntax {
        assert paramTypes != null;
        return new DFJarFileMethod(this, paramTypes);
    }

    // Builds the internal structure.
    @SuppressWarnings("unchecked")
    private void build()
        throws InvalidSyntax {
        assert _finder != null;
        assert _meth != null;

        String sig = Utils.getJKlassSignature(_meth.getAttributes());
        if (sig != null) {
            //Logger.info("meth:", _meth.getName(), sig);
            if (this.getGenericMethod() == null) {
                DFMapType[] mapTypes = JNITypeParser.createMapTypes(
                    this, _finder, sig);
                this.setMapTypes(mapTypes);
            }
            JNITypeParser parser = new JNITypeParser(sig);
            try {
                _funcType = (DFFunctionType)parser.resolveType(_finder);
            } catch (TypeNotFound e) {
                Logger.error(
                    "DFJarFileMethod.build: TypeNotFound (method)",
                    this, e.name, sig);
                return;
            }
        } else {
            org.apache.bcel.generic.Type[] args = _meth.getArgumentTypes();
            DFType[] argTypes = new DFType[args.length];
            for (int i = 0; i < args.length; i++) {
                argTypes[i] = _finder.resolveSafe(args[i]);
            }
            DFType returnType = _finder.resolveSafe(_meth.getReturnType());
            _funcType = new DFFunctionType(argTypes, returnType);
        }
        // For varargs methods, the last argument is declared as an array
        // so no special treatment is required here.
        _funcType.setVarArgs(_meth.isVarArgs());
        ExceptionTable excTable = _meth.getExceptionTable();
        if (excTable != null) {
            String[] excNames = excTable.getExceptionNames();
            DFKlass[] exceptions = new DFKlass[excNames.length];
            for (int i = 0; i < excNames.length; i++) {
                DFType type;
                try {
                    type = _finder.lookupType(excNames[i]);
                } catch (TypeNotFound e) {
                    Logger.error(
                        "DFJarFileMethod.build: TypeNotFound (exception)",
                        this, e.name);
                    type = DFUnknownType.UNKNOWN;
                }
                exceptions[i] = type.toKlass();
            }
            _funcType.setExceptions(exceptions);
        }
    }

}
