package com.uwaterloo.datadriven.utils;

import com.ibm.wala.ssa.*;

public class DataflowUtils {
    public static boolean isHasNext(SSAAbstractInvokeInstruction inv) {
        return inv.getDeclaredTarget().getName().toString().equals("hasNext")
                && inv.getDeclaredTarget().getDeclaringClass().getName().toString().equals("Ljava/util/Iterator");
    }
    public static boolean isLoopConditional(SSAConditionalBranchInstruction cIns,
                                            DefUse du, SymbolTable st) {
        int pred1 = cIns.getUse(0);
        int pred2 = cIns.getUse(1);

        if ((st.isConstant(pred1) && st.isConstant(pred2))
                || (!st.isConstant(pred1) && !st.isConstant(pred2)))
            return false;

        int otherVal = -1;
        if (st.isConstant(pred1)) {
            otherVal = pred2;
        } else if (st.isConstant(pred2)) {
            otherVal = pred1;
        }
        SSAInstruction otherDef = du.getDef(otherVal);
        if (otherDef instanceof SSAAbstractInvokeInstruction inv)
            return isHasNext(inv);
        else
            return false;
    }
}
