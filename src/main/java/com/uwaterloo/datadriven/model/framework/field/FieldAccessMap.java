package com.uwaterloo.datadriven.model.framework.field;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class FieldAccessMap<T> {
    public final Map<AccessType, HashSet<T>> accessTypeMap = new HashMap<>();
    public FieldAccessMap() {
        for (AccessType t : AccessType.values()) {
            accessTypeMap.put(t, new HashSet<>());
        }
    }

    public void addInAccessMap(AccessType accessType, T t) {
        accessTypeMap.get(accessType).add(t);
    }
    public void addAllInAccessMap(AccessType accessType, HashSet<T> t) {
        accessTypeMap.get(accessType).addAll(t);
    }
    public FieldAccessMap<T> mergeWith(FieldAccessMap<T> otherMap) {
        for (AccessType type : AccessType.values()) {
            if (otherMap.accessTypeMap.containsKey(type))
                addAllInAccessMap(type, otherMap.accessTypeMap.get(type));
        }

        return this;
    }
    @Override
    public String toString() {
        return accessTypeMap.toString();
    }


    public ArrayList<String> toCsvString() {
        ArrayList<String> csvStrings = new ArrayList<>();
        csvStrings.add(accessTypeMap.toString());
        return csvStrings;
    }

}
