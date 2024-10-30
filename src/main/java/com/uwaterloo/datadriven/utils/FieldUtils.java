package com.uwaterloo.datadriven.utils;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.dalvik.classLoader.DexIClass;
import com.ibm.wala.dalvik.classLoader.DexIField;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.TypeReference;
import com.uwaterloo.datadriven.model.framework.field.CollectionField;
import com.uwaterloo.datadriven.model.framework.field.ComplexField;
import com.uwaterloo.datadriven.model.framework.field.FrameworkField;
import com.uwaterloo.datadriven.model.framework.field.PrimitiveField;

import java.io.BufferedReader;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class FieldUtils {

    public static boolean isPrimitiveOrStringOrBundle(TypeReference type) {
        return type.isPrimitiveType()
                || type.getName().toString().equals("Ljava/lang/Object")
                || type.getName().toString().equals("Ljava/lang/String")
                || type.getName().toString().equals("Landroid/os/Bundle");
    }
    public static boolean isThisField(IField field) {
        String fieldName = field.getName().toString();
        if (fieldName.indexOf("$") > 0)
            fieldName = fieldName.substring(0, fieldName.indexOf("$"));
        return fieldName.equals("this");
    }
    public static boolean isBlackListed(TypeReference fieldType) {
        String fieldName = fieldType.getName().toString();
        return fieldName.equals("Landroid/os/Handler")
                || fieldName.equals("Landroid/content/Context");
    }
    public static String sanitizeType(String rawTypeStr) {
        return rawTypeStr.replace(",","")
                .replace(" ","")
                .replace("[","")
                .replace("]","")
                .replace("{","")
                .replace("}","")
                .replace(";>;", ">");
    }
    public static IField getField(IClass cls, FieldReference fieldReference) {
        for (IField field : cls.getAllFields()) {
            if (field.getReference().equals(fieldReference))
                return field;
        }
        return null;
    }
    public static String getCollectionFieldTypeStr(TypeReference typeRef, String annotationStr) {
        StringBuilder typeStr = new StringBuilder();
        if (typeRef.isArrayType())
            typeStr.append("Array<");
        if (annotationStr != null && !annotationStr.isBlank()) {
            String parentType = typeRef.getName().toString();
            int parentTypeId = annotationStr.indexOf(parentType);
            if (parentTypeId > 0)
                typeStr.append(FieldUtils.sanitizeType(annotationStr.substring(parentTypeId)));
            else
                typeStr.append(FieldUtils.sanitizeType(typeRef.getName().toString()));
        }
        if (typeRef.isArrayType())
            typeStr.append(">");
        if (typeStr.isEmpty())
            typeStr.append(typeRef.getName().toString());

        return typeStr.toString();
    }
    public static String getClassContentAsString(String fieldStr, ClassHierarchy cha) {
        try {
            IClass cls = cha.lookupClass(TypeReference.find(ClassLoaderReference.Application, fieldStr));
            BufferedReader br = new BufferedReader(cls.getSource());
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null)
                output.append(line).append("\n");
            return output.toString();
        } catch (Exception e) {
            //ignore
        }
        return null;
    }
    public static FrameworkField createField(ClassHierarchy cha, IField field,
                                             IClass parentClass, HashSet<String> visited) {

        if (!(field instanceof DexIField dexField) || isThisField(field))
            return null;

        IClass immParent = field.getDeclaringClass();
        String fieldId = field.getName().toString();
        String fieldType = field.getFieldTypeReference().getName().toString();

        HashSet<String> modKeywords = getModKeywords(field);

        if (FieldUtils.isBlackListed(field.getFieldTypeReference()))
            return null;
        TypeReference typeRef = field.getFieldTypeReference();
        if (FieldUtils.isPrimitiveOrStringOrBundle(typeRef))
            return new PrimitiveField(parentClass, immParent, fieldId, fieldType, modKeywords);
        if (visited.contains(fieldId+"::"+typeRef+"::"+immParent))
            return null;
        visited.add(fieldId+"::"+typeRef+"::"+immParent);
        if (CollectionUtils.getInstance(cha).isCollection(typeRef)) {
            ArrayList<TypeReference> innerTypes = CollectionUtils.getInstance(cha)
                    .getInnerTypes(field.getFieldTypeReference(), dexField.getAnnotations().toString());
            if (innerTypes == null || innerTypes.isEmpty())
                return null;
            TypeReference indexType, valueType;
            if (CollectionUtils.getInstance(cha).isSet(typeRef)) {
                indexType = null;
                valueType = innerTypes.get(0);
            } else if (innerTypes.size() == 1) {
                indexType = TypeReference.Int;
                valueType = innerTypes.get(0);
            } else if (innerTypes.size() == 2){
                indexType = innerTypes.get(0);
                valueType = innerTypes.get(1);
            } else {
                throw new UnsupportedOperationException("Weird Collection!");
            }
            CollectionField newFld = new CollectionField(parentClass, immParent, fieldId,
                    getCollectionFieldTypeStr(field.getFieldTypeReference(),
                            dexField.getAnnotations().toString()), indexType, valueType, modKeywords);
            newFld.addMember(CollectionField.ALL_MEMBERS, cha, visited);
            newFld.addMember(CollectionField.SOME_MEMBERS, cha, visited);
            return newFld;
        }
        IClass typeCls = cha.lookupClass(typeRef);
        if (typeCls == null)
            return null;

        ComplexField newField = new ComplexField(parentClass, immParent, fieldId, fieldType, modKeywords);
        for (IField subFld : typeCls.getAllFields()) {
            FrameworkField newSubFld = createField(cha, subFld, parentClass, visited);
            if (newSubFld != null)
                newField.addMember(newSubFld);
        }

        return newField;
    }

    public static FrameworkField createField(ClassHierarchy cha, IClass parentClass,
                                             TypeReference typeRef, String annotationStr,
                                             IClass immParent, String fieldId,
                                             HashSet<String> modKeywords,
                                             HashSet<String> visited) {

        if (FieldUtils.isBlackListed(typeRef))
            return null;
        if (visited.contains(fieldId+"::"+typeRef+"::"+immParent))
            return null;
        visited.add(fieldId+"::"+typeRef+"::"+immParent);
        String fieldType = typeRef.getName().toString();
        if (FieldUtils.isPrimitiveOrStringOrBundle(typeRef))
            return new PrimitiveField(parentClass, immParent, fieldId, fieldType, modKeywords);
        if (CollectionUtils.getInstance(cha).isCollection(typeRef)) {
            ArrayList<TypeReference> innerTypes = CollectionUtils.getInstance(cha)
                    .getInnerTypes(typeRef, annotationStr);
            if (innerTypes == null || innerTypes.isEmpty())
                return null;
            TypeReference indexType, valueType;
            if (CollectionUtils.getInstance(cha).isSet(typeRef)) {
                indexType = null;
                valueType = innerTypes.get(0);
            } else if (innerTypes.size() == 1) {
                indexType = TypeReference.Int;
                valueType = innerTypes.get(0);
            } else if (innerTypes.size() == 2){
                indexType = innerTypes.get(0);
                valueType = innerTypes.get(1);
            } else {
                throw new UnsupportedOperationException("Weird Collection!");
            }
            return new CollectionField(parentClass, immParent, fieldId,
                    getCollectionFieldTypeStr(typeRef, annotationStr),
                    indexType, valueType, modKeywords);
        }
        IClass typeCls = cha.lookupClass(typeRef);
        if (typeCls == null)
            return null;

        ComplexField newField = new ComplexField(parentClass, immParent, fieldId, fieldType, modKeywords);
        for (IField subFld : typeCls.getAllFields()) {
            FrameworkField newSubFld = createField(cha, subFld, parentClass, visited);
            if (newSubFld != null)
                newField.addMember(newSubFld);
        }

        return newField;
    }

    private static HashSet<String> getModKeywords(IField field) {
        HashSet<String> modKeywords = new HashSet<>();
        if (field.isPrivate())
            modKeywords.add(FrameworkField.PRIVATE_MOD);
        else if (field.isProtected())
            modKeywords.add(FrameworkField.PROTECTED_MOD);
        else if (field.isPublic())
            modKeywords.add(FrameworkField.PUBLIC_MOD);
        else
            modKeywords.add(FrameworkField.PACKAGE_PRIVATE_MOD);

        if (field.isStatic())
            modKeywords.add(FrameworkField.STATIC_KEYWORD);
        if (field.isVolatile())
            modKeywords.add(FrameworkField.VOLATILE_KEYWORD);
        if (field.isFinal())
            modKeywords.add(FrameworkField.FINAL_KEYWORD);
        return modKeywords;
    }
}
