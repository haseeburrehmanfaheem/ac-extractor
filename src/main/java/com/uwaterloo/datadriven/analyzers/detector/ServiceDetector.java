package com.uwaterloo.datadriven.analyzers.detector;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.dalvik.dex.instructions.Invoke;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.TypeReference;
import com.uwaterloo.datadriven.model.framework.FrameworkService;
import com.uwaterloo.datadriven.dataflow.TypeFinder;
import com.uwaterloo.datadriven.utils.CachedCallGraphs;
import com.uwaterloo.datadriven.utils.CgUtils;
import com.uwaterloo.datadriven.utils.ChaUtils;
import com.uwaterloo.datadriven.utils.InstructionUtils;

import java.util.HashSet;
import java.util.List;

public class ServiceDetector {
    private final AnalysisScope scope;
    private final ClassHierarchy cha;
    private final HashSet<String> excludedClasses = new HashSet<>(List.of(
            "Landroid/os/IBinder",
            "Lcom/android/server/SystemService"
    ));

    public ServiceDetector(ClassHierarchy cha) {
        this.scope = cha.getScope();
        this.cha = cha;
    }
    public HashSet<FrameworkService> detectFrameworkServices() {
        HashSet<FrameworkService> services = new HashSet<>();
        HashSet<String> visitedClasses = new HashSet<>();
        List<DefaultEntrypoint> eps = ChaUtils.generateEps(cha,
                c -> scope.isApplicationLoader(c.getClassLoader())
                        && !c.toString().contains("apache")
                        && !c.toString().contains("junit")
                        && !c.toString().contains("ServiceManager")
                        && !c.toString().contains("Lcom/android/server/SystemService"),
                null,
                ins -> ins instanceof Invoke
                        && (ins.toString().contains("Landroid/os/ServiceManager addService")
                        || ins.toString().contains("publishBinderService")));
        CallGraph cg = CgUtils.buildCg(cha, eps);
        for (CGNode node : cg) {
            if (scope.isApplicationLoader(node.getMethod().getDeclaringClass().getClassLoader())
                    && node.getIR() != null) {
                for (SSAInstruction ins : node.getIR().getInstructions()) {
                    if (ins instanceof SSAAbstractInvokeInstruction
                            && (ins.toString().contains("Landroid/os/ServiceManager, addService(")
                            || ins.toString().contains("publishBinderService("))) {
                        try {
                            IClass service = TypeFinder.findConcreteType(cg, node,
                                    InstructionUtils.getRealUse((SSAAbstractInvokeInstruction) ins, 1));
                            if (service!=null && service.getName().toString().contains("Landroid/os/IBinder")) {
                                service = analyzeFieldInitialization(cg, node, ins);
                            }
                            if (service!=null && !visitedClasses.contains(service.toString())
                                    && !excludedClasses.contains(service.getName().toString())) {
                                visitedClasses.add(service.toString());
                                services.add(new FrameworkService(service, cha));
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

        return services;
    }




    /*
     * This method is used to analyze the initialization of a field in the class where the annonymous stub
     *  class is assigned to the field present in the second parameter of publishBinderService/addService
     * @param cg: call graph
     * @param node: current node
     * @param ins: Abstract Invoke Instruction of publishBinderService/addService
     * @return IClass: the service class of the field
     */
    public IClass analyzeFieldInitialization(CallGraph cg, CGNode node, SSAInstruction ins){
        DefUse du = node.getDU();
        SSAInstruction defIns = du.getDef(InstructionUtils.getRealUse((SSAAbstractInvokeInstruction) ins, 1));
        if (defIns instanceof SSAGetInstruction getIns) {
            String variableName = getIns.getDeclaredField().getName().toString();
            TypeReference tempReference = getIns.getDeclaredField().getDeclaringClass();
            IClass outerClass = cha.lookupClass(tempReference);
            for (IMethod method : outerClass.getDeclaredMethods()) {
                if (method.isInit()) {
                    CGNode initNode = cg.getNode(method, Everywhere.EVERYWHERE);
                    if(initNode == null){
                        initNode = (CGNode) CachedCallGraphs.buildSingleEpCg(method.getReference(), cha).getEntrypointNodes().toArray()[0];// Can this be improved?
                    }
                    for(SSAInstruction ins2 : initNode.getIR().getInstructions()){
                        if(ins2 instanceof SSAPutInstruction putIns && putIns.getDeclaredField().getName().toString().equals(variableName)){
                            return TypeFinder.findConcreteType(cg, initNode,putIns.getVal());
                        }
                    }
                }
            }
        }
        return null;
    }
}
