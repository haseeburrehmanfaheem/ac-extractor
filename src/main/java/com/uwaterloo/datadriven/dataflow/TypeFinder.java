package com.uwaterloo.datadriven.dataflow;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;

public class TypeFinder {
    public static IClass findConcreteType(CallGraph cg, CGNode node, int valNum) {
        DefUse du = node.getDU();
        SymbolTable st = node.getIR().getSymbolTable();
        TypeReference concreteType = null;
        if (!st.isParameter(valNum)) {
            if (st.isConstant(valNum)) {
                concreteType = getConstantType(st, valNum, cg.getClassHierarchy());
            }
            SSAInstruction defIns = du.getDef(valNum);
            if (defIns instanceof SSAAbstractInvokeInstruction)
                concreteType = findInvokeConcreteType((SSAAbstractInvokeInstruction) defIns); // 1 case here still unresolved(VR manager service)
            else if (defIns instanceof SSACheckCastInstruction)
                concreteType = ((SSACheckCastInstruction) defIns).getDeclaredResultTypes()[0];
            else if (defIns instanceof SSAGetInstruction)
                concreteType = ((SSAGetInstruction) defIns).getDeclaredFieldType();
            else if (defIns instanceof SSANewInstruction)
                concreteType = ((SSANewInstruction) defIns).getConcreteType();
        }
        else {
            concreteType = node.getMethod().getDeclaringClass().getReference();
        }
        return getClassFromType(cg.getClassHierarchy(), concreteType);
    }

    private static TypeReference getConstantType(SymbolTable st, int valNum, IClassHierarchy cha) {
        if (st.isBooleanConstant(valNum))
            return TypeReference.Boolean;
        if (st.isDoubleConstant(valNum))
            return TypeReference.Double;
        if (st.isFloatConstant(valNum))
            return TypeReference.Float;
        if (st.isIntegerConstant(valNum))
            return TypeReference.Int;
        if (st.isLongConstant(valNum))
            return TypeReference.Long;
        if (st.isStringConstant(valNum))
            return TypeReference.JavaLangString;
        if (st.isNullConstant(valNum))
            return TypeReference.Null;
        return null;
    }
    
    private static IClass getClassFromType(IClassHierarchy cha, TypeReference typeReference) {
        if (cha == null || typeReference == null)
            return null;
        return cha.lookupClass(typeReference);
    }

    private static TypeReference findInvokeConcreteType(SSAAbstractInvokeInstruction invIns) {
        MethodReference invMethod = invIns.getDeclaredTarget();
        if (invMethod.getReturnType().toString().contains("Landroid/os/IBinder")
                && invMethod.getName().toString().contains("asBinder"))
            return invMethod.getDeclaringClass();

        return invIns.getDeclaredResultType();
    }
}
