package com.uwaterloo.datadriven.model.framework;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.uwaterloo.datadriven.model.accesscontrol.misc.ProtectionLevel;
import com.uwaterloo.datadriven.utils.CachedCallGraphs;
import com.uwaterloo.datadriven.utils.ChaUtils;

import java.util.*;

public abstract class FrameworkParent {
    public IClass fwParentClass;
    public List<FrameworkEp> eps;

    protected FrameworkParent(IClass fwParentClass, List<FrameworkEp> eps) {
        this.fwParentClass = fwParentClass;
        this.eps = eps;
    }
    protected FrameworkParent(IClass fwParentClass) {
        this.fwParentClass = fwParentClass;
    }
    abstract String getParentType();
    public ArrayList<String[]> toCsvStrings(HashMap<String, ProtectionLevel> protectionMap) {
        ArrayList<String[]> csvStrings = new ArrayList<>();
        for (FrameworkEp api : eps) {
            ArrayList<String> csvStrs = api.toCsvStrings();
            csvStrs.add(0, getParentType());
            csvStrs.add(protectionMap.getOrDefault(api.epMethod.getMethod().getSignature(), ProtectionLevel.NONE).toString());
            csvStrings.add(csvStrs.toArray(new String[0]));
        }

        return csvStrings;
    }
    public HashSet<IClass> getReachableClasses(ClassHierarchy cha) {
        HashMap<String, IClass> reachableClasses = new HashMap<>();
        reachableClasses.put(fwParentClass.getName().toString(), fwParentClass);
        for (FrameworkEp ep : eps) {
            CallGraph apiCg = CachedCallGraphs.buildSingleEpCg(ep.epMethod, cha);
            if (apiCg != null) {
                for (CGNode node : apiCg) {
                    IClass parentClass = node.getMethod().getDeclaringClass();
                    if (ChaUtils.shouldAnalyze(parentClass, cha.getScope())) {
                        if (!reachableClasses.containsKey(parentClass.getName().toString()))
                            reachableClasses.put(parentClass.getName().toString(), parentClass);
                    }
                    try {
                        for (SSAInstruction ins : node.getIR().getInstructions()) {
                            if (ins instanceof SSAAbstractInvokeInstruction abIns) {
                                try {
                                    IClass invClass = cha.lookupClass(abIns.getDeclaredTarget()
                                            .getDeclaringClass());
                                    if (ChaUtils.shouldAnalyze(invClass, cha.getScope())) {
                                        if (!reachableClasses.containsKey(invClass.getName().toString()))
                                            reachableClasses.put(invClass.getName().toString(), invClass);
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
        }
        return new HashSet<>(reachableClasses.values());
    }
}
