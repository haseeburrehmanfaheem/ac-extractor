package com.uwaterloo.datadriven.analyzers;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.dalvik.classLoader.DexIField;
import com.ibm.wala.dalvik.classLoader.DexIMethod;
import com.ibm.wala.dalvik.dex.instructions.Instruction;
import com.ibm.wala.dalvik.dex.instructions.Invoke;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.debug.UnimplementedError;
import com.uwaterloo.datadriven.model.framework.FrameworkEp;
import com.uwaterloo.datadriven.model.framework.FrameworkParent;
import com.uwaterloo.datadriven.model.framework.field.*;
import com.uwaterloo.datadriven.model.functional.CleanupFunction;
import com.uwaterloo.datadriven.utils.CachedCallGraphs;
import com.uwaterloo.datadriven.utils.CollectionUtils;
import com.uwaterloo.datadriven.utils.FieldUtils;
import com.uwaterloo.datadriven.utils.InstructionUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class DataStructureExtractor {
    private final ClassHierarchy cha;
    private AnalysisScope scope;
    private FrameworkParent fwParent;
    private final HashMap<IField, FrameworkField> allExtractedFields = new HashMap<>();
    private final HashMap<String, Boolean> analyzedTypes = new HashMap<>();
    private static final HashSet<String> visitedClasses = new HashSet<>();
    private final List<CleanupFunction> cleanupFunctions = List.of(
            () -> fwParent = null,
            allExtractedFields::clear,
            analyzedTypes::clear
    );
    public DataStructureExtractor(ClassHierarchy cha, AnalysisScope scope) {
        this.cha = cha;
        this.scope = scope;
    }
    public HashMap<IField, FrameworkField> extractAllDs(FrameworkParent fwParent) {
        cleanAll();
        this.fwParent = fwParent;
        HashSet<IClass> reachableClasses = fwParent.getReachableClasses(cha);
        for (IClass reachableClass : reachableClasses) {
            if (!visitedClasses.contains(reachableClass.getName().toString())) {
                visitedClasses.add(reachableClass.getName().toString());
                try {
                    for (IField field : reachableClass.getAllFields()) {
                        if (shouldInclude(field)) {
                            FrameworkField newFld = null;
                            try {
                                newFld = FieldUtils.createField(cha, field, reachableClass, new HashSet<>());
                            } catch (Exception e) {
                                //ignore
                            }
                            if (newFld != null)
                                allExtractedFields.put(field, newFld);
                        }
                    }
                } catch (Exception | UnimplementedError e) {
                    //ignore
                }
            }
        }

        return allExtractedFields;
    }

    private boolean shouldInclude(IField field) {
        if (CollectionUtils.getInstance(cha).isCollection(field.getFieldTypeReference())) {
            return isValidCollection(field);
        }
        return !FieldUtils.isThisField(field)
                && !field.isStatic()
                && !FieldUtils.isPrimitiveOrStringOrBundle(field.getFieldTypeReference())
                && !FieldUtils.isBlackListed(field.getFieldTypeReference())
                && isSink(field.getFieldTypeReference())
                && isFieldReachableFromApis(field);
    }
    private boolean isFieldReachableFromApis(IField field) {
        for (FrameworkEp apiEp : fwParent.eps) {
            if (isFieldReachableFromApi(field, apiEp.epMethod))
                return true;
        }
        return false;
    }

    private boolean isFieldReachableFromApi(IField field, DefaultEntrypoint apiEp) {
        TypeReference curFieldType = field.getFieldTypeReference();
        CallGraph apiCg = CachedCallGraphs.buildSingleEpCg(apiEp.getMethod().getReference(), cha);
        if (apiCg != null) {
            for (CGNode node : apiCg) {
                try {
                    for (SSAInstruction ins : node.getIR().getInstructions()) {
                        if (ins instanceof SSAFieldAccessInstruction faIns
                                && faIns.getDeclaredFieldType().equals(curFieldType)) {
                            return true;
                        } else if (ins instanceof SSANewInstruction newIns
                                && newIns.getConcreteType().equals(curFieldType)) {
                            return true;
                        } else if (ins instanceof SSACheckCastInstruction ccIns
                                && ccIns.getDeclaredResultTypes()[0].equals(curFieldType)) {
                            return true;
                        }
                    }
                } catch (Exception e) {
                    //ignore
                }
            }
        }

        return false;
    }

    private boolean isValidCollection(IField field) {
        if (!(field instanceof DexIField dexField))
            return false;
        Collection<TypeReference> innerTypes = CollectionUtils.getInstance(cha)
                .getInnerTypes(field.getFieldTypeReference(), dexField.getAnnotations().toString());
        if (innerTypes != null && !innerTypes.isEmpty()) {
            for (TypeReference innerType : innerTypes) {
                if (FieldUtils.isPrimitiveOrStringOrBundle(innerType)
                        || CollectionUtils.getInstance(cha).isCollection(innerType))
                    continue;
                if (FieldUtils.isBlackListed(innerType) || !isSink(innerType))
                    return false;
            }
        }
        return true;
    }

    private boolean isSink(TypeReference fieldType) {
        if (analyzedTypes.containsKey(fieldType.getName().toString()))
            return analyzedTypes.get(fieldType.getName().toString());
        analyzedTypes.put(fieldType.getName().toString(), false);
        IClass typeClass = cha.lookupClass(fieldType);
        try {
            if (typeClass == null || typeClass.isInterface()
                    || typeClass.isAbstract() || typeClass.getAllFields().isEmpty())
                return false;
        } catch (Exception | UnimplementedError e) {
            return false;
        }
        for (IField subField : typeClass.getAllFields()) {
            if (FieldUtils.isThisField(subField))
                continue;
            if (analyzedTypes.containsKey(subField.getFieldTypeReference().getName().toString())) {
                if (analyzedTypes.get(subField.getFieldTypeReference().getName().toString()))
                    continue;
                else
                    return false;
            }
            if (FieldUtils.isBlackListed(subField.getFieldTypeReference()))
                continue;
            if (CollectionUtils.getInstance(cha).isCollection(subField.getFieldTypeReference())
                    && isValidCollection(subField))
                continue;
            if (!FieldUtils.isPrimitiveOrStringOrBundle(subField.getFieldTypeReference())
                    && !subField.isStatic() && !isSink(subField.getFieldTypeReference()))
                return false;
        }
        for (IMethod method : typeClass.getAllMethods()) {
            if (method instanceof DexIMethod dexM) {
                for (Instruction ins : dexM.getInstructions()) {
                    if (ins instanceof Invoke inv
                            && !isWhiteListed(inv)
                            && !inv.clazzName.equals(typeClass.getName().toString()))
                        return false;
                }
            }
        }
        analyzedTypes.put(fieldType.getName().toString(), true);
        return true;
    }

    private boolean isWhiteListed(Invoke inv) {
        return InstructionUtils.isInit(inv)
                || InstructionUtils.isJavaOrAndroidUtilMethod(inv)
                || InstructionUtils.isXmlMethod(inv)
                || InstructionUtils.isStatic(inv);
    }

    private void cleanAll() {
        for (CleanupFunction cleanupFunction : cleanupFunctions)
            cleanupFunction.cleanup();
    }
}
