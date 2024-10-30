package com.uwaterloo.datadriven.utils;

import com.ibm.wala.classLoader.Language;
import com.ibm.wala.dalvik.classLoader.DexIRFactory;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.cha.ClassHierarchy;

import java.util.Collection;
import java.util.List;

public class CgUtils {
    public static CallGraph buildSingleEpCg(ClassHierarchy cha, DefaultEntrypoint ep) {
        return buildCg(cha, List.of(ep));
    }
    public static CallGraph buildCg(ClassHierarchy cha, Collection<DefaultEntrypoint> eps) {
        AnalysisCacheImpl cache = new AnalysisCacheImpl(new DexIRFactory());
        AnalysisOptions options = new AnalysisOptions();
        options.setAnalysisScope(cha.getScope());
        options.setEntrypoints(eps);
        options.setReflectionOptions(AnalysisOptions.ReflectionOptions.NONE);
        SSAPropagationCallGraphBuilder cgBuilder = Util.makeVanillaZeroOneCFABuilder(Language.JAVA, options, cache, cha);
        CallGraph cg = null;
        try {
            cg = cgBuilder.makeCallGraph(options, null);
        } catch (Exception e) {
            //ignore
        }

        return cg;
    }
}
