//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import javax.xml.stream.*;
import org.apache.bcel.*;
import org.apache.bcel.classfile.*;


//  DFJarFileMethod
//
public class DFJarFileMethod extends DFMethod {

    DFTypeFinder _finder;
    Method _meth;
    DFFunctionType _funcType;

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

    // Constructor for a parameterized method.
    private DFJarFileMethod(
        DFJarFileMethod genericMethod, DFKlass[] paramTypes)
        throws InvalidSyntax {
        super(genericMethod, paramTypes);

        _finder = new DFTypeFinder(this, genericMethod._finder);
        _meth = genericMethod._meth;
        this.build();
    }

    protected DFMethod parameterize(DFKlass[] paramTypes)
        throws InvalidSyntax {
        assert paramTypes != null;
        return new DFJarFileMethod(this, paramTypes);
    }

    public DFFunctionType getFuncType() {
        return _funcType;
    }

    @SuppressWarnings("unchecked")
    private void build()
        throws InvalidSyntax {
        assert _finder != null;
        assert _meth != null;

        String sig = Utils.getJKlassSignature(_meth.getAttributes());
        if (sig != null) {
            //Logger.info("meth:", _meth.getName(), sig);
            if (this.getGenericMethod() == null) {
                DFMapType[] mapTypes = JNITypeParser.createMapTypes(sig, this);
                if (mapTypes != null) {
                    for (DFMapType mapType : mapTypes) {
                        mapType.setBaseFinder(_finder);
                    }
                    this.setMapTypes(mapTypes);
                }
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
