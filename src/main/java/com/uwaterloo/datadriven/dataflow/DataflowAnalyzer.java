package com.uwaterloo.datadriven.dataflow;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.cfg.InterproceduralCFG;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.debug.UnimplementedError;
import com.uwaterloo.datadriven.utils.AccessControlUtils;
import com.uwaterloo.datadriven.utils.CachedCallGraphs;
import com.uwaterloo.datadriven.utils.CollectionUtils;
import com.uwaterloo.datadriven.utils.InstructionUtils;

import java.util.*;

public class DataflowAnalyzer {
    private final ClassHierarchy cha;
    private final int MAX_DEPTH = 15;

    private static final int CACHE_MAX_SIZE = 1000;
    private static final Cache<Pair<SSAInstruction, CGNode>, HashSet<Object>> cachedRets
            = CacheBuilder.newBuilder().maximumSize(CACHE_MAX_SIZE).build();
    private static final Cache<Pair<Integer, CGNode>, HashSet<Object>> cachedDefs
            = CacheBuilder.newBuilder().maximumSize(CACHE_MAX_SIZE).build();
    private static final Cache<Pair<Integer, CGNode>, Boolean> cachedUses
            = CacheBuilder.newBuilder().maximumSize(CACHE_MAX_SIZE).build();
    private final String CG_PARAM_DEF = "CG_PARAM_DEF";
    private final HashSet<String> DO_NOT_TRACE_PARAMS = new HashSet<>(List.of(
            "Landroid/content/Context"
    ));

    private final List<Class<?>> PROBLEMATIC_CLASSES = Arrays.asList(
            SSAPhiInstruction.class,
            SSAAbstractUnaryInstruction.class,
            SSAAbstractBinaryInstruction.class
    );

    private static DataflowAnalyzer instance = null;

    public synchronized static DataflowAnalyzer getInstance(ClassHierarchy cha) {
        if (cha == null)
            throw new RuntimeException("CHA cannot be null");
        if (instance == null)
            instance = new DataflowAnalyzer(cha);

        return instance;
    }

    private DataflowAnalyzer(ClassHierarchy cha) {
        this.cha = cha;
    }

    public boolean isReturnedByApi(CGNode node, List<Integer> resultValNums,
                                   HashSet<Pair<CGNode, Integer>> visited, CallGraph cg) {
        boolean isRet = false;
        try {
            for (int val : resultValNums) {
                if (visited.contains(Pair.make(node, val)) || visited.size() >= MAX_DEPTH)
                    continue;
                visited.add(Pair.make(node, val));
                Boolean isCurRet = cachedUses.getIfPresent(Pair.make(val, node));
                if (isCurRet == null) {
                    isCurRet = false;
                    for (Iterator<SSAInstruction> it = node.getDU().getUses(val); it.hasNext(); ) {
                        SSAInstruction ins = it.next();
                        if (ins instanceof SSAReturnInstruction) {
                            if (cg.getPredNodeCount(node) > 1) {
                                for (Iterator<CGNode> iter = cg.getPredNodes(node); iter.hasNext(); ) {
                                    CGNode pred = iter.next();
                                    if (!cg.getFakeRootNode().equals(pred)
                                            && !cg.getFakeWorldClinitNode().equals(pred)) {
                                        isCurRet = isCurRet || isReturnedByApi(pred,
                                                getInvResultValNums(cg, node.getMethod().getSignature()),
                                                visited, cg);
                                    }
                                }
                            } else if (cg.getPredNodeCount(node) == 1) {
                                CGNode pred = cg.getPredNodes(node).next();
                                if (!cg.getFakeRootNode().equals(pred)
                                        && !cg.getFakeWorldClinitNode().equals(pred)) {
                                    isCurRet = isCurRet || isReturnedByApi(pred,
                                            getInvResultValNums(cg, node.getMethod().getSignature()),
                                            visited, cg);
                                } else {
                                    isCurRet = true;
                                }
                            } else {
                                isCurRet = true;
                            }
                        } else if (ins instanceof SSAAbstractInvokeInstruction abIns) {
                            if (CollectionUtils.getInstance(cha).isAnyCollectionMethod(abIns)
                                    || CollectionUtils.getInstance(cha).isCollection(abIns.getDeclaredTarget().getDeclaringClass())) {
                                isCurRet = isCurRet || isReturnedByApi(node,
                                        List.of(abIns.getUse(0)), visited, cg);
                            } else if (abIns.getNumberOfReturnValues() > 0) {
                                if (shouldTraceReturnUses(abIns, val, new HashSet<>()))
                                    isCurRet = isCurRet || isReturnedByApi(node,
                                            List.of(abIns.getReturnValue(0)), visited, cg);
                            }
                        } else if (isProblematicInstrType(ins)
                                || ins instanceof SSANewInstruction
                                || ins instanceof SSACheckCastInstruction) {
                            isCurRet = isCurRet || isReturnedByApi(node,
                                    List.of(ins.getDef()), visited, cg);
                        } else if (ins instanceof SSAFieldAccessInstruction) {
                            //TODO: Check if we want to handle inter-field relations.
                            // If yes, they (most probably) go here.
                        }
                    }
                    cachedUses.put(Pair.make(val, node), isCurRet);
                }
                isRet = isRet || isCurRet;
            }
        }catch (Exception e) {
            //ignore
        }

        return isRet;
    }

    private ArrayList<Integer> getInvResultValNums(CallGraph singleEpCg, String invMethod) {
        CGNode node = (CGNode) singleEpCg.getEntrypointNodes().toArray()[0];
        ArrayList<Integer> ret = new ArrayList<>();
        for (SSAInstruction ins : node.getIR().getInstructions()) {
            if (ins instanceof SSAAbstractInvokeInstruction) {
                try {
                    SSAAbstractInvokeInstruction inv = (SSAAbstractInvokeInstruction) ins;
                    if (inv.getDeclaredTarget().getSignature().equals(invMethod)
                            && inv.getNumberOfReturnValues() == 1)
                        ret.add(inv.getReturnValue(0));
                } catch (Exception e) {
                    //ignore
                }
            }
        }

        return ret;
    }

    private boolean shouldTraceReturnUses(SSAAbstractInvokeInstruction abIns, int val,
                                          HashSet<Pair<SSAInstruction, CGNode>> visited) {
        CallGraph nextCg = null;
        CGNode nextNode = null;
        try {
            nextCg = CachedCallGraphs.buildSingleEpCg(abIns.getDeclaredTarget(), cha);
            nextNode = (CGNode) nextCg.getEntrypointNodes().toArray()[0];

            int paramValNum = nextNode.getIR().getParameter(getParamNumFromVal(abIns, val));

            Stack<Pair<SSAInstruction, Integer>> toAnalyze = new Stack<>();

            addAllUsesToStack(toAnalyze, visited, nextNode, paramValNum);
            while (!toAnalyze.isEmpty()) {
                Pair<SSAInstruction, Integer> insP = toAnalyze.pop();
                SSAInstruction ins = insP.fst;
                if (ins instanceof SSAReturnInstruction)
                    return true;
                else if (isProblematicInstrType(ins)
                        || ins instanceof SSANewInstruction
                        || ins instanceof SSAFieldAccessInstruction)
                    addAllUsesToStack(toAnalyze, visited, nextNode, ins.getDef());
                else if (ins instanceof SSAAbstractInvokeInstruction nextAbIns) {
                    if (nextAbIns.getNumberOfReturnValues() > 0) {
                        if (isPrimitive(nextAbIns)
                                || shouldTraceReturnUses(nextAbIns, insP.snd, visited))
                            addAllUsesToStack(toAnalyze, visited, nextNode, nextAbIns.getReturnValue(0));
                    }
                }
            }
        } catch (Exception | UnimplementedError e) {
            //ignore
        }
        return false;
    }

    private void addAllUsesToStack(Stack<Pair<SSAInstruction, Integer>> st,
                                   HashSet<Pair<SSAInstruction, CGNode>> visited, CGNode node, int v) {
        for (Iterator<SSAInstruction> it = node.getDU().getUses(v); it.hasNext(); ) {
            SSAInstruction i = it.next();
            Pair<SSAInstruction, CGNode> curP = Pair.make(i, node);
            if (!visited.contains(curP)) {
                visited.add(curP);
                st.push(Pair.make(i, v));
            }
        }
    }

    private boolean isPrimitive(SSAAbstractInvokeInstruction inv) {
        try {
            return inv.getDeclaredTarget().getDeclaringClass().isPrimitiveType();
        } catch (Exception e) {
            //ignore
        }

        return true;
    }

    private HashSet<Object> traceValDefs(CGNode node, int valNum, CallGraph cg) {
        SymbolTable st = node.getIR().getSymbolTable();

        HashSet<Object> curDefs = cachedDefs.getIfPresent(Pair.make(valNum, node));
        if (curDefs == null) {
            curDefs = new HashSet<>();
            int paramNum = -1;
            if (!st.isParameter(valNum)) {
                HashSet<Object> defs = getDefFromVal(valNum, node, cg, new HashSet<>());
                for (Object def : defs) {
                    if (def instanceof Pair<?, ?>
                            && ((Pair<?, ?>) def).fst instanceof String
                            && ((Pair<?, ?>) def).snd instanceof Integer
                            && ((Pair<?, ?>) def).fst.equals(CG_PARAM_DEF))
                        paramNum = (Integer) ((Pair<?, ?>) def).snd;
                    else
                        curDefs.add(def);
                }
            }
            if (st.isParameter(valNum) || paramNum != -1) {
                if (cg.getPredNodeCount(node) > 0) {
                    for (Iterator<CGNode> it = cg.getPredNodes(node); it.hasNext(); ) {
                        CGNode pred = it.next();
                        for (Pair<Integer, TypeReference> pVal : getParamValNums(cg,
                                node.getMethod().getSignature(),
                                paramNum == -1 ? getParamNumFromVal(node, valNum) : paramNum))
                            if (!DO_NOT_TRACE_PARAMS.contains(pVal.snd.getName().toString()))
                                curDefs.addAll(traceValDefs(pred, pVal.fst, cg));
                            else
                                curDefs.add(pVal.snd.getName().toString());
                    }
                } else {
                    String pType = "";
                    try {
                        int pNum = paramNum == -1 ? getParamNumFromVal(node, valNum) : paramNum;
                        pType = node.getMethod().getParameterType(pNum).getName().toString();
                    } catch (Exception e) {
                        //ignore
                    }
                    curDefs.add("From parameter: [" + pType + "] of top method");
                }
            }
            cachedDefs.put(Pair.make(valNum, node), curDefs);
        }

        return curDefs;
    }

    private ArrayList<Pair<Integer, TypeReference>> getParamValNums(CallGraph cg, String method, int param) {
        ArrayList<Pair<Integer, TypeReference>> retVals = new ArrayList<>();
        CGNode node = (CGNode) cg.getEntrypointNodes().toArray()[0];
        for (SSAInstruction ins : node.getIR().getInstructions()) {
            if (ins instanceof SSAAbstractInvokeInstruction abIns) {
                if (abIns.getDeclaredTarget().getSignature().equals(method)) {
                    TypeReference pType = abIns.getDeclaredTarget().getParameterType(param-1);
                    retVals.add(Pair.make(abIns.getUse(abIns.isStatic() ? param - 1 : param), pType));
                }
            }
        }

        return retVals;
    }

    private int getParamNumFromVal(CGNode node, int valNum) {
        int[] paramVals = node.getIR().getParameterValueNumbers();
        for (int i = 0; i < paramVals.length; i++) {
            if (paramVals[i] == valNum)
                return i;
        }
        return -1;
    }

    private int getParamNumFromVal(SSAAbstractInvokeInstruction inv, int valNum) {
        try {
            for (int i = 0; i < inv.getNumberOfUses(); i++) {
                if (inv.getUse(i) == valNum)
                    return i;
            }
        } catch (Exception e) {
            //ignore
        }
        return -1;
    }

    public boolean isApiParam(int val, CGNode node, CallGraph cg) {
        HashSet<Object> defs = getDefFromVal(val, node, cg, new HashSet<>());
        for (Object def : defs) {
            if (def instanceof Pair<?,?> defPair) {
                if (defPair.fst instanceof String && defPair.fst.equals(CG_PARAM_DEF))
                    return true;
            }
        }
        return false;
    }

    private HashSet<Object> getDefFromVal(int val, CGNode node, CallGraph compCg,
                                         HashSet<Pair<CGNode, Integer>> visited) {

        if (visited.contains(Pair.make(node, val)) || visited.size() >= MAX_DEPTH)
            return new HashSet<>();
        visited.add(Pair.make(node, val));

        HashSet<Object> retVal = cachedDefs.getIfPresent(Pair.make(val, node));
        if (retVal == null) {
            retVal = new HashSet<>();
            try {
                Object paramDef = getDefFromValNum(node, val, compCg);
                if (paramDef instanceof SSAInstruction)
                    retVal.addAll(getReturnVal((SSAInstruction) paramDef, node, compCg, visited));
                else if (paramDef instanceof Collection) {
                    for (Object def : (Collection<?>) paramDef) {
                        CGNode curNode = node;
                        if (def instanceof Pair) {
                            curNode = (CGNode) ((Pair<?, ?>) def).fst;
                            def = ((Pair<?, ?>) def).snd;
                        }
                        if (def instanceof SSAInstruction)
                            retVal.addAll(getReturnVal((SSAInstruction) def, curNode, compCg, visited));
                        else if (def != null)
                            retVal.add(def);
                    }
                } else if (paramDef != null) {
                    retVal.add(paramDef);
                }
                if (paramDef != null && retVal.isEmpty())
                    retVal.add(paramDef);
            } catch (Exception e) {
                //ignore
            }
            cachedDefs.put(Pair.make(val, node), retVal);
        }

        return retVal;
    }

    private HashSet<Object> getReturnVal(SSAInstruction target, CGNode curNode,
                                         CallGraph compCg, HashSet<Pair<CGNode, Integer>> visited) {
        Pair<SSAInstruction, CGNode> curKey = Pair.make(target, curNode);

        HashSet<Object> retVals = cachedRets.getIfPresent(curKey);
        if (retVals == null) {
            retVals = new HashSet<>();
            if (isProblematicInstrType(target)) {
                for (int v : getValNumFromProbInstrType(target, curNode)) {
                    retVals.addAll(getDefFromVal(v, curNode, compCg, visited));
                }
            } else if (target instanceof SSAFieldAccessInstruction) {
                //TODO: Check if we want to handle inter-field relations.
                // If yes, they (most probably) go here.
            } else if (target instanceof SSAAbstractInvokeInstruction abIns) {
                if (AccessControlUtils.isCheckingMethod(abIns.getDeclaredTarget().getName().toString())
                        || AccessControlUtils.isEnforcingMethod(abIns.getDeclaredTarget().getName().toString())) {
                    int retInsVal = abIns.getUse(InstructionUtils.getParameterIndex(abIns, 0));
                    retVals.addAll(getDefFromVal(retInsVal, curNode, compCg, visited));
                } else if (InstructionUtils.isEqualsMethod(abIns)) {
                    retVals.addAll(getDefFromVal(abIns.getUse(0), curNode, compCg, visited));
                    retVals.addAll(getDefFromVal(abIns.getUse(1), curNode, compCg, visited));
                } else if(InstructionUtils.isStringBuilderMethod(abIns, "toString")) {
                    int sbVal = abIns.getUse(0);
                    for (Iterator<SSAInstruction> it = curNode.getDU().getUses(sbVal); it.hasNext(); ) {
                        SSAInstruction sbIns = it.next();
                        if (sbIns instanceof SSAAbstractInvokeInstruction sbInv) {
                            if ((sbInv.getDeclaredTarget().isInit() && sbInv.getNumberOfUses() > 1)
                                    || (InstructionUtils.isStringBuilderMethod((SSAAbstractInvokeInstruction) sbIns,
                                    "append"))) {
                                int retInsVal = sbInv.getUse(1);
                                retVals.addAll(getDefFromVal(retInsVal, curNode, compCg, visited));
                            }
                        }
                    }
                } else if (InstructionUtils.isStringFormatMethod(abIns)) {
                    for (int i = 0; i < abIns.getDeclaredTarget().getNumberOfParameters(); i++) {
                        if (isStringType(abIns.getDeclaredTarget().getParameterType(i)))
                            retVals.addAll(getDefFromVal(abIns.getUse(i), curNode, compCg, visited));
                    }
                } else {
                    Set<CGNode> nodes = compCg.getNodes(abIns.getDeclaredTarget());
                    for (CGNode node : nodes) {
                        for (SSAInstruction ins : node.getIR().getInstructions()) {
                            try {
                                if (ins instanceof SSAReturnInstruction) {
                                    int retInsVal = ((SSAReturnInstruction) ins).getResult();
                                    if (retInsVal != -1)
                                        retVals.addAll(getDefFromVal(retInsVal, node, compCg, visited));
                                }
                            } catch (Exception e) {
                                //ignore
                            }
                        }
                    }
                }
            } else if (target instanceof SSANewInstruction newIns) {
                for (Iterator<SSAInstruction> it = curNode.getDU().getUses(newIns.getDef());
                     it.hasNext(); ) {
                    SSAInstruction ins = it.next();
                    if (ins instanceof SSAAbstractInvokeInstruction abIns
                            && ((SSAAbstractInvokeInstruction) ins).getDeclaredTarget().isInit()) {
                        int start = abIns.isStatic() ? 0 : 1;
                        for (int i = start; i < abIns.getNumberOfUses(); i++) {
                            retVals.addAll(getDefFromVal(abIns.getUse(i), curNode, compCg, visited));
                        }
                    }
                }
            } else if (target instanceof SSACheckCastInstruction ccIns) {
                retVals.addAll(getDefFromVal(ccIns.getVal(), curNode, compCg, visited));
            }

            if (retVals.isEmpty())
                retVals.add(target);

            cachedRets.put(curKey, retVals);
        }

        return retVals;
    }

    private Object getDefFromValNum(CGNode node, int valNum, CallGraph cg) {
        Object retVal = null;
        try {
            SymbolTable st = node.getIR().getSymbolTable();
            if (st.isConstant(valNum))
                retVal = st.getConstantValue(valNum);
            else if (st.isParameter(valNum)) {
                InterproceduralCFG icfg = new InterproceduralCFG(cg);
                HashSet<Object> foundDef = new HashSet<>(DefUseChain.chainWithNodes(valNum,
                        icfg.getEntry(node), icfg, new ArrayList<>()));
                if (!foundDef.isEmpty())
                    retVal = foundDef;
                else
                    retVal = Pair.make(CG_PARAM_DEF, getParamNumFromVal(node, valNum));
            }
            if (retVal == null)
                retVal = node.getDU().getDef(valNum);
        } catch (Exception e) {
            //make peace with it and move on
        }

        return retVal;
    }

    private HashSet<Integer> getValNumFromProbInstrType(SSAInstruction ins, CGNode node) {
        HashSet<Integer> newValueNumbers = new HashSet<>();
        DefUse du = node.getDU();

        HashSet<SSAInstruction> visitedInstrs = new HashSet<>(Collections.singletonList(ins));
        Stack<SSAInstruction> nextInstrs = new Stack<>();
        nextInstrs.push(ins);

        while (!nextInstrs.isEmpty()) {
            SSAInstruction curInstr = nextInstrs.pop();
            for(int i = 0; i < curInstr.getNumberOfUses(); i++) {
                int curValNum = curInstr.getUse(i);
                if (node.getIR().getSymbolTable().isConstant(curValNum)) {
                    newValueNumbers.add(curValNum);
                    continue;
                }
                SSAInstruction nextInstr = du.getDef(curValNum);
                if (nextInstr != null) {
                    if (isProblematicInstrType(nextInstr)) {
                        if(!visitedInstrs.contains(nextInstr)) {
                            nextInstrs.push(nextInstr);
                            visitedInstrs.add(curInstr);
                        }
                    } else {
                        newValueNumbers.add(curValNum);
                    }
                }
            }
        }

        return newValueNumbers;
    }

    private boolean isProblematicInstrType(SSAInstruction ins) {
        Class<?> curCls = ins.getClass();
        for (Class<?> pc : PROBLEMATIC_CLASSES) {
            if (pc.isAssignableFrom(curCls))
                return true;
        }

        return false;
    }

    private boolean isStringType(TypeReference type) {
        return type.getName().toString().startsWith("Ljava/")
                && type.getName().getClassName().toString().equals("String");
    }

}