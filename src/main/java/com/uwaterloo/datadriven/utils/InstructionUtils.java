package com.uwaterloo.datadriven.utils;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.dalvik.dex.instructions.Invoke;
import com.ibm.wala.shrike.shrikeBT.IInvokeInstruction;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;

public class InstructionUtils {
    public static int getRealUse(SSAAbstractInvokeInstruction ins, int valNum) {
        return ins.getUse(getParameterIndex(ins, valNum));
    }

    public static int getParameterIndex(SSAAbstractInvokeInstruction abIns, int paramNum) {
        int paramId = paramNum;
        if (!abIns.isStatic())
            paramId++;

        return paramId;
    }

    public static int findParameterIndex(IMethod m, int valueNumber, SymbolTable st) {
        int newValueNumber = valueNumber;
        if(!m.isStatic() && st.isParameter(valueNumber)) {
            newValueNumber -= 1;
        }
        return newValueNumber;
    }

    public static int findParameterUseNumber(SSAAbstractInvokeInstruction i, int normalizedValueNumber) {
        int parameterUseNumber = normalizedValueNumber;
        if(i.isStatic()) {
            parameterUseNumber -= 1;
        }
        return parameterUseNumber;
    }

    //// starts with Ljava/lang , ignore the case
    public static boolean isJavaLangMethod(SSAAbstractInvokeInstruction ins) {
        try {
            MethodReference mIns = ins.getDeclaredTarget();
            TypeReference curCls = mIns.getDeclaringClass();
            return curCls.getName().toString().startsWith("Ljava/lang");
        } catch (Exception e) {
            //ignore
        }
        return false;
    }
    public static boolean isJavaOrAndroidUtilMethod(Invoke inv) {
        try {
            return inv.clazzName.startsWith("Ljava/util")
                    || inv.clazzName.startsWith("Ljava/lang")
                    || inv.clazzName.startsWith("Ljava/io")
                    || inv.clazzName.startsWith("Landroid/util")
                    || inv.clazzName.startsWith("Landroid/os");
        } catch (Exception e) {
            //ignore
        }
        return false;
    }
    public static boolean isXmlMethod(Invoke inv) {
        try {
            return inv.clazzName.startsWith("Lorg/xml");
        } catch (Exception e) {
            //ignore
        }
        return false;
    }
    public static boolean isAndroidUtilMethod(SSAAbstractInvokeInstruction ins) {
        try {
            MethodReference mIns = ins.getDeclaredTarget();
            TypeReference curCls = mIns.getDeclaringClass();
            return curCls.getName().toString().startsWith("Landroid/util");
        } catch (Exception e) {
            //ignore
        }
        return false;
    }
    public static boolean isStatic(Invoke inv) {
        try {
            return inv.getInvocationCode() == IInvokeInstruction.Dispatch.STATIC;
        } catch (Exception e) {
            //ignore
        }
        return false;
    }
    public static boolean isEqualsMethod(SSAAbstractInvokeInstruction ins) {
        try {
            MethodReference mIns = ins.getDeclaredTarget();
            return mIns.getName().toString().equals("equals");
        } catch (Exception e) {
            //ignore
        }

        return false;
    }

    public static boolean isStringBuilderMethod(SSAAbstractInvokeInstruction ins, String methodName) {
        try {
            MethodReference mIns = ins.getDeclaredTarget();
            TypeReference curCls = mIns.getDeclaringClass();
            return curCls.getName().toString().startsWith("Ljava/")
                    && curCls.getName().toString().endsWith("StringBuilder")
                    && mIns.getName().toString().equals(methodName);
        } catch (Exception e) {
            //ignore
        }
        return false;
    }

    public static boolean isStringBuilderMethod(SSAAbstractInvokeInstruction ins) {
        try {
            MethodReference mIns = ins.getDeclaredTarget();
            TypeReference curCls = mIns.getDeclaringClass();
            return curCls.getName().toString().startsWith("Ljava/")
                    && curCls.getName().toString().endsWith("StringBuilder");
        } catch (Exception e) {
            //ignore
        }
        return false;
    }

    public static boolean isStringFormatMethod(SSAAbstractInvokeInstruction ins) {
        try {
            MethodReference mIns = ins.getDeclaredTarget();
            TypeReference curCls = mIns.getDeclaringClass();
            return ins.isStatic()
                    && curCls.getName().toString().startsWith("Ljava/")
                    && curCls.getName().toString().endsWith("String")
                    && mIns.getName().toString().equals("format");
        } catch (Exception e) {
            //ignore
        }

        return false;
    }

    public static boolean isInit(Invoke inv) {
        return inv.methodName.equals("<init>")
                || inv.methodName.equals("<clinit>");
    }
}
