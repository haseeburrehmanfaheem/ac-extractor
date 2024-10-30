package com.uwaterloo.datadriven.analyzers.detector;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ipa.cfg.InterproceduralCFG;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.shrike.shrikeBT.IConditionalBranchInstruction;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.MethodReference;
import com.uwaterloo.datadriven.model.accesscontrol.AccessControl;
import com.uwaterloo.datadriven.model.accesscontrol.ProgrammaticAccessControl;
import com.uwaterloo.datadriven.model.accesscontrol.misc.AccessControlParameterMapping;
import com.uwaterloo.datadriven.model.accesscontrol.misc.AccessControlType;
import com.uwaterloo.datadriven.model.accesscontrol.misc.ProtectionLevel;
import com.uwaterloo.datadriven.utils.AccessControlUtils;
import com.uwaterloo.datadriven.utils.CachedCallGraphs;
import com.uwaterloo.datadriven.utils.InstructionUtils;

import java.util.*;

import static com.uwaterloo.datadriven.utils.ChaUtils.isApplicationLoader;

public class AccessControlDetector {
    private static final int DEPTH = 10;
    private final ClassHierarchy cha;
    private final AnalysisScope scope;
    private final HashSet<CGNode> visited = new HashSet<>();
    private final HashMap<String, ProtectionLevel> curProtection;

    public AccessControlDetector(ClassHierarchy cha, AnalysisScope scope, HashMap<String, ProtectionLevel> curProtection) {
        this.cha = cha;
        this.scope = scope;
        this.curProtection = curProtection;
    }

    public boolean isAcMethod(SSAInstruction ins) {
        if (ins instanceof SSAAbstractInvokeInstruction) {
            return AccessControlUtils.isAcMethod(((SSAAbstractInvokeInstruction) ins).getDeclaredTarget().getName().toString());
        }
        return false;
    }


    public AccessControl extractAc(SSAInstruction ins,
                                   BasicBlockInContext<ISSABasicBlock> block,
                                   InterproceduralCFG icfg) {
        // Implement the method to extract AC from a known AC related method
        AccessControl ac = null;
        String targetMethodName = ((SSAAbstractInvokeInstruction) ins).getDeclaredTarget().getName().toString();
        SymbolTable st = block.getNode().getIR().getSymbolTable();
        DefUse du = block.getNode().getDU();
        AccessControlType acType = AccessControlUtils.findTypeByMethodName(targetMethodName);
        switch (acType) {
            case Permission:
            case UserRestriction:
            case CrossUser:
            case AppOps:
            case Signature: {
                int paraNumber = AccessControlParameterMapping.getPermissionValue(targetMethodName);
                if(paraNumber == -1 || paraNumber >= ((SSAAbstractInvokeInstruction) ins).getNumberOfPositionalParameters()){
                    return extractParameters((SSAAbstractInvokeInstruction) ins, acType, st, du, block.getNode(), icfg);
                }
                paraNumber = ((SSAAbstractInvokeInstruction) ins).getInvocationCode().hasImplicitThis() ? paraNumber + 1 : paraNumber;
                return extractSingleParameter((SSAAbstractInvokeInstruction) ins, paraNumber, acType, block.getNode(), icfg);
            }
            case Uid:
            case Pid:
            case Gid:
            case User:
            case AppId:
            case UserId:
            case Tid: {
                if (AccessControlUtils.isGettingMethod(targetMethodName)) { // is getting method
                    CGNode node = icfg.getCGNode(block);
                    ac = AccessControlUtils.mergeConjunctiveAc(ac, analyzeGettingFlow((SSAAbstractInvokeInstruction) ins, acType, node, icfg));
                    ac = AccessControlUtils.mergeConjunctiveAc(ac, trackAndAnalyzeParameter((SSAAbstractInvokeInstruction) ins, acType, st, du, node, icfg));
                    return ac;
                } else if (AccessControlUtils.isCheckingMethod(targetMethodName)) {
                    return extractParameters((SSAAbstractInvokeInstruction) ins, acType, st, du, block.getNode(), icfg);
                }
            }
            case Package:
                if (AccessControlUtils.isGettingMethod(targetMethodName)) { // is getting method
                    CGNode node = icfg.getCGNode(block);
                    ac = AccessControlUtils.mergeConjunctiveAc(ac, analyzeGettingFlow((SSAAbstractInvokeInstruction) ins, acType, node, icfg));
                    ac = AccessControlUtils.mergeConjunctiveAc(ac, trackAndAnalyzeParameter((SSAAbstractInvokeInstruction) ins, acType, st, du, node, icfg));
                    return ac;
                } else if (AccessControlUtils.isCheckingMethod(targetMethodName)) {
                    return extractParameters((SSAAbstractInvokeInstruction) ins, acType, st, du, block.getNode(), icfg);
                }
            default:
                return null;
        }
    }


    public ProgrammaticAccessControl extractSingleParameter(SSAAbstractInvokeInstruction ins, int paraNumber,AccessControlType acType,CGNode node, InterproceduralCFG icfg) {
        Collection<String> values = new ArrayList<>();
        HashSet<Object> definitionResults = new HashSet<>();
        try {
            definitionResults = getDefFromVal(ins.getUse(paraNumber), node, icfg.getCallGraph(), 0);
        } catch (Exception e) {
            //ignore
        }
        visited.clear();
        for (Object o : definitionResults) {
            values.add(o.toString());
        }
        return new ProgrammaticAccessControl(acType, AccessControlUtils.getProtectionLevel(acType, values,curProtection), ProgrammaticAccessControl.Operator.EQ, values);
    }
    public AccessControl trackAndAnalyzeParameter(SSAAbstractInvokeInstruction ins, AccessControlType acType, SymbolTable st, DefUse du, CGNode node, InterproceduralCFG icfg) {
        AccessControl ac = null;
        for (Iterator<SSAInstruction> it = du.getUses(ins.getDef()); it.hasNext(); ) {
            SSAInstruction useInstr = it.next();
            if (useInstr instanceof SSAConditionalBranchInstruction condIns) {
                boolean found = false;
                boolean para = false;
                int paramVal = 0;
                ProgrammaticAccessControl.Operator op = AccessControlUtils.findOperatorByString(condIns.getOperator().toString());
                int predicate1 = condIns.getUse(0);
                int predicate2 = condIns.getUse(1);

                if (st.isParameter(predicate1)) {
                    paramVal = predicate1;
                    para = true;
                } else if (!st.isConstant(predicate1)) {
                    SSAInstruction s = du.getDef(predicate1);
                    if (s != null && s.toString().equals(ins.toString())) {
                        found = true;
                    }
                }
                if (st.isParameter(predicate2)) {
                    paramVal = predicate2;
                    para = true;
                } else if (!st.isConstant(predicate2)) {
                    SSAInstruction s = du.getDef(predicate2);
                    if (s != null && s.toString().equals(ins.toString())) {
                        found = true;
                    }
                }
                if (para && found) {
                    HashSet<Object> definitionResults = getDefFromVal(paramVal, node, icfg.getCallGraph(), 0);
                    visited.clear();
                    for (Object o : definitionResults) {
                        Collection<String> values1 = new ArrayList<>();
                        values1.add(o.toString());
                        ac = AccessControlUtils.mergeConjunctiveAc(ac, new ProgrammaticAccessControl(acType, AccessControlUtils.getProtectionLevel(acType, values1,curProtection), op, values1));
                    }
                    return ac;
                }
            }
        }
        return ac;
    }


    /*
    * This method is used to extract the parameters of a known AC method. If required, it goes back in the function
    * invocations to resolve a paramter
    * @param ins: Abstract Invoke Instruction of the AC method
    * @param acType: Type of the AC method
    * @param st: Symbol Table of the current node
    * @param du: DefUse of the current node
    * @param node: current node
    * @param icfg: Interprocedural CFG
     */
    public ProgrammaticAccessControl extractParameters(SSAAbstractInvokeInstruction ins, AccessControlType acType, SymbolTable st, DefUse du, CGNode node, InterproceduralCFG icfg) {
        int firstparam = ins.getInvocationCode().hasImplicitThis() ? 1 : 0;
        Collection<String> values = new ArrayList<>();
        for (int i = firstparam; i < ins.getNumberOfPositionalParameters(); i++) { // check if just calling getDefFromVal is enough
            if (st.isParameter(ins.getUse(i))) {
                HashSet<Object> definitionResults = getDefFromVal(ins.getUse(i), node, icfg.getCallGraph(), 0);
                visited.clear();
                for (Object o : definitionResults) {
                    values.add(o.toString());
                }
            } else if (st.isConstant(ins.getUse(i))) {
                values.add(st.getConstantValue(ins.getUse(i)) + "");
            } else if (!st.isConstant(ins.getUse(i))) {
                SSAInstruction s = du.getDef(ins.getUse(i));
                if (s != null) {
                    if (isAcMethod(s)) {
                        values.add(((SSAAbstractInvokeInstruction) s).getDeclaredTarget().getName().toString());
                    } else if(s instanceof SSAPhiInstruction) {
                        for (int j=0; j<s.getNumberOfUses(); j++){
                            HashSet<Object> definitionResults = getDefFromVal(s.getUse(j), node, icfg.getCallGraph(), 0);
                            visited.clear();
                            for (Object o : definitionResults) {
                                values.add(o.toString());
                            }
                        }
                    }
                }
            }
        }
        return new ProgrammaticAccessControl(acType, AccessControlUtils.getProtectionLevel(acType, values,curProtection), ProgrammaticAccessControl.Operator.EQ, values);
    }


    /*
     * This method is used to analyze if the value of a gettingACmethod is being passed in other methods as parameter. If required, it traverses through the function
     * extract usage in conditional branch instructions
     * @param ins: Abstract Invoke Instruction of the AC method
     * @param acType: Type of the AC method
     * @param node: current node
     * @param icfg: InterproceduralCFG
     */
    public AccessControl analyzeGettingFlow(SSAAbstractInvokeInstruction ins, AccessControlType acType, CGNode node, InterproceduralCFG icfg) {
        AccessControl ac = null;
        HashSet<Object> usageResults = getUsageFromVal(ins.getDef(), node, icfg.getCallGraph(), 0);
        try {
            for (Object o : usageResults) {
                if (o instanceof AbstractMap.SimpleEntry<?, ?>) {
                    Object key = ((AbstractMap.SimpleEntry<?, ?>) o).getKey();
                    Object value = ((AbstractMap.SimpleEntry<?, ?>) o).getValue();
                    Collection<String> values = new ArrayList<>();
                    values.add(key.toString());
                    ac = AccessControlUtils.mergeConjunctiveAc(ac, new ProgrammaticAccessControl(acType, AccessControlUtils.getProtectionLevel(acType, values ,curProtection), AccessControlUtils.findOperatorByString(value.toString()), values));
                } else {  // should not come here
                    // Note : the HashSet should always have a key/value(of operator and val) pair for both cases
                    // 1- conditional branch has int Constant and origInstr
                    // 2- conditional branch has knownAcMethod and origInstr
                    Collection<String> values = new ArrayList<>();
                    values.add(o.toString());
                    ac = AccessControlUtils.mergeConjunctiveAc(ac, new ProgrammaticAccessControl(acType, AccessControlUtils.getProtectionLevel(acType, values,curProtection), ProgrammaticAccessControl.Operator.EQ, values));
                }
            }
        } catch (Exception e) {
            System.out.println("Error in analyzeGettingFlow");
        }
        return ac;
    }


    public HashSet<Object> getDefFromVal(int valnum, CGNode node, CallGraph callGraph, int depth) {
        HashSet<Object> resultSet = new HashSet<>();
        if (depth >= DEPTH) {
            return resultSet;
        }
        Iterator<CGNode> predNodes = callGraph.getPredNodes(node);
        SymbolTable st = node.getIR().getSymbolTable();
        DefUse du = node.getDU();
        SSAInstruction ins = du.getDef(valnum);
        if (ins != null) {
            if (isAcMethod(ins)) {
                resultSet.add(((SSAAbstractInvokeInstruction) ins).getDeclaredTarget().getName());
            }
            else if(ins instanceof SSAPhiInstruction){
                for(int i = 0; i < ins.getNumberOfUses(); i++){
                    HashSet<Object> result = getDefFromVal(ins.getUse(i), node, callGraph, depth + 1);
                    resultSet.addAll(result);
                }
            }
            return resultSet;
        } else if (st.isConstant(valnum)) {
            resultSet.add(st.getConstantValue(valnum));
            return resultSet;
        } else if (st.isParameter(valnum)) {
            if (callGraph.getEntrypointNodes().toArray()[0].toString().equals(node.toString())) { // new Base*
                int para = node.getMethod().isStatic() ? valnum : valnum - 1;
                String param = "x" + para;
                resultSet.add(param);
                return resultSet;
            }
            for (Iterator<CGNode> it = predNodes; it.hasNext(); ) {
                CGNode pred = it.next();
                if (pred != null && isApplicationLoader(cha, pred) && !visited.contains(pred)) {
                    visited.add(pred);
                    for (Iterator<SSAInstruction> it2 = pred.getIR().iterateAllInstructions(); it2.hasNext(); ) {
                        SSAInstruction blockIns = it2.next();
                        if (blockIns instanceof SSAAbstractInvokeInstruction invokeIns && invokeIns.getDeclaredTarget().getSignature().equals(node.getMethod().getSignature())) {
                            int y = valnum - 1;
                            HashSet<Object> result = getDefFromVal(invokeIns.getUse(y), pred, callGraph, depth + 1);
                            resultSet.addAll(result);
                        }
                    }
                }
            }
        }
        return resultSet;
    }


    public HashSet<Object> getUsageFromVal(int valnum, CGNode node, CallGraph callGraph, int depth) {
        HashSet<Object> resultSet = new HashSet<>();
        if (depth >= DEPTH) {
            return resultSet;
        }
        try {
            SymbolTable st = node.getIR().getSymbolTable();
            DefUse du = node.getDU();
            for (Iterator<SSAInstruction> it = du.getUses(valnum); it.hasNext(); ) {
                SSAInstruction useInstr = it.next();
                if (useInstr instanceof SSAConditionalBranchInstruction cdIns) {
                    // resolve conditional branch instruction
                    for (int i = 0; i < 2; i++) {
                        int predicate = cdIns.getUse(i);
                        if (predicate == valnum)
                            continue;
                        IConditionalBranchInstruction.IOperator op = cdIns.getOperator();
                        if (st.isConstant(predicate)) {
                            Object predicateValue = st.getConstantValue(predicate);
                            if (predicateValue != null) {
                                AbstractMap.SimpleEntry<Object, IConditionalBranchInstruction.IOperator> pair = new AbstractMap.SimpleEntry<>(st.getConstantValue(predicate), op);
                                resultSet.add(pair);
                            }
                        } else if (!st.isConstant(predicate)) {
                            SSAInstruction s = du.getDef(predicate);
                            if (isAcMethod(s)) {
                                AbstractMap.SimpleEntry<Object, IConditionalBranchInstruction.IOperator> pair = new AbstractMap.SimpleEntry<>(((SSAAbstractInvokeInstruction) s).getDeclaredTarget().getName(), op);
                                resultSet.add(pair);
                            } else {
                                // ignore
                            }
                        }
                    }
                } else if (useInstr instanceof SSAAbstractInvokeInstruction invIns && !isAcMethod(useInstr) && !InstructionUtils.isStringBuilderMethod((SSAAbstractInvokeInstruction) useInstr) && !InstructionUtils.isJavaLangMethod(invIns) && !InstructionUtils.isAndroidUtilMethod(invIns)) {
                    Iterator<CGNode> succNodes = callGraph.getSuccNodes(node);
                    MethodReference targetMethod = invIns.getDeclaredTarget();
                    boolean found = false;
                    while (succNodes != null && succNodes.hasNext()) {
                        CGNode succNode = succNodes.next();
                        String parentClass = succNode.getMethod().getDeclaringClass().getName().toString();
                        if (succNode.getMethod().getReference().equals(targetMethod) && isApplicationLoader(cha, succNode) && !parentClass.contains("UserHandle")) {
                            found = true;
                            // map the parameter to the argument
                            for (int i = 0; i < invIns.getNumberOfPositionalParameters(); i++) {
                                if (invIns.getUse(i) == valnum) {
                                    HashSet<Object> result = getUsageFromVal(i + 1, succNode, callGraph, depth + 1);
                                    if (result != null) {
                                        resultSet.addAll(result);
                                    }
                                }
                            }
                        }
                    }
                    try {
                        if (!found) {
                            IMethod iMethod = null;
                            CGNode lookupNode = null;
                            try {
                                iMethod = cha.resolveMethod(targetMethod);
                                if (iMethod == null) {
                                    return resultSet;
                                }
                                lookupNode = callGraph.getNode(iMethod, Everywhere.EVERYWHERE);
                            } catch (Exception e) {
                                return resultSet;
                            }
                            CGNode succNode;
                            CallGraph cgUpdated = null;
                            if (lookupNode == null) {
                                cgUpdated = CachedCallGraphs.buildSingleEpCg(iMethod.getReference(), cha);
                                if (cgUpdated == null || cgUpdated.getEntrypointNodes().toArray().length == 0) {
                                    return resultSet;
                                }
                                try {
                                    succNode = (CGNode) cgUpdated.getEntrypointNodes().toArray()[0];
                                } catch (Exception e) {
                                    return resultSet;
                                }
                            } else {
                                succNode = lookupNode;
                            }
                            for (int i = 0; i < invIns.getNumberOfPositionalParameters(); i++) {
                                if (invIns.getUse(i) == valnum) {
                                    HashSet<Object> result = getUsageFromVal(i + 1, succNode, cgUpdated, depth + 1);
                                    if (result != null) {
                                        resultSet.addAll(result);
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                    }
                }
            }
        } catch (Exception e) {
            //ignore
        }
        return resultSet;
    }

}

