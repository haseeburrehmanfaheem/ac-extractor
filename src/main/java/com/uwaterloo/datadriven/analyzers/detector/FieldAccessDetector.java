package com.uwaterloo.datadriven.analyzers.detector;


import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ipa.cfg.InterproceduralCFG;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ssa.*;
import com.ibm.wala.util.collections.Pair;
import com.uwaterloo.datadriven.dataflow.DataflowAnalyzer;
import com.uwaterloo.datadriven.model.framework.field.*;
import com.uwaterloo.datadriven.utils.CollectionUtils;
import com.uwaterloo.datadriven.utils.DataflowUtils;
import com.uwaterloo.datadriven.utils.FieldUtils;


import java.util.*;

public class FieldAccessDetector {
    private final ClassHierarchy cha;
    private final AnalysisScope scope;

    private final DataflowAnalyzer dfAnalyzer;
    private final CollectionUtils collectionUtils;
    private InterproceduralCFG icfg;
    private HashSet<SSAInstruction> visitedInstr;
    private boolean traceSets = false;
    private boolean traceGets = false;

    public FieldAccessDetector(ClassHierarchy cha, AnalysisScope scope) {
        this.cha = cha;
        this.scope = scope;
        dfAnalyzer = DataflowAnalyzer.getInstance(cha);
        collectionUtils = CollectionUtils.getInstance(cha);
    }
    public FrameworkField isFieldAccess(SSAInstruction ins, IClass insClass,
                                 HashMap<IField, FrameworkField> fieldsToAnalyze) {
        // implement checks for field accesses
        if (ins instanceof SSAFieldAccessInstruction fieldIns) {
            return fieldsToAnalyze.getOrDefault(FieldUtils
                    .getField(insClass, fieldIns.getDeclaredField()), null);
        }
        return null;
    }
    private Pair<FrameworkField, ArrayList<String>> getCompField(SSAFieldAccessInstruction fldIns,
                                                                 FrameworkField pFld,
                                                                 ArrayList<String> pPath) {
        String fldId = fldIns.getDeclaredField().getName().toString();
        if (pFld instanceof ComplexField cFld) {
            if (cFld.members.containsKey(fldId)) {
                ArrayList<String> newPath = new ArrayList<>(pPath);
                newPath.add(fldId);
                return Pair.make(cFld.members.get(fldId), newPath);
            }
        }

        return null;
    }
    private AccessType getPreciseOrModify(AccessType preciseType, int val, CGNode node) {
        AccessType accessType = AccessType.MODIFY;
        if (traceSets && dfAnalyzer.isApiParam(val, node, icfg.getCallGraph()))
            accessType = preciseType;
        return accessType;
    }
    private AccessType getPreciseOrFetch(AccessType preciseType, int val, CGNode node) {
        AccessType accessType = AccessType.FETCH;
        if (traceGets && dfAnalyzer.isReturnedByApi(node, new ArrayList<>(List.of(val)),
                new HashSet<>(), icfg.getCallGraph()))
            accessType = preciseType;
        return accessType;
    }
    private boolean ifNonLoopCondExists(SSAInstruction ins) {
        BasicBlockInContext<ISSABasicBlock> curBlock = null;
        for (BasicBlockInContext<ISSABasicBlock> bb : icfg) {
            for (SSAInstruction in : bb) {
                if (in.equals(ins)) {
                    curBlock = bb;
                    break;
                }
            }
        }
        for (Iterator<BasicBlockInContext<ISSABasicBlock>> it = icfg.getPredNodes(curBlock); it.hasNext(); ) {
            BasicBlockInContext<ISSABasicBlock> predB = it.next();
            CGNode pNode = predB.getNode();
            for (SSAInstruction nextIns : predB) {
                if (nextIns instanceof SSAConditionalBranchInstruction condIns) {
                    if (!DataflowUtils.isLoopConditional(condIns, pNode.getDU(), pNode.getIR().getSymbolTable())) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
    public HashSet<Pair<FrameworkField, FieldAccess>> extractFieldAccess(FrameworkField fwField,
                                                                         SSAFieldAccessInstruction fieldIns,
                                                                         BasicBlockInContext<ISSABasicBlock> block,
                                                                         InterproceduralCFG icfg,
                                                                         HashSet<SSAInstruction> visitedInstr,
                                                                         boolean traceGets, boolean traceSets) {
        ArrayList<String> fieldPath = new ArrayList<>();
        fieldPath.add(fwField.id);
        this.icfg = icfg;
        this.visitedInstr = visitedInstr;
        this.traceSets = traceSets;
        this.traceGets = traceGets;

        HashSet<Pair<FrameworkField, FieldAccess>> allAccesses = new HashSet<>();
        for (Pair<FrameworkField, FieldAccess> access : processFieldAccess(fieldIns,
                fwField, block.getNode(), fieldPath)) {
            if (access.fst instanceof PrimitiveField
                    || access.snd.accessType().isPrecise())
                allAccesses.add(access);
        }
        return allAccesses;
    }
    private HashSet<Pair<FrameworkField, FieldAccess>> processUses(SSAInstruction ins,
                                                                   FrameworkField fwField,
                                                                   CGNode node,
                                                                   ArrayList<String> fieldPath) {
        if(ifNonLoopCondExists(ins)) {
            for (int i=0; i<fieldPath.size(); i++) {
                if (fieldPath.get(i).equals(CollectionField.ALL_MEMBERS))
                    fieldPath.set(i, CollectionField.SOME_MEMBERS);
            }
        }
        HashSet<Pair<FrameworkField, FieldAccess>> accesses = new HashSet<>();
        int valForUse = ins.getDef();
        if (ins instanceof SSACheckCastInstruction castIns)
            valForUse = castIns.getResult();
        try {
            for (Iterator<SSAInstruction> it = node.getDU().getUses(valForUse); it.hasNext(); ) {
                try {
                    SSAInstruction use = it.next();
                    if (!visitedInstr.contains(use)) {
                        visitedInstr.add(use);
                        if (use instanceof SSAReturnInstruction
                                || use instanceof SSAConditionalBranchInstruction) {
                            continue;
                        } else if (use instanceof SSAFieldAccessInstruction fieldIns) {
                            Pair<FrameworkField, ArrayList<String>> cur = getCompField(fieldIns, fwField, fieldPath);
                            if (cur != null) {
                                accesses.addAll(processFieldAccess(fieldIns, cur.fst, node, cur.snd));
                            }
                        } else if (use instanceof SSAAbstractInvokeInstruction abIns) {
                            accesses.addAll(processInvoke(abIns, fwField, node, fieldPath));
                        } else {
                            accesses.addAll(processUses(use, fwField, node, fieldPath));
                        }
                    }
                } catch (Exception e) {
                    //ignore
                }
            }
        } catch (Exception e) {
            //ignore
        }
        return accesses;
    }
    private HashSet<Pair<FrameworkField, FieldAccess>> processFieldAccess(SSAFieldAccessInstruction fieldIns,
                                                                 FrameworkField fwField,
                                                                 CGNode node,
                                                                 ArrayList<String> fieldPath) {
        AccessType accessType;
        if (fieldIns instanceof SSAPutInstruction putIns) {
            accessType = getPreciseOrModify(AccessType.SET, putIns.getVal(), node);
        } else if (fieldIns instanceof SSAGetInstruction getIns){
            accessType = getPreciseOrFetch(AccessType.GET, getIns.getDef(), node);
        } else {
            throw new UnsupportedOperationException("Weird Field Access");
        }
        HashSet<Pair<FrameworkField, FieldAccess>> allAccesses = new HashSet<>();
        allAccesses.add(Pair.make(fwField, new FieldAccess(fieldPath, accessType)));
        allAccesses.addAll(processUses(fieldIns, fwField, node, fieldPath));
        return allAccesses;
    }
    private HashSet<Pair<FrameworkField, FieldAccess>> processInvoke(SSAAbstractInvokeInstruction invIns,
                                                                     FrameworkField fwField,
                                                                     CGNode node,
                                                                     ArrayList<String> fieldPath) {
        HashSet<Pair<FrameworkField, FieldAccess>> retVal = new HashSet<>();
        if (fwField instanceof CollectionField collFld) {
            if (collectionUtils.isCollectionKeysAccess(invIns)) {
                fieldPath.add(CollectionField.INDEX_MEMBER);
                fieldPath.add(CollectionField.ALL_MEMBERS);
                retVal.add(Pair.make(collFld.members.get(CollectionField.ALL_MEMBERS).indexDummy(),
                        new FieldAccess(fieldPath,
                                getPreciseOrFetch(AccessType.GET,
                                        invIns.getReturnValue(0), node))));
                retVal.addAll(processUses(invIns, collFld.members.get(CollectionField.ALL_MEMBERS)
                        .indexDummy(), node, fieldPath));
            } else if (collectionUtils.isCollectionValsAccess(invIns)) {
                fieldPath.add(CollectionField.VALUE_MEMBER);
                fieldPath.add(CollectionField.ALL_MEMBERS);
                retVal.add(Pair.make(collFld.members.get(CollectionField.ALL_MEMBERS).valueDummy(),
                        new FieldAccess(fieldPath,
                                getPreciseOrFetch(AccessType.GET, invIns.getReturnValue(0), node))
                ));
                retVal.addAll(processUses(invIns, collFld.members.get(CollectionField.ALL_MEMBERS)
                        .valueDummy(), node, fieldPath));
            } else if (collectionUtils.isCollectionGetter(invIns)) {
                fieldPath.add(CollectionField.VALUE_MEMBER);
                fieldPath.add(CollectionField.ALL_MEMBERS);
                retVal.add(Pair.make(collFld.members.get(CollectionField.ALL_MEMBERS).valueDummy(),
                        new FieldAccess(fieldPath,
                                getPreciseOrFetch(AccessType.GET, invIns.getReturnValue(0), node))
                ));
                retVal.addAll(processUses(invIns, collFld.members.get(CollectionField.ALL_MEMBERS)
                        .valueDummy(), node, fieldPath));
            } else if (collectionUtils.isCollectionAdder(invIns)) {
                fieldPath.add(CollectionField.VALUE_MEMBER);
                fieldPath.add(CollectionField.ALL_MEMBERS);
                retVal.add(Pair.make(collFld.members.get(CollectionField.ALL_MEMBERS).valueDummy(),
                        new FieldAccess(fieldPath,
                        getPreciseOrModify(AccessType.ADD,
                                invIns.getUse(collectionUtils.getValNum(invIns)), node))
                ));
                retVal.addAll(processUses(invIns, collFld.members.get(CollectionField.ALL_MEMBERS)
                        .valueDummy(), node, fieldPath));
            } else if (collectionUtils.isCollectionRemover(invIns)) {
                fieldPath.add(CollectionField.VALUE_MEMBER);
                fieldPath.add(CollectionField.ALL_MEMBERS);
                retVal.add(Pair.make(collFld.members.get(CollectionField.ALL_MEMBERS).valueDummy(),
                        new FieldAccess(fieldPath,
                        getPreciseOrModify(AccessType.REMOVE,
                                invIns.getUse(collectionUtils.getValNum(invIns)), node))
                ));
                retVal.addAll(processUses(invIns, collFld.members.get(CollectionField.ALL_MEMBERS)
                        .valueDummy(), node, fieldPath));
            } else if (collectionUtils.isCollectionKeyExists(invIns)) {
                fieldPath.add(CollectionField.INDEX_MEMBER);
                fieldPath.add(CollectionField.ALL_MEMBERS);
                retVal.add(Pair.make(collFld.members.get(CollectionField.ALL_MEMBERS).indexDummy(),
                        new FieldAccess(fieldPath,
                        getPreciseOrFetch(AccessType.INDEX_EXISTS,
                                invIns.getReturnValue(0), node))));
                retVal.addAll(processUses(invIns, collFld.members.get(CollectionField.ALL_MEMBERS)
                        .indexDummy(), node, fieldPath));
            } else if (collectionUtils.isCollectionValExists(invIns)) {
                fieldPath.add(CollectionField.VALUE_MEMBER);
                fieldPath.add(CollectionField.ALL_MEMBERS);
                retVal.add(Pair.make(collFld.members.get(CollectionField.ALL_MEMBERS).valueDummy(),
                        new FieldAccess(fieldPath,
                        getPreciseOrFetch(AccessType.VALUE_EXISTS,
                                invIns.getReturnValue(0), node))));
                retVal.addAll(processUses(invIns, collFld.members.get(CollectionField.ALL_MEMBERS)
                        .valueDummy(), node, fieldPath));
            }
        } else if (collectionUtils.isCollectionGetter(invIns)) {
            retVal.addAll(processUses(invIns, fwField, node, fieldPath));
        }
        if (collectionUtils.isCollectionIterate(invIns))
            retVal.addAll(processUses(invIns, fwField, node, fieldPath));

        return retVal;
    }
}
