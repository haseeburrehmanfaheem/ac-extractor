package com.uwaterloo.datadriven.utils;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.dalvik.classLoader.DexIMethod;
import com.ibm.wala.dalvik.dex.instructions.Instruction;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeReference;

import java.util.HashSet;
import java.util.List;
import java.util.function.Function;

public class ChaUtils {
    public static List<DefaultEntrypoint> generateEps(ClassHierarchy cha,
                                                      Function<IClass, Boolean> classFilter,
                                                      Function<IMethod, Boolean> methodFilter,
                                                      Function<Instruction, Boolean> instructionFilter) {
        HashSet<IMethod> epsMethods= new HashSet<>();
        for (IClass c : cha) {
            if (applyOrNull(classFilter, c)) {
//                if(c.getName().toString().contains("ContextAwareService"))
                    try {
                        for (IMethod m : c.getAllMethods()) {
                            if (m instanceof DexIMethod
                                    && applyOrNull(methodFilter, m)) {
                                try {
                                    for (Instruction i : ((DexIMethod) m).getInstructions()) {
                                        if (applyOrNull(instructionFilter, i)) {
                                            epsMethods.add(m);
                                            break;
                                        }
                                    }
                                } catch (Exception e) {
                                    //ignore
                                }
                            }
                        }
                    } catch (Exception e) {
                        //ignore
                    }
            }
        }

        return epsMethods.stream()
                .map(epM -> new DefaultEntrypoint(epM, cha)).toList();
    }

    private static <T> boolean applyOrNull(Function<T, Boolean> filter, T val) {
        return filter == null || filter.apply(val);
    }

    public static String getClassNameFromSignature(String methodSignature) {
        String fullMethodName = methodSignature.substring(0, methodSignature.indexOf('('));
        return "L"+fullMethodName.substring(0, fullMethodName.lastIndexOf('.')).replace('.','/');
    }

    public static IClass getClassFromSignature(String signature, ClassHierarchy cha) {
        try {
            return cha.lookupClass(TypeReference.find(ClassLoaderReference.Application, signature));
        } catch (Exception e) {
            //ignore
            return null;
        }
    }

    public static IMethod getMethodFromSignature(String signature, IClass iClass) {
        return iClass.getMethod(Selector.make(signature));
    }
    public static boolean isApplicationLoader(ClassHierarchy cha, CGNode node){
        return cha.getScope().isApplicationLoader(node.getMethod().getDeclaringClass().getClassLoader());
    }

    public static boolean doesImplementOrExtend(IClass curClass, IClass interfaceClass, ClassHierarchy cha) {
        if (curClass == null || interfaceClass == null)
            return false;
        if (curClass == interfaceClass)
            return true;
        try {
            for (IClass impl : curClass.getAllImplementedInterfaces()) {
                if (impl.equals(interfaceClass))
                    return true;
            }
            return cha.isSubclassOf(curClass, interfaceClass);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    public static boolean shouldAnalyze(TypeReference clazz, ClassHierarchy cha) {
        try {
            return shouldAnalyze(cha.lookupClass(clazz), cha.getScope());
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean shouldAnalyze(IClass clazz, AnalysisScope scope) {
        if (clazz == null || scope == null)
            return false;
        String fullClassName = clazz.getName().toString();
        return scope.isApplicationLoader(clazz.getClassLoader())
                && !clazz.isInterface()
                && !fullClassName.endsWith("Stub")
                && !fullClassName.contains("Ljava/lang/")
                && !fullClassName.contains("Ljava/util/")
                && !fullClassName.contains("Landroid/util/")
                && !fullClassName.contains("Landroid/os/Bundle")
                && !fullClassName.contains("Landroid/os/Handler")
                && !fullClassName.contains("Landroid/os/Binder")
                && !fullClassName.contains("Landroid/content/Context");
    }
}