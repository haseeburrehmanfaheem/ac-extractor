package com.uwaterloo.datadriven.model.framework.field;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.util.collections.Pair;
import com.uwaterloo.datadriven.model.accesscontrol.misc.AccessControlSource;
import com.uwaterloo.datadriven.model.framework.FrameworkClass;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class ComplexField extends FrameworkField {
    public final HashMap<String, FrameworkField> members = new HashMap<>();
    public ComplexField(IClass parent, IClass immediateParent, String id,
                        String type, HashSet<String> modsAndKeywords) {
        super(parent, immediateParent, id, type, modsAndKeywords);
    }
    public void addMember(FrameworkField member) {
        if (!members.containsKey(member.id))
            members.put(member.id, member);
    }

    @Override
    public void setParentClass(FrameworkClass parentClass) {
        super.setParentClass(parentClass);
        super.setParentClass(parentClass);
        for (FrameworkField m : members.values()) {
            m.setParentClass(parentClass);
        }
    }

    @Override
    protected ArrayList<String> getChildCsvStrings() {
        ArrayList<String> row = new ArrayList<>();
        row.add(getNormalizedType());
        row.add(" ");
        return row;
    }

    @Override
    public ArrayList<String> getMemberCsvStrings(){
        ArrayList<String> row = new ArrayList<>();
        for(String memberId : members.keySet()){
            String toAdd = memberId + "::<" + members.get(memberId).type + ">";
            row.add(toAdd);
        }
        return row;
    }

    @Override
    public String getNormalizedType() {
        return "ComplexField";
    }
}
