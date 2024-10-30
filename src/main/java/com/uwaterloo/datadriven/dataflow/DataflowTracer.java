//package com.uwaterloo.datadriven.dataflow;
//
//import com.google.common.cache.Cache;
//import com.google.common.cache.CacheBuilder;
//import com.ibm.wala.classLoader.IClass;
//import com.ibm.wala.ipa.callgraph.CGNode;
//import com.ibm.wala.ipa.callgraph.CallGraph;
//import com.ibm.wala.ipa.cha.ClassHierarchy;
//import com.ibm.wala.ssa.*;
//import com.ibm.wala.types.FieldReference;
//import com.ibm.wala.types.MethodReference;
//import com.ibm.wala.types.TypeReference;
//import com.ibm.wala.util.collections.Pair;
//import com.uwaterloo.datadriven.model.accesscontrol.misc.AccessControlType;
//import com.uwaterloo.datadriven.utils.AccessControlUtils;
//import com.uwaterloo.datadriven.utils.CachedCallGraphs;
//import com.uwaterloo.datadriven.utils.CollectionUtils;
//import com.uwaterloo.datadriven.utils.InstructionUtils;
//
//import java.util.*;
////TODO: Delete if unused
//public class DataflowTracer {
//    private final ClassHierarchy cha;
//    private final CollectionUtils collectionUtils;
//    private final int MAX_DEPTH = 15;
//
//    private static final int CACHE_MAX_SIZE = 1000;
//    private static final Cache<Pair<SSAInstruction, CGNode>, HashSet<Object>> cachedRets
//            = CacheBuilder.newBuilder().maximumSize(CACHE_MAX_SIZE).build();
//    private static final Cache<Pair<Integer, CGNode>, HashSet<Object>> cachedDefs
//            = CacheBuilder.newBuilder().maximumSize(CACHE_MAX_SIZE).build();
//    private final List<Class<?>> PROBLEMATIC_CLASSES = Arrays.asList(
//            SSAPhiInstruction.class,
//            SSAAbstractUnaryInstruction.class,
//            SSAAbstractBinaryInstruction.class
//    );
//
//    public DataflowTracer(ClassHierarchy cha) {
//        this.cha = cha;
//        collectionUtils = CollectionUtils.getInstance(cha);
//    }
//
//    private int getParamNumFromVal(CGNode node, int valNum) {
//        int[] paramVals = node.getIR().getParameterValueNumbers();
//        for (int i = 0; i < paramVals.length; i++) {
//            if (paramVals[i] == valNum)
//                return i;
//        }
//        return -1;
//    }
//
//    public HashSet<Object> getDefFromVal(int val, CGNode node, CallGraph cg,
//                                         ArrayList<ArrayList<Object>> resolvedParams,
//                                         HashSet<Pair<CGNode, Integer>> visited) {
//
//        if (visited.contains(Pair.make(node, val)) || visited.size() >= MAX_DEPTH)
//            return new HashSet<>();
//        visited.add(Pair.make(node, val));
//
//        HashSet<Object> retVal = cachedDefs.getIfPresent(Pair.make(val, node));
//        if (retVal == null) {
//            retVal = new HashSet<>();
//            try {
//                Object paramDef = getDefFromValNum(node, val, resolvedParams);
//                if (paramDef instanceof SSAInstruction)
//                    retVal.addAll(getReturnVal((SSAInstruction) paramDef, node, cg, resolvedParams, visited));
//                else if (paramDef instanceof Collection) {
//                    for (Object def : (Collection<?>) paramDef) {
//                        CGNode curNode = node;
//                        if (def instanceof Pair) {
//                            curNode = (CGNode) ((Pair<?, ?>) def).fst;
//                            def = ((Pair<?, ?>) def).snd;
//                        }
//                        if (def instanceof SSAInstruction)
//                            retVal.addAll(getReturnVal((SSAInstruction) def, curNode, cg,
//                                    resolvedParams, visited));
//                        else if (def != null)
//                            retVal.add(def);
//                    }
//                } else if (paramDef != null) {
//                    retVal.add(paramDef);
//                }
//                if (paramDef != null && retVal.isEmpty())
//                    retVal.add(paramDef);
//            } catch (Exception e) {
//                //ignore
//            }
//            cachedDefs.put(Pair.make(val, node), retVal);
//        }
//
//        return retVal;
//    }
//
//    private HashSet<Object> getReturnVal(SSAInstruction target, CGNode curNode,
//                                         CallGraph cg, ArrayList<ArrayList<Object>> resolvedParams,
//                                         HashSet<Pair<CGNode, Integer>> visited) {
//        Pair<SSAInstruction, CGNode> curKey = Pair.make(target, curNode);
//
//        HashSet<Object> retVals = cachedRets.getIfPresent(curKey);
//        if (retVals == null) {
//            retVals = new HashSet<>();
//            if (isProblematicInstrType(target)) {
//                for (int v : getValNumFromProbInstrType(target, curNode)) {
//                    retVals.addAll(getDefFromVal(v, curNode, cg, resolvedParams, visited));
//                }
//            } else if (target instanceof SSAFieldAccessInstruction) {
//                IClass clazz = curNode.getMethod().getDeclaringClass();
//                FieldReference curField = ((SSAFieldAccessInstruction) target).getDeclaredField();
//                ArrayList<Pair<SSAPutInstruction, CGNode>> fieldPuts = getAllFieldPut(curField, clazz);
//                for (Pair<SSAPutInstruction, CGNode> p : fieldPuts) {
//                    retVals.addAll(getDefFromVal(p.fst.getVal(), p.snd,
//                            CachedCallGraphs.buildClassCg(clazz, cha),
//                            new ArrayList<>(),//Might need to change this by changing getAllFieldPut return value
//                            visited));
//                }
//                if (collectionUtils.isCollection(curField.getFieldType())) {
//                    try {
//                        ArrayList<Pair<SSAAbstractInvokeInstruction, CGNode>> fieldModifiers
//                                = getAllCollectionModifiers(curField, clazz);
//                        for (Pair<SSAAbstractInvokeInstruction, CGNode> m : fieldModifiers) {
//                            int fVal = collectionUtils.getNumParamsForCollMod(m.fst);
//                            retVals.addAll(getDefFromVal(m.fst.getUse(fVal), m.snd,
//                                    CachedCallGraphs.buildClassCg(clazz, cha),
//                                    new ArrayList<>()/*Might need to change*/, visited));
//                        }
//                    } catch (Exception e) {
//                        //ignore
//                    }
//                }
//            } else if (target instanceof SSAAbstractInvokeInstruction) {
//                SSAAbstractInvokeInstruction abIns = (SSAAbstractInvokeInstruction) target;
//                String methodName = abIns.getDeclaredTarget().getName().toString();
//                if (AccessControlUtils.isCheckingMethod(methodName, AccessControlType.Permission)
//                        || AccessControlUtils.isEnforcingMethod(methodName, AccessControlType.Permission)) {
//                    int retInsVal = abIns.getUse(InstructionUtils.getParameterIndex(abIns, 0));
//                    retVals.addAll(getDefFromVal(retInsVal, curNode, cg, resolvedParams, visited));
//                } else if (InstructionUtils.isEqualsMethod(abIns)) {
//                    retVals.addAll(getDefFromVal(abIns.getUse(0), curNode, cg, resolvedParams, visited));
//                    retVals.addAll(getDefFromVal(abIns.getUse(1), curNode, cg, resolvedParams, visited));
//                } else if(InstructionUtils.isStringBuilderMethod(abIns, "toString")) {
//                    int sbVal = abIns.getUse(0);
//                    for (Iterator<SSAInstruction> it = curNode.getDU().getUses(sbVal); it.hasNext(); ) {
//                        SSAInstruction sbIns = it.next();
//                        if (sbIns instanceof SSAAbstractInvokeInstruction sbInv) {
//                            if ((sbInv.getDeclaredTarget().isInit() && sbInv.getNumberOfUses() > 1)
//                                    || (InstructionUtils.isStringBuilderMethod((SSAAbstractInvokeInstruction) sbIns,
//                                    "append"))) {
//                                int retInsVal = sbInv.getUse(1);
//                                retVals.addAll(getDefFromVal(retInsVal, curNode, cg, resolvedParams, visited));
//                            }
//                        }
//                    }
//                } else if (InstructionUtils.isStringFormatMethod(abIns)) {
//                    for (int i = 0; i < abIns.getDeclaredTarget().getNumberOfParameters(); i++) {
//                        if (isStringType(abIns.getDeclaredTarget().getParameterType(i)))
//                            retVals.addAll(getDefFromVal(abIns.getUse(i), curNode, cg, resolvedParams, visited));
//                    }
//                } else {
//                    ArrayList<ArrayList<Object>> invResolvedParams = resolveTargetParams(abIns, curNode,
//                            cg, resolvedParams, visited);
//                    for (CGNode node : getTargetNodes(abIns.getDeclaredTarget(), curNode, cg)) {
//                        for (SSAInstruction ins : node.getIR().getInstructions()) {
//                            try {
//                                if (ins instanceof SSAReturnInstruction) {
//                                    int retInsVal = ((SSAReturnInstruction) ins).getResult();
//                                    if (retInsVal != -1)
//                                        retVals.addAll(getDefFromVal(retInsVal, node, cg,
//                                                invResolvedParams, visited));
//                                }
//                            } catch (Exception e) {
//                                //ignore
//                            }
//                        }
//                    }
//                }
//            } else if (target instanceof SSANewInstruction newIns) {
//                for (Iterator<SSAInstruction> it = curNode.getDU().getUses(newIns.getDef());
//                     it.hasNext(); ) {
//                    SSAInstruction ins = it.next();
//                    if (ins instanceof SSAAbstractInvokeInstruction abIns
//                            && ((SSAAbstractInvokeInstruction) ins).getDeclaredTarget().isInit()) {
//                        int start = abIns.isStatic() ? 0 : 1;
//                        for (int i = start; i < abIns.getNumberOfUses(); i++) {
//                            retVals.addAll(getDefFromVal(abIns.getUse(i), curNode, cg, resolvedParams, visited));
//                        }
//                    }
//                }
//            } else if (target instanceof SSACheckCastInstruction ccIns) {
//                retVals.addAll(getDefFromVal(ccIns.getVal(), curNode, cg, resolvedParams, visited));
//            }
//
//            if (retVals.isEmpty())
//                retVals.add(target);
//
//            cachedRets.put(curKey, retVals);
//        }
//
//        return retVals;
//    }
//
//    private Object getDefFromValNum(CGNode node, int valNum, ArrayList<ArrayList<Object>> resolvedParams) {
//        Object retVal = null;
//
//        try {
//            SymbolTable st = node.getIR().getSymbolTable();
//            if (st.isConstant(valNum))
//                retVal = st.getConstantValue(valNum);
//            else if (st.isParameter(valNum)) {
//                int paramNum = getParamNumFromVal(node, valNum);
//                if (paramNum >= 0 && paramNum < resolvedParams.size())
//                    retVal = resolvedParams.get(paramNum);
//            }
//            if (retVal == null)
//                retVal = node.getDU().getDef(valNum);
//        } catch (Exception e) {
//            //make peace with it and move on
//        }
//
//        return retVal;
//    }
//
//    /*
//    This method might need to return resolved parameters for each CGNode.
//    Because it is the responsibility of whoever creates a CGNode to resolve param values.
//     */
//    private ArrayList<Pair<SSAPutInstruction, CGNode>> getAllFieldPut(FieldReference fr, IClass curClass) {
//        ArrayList<Pair<SSAPutInstruction, CGNode>> ret = new ArrayList<>();
//        CallGraph cg = CachedCallGraphs.buildClassCg(curClass, cha);
//        for (CGNode n : cg) {
//            if (n.getMethod().getDeclaringClass().equals(curClass)) {
//                for (SSAInstruction i : n.getIR().getInstructions()) {
//                    if (i instanceof SSAPutInstruction fi) {
//                        if (fi.getDeclaredField().equals(fr))
//                            ret.add(Pair.make(fi, n));
//                    }
//                }
//            }
//        }
//
//        return ret;
//    }
//
//    private ArrayList<Pair<SSAAbstractInvokeInstruction, CGNode>> getAllCollectionModifiers(FieldReference fr,
//                                                                                            IClass curClass) {
//        ArrayList<Pair<SSAAbstractInvokeInstruction, CGNode>> ret = new ArrayList<>();
//        HashSet<SSAAbstractInvokeInstruction> curUniques = new HashSet<>();
//        CallGraph cg = CachedCallGraphs.buildClassCg(curClass, cha);
//        for (CGNode n : cg) {
//            if (n.getMethod().getDeclaringClass().equals(curClass)) {
//                for (SSAInstruction i : n.getIR().getInstructions()) {
//                    if (i instanceof SSAGetInstruction fi) {
//                        if (fi.getDeclaredField().equals(fr)) {
//                            DefUse du = n.getDU();
//                            for (Iterator<SSAInstruction> it = du.getUses(fi.getDef()); it.hasNext(); ) {
//                                SSAInstruction ins = it.next();
//                                if (ins instanceof SSAAbstractInvokeInstruction abIns) {
//                                    if (collectionUtils.isCollectionModifier(abIns) && curUniques.add(abIns))
//                                        ret.add(Pair.make(abIns, n));
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//        }
//
//        return ret;
//    }
//
//    private Collection<CGNode> getTargetNodes(MethodReference method, CGNode curNode, CallGraph cg) {
//        ArrayList<CGNode> targetNodes = new ArrayList<>();
//        for (Iterator<CGNode> it = cg.getSuccNodes(curNode); it.hasNext(); ) {
//            CGNode succNode = it.next();
//            if (succNode.getMethod().getSignature().equals(method.getSignature()))
//                targetNodes.add(succNode);
//        }
//
//        return targetNodes;
//    }
//
//    private ArrayList<ArrayList<Object>> resolveTargetParams(SSAAbstractInvokeInstruction abIns,
//                                                             CGNode curNode, CallGraph cg,
//                                                             ArrayList<ArrayList<Object>> curResolvedParams,
//                                                             HashSet<Pair<CGNode, Integer>> visited) {
//        int firstParam = InstructionUtils.getParameterIndex(abIns, 0);
//        ArrayList<ArrayList<Object>> newlyResolvedParams = new ArrayList<>();
//        for (int param = firstParam; param < abIns.getNumberOfUses(); param++) {
//            newlyResolvedParams.add(new ArrayList<>(getDefFromVal(abIns.getUse(param),
//                    curNode, cg, curResolvedParams, visited)));
//        }
//
//        return newlyResolvedParams;
//    }
//
//    private HashSet<Integer> getValNumFromProbInstrType(SSAInstruction ins, CGNode node) {
//        HashSet<Integer> newValueNumbers = new HashSet<>();
//        DefUse du = node.getDU();
//
//        HashSet<SSAInstruction> visitedInstrs = new HashSet<>(Collections.singletonList(ins));
//        Stack<SSAInstruction> nextInstrs = new Stack<>();
//        nextInstrs.push(ins);
//
//        while (!nextInstrs.isEmpty()) {
//            SSAInstruction curInstr = nextInstrs.pop();
//            for(int i = 0; i < curInstr.getNumberOfUses(); i++) {
//                int curValNum = curInstr.getUse(i);
//                if (node.getIR().getSymbolTable().isConstant(curValNum)) {
//                    newValueNumbers.add(curValNum);
//                    continue;
//                }
//                SSAInstruction nextInstr = du.getDef(curValNum);
//                if (nextInstr != null) {
//                    if (isProblematicInstrType(nextInstr)) {
//                        if(!visitedInstrs.contains(nextInstr)) {
//                            nextInstrs.push(nextInstr);
//                            visitedInstrs.add(curInstr);
//                        }
//                    } else {
//                        newValueNumbers.add(curValNum);
//                    }
//                }
//            }
//        }
//
//        return newValueNumbers;
//    }
//
//    private boolean isProblematicInstrType(SSAInstruction ins) {
//        Class<?> curCls = ins.getClass();
//        for (Class<?> pc : PROBLEMATIC_CLASSES) {
//            if (pc.isAssignableFrom(curCls))
//                return true;
//        }
//
//        return false;
//    }
//
//    private boolean isStringType(TypeReference type) {
//        return type.getName().toString().startsWith("Ljava/")
//                && type.getName().getClassName().toString().equals("String");
//    }
//}
