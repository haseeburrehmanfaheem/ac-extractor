package com.uwaterloo.datadriven.model.framework.field;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.types.TypeReference;
import com.uwaterloo.datadriven.model.framework.FrameworkClass;
import com.uwaterloo.datadriven.utils.FieldUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class CollectionField extends FrameworkField {

    public static final String ALL_MEMBERS = "ALL_MEMBERS";
    public static final String SOME_MEMBERS = "SOME_MEMBERS";
    public static final String INDEX_MEMBER = "INDEX_MEMBER";
    public static final String VALUE_MEMBER = "VALUE_MEMBER";

    public final HashMap<String, CollectionMember> members = new HashMap<>();
    public final TypeReference indexType;
    public final TypeReference valueType;

    public CollectionField(IClass parent, IClass immediateParent, String id, String type,
                           TypeReference indexType, TypeReference valueType,
                           HashSet<String> modsAndKeywords) {
        super(parent, immediateParent, id, type, modsAndKeywords);
        this.indexType = indexType;
        this.valueType = valueType;
    }

    public void addMember(String memberIndex, ClassHierarchy cha, HashSet<String> visited) {
        if (!members.containsKey(memberIndex)) {
            members.put(memberIndex, createDummyField(memberIndex, cha, visited));
        }
    }

    @Override
    public void setParentClass(FrameworkClass parentClass) {
        super.setParentClass(parentClass);
        for (CollectionMember m : members.values()) {
            if (m.indexDummy() != null)
                m.indexDummy().setParentClass(parentClass);
            if (m.valueDummy() != null)
                m.valueDummy().setParentClass(parentClass);
        }
    }

    @Override
    protected ArrayList<String> getChildCsvStrings() {
        ArrayList<String> row = new ArrayList<>();
        row.add(getNormalizedType());
        row.add((indexType!=null ? indexType.toString() : "") + "::" + valueType.toString()); // we can add something for set
        return row;
    }

    @Override
    public ArrayList<String> getMemberCsvStrings(){
        ArrayList<String> row = new ArrayList<>();
        for(String memberId : members.keySet()){
            CollectionMember val = members.get(memberId);
            String toAdd = memberId + "::<" + ( val.indexDummy()!=null ? val.indexDummy().type : "") + "::" + ( val.valueDummy()!=null ? val.valueDummy().type : "") + ">";
            row.add(toAdd);
        }
        return row;
    }

    private CollectionMember createDummyField(String index, ClassHierarchy cha, HashSet<String> visited) {
        FrameworkField indexDummy = null;
        if (indexType != null)
            indexDummy = FieldUtils.createField(cha, parent,
                    indexType, indexType.getName().toString(), immediateParent, index,
                    new HashSet<>(List.of()), new HashSet<>());
        FrameworkField valueDummy = FieldUtils.createField(cha, parent,
                valueType, valueType.getName().toString(), immediateParent, index,
                new HashSet<>(), visited);
        return new CollectionMember(indexDummy, valueDummy);
    }

    @Override
    public String getNormalizedType() {
        return "CollectionField";
    }
}
