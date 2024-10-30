package com.uwaterloo.datadriven.utils;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.Language;
import com.ibm.wala.dalvik.classLoader.DexIRFactory;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.debug.UnimplementedError;

import java.util.Collection;
import java.util.HashSet;

public class CachedCallGraphs {
    private static final int CG_CACHE_MAX_SIZE = 1000;
    private static final Cache<String, CallGraph> cachedCg
            = CacheBuilder.newBuilder().maximumSize(CG_CACHE_MAX_SIZE).build();

    public static CallGraph buildSingleEpCg(String method, ClassHierarchy cha) {
        IMethod curMethod = getMethodFromSignature(method, cha);
        return buildSingleEpCg(curMethod.getReference(), cha);
    }

    public static CallGraph buildSingleEpCg(MethodReference method, ClassHierarchy cha) {
        try {
            return buildSingleEpCg(new DefaultEntrypoint(method, cha), cha);
        } catch (Exception | UnimplementedError e) {
            //ignore
        }
        return null;
    }

    public static CallGraph buildSingleEpCg(DefaultEntrypoint method, ClassHierarchy cha) {
        CallGraph cg = cachedCg.getIfPresent(method.getMethod().getSignature());
        if (cg == null) {
            try {
                cg = CgUtils.buildSingleEpCg(cha, method);
                cachedCg.put(method.getMethod().getSignature(), cg);
            } catch (Exception | UnimplementedError e) {
                throw new RuntimeException("Error building CG", e);
            }
        }
        return cg;
    }

    public static CallGraph buildClassCg(IClass clazz, ClassHierarchy cha) {
        String className = clazz.getName().toString();
        CallGraph cg = cachedCg.getIfPresent(className);
        if (cg == null) {
            try {
                Collection<DefaultEntrypoint> eps = getEps(clazz, cha);
                cachedCg.put(className, CgUtils.buildCg(cha, eps));
            } catch (Exception | UnimplementedError e) {
                throw new RuntimeException("Error building CG", e);
            }
        }
        return cg;
    }

    public static IMethod getMethodFromSignature(String methodSign, ClassHierarchy cha) {
        IMethod curMethod = null;
        try {
            IClass curClass = cha.lookupClass(TypeReference.find(ClassLoaderReference.Application,
                    ChaUtils.getClassNameFromSignature(methodSign)));
            curMethod = curClass.getMethod(Selector
                    .make(methodSign.substring(methodSign.lastIndexOf('.')+1)));
        } catch (Exception e) {
            //ignore
        }
        return curMethod;
    }

    private static Collection<DefaultEntrypoint> getEps(IClass clazz, ClassHierarchy cha) {
        HashSet<DefaultEntrypoint> eps = new HashSet<>();
        try {
            for (IMethod m : clazz.getDeclaredMethods()) {
                if (m.isPublic())
                    eps.add(new DefaultEntrypoint(m, cha));
            }
        } catch (Exception e) {
            //ignore
        }
        return eps;
    }
}
