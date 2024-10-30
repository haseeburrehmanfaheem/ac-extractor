package com.uwaterloo.datadriven.analyzers;

import com.ibm.wala.classLoader.IField;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ipa.cfg.InterproceduralCFG;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.debug.UnimplementedError;
import com.uwaterloo.datadriven.analyzers.detector.AccessControlDetector;
import com.uwaterloo.datadriven.analyzers.detector.FieldAccessDetector;
import com.uwaterloo.datadriven.model.accesscontrol.AccessControl;
import com.uwaterloo.datadriven.model.accesscontrol.misc.AccessControlSource;
import com.uwaterloo.datadriven.model.accesscontrol.misc.ProtectionLevel;
import com.uwaterloo.datadriven.model.framework.FrameworkEp;
import com.uwaterloo.datadriven.model.framework.field.FieldAccess;
import com.uwaterloo.datadriven.model.framework.field.FrameworkField;
import com.uwaterloo.datadriven.model.functional.CleanupFunction;
import com.uwaterloo.datadriven.utils.AccessControlUtils;
import com.uwaterloo.datadriven.utils.CachedCallGraphs;
import com.uwaterloo.datadriven.utils.CgUtils;
import com.uwaterloo.datadriven.utils.ChaUtils;

import java.util.*;

public class EntrypointAnalyzer {
    private final ClassHierarchy cha;
    private final AccessControlDetector acDetector;
    private final FieldAccessDetector fieldDetector;

    private final HashSet<Pair<FrameworkField, FieldAccess>> fields = new HashSet<>();
    private HashMap<IField, FrameworkField> fieldsToAnalyze;
    private AccessControl curAc = null;
    private final HashSet<BasicBlockInContext<ISSABasicBlock>> visited = new HashSet<>();
    private final HashSet<SSAAbstractInvokeInstruction> visitedWeirdInv = new HashSet<>();
    private final HashSet<SSAInstruction> visitedInstr = new HashSet<>();
    private final Stack<Pair<InterproceduralCFG, BasicBlockInContext<ISSABasicBlock>>> curBlockStack = new Stack<>();
    private boolean traceGetsOrExists;
    private boolean traceSetsOrAddRemoves;
    private final List<CleanupFunction> cleanupFunctions = List.of(
            fields::clear,
            () -> fieldsToAnalyze = null,
            () -> curAc = null,
            visited::clear,
            visitedInstr::clear,
            visitedWeirdInv::clear,
            curBlockStack::clear
    );

    public EntrypointAnalyzer(ClassHierarchy cha, HashMap<String, ProtectionLevel> curProtection) {// Pass to extract AC method
        this.cha = cha;
        acDetector = new AccessControlDetector(cha, cha.getScope(), curProtection);
        fieldDetector = new FieldAccessDetector(cha, cha.getScope());
    }

    public Pair<AccessControlSource, HashSet<Pair<FrameworkField, FieldAccess>>> analyze(FrameworkEp frameworkEp) {
        CallGraph cg = CgUtils.buildSingleEpCg(cha, frameworkEp.epMethod);
        if (cg == null)
            return null;
        InterproceduralCFG icfg = new InterproceduralCFG(cg);
        int paramNum = frameworkEp.epMethod.getNumberOfParameters();
        if (!frameworkEp.epMethod.getMethod().isStatic())
            paramNum--;
        traceSetsOrAddRemoves = paramNum > 0;
        traceGetsOrExists = !frameworkEp.epMethod.getMethod().getReturnType().equals(TypeReference.Void);
        startDfs(icfg);
        Pair<AccessControlSource, HashSet<Pair<FrameworkField, FieldAccess>>> retVal = Pair.make(
                new AccessControlSource(frameworkEp.epMethod.getMethod().getSignature(), curAc),
                new HashSet<>(fields)
        );
        cleanAll();
        return retVal;
    }

    private void startDfs(InterproceduralCFG icfg) {
        if (!icfg.getCallGraph().getEntrypointNodes().isEmpty()) {
            curBlockStack.push(Pair.make(icfg,
                    icfg.getEntry((CGNode) icfg.getCallGraph().getEntrypointNodes().toArray()[0])));
            while (!curBlockStack.isEmpty()) {
                Pair<InterproceduralCFG, BasicBlockInContext<ISSABasicBlock>> curPair = curBlockStack.pop();
                if (!visited.contains(curPair.snd)) {
                    visited.add(curPair.snd);
                    processBlock(curPair);
                }
            }
        }
    }

    private void processBlock(Pair<InterproceduralCFG, BasicBlockInContext<ISSABasicBlock>> blockPair) {
        //process the current block
        InterproceduralCFG icfg = blockPair.fst;
        BasicBlockInContext<ISSABasicBlock> block = blockPair.snd;
        if (block == null)
            return;
        for (SSAInstruction ins : block) {
            FrameworkField fwField;
            if (acDetector.isAcMethod(ins)) {
                curAc = AccessControlUtils.mergeConjunctiveAc(curAc, acDetector.extractAc(ins, block, icfg));
            }
//            else if (!visitedInstr.contains(ins)
//                    && (fwField = fieldDetector.isFieldAccess(ins,
//                    block.getNode().getMethod().getDeclaringClass(),
//                    fieldsToAnalyze)) != null) {
//                visitedInstr.add(ins);
//                HashSet<Pair<FrameworkField, FieldAccess>> fieldAccesses = fieldDetector.extractFieldAccess(fwField,
//                        (SSAFieldAccessInstruction) ins, block, icfg, visitedInstr,
//                        traceGetsOrExists, traceSetsOrAddRemoves);
//                if (fieldAccesses != null) {
//                    fields.addAll(fieldAccesses);
//                }
//            }
        else if (ins instanceof SSAAbstractInvokeInstruction abIns
                    && ChaUtils.shouldAnalyze(abIns.getDeclaredTarget().getDeclaringClass(), cha)
                    && visitedWeirdInv.add(abIns)){
                try {
                    CGNode nextNode = null;
                    try {
                        nextNode = icfg.getCallGraph().getNode(cha.resolveMethod(
                                abIns.getDeclaredTarget()), block.getNode().getContext());
                    } catch (Exception | UnimplementedError e) {
                        //ignore
                    }
                    if (nextNode == null) {
                        try {
                            CallGraph cg = CachedCallGraphs.buildSingleEpCg(abIns.getDeclaredTarget(), cha);
                            if (cg != null) {
                                InterproceduralCFG nextIcfg = new InterproceduralCFG(cg);
                                BasicBlockInContext<ISSABasicBlock> nextB = nextIcfg
                                        .getEntry((CGNode) nextIcfg.getCallGraph()
                                                .getEntrypointNodes().toArray()[0]);
                                curBlockStack.push(Pair.make(nextIcfg, nextB));
                            }
                        } catch (Exception e) {
                            //ignore
                        }
                    }
                } catch (Exception | UnimplementedError e) {
                    //ignore
                }
            }
        }

        //add next blocks in dfs stack
        for (Iterator<BasicBlockInContext<ISSABasicBlock>> it = icfg.getSuccNodes(block); it.hasNext(); ) {
            BasicBlockInContext<ISSABasicBlock> nextBlock = it.next();
            if (nextBlock != null && cha.getScope().isApplicationLoader(nextBlock.getMethod().getDeclaringClass().getClassLoader()) && !visited.contains(nextBlock)) {
                curBlockStack.push(Pair.make(icfg, nextBlock));
            }
        }

    }
    private void cleanAll() {
        for (CleanupFunction cleanupFunction : cleanupFunctions)
            cleanupFunction.cleanup();
    }
}
