package com.uwaterloo.datadriven.model.framework.field;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.uwaterloo.datadriven.model.accesscontrol.misc.AccessControlSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class PrimitiveField extends FrameworkField {

    public PrimitiveField(IClass parent, IClass immediateParent, String id,
                          String type, HashSet<String> modsAndKeywords) {
        super(parent, immediateParent, id, type, modsAndKeywords);
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
        return new ArrayList<>();
    }

    @Override
    public String getNormalizedType() {
        return "PrimitiveField";
    }
}
