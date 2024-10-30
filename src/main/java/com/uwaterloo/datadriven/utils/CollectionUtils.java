package com.uwaterloo.datadriven.utils;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.TypeReference;
import com.uwaterloo.datadriven.model.framework.field.CollectionField;

import java.util.*;

public class CollectionUtils {
    private static CollectionUtils instance = null;
    private final ClassHierarchy cha;
    private final IClass COLLECTION_CLASS;
    private final IClass MAP_CLASS;
    private final IClass SET_CLASS;
    private final IClass LIST_CLASS;
    private final Set<String> COLLECTION_METHODS_WITH_VAL_NUM_2 = Set.of(
            "put",
            "set",
            "get"
    );
    private final Set<String> COLLECTION_ADDERS = Set.of("add", "set", "put", "addAll", "setAll", "putAll");
    private final Set<String> COLLECTION_REMOVERS = Set.of("remove", "pop", "removeAll");
    private final Set<String> COLLECTION_GETTERS = Set.of("get", "getAll", "next");
    private final Set<String> COLLECTION_KEY_EXISTS = Set.of("containsKey");
    private final Set<String> COLLECTION_VALUE_EXISTS = Set.of("contains", "containsValue");
    private final Set<String> COLLECTION_KEYS_ACCESS = Set.of("keySet");
    private final Set<String> COLLECTION_VALUES_ACCESS = Set.of("values");
    private final Set<String> COLLECTION_ITERATE = Set.of("iterator");

    private final List<Set<String>> ALL_COLLECTION_METHODS = List.of(COLLECTION_ADDERS,
            COLLECTION_REMOVERS, COLLECTION_GETTERS, COLLECTION_KEY_EXISTS, COLLECTION_VALUE_EXISTS,
            COLLECTION_KEYS_ACCESS, COLLECTION_VALUES_ACCESS, COLLECTION_ITERATE
    );

    private CollectionUtils(ClassHierarchy cha) {
        this.cha = cha;
        COLLECTION_CLASS = cha.lookupClass(TypeReference
                .find(ClassLoaderReference.Primordial, "Ljava/util/Collection"));
        MAP_CLASS = cha.lookupClass(TypeReference
                .find(ClassLoaderReference.Primordial, "Ljava/util/Map"));
        SET_CLASS = cha.lookupClass(TypeReference
                .find(ClassLoaderReference.Primordial, "Ljava/util/Set"));
        LIST_CLASS = cha.lookupClass(TypeReference
                .find(ClassLoaderReference.Primordial, "Ljava/util/List"));
    }

    synchronized public static CollectionUtils getInstance(ClassHierarchy cha) {
        if (instance == null) {
            instance = new CollectionUtils(cha);
        }
        return instance;
    }

    public boolean isCollection(TypeReference fieldType) {
        if (fieldType == null)
            return false;
        IClass curClass = cha.lookupClass(fieldType);
        return isArray(fieldType)
                || isMap(fieldType)
                || isAndroidUtilsCollection(fieldType)
                || ChaUtils.doesImplementOrExtend(curClass, COLLECTION_CLASS, cha);
    }

    public boolean isArray(TypeReference fieldType) {
        if (fieldType == null)
            return false;
        return fieldType.isArrayType();
    }

    public boolean isMap(TypeReference fieldType) {
        if (fieldType == null)
            return false;
        return ChaUtils.doesImplementOrExtend(cha.lookupClass(fieldType), MAP_CLASS, cha);
    }

    public boolean isSet(TypeReference fieldType) {
        if (fieldType == null)
            return false;
        return ChaUtils.doesImplementOrExtend(cha.lookupClass(fieldType), SET_CLASS, cha);
    }

    public boolean isList(TypeReference fieldType) {
        if (fieldType == null)
            return false;
        return ChaUtils.doesImplementOrExtend(cha.lookupClass(fieldType), LIST_CLASS, cha);
    }

    public boolean isAndroidUtilsCollection(TypeReference fieldType) {
        if (fieldType == null)
            return false;
        String fullTypeName = fieldType.getName().toString();
        String typeClassName = "";
        int id = fullTypeName.lastIndexOf('/');
        if (id > 0 && id < fullTypeName.length()-1)
            typeClassName = fullTypeName.substring(id+1);

        return fullTypeName.startsWith("Landroid/util")
                && (typeClassName.contains("Array") || typeClassName.contains("Map")
                || typeClassName.contains("Set") || typeClassName.contains("Queue"));
    }

    public boolean isCollectionGetter(SSAAbstractInvokeInstruction abIns) {
        return isCollectionMethod(COLLECTION_GETTERS, abIns);
    }

    public boolean isCollectionAdder(SSAAbstractInvokeInstruction abIns) {
        return isCollectionMethod(COLLECTION_ADDERS, abIns);
    }

    public boolean isCollectionRemover(SSAAbstractInvokeInstruction abIns) {
        return isCollectionMethod(COLLECTION_REMOVERS, abIns);
    }

    public boolean isCollectionKeyExists(SSAAbstractInvokeInstruction abIns) {
        return isCollectionMethod(COLLECTION_KEY_EXISTS, abIns);
    }

    public boolean isCollectionValExists(SSAAbstractInvokeInstruction abIns) {
        return isCollectionMethod(COLLECTION_VALUE_EXISTS, abIns);
    }

    public boolean isCollectionKeysAccess(SSAAbstractInvokeInstruction abIns) {
        return isCollectionMethod(COLLECTION_KEYS_ACCESS, abIns);
    }

    public boolean isCollectionValsAccess(SSAAbstractInvokeInstruction abIns) {
        return isCollectionMethod(COLLECTION_VALUES_ACCESS, abIns);
    }

    public boolean isCollectionIterate(SSAAbstractInvokeInstruction abIns) {
        return isCollectionMethod(COLLECTION_ITERATE, abIns);
    }

    public boolean isAnyCollectionMethod(SSAAbstractInvokeInstruction abIns) {
        for (Set<String> collMethods : ALL_COLLECTION_METHODS) {
            if (isCollectionMethod(collMethods, abIns))
                return true;
        }
        return false;
    }

    private boolean isCollectionMethod(Set<String> collSet, SSAAbstractInvokeInstruction abIns) {
        TypeReference cls = abIns.getDeclaredTarget().getDeclaringClass();
        if (cls.getName().getClassName().toString().contains("Iterable")
                || cls.getName().getClassName().toString().contains("Iterator")
                || isCollection(cls))
            return collSet.contains(abIns.getDeclaredTarget().getName().toString());
        return false;
    }

    public int getValNum(SSAAbstractInvokeInstruction knownMethod) {
        return COLLECTION_METHODS_WITH_VAL_NUM_2.contains(knownMethod
                .getDeclaredTarget().getName().toString()) ? 2 : 1;
    }

    public ArrayList<TypeReference> getInnerTypes(TypeReference typeRef, String annotationStr) {
        if (!isCollection(typeRef))
            return null;
        ArrayList<TypeReference> innerTypes = new ArrayList<>();
        String[] concreteTypes = FieldUtils.getCollectionFieldTypeStr(typeRef, annotationStr)
                .replace("Array","")
                .replace('<',',')
                .replace('>',',')
                .replace(';',',')
                .split(",");
        if (concreteTypes.length < 2)
            innerTypes.add(TypeReference.Int);
        else {
            for (int i=1; i < concreteTypes.length; i++) {
                String typeStr = concreteTypes[i];
                if (typeStr != null && !typeStr.isBlank()) {
                    typeStr = typeStr.replace(",", "");
                    TypeReference innerType = null;
                    try {
                        innerType = TypeReference.find(ClassLoaderReference.Application, typeStr);
                    } catch (Exception e) {
                        //ignore
                    }
                    if (innerType == null) {
                        try {
                            innerType = TypeReference.find(ClassLoaderReference.Primordial, typeStr);
                        } catch (Exception e) {
                            //ignore
                        }
                    }
                    if (innerType != null)
                        innerTypes.add(innerType);
                }
            }
        }
        return innerTypes;
    }

    public String getOuterTypeStr(CollectionField field) {
        return field.type.substring(0, field.type.indexOf('<'));
    }
}
