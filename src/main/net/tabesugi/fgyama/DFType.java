//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFType
//
public abstract class DFType {

    public abstract String getTypeName();
    public abstract boolean equals(DFType type);
    public abstract int canConvertFrom(DFType type, Map<DFMapType, DFType> typeMap);

    public DFKlass getKlass() {
        return null;
    }

    public static DFType inferPrefixType(
        DFType type, PrefixExpression.Operator op) {
        if (op == PrefixExpression.Operator.NOT) {
            return DFBasicType.BOOLEAN;
        } else {
            return type;
        }
    }

    public static DFType inferInfixType(
        DFType left, InfixExpression.Operator op, DFType right) {
        if (op == InfixExpression.Operator.EQUALS ||
            op == InfixExpression.Operator.NOT_EQUALS ||
            op == InfixExpression.Operator.LESS ||
            op == InfixExpression.Operator.GREATER ||
            op == InfixExpression.Operator.LESS_EQUALS ||
            op == InfixExpression.Operator.GREATER_EQUALS ||
            op == InfixExpression.Operator.CONDITIONAL_AND ||
            op == InfixExpression.Operator.CONDITIONAL_OR) {
            return DFBasicType.BOOLEAN;
        } else if (op == InfixExpression.Operator.PLUS &&
                   (left == DFBuiltinTypes.getStringKlass() ||
                    right == DFBuiltinTypes.getStringKlass())) {
            return DFBuiltinTypes.getStringKlass();
        } else if (left instanceof DFUnknownType ||
                   right instanceof DFUnknownType) {
            return (left instanceof DFUnknownType)? right : left;
        } else if (0 <= left.canConvertFrom(right, null)) {
            return left;
        } else {
            return right;
        }
    }
}
